package com.maccms.tv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maccms.tv.data.Vod
import com.maccms.tv.data.VodRepository
import com.maccms.tv.ui.components.ErrorBox
import com.maccms.tv.ui.components.LoadingBox
import com.maccms.tv.ui.components.PosterRow
import com.maccms.tv.ui.theme.NetflixBlack
import com.maccms.tv.ui.theme.NetflixRed
import com.maccms.tv.ui.theme.TextGray
import com.maccms.tv.ui.theme.TextWhite
import kotlinx.coroutines.launch

/** 搜索页：中文字符输入框（遥控器键盘） + 结果行 */
@Composable
fun SearchScreen(
    repository: VodRepository,
    onVodClick: (Vod) -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Vod>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    fun doSearch() {
        if (keyword.isBlank()) return
        scope.launch {
            loading = true
            error = null
            repository.search(keyword.trim()).onSuccess {
                results = it
                loading = false
            }.onFailure {
                error = it.message
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(Modifier.fillMaxSize().background(NetflixBlack).padding(horizontal = 40.dp)) {
        Spacer(Modifier.height(48.dp))
        Text("搜索", color = TextWhite, fontSize = 26.sp, modifier = Modifier.padding(bottom = 16.dp))
        TextField(
            value = keyword,
            onValueChange = { keyword = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            placeholder = { Text("输入片名关键词…", color = TextGray) },
            singleLine = true,
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = Color(0xFF222222),
                focusedIndicatorColor = NetflixRed,
                unfocusedIndicatorColor = Color(0xFF555555),
                cursorColor = NetflixRed,
                textColor = TextWhite
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { doSearch() })
        )
        Text(
            "按遥控器 ENTER 搜索（示例：星际 / 长安 / 海贼 / 舌尖）",
            color = TextGray,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> LoadingBox()
                error != null -> ErrorBox(error ?: "", onRetry = { doSearch() })
                keyword.isNotBlank() && results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有找到「$keyword」", color = TextGray, fontSize = 16.sp)
                }
                else -> LazyColumn {
                    item {
                        PosterRow(title = "搜索结果", vods = results, onVodClick = onVodClick)
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
