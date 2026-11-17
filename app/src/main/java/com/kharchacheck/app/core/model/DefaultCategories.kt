package com.kharchacheck.app.core.model

/**
 * The starter set. Keys are stable forever (SMS rules, challenges and backups refer to them);
 * names, emoji and colours can be edited by the user. Users can add their own on top.
 */
object DefaultCategories {
    private var order = 0
    private fun c(key: String, name: String, emoji: String, color: Long, bucket: Bucket) =
        Category(id = 0L, key = key, name = name, emoji = emoji, colorArgb = color, bucket = bucket, sortOrder = order++)

    val all: List<Category> = listOf(
        // NEEDS: essentials
        c("rent", "Rent & Housing", "🏠", 0xFF5B6CFF, Bucket.NEED),
        c("groceries", "Groceries & Ration", "🛒", 0xFF2BB673, Bucket.NEED),
        c("vegetables", "Vegetables, Fruit & Milk", "🥦", 0xFF43A047, Bucket.NEED),
        c("electricity", "Electricity & Water", "💡", 0xFF00ACC1, Bucket.NEED),
        c("gas", "Gas / LPG", "🔥", 0xFFEF6C00, Bucket.NEED),
        c("mobile_internet", "Mobile & Internet", "📶", 0xFF1E88E5, Bucket.NEED),
        c("commute", "Commute (Metro, Bus, Auto)", "🚇", 0xFF3949AB, Bucket.NEED),
        c("fuel", "Fuel", "⛽", 0xFF6D4C41, Bucket.NEED),
        c("vehicle", "Vehicle Service & Parking", "🛵", 0xFF546E7A, Bucket.NEED),
        c("medical", "Medicine & Doctor", "💊", 0xFF00897B, Bucket.NEED),
        c("insurance", "Insurance Premium", "🛡️", 0xFF26A69A, Bucket.NEED),
        c("emi", "EMI & Loan Repayment", "🏦", 0xFF7E57C2, Bucket.NEED),
        c("education", "School & College Fees", "🎓", 0xFF3F51B5, Bucket.NEED),
        c("childcare", "Kids Essentials", "🧸", 0xFFEC407A, Bucket.NEED),
        c("family_support", "Parents & Family Support", "👪", 0xFF8D6E63, Bucket.NEED),
        c("house_help", "House Help & Maintenance", "🧹", 0xFF78909C, Bucket.NEED),
        c("household", "Household Supplies", "🧴", 0xFF26C6DA, Bucket.NEED),
        c("work_tools", "Work Tools & Office", "💼", 0xFF5C6BC0, Bucket.NEED),

        // WANTS: fine in moderation, plan them
        c("dining_out", "Dining Out", "🍽️", 0xFFFFA000, Bucket.WANT),
        c("shopping_clothes", "Clothes & Footwear", "👕", 0xFFEC407A, Bucket.WANT),
        c("shopping", "Shopping (General)", "🛍️", 0xFFAD1457, Bucket.WANT),
        c("gadgets", "Gadgets & Electronics", "📱", 0xFF42A5F5, Bucket.WANT),
        c("entertainment", "Movies & Events", "🎬", 0xFFFF7043, Bucket.WANT),
        c("ott", "OTT & Music Subscriptions", "📺", 0xFFAB47BC, Bucket.WANT),
        c("travel", "Travel & Trips", "✈️", 0xFF29B6F6, Bucket.WANT),
        c("cab", "Cabs (Uber, Ola, Rapido)", "🚕", 0xFFFFCA28, Bucket.WANT),
        c("gifts", "Gifts & Shagun", "🎁", 0xFFE91E63, Bucket.WANT),
        c("festivals", "Festivals & Pooja", "🪔", 0xFFFF8F00, Bucket.WANT),
        c("fitness", "Gym & Fitness", "🏋️", 0xFF66BB6A, Bucket.WANT),
        c("personal_care", "Salon & Grooming", "💇", 0xFFF06292, Bucket.WANT),
        c("hobbies", "Hobbies", "🎨", 0xFF9575CD, Bucket.WANT),
        c("books_courses", "Books & Courses", "📚", 0xFF5C6BC0, Bucket.WANT),
        c("home_decor", "Home & Decor", "🛋️", 0xFFA1887F, Bucket.WANT),
        c("pets", "Pets", "🐾", 0xFF8D6E63, Bucket.WANT),
        c("donation", "Donations & Charity", "🤲", 0xFF4DB6AC, Bucket.WANT),

        // LEAKS: small, frequent, regret-prone. The first place to cut.
        c("food_delivery", "Food Delivery (Swiggy, Zomato)", "🛵", 0xFFFF6B4A, Bucket.LEAK),
        c("junk_food", "Junk Food & Snacks", "🍟", 0xFFF4511E, Bucket.LEAK),
        c("chai_coffee", "Chai & Coffee Outside", "☕", 0xFFBF7B3F, Bucket.LEAK),
        c("cold_drinks", "Cold Drinks & Packaged Drinks", "🥤", 0xFFE53935, Bucket.LEAK),
        c("sweets_desserts", "Sweets & Desserts", "🍰", 0xFFF06292, Bucket.LEAK),
        c("quick_commerce", "Quick Commerce Impulse (Blinkit, Zepto)", "⚡", 0xFFFFB300, Bucket.LEAK),
        c("impulse_shopping", "Impulse Online Shopping", "🛍️", 0xFFD81B60, Bucket.LEAK),
        c("unused_subscriptions", "Unused Subscriptions", "🔁", 0xFF8E24AA, Bucket.LEAK),
        c("smoking", "Cigarettes, Paan & Tobacco", "🚬", 0xFF757575, Bucket.LEAK),
        c("alcohol", "Alcohol & Parties", "🍺", 0xFFFFA726, Bucket.LEAK),
        c("gaming", "Games & In-app Purchases", "🎮", 0xFF7C4DFF, Bucket.LEAK),
        c("betting", "Betting & Fantasy Apps", "🎲", 0xFFC62828, Bucket.LEAK),
        c("fees_penalties", "Late Fees & Penalties", "⚠️", 0xFFD84315, Bucket.LEAK),
        c("bank_charges", "Bank & Convenience Charges", "🧾", 0xFF6D4C41, Bucket.LEAK),
        c("cc_interest", "Credit Card Interest", "💳", 0xFFB71C1C, Bucket.LEAK),
        c("lottery", "Lottery & Schemes", "🎟️", 0xFF9E9D24, Bucket.LEAK),
        c("regret_misc", "Bought & Regretted", "😬", 0xFF8D6E63, Bucket.LEAK),

        // SAVE & INVEST: paying your future self
        c("sip", "SIP / Mutual Funds", "📈", 0xFF2BB673, Bucket.INVEST),
        c("stocks", "Stocks", "📊", 0xFF00897B, Bucket.INVEST),
        c("ppf_epf_nps", "PPF / EPF / NPS", "🏛️", 0xFF3949AB, Bucket.INVEST),
        c("fd_rd", "FD / RD", "🔒", 0xFF5E35B1, Bucket.INVEST),
        c("gold", "Gold & SGB", "🪙", 0xFFFFB300, Bucket.INVEST),
        c("emergency_fund", "Emergency Fund", "🛟", 0xFFE53935, Bucket.INVEST),
        c("goal_savings", "Goal Savings", "🎯", 0xFF1E88E5, Bucket.INVEST),
        c("debt_prepay", "Loan Prepayment", "✂️", 0xFF6D4C41, Bucket.INVEST),

        // INCOME
        c("salary", "Salary", "💰", 0xFF0B7A55, Bucket.INCOME),
        c("freelance", "Freelance & Side Income", "🧑‍💻", 0xFF00897B, Bucket.INCOME),
        c("business", "Business Income", "🏪", 0xFF2E7D32, Bucket.INCOME),
        c("interest_dividend", "Interest & Dividends", "🌱", 0xFF43A047, Bucket.INCOME),
        c("refund", "Refunds & Cashback", "↩️", 0xFF26A69A, Bucket.INCOME),
        c("rent_received", "Rent Received", "🏘️", 0xFF558B2F, Bucket.INCOME),
        c("gift_received", "Gifts Received", "🎉", 0xFFEC407A, Bucket.INCOME),
        c("other_income", "Other Income", "➕", 0xFF78909C, Bucket.INCOME),

        // TRANSFERS: not spending
        c("self_transfer", "Own Account Transfer", "🔄", 0xFF8A90A8, Bucket.TRANSFER),
        c("cc_bill", "Credit Card Bill Payment", "💳", 0xFF8A90A8, Bucket.TRANSFER),
        c("atm_cash", "ATM Cash Withdrawal", "🏧", 0xFF8A90A8, Bucket.TRANSFER),
        c("lent_borrowed", "Lent / Borrowed", "🤝", 0xFF8A90A8, Bucket.TRANSFER),

        // Catch-all
        c("uncategorised", "Uncategorised", "❓", 0xFF94A3B8, Bucket.WANT),
    )

    const val UNCATEGORISED = "uncategorised"
}
