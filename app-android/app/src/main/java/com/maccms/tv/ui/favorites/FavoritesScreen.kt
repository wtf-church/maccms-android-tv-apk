package com.maccms.tv.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maccms.tv.data.Vod
import com.maccms.tv.ui.components.LibraryListRow
import com.maccms.tv.ui.theme.NetflixBlack
import com.maccms.tv.ui.theme.TextGray
import com.maccms.tv.ui.theme.TextWhite
import com.maccms.tv.util.FavoriteEntry
import com.maccms.tv.util.FavoriteStore
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 我的收藏：OK 打开详情（可选集/续播），右方向键移焦到「取消收藏」。
 * 收藏入口在详情页的「收藏」按钮。
 */
@Composable
fun FavoritesScreen(onOpenDetail: (Vod) -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(FavoriteStore.list(context)) }
    val listState = rememberLazyListState()
    val requesters = remember { mutableStateMapOf<Int, FocusRequester>() }
    var pendingFocusId by remember { mutableStateOf<Int?>(null) }

    // 删除后把焦点落到相邻条目，避免遥控器失焦
    LaunchedEffect(entries) {
        if (entries.isEmpty()) return@LaunchedEffect
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

    fun remove(entry: FavoriteEntry) {
        val index = entries.indexOfFirst { it.vodId == entry.vodId }
        FavoriteStore.remove(context, entry.vodId)
        val rest = entries.filterNot { it.vodId == entry.vodId }
        pendingFocusId = rest.getOrNull(index.coerceAtMost(rest.lastIndex))?.vodId
        entries = rest
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .padding(horizontal = 40.dp, vertical = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("我的收藏", color = TextWhite, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text("${entries.size} 部", color = TextGray, fontSize = 14.sp)
        }
        Spacer(Modifier.height(18.dp))
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有收藏，进入影片详情页点「收藏」即可", color = TextGray, fontSize = 16.sp)
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
                            listOf(entry.year, entry.remarks)
                                .filter { it.isNotBlank() }
                                .forEach { append(it).append(" · ") }
                            append("收藏于 ")
                            append(shortDate(entry.addedAt))
                        },
                        pic = entry.pic,
                        actionLabel = "取消收藏",
                        onClick = { onOpenDetail(entry.toVod()) },
                        onAction = { remove(entry) },
                        focusRequester = fr,
                        actionFocusRequester = actionFr,
                        onRequestAction = { runCatching { actionFr.requestFocus() } },
                        onRequestRow = { runCatching { fr.requestFocus() } }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "OK 查看详情 · 方向键 → 取消收藏 · 返回 退出",
                color = TextGray,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

private fun shortDate(ts: Long): String =
    if (ts <= 0) "" else SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ts))
