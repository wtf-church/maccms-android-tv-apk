package com.maccms.tv.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maccms.tv.data.LiveRepository
import com.maccms.tv.ui.theme.NetflixBlack
import com.maccms.tv.ui.theme.NetflixRed
import com.maccms.tv.ui.theme.TextGray
import com.maccms.tv.ui.theme.TextWhite
import com.maccms.tv.util.BaseUrlManager
import com.maccms.tv.util.LiveSettings
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 设置页：API 服务器地址 + 直播源地址（M3U）。 */
@Composable
fun SettingsScreen(liveRepository: LiveRepository, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apiUrl by remember { mutableStateOf(BaseUrlManager.getBaseUrl(context)) }
    var saved by remember { mutableStateOf(false) }
    var liveUrl by remember { mutableStateOf(LiveSettings.getPlaylistUrl(context)) }
    var liveMessage by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val cacheMeta = liveRepository.cacheMeta()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun saveLiveUrl(): Boolean {
        val normalized = LiveSettings.normalize(liveUrl)
        if (normalized == null) {
            liveMessage = "请输入有效的 http(s) 直播源地址"
            return false
        }
        val previous = LiveSettings.getPlaylistUrl(context)
        val changed = previous != normalized
        if (!LiveSettings.setPlaylistUrl(context, normalized)) {
            liveMessage = "请输入有效的 http(s) 直播源地址"
            return false
        }
        liveUrl = normalized
        if (changed) {
            liveRepository.invalidateCache()
            liveMessage = "已保存直播源，进入直播页将重新下载节目源"
        } else {
            liveMessage = "直播源地址未变化"
        }
        return true
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 40.dp)
    ) {
        Text("设置", color = TextWhite, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Text("服务器地址（API Base URL）", color = TextGray, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        TextField(
            value = apiUrl,
            onValueChange = { apiUrl = it; saved = false },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            singleLine = true,
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = Color(0xFF222222),
                focusedIndicatorColor = NetflixRed,
                unfocusedIndicatorColor = Color(0xFF555555),
                cursorColor = NetflixRed,
                textColor = TextWhite
            )
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "默认 ${BaseUrlManager.DEFAULT_BASE_URL}。\n修改后立即生效；如遇网络问题，请确认电视可访问该域名。",
            color = TextGray,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingButton("保存", primary = true) {
                BaseUrlManager.setBaseUrl(context, apiUrl)
                saved = true
                onDone()
            }
            SettingButton("恢复默认", primary = false) {
                apiUrl = BaseUrlManager.DEFAULT_BASE_URL
                BaseUrlManager.setBaseUrl(context, BaseUrlManager.DEFAULT_BASE_URL)
                saved = true
            }
        }
        if (saved) {
            Spacer(Modifier.height(12.dp))
            Text("已保存 ✓", color = NetflixRed, fontSize = 14.sp)
        }

        Spacer(Modifier.height(32.dp))
        Text("直播节目源（M3U）", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        TextField(
            value = liveUrl,
            onValueChange = { liveUrl = it; liveMessage = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = Color(0xFF222222),
                focusedIndicatorColor = NetflixRed,
                unfocusedIndicatorColor = Color(0xFF555555),
                cursorColor = NetflixRed,
                textColor = TextWhite
            )
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "默认源由 assets/live_config.properties 的 playlistUrl 配置（当前为 cn_all_status）。修改地址后保存，直播页会重新下载；\n缓存 12 小时后自动后台更新，也可用「刷新节目源」或直播页菜单键手动刷新。",
            color = TextGray,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingButton("保存直播源", primary = true) {
                saveLiveUrl()
            }
            SettingButton("恢复默认直播源", primary = false) {
                val defaultUrl = LiveSettings.getDefaultPlaylistUrl(context)
                if (defaultUrl.isNullOrBlank()) {
                    liveMessage = "未找到默认直播源配置（assets/live_config.properties）"
                } else {
                    liveUrl = defaultUrl
                    if (LiveSettings.setPlaylistUrl(context, defaultUrl)) {
                        liveRepository.invalidateCache()
                        liveMessage = "已恢复默认直播源，进入直播页将重新下载"
                    }
                }
            }
            SettingButton("刷新节目源", primary = false, enabled = !refreshing) {
                if (!saveLiveUrl()) return@SettingButton
                refreshing = true
                scope.launch {
                    liveRepository.refresh(force = true)
                        .onSuccess { playlist ->
                            liveMessage = "已刷新：${playlist.channelCount} 个频道 · ${formatTime(System.currentTimeMillis())}"
                        }
                        .onFailure { error ->
                            liveMessage = "刷新失败：${error.message ?: "未知错误"}（已保留旧缓存）"
                        }
                    refreshing = false
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            when {
                refreshing -> "正在刷新节目源…"
                cacheMeta == null -> "缓存：尚未缓存"
                else -> "缓存：${cacheMeta.channelCount} 个频道，更新于 ${formatTime(cacheMeta.fetchedAt)}"
            },
            color = TextGray,
            fontSize = 13.sp
        )
        if (!liveMessage.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            val isError = liveMessage?.contains("失败") == true || liveMessage?.contains("请输入") == true
            Text(
                liveMessage ?: "",
                color = if (isError) NetflixRed else TextGray,
                fontSize = 14.sp
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun formatTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return "—"
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestampMs))
}

@Composable
private fun SettingButton(
    label: String,
    primary: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) TextWhite else Color.Transparent,
        label = "setBorder"
    )
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .alpha(if (enabled) 1f else 0.45f)
            .background(if (primary) NetflixRed else Color(0xFF333333))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Text(
            label,
            color = TextWhite,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}
