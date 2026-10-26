/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Serves downloaded files over a token-protected local HTTP server.
 */
package com.douyin.data.transfer

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Token-protected LAN HTTP server that exposes completed downloads to a desktop browser.
 */
class WifiTransferServer(private val context: Context) {

    private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null
    @Volatile
    private var running = false

    /** Per-session access token generated at start; requests must include it in the query string. */
    @Volatile
    var accessToken: String = ""

    val isRunning: Boolean get() = running

    @Synchronized
    /**
     * Starts the LAN server and generates a fresh access token.
     *
     * @param port Preferred non-privileged TCP port.
     * @return True when the server socket was created successfully.
     */
    fun start(port: Int = 8080): Boolean {
        if (running) return true
        return try {
            serverSocket = ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))
            accessToken = TransferSecurity.newToken()
            executor = Executors.newFixedThreadPool(MAX_WORKERS)
            running = true
            Thread { acceptLoop() }.start()
            true
        } catch (e: Exception) {
            Log.e("DYAPP", "wifi server start fail", e)
            false
        }
    }

    @Synchronized
    /** Stops accepting requests and releases the server socket. Safe to call repeatedly. */
    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        executor?.shutdownNow()
        executor = null
        accessToken = ""
    }

    /** Returns the browser URL including the current access token. */
    fun getAccessUrlWithToken(port: Int = 8080): String {
        val base = getAccessUrl(port)
        return if (base.startsWith("http")) "$base?token=$accessToken" else base
    }

    private fun acceptLoop() {
        while (running) {
            try {
                val socket = serverSocket?.accept() ?: break
                executor?.execute { handle(socket) } ?: socket.close()
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
            // Skip the HTTP request header before parsing the request line.
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            val out = socket.getOutputStream()
            if (method == "GET") {
                // Reject requests without the session token to prevent arbitrary LAN access.
                val query = path.substringAfter('?', "")
                val token = query.split("&").firstOrNull { it.startsWith("token=") }?.substringAfter("=") ?: ""
                if (!TransferSecurity.tokenMatches(accessToken, token)) {
                    sendUnauthorized(out)
                    socket.close()
                    return
                }
                val cleanPath = path.substringBefore('?')
                when {
                    cleanPath == "/" || cleanPath == "/index.html" -> sendIndex(out)
                    cleanPath == "/download" -> {
                        // Parse the file parameter from the raw query because the cleaned path omits it.
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
                // Escape filenames in HTML because titles are untrusted user/platform data.
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
            <html><head><meta charset="utf-8"><title>ClipCove - WiFi Transfer</title>
            <style>
            body{font-family:sans-serif;background:#141518;color:#fff;margin:0;padding:20px}
            h1{font-size:20px}a{color:#25F4EE;text-decoration:none}
            table{width:100%;border-collapse:collapse;margin-top:12px}
            td,th{padding:8px;border-bottom:1px solid #2a2a30;text-align:left;font-size:14px}
            th{color:#8a8a93;font-size:12px}
            .empty{color:#8a8a93;margin-top:20px}
            </style></head><body>
            <h1>ClipCove · WiFi Transfer</h1>
            <p style="color:#8a8a93">手机已下载的文件,点击即可下载到本机</p>
            ${if (files.isEmpty()) "<p class='empty'>暂无已下载文件</p>" else "<table><tr><th>文件名</th><th>大小</th><th>字节</th></tr>$rows</table>"}
            </body></html>
        """.trimIndent()
        writeResponse(out, "text/html; charset=utf-8", html.toByteArray())
    }

    private fun sendFile(out: java.io.OutputStream, name: String) {
        // Reject path traversal by requiring the canonical target to remain under the downloads root.
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
        val attachmentName = TransferSecurity.sanitizeAttachmentName(name)
        val encodedName = java.net.URLEncoder.encode(attachmentName, "UTF-8").replace("+", "%20")
        val header = (
            "HTTP/1.1 200 OK\r\n" +
                "Content-Type: $mime\r\n" +
                "Content-Length: ${file.length()}\r\n" +
                "Content-Disposition: attachment; filename=\"$attachmentName\"; filename*=UTF-8''$encodedName\r\n" +
                "X-Content-Type-Options: nosniff\r\n" +
                "Cache-Control: no-store\r\n" +
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
                "Cache-Control: no-store\r\n" +
                "X-Content-Type-Options: nosniff\r\n" +
                "Connection: close\r\n\r\n"
            ).toByteArray()
        out.write(header)
        out.write(body)
    }

    /** Returns a browser URL containing the current session token, or empty when stopped. */
    fun getAccessUrl(port: Int = 8080): String {
        return try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ip = wifi.connectionInfo?.ipAddress ?: 0
            if (ip == 0) {
                ""
            } else {
                val ipStr = String.format(
                    "%d.%d.%d.%d",
                    ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff
                )
                "http://$ipStr:$port"
            }
        } catch (e: Exception) {
            Log.e("DYAPP", "get ip fail", e)
            ""
        }
    }

    private companion object {
        const val MAX_WORKERS = 8
    }
}