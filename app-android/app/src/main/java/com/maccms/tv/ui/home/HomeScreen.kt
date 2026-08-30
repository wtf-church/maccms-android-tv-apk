package com.maccms.tv.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.maccms.tv.data.Vod
import com.maccms.tv.data.VodRepository
import com.maccms.tv.ui.components.ErrorBox
import com.maccms.tv.ui.components.HeroBanner
import com.maccms.tv.ui.components.LoadingBox
import com.maccms.tv.ui.components.PosterRow
import com.maccms.tv.ui.theme.NetflixBlack
import com.maccms.tv.ui.theme.NetflixRed
import com.maccms.tv.ui.theme.TextWhite
import kotlinx.coroutines.launch

/** 首页：顶部导航 + Hero 横幅 + 全部影片行 + 各分类行 */
@Composable
fun HomeScreen(
    repository: VodRepository,
    onVodClick: (Vod) -> Unit,
    onPlayClick: (Vod) -> Unit,
    onNavigate: (String) -> Unit
) {
    var allVods by remember { mutableStateOf<List<Vod>>(emptyList()) }
    var typeRows by remember { mutableStateOf<Map<Int, List<Vod>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val typeNames = listOf(
        1 to "电影", 2 to "电视剧", 3 to "综艺", 4 to "动漫", 5 to "短剧", 6 to "纪录片"
    )
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            error = null
            repository.getHomeList().onSuccess { list ->
                allVods = list
                typeNames.forEach { (id, _) ->
                    typeRows = typeRows + (id to list.filter { it.type_id == id }.take(12))
                }
                loading = false
            }.onFailure {
                error = it.message
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Box(Modifier.fillMaxSize().background(NetflixBlack)) {
        when {
            loading -> LoadingBox()
            error != null -> ErrorBox(error ?: "", onRetry = { load() })
            else -> Column(Modifier.fillMaxSize()) {
                TopNavBar(onNavigate = onNavigate, selected = "home")
                if (allVods.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        item {
                            HeroBanner(
                                vod = allVods.first(),
                                onPlayClick = { onPlayClick(allVods.first()) },
                                onInfoClick = { onVodClick(allVods.first()) }
                            )
                        }
                        typeNames.forEach { (id, name) ->
                            item {
                                PosterRow(
                                    title = name,
                                    vods = typeRows[id] ?: emptyList(),
                                    onVodClick = onVodClick
                                )
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

/** 顶部导航：Netflix 风格 —— 左侧 LOGO + 横向菜单 */
@Composable
fun TopNavBar(onNavigate: (String) -> Unit, selected: String) {
    val items = listOf("home" to "首页", "browse" to "浏览", "search" to "搜索", "settings" to "设置")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(NetflixBlack.copy(alpha = 0.95f))
            .padding(horizontal = 40.dp)
    ) {
        Text("MAC", color = TextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("CMS", color = NetflixRed, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(32.dp))
        items.forEach { (route, label) ->
            NavItem(label = label, isSelected = selected == route, onClick = { onNavigate(route) })
            Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
private fun NavItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f),
        label = "navScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) TextWhite else Color.Transparent,
        label = "navBorder"
    )
    Box(
        Modifier
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .scale(scale)
            .zIndex(if (focused) 2f else 0f)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isSelected -> NetflixRed
                    focused -> Color(0xFF333333)
                    else -> Color.Transparent
                }
            )
            .border(2.dp, borderColor, RoundedCornerShape(6.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
    ) {
        Text(
            label,
            color = TextWhite,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}
