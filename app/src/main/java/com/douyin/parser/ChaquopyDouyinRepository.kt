package com.douyin.parser

import android.util.Log
import com.chaquo.python.Python
import com.douyin.core.model.ParseFailure
import com.douyin.core.model.ParseFailureType
import com.douyin.core.model.ParseResult
import com.douyin.data.douyin.DouyinMediaMapper
import com.douyin.data.douyin.DouyinRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Chaquopy 适配器：只负责调用 Python，平台 JSON 由 data 层映射。 */
class ChaquopyDouyinRepository : DouyinRepository {

    override suspend fun resolve(input: String): ParseResult = withContext(Dispatchers.IO) {
        try {
            val raw = Python.getInstance()
                .getModule("douyin_resolver")
                .callAttr("resolve", input)
                .toString()
            DouyinMediaMapper.fromPythonJson(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Douyin Python resolver failed", e)
            ParseResult.Failure(
                ParseFailure(
                    type = ParseFailureType.UNKNOWN,
                    message = "抖音解析引擎启动失败，请重启 App 后重试",
                    debugDetail = e.message,
                )
            )
        }
    }

    private companion object {
        const val TAG = "DouyinRepository"
    }
}
