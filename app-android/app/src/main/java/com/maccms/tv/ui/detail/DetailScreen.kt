package com.maccms.tv.ui.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.maccms.tv.data.Episode
import com.maccms.tv.data.Vod
import com.maccms.tv.data.VodParser
import com.maccms.tv.data.VodRepository
import com.maccms.tv.ui.components.ErrorBox
import com.maccms.tv.ui.components.LoadingBox
import com.maccms.tv.ui.theme.NetflixBlack
import com.maccms.tv.ui.theme.NetflixRed
import com.maccms.tv.ui.theme.TextGray
import com.maccms.tv.ui.theme.TextWhite
import kotlinx.coroutines.launch

/** 详情页：大背景 + 简介 + 播放源分集列表 */
@Composable
fun DetailScreen(
    vodId: Int,
    repository: VodRepository,
    onBack: () -> Unit,
    onPlayEpisode: (Vod, Episode) -> Unit
) {
    var vod by remember { mutableStateOf<Vod?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            error = null
            repository.getDetail(vodId).onSuccess {
                vod = it
                loading = false
            }.onFailure {
                error = it.message
                loading = false
            }
        }
    }

    LaunchedEffect(vodId) { load() }

    Box(Modifier.fillMaxSize().background(NetflixBlack)) {
        when {
            loading -> LoadingBox()
            error != null -> ErrorBox(error ?: "", onRetry = { load() })
            vod != null -> DetailContent(
                vod = vod!!,
                onBack = onBack,
                onPlayEpisode = onPlayEpisode
            )
        }
    }
}

@Composable
private fun DetailContent(
    vod: Vod,
    onBack: () -> Unit,
    onPlayEpisode: (Vod, Episode) -> Unit
) {
    val sources = remember(vod.vod_play_url) {
        VodParser.parsePlaySources(vod.vod_play_from, vod.vod_play_url)
    }
    var selectedSource by remember { mutableStateOf(0) }
    val currentSource = sources.getOrNull(selectedSource) ?: sources.firstOrNull()

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                AsyncImage(
                    model = vod.vod_pic,
                    contentDescription = vod.vod_name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .background(Brush.verticalGradient(listOf(Color(0xCC141414), Color.Transparent)))
                )
                BackButton(onBack, Modifier.padding(24.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 40.dp, vertical = 16.dp)) {
                Text(vod.vod_name, color = TextWhite, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row {
                    if (vod.vod_score.isNotBlank() && vod.vod_score != "0") {
                        Text(vod.vod_score, color = NetflixRed, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                    }
                    listOf(vod.vod_year, vod.vod_area, vod.vod_lang, vod.vod_remarks)
                        .filter { it.isNotBlank() }
                        .forEach {
                            Text(it, color = TextGray, fontSize = 14.sp)
                            Spacer(Modifier.width(12.dp))
                        }
                }
                Spacer(Modifier.height(12.dp))
                if (vod.vod_content.isNotBlank()) {
                    Text(vod.vod_content, color = TextGray, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                if (vod.vod_actor.isNotBlank()) {
                    Text("主演：${vod.vod_actor}", color = TextGray, fontSize = 13.sp)
                }
            }
        }
        if (!sources.isEmpty()) {
            item {
                Row(
                    Modifier.padding(start = 40.dp, top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    sources.forEachIndexed { i, s ->
                        SourceTab(name = s.from, selected = i == selectedSource, onClick = { selectedSource = i })
                    }
                }
            }
            if (currentSource != null) {
                itemsIndexed(currentSource.episodes) { _, ep ->
                    EpisodeItem(ep = ep, onClick = { onPlayEpisode(vod, ep) })
                }
            }
        } else {
            item {
                Text(
                    "暂无播放源",
                    color = TextGray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 24.dp)
                )
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) TextWhite else Color.Transparent,
        label = "backBorder"
    )
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xAA000000))
            .border(2.dp, borderColor, RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onBack)
    ) {
        Text(
            "‹  返回",
            color = TextWhite,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SourceTab(name: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) TextWhite else Color.Transparent,
        label = "srcBorder"
    )
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    selected -> NetflixRed
                    focused -> Color(0xFF333333)
                    else -> Color(0xFF222222)
                }
            )
            .border(2.dp, borderColor, RoundedCornerShape(6.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
    ) {
        Text(
            name,
            color = TextWhite,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun EpisodeItem(ep: Episode, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.01f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "epScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) NetflixRed else Color.Transparent,
        label = "epBorder"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 3.dp)
            .scale(scale)
            .zIndex(if (focused) 2f else 0f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color(0xFF333333) else Color(0xFF1D1D1D))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("▶", color = NetflixRed, fontSize = 12.sp)
            Spacer(Modifier.width(12.dp))
            Text(ep.name, color = TextWhite, fontSize = 15.sp)
        }
    }
}
