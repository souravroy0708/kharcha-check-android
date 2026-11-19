@file:OptIn(ExperimentalMaterial3Api::class)

package com.kharchacheck.app.feature

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kharchacheck.app.core.model.Attachment
import com.kharchacheck.app.core.model.Money
import com.kharchacheck.app.domain.DiaryEntry
import com.kharchacheck.app.domain.Trip
import com.kharchacheck.app.domain.TripItem
import com.kharchacheck.app.domain.TripItemKind
import com.kharchacheck.app.domain.TripPerson
import com.kharchacheck.app.domain.TripPlace
import com.kharchacheck.app.domain.TripPlan
import com.kharchacheck.app.domain.Trips
import com.kharchacheck.app.domain.VoiceEntry
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.LocalPlatform
import com.kharchacheck.app.ui.components.AttachmentThumb
import com.kharchacheck.app.ui.components.AttachmentViewer
import com.kharchacheck.app.ui.components.BigButton
import com.kharchacheck.app.ui.components.EmptyState
import com.kharchacheck.app.ui.components.KcCard
import com.kharchacheck.app.ui.components.MicButton
import com.kharchacheck.app.ui.components.Pill
import com.kharchacheck.app.ui.components.ProgressBar
import com.kharchacheck.app.ui.components.SubScreen
import com.kharchacheck.app.ui.components.appendSpoken
import com.kharchacheck.app.ui.components.spokenName
import com.kharchacheck.app.ui.theme.Coral
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Kc
import com.kharchacheck.app.ui.theme.Mint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class TripTab(val label: String) { OVERVIEW("Overview"), COSTS("Bookings & costs"), PLACES("Places"), PEOPLE("People"), DIARY("Diary") }

private val zone: ZoneId get() = ZoneId.systemDefault()
private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
private fun whenText(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).let { "${it.format(dayFmt)}, ${it.format(timeFmt)}" }

