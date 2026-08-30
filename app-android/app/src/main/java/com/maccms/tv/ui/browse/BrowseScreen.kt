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
    val typeNames = listOf(
        1 to "电影", 2 to "电视剧", 3 to "综艺", 4 to "动漫", 5 to "短剧", 6 to "纪录片"
    )
    var selectedType by remember { mutableStateOf(1) }
    var vods by remember { mutableStateOf<List<Vod>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
