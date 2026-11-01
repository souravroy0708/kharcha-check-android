package com.kharchacheck.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Lang {
    var current by mutableStateOf("en")

    private val HI = mapOf(
        // Navigation
        "Home" to "होम",
        "Transactions" to "लेन-देन",
        "Insights" to "इनसाइट्स",
        "Budgets" to "बजट",
        "Settings" to "सेटिंग्स",

        // Headlines & Tagline
        "Paisa kahan gaya — ab pata chalega." to "पैसा कहाँ गया — अब पता चलेगा।",
        "100% On-Device · No Internet Permission" to "100% फ़ोन पर · बिना इंटरनेट अनुमति",
        "Spent this month" to "इस महीने का खर्च",
        "Income" to "आय",
        "Saved" to "बचत",
        "Quick Add" to "तुरंत जोड़ें",
        "Review Tray" to "रिव्यू ट्रे",
        "Start 3-Min Month-End Review" to "3-मिनट मंथ-एंड रिव्यू शुरू करें",
        "Small Leaks (< ₹200)" to "छोटे खर्च (< ₹200)",
        "Today & Recent Spends" to "आज और हाल के खर्च",
        "Where it went" to "पैसा कहाँ गया",
        "Fixed vs Flexible Spend" to "तय बनाम लचीला खर्च",
        "Recurring Fixed Costs" to "हर महीने के तय खर्च (Rent, EMI)",
        "Overspend Flags (Max 3)" to "ज़्यादा खर्च वाली श्रेणियाँ (अधिकतम 3)",
        "Save" to "सेव करें",
        "Cancel" to "रद्द करें",
        "Confirm" to "पक्का करें",
    )

    fun t(en: String): String = if (current == "hi") HI[en] ?: en else en
}

fun t(en: String): String = Lang.t(en)
