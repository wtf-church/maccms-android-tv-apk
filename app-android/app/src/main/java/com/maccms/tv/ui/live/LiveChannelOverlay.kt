package com.maccms.tv.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maccms.tv.data.LiveChannel
import com.maccms.tv.data.LivePlaylist
import com.maccms.tv.ui.theme.Gray20
import com.maccms.tv.ui.theme.NetflixRed
import com.maccms.tv.ui.theme.TextGray
import com.maccms.tv.ui.theme.TextWhite

/**
 * 直播频道浮层。纯展示，不持有 Compose 焦点：
 * 方向键/OK 由 LiveScreen 的根节点统一处理，这里只根据 selectedGroup/selectedChannel 高亮。
 */
@Composable
internal fun LiveChannelOverlay(
    playlist: LivePlaylist,
    currentChannel: LiveChannel?,
    selectedGroup: Int,
    selectedChannel: Int,
    refreshing: Boolean,
    message: String?,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val groupState = rememberLazyListState()
    val groups = playlist.groups
    val safeGroup = selectedGroup.coerceIn(0, (groups.size - 1).coerceAtLeast(0))
    val group = groups.getOrNull(safeGroup)
    val channels = group?.channels.orEmpty()

    LaunchedEffect(safeGroup, selectedChannel, channels.size) {
        if (channels.isEmpty()) return@LaunchedEffect
        val target = selectedChannel.coerceIn(0, channels.lastIndex)
        val visible = listState.layoutInfo.visibleItemsInfo
        val first = visible.firstOrNull()?.index ?: -1
        val last = visible.lastOrNull()?.index ?: -1
        if (first < 0 || target < first || target > last) {
            listState.scrollToItem(target)
        }
    }
    LaunchedEffect(safeGroup) {
        if (groups.isNotEmpty()) groupState.animateScrollToItem(safeGroup)
    }

    Box(modifier.fillMaxSize().background(Color(0x99000000))) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(440.dp)
                .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                .background(Color(0xF2141414))
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("直播频道", color = TextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text("${playlist.channelCount} 个频道", color = TextGray, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "当前：${currentChannel?.name ?: "未选择"}",
                color = TextGray,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(state = groupState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(groups, key = { _, g -> g.name }) { index, g ->
                    val selected = index == safeGroup
                    Text(
                        "${g.name} ${g.channels.size}",
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) NetflixRed else Gray20)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                itemsIndexed(channels, key = { _, channel -> channel.name }) { index, channel ->
                    ChannelRow(
                        number = index + 1,
                        channel = channel,
                        selected = index == selectedChannel,
                        playing = channel.group == currentChannel?.group &&
                            channel.name == currentChannel?.name
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (refreshing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = NetflixRed,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("正在刷新节目源…", color = TextGray, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
            }
            if (!message.isNullOrBlank()) {
                Text(
                    message,
                    color = NetflixRed,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                "OK 切换 · ◀▶ 分组 · 菜单键刷新 · 返回关闭",
                color = TextGray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ChannelRow(
    number: Int,
    channel: LiveChannel,
    selected: Boolean,
    playing: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    selected -> NetflixRed
                    playing -> Color(0xFF3A1113)
                    else -> Color.Transparent
                }
            )
            .then(
                if (playing && !selected) {
                    Modifier.border(1.dp, NetflixRed, RoundedCornerShape(6.dp))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(
            number.toString().padStart(3, '0'),
            color = if (selected) TextWhite else TextGray,
            fontSize = 12.sp,
            modifier = Modifier.width(40.dp)
        )
        Text(
            channel.name,
            color = TextWhite,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (channel.isIpv6) {
            Text(
                "IPv6",
                color = if (selected) TextWhite else Color(0xFF64B5F6),
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (selected) Color(0x33FFFFFF) else Color(0xFF1E3A5F))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        if (playing) {
            Text("▶", color = if (selected) TextWhite else NetflixRed, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            "${channel.urls.size}源",
            color = if (selected) TextWhite else TextGray,
            fontSize = 12.sp
        )
    }
}
