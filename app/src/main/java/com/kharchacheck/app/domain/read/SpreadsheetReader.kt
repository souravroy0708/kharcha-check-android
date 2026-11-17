package com.kharchacheck.app.domain.read

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Reads the spreadsheet downloads Indian netbanking sites give, without a heavy Excel library:
 *  - .xlsx (a zip of XML files): first worksheet, shared strings and inline strings
 *  - "fake .xls" that is really an HTML table (SBI and several other banks export these)
 *  - CSV / TSV / any delimited text
 * Real binary .xls (old Excel 97) isn't supported; the caller shows how to save it as .xlsx or CSV.
 */
object SpreadsheetReader {

    class UnsupportedFormatException(message: String) : Exception(message)

    fun read(bytes: ByteArray): List<List<String>> = when {
        isZip(bytes) -> xlsx(bytes)
        isOle(bytes) -> throw UnsupportedFormatException(
            "This is an old-style Excel (.xls) file. Open it in Excel or Google Sheets and save or download it as .xlsx or CSV, then attach that.")
        else -> {
            val text = decodeText(bytes)
            if (Regex("""<\s*table""", RegexOption.IGNORE_CASE).containsMatchIn(text)) html(text) else delimited(text)
        }
    }

    private fun isZip(b: ByteArray) = b.size > 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte()
    private fun isOle(b: ByteArray) = b.size > 8 && b[0] == 0xD0.toByte() && b[1] == 0xCF.toByte() && b[2] == 0x11.toByte() && b[3] == 0xE0.toByte()

    fun decodeText(bytes: ByteArray): String = when {
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> String(bytes, Charsets.UTF_16LE)
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> String(bytes, Charsets.UTF_16BE)
        else -> String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
    }

    // ---------------------------------------------------------------- delimited text

    fun delimited(text: String): List<List<String>> {
        val lines = text.lines().filter { it.isNotBlank() }
        val delim = listOf(',', '\t', ';', '|').maxByOrNull { d -> lines.take(40).sumOf { l -> l.count { it == d } } } ?: ','
        return lines.map { StatementParser.split(it, delim).map(String::trim) }
    }

    // ---------------------------------------------------------------- HTML table

    fun html(text: String): List<List<String>> {
        val rows = Regex("""<tr\b[^>]*>(.*?)</tr>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(text)
        return rows.map { r ->
            Regex("""<t[dh]\b[^>]*>(.*?)</t[dh]>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(r.groupValues[1])
                .map { c -> unescape(c.groupValues[1].replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), " ").replace(Regex("""<[^>]+>"""), "")).replace(Regex("""\s+"""), " ").trim() }
                .toList()
        }.filter { it.isNotEmpty() }.toList()
    }

    // ---------------------------------------------------------------- XLSX

    fun xlsx(bytes: ByteArray): List<List<String>> {
        val files = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val n = e.name
                if (n == "xl/sharedStrings.xml" || n == "xl/workbook.xml" || n == "xl/_rels/workbook.xml.rels" || (n.startsWith("xl/worksheets/sheet") && n.endsWith(".xml"))) {
                    files[n] = String(z.readBytes(), Charsets.UTF_8)
                }
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let { xml ->
            Regex("""<si\b[^>]*>(.*?)</si>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { si ->
                Regex("""<t\b[^>]*>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL).findAll(si.groupValues[1]).joinToString("") { unescape(it.groupValues[1]) }
            }.toList()
        }.orEmpty()
        val sheetXml = firstSheet(files) ?: throw UnsupportedFormatException("This spreadsheet has no readable sheet.")

        val out = mutableListOf<List<String>>()
        Regex("""<row\b[^>]*>(.*?)</row>""", RegexOption.DOT_MATCHES_ALL).findAll(sheetXml).forEach { row ->
            val cells = sortedMapOf<Int, String>()
            var next = 0
            Regex("""<c\b([^>]*?)(?:/>|>(.*?)</c>)""", RegexOption.DOT_MATCHES_ALL).findAll(row.groupValues[1]).forEach { c ->
                val attrs = c.groupValues[1]
                val inner = c.groupValues[2]
                val ref = Regex("""\br="([A-Z]+)\d+"""").find(attrs)?.groupValues?.get(1)
                val col = ref?.let(::columnIndex) ?: next
                next = col + 1
                val type = Regex("""\bt="(\w+)"""").find(attrs)?.groupValues?.get(1)
                val v = Regex("""<v>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL).find(inner)?.groupValues?.get(1)
                val value = when (type) {
                    "s" -> v?.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                    "inlineStr" -> Regex("""<t\b[^>]*>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL).findAll(inner).joinToString("") { unescape(it.groupValues[1]) }
                    "b" -> if (v == "1") "TRUE" else "FALSE"
                    else -> v?.let(::unescape).orEmpty()
                }
                cells[col] = value.trim()
            }
            if (cells.isNotEmpty()) {
                val width = cells.lastKey() + 1
                out += List(width) { cells[it] ?: "" }
            }
        }
        return out
    }

    private fun firstSheet(files: Map<String, String>): String? {
        // Follow workbook.xml → rels to find the first sheet; fall back to sheet1.xml.
        val wb = files["xl/workbook.xml"]; val rels = files["xl/_rels/workbook.xml.rels"]
        if (wb != null && rels != null) {
            val rid = Regex("""<sheet\b[^>]*r:id="([^"]+)"""").find(wb)?.groupValues?.get(1)
            val target = rid?.let { id -> Regex("""<Relationship\b[^>]*Id="$id"[^>]*Target="([^"]+)"""").find(rels)?.groupValues?.get(1)
                ?: Regex("""<Relationship\b[^>]*Target="([^"]+)"[^>]*Id="$id"""").find(rels)?.groupValues?.get(1) }
            target?.let { t -> files["xl/" + t.removePrefix("/xl/").removePrefix("xl/")] }?.let { return it }
        }
        return files.keys.filter { it.startsWith("xl/worksheets/sheet") }.minByOrNull { it.length * 1000 + (Regex("""\d+""").find(it)?.value?.toIntOrNull() ?: 0) }?.let { files[it] }
    }

    internal fun columnIndex(letters: String): Int = letters.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1

    internal fun unescape(s: String): String = s
        .replace(Regex("""&#x([0-9a-fA-F]+);""")) { it.groupValues[1].toInt(16).toChar().toString() }
        .replace(Regex("""&#(\d+);""")) { it.groupValues[1].toInt().toChar().toString() }
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
}
