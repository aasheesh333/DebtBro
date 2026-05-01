package com.dhanuk.debtbro.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.presentation.screens.adddebt.AddDebtScreen
import com.dhanuk.debtbro.presentation.screens.analytics.AnalyticsScreen
import com.dhanuk.debtbro.presentation.screens.dashboard.DashboardScreen
import com.dhanuk.debtbro.presentation.screens.debtdetail.DebtDetailScreen
import com.dhanuk.debtbro.presentation.screens.debtlist.DebtListScreen
import com.dhanuk.debtbro.presentation.screens.onboarding.OnboardingScreen
import com.dhanuk.debtbro.presentation.screens.settings.SettingsScreen
import com.dhanuk.debtbro.presentation.screens.split.SplitScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtBroNavGraph(appPreferences: AppPreferences) {
    val nav = rememberNavController()
    var showAddDebt by remember { mutableStateOf(false) }
    val start = if (runBlocking { appPreferences.hasCompletedOnboarding.first() }) Screen.Dashboard.route else Screen.Onboarding.route
    val tabs = listOf(Screen.Dashboard to "Home", Screen.DebtList to "Debts", Screen.Split to "Split", Screen.Analytics to "Stats")
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    Scaffold(bottomBar = {
        if (route in tabs.map { it.first.route }) {
            NavigationBar {
                tabs.forEach { (screen, label) ->
                    NavigationBarItem(selected = route == screen.route, onClick = { nav.navigate(screen.route) { launchSingleTop = true; popUpTo(Screen.Dashboard.route) { saveState = true }; restoreState = true } }, icon = { androidx.compose.material3.Icon(when (screen) { Screen.Dashboard -> Icons.Default.Home; Screen.DebtList -> Icons.Default.List; Screen.Split -> Icons.Default.Settings; else -> Icons.Default.PieChart }, null) }, label = { Text(label) })
                }
            }
        }
    }) { padding ->
        NavHost(navController = nav, startDestination = start, modifier = Modifier.padding(padding)) {
            composable(Screen.Onboarding.route) { OnboardingScreen(onOnboardingComplete = { nav.navigate(Screen.Dashboard.route) { popUpTo(Screen.Onboarding.route) { inclusive = true } } }) }
            composable(Screen.Dashboard.route) { DashboardScreen({ showAddDebt = true }, { nav.navigate(Screen.DebtList.route) }, { nav.navigate(Screen.Split.route) }, { nav.navigate(Screen.Settings.route) }, { nav.navigate(Screen.DebtDetail.createRoute(it)) }) }
            composable(Screen.DebtList.route) { DebtListScreen({ showAddDebt = true }, { nav.navigate(Screen.DebtDetail.createRoute(it)) }) }
            composable(Screen.Split.route) { SplitScreen() }
            composable(Screen.Analytics.route) { AnalyticsScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable(Screen.DebtDetail.route, arguments = listOf(navArgument("debtId") { type = NavType.IntType }), deepLinks = listOf(navDeepLink { uriPattern = "debtbro://debt/{debtId}" })) { DebtDetailScreen(onBack = { nav.popBackStack() }) }
        }
    }
    if (showAddDebt) ModalBottomSheet(onDismissRequest = { showAddDebt = false }) { AddDebtScreen(onSaved = { showAddDebt = false }) }
}
