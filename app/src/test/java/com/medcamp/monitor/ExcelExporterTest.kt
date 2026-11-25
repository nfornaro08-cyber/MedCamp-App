package com.medcamp.monitor

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class ExcelExporterTest {

    @Test
    fun generaUnXlsxValidoConTodasLasHojas() {
        val carpeta = File(
            System.getProperty("java.io.tmpdir"),
            "medcamp-excel-test-${System.nanoTime()}"
        )

        try {
            val archivo = ExcelExporter.generarEnCarpeta(
                carpeta = carpeta,
                resumen = listOf(
                    FilaResumenExcel(
                        nodo = "Nodo & <1>",
                        comentario = "Invernadero \"norte\"",
                        ultimaLectura = "25/11/2026 10:00:00",
                        tempAire = 22.5,
                        humAire = 60.0,
                        presion = 1012.0,
                        hum15 = 45.0,
                        hum40 = 50.0,
                        tempSuelo = 18.0,
                        vpd = 0.8,
                        bateria = 3.9,
                        estado = "En rango"
                    )
                ),
                historial = listOf(
                    FilaHistorialExcel(
                        timestamp = 1764079200000L,
                        nodo = "Nodo & <1>",
                        comentario = "Invernadero \"norte\"",
                        fecha = "25/11/2026 10:00:00",
                        tempAire = 22.5,
                        humAire = 60.0,
                        presion = 1012.0,
                        hum15 = 45.0,
                        hum40 = 50.0,
                        tempSuelo = 18.0,
                        vpd = 0.8,
                        bateria = 3.9
                    )
                ),
                umbrales = listOf(
                    FilaUmbralExcel("Temperatura del aire", 2.0, 35.0, "°C")
                )
            )

            assertTrue("El archivo no se creó", archivo.exists())
            assertTrue("El archivo está vacío", archivo.length() > 0)

            ZipFile(archivo).use { zip ->
                val requeridos = listOf(
                    "[Content_Types].xml",
                    "_rels/.rels",
                    "xl/workbook.xml",
                    "xl/_rels/workbook.xml.rels",
                    "xl/styles.xml",
                    "xl/worksheets/sheet1.xml",
                    "xl/worksheets/sheet2.xml",
                    "xl/worksheets/sheet3.xml"
                )

                requeridos.forEach { nombre ->
                    val entrada = zip.getEntry(nombre)
                    assertTrue("Falta la parte $nombre", entrada != null)
                    DocumentBuilderFactory.newInstance().newDocumentBuilder()
                        .parse(zip.getInputStream(entrada))
                }

                val sheet1 = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml"))
                    .bufferedReader()
                    .use { it.readText() }
                assertTrue(
                    "El texto no se escapó correctamente",
                    sheet1.contains("Nodo &amp; &lt;1&gt;")
                )
            }
        } finally {
            carpeta.deleteRecursively()
        }
    }
}
