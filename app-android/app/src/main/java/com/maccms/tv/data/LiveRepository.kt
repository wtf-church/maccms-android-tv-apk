package com.maccms.tv.data

import android.content.Context
import android.util.Log
import com.maccms.tv.util.LiveSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * 直播源仓库：缓存优先 + 过期后台刷新。
 *
 * 磁盘缓存位于 `filesDir/live/playlist.m3u`，元数据（源地址、抓取时间、ETag、
 * 频道数）存在 SharedPreferences。TTL 12 小时；进入直播页先返回缓存保证秒开，
 * 过期后再在后台用 ETag 条件请求刷新。
 */
class LiveRepository(context: Context) {

    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()
    private val mutex = Mutex()
    private val cacheDir = File(appContext.filesDir, "live")
    private val cacheFile = File(cacheDir, "playlist.m3u")
    private val cacheTmpFile = File(cacheDir, "playlist.m3u.tmp")

    @Volatile
    private var memoryPlaylist: LivePlaylist? = null

    @Volatile
    private var memoryUrl: String? = null

    /** 缓存不存在、源地址变了或超过 TTL 时需要刷新。 */
    fun needsRefresh(): Boolean {
        val url = LiveSettings.getPlaylistUrl(appContext)
        if (url.isBlank()) return true
        if (LiveSettings.getCachedUrl(appContext) != url) return true
        if (!cacheFile.exists()) return true
        val fetchedAt = LiveSettings.getFetchedAt(appContext)
        return fetchedAt <= 0L || System.currentTimeMillis() - fetchedAt >= CACHE_TTL_MS
    }

    /** 当前源地址对应的磁盘缓存信息；没有可用缓存时返回 null。 */
    fun cacheMeta(): LiveCacheMeta? {
        val url = LiveSettings.getPlaylistUrl(appContext)
        if (url.isBlank()) return null
        if (LiveSettings.getCachedUrl(appContext) != url) return null
        val fetchedAt = LiveSettings.getFetchedAt(appContext)
        if (fetchedAt <= 0L || !cacheFile.exists()) return null
        return LiveCacheMeta(
            fetchedAt = fetchedAt,
            channelCount = LiveSettings.getChannelCount(appContext),
            sourceUrl = url
        )
    }

