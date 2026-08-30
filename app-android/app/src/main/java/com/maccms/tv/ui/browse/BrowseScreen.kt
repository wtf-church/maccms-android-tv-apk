package com.maccms.tv.ui.browse

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch

/** 浏览页：左侧分类列表 + 右侧该类影片网格 */
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
    val scope = rememberCoroutineScope()

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
            repository.getListByType(id).onSuccess {
                vods = it
                loading = false
            }.onFailure {
                error = it.message
                loading = false
            }
        }
    }

    LaunchedEffect(selectedType) { load(selectedType) }

    Row(Modifier.fillMaxSize().background(NetflixBlack)) {
        LazyColumn(
            modifier = Modifier.width(220.dp).fillMaxHeight(),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            items(typeNames, key = { it.first }) { (id, name) ->
                TypeItem(name = name, selected = id == selectedType, onClick = { selectedType = id })
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(end = 40.dp)) {
            when {
                loading -> LoadingBox()
                error != null -> ErrorBox(error ?: "", onRetry = { load(selectedType) })
                else -> LazyColumn(contentPadding = PaddingValues(top = 32.dp, bottom = 40.dp)) {
                    items(vods.chunked(5)) { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(bottom = 20.dp)
                        ) {
                            row.forEach { vod ->
                                PosterCard(
                                    vod = vod,
                                    width = 176.dp,
                                    height = 250.dp,
                                    onClick = { onVodClick(vod) }
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
private fun TypeItem(name: String, selected: Boolean, onClick: () -> Unit) {
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
            .onFocusChanged { focused = it.isFocused }
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
