package com.maccms.tv.data

import com.maccms.tv.util.BaseUrlManager

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import android.content.Context

/** maccms 标准 API（api.php/provide/vod） */
interface MaccmsApi {

    /** 列表：ac=videolist 返回全部字段（含 vod_pic/vod_play_url）；pg=页码；t=分类 id；wd=搜索关键词；by=排序（hits/score/time） */
    @GET("api.php/provide/vod")
    suspend fun getVodList(
        @Query("ac") ac: String = "videolist",
        @Query("pg") pg: Int = 1,
        @Query("t") t: String? = null,
        @Query("wd") wd: String? = null,
        @Query("by") by: String? = null
    ): String

    /** 轻量列表：ac=list（不含 vod_pic 等字段，字段更少、更快）；by=排序 */
    @GET("api.php/provide/vod")
    suspend fun getVodListLight(
        @Query("ac") ac: String = "list",
        @Query("pg") pg: Int = 1,
        @Query("t") t: String? = null,
        @Query("wd") wd: String? = null,
        @Query("by") by: String? = null
    ): String

    /** 详情：ac=detail&ids=vod_id */
    @GET("api.php/provide/vod")
    suspend fun getVodDetail(
        @Query("ac") ac: String = "detail",
        @Query("ids") ids: Int
    ): String
}

class ApiClient(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    val api: MaccmsApi by lazy {
        Retrofit.Builder()
            .baseUrl(BaseUrlManager.getBaseUrl(context).ensureSlash())
            .client(okHttp)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(MaccmsApi::class.java)
    }

    fun decodeList(raw: String): ApiResponse =
        json.decodeFromString(ApiResponse.serializer(), raw)

    fun decodeDetail(raw: String): Vod? {
        val resp = json.decodeFromString(ApiResponse.serializer(), raw)
        return resp.list.firstOrNull()
    }

    private fun String.ensureSlash(): String = if (endsWith("/")) this else "$this/"
}
