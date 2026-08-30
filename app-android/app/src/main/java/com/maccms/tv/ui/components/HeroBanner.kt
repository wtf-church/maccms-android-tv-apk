package com.maccms.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.maccms.tv.data.Vod
import com.maccms.tv.ui.theme.NetflixRed
import com.maccms.tv.ui.theme.TextWhite

/** Netflix 风格 Hero 横幅：大图 + 渐变遮罩 + 标题 + 操作按钮 */
@Composable
fun HeroBanner(
    vod: Vod,
    modifier: Modifier = Modifier,
    onPlayClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "heroScale"
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(400.dp)
            .scale(scale)
    ) {
        AsyncImage(
            model = vod.vod_pic,
            contentDescription = vod.vod_name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xF2141414))
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 48.dp, end = 48.dp, bottom = 36.dp)
        ) {
            Text(
                text = vod.vod_name,
                color = TextWhite,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vod.vod_score.isNotBlank() && vod.vod_score != "0") {
                    Text(vod.vod_score, color = NetflixRed, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("分", color = TextWhite, fontSize = 12.sp)
                    Spacer(Modifier.width(12.dp))
                }
                if (vod.vod_year.isNotBlank()) {
                    Text(vod.vod_year, color = TextWhite, fontSize = 15.sp)
                    Spacer(Modifier.width(12.dp))
                }
                if (vod.vod_remarks.isNotBlank()) {
                    Text(
                        vod.vod_remarks,
                        color = TextWhite,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .background(NetflixRed.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (vod.vod_content.isNotBlank()) {
                Text(
                    text = vod.vod_content,
                    color = TextWhite.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(16.dp))
            Row {
                HeroButton(label = "▶  播放", isPrimary = true, onClick = onPlayClick)
                Spacer(Modifier.width(16.dp))
                HeroButton(label = "＋  详情", isPrimary = false, onClick = onInfoClick)
            }
        }
    }
}

@Composable
private fun HeroButton(label: String, isPrimary: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "btnScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) Color.White else Color.Transparent,
        label = "btnBorder"
    )
    Box(
        Modifier
            .height(44.dp)
            .scale(scale)
            .zIndex(if (focused) 2f else 0f)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isPrimary) NetflixRed else Color(0xFF333333))
            .border(2.dp, borderColor, RoundedCornerShape(6.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
