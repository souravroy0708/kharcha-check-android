package com.kharchacheck.app.ui.nav

import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object ActivityRoute
@Serializable object ReportRoute
@Serializable object GrowRoute
@Serializable object SettingsRoute
@Serializable object CategoriesRoute
@Serializable object BudgetsRoute
@Serializable object InboxRoute
@Serializable object PrivacyRoute
@Serializable object RecurringRoute
@Serializable data class StatementsRoute(val month: String = "")
@Serializable data class ImportRoute(val attachmentId: Long)

/** id = 0 for a new entry. type preselects the tab. fromDraft = open with a parsed shared/pasted draft. */
@Serializable data class EditRoute(val id: Long = 0, val type: String = "EXPENSE", val fromDraft: Boolean = false, val categoryId: Long = 0)
@Serializable data class CategoryInsightsRoute(val categoryId: Long, val months: Int = 6)
@Serializable object DuesRoute
@Serializable object BillsRoute
/** period = "2026-10" for a month, "2026" for a year. */
@Serializable data class WrappedRoute(val period: String)
@Serializable object WealthRoute
@Serializable data class GuideRoute(val tab: Int = 0)
@Serializable object ExportRoute
@Serializable object HiddenRoute
@Serializable object GoalsRoute
