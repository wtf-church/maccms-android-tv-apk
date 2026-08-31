package com.maccms.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 数据仓库：封装 API 调用与解析，UI 层只依赖它。 */
class VodRepository(context: Context) {

    private val client = ApiClient(context.applicationContext)

    /** 首页：只取第一页 + 按热度排序（服务端 by=hits；未支持时客户端按 vod_hits 降序兜底） */
    suspend fun getHomeList(): Result<List<Vod>> = withContext(Dispatchers.IO) {
        runCatching {
            val list = client.decodeList(client.api.getVodListLight(by = "hits")).list
            sortByHits(list)
        }
    }

    /** 按分类拉取（分页，用于首页分类行）；按热度排序 */
    suspend fun getListByType(typeId: Int, page: Int = 1): Result<List<Vod>> =
        getListByTypePage(typeId, page).map { it.list }

    /**
     * 按分类拉取分页，返回原始响应（含 pagecount，浏览页无限滚动用）。
     * 服务端未按热度排序时客户端按 vod_hits 降序兜底。
     */
    suspend fun getListByTypePage(typeId: Int, page: Int): Result<ApiResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = client.api.getVodList(t = typeId.toString(), pg = page, by = "hits")
            client.decodeList(raw).let { it.copy(list = sortByHits(it.list)) }
        }
    }

    /** 获取分类列表：从 videolist 数据的 type_id/type_name 提取 + 常用分类顺序 */
    suspend fun getTypes(): Result<List<Pair<Int, String>>> = withContext(Dispatchers.IO) {
        runCatching {
            // 从第一页数据里提取出现的分类（name 为 null 时用 id 占位）
            val vods = client.decodeList(client.api.getVodListLight()).list
            vods.mapNotNull { v ->
                val n = v.type_name
                if (n != null) v.type_id to n else null
            }.distinctBy { it.first }
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

    /** 客户端兜底排序：服务端未按热度排序时按 vod_hits 降序 */
    private fun sortByHits(list: List<Vod>): List<Vod> =
        list.sortedByDescending { it.vod_hits }
}
