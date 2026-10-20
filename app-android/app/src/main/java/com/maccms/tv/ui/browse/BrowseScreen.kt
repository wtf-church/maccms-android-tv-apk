package com.maccms.tv.ui.browse

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maccms.tv.data.Vod
import com.maccms.tv.data.VodRepository
import com.maccms.tv.ui.components.ErrorBox
import com.maccms.tv.ui.components.LoadingBox
import com.maccms.tv.ui.components.PosterCard
import com.maccms.tv.ui.theme.NetflixBlack
import com.maccms.tv.ui.theme.NetflixRed
import com.maccms.tv.ui.theme.TextWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val GRID_COLUMNS = 5

/**
 * 浏览页状态：提升到 NavHost 外层(remember)持有，导航到详情再返回时
 * 分类选择、已加载列表、网格滚动位置全部保留。
 */
class BrowseState {
    var selectedType by mutableIntStateOf(5)
    var vods by mutableStateOf<List<Vod>>(emptyList())
    var loadedType by mutableIntStateOf(0)
    var maxPage by mutableIntStateOf(1)
    var nextPage by mutableIntStateOf(1)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var gridState = LazyGridState()
    var typeListState = LazyListState()
}

/** 浏览页：左侧分类列表 + 右侧该类影片网格（遥控器方向键跨区域导航） */
@Composable
fun BrowseScreen(
    repository: VodRepository,
    onVodClick: (Vod) -> Unit,
    state: BrowseState = remember { BrowseState() }
) {
    val allTypeNames = listOf(
        5 to "短剧", 6 to "纪录片",
        20 to "国产剧", 21 to "韩剧", 22 to "日漫", 23 to "美剧",
        24 to "华语电影", 25 to "欧美电影", 26 to "动作片", 27 to "爱情片", 28 to "科幻片",
        29 to "恐怖片", 30 to "剧情片", 31 to "伦理片", 32 to "香港剧", 33 to "台湾剧",
        34 to "日剧", 35 to "海外剧", 36 to "泰剧", 37 to "国漫", 38 to "欧美动漫",
        39 to "动画片", 40 to "大陆综艺", 41 to "港台综艺", 42 to "日韩综艺",
        43 to "欧美综艺", 44 to "电影解说", 45 to "预告片"
    )
    var typeNames by remember { mutableStateOf(allTypeNames) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load(id: Int) {
        scope.launch {
            state.loading = true
            state.error = null
            loadingMore = false
            state.maxPage = 1
            state.nextPage = 1
            repository.getListByTypePage(id, 1).onSuccess { resp ->
                if (state.selectedType == id) {
                    state.vods = resp.list
                    state.maxPage = resp.pagecount.coerceAtLeast(1)
                    state.loadedType = id
                }
                state.loading = false
            }.onFailure {
                if (state.selectedType == id) {
                    // 标记已尝试：避免 effect 自动重试死循环，由用户点重试
                    state.error = it.message
                    state.loadedType = id
                }
                state.loading = false
            }
        }
    }

    // 首次进入浏览页才探测分类；有保存状态时跳过（避免重复探测覆盖用户选择）
    var probed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!probed) {
            probed = true
            if (state.vods.isEmpty()) {
                val existing = mutableListOf<Pair<Int, String>>()
                allTypeNames.forEach { (id, name) ->
                    repository.getListByType(id).onSuccess { typeList ->
                        if (typeList.isNotEmpty()) existing += (id to name)
                    }
                }
                typeNames = if (existing.isEmpty()) allTypeNames else existing
            }
        }
    }

    // 分类变化时加载第一页；返回浏览页时缓存列表已就绪则不重复请求。
    // loading 也作为 key：加载中切换分类时跳过本次，请求结束后再触发。
    // 防自动重试死循环：load 成功/失败都会更新 loadedType，使条件不再成立。
    LaunchedEffect(state.selectedType, state.loadedType, state.loading) {
        if (state.selectedType != state.loadedType && !state.loading) {
            state.vods = emptyList()
            state.error = null
            state.gridState.scrollToItem(0)
            load(state.selectedType)
        }
    }

    // 触底加载下一页：读最后一项时预取下一页（pagecount 上限内）
    LaunchedEffect(state.gridState, state.nextPage, state.maxPage, state.selectedType, state.vods.size) {
        if (!state.loading && !loadingMore && state.nextPage < state.maxPage && state.vods.isNotEmpty()) {
            val last = state.gridState.layoutInfo.visibleItemsInfo.lastOrNull()
            val target = (state.vods.size - 1).coerceAtLeast(0)
            if (last != null && last.index >= target - GRID_COLUMNS) {
                loadingMore = true
                val page = state.nextPage
                val type = state.selectedType
                repository.getListByTypePage(type, page).onSuccess { resp ->
                    if (state.selectedType == type) {
                        state.vods = (state.vods + resp.list).distinctBy { it.vod_id }
                        state.maxPage = resp.pagecount.coerceAtLeast(state.maxPage)
                        state.nextPage = page + 1
                    }
                }
                loadingMore = false
            }
        }
    }

    // —— 遥控器焦点管理 ——
    val focusedTypeId = remember { mutableIntStateOf(state.selectedType) }
    val typeRequesters = remember { mutableStateMapOf<Int, FocusRequester>() }

    // 进入浏览页时先聚焦当前分类，保证遥控器立即可用
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { typeRequesters[focusedTypeId.intValue]?.requestFocus() }
    }

    // 网格边界键（最左列 LEFT / 首行 UP）跳回分类列表
    fun focusCategory() {
        scope.launch {
            state.typeListState.animateScrollToItem(
                typeNames.indexOfFirst { it.first == focusedTypeId.intValue }.coerceAtLeast(0)
            )
            delay(60)
            runCatching { typeRequesters[focusedTypeId.intValue]?.requestFocus() }
        }
    }

    Row(Modifier.fillMaxSize().background(NetflixBlack)) {
        LazyColumn(
            state = state.typeListState,
            modifier = Modifier.width(220.dp).fillMaxHeight(),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            items(typeNames, key = { it.first }) { (id, name) ->
                TypeItem(
                    name = name,
                    selected = id == state.selectedType,
                    onClick = { state.selectedType = id },
                    focusRequester = remember(id) { FocusRequester().also { typeRequesters[id] = it } },
                    onFocused = { focusedTypeId.intValue = id }
                )
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(end = 40.dp)
        ) {
            when {
                state.loading -> LoadingBox()
                state.error != null -> ErrorBox(state.error ?: "", onRetry = { load(state.selectedType) })
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(GRID_COLUMNS),
                    state = state.gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 32.dp, bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    itemsIndexed(state.vods, key = { _, v -> v.vod_id }) { index, vod ->
                        PosterCard(
                            vod = vod,
                            width = 176.dp,
                            height = 250.dp,
                            onClick = { onVodClick(vod) },
                            keyHandler = { event ->
                                // 网格边界键处理：最左列 LEFT / 首行 UP 跳回分类列表
                                if (event.type != KeyEventType.KeyDown) return@PosterCard false
                                val col = index % GRID_COLUMNS
                                val row = index / GRID_COLUMNS
                                when (event.key) {
                                    Key.DirectionLeft -> {
                                        if (col == 0) {
                                            focusCategory()
                                            true
                                        } else false
                                    }
                                    Key.DirectionUp -> {
                                        if (row == 0) {
                                            focusCategory()
                                            true
                                        } else false
                                    }
                                    else -> false
                                }
                            }
                        )
                    }
                    if (loadingMore) {
                        // 触底加载更多时在网格尾部显示加载圈
                        item(key = "loading_more", span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = NetflixRed,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeItem(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    onFocused: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f),
        label = "typeScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) TextWhite else Color.Transparent,
        label = "typeBorder"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    selected -> NetflixRed
                    focused -> Color(0xFF333333)
                    else -> Color.Transparent
                }
            )
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(onClick = onClick)
    ) {
        Text(
            name,
            color = TextWhite,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}
