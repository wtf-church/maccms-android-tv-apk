package com.maccms.tv.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maccms.tv.ui.theme.NetflixBlack
import com.maccms.tv.ui.theme.NetflixRed
import com.maccms.tv.ui.theme.TextGray
import com.maccms.tv.ui.theme.TextWhite
import com.maccms.tv.util.BaseUrlManager

/** 设置页：修改 API 服务器地址（真机可改为宿主机局域网 IP） */
@Composable
fun SettingsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(BaseUrlManager.getBaseUrl(context)) }
    var saved by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .padding(horizontal = 48.dp, vertical = 40.dp)
    ) {
        Text("设置", color = TextWhite, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Text("服务器地址（API Base URL）", color = TextGray, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        TextField(
            value = url,
            onValueChange = { url = it; saved = false },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            singleLine = true,
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = Color(0xFF222222),
                focusedIndicatorColor = NetflixRed,
                unfocusedIndicatorColor = Color(0xFF555555),
                cursorColor = NetflixRed,
                textColor = TextWhite
            )
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "默认 ${BaseUrlManager.DEFAULT_BASE_URL}（模拟器内指向 maccms）。\n真机安装时，请改为电脑的局域网地址，如 http://192.168.1.100:8080，并确保电视与电脑同一网络。",
            color = TextGray,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingButton("保存", primary = true) {
                BaseUrlManager.setBaseUrl(context, url)
                saved = true
                onDone()
            }
            SettingButton("恢复默认", primary = false) {
                url = BaseUrlManager.DEFAULT_BASE_URL
                BaseUrlManager.setBaseUrl(context, BaseUrlManager.DEFAULT_BASE_URL)
                saved = true
            }
        }
        if (saved) {
            Spacer(Modifier.height(12.dp))
            Text("已保存 ✓", color = NetflixRed, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SettingButton(label: String, primary: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) TextWhite else Color.Transparent,
        label = "setBorder"
    )
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (primary) NetflixRed else Color(0xFF333333))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
    ) {
        Text(
            label,
            color = TextWhite,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}