/** One trip: overview, every booking and cost line item, places, people and a photo diary. Stored only on this phone. */
@Composable
fun TripDetailScreen(vm: AppViewModel, nav: NavHostController, tripId: Long) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    val trips by vm.trips.collectAsStateWithLifecycle()
    val trip = trips.firstOrNull { it.id == tripId }
    if (trip == null) {
        SubScreen("Trip", onBack = { nav.popBackStack() }) { item { EmptyState("🧳", "This trip was removed", "Its expense entries are still in your records.") } }
        return
    }
    val allItems by vm.tripItems.collectAsStateWithLifecycle()
    val allPlaces by vm.tripPlaces.collectAsStateWithLifecycle()
    val allPeople by vm.tripPeople.collectAsStateWithLifecycle()
    val allDiary by vm.tripDiary.collectAsStateWithLifecycle()
    val attachments by vm.attachments.collectAsStateWithLifecycle()
    val lineItems = remember(allItems, tripId) { TripPlan.ordered(allItems.filter { it.tripId == tripId }) }
    val places = remember(allPlaces, tripId) { allPlaces.filter { it.tripId == tripId } }
    val people = remember(allPeople, tripId) { allPeople.filter { it.tripId == tripId } }
    val diary = remember(allDiary, tripId) { allDiary.filter { it.tripId == tripId } }
    val txnIds = remember(data.allTxns) { data.allTxns.map { it.id }.toSet() }
    val spent = remember(trip, data.allTxns) { Trips.spent(trip, data.allTxns) }
    val today = LocalDate.now()
    val summary = remember(lineItems, people, spent, txnIds) { TripPlan.summary(lineItems, people.size, spent, txnIds, System.currentTimeMillis()) }
    val settle = remember(lineItems, people) { TripPlan.settle(lineItems, people) }
    val perDay = remember(trip, lineItems, data.allTxns, txnIds) {
        val tagged = data.allTxns.filter { it.type == com.kharchacheck.app.core.model.TxType.EXPENSE && Trips.hasTag(it, trip.tag) }.map { it.date to it.amountPaise }
        TripPlan.byDay(trip, lineItems, tagged, txnIds)
    }
    val tripPhotos = remember(diary, attachments) { diary.sortedBy { it.day }.flatMap { e -> e.photoIds.mapNotNull { id -> attachments.firstOrNull { it.id == id } } } }
    val context = LocalContext.current

    var tab by rememberSaveable { mutableStateOf(TripTab.OVERVIEW) }
    var editTrip by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<TripItem?>(null) }
    var editPlace by remember { mutableStateOf<TripPlace?>(null) }
    var editPerson by remember { mutableStateOf<TripPerson?>(null) }
    var editDiary by remember { mutableStateOf<DiaryEntry?>(null) }
    var viewing by remember { mutableStateOf<Attachment?>(null) }

    fun newItem(kind: TripItemKind = TripItemKind.TRAIN) = TripItem(tripId = tripId, kind = kind, title = "")
    fun newDiary() = DiaryEntry(tripId = tripId, day = if (trip.isOn(today)) today else trip.start)

    SubScreen("${trip.emoji} ${trip.name}", onBack = { nav.popBackStack() }) {
        item {
            KcCard {
                Text(listOf(trip.destination, TripPlan.status(trip, today)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                Text("${trip.start.format(dayFmt)} – ${trip.end.format(dayFmt)} · ${trip.days} days" +
                    (if (people.isNotEmpty()) " · ${people.size} going" else "") + (if (trip.foreign) " · ${trip.currency}" else ""),
                    style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("✏️ Edit trip", Kc.colors.chip, Kc.colors.text) { editTrip = true }
                    Pill("📤 Share trip", Kc.colors.chip, Kc.colors.text) {
                        val text = TripPlan.shareText(trip, summary, lineItems, places, people, diary)
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                        runCatching { context.startActivity(Intent.createChooser(send, "Share ${trip.name}")) }
                    }
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TripTab.entries) { t ->
                    val n = when (t) { TripTab.COSTS -> lineItems.size; TripTab.PLACES -> places.size; TripTab.PEOPLE -> people.size; TripTab.DIARY -> diary.size; else -> 0 }
                    Pill(t.label + if (n > 0) " ($n)" else "", if (tab == t) Ink else Kc.colors.chip, if (tab == t) Haldi else Kc.colors.text) { tab = t }
                }
            }
        }

        when (tab) {
            TripTab.OVERVIEW -> {
                item { QuickSpend(trip, people) { vm.saveTripItem(it) } }
                item { CostCard(trip, summary) }
                if (people.size >= 2) item { SettleCard(settle, lineItems.any { it.paidBy.isNotBlank() }) }
                if (perDay.any { it.second > 0 }) item {
                    KcCard(title = "Spend by day") {
                        val max = perDay.maxOf { it.second }.coerceAtLeast(1)
                        perDay.forEachIndexed { idx, (day, amt) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Day ${idx + 1}", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted, modifier = Modifier.width(48.dp))
                                ProgressBar(amt.toFloat() / max, Haldi, Modifier.weight(1f), height = 8.dp)
                                Text(if (amt > 0) Money.compact(amt) else "–", style = MaterialTheme.typography.bodySmall, color = Kc.colors.text, modifier = Modifier.width(64.dp))
                            }
                        }
                    }
                }
                if (tripPhotos.isNotEmpty()) item {
                    KcCard(title = "Moments", onClick = { tab = TripTab.DIARY }) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(tripPhotos, key = { it.id }) { a -> AttachmentThumb(vm, a, 110.dp) { viewing = a } }
                        }
                        Text("${diary.size} diary entries · tap to read", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    }
                }
                summary.next?.let { n ->
                    item {
                        KcCard(onClick = { editItem = n }) {
                            Text("Next up", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                            ItemSummary(n)
                        }
                    }
                }
                if (summary.byKind.isNotEmpty()) item {
                    KcCard(title = "Where the money goes") {
                        summary.byKind.forEach { (k, v) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${k.emoji}  ${k.label}", style = MaterialTheme.typography.bodyLarge, color = Kc.colors.text, modifier = Modifier.weight(1f))
                                Text(Money.format(v), style = MaterialTheme.typography.titleSmall, color = Kc.colors.text)
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { editItem = newItem() }, modifier = Modifier.weight(1f)) { Text("🎫 Booking") }
                        OutlinedButton(onClick = { editPlace = TripPlace(tripId = tripId, name = "", sortOrder = places.size) }, modifier = Modifier.weight(1f)) { Text("📍 Place") }
                        OutlinedButton(onClick = { editDiary = newDiary() }, modifier = Modifier.weight(1f)) { Text("📔 Diary") }
                    }
                }
                if (trip.notes.isNotBlank()) item { KcCard(title = "Notes") { Text(trip.notes, style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text) } }
            }

            TripTab.COSTS -> {
                item {
                    Text("Every ticket, stay, meal and small spend of this trip, line by line. These are for the trip only: they don't touch your monthly budget unless you tap “Add to expenses” (and then they're counted once).",
                        style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
                }
                item { QuickSpend(trip, people) { vm.saveTripItem(it) } }
                if (lineItems.isEmpty()) item { EmptyState("🎫", "No bookings yet", "Train tickets, flights, the hotel, cabs, entry tickets or a planned shopping budget.", "Add the first one") { editItem = newItem() } }
                var lastDay: LocalDate? = null
                lineItems.forEach { i ->
                    val day = i.startAt?.let { dayOf(it) }
                    if (day != lastDay) {
                        lastDay = day
                        item { Text(day?.format(dayFmt) ?: "No date yet", style = MaterialTheme.typography.titleSmall, color = Kc.colors.muted, modifier = Modifier.padding(top = 6.dp)) }
                    }
                    item(key = "item-${i.id}") {
                        val recorded = TripPlan.isRecorded(i, txnIds)
                        KcCard(onClick = { editItem = i }) {
                            ItemSummary(i)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pill(when { recorded -> "✓ In expenses"; i.paid -> "Booked"; else -> "Planned" },
                                    if (recorded) Mint.copy(alpha = 0.18f) else Kc.colors.chip, Kc.colors.text)
                                Spacer(Modifier.weight(1f))
                                if (!recorded && i.costPaise > 0) Pill("+ Add to expenses", Ink, Haldi) { vm.recordTripItem(trip, i) }
                            }
                        }
                    }
                }
                if (lineItems.isNotEmpty()) item {
                    KcCard {
                        Row { Text("Total of all items", modifier = Modifier.weight(1f), color = Kc.colors.text); Text(Money.format(summary.itemsTotal), fontWeight = FontWeight.Bold, color = Kc.colors.text) }
                        if (summary.notRecorded > 0) Text("${Money.format(summary.notRecorded)} not in expenses yet", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    }
                }
                item { BigButton("+ Add booking or cost") { editItem = newItem() } }
            }

            TripTab.PLACES -> {
                item { Text("Where you're going, day by day. Tick them off as you go.", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted) }
                if (places.isEmpty()) item { EmptyState("📍", "No places yet", "Beaches, temples, forts, cafés, the friend's house: add them to plan the days.", "Add a place") { editPlace = TripPlace(tripId = tripId, name = "") } }
                val grouped = places.sortedWith(compareBy({ it.day ?: LocalDate.MAX }, { it.sortOrder }, { it.id })).groupBy { it.day }
                grouped.forEach { (day, list) ->
                    item { Text(day?.let { "Day ${java.time.temporal.ChronoUnit.DAYS.between(trip.start, it) + 1} · ${it.format(dayFmt)}" } ?: "Any day",
                        style = MaterialTheme.typography.titleSmall, color = Kc.colors.muted, modifier = Modifier.padding(top = 6.dp)) }
                    list.forEach { p ->
                        item(key = "place-${p.id}") {
                            KcCard(padding = 10.dp) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(p.visited, { vm.saveTripPlace(p.copy(visited = it)) })
                                    Column(Modifier.weight(1f).clickable { editPlace = p }) {
                                        Text(p.name, style = MaterialTheme.typography.titleMedium, color = if (p.visited) Kc.colors.muted else Kc.colors.text)
                                        if (p.note.isNotBlank()) Text(p.note, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                                    }
                                }
                            }
                        }
                    }
                }
                item { BigButton("+ Add place") { editPlace = TripPlace(tripId = tripId, name = "", sortOrder = places.size) } }
            }

            TripTab.PEOPLE -> {
                item { Text("Who's going, you included (a couple: add both of you). Costs are shared equally, and picking who paid each item shows who owes whom at the end.", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted) }
                if (people.size >= 2) item { SettleCard(settle, lineItems.any { it.paidBy.isNotBlank() }) }
                if (people.isEmpty()) item { EmptyState("👨‍👩‍👧", "Just you so far", "Add family or friends to see the cost per person and note who each ticket is for.", "Add a person") { editPerson = TripPerson(tripId = tripId, name = "") } }
                people.forEach { p ->
                    item(key = "person-${p.id}") {
                        KcCard(onClick = { editPerson = p }, padding = 14.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🙂", fontSize = 24.sp); Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                                    if (p.note.isNotBlank()) Text(p.note, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                                }
                                summary.perPerson?.let { Text("≈ ${Money.compact(it)}", style = MaterialTheme.typography.titleSmall, color = Kc.colors.text) }
                            }
                        }
                    }
                }
                item { BigButton("+ Add person") { editPerson = TripPerson(tripId = tripId, name = "") } }
            }

            TripTab.DIARY -> {
                item { Text("Memorable moments, day by day, with photos. Private and encrypted on this phone.", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted) }
                if (diary.isEmpty()) item { EmptyState("📔", "Your trip diary is empty", "Write what made today special: the sunset, the food, what the kids said. Add a photo or two.", "Write the first entry") { editDiary = newDiary() } }
                diary.forEach { e ->
                    item(key = "diary-${e.id}") {
                        KcCard(onClick = { editDiary = e }) {
                            Text("${e.day.format(dayFmt)}  ${e.mood}", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                            if (e.title.isNotBlank()) Text(e.title, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                            if (e.place.isNotBlank()) Text("📍 ${e.place}", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                            if (e.body.isNotBlank()) Text(e.body, style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
                            val photos = e.photoIds.mapNotNull { id -> attachments.firstOrNull { it.id == id } }
                            if (photos.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(photos, key = { it.id }) { a -> AttachmentThumb(vm, a, 96.dp) { viewing = a } }
                            }
                        }
                    }
                }
                item { BigButton("+ Write in diary") { editDiary = newDiary() } }
            }
        }
    }

    if (editTrip) TripDialog(trip, taken = trips.filter { it.id != trip.id }.map { it.tag }.toSet(),
        onDismiss = { editTrip = false }, onSave = { vm.saveTrip(it); editTrip = false },
        onDelete = { editTrip = false; vm.deleteTrip(trip); nav.popBackStack() })
    editItem?.let { i -> ItemDialog(trip, i, people, onDismiss = { editItem = null }, onSave = { vm.saveTripItem(it); editItem = null },
        onDelete = { vm.deleteTripItem(i); editItem = null }) }
    editPlace?.let { p -> PlaceDialog(trip, p, onDismiss = { editPlace = null }, onSave = { vm.saveTripPlace(it); editPlace = null },
        onDelete = { vm.deleteTripPlace(p); editPlace = null }) }
    editPerson?.let { p -> PersonDialog(p, onDismiss = { editPerson = null }, onSave = { vm.saveTripPerson(it); editPerson = null },
        onDelete = { vm.deleteTripPerson(p); editPerson = null }) }
    editDiary?.let { e -> DiaryDialog(vm, trip, e, places, attachments, onDismiss = { editDiary = null }, onSave = { vm.saveDiary(it); editDiary = null },
        onDelete = { vm.deleteDiary(e); editDiary = null }) }
    viewing?.let { a -> AttachmentViewer(vm, a, onDismiss = { viewing = null }, onDelete = null) }
}

@Composable
private fun CostCard(trip: Trip, s: TripPlan.Summary) {
    KcCard {
        Text("Trip cost", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
        Text(Money.format(s.expected), style = MaterialTheme.typography.headlineMedium, color = Kc.colors.text)
        if (trip.budgetPaise > 0) {
            val over = s.expected > trip.budgetPaise
            ProgressBar((s.expected.toFloat() / trip.budgetPaise).coerceIn(0f, 1f), if (over) Coral else Mint)
            Text(if (over) "${Money.format(s.expected - trip.budgetPaise)} over the ${Money.format(trip.budgetPaise)} budget"
                else "${Money.format(trip.budgetPaise - s.expected)} left of ${Money.format(trip.budgetPaise)}",
                style = MaterialTheme.typography.bodyMedium, color = if (over) Coral else Kc.colors.text)
        }
        Text("Spent so far ${Money.format(s.spent)}" + if (s.notRecorded > 0) " · bookings still to add ${Money.format(s.notRecorded)}" else "",
            style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
        s.perPerson?.let { Text("About ${Money.format(it)} per person", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted) }
    }
}

@Composable
private fun ItemSummary(i: TripItem) {
    Row(verticalAlignment = Alignment.Top) {
        Text(i.kind.emoji, fontSize = 26.sp); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(i.title.ifBlank { i.kind.label }, style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
            if (i.from.isNotBlank() || i.to.isNotBlank()) Text("${i.from} → ${i.to}", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
            val times = listOfNotNull(i.startAt?.let { whenText(it) }, i.endAt?.let { "until " + whenText(it) }).joinToString(" ")
            if (times.isNotBlank()) Text(times, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
            val extra = listOfNotNull(i.refNo.takeIf { it.isNotBlank() }?.let { "${i.kind.refLabel.substringBefore(" /")} $it" },
                i.details.takeIf { it.isNotBlank() }, i.travellers.takeIf { it.isNotBlank() }?.let { "for $it" },
                i.paidBy.takeIf { it.isNotBlank() }?.let { "paid by $it" }).joinToString(" · ")
            if (extra.isNotBlank()) Text(extra, style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
        }
        Text(if (i.costPaise > 0) Money.format(i.costPaise) else "—", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
    }
}

// ---------------- dialogs ----------------

@Composable
private fun ItemDialog(trip: Trip, i: TripItem, people: List<TripPerson>, onDismiss: () -> Unit, onSave: (TripItem) -> Unit, onDelete: () -> Unit) {
    var kind by rememberSaveable { mutableStateOf(i.kind) }
    var title by rememberSaveable { mutableStateOf(i.title) }
    var from by rememberSaveable { mutableStateOf(i.from) }
    var to by rememberSaveable { mutableStateOf(i.to) }
    var startAt by rememberSaveable { mutableStateOf(i.startAt) }
    var endAt by rememberSaveable { mutableStateOf(i.endAt) }
    var ref by rememberSaveable { mutableStateOf(i.refNo) }
    var details by rememberSaveable { mutableStateOf(i.details) }
    var who by rememberSaveable { mutableStateOf(i.travellers) }
    var cost by rememberSaveable { mutableStateOf(if (i.costPaise > 0) Money.format(i.costPaise).filter { it.isDigit() || it == '.' } else "") }
    var inForeign by rememberSaveable { mutableStateOf(false) }
    var paid by rememberSaveable { mutableStateOf(i.paid) }
    var paidBy by rememberSaveable { mutableStateOf(i.paidBy) }
    var note by rememberSaveable { mutableStateOf(i.note) }
    var confirmDelete by remember { mutableStateOf(false) }

    val typed = if (cost.isBlank()) 0L else Money.parse(cost)
    val rupees = typed?.let { if (inForeign && trip.foreign) Trips.toRupees(it, trip.ratePaise) else it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (i.id == 0L) "Add booking or cost" else "Edit ${i.kind.label.lowercase()}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(TripItemKind.entries) { k -> Pill("${k.emoji} ${k.label}", if (k == kind) Ink else Kc.colors.chip, if (k == kind) Haldi else Kc.colors.text) { kind = k } }
                }
                OutlinedTextField(title, { title = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(when (kind) { TripItemKind.TRAIN -> "Train, e.g. 12779 Goa Express"; TripItemKind.FLIGHT -> "Flight, e.g. IndiGo 6E 512"
                        TripItemKind.STAY -> "Hotel or homestay name"; else -> "What is it?" }) },
                    trailingIcon = { MicButton("What is it?") { title = spokenName(it, 40) } })
                if (kind.hasRoute) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(from, { from = it.take(30) }, label = { Text("From") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(to, { to = it.take(30) }, label = { Text("To") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                DateTimeField(if (kind == TripItemKind.STAY) "Check-in" else if (kind.hasRoute) "Departs" else "When", startAt, trip.start) { startAt = it }
                if (kind.hasRoute || kind == TripItemKind.STAY) DateTimeField(if (kind == TripItemKind.STAY) "Check-out" else "Arrives", endAt, trip.start) { endAt = it }
                OutlinedTextField(ref, { ref = it.take(30) }, label = { Text(kind.refLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(details, { details = it.take(80) }, label = { Text(kind.detailsLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(who, { who = it.take(60) }, label = { Text("For whom (e.g. All, Sourav & Riya)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (people.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { Pill("Everyone", Kc.colors.chip, Kc.colors.text) { who = "Everyone (${people.size})" } }
                    items(people, key = { it.id }) { p -> Pill(p.name, Kc.colors.chip, Kc.colors.text) { who = Trips.addTag(who.removePrefix("Everyone (${people.size})"), p.name) } }
                }
                OutlinedTextField(cost, { v -> cost = v.filter { it.isDigit() || it == '.' }.take(10) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Cost") }, prefix = { Text(if (inForeign && trip.foreign) "${trip.currency} " else "₹ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (trip.foreign) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill("₹", if (!inForeign) Ink else Kc.colors.chip, if (!inForeign) Haldi else Kc.colors.text) { inForeign = false }
                    Pill(trip.currency, if (inForeign) Ink else Kc.colors.chip, if (inForeign) Haldi else Kc.colors.text) { inForeign = true }
                    if (inForeign && rupees != null && rupees > 0) Text("= ${Money.format(rupees)}", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(paid, { paid = it }); Text("Booked and paid", color = Kc.colors.text)
                }
                if (people.size >= 2) {
                    Text("Paid by", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(people, key = { it.id }) { p ->
                            Pill(p.name, if (paidBy == p.name) Ink else Kc.colors.chip, if (paidBy == p.name) Haldi else Kc.colors.text) { paidBy = if (paidBy == p.name) "" else p.name; paid = true }
                        }
                    }
                }
                OutlinedTextField(note, { note = it.take(200) }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { MicButton("Add a note") { note = appendSpoken(note, it, 200) } })
                if (i.id != 0L) Pill("Delete", Kc.colors.warnBg, Coral) { confirmDelete = true }
            }
        },
        confirmButton = {
            TextButton(enabled = (title.isNotBlank() || kind != TripItemKind.OTHER) && rupees != null, onClick = {
                val fx = if (inForeign && trip.foreign && typed != null && typed > 0) "${trip.currency} ${Money.format(typed ?: 0L).drop(1)} at ₹${"%.2f".format(Locale.ENGLISH, trip.ratePaise / 100.0)}" else ""
                onSave(i.copy(kind = kind, title = title.trim().ifBlank { kind.label }, from = from, to = to, startAt = startAt, endAt = endAt,
                    refNo = ref, details = details, travellers = who, costPaise = rupees ?: 0L, paid = paid, paidBy = paidBy,
                    note = listOf(note.trim(), fx).filter { it.isNotBlank() }.joinToString(" · ")))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this item?") },
        text = { Text(if (i.txnId != null) "Its expense entry stays in your records; delete that from Activity if you want." else "This can't be undone.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = Coral) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

/** A date + time button: date picker, then a time box. Empty until set. */
@Composable
private fun DateTimeField(label: String, value: Long?, defaultDay: LocalDate, onChange: (Long?) -> Unit) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<LocalDate?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f)) { Text(if (value != null) "$label: ${whenText(value)}" else "📅 $label (optional)") }
        if (value != null) TextButton(onClick = { onChange(null) }) { Text("Clear") }
    }
    if (pickDate) {
        val start = value?.let { dayOf(it) } ?: defaultDay
        val ds = rememberDatePickerState(initialSelectedDateMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = {
                pickDate = false
                ds.selectedDateMillis?.let { pickTime = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
            }) { Text("Next") } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = ds) }
    }
    pickTime?.let { day ->
        val old = value?.let { Instant.ofEpochMilli(it).atZone(zone) }
        val ts = rememberTimePickerState(initialHour = old?.hour ?: 9, initialMinute = old?.minute ?: 0, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = null },
            title = { Text("Time") },
            text = { TimeInput(state = ts) },
            confirmButton = { TextButton(onClick = {
                onChange(day.atTime(ts.hour, ts.minute).atZone(zone).toInstant().toEpochMilli()); pickTime = null
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { onChange(day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()); pickTime = null }) { Text("No time") } },
        )
    }
}

/** Chips for each day of the trip (plus "Any day"). */
@Composable
private fun DayChips(trip: Trip, selected: LocalDate?, allowNone: Boolean, onPick: (LocalDate?) -> Unit) {
    val days = (0 until trip.days.coerceAtMost(60)).map { trip.start.plusDays(it.toLong()) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (allowNone) item { Pill("Any day", if (selected == null) Ink else Kc.colors.chip, if (selected == null) Haldi else Kc.colors.text) { onPick(null) } }
        items(days) { d ->
            val on = d == selected
            Pill("Day ${days.indexOf(d) + 1} · ${d.dayOfMonth} ${d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}",
                if (on) Ink else Kc.colors.chip, if (on) Haldi else Kc.colors.text) { onPick(d) }
        }
    }
}

@Composable
private fun PlaceDialog(trip: Trip, p: TripPlace, onDismiss: () -> Unit, onSave: (TripPlace) -> Unit, onDelete: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(p.name) }
    var note by rememberSaveable { mutableStateOf(p.note) }
    var day by remember { mutableStateOf(p.day) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (p.id == 0L) "Add a place" else "Edit place") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Place, e.g. Baga beach, Fort Aguada") }, singleLine = true,
                    trailingIcon = { MicButton("Which place?") { name = spokenName(it, 40) } }, modifier = Modifier.fillMaxWidth())
                DayChips(trip, day, allowNone = true) { day = it }
                OutlinedTextField(note, { note = it.take(120) }, label = { Text("Note (timings, tickets, tips)") }, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { MicButton("Add a note") { note = appendSpoken(note, it, 120) } })
                if (p.id != 0L) Pill("Delete", Kc.colors.warnBg, Coral) { onDelete() }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(p.copy(name = name, note = note, day = day)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PersonDialog(p: TripPerson, onDismiss: () -> Unit, onSave: (TripPerson) -> Unit, onDelete: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(p.name) }
    var note by rememberSaveable { mutableStateOf(p.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (p.id == 0L) "Who's going?" else "Edit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(30) }, label = { Text("Name") }, singleLine = true,
                    trailingIcon = { MicButton("Who is it?") { name = spokenName(it, 30) } }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it.take(60) }, label = { Text("Note (age, seat preference, ID to carry)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (p.id != 0L) Pill("Remove", Kc.colors.warnBg, Coral) { onDelete() }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(p.copy(name = name, note = note)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DiaryDialog(
    vm: AppViewModel, trip: Trip, e: DiaryEntry, places: List<TripPlace>, attachments: List<Attachment>,
    onDismiss: () -> Unit, onSave: (DiaryEntry) -> Unit, onDelete: () -> Unit,
) {
    val platform = LocalPlatform.current
    var day by remember { mutableStateOf(e.day) }
    var mood by rememberSaveable { mutableStateOf(e.mood) }
    var title by rememberSaveable { mutableStateOf(e.title) }
    var place by rememberSaveable { mutableStateOf(e.place) }
    var body by rememberSaveable { mutableStateOf(e.body) }
    val photos = remember { mutableStateListOf<Long>().apply { addAll(e.photoIds) } }
    // Photos added in this dialog but not saved are removed again on Cancel, so nothing is left behind.
    val addedNow = remember { mutableStateListOf<Long>() }
    var confirmDelete by remember { mutableStateOf(false) }

    fun cancel() { addedNow.forEach { vm.removeTripPhoto(it) }; onDismiss() }

    AlertDialog(
        onDismissRequest = { cancel() },
        title = { Text(if (e.id == 0L) "Trip diary" else "Edit diary entry") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DayChips(trip, day, allowNone = false) { if (it != null) day = it }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("😊", "🤩", "😍", "🌅", "😋", "😴", "🥵", "🌧️", "😅", "🥹")) { m ->
                        Pill(m, if (m == mood) Haldi else Kc.colors.chip, Ink) { mood = m }
                    }
                }
                OutlinedTextField(title, { title = it.take(60) }, label = { Text("Moment, e.g. Sunset at Baga") }, singleLine = true,
                    trailingIcon = { MicButton("What was the moment?") { title = spokenName(it, 60) } }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(place, { place = it.take(40) }, label = { Text("Where") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (places.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(places, key = { it.id }) { p -> Pill("📍 ${p.name}", if (place == p.name) Ink else Kc.colors.chip, if (place == p.name) Haldi else Kc.colors.text) { place = p.name } }
                }
                OutlinedTextField(body, { body = it.take(4000) }, label = { Text("What happened? (tap 🎤 and just talk)") }, minLines = 4, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { MicButton("Tell me about today") { body = appendSpoken(body, it, 4000) } })
                val shown = photos.mapNotNull { id -> attachments.firstOrNull { it.id == id } }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    items(shown, key = { it.id }) { a ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AttachmentThumb(vm, a, 72.dp) {}
                            TextButton(onClick = { photos.remove(a.id); if (a.id in addedNow) { addedNow.remove(a.id); vm.removeTripPhoto(a.id) } }) { Text("Remove", fontSize = 12.sp) }
                        }
                    }
                    if (photos.size < 10) item {
                        OutlinedButton(onClick = { platform.pickImage { uri -> vm.addTripPhoto(uri) { a -> photos.add(a.id); addedNow.add(a.id) } } }) { Text("📸 Add photo") }
                    }
                }
                if (e.id != 0L) Pill("Delete entry", Kc.colors.warnBg, Coral) { confirmDelete = true }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() || body.isNotBlank() || photos.isNotEmpty(), onClick = {
                // Photos removed from a saved entry are deleted from the phone when it's saved.
                e.photoIds.filter { it !in photos }.forEach { vm.removeTripPhoto(it) }
                onSave(e.copy(day = day, mood = mood, title = title, place = place, body = body, photoIds = photos.toList()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { cancel() }) { Text("Cancel") } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this diary entry?") },
        text = { Text("The entry and its photos are deleted from this phone. This can't be undone.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = Coral) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

/**
 * Log a spend in one line while travelling: "240 chai at the beach", typed or spoken. It becomes a trip line item
 * (amount, kind, what) for reference; it doesn't touch the monthly budget unless added to expenses later.
 */
@Composable
private fun QuickSpend(trip: Trip, people: List<TripPerson>, onAdd: (TripItem) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var payer by rememberSaveable { mutableStateOf(people.firstOrNull()?.name.orEmpty()) }
    var inForeign by rememberSaveable { mutableStateOf(trip.foreign) }
    val parsed = remember(text) { if (text.isBlank()) null else VoiceEntry.parse(text) }
    val typed = parsed?.amountPaise
    val rupees = typed?.let { if (inForeign && trip.foreign) Trips.toRupees(it, trip.ratePaise) else it }

    fun add() {
        val r = parsed ?: return
        val amt = rupees ?: return
        val kind = TripPlan.kindFor(r.categoryKey)
        val what = listOf(r.payee, r.note).firstOrNull { it.isNotBlank() }
            ?: text.replace(Regex("[₹0-9.,]+|rupees?|rs\\.?", RegexOption.IGNORE_CASE), " ").trim().replaceFirstChar { it.titlecase() }.ifBlank { kind.label }
        val fx = if (inForeign && trip.foreign) "${trip.currency} ${Money.format(typed ?: 0L).drop(1)} at ₹${"%.2f".format(Locale.ENGLISH, trip.ratePaise / 100.0)}" else ""
        onAdd(TripItem(tripId = trip.id, kind = kind, title = what.take(40), startAt = System.currentTimeMillis(), costPaise = amt, paid = true,
            paidBy = if (people.size >= 2) payer else "", note = listOf(r.note.takeIf { it != what }.orEmpty(), fx).filter { it.isNotBlank() }.joinToString(" · ")))
        text = ""
    }

    KcCard {
        Text("Spent something?", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
        OutlinedTextField(text, { text = it.take(80) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. 240 chai at the beach") },
            trailingIcon = { MicButton("Say what you spent, e.g. 240 chai at the beach") { text = it } })
        if (trip.foreign) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("₹", if (!inForeign) Ink else Kc.colors.chip, if (!inForeign) Haldi else Kc.colors.text) { inForeign = false }
            Pill(trip.currency, if (inForeign) Ink else Kc.colors.chip, if (inForeign) Haldi else Kc.colors.text) { inForeign = true }
        }
        if (people.size >= 2) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Text("Paid by", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted, modifier = Modifier.padding(top = 6.dp)) }
            items(people, key = { it.id }) { p -> Pill(p.name, if (payer == p.name) Ink else Kc.colors.chip, if (payer == p.name) Haldi else Kc.colors.text) { payer = p.name } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    parsed == null -> "Type or tap 🎤. Amount first is easiest."
                    rupees == null -> "Add an amount, e.g. 240"
                    else -> "${TripPlan.kindFor(parsed.categoryKey).emoji} ${Money.format(rupees)}" + if (inForeign && trip.foreign) " (${trip.currency} ${Money.format(typed!!).drop(1)})" else ""
                },
                style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted, modifier = Modifier.weight(1f),
            )
            Pill("Add", if (rupees != null) Ink else Kc.colors.chip, if (rupees != null) Haldi else Kc.colors.muted) { if (rupees != null) add() }
        }
    }
}

/** Who paid what, each person's equal share, and the fewest payments to square up. */
@Composable
private fun SettleCard(settle: Pair<List<TripPlan.Balance>, List<TripPlan.Transfer>>, anyPayer: Boolean) {
    val (balances, transfers) = settle
    KcCard(title = "Settle up") {
        if (!anyPayer) {
            Text("Pick who paid on each item (or in “Spent something?”) and this shows who owes whom, shared equally.",
                style = MaterialTheme.typography.bodyMedium, color = Kc.colors.muted)
            return@KcCard
        }
        balances.forEach { b ->
            Row {
                Text(b.name, color = Kc.colors.text, modifier = Modifier.weight(1f))
                Text("paid ${Money.compact(b.paid)} · share ${Money.compact(b.share)}", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
            }
        }
        if (transfers.isEmpty()) Text("All square ✓", style = MaterialTheme.typography.titleMedium, color = Mint)
        else transfers.forEach { t -> Text("${t.from} pays ${t.to} ${Money.format(t.amountPaise)}", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text) }
        Text("Only items with a payer are counted. Shares are equal.", style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
    }
}

/**
 * Memories: every trip as a keepsake card (cover photo, where, who, moments, what it cost) and
 * "On this day" moments from earlier years. Tap any card to relive the trip.
 */
@Composable
fun MemoriesScreen(vm: AppViewModel, nav: NavHostController) {
    val d by vm.data.collectAsStateWithLifecycle()
    val data = d ?: return
    val trips by vm.trips.collectAsStateWithLifecycle()
    val allItems by vm.tripItems.collectAsStateWithLifecycle()
    val allPlaces by vm.tripPlaces.collectAsStateWithLifecycle()
    val allPeople by vm.tripPeople.collectAsStateWithLifecycle()
    val allDiary by vm.tripDiary.collectAsStateWithLifecycle()
    val attachments by vm.attachments.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val txnIds = remember(data.allTxns) { data.allTxns.map { it.id }.toSet() }
    val onThisDay = remember(allDiary, today) { TripPlan.onThisDay(allDiary, today) }
    var viewing by remember { mutableStateOf<Attachment?>(null) }

    SubScreen("Memories", onBack = { nav.popBackStack() }) {
        if (onThisDay.isNotEmpty()) {
            item { Text("On this day", style = MaterialTheme.typography.titleLarge, color = Kc.colors.text) }
            onThisDay.forEach { e ->
                val trip = trips.firstOrNull { it.id == e.tripId }
                item(key = "otd-${e.id}") {
                    KcCard(onClick = { nav.navigate(com.kharchacheck.app.ui.nav.TripRoute(e.tripId)) }) {
                        Text("${today.year - e.day.year} year${if (today.year - e.day.year > 1) "s" else ""} ago · ${trip?.let { "${it.emoji} ${it.name}" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                        Text("${e.mood} ${e.title.ifBlank { e.place.ifBlank { "A moment" } }}", style = MaterialTheme.typography.titleMedium, color = Kc.colors.text)
                        if (e.body.isNotBlank()) Text(e.body, style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text, maxLines = 4)
                        val photos = e.photoIds.mapNotNull { id -> attachments.firstOrNull { it.id == id } }
                        if (photos.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(photos, key = { it.id }) { a -> AttachmentThumb(vm, a, 110.dp) { viewing = a } }
                        }
                    }
                }
            }
        }
        item { Text("Your trips", style = MaterialTheme.typography.titleLarge, color = Kc.colors.text) }
        if (trips.isEmpty()) item {
            EmptyState("📸", "No trips yet", "Every trip you add keeps its bookings, spends and diary here, to look back on any time.",
                "Plan a trip") { nav.navigate(com.kharchacheck.app.ui.nav.TripsRoute) }
        }
        trips.sortedByDescending { it.start }.forEach { t ->
            item(key = "mem-${t.id}") {
                val diary = allDiary.filter { it.tripId == t.id }
                val photos = diary.sortedBy { it.day }.flatMap { e -> e.photoIds.mapNotNull { id -> attachments.firstOrNull { it.id == id } } }
                val lineItems = allItems.filter { it.tripId == t.id }
                val people = allPeople.filter { it.tripId == t.id }
                val places = allPlaces.filter { it.tripId == t.id }
                val cost = TripPlan.summary(lineItems, people.size, Trips.spent(t, data.allTxns), txnIds, System.currentTimeMillis()).expected
                KcCard(onClick = { nav.navigate(com.kharchacheck.app.ui.nav.TripRoute(t.id)) }) {
                    if (photos.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(photos.take(8), key = { it.id }) { a -> AttachmentThumb(vm, a, 120.dp) { viewing = a } }
                    }
                    Text("${t.emoji} ${t.name}", style = MaterialTheme.typography.titleLarge, color = Kc.colors.text)
                    Text(listOf(t.destination, "${t.start.format(dayFmt)} – ${t.end.format(dayFmt)} ${t.end.year}", TripPlan.status(t, today))
                        .filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    if (people.isNotEmpty()) Text("With ${people.joinToString(", ") { it.name }}", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
                    Text(listOf(
                        if (cost > 0) "${Money.format(cost)} spent" else "",
                        if (places.isNotEmpty()) "${places.count { it.visited }}/${places.size} places" else "",
                        if (diary.isNotEmpty()) "${diary.size} moments" else "",
                        if (photos.isNotEmpty()) "${photos.size} photos" else "",
                    ).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Tap to add bookings, spends and memories" },
                        style = MaterialTheme.typography.bodySmall, color = Kc.colors.muted)
                    diary.maxByOrNull { it.body.length }?.takeIf { it.body.isNotBlank() }?.let { best ->
                        Text("“${best.body.take(140)}${if (best.body.length > 140) "…" else ""}”", style = MaterialTheme.typography.bodyMedium, color = Kc.colors.text)
                    }
                }
            }
        }
    }
    viewing?.let { a -> AttachmentViewer(vm, a, onDismiss = { viewing = null }, onDelete = null) }
}
