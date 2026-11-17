package com.kharchacheck.app.domain

enum class SourceKind(val label: String, val emoji: String) { BOOK("Book", "📖"), STUDY("Research", "🔬"), RULE("Indian rules", "⚖️") }
enum class LibTopic(val label: String) { ONLINE("Online shopping"), IMPULSE("Impulse & habits"), SAVING("Saving"), INVESTING("Investing"), DEBT("Debt"), HAPPINESS("Spending that makes you happy") }

/**
 * One evidence-backed idea: who said or found it, what it means (in our own words), and one thing to do.
 * Only the Franklin line is quoted (public domain); everything else is paraphrased with its source named.
 */
data class LibCard(val id: String, val kind: SourceKind, val topic: LibTopic, val source: String, val idea: String, val action: String)

object WisdomLibrary {
    val cards: List<LibCard> = listOf(
        // ---------------- Online shopping & impulse (research)
        LibCard("prelec_simester", SourceKind.STUDY, LibTopic.ONLINE, "Prelec & Simester, Marketing Letters (2001)",
            "In sealed-bid auctions for sports tickets, people told to pay by credit card bid far more, in one test about double, than people told to pay cash.",
            "Remove saved cards from shopping apps. For wants, pay from a separate small \"fun\" balance so you feel each rupee."),
        LibCard("pain_of_paying", SourceKind.STUDY, LibTopic.ONLINE, "Prelec & Loewenstein, Marketing Science (1998)",
            "The \"pain of paying\" acts as a brake on spending. Payment methods that separate paying from buying (cards, pay-later) weaken that brake.",
            "Keep Buy Now Pay Later switched off. If you'd hesitate to pay in full today, skip it."),
        LibCard("knutson", SourceKind.STUDY, LibTopic.ONLINE, "Knutson et al., Neuron (2007)",
            "Brain scans while shopping showed that an excessive price activated a region linked to pain and predicted the decision not to buy.",
            "Always look at the final price with delivery, platform and convenience fees before deciding. Hidden fees mute that warning."),
        LibCard("scarcity_cookies", SourceKind.STUDY, LibTopic.ONLINE, "Worchel, Lee & Adewole, J. Personality & Social Psychology (1975)",
            "Identical cookies were rated more desirable when they came from a nearly empty jar than from a full one. Scarcity alone made them seem better.",
            "Treat \"only 2 left\" and countdown timers as noise. Put the item on your wishlist instead."),
        LibCard("decoy", SourceKind.BOOK, LibTopic.ONLINE, "Dan Ariely, Predictably Irrational (2008)",
            "Adding an unattractive \"decoy\" option (like print-only at the same price as print + web) steers people towards the pricier choice.",
            "When plans or sizes are offered, compare only the options you actually need. Ignore the one designed to make another look like a deal."),
        LibCard("loss_aversion", SourceKind.STUDY, LibTopic.ONLINE, "Kahneman & Tversky, Econometrica (1979)",
            "Losses feel roughly twice as strong as equal gains, so \"don't miss out\" and \"offer ends tonight\" framing pushes us to act.",
            "Re-read the offer as a gain: \"Do I gain anything I need?\" If not, missing it costs nothing."),
        LibCard("mental_accounting", SourceKind.STUDY, LibTopic.SAVING, "Richard Thaler, J. Behavioral Decision Making (1999)",
            "People treat money differently depending on its label: cashback, refunds and bonuses feel like \"free money\" and get spent more easily.",
            "Treat cashback, refunds and bonuses as real salary: move them straight to a goal."),
        LibCard("fast_slow", SourceKind.BOOK, LibTopic.IMPULSE, "Daniel Kahneman, Thinking, Fast and Slow (2011)",
            "Quick, intuitive thinking drives many snap decisions; slowing down gives deliberate thinking a chance to check them.",
            "Use the wishlist: a few days of delay turns an urge into a decision."),
        LibCard("hedonic", SourceKind.STUDY, LibTopic.HAPPINESS, "Frederick & Loewenstein, in Well-Being (1999)",
            "We adapt to new possessions: the lift from a new purchase fades as it becomes normal.",
            "Before buying, ask how you'll feel about it in a month, not today."),
        LibCard("experiences", SourceKind.STUDY, LibTopic.HAPPINESS, "Van Boven & Gilovich, J. Personality & Social Psychology (2003)",
            "In their studies, experiential purchases (trips, events, meals with people) tended to make people happier than material purchases.",
            "If you're going to spend on a want, consider an experience with people you care about."),
        LibCard("prosocial", SourceKind.STUDY, LibTopic.HAPPINESS, "Dunn, Aknin & Norton, Science (2008)",
            "People who were asked to spend a small sum on someone else reported more happiness than those who spent it on themselves.",
            "Keep a small giving budget. It often brings more joy than another thing for yourself."),

        // ---------------- Saving & habits (books)
        LibCard("franklin", SourceKind.BOOK, LibTopic.SAVING, "Benjamin Franklin, Poor Richard's Almanack (1700s)",
            "\"Beware of little expenses; a small leak will sink a great ship.\"",
            "Check your Leaks every week. Small daily spends are where most money disappears."),
        LibCard("housel_invisible", SourceKind.BOOK, LibTopic.SAVING, "Morgan Housel, The Psychology of Money (2020)",
            "Wealth is mostly invisible: it's the purchases you didn't make, the upgrade you skipped, the money left untouched.",
            "Every item you skip on the wishlist is wealth you can see: it's counted for you."),
        LibCard("housel_rate", SourceKind.BOOK, LibTopic.SAVING, "Morgan Housel, The Psychology of Money (2020)",
            "Your savings rate is the part of building wealth you control most, and you don't need a specific reason to save.",
            "Raise your savings rate a little each month; aim for the split in your salary plan."),
        LibCard("millionaire", SourceKind.BOOK, LibTopic.SAVING, "Thomas Stanley & William Danko, The Millionaire Next Door (1996)",
            "Many of the self-made millionaires they studied lived well below their means: modest homes, used cars, careful budgets.",
            "Judge purchases by need and use, not by what others will see."),
        LibCard("life_energy", SourceKind.BOOK, LibTopic.IMPULSE, "Vicki Robin & Joe Dominguez, Your Money or Your Life (1992)",
            "Money is life energy you traded your hours for. Converting a price into hours of work changes how worth it something feels.",
            "Look at the \"hours of your work\" shown on wants and wishlist items before buying."),
        LibCard("conscious_spending", SourceKind.BOOK, LibTopic.SAVING, "Ramit Sethi, I Will Teach You to Be Rich (2009)",
            "Spend generously on the few things you love, and cut mercilessly on the things you don't care about. Automate the rest.",
            "Pick two \"love\" categories; set tight budgets on the others."),
        LibCard("goodbye_things", SourceKind.BOOK, LibTopic.IMPULSE, "Fumio Sasaki, Goodbye, Things (2015)",
            "Owning less frees time and attention otherwise spent buying, storing and maintaining things.",
            "One in, one out: before buying, decide what it replaces."),
        LibCard("save_more_tomorrow", SourceKind.STUDY, LibTopic.SAVING, "Thaler & Benartzi, J. Political Economy (2004)",
            "Employees who committed in advance to save part of future raises lifted their savings rate from about 3.5% to 13.6% in under four years.",
            "Turn on step-up SIPs: commit now to invest part of every raise."),

        // ---------------- Investing & debt
        LibCard("buffett", SourceKind.BOOK, LibTopic.INVESTING, "Warren Buffett, Berkshire Hathaway shareholder letter (2013)",
            "He wrote that he'd instructed the trustee for his wife to put most of her money in a very low-cost index fund, and that most non-professional investors would do well that way.",
            "A low-cost index fund SIP is a strong default. Avoid frequent switching."),
        LibCard("bogle", SourceKind.BOOK, LibTopic.INVESTING, "John C. Bogle, The Little Book of Common Sense Investing (2007)",
            "Costs compound against you: after fees, most actively managed funds trail a low-cost index fund over long periods.",
            "Check the expense ratio and prefer direct plans of mutual funds."),
        LibCard("halan", SourceKind.BOOK, LibTopic.INVESTING, "Monika Halan, Let's Talk Money (2018)",
            "Build a simple money system: protect first (insurance, emergency fund), keep insurance and investment separate, and keep products simple.",
            "Follow the order in your salary plan's money ladder."),
        LibCard("snowball", SourceKind.STUDY, LibTopic.DEBT, "Gal & McShane, J. Marketing Research (2012)",
            "People who closed off individual debt accounts (small wins) were more likely to eventually clear all their debt.",
            "Paying several loans? Clear the smallest first for momentum, unless one has far higher interest."),

        // ---------------- Indian rules
        LibCard("ccpa_dark_patterns", SourceKind.RULE, LibTopic.ONLINE, "CCPA, Guidelines for Prevention and Regulation of Dark Patterns (2023)",
            "India's Central Consumer Protection Authority lists 13 banned dark patterns for online platforms, including false urgency, basket sneaking (adding items or charges you didn't choose), confirm-shaming, forced action, subscription traps, bait-and-switch and drip pricing.",
            "Spotted one? Complain on the National Consumer Helpline: call 1915 or use the NCH app / consumerhelpline.gov.in."),
        LibCard("rbi_mandates", SourceKind.RULE, LibTopic.ONLINE, "RBI e-mandate framework for recurring payments",
            "Banks must notify you before each recurring auto-debit, and you can view and cancel AutoPay mandates yourself.",
            "Open your UPI app → AutoPay / mandates every few months and revoke anything you don't use."),
    )

    fun byTopic(t: LibTopic) = cards.filter { it.topic == t }

    /** A card for today, rotating through the library (used on the Online spending tab). */
    fun forDay(epochDay: Long): LibCard = cards[Math.floorMod(epochDay, cards.size.toLong()).toInt()]
}
