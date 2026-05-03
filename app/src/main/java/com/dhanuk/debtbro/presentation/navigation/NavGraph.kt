package com.dhanuk.debtbro.presentation.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.presentation.screens.adddebt.AddDebtBottomSheet
import com.dhanuk.debtbro.presentation.screens.analytics.AnalyticsScreen
import com.dhanuk.debtbro.presentation.screens.dashboard.DashboardScreen
import com.dhanuk.debtbro.presentation.screens.debtdetail.DebtDetailScreen
import com.dhanuk.debtbro.presentation.screens.debtlist.DebtListScreen
import com.dhanuk.debtbro.presentation.screens.onboarding.OnboardingScreen
import com.dhanuk.debtbro.presentation.screens.settings.SettingsScreen
import com.dhanuk.debtbro.presentation.screens.split.SplitScreen
import com.dhanuk.debtbro.presentation.theme.PrimaryGreen
import com.dhanuk.debtbro.presentation.theme.SubtitleGray
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtBroNavGraph(appPreferences: AppPreferences) {
    val navController = rememberNavController()
    var showAddDebt by remember { mutableStateOf(false) }
    
    val startDestination = if (runBlocking { appPreferences.hasCompletedOnboarding.first() }) {
        Screen.Dashboard.route
    } else {
        Screen.Onboarding.route
    }
    
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val navTabs = listOf(
        Screen.Dashboard,
        Screen.DebtList,
        Screen.Split,
        Screen.Analytics
    )

    val navTabRoutes = navTabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (currentRoute in navTabRoutes) {
                Column {
                    NavigationBar(
                        containerColor = Color(0xFF111111),
                        tonalElevation = 0.dp
                    ) {
                        // Tab 1: Home
                        NavigationBarItem(
                            selected = currentRoute == Screen.Dashboard.route,
                            onClick = { 
                                navController.navigate(Screen.Dashboard.route) {
                                    popUpTo(Screen.Dashboard.route) {
                                        inclusive = false
                                        saveState = false
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { 
                                Icon(
                                    if (currentRoute == Screen.Dashboard.route) 
                                        Icons.Filled.Home 
                                    else Icons.Outlined.Home,
                                    contentDescription = "Home"
                                )
                            },
                            label = { Text("Home") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryGreen,
                                selectedTextColor = PrimaryGreen,
                                indicatorColor = PrimaryGreen.copy(alpha = 0.15f),
                                unselectedIconColor = SubtitleGray,
                                unselectedTextColor = SubtitleGray
                            )
                        )
                        
                        // Tab 2: Debts
                        NavigationBarItem(
                            selected = currentRoute == Screen.DebtList.route,
                            onClick = { 
                                navController.navigate(Screen.DebtList.route) {
                                    popUpTo(Screen.DebtList.route) {
                                        inclusive = false
                                        saveState = false
                                    }
                                    launchSingleTop = true
                                }
                            },
                            icon = { 
                                Icon(
                                    if (currentRoute == Screen.DebtList.route) 
                                        Icons.Filled.AccountBalanceWallet 
                                    else Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = "Debts"
                                )
                            },
                            label = { Text("Debts") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryGreen,
                                selectedTextColor = PrimaryGreen,
                                indicatorColor = PrimaryGreen.copy(alpha = 0.15f),
                                unselectedIconColor = SubtitleGray,
                                unselectedTextColor = SubtitleGray
                            )
                        )
                        
                        // Tab 3: Split
                        NavigationBarItem(
                            selected = currentRoute == Screen.Split.route,
                            onClick = { 
                                navController.navigate(Screen.Split.route) {
                                    popUpTo(Screen.Split.route) {
                                        inclusive = false
                                        saveState = false
                                    }
                                    launchSingleTop = true
                                }
                            },
                            icon = { 
                                Icon(
                                    Icons.Default.CallSplit,
                                    contentDescription = "Split"
                                )
                            },
                            label = { Text("Split") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryGreen,
                                selectedTextColor = PrimaryGreen,
                                indicatorColor = PrimaryGreen.copy(alpha = 0.15f),
                                unselectedIconColor = SubtitleGray,
                                unselectedTextColor = SubtitleGray
                            )
                        )
                        
                        // Tab 4: Stats
                        NavigationBarItem(
                            selected = currentRoute == Screen.Analytics.route,
                            onClick = { 
                                navController.navigate(Screen.Analytics.route) {
                                    popUpTo(Screen.Analytics.route) {
                                        inclusive = false
                                        saveState = false
                                    }
                                    launchSingleTop = true
                                }
                            },
                            icon = { 
                                Icon(
                                    if (currentRoute == Screen.Analytics.route) 
                                        Icons.Filled.BarChart 
                                    else Icons.Outlined.BarChart,
                                    contentDescription = "Stats"
                                )
                            },
                            label = { Text("Stats") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryGreen,
                                selectedTextColor = PrimaryGreen,
                                indicatorColor = PrimaryGreen.copy(alpha = 0.15f),
                                unselectedIconColor = SubtitleGray,
                                unselectedTextColor = SubtitleGray
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onOnboardingComplete = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Dashboard.route) { 
                DashboardScreen(
                    onAddDebtClick = { showAddDebt = true },
                    onSeeAllClick = { navController.navigate(Screen.DebtList.route) },
                    onSplitClick = { navController.navigate(Screen.Split.route) },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                    onDebtClick = { debtId -> navController.navigate(Screen.DebtDetail.createRoute(debtId)) }
                )
            }
            composable(Screen.DebtList.route) { 
                DebtListScreen(
                    onAddDebtClick = { showAddDebt = true },
                    onDebtClick = { debtId -> navController.navigate(Screen.DebtDetail.createRoute(debtId)) }
                )
            }
            composable(Screen.Split.route) { SplitScreen() }
            composable(Screen.Analytics.route) { AnalyticsScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable(
                Screen.DebtDetail.route,
                arguments = listOf(navArgument("debtId") { type = NavType.IntType }),
                deepLinks = listOf(navDeepLink { uriPattern = "debtbro://debt/{debtId}" })
            ) { 
                DebtDetailScreen(onBack = { navController.popBackStack() }) 
            }
        }
    }
    
    if (showAddDebt) {
        AddDebtBottomSheet(
            onDismiss = { showAddDebt = false },
            onDebtAdded = { showAddDebt = false }
        )
    }
}
