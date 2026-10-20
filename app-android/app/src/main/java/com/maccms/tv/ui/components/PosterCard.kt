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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.maccms.tv.data.Vod
import com.maccms.tv.ui.theme.NetflixBlack
import com.maccms.tv.ui.theme.NetflixRed
import com.maccms.tv.ui.theme.TextWhite

/** 焦点缩放的海报卡片（Netflix 风格：聚焦 1.0→1.1 + 白色描边） */
@Composable
fun PosterCard(
    vod: Vod,
    modifier: Modifier = Modifier,
    width: Dp = 160.dp,
    height: Dp = 224.dp,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    keyHandler: ((KeyEvent) -> Boolean)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f),
        label = "posterScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Color.White else Color.Transparent,
        label = "borderColor"
    )

    Box(
        modifier
            .width(width)
            .height(height)
            .scale(scale)
            .zIndex(if (isFocused) 2f else 0f)
            .border(3.dp, borderColor, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(NetflixBlack)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .then(if (keyHandler != null) Modifier.onKeyEvent(keyHandler) else Modifier)
            .clickable(onClick = onClick)
    ) {
        if (vod.vod_pic.isNotBlank()) {
            AsyncImage(
                model = vod.vod_pic,
                contentDescription = vod.vod_name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center
            ) {
                Text("N", color = NetflixRed, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
        }
        // 底部渐隐 + 片名
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xE6000000))
                    )
                )
        )
        Text(
            text = vod.vod_name,
            color = TextWhite,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .fillMaxWidth()
        )
    }
}
