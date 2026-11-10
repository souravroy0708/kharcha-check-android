package com.kharchacheck.app.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kharchacheck.app.feature.ActivityScreen
import com.kharchacheck.app.feature.BudgetsScreen
import com.kharchacheck.app.feature.CategoriesScreen
import com.kharchacheck.app.feature.EditTxnScreen
import com.kharchacheck.app.feature.GrowScreen
import com.kharchacheck.app.feature.HomeScreen
import com.kharchacheck.app.feature.InboxScreen
import com.kharchacheck.app.feature.LockScreen
import com.kharchacheck.app.feature.OnboardingScreen
import com.kharchacheck.app.feature.PrivacyScreen
import com.kharchacheck.app.feature.RecurringScreen
import com.kharchacheck.app.feature.StatementsScreen
import com.kharchacheck.app.feature.ImportScreen
import com.kharchacheck.app.feature.BillsScreen
import com.kharchacheck.app.feature.DuesScreen
import com.kharchacheck.app.feature.WealthScreen
import com.kharchacheck.app.feature.GuideScreen
import com.kharchacheck.app.feature.WrappedScreen
import com.kharchacheck.app.feature.CategoryInsightsScreen
import com.kharchacheck.app.feature.ReportScreen
import com.kharchacheck.app.feature.SettingsScreen
import com.kharchacheck.app.ui.components.Celebration
import com.kharchacheck.app.ui.nav.ActivityRoute
import com.kharchacheck.app.ui.nav.BudgetsRoute
import com.kharchacheck.app.ui.nav.CategoriesRoute
import com.kharchacheck.app.ui.nav.EditRoute
import com.kharchacheck.app.ui.nav.GrowRoute
import com.kharchacheck.app.ui.nav.HomeRoute
import com.kharchacheck.app.ui.nav.InboxRoute
import com.kharchacheck.app.ui.nav.PrivacyRoute
import com.kharchacheck.app.ui.nav.RecurringRoute
import com.kharchacheck.app.ui.nav.StatementsRoute
import com.kharchacheck.app.ui.nav.ImportRoute
import com.kharchacheck.app.ui.nav.BillsRoute
import com.kharchacheck.app.ui.nav.DuesRoute
import com.kharchacheck.app.ui.nav.WealthRoute
import com.kharchacheck.app.ui.nav.GuideRoute
import com.kharchacheck.app.ui.nav.WrappedRoute
import com.kharchacheck.app.ui.nav.CategoryInsightsRoute
import com.kharchacheck.app.ui.nav.ReportRoute
import com.kharchacheck.app.ui.nav.SettingsRoute
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Kc
import com.kharchacheck.app.ui.theme.MutedOnInk
import kotlinx.coroutines.flow.Flow

@Composable
fun KharchaRoot(vm: AppViewModel, navRequests: Flow<String>) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val s = settings ?: return
    when {
        !s.onboarded -> OnboardingScreen(vm)
        s.appLock && locked -> LockScreen(vm)
        else -> MainShell(vm, navRequests)
    }
}

private data class Tab(val route: Any, val isAt: (NavDestination) -> Boolean, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(HomeRoute, { it.hasRoute<HomeRoute>() }, "Home", Icons.Rounded.Home),
    Tab(ActivityRoute, { it.hasRoute<ActivityRoute>() }, "Activity", Icons.Rounded.Receipt),
    Tab(ReportRoute, { it.hasRoute<ReportRoute>() }, "Report", Icons.Rounded.PieChart),
    Tab(GrowRoute, { it.hasRoute<GrowRoute>() }, "Grow", Icons.Rounded.EmojiEvents),
)

