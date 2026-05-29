package com.painhunt.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.padding
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
import com.painhunt.data.SupabaseClientProvider
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
import com.painhunt.ui.settings.SettingsScreen
import com.painhunt.ui.offline.OfflineScreen
import com.painhunt.ui.sources.SourcesScreen
import com.painhunt.ui.theme.PainHuntTheme
import androidx.compose.material.icons.filled.Build
import kotlinx.serialization.Serializable

@Serializable object FeedRoute
@Serializable object SourcesRoute
@Serializable object SettingsRoute
@Serializable data class DetailRoute(val ideaId: String)
@Serializable object ImplementingRoute
@Serializable data class ImplementingDetailRoute(val implementationId: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val supabase = SupabaseClientProvider.create(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
        val ideasRepo = IdeasRepository(supabase)
        val bookmarksRepo = BookmarksRepository(supabase)
        val chatRepo = ChatRepository(supabase)
        val sourcesRepo = SourcesRepository(supabase)
        val settingsRepo = SettingsRepository(supabase)
        val implementationsRepo = ImplementationsRepository(supabase)

        setContent {
            PainHuntTheme {
                val (isOnline, retryConnection) = rememberIsOnline()
                if (!isOnline) {
                    OfflineScreen(onRetry = retryConnection)
                    return@PainHuntTheme
                }

                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()

                // The detail screen hides the bottom bar to use the full height.
                val showBottomBar = backStack?.destination?.hasRoute(DetailRoute::class) != true

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
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
                    }
                ) { innerPadding ->
                    NavHost(
                        navController,
                        startDestination = FeedRoute,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable<FeedRoute> {
                            val vm = viewModel { FeedViewModel(ideasRepo, settingsRepo) }
                            FeedScreen(
                                viewModel = vm,
                                onIdeaClick = { ideaId -> navController.navigate(DetailRoute(ideaId)) },
                                onSources = { navController.navigate(SourcesRoute) { launchSingleTop = true } },
                                onSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                            )
                        }
                        composable<DetailRoute> { entry ->
                            val route = entry.toRoute<DetailRoute>()
                            val vm = viewModel { IdeaDetailViewModel(ideasRepo, bookmarksRepo, settingsRepo, implementationsRepo) }
                            val chatVm = viewModel { IdeaChatViewModel(chatRepo, settingsRepo) }
                            IdeaDetailScreen(
                                ideaId = route.ideaId,
                                viewModel = vm,
                                chatViewModel = chatVm,
                                onBack = { navController.popBackStack() },
                                onNavigateToImplementation = { implId -> navController.navigate(ImplementingDetailRoute(implId)) },
                            )
                        }
                        composable<SourcesRoute> {
                            val vm = viewModel { SourcesViewModel(sourcesRepo) }
                            SourcesScreen(vm, onBack = { navController.popBackStack() })
                        }
                        composable<SettingsRoute> {
                            val vm = viewModel { SettingsViewModel(settingsRepo) }
                            SettingsScreen(vm, onBack = { navController.popBackStack() })
                        }
                        composable<ImplementingRoute> {
                            val vm = viewModel { ImplementingListViewModel(implementationsRepo) }
                            ImplementingListScreen(vm) { id -> navController.navigate(ImplementingDetailRoute(id)) }
                        }
                        composable<ImplementingDetailRoute> { entry ->
                            val route = entry.toRoute<ImplementingDetailRoute>()
                            val vm = viewModel { ImplementingDetailViewModel(implementationsRepo, ideasRepo) }
                            ImplementingDetailScreen(route.implementationId, vm) { navController.popBackStack() }
                        }
                    }
                }
            }
        }
    }
}
