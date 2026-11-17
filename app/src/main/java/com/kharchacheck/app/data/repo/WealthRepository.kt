package com.kharchacheck.app.data.repo

import android.content.Context
import com.kharchacheck.app.data.db.KharchaDatabase
import com.kharchacheck.app.data.db.NetSnapshotEntity
import com.kharchacheck.app.data.db.toEntity
import com.kharchacheck.app.data.db.toModel
import com.kharchacheck.app.domain.AmfiNav
import com.kharchacheck.app.domain.Holding
import com.kharchacheck.app.domain.NetItem
import com.kharchacheck.app.domain.Scheme
import com.kharchacheck.app.domain.Wealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.YearMonth

/**
 * Investments and net worth.
 *
 * Mutual fund NAVs come from AMFI's public daily file of ALL schemes. The whole file is downloaded and
 * searched on the phone, so the request reveals nothing about what the user owns. A compact cache keeps
 * search and values working offline.
 */
class WealthRepository(private val context: Context, private val db: KharchaDatabase) {
    private val cache = File(context.noBackupFilesDir, "amfi_nav.tsv")
    private val lock = Mutex()
    private val _schemes = MutableStateFlow<List<Scheme>>(emptyList())
    val schemes: StateFlow<List<Scheme>> = _schemes.asStateFlow()
    val navUpdatedAt: Long get() = if (cache.exists()) cache.lastModified() else 0L

    val holdings: Flow<List<Holding>> = db.wealth().holdings().map { l -> l.map { it.toModel() } }
    val items: Flow<List<NetItem>> = db.wealth().items().map { l -> l.map { it.toModel() } }
    val snapshots: Flow<Map<YearMonth, Long>> = db.wealth().snapshots().map { l -> l.associate { YearMonth.parse(it.month) to it.netPaise } }

    suspend fun saveHolding(h: Holding) { db.wealth().upsertHolding(h.toEntity()) }
    suspend fun deleteHolding(id: Long) = db.wealth().deleteHolding(id)
    suspend fun saveItem(i: NetItem) { db.wealth().upsertItem(i.toEntity()) }
    suspend fun deleteItem(id: Long) = db.wealth().deleteItem(id)

    /** Loads cached NAVs into memory (fast) without touching the network. */
    suspend fun loadCache() = lock.withLock {
        if (_schemes.value.isNotEmpty() || !cache.exists()) return@withLock
        _schemes.value = withContext(Dispatchers.IO) {
            cache.readLines().mapNotNull { l ->
                val p = l.split('\t'); if (p.size < 6) return@mapNotNull null
                Scheme(p[0], p[1], p[2], p[3], p[4].toLongOrNull(), p[5].takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
            }
        }
    }

    /** Downloads today's NAVs if the cache is older than [maxAgeHours]. Returns true when fresh data was fetched. */
    suspend fun refresh(maxAgeHours: Int = 12, force: Boolean = false): Boolean = lock.withLock {
        if (!force && cache.exists() && System.currentTimeMillis() - cache.lastModified() < maxAgeHours * 3_600_000L) return@withLock false
        val text = withContext(Dispatchers.IO) { download(AmfiNav.URL) ?: download(AmfiNav.FALLBACK_URL) } ?: error("Couldn't reach AMFI. Check your connection.")
        val parsed = withContext(Dispatchers.Default) { AmfiNav.parse(text) }
        if (parsed.size < 1000) error("AMFI returned an unexpected file. Try again later.")
        withContext(Dispatchers.IO) {
            val tmp = File(cache.parentFile, "amfi_nav.tmp")
            tmp.bufferedWriter().use { w -> parsed.forEach { s ->
                w.write(listOf(s.code, s.name.replace('\t', ' '), s.amc, s.category, s.navMicro?.toString().orEmpty(), s.date?.toString().orEmpty()).joinToString("\t")); w.write("\n")
            } }
            cache.delete(); tmp.renameTo(cache)
        }
        _schemes.value = parsed
        true
    }

    private fun download(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 45_000
        c.setRequestProperty("User-Agent", "KharchaCheck-Android")
        try {
            if (c.responseCode != 200) null else c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally { c.disconnect() }
    }.getOrNull()

    /** Records this month's net worth for the trend chart. */
    suspend fun snapshotNow() {
        loadCache()
        val navs = _schemes.value.associateBy { it.code }
        val nw = Wealth.netWorth(holdings.first(), items.first(), navs)
        if (nw.holdings.isEmpty() && nw.assets.isEmpty() && nw.liabilities.isEmpty()) return
        db.wealth().upsertSnapshot(NetSnapshotEntity(YearMonth.now().toString(), nw.netPaise))
    }

    /** Frees ~10 MB of scheme data when the user leaves the wealth screens. */
    fun trimMemory() { _schemes.value = emptyList() }
}
