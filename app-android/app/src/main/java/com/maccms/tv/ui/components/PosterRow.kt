package com.maccms.tv.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maccms.tv.data.Vod
import com.maccms.tv.ui.theme.TextWhite

/** Netflix 风格横向海报行：标题 + LazyRow 海报卡片 */
@Composable
fun PosterRow(
    title: String,
    vods: List<Vod>,
    onVodClick: (Vod) -> Unit,
    // 可选：把焦点交给第一张海报（搜索完成后聚焦结果用）
    focusRequester: FocusRequester? = null
) {
    Spacer(Modifier.height(12.dp))
    Text(
        text = title,
        color = TextWhite,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 40.dp, bottom = 10.dp)
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 40.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(vods, key = { _, v -> v.vod_id + title.hashCode() }) { index, vod ->
            PosterCard(
                vod = vod,
                onClick = { onVodClick(vod) },
                focusRequester = if (index == 0) focusRequester else null
            )
        }
    }
}

