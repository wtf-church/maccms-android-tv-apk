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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.maccms.tv.ui.theme.NetflixRed
import com.maccms.tv.ui.theme.TextGray
import com.maccms.tv.ui.theme.TextWhite

/**
 * 观看历史 / 我的收藏的列表行：左边海报，中间标题+副标题（可带进度条），
 * 右侧一个动作按钮（删除 / 取消收藏）。行本身 OK 打开，方向键右移可聚焦动作按钮。
 */
@Composable
fun LibraryListRow(
    title: String,
    subtitle: String,
    pic: String,
    actionLabel: String,
    onClick: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    focusRequester: FocusRequester? = null,
    actionFocusRequester: FocusRequester? = null,
    onRequestAction: () -> Unit = {},
    onRequestRow: () -> Unit = {},
    onRowFocused: () -> Unit = {}
) {
    var rowFocused by remember { mutableStateOf(false) }
    var actionFocused by remember { mutableStateOf(false) }
    val active = rowFocused || actionFocused
    val scale by animateFloatAsState(
        targetValue = if (active) 1.01f else 1f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "libRowScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (active) TextWhite else Color.Transparent,
        label = "libRowBorder"
    )
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .scale(scale)
            .zIndex(if (active) 2f else 0f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Color(0xFF2A2A2A) else Color(0xFF1C1C1C))
            .border(2.dp, borderColor, RoundedCornerShape(10.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                rowFocused = it.isFocused
                if (it.isFocused) onRowFocused()
            }
            // 行本身未聚焦时不吃按键；聚焦后 RIGHT 手动移到动作按钮
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && rowFocused && e.key == Key.DirectionRight) {
                    onRequestAction()
                    true
                } else false
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = pic,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(56.dp)
                .height(78.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0E0E0E))
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = TextWhite,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                color = TextGray,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (progress > 0f) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF3A3A3A))
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(NetflixRed)
                    )
                }
            }
        }
        Spacer(Modifier.width(20.dp))
        LibraryActionChip(
            label = actionLabel,
            onClick = onAction,
            focusRequester = actionFocusRequester,
            onRequestRow = onRequestRow,
            onFocusChange = { actionFocused = it }
        )
    }
}

@Composable
private fun LibraryActionChip(
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    onRequestRow: () -> Unit,
    onFocusChange: (Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) TextWhite else Color.Transparent,
        label = "libChipBorder"
    )
    Box(
        Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) NetflixRed else Color(0xFF3A3A3A))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused; onFocusChange(it.isFocused) }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && focused && e.key == Key.DirectionLeft) {
                    onRequestRow()
                    true
                } else false
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(label, color = TextWhite, fontSize = 14.sp)
    }
}
