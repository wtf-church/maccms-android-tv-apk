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

    // 分类名称映射：线上 API type_name 为 null，用内置名称（已移除空分类；只保留有采集数据的分类）
    val typeNames = listOf(
        5 to "短剧", 6 to "纪录片",
        20 to "国产剧", 21 to "韩剧", 22 to "日漫", 23 to "美剧",
        24 to "华语电影", 25 to "欧美电影", 26 to "动作片", 27 to "爱情片", 28 to "科幻片",
        29 to "恐怖片", 30 to "剧情片", 31 to "伦理片", 32 to "香港剧", 33 to "台湾剧",
        34 to "日剧", 35 to "海外剧", 36 to "泰剧", 37 to "国漫", 38 to "欧美动漫",
        39 to "动画片", 40 to "大陆综艺", 41 to "港台综艺", 42 to "日韩综艺",
        43 to "欧美综艺", 44 to "电影解说", 45 to "预告片"
    )
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            error = null
            repository.getHomeList().onSuccess { list ->
                allVods = list
                // 每个分类行独立拉取第一页（避免全量 15 万条）；只保留有数据的分类
                typeNames.forEach { (id, _) ->
                    scope.launch {
                        repository.getListByType(id).onSuccess { typeList ->
                            if (typeList.isNotEmpty()) {
                                typeRows = typeRows + (id to typeList.take(12))
                            }
                        }
                    }
                }
                loading = false
            }.onFailure {
                error = it.message
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().background(NetflixBlack)) {
        TopNavBar(onNavigate = onNavigate, selected = "home")
        Box(Modifier.weight(1f)) {
            when {
                loading -> LoadingBox()
                error != null -> ErrorBox(error ?: "", onRetry = { load() })
                else -> if (allVods.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            HeroBanner(
                                vod = allVods.first(),
                                onPlayClick = { onPlayClick(allVods.first()) },
                                onInfoClick = { onVodClick(allVods.first()) }
                            )
                        }
                        // 只渲染有数据的分类行（避免空分类占位）
                        typeNames.filter { !(typeRows[it.first] ?: emptyList()).isEmpty() }
                            .forEach { (id, name) ->
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
    val items = listOf(
        "home" to "首页",
        "live" to "直播",
        "browse" to "浏览",
        "search" to "搜索",
        "history" to "观看历史",
        "favorites" to "我的收藏",
        "settings" to "设置"
    )
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
