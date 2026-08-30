package com.maccms.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 数据仓库：封装 API 调用与解析，UI 层只依赖它。 */
class VodRepository(context: Context) {

    private val client = ApiClient(context.applicationContext)

    suspend fun getHomeList(): Result<List<Vod>> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = client.api.getVodList()
            client.decodeList(raw).list
        }
    }

    suspend fun getListByType(typeId: Int, page: Int = 1): Result<List<Vod>> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = client.api.getVodList(t = typeId.toString(), pg = page)
            client.decodeList(raw).list
        }
    }

    suspend fun search(keyword: String): Result<List<Vod>> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = client.api.getVodList(wd = keyword)
            client.decodeList(raw).list
        }
    }

    suspend fun getDetail(vodId: Int): Result<Vod> = withContext(Dispatchers.IO) {
        runCatching {
            client.decodeDetail(client.api.getVodDetail(ids = vodId))
                ?: throw IllegalStateException("detail empty")
        }
    }
}