fun NavHostController.openTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun MainShell(vm: AppViewModel, navRequests: Flow<String>) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val win by vm.win.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val onTab = dest != null && TABS.any { t -> t.isAt(dest) }

    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            val r = snackbar.showSnackbar(m.text, actionLabel = if (m.undo != null) "Undo" else null, withDismissAction = m.undo == null)
            if (r == SnackbarResult.ActionPerformed && m.undo != null) vm.restore(m.undo)
        }
    }
    LaunchedEffect(Unit) {
        navRequests.collect { target ->
            when (target) {
                "add" -> nav.navigate(EditRoute())
                "draft" -> nav.navigate(EditRoute(fromDraft = true))
                "inbox" -> nav.navigate(InboxRoute)
                "report" -> nav.openTab(ReportRoute)
                "bills" -> nav.navigate(BillsRoute)
                "settings" -> nav.navigate(SettingsRoute)
                "dues" -> nav.navigate(DuesRoute)
                else -> if (target.startsWith("import:")) target.removePrefix("import:").toLongOrNull()?.let { nav.navigate(ImportRoute(it)) }
            }
        }
    }

    win?.let { Celebration(it.emoji, it.title, it.body, onClose = vm::dismissWin) }

    // Crash report from the previous run: the user chooses whether to email it.
    val platform = LocalPlatform.current
    var crash by remember { mutableStateOf(vm.pendingCrash()) }
    crash?.let { report ->
        AlertDialog(
            onDismissRequest = { vm.clearCrash(); crash = null },
            title = { Text("Sorry, Kharcha Check closed unexpectedly") },
            text = { Text("Would you like to email the technical details to the developer so it can be fixed? You'll see the email before it's sent, and it contains no amounts, payees or notes.") },
            confirmButton = { TextButton(onClick = { platform.emailCrash(report); vm.clearCrash(); crash = null }) { Text("Send report") } },
            dismissButton = { TextButton(onClick = { vm.clearCrash(); crash = null }) { Text("Not now") } },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Kc.colors.paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (onTab) FloatingActionButton(
                onClick = { nav.navigate(EditRoute()) },
                containerColor = Haldi, contentColor = Ink, shape = RoundedCornerShape(20.dp),
            ) { Icon(Icons.Rounded.Add, contentDescription = "Add entry", modifier = Modifier.size(30.dp)) }
        },
        bottomBar = {
            if (onTab) NavigationBar(containerColor = Ink) {
                TABS.forEach { t ->
                    NavigationBarItem(
                        selected = dest != null && t.isAt(dest),
                        onClick = { nav.openTab(t.route) },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Ink, selectedTextColor = Haldi, indicatorColor = Haldi,
                            unselectedIconColor = MutedOnInk, unselectedTextColor = MutedOnInk,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            NavHost(
                nav, startDestination = HomeRoute,
                enterTransition = { fadeIn(tween(220, delayMillis = 60)) }, exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(220, delayMillis = 60)) }, popExitTransition = { fadeOut(tween(120)) },
            ) {
                composable<HomeRoute> { HomeScreen(vm, nav) }
                composable<ActivityRoute> { ActivityScreen(vm, nav) }
                composable<ReportRoute> { ReportScreen(vm, nav) }
                composable<GrowRoute> { GrowScreen(vm, nav) }
                composable<SettingsRoute> { SettingsScreen(vm, nav) }
                composable<CategoriesRoute> { CategoriesScreen(vm, nav) }
                composable<BudgetsRoute> { BudgetsScreen(vm, nav) }
                composable<InboxRoute> { InboxScreen(vm, nav) }
                composable<PrivacyRoute> { PrivacyScreen(nav) }
                composable<RecurringRoute> { RecurringScreen(vm, nav) }
                composable<DuesRoute> { DuesScreen(vm, nav) }
                composable<WealthRoute> { WealthScreen(vm, nav) }
                composable<GuideRoute> { e -> GuideScreen(vm, nav, e.toRoute<GuideRoute>()) }
                composable<BillsRoute> { BillsScreen(vm, nav) }
                composable<WrappedRoute> { e -> WrappedScreen(vm, nav, e.toRoute<WrappedRoute>()) }
                composable<CategoryInsightsRoute> { e -> CategoryInsightsScreen(vm, nav, e.toRoute<CategoryInsightsRoute>()) }
                composable<ImportRoute> { e -> ImportScreen(vm, nav, e.toRoute<ImportRoute>()) }
                composable<StatementsRoute> { e -> StatementsScreen(vm, nav, e.toRoute<StatementsRoute>()) }
                composable<EditRoute> { e -> EditTxnScreen(vm, nav, e.toRoute<EditRoute>()) }
            }
        }
    }
}
