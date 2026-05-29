package com.painhunt.desktop

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.painhunt.data.BookmarksRepository
import com.painhunt.data.ChatRepository
import com.painhunt.data.IdeasRepository
import com.painhunt.data.ImplementationsRepository
import com.painhunt.data.SettingsRepository
import com.painhunt.data.SourcesRepository
import com.painhunt.desktop.platform.rememberIsOnline
import com.painhunt.desktop.theme.PainHuntTheme
import com.painhunt.presentation.FeedViewModel
import com.painhunt.presentation.IdeaChatViewModel
import com.painhunt.presentation.IdeaDetailViewModel
import com.painhunt.presentation.ImplementingDetailViewModel
import com.painhunt.presentation.ImplementingListViewModel
import com.painhunt.presentation.SettingsViewModel
import com.painhunt.presentation.SourcesViewModel
import com.painhunt.ui.detail.IdeaDetailScreen
import com.painhunt.ui.feed.FeedScreen
import com.painhunt.ui.implementing.ImplementingDetailScreen
import com.painhunt.ui.implementing.ImplementingListScreen
import com.painhunt.ui.offline.OfflineScreen
import com.painhunt.ui.settings.SettingsScreen
import com.painhunt.ui.sources.SourcesScreen
import kotlinx.serialization.Serializable

@Serializable object FeedRoute
@Serializable object SourcesRoute
@Serializable object SettingsRoute
@Serializable data class DetailRoute(val ideaId: String)
@Serializable object ImplementingRoute
@Serializable data class ImplementingDetailRoute(val implementationId: String)

class AppRepositories(
    val ideas: IdeasRepository,
    val bookmarks: BookmarksRepository,
    val chat: ChatRepository,
    val sources: SourcesRepository,
    val settings: SettingsRepository,
    val implementations: ImplementationsRepository,
)

@Composable
fun App(repos: AppRepositories) {
    PainHuntTheme {
        val navController = rememberNavController()
        val (isOnline, retryConnection) = rememberIsOnline()
        if (!isOnline) {
            OfflineScreen(onRetry = retryConnection)
            return@PainHuntTheme
        }
        val backStack by navController.currentBackStackEntryAsState()

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = backStack?.destination?.hasRoute(FeedRoute::class) == true,
                        onClick = { navController.navigate(FeedRoute) { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("Feed") },
                    )
                    NavigationBarItem(
                        selected = backStack?.destination?.hasRoute(ImplementingRoute::class) == true,
                        onClick = { navController.navigate(ImplementingRoute) { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.Build, null) },
                        label = { Text("Implementing") },
                    )
                }
            }
        ) { innerPadding ->
            NavHost(
                navController,
                startDestination = FeedRoute,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable<FeedRoute> {
                    val vm = viewModel { FeedViewModel(repos.ideas, repos.settings) }
                    FeedScreen(
                        viewModel = vm,
                        onIdeaClick = { ideaId -> navController.navigate(DetailRoute(ideaId)) },
                        onSources = { navController.navigate(SourcesRoute) { launchSingleTop = true } },
                        onSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                    )
                }
                composable<DetailRoute> { entry ->
                    val route = entry.toRoute<DetailRoute>()
                    val vm = viewModel { IdeaDetailViewModel(repos.ideas, repos.bookmarks, repos.settings, repos.implementations) }
                    val chatVm = viewModel { IdeaChatViewModel(repos.chat, repos.settings) }
                    IdeaDetailScreen(
                        ideaId = route.ideaId,
                        viewModel = vm,
                        chatViewModel = chatVm,
                        onBack = { navController.popBackStack() },
                        onNavigateToImplementation = { implId -> navController.navigate(ImplementingDetailRoute(implId)) },
                    )
                }
                composable<SourcesRoute> {
                    val vm = viewModel { SourcesViewModel(repos.sources) }
                    SourcesScreen(vm, onBack = { navController.popBackStack() })
                }
                composable<SettingsRoute> {
                    val vm = viewModel { SettingsViewModel(repos.settings) }
                    SettingsScreen(vm, onBack = { navController.popBackStack() })
                }
                composable<ImplementingRoute> {
                    val vm = viewModel { ImplementingListViewModel(repos.implementations) }
                    ImplementingListScreen(vm) { id -> navController.navigate(ImplementingDetailRoute(id)) }
                }
                composable<ImplementingDetailRoute> { entry ->
                    val route = entry.toRoute<ImplementingDetailRoute>()
                    val vm = viewModel { ImplementingDetailViewModel(repos.implementations, repos.ideas) }
                    ImplementingDetailScreen(route.implementationId, vm) { navController.popBackStack() }
                }
            }
        }
    }
}
