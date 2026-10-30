package com.github.blogshot.crtv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.github.blogshot.crtv.ui.theme.GlassNavItem as ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.blogshot.crtv.AppContainer
import com.github.blogshot.crtv.core.content.EpisodeCard
import com.github.blogshot.crtv.core.content.MediaCard
import com.github.blogshot.crtv.ui.home.HomeScreen
import com.github.blogshot.crtv.ui.home.HomeViewModel
import com.github.blogshot.crtv.ui.home.WatchlistScreen
import com.github.blogshot.crtv.ui.home.WatchlistViewModel
import com.github.blogshot.crtv.ui.player.PlayerPlaceholder
import com.github.blogshot.crtv.ui.search.SearchScreen
import com.github.blogshot.crtv.ui.search.SearchViewModel
import com.github.blogshot.crtv.ui.series.SeriesScreen
import com.github.blogshot.crtv.ui.series.SeriesViewModel
import com.github.blogshot.crtv.ui.theme.CrColors
import com.github.blogshot.crtv.ui.theme.glass
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch

private enum class Tab(val route: String, val label: String) {
    Home("home", "Start"),
    Watchlist("watchlist", "Watchlist"),
    Search("search", "Suche"),
}

/**
 * Seitenleiste + Navigation. Tabwechsel speichern und restaurieren den kompletten Back-Stack des Tabs
 * (ViewModels, Scrollposition). Ein Klick auf den aktiven Tab springt nach oben und aktualisiert.
 */
@Composable
fun AppShell(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val reselect = remember { mutableStateMapOf<Tab, Int>() }
    val scope = rememberCoroutineScope()

    val openSeries: (MediaCard) -> Unit = { nav.navigate("series/${it.id}") }
    val play: (EpisodeCard) -> Unit = { nav.navigate("player/${it.id}") }

    Row(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .width(220.dp)
                .fillMaxHeight()
                .glass(RoundedCornerShape(24.dp))
                .padding(vertical = 24.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Crunchyroll", style = MaterialTheme.typography.titleMedium, color = CrColors.Accent, modifier = Modifier.padding(12.dp))
            Tab.entries.forEach { tab ->
                val active = nav.isOnTab(tab, currentRoute)
                ListItem(
                    selected = active,
                    onClick = {
                        if (active && currentRoute == tab.route) {
                            reselect[tab] = (reselect[tab] ?: 0) + 1
                        } else {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    headlineContent = { Text(tab.label) },
                )
            }
            Box(Modifier.weight(1f))
            ListItem(
                selected = currentRoute == "profiles",
                onClick = { nav.navigate("profiles") { launchSingleTop = true } },
                headlineContent = { Text("Profil wechseln") },
            )
            ListItem(
                selected = false,
                onClick = { scope.launch { container.session.logout() } },
                headlineContent = { Text("Abmelden") },
            )
        }

        NavHost(nav, startDestination = Tab.Home.route, modifier = Modifier.weight(1f).fillMaxHeight()) {
            composable(Tab.Home.route) {
                HomeScreen(viewModel { HomeViewModel(container) }, reselect[Tab.Home] ?: 0, openSeries, play)
            }
            composable(Tab.Watchlist.route) {
                WatchlistScreen(viewModel { WatchlistViewModel(container) }, reselect[Tab.Watchlist] ?: 0, openSeries)
            }
            composable(Tab.Search.route) {
                SearchScreen(viewModel { SearchViewModel(container) }, openSeries)
            }
            composable("series/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                SeriesScreen(viewModel { SeriesViewModel(container, id) }, play)
            }
            composable("profiles") {
                ProfileScreen(
                    viewModel { ProfileViewModel(container) },
                    // Neues Profil: alle Tabs mit frischem Zustand neu aufbauen.
                    onDone = { nav.navigate(Tab.Home.route) { popUpTo(nav.graph.id) { inclusive = true } } },
                    onLogout = { scope.launch { container.session.logout() } },
                )
            }
            composable("player/{id}") { entry ->
                PlayerPlaceholder(entry.arguments?.getString("id").orEmpty())
            }
        }
    }
}

/** Der Tab, in dessen Back-Stack wir gerade sind (auch auf Serien- oder Player-Seiten). */
private fun NavHostController.isOnTab(tab: Tab, currentRoute: String?): Boolean {
    if (currentRoute == tab.route) return true
    val tabsInStack = currentBackStack.value.mapNotNull { it.destination.route }.filter { r -> Tab.entries.any { it.route == r } }
    return tabsInStack.lastOrNull() == tab.route
}
