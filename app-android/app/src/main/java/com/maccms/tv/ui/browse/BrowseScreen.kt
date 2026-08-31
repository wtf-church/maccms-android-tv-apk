package com.maccms.tv.ui.browse

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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

/** 浏览页：左侧分类列表 + 右侧该类影片网格（遥控器方向键跨区域导航） */
@Composable
fun BrowseScreen(
    repository: VodRepository,
    onVodClick: (Vod) -> Unit
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
    var selectedType by remember { mutableStateOf(5) }
    var vods by remember { mutableStateOf<List<Vod>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var maxPage by remember { mutableIntStateOf(1) }
    var nextPage by remember { mutableIntStateOf(1) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    // 首次加载：探测各分类是否有数据，只保留有数据的分类
    LaunchedEffect(Unit) {
        val existing = mutableListOf<Pair<Int, String>>()
        allTypeNames.forEach { (id, name) ->
            repository.getListByType(id).onSuccess { typeList ->
                if (typeList.isNotEmpty()) existing += (id to name)
            }
        }
        typeNames = if (existing.isEmpty()) allTypeNames else existing
    }

    fun load(id: Int) {
        scope.launch {
            loading = true
            error = null
            loadingMore = false
            maxPage = 1
            nextPage = 1
            repository.getListByTypePage(id, 1).onSuccess { resp ->
                if (selectedType == id) {
                    vods = resp.list
                    maxPage = resp.pagecount.coerceAtLeast(1)
                }
                loading = false
            }.onFailure {
                error = it.message
                loading = false
            }
        }
    }

    // 触底加载下一页：读最后一项时预取下一页（pagecount 上限内）
    LaunchedEffect(gridState, nextPage, maxPage, selectedType, vods.size) {
        if (!loading && !loadingMore && nextPage < maxPage && vods.isNotEmpty()) {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
            val target = (vods.size - 1).coerceAtLeast(0)
            if (last != null && last.index >= target - GRID_COLUMNS) {
                loadingMore = true
                val page = nextPage
                val type = selectedType
                repository.getListByTypePage(type, page).onSuccess { resp ->
                    if (selectedType == type) {
                        vods = (vods + resp.list).distinctBy { it.vod_id }
                        maxPage = resp.pagecount.coerceAtLeast(maxPage)
                        nextPage = page + 1
                    }
                }
                loadingMore = false
            }
        }
    }

    LaunchedEffect(selectedType) { load(selectedType) }

    // —— 遥控器焦点管理 ——
    val typeListState = rememberLazyListState()
    val focusedTypeId = remember { mutableIntStateOf(selectedType) }
    val typeRequesters = remember { mutableStateMapOf<Int, FocusRequester>() }

    // 进入浏览页时先聚焦当前分类，保证遥控器立即可用
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { typeRequesters[focusedTypeId.intValue]?.requestFocus() }
    }

    // 网格边界键（最左列 LEFT / 首行 UP）跳回分类列表
    fun focusCategory() {
        scope.launch {
            typeListState.animateScrollToItem(
                typeNames.indexOfFirst { it.first == focusedTypeId.intValue }.coerceAtLeast(0)
            )
            delay(60)
            runCatching { typeRequesters[focusedTypeId.intValue]?.requestFocus() }
        }
    }

    Row(Modifier.fillMaxSize().background(NetflixBlack)) {
        LazyColumn(
            state = typeListState,
            modifier = Modifier.width(220.dp).fillMaxHeight(),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            items(typeNames, key = { it.first }) { (id, name) ->
                TypeItem(
                    name = name,
                    selected = id == selectedType,
                    onClick = { selectedType = id },
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
                loading -> LoadingBox()
                error != null -> ErrorBox(error ?: "", onRetry = { load(selectedType) })
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(GRID_COLUMNS),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 32.dp, bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    itemsIndexed(vods, key = { _, v -> v.vod_id }) { index, vod ->
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
