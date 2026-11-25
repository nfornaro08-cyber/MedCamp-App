package com.medcamp.monitor

import android.content.Context
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class FilaResumenExcel(
    val nodo: String,
    val comentario: String,
    val ultimaLectura: String,
    val tempAire: Double?,
    val humAire: Double?,
    val presion: Double?,
    val hum15: Double?,
    val hum40: Double?,
    val tempSuelo: Double?,
    val vpd: Double?,
    val bateria: Double?,
    val estado: String
)

data class FilaHistorialExcel(
    val timestamp: Long,
    val nodo: String,
    val comentario: String,
    val fecha: String,
    val tempAire: Double?,
    val humAire: Double?,
    val presion: Double?,
    val hum15: Double?,
    val hum40: Double?,
    val tempSuelo: Double?,
    val vpd: Double?,
    val bateria: Double?
)

data class FilaUmbralExcel(
    val variable: String,
    val minimo: Double?,
    val maximo: Double?,
    val unidad: String
)

/**
 * Genera un archivo .xlsx real (OOXML) sin librerías externas, usando
 * celdas de texto en línea para evitar dependencias de terceros en Android.
 */
object ExcelExporter {

    fun generar(
        contexto: Context,
        resumen: List<FilaResumenExcel>,
        historial: List<FilaHistorialExcel>,
        umbrales: List<FilaUmbralExcel>
    ): File {
        return generarEnCarpeta(
            File(contexto.cacheDir, "exports"),
            resumen,
            historial,
            umbrales
        )
    }

    fun generarEnCarpeta(
        carpeta: File,
        resumen: List<FilaResumenExcel>,
        historial: List<FilaHistorialExcel>,
        umbrales: List<FilaUmbralExcel>
    ): File {
        carpeta.mkdirs()
        val nombre = "MedCamp_informe_${
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        }.xlsx"
        val archivo = File(carpeta, nombre)

        ZipOutputStream(BufferedOutputStream(FileOutputStream(archivo))).use { zip ->
            agregar(zip, "[Content_Types].xml", contentTypes())
            agregar(zip, "_rels/.rels", relacionesRaiz())
            agregar(zip, "xl/workbook.xml", libro())
            agregar(zip, "xl/_rels/workbook.xml.rels", relacionesLibro())
            agregar(zip, "xl/styles.xml", estilos())
            agregar(zip, "xl/worksheets/sheet1.xml", hojaResumen(resumen))
            agregar(zip, "xl/worksheets/sheet2.xml", hojaHistorial(historial))
            agregar(zip, "xl/worksheets/sheet3.xml", hojaUmbrales(umbrales))
        }

