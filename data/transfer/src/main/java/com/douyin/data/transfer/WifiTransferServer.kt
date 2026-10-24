package com.douyin.data.transfer

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * WiFi 传文件:App 内轻量 HTTP 服务器,局域网内电脑浏览器直接下载已下载的文件。
 */
class WifiTransferServer(private val context: Context) {

    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    @Volatile
    private var running = false

    /** 访问令牌:开启时生成,客户端须带 ?token= 才能访问 */
    @Volatile
    var accessToken: String = ""

    val isRunning: Boolean get() = running

    fun start(port: Int = 8080): Boolean {
        if (running) return true
        return try {
            serverSocket = ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))
            // 随机令牌防局域网任意设备访问
            accessToken = java.util.UUID.randomUUID().toString().replace("-", "").take(8)
            running = true
            Thread { acceptLoop() }.start()
            true
        } catch (e: Exception) {
            Log.e("DYAPP", "wifi server start fail", e)
            false
        }
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
    }

    /** 获取带 token 的访问地址 */
    fun getAccessUrlWithToken(port: Int = 8080): String {
        val base = getAccessUrl(port)
        return if (base.startsWith("http")) "$base?token=$accessToken" else base
    }

    private fun acceptLoop() {
        while (running) {
            try {
                val socket = serverSocket?.accept() ?: break
                executor.execute { handle(socket) }
            } catch (e: Exception) {
                if (running) Log.e("DYAPP", "accept fail", e)
            }
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = 15000
            val reader = socket.getInputStream().bufferedReader()
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1]
            // 跳过请求头
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            val out = socket.getOutputStream()
            if (method == "GET") {
                // Token 鉴权:防止局域网内任意设备访问
                val query = path.substringAfter('?', "")
                val token = query.split("&").firstOrNull { it.startsWith("token=") }?.substringAfter("=") ?: ""
                if (accessToken.isNotEmpty() && token != accessToken) {
                    sendUnauthorized(out)
                    socket.close()
                    return
                }
                val cleanPath = path.substringBefore('?')
                when {
                    cleanPath == "/" || cleanPath == "/index.html" -> sendIndex(out)
                    cleanPath == "/download" -> {
                        // f 参数从原始 query 解析(cleanPath 已去掉 ? 后内容)
                        val name = java.net.URLDecoder.decode(
                            query.substringAfter("f=").substringBefore("&"),
                            "UTF-8"
                        )
                        sendFile(out, name)
                    }
                    else -> sendNotFound(out)
                }
            } else {
                sendNotFound(out)
            }
            out.flush()
        } catch (e: Exception) {
            Log.e("DYAPP", "handle fail", e)
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun sendIndex(out: java.io.OutputStream) {
        val dir = File(context.filesDir, "downloads")
        val files = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
        val rows = buildString {
            files.forEach { f ->
                val sizeMb = String.format("%.2f", f.length() / 1048576.0)
                // HTML 转义:文件名来自用户视频标题,防 XSS
                val safeName = f.name
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;")
                append(
                    "<tr><td><a href='/download?f=${java.net.URLEncoder.encode(f.name, "UTF-8")}&token=${accessToken}'>$safeName</a></td>" +
                        "<td>${sizeMb} MB</td><td>${f.length()} bytes</td></tr>"
                )
            }
        }
        val html = """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8"><title>拾刻 - WiFi 传输</title>
            <style>
            body{font-family:sans-serif;background:#141518;color:#fff;margin:0;padding:20px}
            h1{font-size:20px}a{color:#25F4EE;text-decoration:none}
            table{width:100%;border-collapse:collapse;margin-top:12px}
            td,th{padding:8px;border-bottom:1px solid #2a2a30;text-align:left;font-size:14px}
            th{color:#8a8a93;font-size:12px}
            .empty{color:#8a8a93;margin-top:20px}
            </style></head><body>
            <h1>拾刻 · WiFi 传输</h1>
            <p style="color:#8a8a93">手机已下载的文件,点击即可下载到本机</p>
            ${if (files.isEmpty()) "<p class='empty'>暂无已下载文件</p>" else "<table><tr><th>文件名</th><th>大小</th><th>字节</th></tr>$rows</table>"}
            </body></html>
        """.trimIndent()
        writeResponse(out, "text/html; charset=utf-8", html.toByteArray())
    }

    private fun sendFile(out: java.io.OutputStream, name: String) {
        // 防路径遍历:规范化后必须位于 downloads 目录内
        val baseDir = File(context.filesDir, "downloads").apply { mkdirs() }
        val file = File(baseDir, name)
        val canonical = try {
            file.canonicalPath
        } catch (e: Exception) {
            sendNotFound(out)
            return
        }
        if (!canonical.startsWith(baseDir.canonicalPath + File.separator) || !file.isFile) {
            sendNotFound(out)
            return
        }
        val mime = when (name.substringAfterLast('.', "mp4")) {
            "mp3" -> "audio/mpeg"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "json" -> "application/json"
            else -> "video/mp4"
        }
        val header = (
            "HTTP/1.1 200 OK\r\n" +
                "Content-Type: $mime\r\n" +
                "Content-Length: ${file.length()}\r\n" +
                "Content-Disposition: attachment; filename=\"${name}\"\r\n" +
                "Connection: close\r\n\r\n"
            ).toByteArray()
        out.write(header)
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
        }
    }

    private fun sendNotFound(out: java.io.OutputStream) {
        writeResponse(out, "text/plain; charset=utf-8", "404 Not Found".toByteArray(), "404 Not Found\r\n")
    }

    private fun sendUnauthorized(out: java.io.OutputStream) {
        writeResponse(out, "text/plain; charset=utf-8", "401 Unauthorized".toByteArray(), "401 Unauthorized\r\n")
    }

    private fun writeResponse(out: java.io.OutputStream, contentType: String, body: ByteArray, status: String = "200 OK\r\n") {
        val header = (
            "HTTP/1.1 $status" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
            ).toByteArray()
        out.write(header)
        out.write(body)
    }

    fun getAccessUrl(port: Int = 8080): String {
        return try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ip = wifi.connectionInfo?.ipAddress ?: 0
            if (ip == 0) {
                "无法获取局域网 IP(请确认已连接 WiFi)"
            } else {
                val ipStr = String.format(
                    "%d.%d.%d.%d",
                    ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff
                )
                "http://$ipStr:$port"
            }
        } catch (e: Exception) {
            Log.e("DYAPP", "get ip fail", e)
            "无法获取 IP(请确认已连接 WiFi)"
        }
    }
}

