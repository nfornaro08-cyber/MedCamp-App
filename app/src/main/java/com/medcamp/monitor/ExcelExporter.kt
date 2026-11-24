package com.medcamp.monitor

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Escribe un .xlsx real (OOXML) usando solo java.util.zip, sin librerías extra.
 * Genera tres hojas: Nodos, Alertas y Resumen.
 */
object ExcelExporter {

    private const val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val NS_CONTENT = "http://schemas.openxmlformats.org/package/2006/content-types"

    private data class Hoja(val nombre: String, val filas: List<List<String>>)

    fun exportar(
        context: Context,
        nodos: List<NodoInfo>,
        alertas: List<String>,
        cache: Map<String, String>,
        resumen: List<Pair<String, String>>
    ): String? {
        return try {
            val hojas = listOf(
                Hoja("Nodos", filasNodos(nodos, cache)),
                Hoja("Alertas", filasAlertas(alertas)),
                Hoja("Resumen", filasResumen(resumen))
            )

            val sello = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val nombre = "MedCamp_Nodos_$sello.xlsx"

            val directorio = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "MedCamp")
            if (!directorio.exists()) directorio.mkdirs()
            val archivo = File(directorio, nombre)

            ZipOutputStream(FileOutputStream(archivo)).use { zip ->
                escribir(zip, "[Content_Types].xml", contentTypes(hojas.size))
                escribir(zip, "_rels/.rels", relRaiz())
                escribir(zip, "xl/workbook.xml", workbook(hojas))
                escribir(zip, "xl/_rels/workbook.xml.rels", relLibro(hojas.size))
                escribir(zip, "xl/styles.xml", estilos())
                hojas.forEachIndexed { i, hoja ->
                    escribir(zip, "xl/worksheets/sheet${i + 1}.xml", hojaXML(hoja.filas))
                }
            }

            publicar(context, archivo, nombre)
        } catch (e: Exception) {
            null
        }
    }

    // ---------- Filas ----------

    private fun filasNodos(nodos: List<NodoInfo>, cache: Map<String, String>): List<List<String>> {
        val cabecera = listOf(
            "ID", "Nombre", "Descripción", "Comentarios", "Estado", "Fecha creación",
            "Última actualización", "VPD (kPa)", "Temp. aire (°C)", "Hum. aire (%)",
            "Presión (hPa)", "Hum. 15cm (%)", "Hum. 40cm (%)", "Temp. suelo (°C)",
            "Batería (V)", "WiFi", "LoRa"
        )
        val servidor = cache.keys.mapNotNull { it.removePrefix("ultima_lectura_").takeIf { key -> key != "global" } }
        val filas = mutableListOf(cabecera)

        nodos.forEach { nodo ->
            val json = cache["ultima_lectura_${nodo.id}"]
            val valores = if (json.isNullOrBlank()) emptyMap() else camposLectura(json)
            filas.add(
                listOf(
                    nodo.id,
                    nodo.nombre,
                    nodo.descripcion,
                    nodo.comentarios,
                    if (nodo.id in servidor || json != null) "Activo" else "Sin datos",
                    nodo.fechaCreacion,
                    valores["timestamp"] ?: valores["fecha"] ?: "",
                    valores["vpd"] ?: "",
                    valores["tempAire"] ?: "",
                    valores["humAire"] ?: "",
                    valores["presion"] ?: "",
                    valores["hum15"] ?: "",
                    valores["hum40"] ?: "",
                    valores["tempSuelo"] ?: "",
                    valores["bateria"] ?: "",
                    valores["wifiCon"] ?: "",
                    valores["loraCon"] ?: ""
                )
            )
        }
        return filas
    }

    private fun camposLectura(json: String): Map<String, String> {
        val obj = JSONObject(json)
        val salida = mutableMapOf<String, String>()
        obj.keys().forEach { clave ->
            val valor = obj.opt(clave)
            if (valor != null && valor != JSONObject.NULL) salida[clave] = valor.toString()
        }
        return salida
    }

    private fun filasAlertas(alertas: List<String>): List<List<String>> {
        val filas = mutableListOf(listOf("Nº", "Registro", "Detalle"))
        alertas.filter { it.isNotBlank() }.forEachIndexed { i, alerta ->
            val partes = alerta.split("\n", limit = 2)
            filas.add(listOf((i + 1).toString(), partes[0], partes.getOrNull(1) ?: ""))
        }
        return filas
    }

    private fun filasResumen(resumen: List<Pair<String, String>>): List<List<String>> {
        val filas = mutableListOf(listOf("Métrica", "Valor"))
        resumen.forEach { filas.add(listOf(it.first, it.second)) }
        return filas
    }

    // ---------- OOXML ----------

    private fun contentTypes(cantidad: Int): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<Types xmlns=\"$NS_CONTENT\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        for (i in 1..cantidad) {
            append("<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        append("</Types>")
    }

    private fun relRaiz(): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<Relationships xmlns=\"$NS_PKG_REL\">")
        append("<Relationship Id=\"rId1\" Type=\"$NS_REL/officeDocument\" Target=\"xl/workbook.xml\"/>")
        append("</Relationships>")
    }

    private fun workbook(hojas: List<Hoja>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<workbook xmlns=\"$NS_MAIN\" xmlns:r=\"$NS_REL\"><sheets>")
        hojas.forEachIndexed { i, hoja ->
            append("<sheet name=\"${esc(hoja.nombre.sanitizar())}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>")
        }
        append("</sheets></workbook>")
    }

    private fun relLibro(cantidad: Int): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<Relationships xmlns=\"$NS_PKG_REL\">")
        for (i in 1..cantidad) {
            append("<Relationship Id=\"rId$i\" Type=\"$NS_REL/worksheet\" Target=\"worksheets/sheet$i.xml\"/>")
        }
        append("<Relationship Id=\"rId${cantidad + 1}\" Type=\"$NS_REL/styles\" Target=\"styles.xml\"/>")
        append("</Relationships>")
    }

    private fun estilos(): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<styleSheet xmlns=\"$NS_MAIN\">")
        append("<fonts count=\"2\">")
        append("<font><sz val=\"11\"/><name val=\"Calibri\"/></font>")
        append("<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>")
        append("</fonts>")
        append("<fills count=\"1\"><fill><patternFill patternType=\"none\"/></fill></fills>")
        append("<borders count=\"1\"><border/></borders>")
        append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>")
        append("<cellXfs count=\"2\">")
        append("<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>")
        append("<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>")
        append("</cellXfs>")
        append("<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>")
        append("<dxfs count=\"0\"/>")
        append("</styleSheet>")
    }

    private fun hojaXML(filas: List<List<String>>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<worksheet xmlns=\"$NS_MAIN\"><sheetData>")
        filas.forEachIndexed { i, fila ->
            val numeroFila = i + 1
            append("<row r=\"$numeroFila\">")
            fila.forEachIndexed { j, valor ->
                val ref = "${columna(j)}$numeroFila"
                val estilo = if (i == 0) " s=\"1\"" else ""
                val texto = valor
                val numero = if (i > 0 && texto.isNotBlank()) texto.toDoubleOrNull() else null
                if (numero != null) {
                    append("<c r=\"$ref\"$estilo><v>$numero</v></c>")
                } else {
                    append("<c r=\"$ref\"$estilo t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(texto)}</t></is></c>")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun columna(indice: Int): String {
        var n = indice
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A'.code + (n % 26)).toChar())
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    private fun esc(texto: String): String = texto
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun String.sanitizar(): String =
        replace(Regex("[^A-Za-z0-9 _]"), "").ifBlank { "Hoja" }

    private fun escribir(zip: ZipOutputStream, ruta: String, contenido: String) {
        zip.putNextEntry(ZipEntry(ruta))
        zip.write(contenido.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    /** Copia el archivo a la carpeta Downloads pública y devuelve la ruta visible. */
    private fun publicar(context: Context, archivo: File, nombre: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val valores = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, nombre)
                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri: Uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
                ?: return archivo.absolutePath
            context.contentResolver.openOutputStream(uri)?.use { salida ->
                archivo.inputStream().use { it.copyTo(salida) }
            }
            valores.clear()
            valores.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, valores, null, null)
            return "Descargas/$nombre"
        }

        val publico = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), nombre)
        return try {
            archivo.copyTo(publico, overwrite = true)
            publico.absolutePath
        } catch (e: Exception) {
            archivo.absolutePath
        }
    }
}
