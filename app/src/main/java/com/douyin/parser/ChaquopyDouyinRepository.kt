package com.douyin.parser

import com.chaquo.python.Python
import com.douyin.data.douyin.DouyinRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Chaquopy 实现(位于 app 层:依赖 Chaquopy 运行时,由插件打包)。
 * Python 解析引擎代码位于 app/src/main/python/。
 */
class ChaquopyDouyinRepository : DouyinRepository {

    override suspend fun resolve(url: String): String = withContext(Dispatchers.IO) {
        Python.getInstance()
            .getModule("douyin_resolver")
            .callAttr("resolve", url).toString()
    }
}
