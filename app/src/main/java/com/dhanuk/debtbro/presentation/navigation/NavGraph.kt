package com.dhanuk.debtbro.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.dhanuk.debtbro.data.ads.AdManager
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.presentation.screens.adddebt.AddDebtBottomSheet
import com.dhanuk.debtbro.presentation.screens.analytics.AnalyticsScreen
import com.dhanuk.debtbro.presentation.screens.auth.SignUpScreen
import com.dhanuk.debtbro.presentation.screens.auth.SignInScreen
import com.dhanuk.debtbro.presentation.screens.dashboard.DashboardScreen
import com.dhanuk.debtbro.presentation.screens.debtdetail.DebtDetailScreen
import com.dhanuk.debtbro.presentation.screens.debtlist.DebtListScreen
import com.dhanuk.debtbro.presentation.screens.onboarding.OnboardingScreen
import com.dhanuk.debtbro.presentation.screens.settings.SettingsScreen
import com.dhanuk.debtbro.presentation.screens.split.SplitScreen
import com.dhanuk.debtbro.presentation.theme.LocalExtraColors
import com.dhanuk.debtbro.util.LocalizedString
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.CircularProgressIndicator
import com.google.android.gms.ads.AdView
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtBroNavGraph(appPreferences: AppPreferences, adManager: AdManager) {
    val navController = rememberNavController()
    var showAddDebt by remember { mutableStateOf(false) }

    var startDestination by remember { mutableStateOf<String?>(null) }
    var selectedLanguage by remember { mutableStateOf("en") }

    LaunchedEffect(Unit) {
        // Read and set language
        selectedLanguage = appPreferences.selectedLanguage.first()
        LocalizedString.setLanguage(selectedLanguage)
        // Listen for language changes
        appPreferences.selectedLanguage.collect { code ->
            selectedLanguage = code
            LocalizedString.setLanguage(code)
        }
    }

    LaunchedEffect(Unit) {
        startDestination = if (appPreferences.hasCompletedOnboarding.first()) {
            Screen.Dashboard.route
        } else {
            Screen.Onboarding.route
        }
    }
    
    if (startDestination == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }
    
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val extra = LocalExtraColors.current
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
                    // ── Banner ad (2026-07-04, Wave 5 Issue 21 3B.1) ─────────────
                    // 320×50 banner AdView mounted in a Column above the bottom
                    // NavigationBar so it persists across all 4 tabs without
                    // blocking the navigation surface. The AdView's lifecycle
                    // (pause / resume / destroy) is wired to the Compose
                    // LocalLifecycleOwner via the LifecycleEventObserver below.
                    // AdManager returns null in release when the banner ad-unit
                    // ID isn't configured; in that case the slot collapses (no
                    // empty box, no white gap).
                    BannerAdSlot(adManager = adManager)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
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
                                    contentDescription = LocalizedString.get("nav_home")
                                )
                            },
                            label = { Text(LocalizedString.get("nav_home")) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                unselectedIconColor = extra.subtitleGray,
                                unselectedTextColor = extra.subtitleGray
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
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (currentRoute == Screen.DebtList.route)
                                        Icons.Filled.AccountBalanceWallet
                                    else Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = LocalizedString.get("nav_debts")
                                )
                            },
                            label = { Text(LocalizedString.get("nav_debts")) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                unselectedIconColor = extra.subtitleGray,
                                unselectedTextColor = extra.subtitleGray
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
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    Icons.Default.CallSplit,
                                    contentDescription = LocalizedString.get("nav_split")
                                )
                            },
                            label = { Text(LocalizedString.get("nav_split")) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                unselectedIconColor = extra.subtitleGray,
                                unselectedTextColor = extra.subtitleGray
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
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (currentRoute == Screen.Analytics.route)
                                        Icons.Filled.BarChart
                                    else Icons.Outlined.BarChart,
                                    contentDescription = LocalizedString.get("nav_stats")
                                )
                            },
                            label = { Text(LocalizedString.get("nav_stats")) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                unselectedIconColor = extra.subtitleGray,
                                unselectedTextColor = extra.subtitleGray
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        val startDest = startDestination ?: return@Scaffold
        NavHost(
            navController = navController,
            startDestination = startDest,
            modifier = Modifier.padding(paddingValues),
            enterTransition = { fadeIn() + slideInHorizontally { it / 3 } },
            exitTransition = { fadeOut() + slideOutHorizontally { -it / 3 } },
            popEnterTransition = { fadeIn() + slideInHorizontally { -it / 3 } },
            popExitTransition = { fadeOut() + slideOutHorizontally { it / 3 } }
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onOnboardingComplete = {
                        navController.navigate(Screen.SignUp.route) {
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
            composable(Screen.Split.route) { SplitScreen(onAuthRequired = { navController.navigate(Screen.SignIn.route) }) }
            composable(Screen.Analytics.route) { AnalyticsScreen() }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onRequireAuth = {
                        navController.navigate(Screen.SignIn.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(
                Screen.DebtDetail.route,
                arguments = listOf(navArgument("debtId") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "debtbro://debt/{debtId}" })
            ) {
                DebtDetailScreen(
                    onBack = { navController.popBackStack() },
                    onAuthRequired = { navController.navigate(Screen.SignIn.route) }
                )
            }
            composable(Screen.SignUp.route) {
                SignUpScreen(
                    onAuthComplete = { navController.popBackStack() },
                    onNavigateToSignIn = { navController.navigate(Screen.SignIn.route) { launchSingleTop = true } },
                    onSkip = { navController.popBackStack() }
                )
            }
            composable(Screen.SignIn.route) {
                SignInScreen(
                    onAuthComplete = { navController.popBackStack() },
                    onNavigateToSignUp = { navController.navigate(Screen.SignUp.route) { launchSingleTop = true } },
                    onSkip = { navController.popBackStack() }
                )
            }
        }
    }
    
    if (showAddDebt) {
        AddDebtBottomSheet(
            onDismiss = { showAddDebt = false },
            onDebtAdded = { showAddDebt = false },
            onSignInRequired = { navController.navigate(Screen.SignIn.route) }
        )
    }
}

/**
 * Banner ad slot — mounted above the bottom NavigationBar in NavGraph.
 *
 * Lifecycle wiring: the AdView requires `pause()`, `resume()`, and
 * `destroy()` calls on the host lifecycle events to be a good app citizen
 * (otherwise ads keep doing background work after the user backgrounds the
 * app). We hook into [LocalLifecycleOwner]'s ON_PAUSE / ON_RESUME /
 * ON_DESTROY events and dispatch the corresponding AdView method.
 *
 * The AdView instance is held in `remember` so it survives recompositions
 * but is keyed on the Activity instance (so a configuration change that
 * recreates the Activity gets a new AdView).
 *
 * Returns nothing if AdManager has no banner ad-unit configured (release
 * builds without ADMOB_BANNER_ID), collapsing the slot.
 */
@Composable
private fun BannerAdSlot(adManager: AdManager) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Construct the AdView once per Activity instance. Keyed on context so
    // a configuration change that recreates the Activity gets a fresh AdView
    // (the old one's destroy() runs via onDispose below).
    var adView by remember(context) { mutableStateOf<AdView?>(adManager.createBannerAdView(context)) }

    val currentAdView = adView
    if (currentAdView != null) {
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> currentAdView.pause()
                    Lifecycle.Event.ON_RESUME -> currentAdView.resume()
                    Lifecycle.Event.ON_DESTROY -> currentAdView.destroy()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                currentAdView.destroy()
            }
        }
        AndroidView(
            factory = { currentAdView },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
