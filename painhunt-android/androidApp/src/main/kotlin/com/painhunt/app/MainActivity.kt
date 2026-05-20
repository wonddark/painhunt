package com.painhunt.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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
import com.painhunt.data.IdeasRepository
import com.painhunt.data.SettingsRepository
import com.painhunt.data.SubredditsRepository
import com.painhunt.data.SupabaseClientProvider
import com.painhunt.presentation.FeedViewModel
import com.painhunt.presentation.IdeaDetailViewModel
import com.painhunt.presentation.SettingsViewModel
import com.painhunt.presentation.SubredditsViewModel
import com.painhunt.ui.detail.IdeaDetailScreen
import com.painhunt.ui.feed.FeedScreen
import com.painhunt.ui.settings.SettingsScreen
import com.painhunt.ui.subreddits.SubredditsScreen
import com.painhunt.ui.theme.PainHuntTheme
import kotlinx.serialization.Serializable

@Serializable object FeedRoute
@Serializable object SubredditsRoute
@Serializable object SettingsRoute
@Serializable data class DetailRoute(val ideaId: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val supabase = SupabaseClientProvider.create(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
        val ideasRepo = IdeasRepository(supabase)
        val bookmarksRepo = BookmarksRepository(supabase)
        val subredditsRepo = SubredditsRepository(supabase)
        val settingsRepo = SettingsRepository(supabase)

        setContent {
            PainHuntTheme {
                val navController = rememberNavController()
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
                                selected = backStack?.destination?.hasRoute(SubredditsRoute::class) == true,
                                onClick = { navController.navigate(SubredditsRoute) { launchSingleTop = true } },
                                icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                                label = { Text("Subreddits") },
                            )
                            NavigationBarItem(
                                selected = backStack?.destination?.hasRoute(SettingsRoute::class) == true,
                                onClick = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.Settings, null) },
                                label = { Text("Settings") },
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
                            val vm = viewModel { FeedViewModel(ideasRepo, settingsRepo) }
                            FeedScreen(vm) { ideaId -> navController.navigate(DetailRoute(ideaId)) }
                        }
                        composable<DetailRoute> { entry ->
                            val route = entry.toRoute<DetailRoute>()
                            val vm = viewModel { IdeaDetailViewModel(ideasRepo, bookmarksRepo) }
                            IdeaDetailScreen(route.ideaId, vm) { navController.popBackStack() }
                        }
                        composable<SubredditsRoute> {
                            val vm = viewModel { SubredditsViewModel(subredditsRepo) }
                            SubredditsScreen(vm)
                        }
                        composable<SettingsRoute> {
                            val vm = viewModel { SettingsViewModel(settingsRepo) }
                            SettingsScreen(vm)
                        }
                    }
                }
            }
        }
    }
}
