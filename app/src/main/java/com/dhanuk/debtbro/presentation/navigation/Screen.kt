package com.dhanuk.debtbro.presentation.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Dashboard : Screen("dashboard")
    object DebtList : Screen("debt_list")
    object DebtDetail : Screen("debt_detail/{debtId}") {
        fun createRoute(debtId: Int) = "debt_detail/$debtId"
        fun createRoute(debtId: String) = "debt_detail/$debtId"
    }
    object Split : Screen("split")
    object Analytics : Screen("analytics")
    object Settings : Screen("settings")
    object Auth : Screen("auth")
}
