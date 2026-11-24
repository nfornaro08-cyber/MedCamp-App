package com.medcamp.monitor

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de inicio (resumen general). El panel de telemetría pasa a ser
 * una sección secundaria accesible desde acá.
 */
class InicioActivity : AppCompatActivity() {

    private lateinit var service: MedCampApiService
    private var token = ""

    private lateinit var tvTotal: TextView
    private lateinit var tvActivos: TextView
    private lateinit var tvAlertas: TextView
    private lateinit var tvSinDatos: TextView
    private lateinit var contenedorUltimos: LinearLayout
    private lateinit var tvEstado: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inicio)

        val prefs = getSharedPreferences("MEDCAMP_PREFS", Context.MODE_PRIVATE)
        token = prefs.getString("token_jwt", "") ?: ""

        tvTotal = findViewById(R.id.tvTotalNodos)
        tvActivos = findViewById(R.id.tvNodosActivos)
        tvAlertas = findViewById(R.id.tvTotalAlertas)
        tvSinDatos = findViewById(R.id.tvNodosSinDatos)
        contenedorUltimos = findViewById(R.id.contenedorUltimosNodos)
        tvEstado = findViewById(R.id.tvEstadoResumen)

        val retrofit = Retrofit.Builder()
            .baseUrl("https://distract-overpass-commodity.ngrok-free.dev/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        service = retrofit.create(MedCampApiService::class.java)

        findViewById<Button>(R.id.btnIrPrincipal).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        findViewById<Button>(R.id.btnAgregarNodo).setOnClickListener {
            startActivity(Intent(this, AgregarNodoActivity::class.java))
        }
        findViewById<Button>(R.id.btnIrHistorial).setOnClickListener {
            startActivity(Intent(this, HistorialActivity::class.java))
        }
        findViewById<Button>(R.id.btnExportarExcel).setOnClickListener { exportarExcel() }

        val btnSesion = findViewById<Button>(R.id.btnCerrarSesionInicio)
        if (token.isBlank()) {
            btnSesion.text = "Iniciar sesión"
            btnSesion.setOnClickListener { startActivity(Intent(this, LoginActivity::class.java)) }
        } else {
            btnSesion.setOnClickListener {
                getSharedPreferences("MEDCAMP_PREFS", Context.MODE_PRIVATE).edit().clear().apply()
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
        }

        actualizarResumen()
        if (token.isNotBlank()) cargarNodosDelServidor() else {
            tvEstado.text = "Modo sin conexión: usando datos guardados"
        }
    }

    override fun onResume() {
        super.onResume()
        actualizarResumen()
    }

    private fun cargarNodosDelServidor() {
        tvEstado.text = "Sincronizando nodos..."
        service.obtenerNodos("Bearer $token").enqueue(object : Callback<List<String>> {
            override fun onResponse(call: Call<List<String>>, response: Response<List<String>>) {
                if (response.isSuccessful) {
                    val nodos = response.body().orEmpty()
                    NodeRepository.registrarDesdeServidor(this@InicioActivity, nodos)
                    tvEstado.text = "Sincronizado: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"
                    actualizarResumen()
                } else {
                    tvEstado.text = "Servidor ${response.code()} · datos guardados"
                }
            }

            override fun onFailure(call: Call<List<String>>, t: Throwable) {
                Log.e("InicioActivity", "Sync nodos: ${t.message}")
                tvEstado.text = "Sin conexión: usando datos guardados"
            }
        })
    }

    private fun actualizarResumen() {
        val nodos = NodeRepository.obtener(this)
        delServidor = NodeRepository.idsDelServidor(this).toSet()
        val cache = getSharedPreferences("OFFLINE_CACHE", Context.MODE_PRIVATE)
        val conDatos = nodos.count { nodo ->
            nodo.id in delServidor || cache.contains("ultima_lectura_${nodo.id}")
        }
        val alertas = obtenerAlertas()

        tvTotal.text = nodos.size.toString()
        tvActivos.text = conDatos.toString()
        tvAlertas.text = alertas.size.toString()
        tvSinDatos.text = (nodos.size - conDatos).coerceAtLeast(0).toString()

        mostrarUltimos(nodos.sortedByDescending { it.fechaCreacion }.take(5))
    }

    private var delServidor: Set<String> = emptySet()

    private fun obtenerAlertas(): List<String> {
        val raw = getSharedPreferences("HISTORIAL_PREFS", Context.MODE_PRIVATE)
            .getString("lista_alertas", "") ?: ""
        return raw.split("|").filter { it.isNotBlank() }
    }

    private fun mostrarUltimos(nodos: List<NodoInfo>) {
        contenedorUltimos.removeAllViews()
        if (nodos.isEmpty()) {
            contenedorUltimos.addView(TextView(this).apply {
                text = "Todavía no hay nodos registrados. Tocá “Agregar nodo” para crear el primero."
                setTextColor(Color.parseColor("#A0A0A0"))
                textSize = 14f
                setPadding(0, 12, 0, 12)
            })
            return
        }

        nodos.forEach { nodo ->
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(android.R.drawable.dialog_holo_dark_frame)
                setPadding(24, 20, 24, 20)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 16 }
            }

            val titulo = TextView(this).apply {
                text = "${nodo.nombre}  ·  ${nodo.id}"
                setTextColor(Color.WHITE)
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            val detalle = TextView(this).apply {
                text = buildString {
                    if (nodo.descripcion.isNotBlank()) append(nodo.descripcion)
                    if (nodo.comentarios.isNotBlank()) {
                        if (isNotEmpty()) append("\n")
                        append("Comentarios: ${nodo.comentarios}")
                    }
                    if (isEmpty()) append("Sin descripción ni comentarios")
                    append("\nAlta: ${nodo.fechaCreacion}")
                }
                setTextColor(Color.parseColor("#A0A0A0"))
                textSize = 12f
                setPadding(0, 6, 0, 0)
            }
            val editar = TextView(this).apply {
                text = "✎ Editar comentarios"
                setTextColor(Color.parseColor("#3AE078"))
                textSize = 12f
                gravity = Gravity.END
                setPadding(0, 10, 0, 0)
                setOnClickListener { editarComentarios(nodo) }
            }

            fila.addView(titulo)
            fila.addView(detalle)
            fila.addView(editar)
            contenedorUltimos.addView(fila)
        }
    }

    private fun editarComentarios(nodo: NodoInfo) {
        val campo = EditText(this).apply {
            setText(nodo.comentarios)
            hint = "Comentarios sobre este nodo"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#777777"))
        }
        AlertDialog.Builder(this)
            .setTitle("Comentarios · ${nodo.nombre}")
            .setView(campo)
            .setPositiveButton("Guardar") { _, _ ->
                NodeRepository.actualizarComentarios(this, nodo.id, campo.text.toString())
                actualizarResumen()
                Toast.makeText(this, "Comentarios guardados", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun exportarExcel() {
        val nodos = NodeRepository.obtener(this)
        val cachePrefs = getSharedPreferences("OFFLINE_CACHE", Context.MODE_PRIVATE)
        val cache = cachePrefs.all.filterValues { it is String }.mapValues { it.value as String }
        val resumen = listOf(
            "Total de nodos" to tvTotal.text.toString(),
            "Nodos activos" to tvActivos.text.toString(),
            "Nodos sin datos" to tvSinDatos.text.toString(),
            "Alertas registradas" to tvAlertas.text.toString(),
            "Fecha de exportación" to SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault()
            ).format(Date())
        )

        val ruta = ExcelExporter.exportar(this, nodos, obtenerAlertas(), cache, resumen)
        Toast.makeText(
            this,
            if (ruta != null) "Excel guardado en $ruta" else "No se pudo generar el Excel",
            Toast.LENGTH_LONG
        ).show()
    }
}
