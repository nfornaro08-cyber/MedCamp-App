package com.medcamp.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var runnableSensores: Runnable
    private lateinit var runnableClima: Runnable

    private lateinit var serviceMedCamp: MedCampApiService
    private lateinit var serviceClima: OpenWeatherApiService

    private val API_KEY_WEATHER = "461759f65143c4752b37833f0d40c1fd"
    private val CIUDAD_MONITOREO = "Rosario"

    private val CANAL_ID = "ALERTAS_CULTIVO"

    private var tokenSesionFormateado: String = ""

    // --- VARIABLES NUEVAS PARA EL MULTINODO ---
    private lateinit var spinnerNodos: Spinner
    private val listaDeNodos = ArrayList<String>()
    private lateinit var nodoAdapter: ArrayAdapter<String>
    private var nodoSeleccionadoActual: String? = null

    // Banderas de estado para evitar el spam de notificaciones duplicadas
    private var alertaVpdActiva = false
    private var alertaHeladaActiva = false
    private var alertaSueloActiva = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar el Spinner desde el XML modificado
        spinnerNodos = findViewById(R.id.spinnerNodos)

        val tokenRaw = intent.getStringExtra("TOKEN_JWT")
            ?: getSharedPreferences("MEDCAMP_PREFS", Context.MODE_PRIVATE)
                .getString("token_jwt", "").orEmpty()
        tokenSesionFormateado = "Bearer $tokenRaw"

        val urlNgrok = "https://distract-overpass-commodity.ngrok-free.dev/"
        val retrofitMedCamp = Retrofit.Builder()
            .baseUrl(urlNgrok)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        serviceMedCamp = retrofitMedCamp.create(MedCampApiService::class.java)

        val retrofitClima = Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        serviceClima = retrofitClima.create(OpenWeatherApiService::class.java)

        configurarClics()
        crearCanalNotificaciones()
        cargarDatosOffline()

        // 1. Cargar el listado de nodos disponibles desde el Servidor Node.js
        obtenerListadoNodosServidor()

        // 2. Escuchar cuando el usuario cambia de nodo en el Spinner
        spinnerNodos.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (listaDeNodos.isNotEmpty() && listaDeNodos[position] != "Sin nodos activos") {
                    nodoSeleccionadoActual = listaDeNodos[position]

                    // Al cambiar, forzamos una actualización inmediata en vez de esperar los 5 seg
                    obtenerDatosSensores()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 3. Bucle de actualización (Usa de forma nativa la variable global del nodo activo)
        runnableSensores = object : Runnable {
            override fun run() {
                obtenerDatosSensores()
                handler.postDelayed(this, 5000)
            }
        }
        handler.post(runnableSensores)

        // 4. Bucle del clima
        runnableClima = object : Runnable {
            override fun run() {
                obtenerDatosClima()
                handler.postDelayed(this, 10 * 60 * 1000)
            }
        }
        handler.post(runnableClima)
    }

    // --- NUEVO MÉTODO: TRAER ARRAY DE NODOS Y INYECTARLOS AL SPINNER ---
    private fun obtenerListadoNodosServidor() {
        serviceMedCamp.obtenerNodos(tokenSesionFormateado).enqueue(object : Callback<List<String>> {
            override fun onResponse(call: Call<List<String>>, response: Response<List<String>>) {
                if (response.isSuccessful) {
                    val nodos = response.body()
                    listaDeNodos.clear()

                    if (!nodos.isNullOrEmpty()) {
                        listaDeNodos.addAll(nodos)
                        nodoSeleccionadoActual = listaDeNodos[0] // Por defecto el primero
                        // Registrar los nodos del servidor para que el Inicio y el Excel los vean
                        NodeRepository.registrarDesdeServidor(this@MainActivity, nodos)
                    } else {
                        listaDeNodos.add("Sin nodos activos")
                    }

                    // Adaptador estilizado con los recursos por defecto de Android
                    nodoAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, listaDeNodos)
                    nodoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerNodos.adapter = nodoAdapter
                }
            }

            override fun onFailure(call: Call<List<String>>, t: Throwable) {
                Log.e("MainActivity", "Error al obtener lista de nodos: ${t.message}")
                listaDeNodos.clear()
                listaDeNodos.add("Error de red")
                nodoAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, listaDeNodos)
                spinnerNodos.adapter = nodoAdapter
            }
        })
    }

    private fun configurarClics() {
        findViewById<MaterialCardView>(R.id.cardVPD).setOnClickListener { abrirGrafico("vpd", "ESTRÉS (VPD)") }
        findViewById<MaterialCardView>(R.id.cardTempAire).setOnClickListener { abrirGrafico("tempAire", "TEMPERATURA AIRE") }
        findViewById<MaterialCardView>(R.id.cardHumAire).setOnClickListener { abrirGrafico("humAire", "HUMEDAD AIRE") }
        findViewById<MaterialCardView>(R.id.cardPresion).setOnClickListener { abrirGrafico("presion", "PRESIÓN ATMOSFÉRICA") }
        findViewById<MaterialCardView>(R.id.cardHum15).setOnClickListener { abrirGrafico("hum15", "HUMEDAD 15CM") }
        findViewById<MaterialCardView>(R.id.cardHum40).setOnClickListener { abrirGrafico("hum40", "HUMEDAD 40CM") }
        findViewById<MaterialCardView>(R.id.cardTempSuelo).setOnClickListener { abrirGrafico("tempSuelo", "TEMPERATURA SUELO") }

        findViewById<android.widget.Button>(R.id.btnIrHistorial).setOnClickListener {
            val intent = Intent(this, HistorialActivity::class.java)
            intent.putExtra("TOKEN_JWT", tokenSesionFormateado.replace("Bearer ", ""))
            // Pasamos el nodo actual para que el historial filtre directo si es necesario
            intent.putExtra("NODO_ID", nodoSeleccionadoActual ?: "")
            startActivity(intent)
        }

        findViewById<android.widget.Button>(R.id.btnInicio).setOnClickListener {
            startActivity(Intent(this, InicioActivity::class.java))
            finish()
        }

        findViewById<android.widget.Button>(R.id.btnExportarExcel).setOnClickListener {
            exportarExcel()
        }
    }

    private fun exportarExcel() {
        val nodos = NodeRepository.obtener(this)
        val cache = getSharedPreferences("OFFLINE_CACHE", Context.MODE_PRIVATE)
            .all.filterValues { it is String }.mapValues { it.value as String }
        val historial = getSharedPreferences("HISTORIAL_PREFS", Context.MODE_PRIVATE)
            .getString("lista_alertas", "") ?: ""
        val alertas = historial.split("|").filter { it.isNotBlank() }
        val servidor = NodeRepository.idsDelServidor(this).toSet()

        val resumen = listOf(
            "Total de nodos" to nodos.size.toString(),
            "Nodos activos" to nodos.count {
                it.id in servidor || cache.containsKey("ultima_lectura_${it.id}")
            }.toString(),
            "Alertas registradas" to alertas.size.toString(),
            "Nodo seleccionado" to (nodoSeleccionadoActual ?: "Ninguno"),
            "Fecha de exportación" to java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()
            ).format(java.util.Date())
        )

        val ruta = ExcelExporter.exportar(this, nodos, alertas, cache, resumen)
        Toast.makeText(
            this,
            if (ruta != null) "Excel guardado en $ruta" else "No se pudo generar el Excel",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun abrirGrafico(variable: String, titulo: String) {
        val intent = Intent(this, DetalleGraficoActivity::class.java)
        intent.putExtra("VARIABLE", variable)
        intent.putExtra("TITULO", titulo)
        intent.putExtra("TOKEN_JWT", tokenSesionFormateado.replace("Bearer ", ""))
        // 🔴 ENVIAR NODO AL GRÁFICO: Fundamental para que el gráfico aísle los datos de este nodo
        intent.putExtra("NODO_ID", nodoSeleccionadoActual ?: "")
        startActivity(intent)
    }

    private fun obtenerDatosSensores() {
        serviceMedCamp.getDatos(tokenSesionFormateado, nodoSeleccionadoActual).enqueue(object : Callback<LecturaData> {
            override fun onResponse(call: Call<LecturaData>, response: Response<LecturaData>) {
                if (response.isSuccessful) {
                    val info = response.body()
                    info?.let {
                        actualizarInterfazUsuario(it)
                        guardarDatosOffline(it)

                        // 🔴 MODIFICADO: Le pasamos el nombre del nodo actual a la evaluación
                        evaluarRangosCriticos(it, nodoSeleccionadoActual ?: "Nodo General")
                    }
                } else {
                    Log.e("MainActivity", "Error devuelto por el servidor: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<LecturaData>, t: Throwable) {
                Log.e("MainActivity", "Error de conexión sensores: ${t.message}. Cargando caché.")
                cargarDatosOffline()
            }
        })
    }

    private fun actualizarInterfazUsuario(info: LecturaData) {
        findViewById<TextView>(R.id.tvVPD).text = String.format("%.2f", info.vpd ?: 0.0)
        findViewById<TextView>(R.id.tvTempAire).text = "${info.tempAire ?: "--"} °C"
        findViewById<TextView>(R.id.tvHumAire).text = "${info.humAire ?: "--"} %"
        findViewById<TextView>(R.id.tvPresion).text = "${info.presion ?: "--"} hPa"
        findViewById<TextView>(R.id.tvHum15).text = "${info.hum15 ?: "--"}%"
        findViewById<TextView>(R.id.tvHum40).text = "${info.hum40 ?: "--"}%"
        findViewById<TextView>(R.id.tvTempSuelo).text = "${info.tempSuelo ?: "--"} °C"

        val imgWifi = findViewById<ImageView>(R.id.imgWifi)
        val imgLora = findViewById<ImageView>(R.id.imgLora)
        val colorVerde = Color.parseColor("#3AE078")
        val colorGris = Color.parseColor("#444444")

        imgWifi.setColorFilter(if (info.wifiCon == 1) colorVerde else colorGris)
        imgLora.setColorFilter(if (info.loraCon == 1) colorVerde else colorGris)
    }

    private fun guardarDatosOffline(info: LecturaData) {
        // Guardamos la caché diferenciada por nodo para evitar que se mezclen al quedar offline
        val claveCache = "ultima_lectura_${nodoSeleccionadoActual ?: "global"}"
        val prefs = getSharedPreferences("OFFLINE_CACHE", Context.MODE_PRIVATE)
        val jsonString = Gson().toJson(info)
        prefs.edit().putString(claveCache, jsonString).apply()
    }

    private fun cargarDatosOffline() {
        val claveCache = "ultima_lectura_${nodoSeleccionadoActual ?: "global"}"
        val prefs = getSharedPreferences("OFFLINE_CACHE", Context.MODE_PRIVATE)
        val jsonString = prefs.getString(claveCache, null)
        if (jsonString != null) {
            try {
                val infoCacheada = Gson().fromJson(jsonString, LecturaData::class.java)
                actualizarInterfazUsuario(infoCacheada)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error al parsear caché offline: ${e.message}")
            }
        }
    }

    private fun guardarEnHistorial(titulo: String, descripcion: String) {
        val prefs = getSharedPreferences("HISTORIAL_PREFS", Context.MODE_PRIVATE)
        val historialActual = prefs.getString("lista_alertas", "") ?: ""

        val sdf = java.text.SimpleDateFormat("dd/MM HH:mm:ss", java.util.Locale.getDefault())
        val fechaHora = sdf.format(java.util.Date())

        // Guardamos la alerta especificando de qué nodo provino
        val nodoIdPrefijo = if (nodoSeleccionadoActual != null) "[$nodoSeleccionadoActual] " else ""
        val nuevoRegistro = "[$fechaHora] $nodoIdPrefijo$titulo\n$descripcion"

        val nuevoHistorial = if (historialActual.isEmpty()) nuevoRegistro else "$historialActual|$nuevoRegistro"
        prefs.edit().putString("lista_alertas", nuevoHistorial).apply()
    }

    private fun obtenerDatosClima() {
        serviceClima.getWeatherByCity(CIUDAD_MONITOREO, API_KEY_WEATHER).enqueue(object : Callback<WeatherResponse> {
            override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                if (response.isSuccessful) {
                    val clima = response.body()
                    clima?.let {
                        val tempExterna = it.main.temperature
                        val humExterna = it.main.humidity
                        val descripcionRaw = it.weather.firstOrNull()?.description ?: "desconocido"

                        val condicionFormateada = descripcionRaw.replaceFirstChar { char ->
                            if (char.isLowerCase()) char.titlecase() else char.toString()
                        }

                        findViewById<TextView>(R.id.tvCiudadClima).text = "CLIMA EXTERIOR (${it.cityName.uppercase()})"
                        findViewById<TextView>(R.id.tvCondicionClima).text = condicionFormateada
                        findViewById<TextView>(R.id.tvTempClima).text = String.format("%.1f°C", tempExterna)
                        findViewById<TextView>(R.id.tvHumClima).text = "$humExterna%"
                    }
                } else {
                    val codigoError = response.code()
                    findViewById<TextView>(R.id.tvCondicionClima).text = "Error Servidor: $codigoError"
                }
            }

            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                findViewById<TextView>(R.id.tvCiudadClima).text = "SIN CONEXIÓN"
            }
        })
    }

    private fun crearCanalNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alertas de Riesgo de Cultivo"
            val descriptionText = "Notificaciones críticas sobre VPD, heladas y humedad del suelo"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CANAL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun lanzarNotificacion(titulo: String, mensaje: String, idNotificacion: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                return
            }
        }

        val builder = NotificationCompat.Builder(this, CANAL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(this)) {
                notify(idNotificacion, builder.build())
            }
        } catch (e: SecurityException) {
            Log.e("MainActivity", "Sin permisos para lanzar notificación: ${e.message}")
        }
    }

    // 🔴 MODIFICADO: Ahora recibe el nombre del nodo que gatilló la lectura
    private fun evaluarRangosCriticos(datos: LecturaData, nodoNombre: String) {
        val vpd = datos.vpd?.toDouble() ?: 0.0
        val tempAire = datos.tempAire?.toDouble() ?: 20.0
        val humSuelo15 = datos.hum15?.toDouble() ?: 50.0

        // 1. Alerta VPD Crítico
        if (vpd > 1.8 || (vpd < 0.4 && vpd > 0.0)) {
            if (!alertaVpdActiva) {
                val mensaje = if (vpd > 1.8) "VPD en ${String.format("%.2f", vpd)} kPa. Riesgo de estrés hídrico." else "VPD en ${String.format("%.2f", vpd)} kPa. Riesgo de hongos por alta humedad."

                // 🔴 TÍTULO MODIFICADO CON EL NODO
                lanzarNotificacion("⚠️ ALERTA [$nodoNombre]: Problema de VPD", mensaje, 100)
                guardarEnHistorial("⚠️ ALERTA: Problema de VPD", mensaje)
                alertaVpdActiva = true
            }
        } else {
            alertaVpdActiva = false
        }

        // 2. Alerta Climática: Heladas o Calor Extremo
        if (tempAire <= 2.0 || tempAire >= 38.0) {
            if (!alertaHeladaActiva) {
                // 🔴 TÍTULO MODIFICADO CON EL NODO
                val titulo = if (tempAire <= 2.0) "❄️ HELADA [$nodoNombre]" else "🔥 GOLPE DE CALOR [$nodoNombre]"
                val mensaje = if (tempAire <= 2.0) "Temperatura crítica del aire: $tempAire °C. Riesgo foliar." else "Temperatura ambiental extrema: $tempAire °C."

                lanzarNotificacion(titulo, mensaje, 200)
                guardarEnHistorial(titulo, mensaje)
                alertaHeladaActiva = true
            }
        } else {
            alertaHeladaActiva = false
        }

        // 3. Alerta de Humedad de Suelo
        if (humSuelo15 < 25.0) {
            if (!alertaSueloActiva) {
                val mensaje = "Humedad a 15cm cayó al $humSuelo15%. Suelo muy seco."

                // 🔴 TÍTULO MODIFICADO CON EL NODO
                lanzarNotificacion("💧 ESTRÉS HÍDRICO [$nodoNombre]", mensaje, 300)
                guardarEnHistorial("💧 ESTRÉS HÍDRICO", mensaje)
                alertaSueloActiva = true
            }
        } else {
            alertaSueloActiva = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnableSensores)
        handler.removeCallbacks(runnableClima)
    }
}