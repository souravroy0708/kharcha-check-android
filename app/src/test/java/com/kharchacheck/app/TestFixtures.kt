package com.kharchacheck.app

import com.kharchacheck.app.core.model.Category
import com.kharchacheck.app.core.model.DefaultCategories
import com.kharchacheck.app.core.model.Txn
import com.kharchacheck.app.core.model.TxType
import java.time.LocalDate
import java.time.ZoneId

object Fx {
    val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    val categories: List<Category> = DefaultCategories.all.mapIndexed { i, c -> c.copy(id = i + 1L) }
    val byId = categories.associateBy { it.id }
    fun id(key: String) = categories.first { it.key == key }.id

    fun tx(key: String, rupees: Long, date: LocalDate, type: TxType? = null, hour: Int = 12): Txn {
        val cat = categories.first { it.key == key }
        return Txn(
            amountPaise = rupees * 100,
            type = type ?: cat.bucket.type,
            categoryId = cat.id,
            epochMillis = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
            zoneId = zone.id,
        )
    }
}
