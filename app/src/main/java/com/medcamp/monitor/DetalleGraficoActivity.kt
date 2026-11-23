package com.medcamp.monitor

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class DetalleGraficoActivity : AppCompatActivity() {

    private lateinit var lineChart: LineChart
    private lateinit var tvPromedio: TextView
    private lateinit var service: MedCampApiService
    private var variableActual: String = "vpd"
    private var horasFiltro: Int = 24

    private var tokenSesionFormateado: String = ""

    // 🔴 NUEVO: Variable global para almacenar el nodo seleccionado que viene de MainActivity
    private var nodoIdActual: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalle_grafico)

        variableActual = intent.getStringExtra("VARIABLE") ?: "vpd"
        val titulo = intent.getStringExtra("TITULO") ?: "DETALLE"

        val tokenRaw = intent.getStringExtra("TOKEN_JWT") ?: ""
        tokenSesionFormateado = "Bearer $tokenRaw"

        // 🔴 NUEVO: Capturamos el identificador del nodo enviado por el Intent
        nodoIdActual = intent.getStringExtra("NODO_ID")

        // Modificamos el título superior para incluir de qué nodo provienen los datos
        val tvTitulo = findViewById<TextView>(R.id.tvTituloGrafico)
        if (!nodoIdActual.isNullOrEmpty()) {
            tvTitulo.text = "$titulo - $nodoIdActual"
        } else {
            tvTitulo.text = titulo
        }

        lineChart = findViewById(R.id.lineChart)
        tvPromedio = findViewById(R.id.tvPromedio)

        configurarGestosGrafico()
        configurarSpinner()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://distract-overpass-commodity.ngrok-free.dev/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        service = retrofit.create(MedCampApiService::class.java)

        runnable = object : Runnable {
            override fun run() {
                cargarDatosDelServidor()
                handler.postDelayed(this, 5000)
            }
        }
        handler.post(runnable)
    }

    private fun configurarGestosGrafico() {
        lineChart.apply {
            setTouchEnabled(true)
            isDragEnabled = true
            isScaleXEnabled = true
            isScaleYEnabled = true
            isDoubleTapToZoomEnabled = true

            dragDecelerationFrictionCoef = 0.9f

            description.isEnabled = false
            legend.isEnabled = false
            axisRight.isEnabled = false

            axisLeft.apply {
                textColor = Color.WHITE
                setDrawGridLines(true)
                gridColor = Color.parseColor("#33FFFFFF")
            }
        }
    }

    private fun configurarSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerFiltro)
        val opciones = arrayOf(
            "Última 1h", "Últimas 6h", "Últimas 24h",
            "Últimos 7 días", "Último Mes (30d)", "Últimos 6 Meses", "Último Año"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opciones)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                horasFiltro = when (position) {
                    0 -> 1
                    1 -> 6
                    2 -> 24
                    3 -> 168
                    4 -> 720
                    5 -> 4320
                    6 -> 8760
                    else -> 24
                }
                cargarDatosDelServidor()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun cargarDatosDelServidor() {
        // 🔴 CORREGIDO: Ahora pasamos los 4 parámetros a la firma (Token, Variable, Horas, Nodo)
        service.getHistorial(tokenSesionFormateado, variableActual, horasFiltro, nodoIdActual)
            .enqueue(object : Callback<List<HistorialData>> {
                override fun onResponse(call: Call<List<HistorialData>>, response: Response<List<HistorialData>>) {
                    if (response.isSuccessful) {
                        val datos = response.body()
                        if (!datos.isNullOrEmpty()) {
                            dibujarGraficoReal(datos)
                            calcularYMostrarPromedio(datos)
                        } else {
                            lineChart.clear()
                            tvPromedio.text = "Sin datos en este rango"
                        }
                    } else {
                        lineChart.clear()
                        tvPromedio.text = "Error del servidor: ${response.code()}"
                    }
                }
                override fun onFailure(call: Call<List<HistorialData>>, t: Throwable) {
                    tvPromedio.text = "Error de conexión"
                }
            })
    }

    private fun calcularYMostrarPromedio(lista: List<HistorialData>) {
        val suma = lista.sumOf { it.valor }
        val promedio = suma / lista.size

        val textoHoras = when {
            horasFiltro >= 8760 -> "año"
            horasFiltro >= 4320 -> "6 meses"
            horasFiltro >= 720  -> "mes"
            horasFiltro >= 24   -> "${horasFiltro / 24}d"
            else -> "${horasFiltro}h"
        }
        tvPromedio.text = "Promedio últimas $textoHoras: ${String.format("%.2f", promedio)}"
    }

    private fun dibujarGraficoReal(listaRaw: List<HistorialData>) {
        val entries = ArrayList<Entry>()
        val sdfEntrada = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val listaOrdenada = listaRaw.sortedBy {
            try { sdfEntrada.parse(it.fecha)?.time ?: 0L } catch(e: Exception) { 0L }
        }

        if (listaOrdenada.isEmpty()) return

        val primerTimestamp = try {
            sdfEntrada.parse(listaOrdenada.first().fecha)?.time ?: 0L
        } catch (e: Exception) { 0L }

        val tamañoBloque = when {
            horasFiltro >= 8760 -> 24
            horasFiltro >= 4320 -> 12
            horasFiltro >= 720  -> 6
            horasFiltro >= 168  -> 3
            else -> 1
        }

        if (tamañoBloque > 1) {
            var i = 0
            while (i < listaOrdenada.size) {
                val fin = minOf(i + tamañoBloque, listaOrdenada.size)
                val subLista = listaOrdenada.subList(i, fin)

                var sumaTimestamps = 0L
                var sumaValores = 0.0
                var puntosValidos = 0

                for (data in subLista) {
                    try {
                        val fecha = sdfEntrada.parse(data.fecha)
                        if (fecha != null) {
                            sumaTimestamps += (fecha.time - primerTimestamp)
                            sumaValores += data.valor
                            puntosValidos++
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }

                if (puntosValidos > 0) {
                    val xPromedio = (sumaTimestamps / puntosValidos).toFloat()
                    val yPromedio = (sumaValores / puntosValidos).toFloat()
                    entries.add(Entry(xPromedio, yPromedio))
                }
                i += tamañoBloque
            }
        } else {
            for (data in listaOrdenada) {
                try {
                    val fecha = sdfEntrada.parse(data.fecha)
                    if (fecha != null) {
                        val xRelativo = (fecha.time - primerTimestamp).toFloat()
                        entries.add(Entry(xRelativo, data.valor.toFloat()))
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }

        if (entries.isEmpty()) {
            lineChart.clear()
            return
        }

        val dataSet = LineDataSet(entries, "Historial").apply {
            color = Color.parseColor("#2ECC71")
            setCircleColor(Color.WHITE)
            lineWidth = 2.5f
            valueTextColor = Color.WHITE
            setDrawFilled(true)
            fillAlpha = 35
            fillColor = Color.parseColor("#2ECC71")
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawValues(false)
            setDrawCircles(entries.size < 80)
            circleRadius = 3f
            highLightColor = Color.YELLOW
            highlightLineWidth = 1.5f
        }

        lineChart.data = LineData(dataSet)

        val xAxis = lineChart.xAxis
        xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            textColor = Color.WHITE
            setDrawGridLines(true)
            gridColor = Color.parseColor("#15FFFFFF")

            val formatoSalida = when {
                horasFiltro >= 4320 -> "MM/yy"
                horasFiltro >= 168  -> "dd/MM"
                else -> "HH:mm"
            }
            val sdfSalida = SimpleDateFormat(formatoSalida, Locale.getDefault())

            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val timestampReal = value.toLong() + primerTimestamp
                    return sdfSalida.format(Date(timestampReal))
                }
            }
            labelCount = 5
            setAvoidFirstLastClipping(true)
        }

        lineChart.apply {
            invalidate()
            fitScreen()

            if (entries.size > 15) {
                val factorZoom = entries.size / 15f
                zoom(factorZoom, 1f, 0f, 0f)
                moveViewToX(entries.last().x)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }
}