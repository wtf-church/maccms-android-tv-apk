package com.maccms.tv.ui.history

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maccms.tv.data.Vod
import com.maccms.tv.ui.components.LibraryListRow
import com.maccms.tv.ui.theme.NetflixBlack
import com.maccms.tv.ui.theme.NetflixRed
import com.maccms.tv.ui.theme.TextGray
import com.maccms.tv.ui.theme.TextWhite
import com.maccms.tv.util.WatchHistoryEntry
import com.maccms.tv.util.WatchHistoryStore
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 观看历史：最近观看排最前，OK 直接续播（一步到位），右方向键移焦到「删除」，
 * 顶部可一键清空。数据来自 WatchHistoryStore（播放页实时写入）。
 */
@Composable
fun HistoryScreen(onPlay: (Vod) -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(WatchHistoryStore.list(context)) }
    val listState = rememberLazyListState()
    val requesters = remember { mutableStateMapOf<Int, FocusRequester>() }
    var pendingFocusId by remember { mutableStateOf<Int?>(null) }
    val clearRequester = remember { FocusRequester() }

    // 列表变化（首次进入 / 删除后）时把焦点落到合适的位置，避免遥控器失焦
    LaunchedEffect(entries) {
        if (entries.isEmpty()) {
            runCatching { clearRequester.requestFocus() }
            return@LaunchedEffect
        }
        val targetId = pendingFocusId?.takeIf { id -> entries.any { it.vodId == id } }
            ?: entries.first().vodId
        pendingFocusId = null
        val index = entries.indexOfFirst { it.vodId == targetId }.coerceAtLeast(0)
        runCatching { listState.scrollToItem(index) }
        repeat(6) {
            delay(60)
            if (runCatching { requesters[targetId]?.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    fun delete(entry: WatchHistoryEntry) {
        val index = entries.indexOfFirst { it.vodId == entry.vodId }
        WatchHistoryStore.remove(context, entry.vodId)
        val rest = entries.filterNot { it.vodId == entry.vodId }
        pendingFocusId = rest.getOrNull(index.coerceAtMost(rest.lastIndex))?.vodId
        entries = rest
    }

    fun clearAll() {
        WatchHistoryStore.clear(context)
        entries = emptyList()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .padding(horizontal = 40.dp, vertical = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("观看历史", color = TextWhite, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text("${entries.size} 部", color = TextGray, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            HeaderButton(
                label = "清空历史",
                onClick = { clearAll() },
                focusRequester = clearRequester
            )
        }
        Spacer(Modifier.height(18.dp))
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有观看记录，去首页挑一部吧", color = TextGray, fontSize = 16.sp)
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                itemsIndexed(entries, key = { _, e -> e.vodId }) { _, entry ->
                    val fr = remember(entry.vodId) {
                        FocusRequester().also { requesters[entry.vodId] = it }
                    }
                    val actionFr = remember(entry.vodId) { FocusRequester() }
                    LibraryListRow(
                        title = entry.name.ifBlank { "影片 ${entry.vodId}" },
                        subtitle = buildString {
                            if (entry.episode.isNotBlank()) {
                                append("看到 ${entry.episode}")
                                if (entry.positionMs > 0) {
                                    append(" · ")
                                    append(formatClock(entry.positionMs))
                                }
                                append(" · ")
                            }
                            append(relativeTime(entry.updatedAt))
                        },
                        pic = entry.pic,
                        actionLabel = "删除",
                        progress = entry.progress,
                        onClick = { onPlay(entry.toVod()) },
                        onAction = { delete(entry) },
                        focusRequester = fr,
                        actionFocusRequester = actionFr,
                        onRequestAction = { runCatching { actionFr.requestFocus() } },
                        onRequestRow = { runCatching { fr.requestFocus() } }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "OK 继续播放 · 方向键 → 删除 · 返回 退出",
                color = TextGray,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun HeaderButton(
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) TextWhite else Color.Transparent,
        label = "histClearBorder"
    )
    Box(
        Modifier
            .focusRequester(focusRequester)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) NetflixRed else Color(0xFF333333))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(label, color = TextWhite, fontSize = 14.sp)
    }
}

private fun formatClock(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun relativeTime(ts: Long): String {
    if (ts <= 0) return ""
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000L} 天前"
        else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(ts))
    }
}