    /** 内存缓存 → 磁盘缓存 → 网络。缓存即使过期也会先返回，由调用方决定是否后台刷新。 */
    suspend fun load(): Result<LivePlaylist> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val url = LiveSettings.getPlaylistUrl(appContext)
            if (url.isBlank()) {
                return@withLock Result.failure(IOException(NO_SOURCE_MESSAGE))
            }
            memoryPlaylist?.takeIf { memoryUrl == url }?.let { cached ->
                Log.d(TAG, "使用内存缓存：${cached.channelCount} 个频道")
                return@withLock Result.success(cached)
            }
            readCached(url)?.let { cached ->
                Log.d(TAG, "使用磁盘缓存：${cached.channelCount} 个频道")
                return@withLock Result.success(cached)
            }
            fetchLocked(url, forceRefresh = false)
        }
    }

    /**
     * 联网刷新。[force] = true 时忽略 ETag 强制下载（手动刷新）；
     * `force = false` 时可用 304 复用缓存（过期后台刷新）。
     * 失败不会删除旧缓存。
     */
    suspend fun refresh(force: Boolean = true): Result<LivePlaylist> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val url = LiveSettings.getPlaylistUrl(appContext)
            if (url.isBlank()) {
                return@withLock Result.failure(IOException(NO_SOURCE_MESSAGE))
            }
            fetchLocked(url, forceRefresh = force)
        }
    }

    /** 源地址变更时清空内存/磁盘缓存与元数据。 */
    fun invalidateCache() {
        memoryPlaylist = null
        memoryUrl = null
        runCatching {
            cacheTmpFile.delete()
            cacheFile.delete()
        }.onFailure { Log.w(TAG, "删除直播源缓存失败", it) }
        LiveSettings.clearCacheMeta(appContext)
    }

    private fun readCached(sourceUrl: String): LivePlaylist? {
        if (LiveSettings.getCachedUrl(appContext) != sourceUrl) return null
        if (!cacheFile.exists()) return null
        return runCatching {
            val text = cacheFile.readText(Charsets.UTF_8)
            val playlist = M3uPlaylistParser.parse(text)
            memoryPlaylist = playlist
            memoryUrl = sourceUrl
            playlist
        }.onFailure { error ->
            Log.w(TAG, "直播源缓存解析失败，清理缓存", error)
            runCatching {
                cacheFile.delete()
                LiveSettings.clearCacheMeta(appContext)
            }
        }.getOrNull()
    }

    private fun fetchLocked(sourceUrl: String, forceRefresh: Boolean): Result<LivePlaylist> {
        return try {
            Result.success(downloadAndParse(sourceUrl, forceRefresh))
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.w(TAG, "直播源加载失败", error)
            Result.failure(IOException(error.friendlyMessage(), error))
        }
    }

    private fun downloadAndParse(sourceUrl: String, forceRefresh: Boolean): LivePlaylist {
        val cachedUrl = LiveSettings.getCachedUrl(appContext)
        val etag = LiveSettings.getEtag(appContext)
        val request = Request.Builder()
            .url(sourceUrl)
            .header("User-Agent", LIVE_USER_AGENT)
            .header("Accept", "*/*")
            .apply {
                if (!forceRefresh && etag != null && cachedUrl == sourceUrl) {
                    header("If-None-Match", etag)
                }
                if (forceRefresh) {
                    cacheControl(CacheControl.Builder().noCache().noStore().build())
                    header("Pragma", "no-cache")
                }
            }
            .build()

        Log.d(TAG, "下载直播源：$sourceUrl（强制刷新=$forceRefresh）")
        client.newCall(request).execute().use { response ->
            if (response.code == 304) {
                val cached = readCached(sourceUrl)
                    ?: throw IOException("直播源缓存缺失，请重试")
                LiveSettings.updateFetchedAt(appContext, System.currentTimeMillis())
                Log.i(TAG, "直播源未变化（304），继续使用缓存")
                return cached
            }
            if (!response.isSuccessful) {
                throw IOException("直播源下载失败（HTTP ${response.code}）")
            }
            val body = response.body ?: throw IOException("直播源下载失败：响应为空")
            val bytes = body.byteStream().use { readLimited(it, MAX_PLAYLIST_BYTES) }
            val text = decode(bytes, response.header("Content-Type"))
            val playlist = M3uPlaylistParser.parse(text)
            writeCache(text)
            LiveSettings.setCacheMeta(
                context = appContext,
                sourceUrl = sourceUrl,
                fetchedAt = System.currentTimeMillis(),
                etag = response.header("ETag"),
                channelCount = playlist.channelCount
            )
            memoryPlaylist = playlist
            memoryUrl = sourceUrl
            Log.i(
                TAG,
                "直播源下载成功：${playlist.groups.size} 个分组 / ${playlist.channelCount} 个频道"
            )
            return playlist
        }
    }

    private fun writeCache(text: String) {
        runCatching {
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                throw IOException("无法创建缓存目录")
            }
            cacheTmpFile.writeText(text, Charsets.UTF_8)
            cacheTmpFile.copyTo(cacheFile, overwrite = true)
            cacheTmpFile.delete()
        }.onFailure { Log.w(TAG, "写入直播源缓存失败", it) }
    }

    private fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                throw IOException("直播源文件超过 ${maxBytes / 1024 / 1024} MB")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun decode(bytes: ByteArray, contentType: String?): String {
        val charsetName = contentType
            ?.substringAfter("charset=", "")
            ?.substringBefore(';')
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotEmpty() }
        val charset = charsetName
            ?.let { runCatching { Charset.forName(it) }.getOrNull() }
            ?: Charsets.UTF_8
        val text = String(bytes, charset)
        if (charset == Charsets.UTF_8 && text.count { it == '�' } > 2) {
            val gbkText = runCatching { String(bytes, Charset.forName("GBK")) }.getOrNull()
            if (gbkText != null && gbkText.count { it == '�' } < text.count { it == '�' }) {
                return gbkText
            }
        }
        return text
    }

    private fun Throwable.friendlyMessage(): String = when (this) {
        is SocketTimeoutException -> "直播源下载超时，请检查网络"
        is UnknownHostException, is ConnectException -> "无法连接直播源服务器"
        is IllegalArgumentException -> message ?: "直播源格式错误"
        is InterruptedIOException -> "直播源下载超时，请稍后重试"
        is IOException -> {
            val msg = message.orEmpty()
            when {
                msg.contains("Canceled", ignoreCase = true) -> "直播源下载超时，请稍后重试"
                msg.startsWith("直播源") -> msg
                else -> "直播源下载失败，请检查网络"
            }
        }
        else -> message ?: "直播源加载失败"
    }

    companion object {
        private const val TAG = "LiveRepository"
        private const val NO_SOURCE_MESSAGE =
            "未配置直播源，请在设置中填写，或检查 assets/live_config.properties"
        const val CACHE_TTL_MS: Long = 12L * 60 * 60 * 1000
        private const val MAX_PLAYLIST_BYTES = 5 * 1024 * 1024
    }
}
