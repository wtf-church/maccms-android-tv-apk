package com.maccms.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.maccms.tv.data.Episode
import com.maccms.tv.data.Vod
import com.maccms.tv.data.VodRepository
import com.maccms.tv.ui.browse.BrowseScreen
import com.maccms.tv.ui.browse.BrowseState
import com.maccms.tv.ui.detail.DetailScreen
import com.maccms.tv.ui.favorites.FavoritesScreen
import com.maccms.tv.ui.history.HistoryScreen
import com.maccms.tv.ui.home.HomeScreen
import com.maccms.tv.ui.player.PlayerScreen
import com.maccms.tv.ui.search.SearchScreen
import com.maccms.tv.ui.settings.SettingsScreen
import com.maccms.tv.ui.theme.MaccmsTvTheme
import com.maccms.tv.ui.theme.NetflixBlack

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaccmsTvTheme {
                TvApp()
            }
        }
    }
}

@Composable
fun TvApp() {
    val context = LocalContext.current
    val repository = remember { VodRepository(context) }
    val navController = rememberNavController()
    // 浏览页状态提升到 NavHost 外层：去详情再返回时，分类选择/列表/滚动位置都保留
    val browseState = remember { BrowseState() }

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = Modifier.fillMaxSize().background(NetflixBlack)
    ) {
        composable("home") {
            HomeScreen(
                repository = repository,
                onVodClick = { vod ->
                    cacheVod(vod)
                    navController.navigate("detail/${vod.vod_id}")
                },
                onPlayClick = { vod ->
                    cacheVod(vod)
                    clearCachedEpisode(vod.vod_id)
                    navController.navigate("player/${vod.vod_id}")
                },
                onNavigate = { route -> navigateToTop(navController, route) }
            )
        }
        composable("browse") {
            BrowseScreen(
                repository = repository,
                onVodClick = { vod ->
                    cacheVod(vod)
                    navController.navigate("detail/${vod.vod_id}")
                },
                state = browseState
            )
        }
        composable("search") {
            SearchScreen(
                repository = repository,
                onVodClick = { vod ->
                    cacheVod(vod)
                    navController.navigate("detail/${vod.vod_id}")
                }
            )
        }
        composable("history") {
            HistoryScreen(
                // 历史行 OK 直接续播：不清空点选缓存，播放页按观看记录回到上次位置
                onPlay = { vod ->
                    cacheVod(vod)
                    clearCachedEpisode(vod.vod_id)
                    navController.navigate("player/${vod.vod_id}")
                }
            )
        }
        composable("favorites") {
            FavoritesScreen(
                onOpenDetail = { vod ->
                    cacheVod(vod)
                    navController.navigate("detail/${vod.vod_id}")
                }
            )
        }
        composable("settings") {
            SettingsScreen(onDone = { navigateToTop(navController, "home") })
        }
        composable("detail/{vodId}") { backStackEntry ->
            val vodId = backStackEntry.arguments?.getString("vodId")?.toIntOrNull() ?: 0
            DetailScreen(
                vodId = vodId,
                repository = repository,
                onBack = { navController.popBackStack() },
                // 默认播放按钮：不指定集数，由播放页按观看记录续播
                onPlay = { vod ->
                    cacheVod(vod)
                    clearCachedEpisode(vod.vod_id)
                    navController.navigate("player/${vod.vod_id}")
                },
                onPlayEpisode = { vod, ep ->
                    cacheVod(vod)
                    cacheEpisode(vod.vod_id, ep)
                    navController.navigate("player/${vod.vod_id}")
                }
            )
        }
        composable("player/{vodId}") { entry ->
            val vodId = entry.arguments?.getString("vodId")?.toIntOrNull() ?: 0
            PlayerScreen(
                vod = cachedVod(vodId),
                episode = cachedEpisode(vodId),
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

private val savedVodCache = mutableMapOf<Int, Vod>()
private val savedEpisodeCache = mutableMapOf<Int, Episode>()

private fun cacheVod(vod: Vod) {
    savedVodCache[vod.vod_id] = vod
}

private fun cacheEpisode(vodId: Int, ep: Episode) {
    savedEpisodeCache[vodId] = ep
}

/** 从「播放/继续观看」入口进入时清掉上次点选的集，让播放页回落到续播逻辑 */
private fun clearCachedEpisode(vodId: Int) {
    savedEpisodeCache.remove(vodId)
}

private fun cachedEpisode(vodId: Int): Episode? = savedEpisodeCache[vodId]

private fun cachedVod(vodId: Int): Vod =
    savedVodCache[vodId] ?: Vod(vod_id = vodId, vod_name = "影片 $vodId")

private fun navigateToTop(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
