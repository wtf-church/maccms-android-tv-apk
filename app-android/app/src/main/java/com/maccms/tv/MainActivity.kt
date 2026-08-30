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
import com.maccms.tv.ui.detail.DetailScreen
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
                }
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
        composable("settings") {
            SettingsScreen(onDone = { navigateToTop(navController, "home") })
        }
        composable("detail/{vodId}") { backStackEntry ->
            val vodId = backStackEntry.arguments?.getString("vodId")?.toIntOrNull() ?: 0
            DetailScreen(
                vodId = vodId,
                repository = repository,
                onBack = { navController.popBackStack() },
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
