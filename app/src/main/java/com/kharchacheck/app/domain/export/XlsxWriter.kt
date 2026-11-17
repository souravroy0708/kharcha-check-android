package com.kharchacheck.app.domain.export

import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal, dependency-free .xlsx writer: real numbers (so SUM works), real dates, a bold frozen header,
 * auto-filter and sensible column widths. Opens in Excel, Google Sheets, LibreOffice and WPS.
 */
object XlsxWriter {
    sealed interface Cell
    data class Text(val v: String) : Cell
    data class Num(val v: Double) : Cell
    /** Amount in paise, shown as rupees with 2 decimals and thousands separators. */
    data class Money(val paise: Long) : Cell
    data class Day(val v: LocalDate) : Cell
    data object Blank : Cell

    data class Sheet(val name: String, val header: List<String>, val rows: List<List<Cell>>, val widths: List<Int> = emptyList())

    fun write(sheets: List<Sheet>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            fun put(name: String, xml: String) { z.putNextEntry(ZipEntry(name)); z.write(xml.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            put("[Content_Types].xml", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
                append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>""")
                append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
                append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
                sheets.indices.forEach { append("""<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""") }
                append("</Types>")
            })
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
                sheets.forEachIndexed { i, s -> append("""<sheet name="${esc(s.name.take(31))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
                append("</sheets>")
                append("<definedNames>")
                sheets.forEachIndexed { i, s -> if (s.rows.isNotEmpty()) append("""<definedName name="_xlnm._FilterDatabase" localSheetId="$i" hidden="1">'${esc(s.name.take(31))}'!${'$'}A${'$'}1:${'$'}${col(s.header.size - 1)}${'$'}${s.rows.size + 1}</definedName>""") }
                append("</definedNames></workbook>")
            })
            put("xl/_rels/workbook.xml.rels", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                sheets.indices.forEach { append("""<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""") }
                append("""<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            })
            // Styles: 0 default, 1 bold header, 2 date, 3 money #,##0.00
            put("xl/styles.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="dd-mmm-yyyy"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFFFE9A8"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="4"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="4" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs></styleSheet>""")
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        }
        return bos.toByteArray()
    }

    private fun sheetXml(s: Sheet): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        if (s.widths.isNotEmpty()) {
            append("<cols>"); s.widths.forEachIndexed { i, w -> append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }; append("</cols>")
        }
        append("<sheetData>")
        append("""<row r="1">"""); s.header.forEachIndexed { c, h -> append(cell(Text(h), c, 1, style = 1)) }; append("</row>")
        s.rows.forEachIndexed { r, row ->
            append("""<row r="${r + 2}">"""); row.forEachIndexed { c, v -> append(cell(v, c, r + 2)) }; append("</row>")
        }
        append("</sheetData>")
        if (s.rows.isNotEmpty()) append("""<autoFilter ref="A1:${col(s.header.size - 1)}${s.rows.size + 1}"/>""")
        append("</worksheet>")
    }

    private val EPOCH = LocalDate.of(1899, 12, 30)

    private fun cell(v: Cell, c: Int, r: Int, style: Int = 0): String {
        val ref = "${col(c)}$r"
        return when (v) {
            is Text -> """<c r="$ref" t="inlineStr"${if (style > 0) " s=\"$style\"" else ""}><is><t xml:space="preserve">${esc(v.v)}</t></is></c>"""
            is Num -> """<c r="$ref"><v>${v.v}</v></c>"""
            is Money -> """<c r="$ref" s="3"><v>${"%d.%02d".format(v.paise / 100, kotlin.math.abs(v.paise % 100)).let { if (v.paise < 0 && v.paise / 100 == 0L) "-$it" else it }}</v></c>"""
            is Day -> """<c r="$ref" s="2"><v>${ChronoUnit.DAYS.between(EPOCH, v.v)}</v></c>"""
            Blank -> ""
        }
    }

    fun col(i: Int): String { var n = i + 1; val sb = StringBuilder(); while (n > 0) { val m = (n - 1) % 26; sb.insert(0, ('A' + m)); n = (n - 1) / 26 }; return sb.toString() }

    private fun esc(s: String) = buildString {
        for (ch in s) when {
            ch == '&' -> append("&amp;"); ch == '<' -> append("&lt;"); ch == '>' -> append("&gt;"); ch == '"' -> append("&quot;")
            ch.code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r' -> {} // invalid in XML
            else -> append(ch)
        }
    }
}
