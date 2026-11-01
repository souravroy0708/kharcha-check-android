package com.kharchacheck.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharchacheck.app.AppController
import com.kharchacheck.app.Tab

data class PhoneActions(
    val onRequestSmsPermissions: (Int) -> Unit,
    val onRequestNotificationPermission: () -> Unit,
    val onOpenAppInfoPermissions: () -> Unit,
    val onExportMonthPdf: () -> Unit,
    val onExportEncryptedBackup: (String) -> Unit,
    val onImportEncryptedBackup: (String) -> Unit,
    val onCopyText: (String, String) -> Unit,
)

private data class NavTab(val tab: Tab, val label: String, val icon: ImageVector)

private val TABS = listOf(
    NavTab(Tab.Home, "Home", Icons.Filled.Home),
    NavTab(Tab.Transactions, "Transactions", Icons.Filled.ReceiptLong),
    NavTab(Tab.Insights, "Insights", Icons.Filled.Insights),
    NavTab(Tab.Budgets, "Budgets", Icons.Filled.AccountBalanceWallet),
    NavTab(Tab.Settings, "Settings", Icons.Filled.Settings),
)

@Composable
fun MainShell(app: AppController, actions: PhoneActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(app.message) {
        app.message?.let {
            snackbar.showSnackbar(it)
            app.message = null
        }
    }

    // Rule learning prompt ("Always do this for this merchant" - Spec Page 5)
    if (app.rulePromptMerchant != null && app.rulePromptCategoryId != null) {
        val cat = app.categoryMap[app.rulePromptCategoryId]
        AlertDialog(
            onDismissRequest = { app.dismissMerchantRulePrompt() },
            title = { Text("Always categorise '${app.rulePromptMerchant}'?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "Whenever a bank SMS or entry mentions '${app.rulePromptMerchant}', Kharcha Check will automatically assign it to ${cat?.emoji ?: ""} ${cat?.nameEn ?: "this category"}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                BigButton("Yes, always do this", emerald = true) {
                    app.acceptMerchantRulePrompt()
                }
            },
            dismissButton = {
                TextButton(onClick = { app.dismissMerchantRulePrompt() }) {
                    Text("Just this once", color = Muted)
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            KharchaTopBar(app = app, actions = actions)
        },
        floatingActionButton = {
            if (!app.quickAddOpen && !app.monthReviewOpen && !app.reviewTrayOpen) {
                FloatingActionButton(
                    onClick = { app.openQuickAdd() },
                    containerColor = Emerald,
                    contentColor = Navy,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(t("Quick Add"), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Navy) {
                TABS.forEach { item ->
                    val selected = app.tab == item.tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            app.quickAddOpen = false
                            app.reviewTrayOpen = false
                            app.monthReviewOpen = false
                            app.tab = item.tab
                        },
                        icon = {
                            if (item.tab == Tab.Transactions && app.reviewTrayItems.isNotEmpty()) {
                                BadgedBox(
                                    badge = {
                                        Badge(containerColor = Amber, contentColor = Navy) {
                                            Text(app.reviewTrayItems.size.toString(), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                ) {
                                    Icon(item.icon, contentDescription = null)
                                }
                            } else {
                                Icon(item.icon, contentDescription = null)
                            }
                        },
                        label = { Text(t(item.label), style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Navy,
                            selectedTextColor = Emerald,
                            indicatorColor = Emerald,
                            unselectedIconColor = MutedOnDark,
                            unselectedTextColor = MutedOnDark,
                        ),
                    )
                }
            }
        },
        containerColor = Paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        val screenState = "${app.tab}:${app.quickAddOpen}:${app.reviewTrayOpen}:${app.monthReviewOpen}"
        AnimatedContent(
            targetState = screenState,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(100)) },
            label = "kharcha_screens",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { _ ->
            Box(Modifier.fillMaxSize()) {
                when {
                    app.quickAddOpen -> {
                        BackHandler { app.quickAddOpen = false }
                        QuickAddScreen(app = app, onClose = { app.quickAddOpen = false })
                    }
                    app.reviewTrayOpen -> {
                        BackHandler { app.reviewTrayOpen = false }
                        ReviewTrayScreen(app = app, onClose = { app.reviewTrayOpen = false })
                    }
                    app.monthReviewOpen -> {
                        BackHandler { app.monthReviewOpen = false }
                        MonthEndReviewFlow(
                            app = app,
                            actions = actions,
                            onClose = { app.monthReviewOpen = false },
                        )
                    }
                    else -> {
                        when (app.tab) {
                            Tab.Home -> HomeTab(app = app, actions = actions)
                            Tab.Transactions -> TransactionsTab(app = app)
                            Tab.Insights -> InsightsTab(app = app, actions = actions)
                            Tab.Budgets -> BudgetsTab(app = app)
                            Tab.Settings -> SettingsTab(app = app, actions = actions)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KharchaTopBar(app: AppController, actions: PhoneActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Navy)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(Emerald),
            contentAlignment = Alignment.Center,
        ) {
            Text("₹", color = Navy, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Kharcha Check", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { actions.onOpenAppInfoPermissions() },
            ) {
                Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = Emerald, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    t("100% On-Device · No Internet Permission"),
                    color = Emerald,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                )
            }
        }

        // Language switch (EN / हिन्दी)
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Navy2)
                .border(1.dp, NavyLine, RoundedCornerShape(50))
                .clickable {
                    val next = if (app.language == "en") "hi" else "en"
                    app.prefs.language = next
                    app.language = next
                    Lang.current = next
                }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (app.language == "hi") "English" else "हिन्दी",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
