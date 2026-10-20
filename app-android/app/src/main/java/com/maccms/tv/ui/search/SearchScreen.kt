package com.maccms.tv.ui.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.maccms.tv.data.Vod
import com.maccms.tv.data.VodRepository
import com.maccms.tv.ui.components.ErrorBox
import com.maccms.tv.ui.components.LoadingBox
import com.maccms.tv.ui.components.PosterRow
import com.maccms.tv.ui.theme.NetflixBlack
import com.maccms.tv.ui.theme.NetflixRed
import com.maccms.tv.ui.theme.TextGray
import com.maccms.tv.ui.theme.TextWhite
import com.maccms.tv.util.SearchHistoryStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 搜索页：关键词输入（遥控器键盘）+ 最近搜索历史 + 结果行 */
@Composable
fun SearchScreen(
    repository: VodRepository,
    onVodClick: (Vod) -> Unit
) {
    val context = LocalContext.current
    var keyword by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Vod>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(SearchHistoryStore.get(context)) }
    // 搜索序号：丢弃被新输入/新搜索取代的旧响应
    var searchSeq by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val fieldFocus = remember { FocusRequester() }
    val historyFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }

    fun runSearch(raw: String) {
        val kw = raw.trim()
        if (kw.isEmpty()) return
        keyword = kw
        searched = true
        loading = true
        error = null
        results = emptyList()
        history = SearchHistoryStore.add(context, kw)
        val seq = ++searchSeq
        scope.launch {
            repository.search(kw).onSuccess {
                if (seq == searchSeq) {
                    results = it
                    loading = false
                }
            }.onFailure {
                if (seq == searchSeq) {
                    error = it.message
                    loading = false
                }
            }
        }
    }

    fun doSearch() = runSearch(keyword)

    // 进入页面：有历史时焦点落在第一条历史（不自动弹键盘），否则落在输入框
    LaunchedEffect(Unit) {
        repeat(5) {
            delay(60)
            val target = if (history.isNotEmpty()) historyFocus else fieldFocus
            if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    // 搜索结束移交焦点：历史行已消失，不处理会丢焦点导致遥控器失灵
    LaunchedEffect(loading, searched, results) {
        if (loading || !searched) return@LaunchedEffect
        if (results.isNotEmpty()) {
            repeat(5) {
                delay(60)
                if (runCatching { firstResultFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            }
        } else {
            runCatching { fieldFocus.requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize().background(NetflixBlack).padding(horizontal = 40.dp)) {
        Spacer(Modifier.height(48.dp))
        Text("搜索", color = TextWhite, fontSize = 26.sp, modifier = Modifier.padding(bottom = 16.dp))
        TextField(
            value = keyword,
            onValueChange = {
                keyword = it
                // 关键词变了，旧结果不再对应；回到历史/提示状态
                if (searched) {
                    searchSeq++
                    searched = false
                    loading = false
                    results = emptyList()
                    error = null
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(fieldFocus)
                .onKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown &&
                        e.key == Key.DirectionDown && history.isNotEmpty()
                    ) {
                        historyFocus.requestFocus()
                        true
                    } else false
                },
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
            "输入片名后按 ENTER 搜索（示例：星际 / 长安 / 海贼 / 舌尖）",
            color = TextGray,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> LoadingBox()
                error != null -> ErrorBox(error ?: "", onRetry = { doSearch() })
                !searched -> SearchHistoryPanel(
                    history = history,
                    firstFocus = historyFocus,
                    onPick = { runSearch(it) },
                    onClear = {
                        SearchHistoryStore.clear(context)
                        history = emptyList()
                        scope.launch { runCatching { fieldFocus.requestFocus() } }
                    }
                )
                results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有找到「$keyword」", color = TextGray, fontSize = 16.sp)
                }
                else -> LazyColumn {
                    item {
                        PosterRow(
                            title = "搜索结果",
                            vods = results,
                            onVodClick = onVodClick,
                            focusRequester = firstResultFocus
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

/** 最近搜索：横向关键词条 + 清空历史，按 OK 直接重新搜索 */
@Composable
private fun SearchHistoryPanel(
    history: List<String>,
    firstFocus: FocusRequester,
    onPick: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(top = 4.dp)) {
        if (history.isEmpty()) {
            Text(
                "暂无搜索历史：按方向键上移选中输入框，按 OK 呼出键盘输入片名",
                color = TextGray,
                fontSize = 15.sp
            )
            return@Column
        }
        Text(
            "最近搜索",
            color = TextWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 14.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(history, key = { _, kw -> kw }) { index, kw ->
                HistoryChip(
                    text = kw,
                    focusRequester = if (index == 0) firstFocus else null,
                    onClick = { onPick(kw) }
                )
            }
            item {
                HistoryChip(text = "清空历史", muted = true, onClick = onClear)
            }
        }
    }
}

/** 历史关键词条：聚焦放大 + 白描边，风格与详情页选集格子一致 */
@Composable
private fun HistoryChip(
    text: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    muted: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f),
        label = "historyChipScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) TextWhite else Color.Transparent,
        label = "historyChipBorder"
    )
    Box(
        Modifier
            .height(48.dp)
            .scale(scale)
            .zIndex(if (focused) 2f else 0f)
            .clip(RoundedCornerShape(24.dp))
            .background(if (focused) NetflixRed.copy(alpha = 0.35f) else Color(0xFF2A2A2A))
            .border(2.dp, borderColor, RoundedCornerShape(24.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (muted) TextGray else TextWhite,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