        return archivo
    }

    private fun agregar(zip: ZipOutputStream, nombre: String, contenido: String) {
        zip.putNextEntry(ZipEntry(nombre))
        zip.write(contenido.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun hojaResumen(resumen: List<FilaResumenExcel>): String {
        val filas = mutableListOf<List<Any?>>()
        filas.add(
            listOf(
                "Nodo", "Comentario", "Última lectura",
                "Temp. aire (°C)", "Hum. aire (%)", "Presión (hPa)",
                "Hum. suelo 15 cm (%)", "Hum. suelo 40 cm (%)",
                "Temp. suelo (°C)", "VPD (kPa)", "Batería (V)", "Estado"
            )
        )
        resumen.forEach { fila ->
            filas.add(
                listOf(
                    fila.nodo, fila.comentario, fila.ultimaLectura,
                    fila.tempAire, fila.humAire, fila.presion,
                    fila.hum15, fila.hum40, fila.tempSuelo,
                    fila.vpd, fila.bateria, fila.estado
                )
            )
        }
        return hoja(filas, listOf(18.0, 34.0, 20.0, 15.0, 15.0, 16.0, 18.0, 18.0, 17.0, 13.0, 13.0, 20.0))
    }

    private fun hojaHistorial(historial: List<FilaHistorialExcel>): String {
        val filas = mutableListOf<List<Any?>>()
        filas.add(
            listOf(
                "Nodo", "Comentario", "Fecha y hora",
                "Temp. aire (°C)", "Hum. aire (%)", "Presión (hPa)",
                "Hum. suelo 15 cm (%)", "Hum. suelo 40 cm (%)",
                "Temp. suelo (°C)", "VPD (kPa)", "Batería (V)"
            )
        )
        historial.forEach { fila ->
            filas.add(
                listOf(
                    fila.nodo, fila.comentario, fila.fecha,
                    fila.tempAire, fila.humAire, fila.presion,
                    fila.hum15, fila.hum40, fila.tempSuelo,
                    fila.vpd, fila.bateria
                )
            )
        }
        return hoja(filas, listOf(16.0, 32.0, 21.0, 15.0, 15.0, 16.0, 18.0, 18.0, 17.0, 13.0, 13.0))
    }

    private fun hojaUmbrales(umbrales: List<FilaUmbralExcel>): String {
        val filas = mutableListOf<List<Any?>>()
        filas.add(listOf("Variable", "Mínimo", "Máximo", "Unidad"))
        umbrales.forEach { fila ->
            filas.add(listOf(fila.variable, fila.minimo, fila.maximo, fila.unidad))
        }
        return hoja(filas, listOf(34.0, 12.0, 12.0, 10.0))
    }

    private fun hoja(filas: List<List<Any?>>, anchos: List<Double>): String {
        val sb = StringBuilder(64 * 1024)
        sb.append(XML_DECLARATION)
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        sb.append("<sheetViews><sheetView workbookViewId=\"0\">")
        sb.append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
        sb.append("</sheetView></sheetViews>")

        sb.append("<cols>")
        anchos.forEachIndexed { indice, ancho ->
            val col = indice + 1
            sb.append("<col min=\"$col\" max=\"$col\" width=\"$ancho\" customWidth=\"1\"/>")
        }
        sb.append("</cols>")

        sb.append("<sheetData>")
        filas.forEachIndexed { indiceFila, fila ->
            val numeroFila = indiceFila + 1
            val encabezado = indiceFila == 0
            sb.append("<row r=\"$numeroFila\">")
            fila.forEachIndexed { indiceColumna, valor ->
                if (valor != null) {
                    val referencia = columna(indiceColumna + 1) + numeroFila
                    when (valor) {
                        is Number -> sb.append(celdaNumero(referencia, valor))
                        else -> sb.append(celdaTexto(referencia, valor.toString(), encabezado))
                    }
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun celdaTexto(referencia: String, valor: String, encabezado: Boolean): String {
        val estilo = if (encabezado) " s=\"1\"" else ""
        return "<c r=\"$referencia\"$estilo t=\"inlineStr\"><is><t xml:space=\"preserve\">" +
            escapar(valor.take(32000)) + "</t></is></c>"
    }

    private fun celdaNumero(referencia: String, valor: Number): String {
        val numero = valor.toDouble()
        if (!numero.isFinite()) return ""
        val texto = BigDecimal.valueOf(numero).stripTrailingZeros().toPlainString()
        return "<c r=\"$referencia\"><v>$texto</v></c>"
    }

    private fun columna(indice: Int): String {
        var numero = indice
        val sb = StringBuilder()
        while (numero > 0) {
            val resto = (numero - 1) % 26
            sb.insert(0, ('A'.code + resto).toChar())
            numero = (numero - 1) / 26
        }
        return sb.toString()
    }

    private fun escapar(texto: String): String {
        val sb = StringBuilder(texto.length + 16)
        for (caracter in texto) {
            when {
                caracter == '&' -> sb.append("&amp;")
                caracter == '<' -> sb.append("&lt;")
                caracter == '>' -> sb.append("&gt;")
                caracter == '"' -> sb.append("&quot;")
                caracter == '\'' -> sb.append("&apos;")
                caracter.code < 0x20 && caracter != '\t' && caracter != '\n' && caracter != '\r' -> Unit
                else -> sb.append(caracter)
            }
        }
        return sb.toString()
    }

    private fun contentTypes(): String =
        XML_DECLARATION +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
            "<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
            "<Override PartName=\"/xl/worksheets/sheet3.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
            "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
            "</Types>"

    private fun relacionesRaiz(): String =
        XML_DECLARATION +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"

    private fun libro(): String =
        XML_DECLARATION +
            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
            "<sheets>" +
            "<sheet name=\"Resumen\" sheetId=\"1\" r:id=\"rId1\"/>" +
            "<sheet name=\"Historial\" sheetId=\"2\" r:id=\"rId2\"/>" +
            "<sheet name=\"Umbrales\" sheetId=\"3\" r:id=\"rId3\"/>" +
            "</sheets></workbook>"

    private fun relacionesLibro(): String =
        XML_DECLARATION +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>" +
            "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet3.xml\"/>" +
            "<Relationship Id=\"rId4\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
            "</Relationships>"

    private fun estilos(): String =
        XML_DECLARATION +
            "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<fonts count=\"2\">" +
            "<font><sz val=\"11\"/><color theme=\"1\"/><name val=\"Calibri\"/><family val=\"2\"/><scheme val=\"minor\"/></font>" +
            "<font><b/><sz val=\"11\"/><color rgb=\"FF065F46\"/><name val=\"Calibri\"/><family val=\"2\"/><scheme val=\"minor\"/></font>" +
            "</fonts>" +
            "<fills count=\"3\">" +
            "<fill><patternFill patternType=\"none\"/></fill>" +
            "<fill><patternFill patternType=\"gray125\"/></fill>" +
            "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFD1FAE5\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
            "</fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"2\">" +
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>" +
            "</cellXfs>" +
            "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
            "<dxfs count=\"0\"/>" +
            "<tableStyles count=\"0\" defaultTableStyle=\"TableStyleMedium2\" defaultPivotStyle=\"PivotStyleLight16\"/>" +
            "</styleSheet>"

    private const val XML_DECLARATION =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
}
