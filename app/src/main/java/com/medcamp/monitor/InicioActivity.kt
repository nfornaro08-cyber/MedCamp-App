package com.medcamp.monitor

import android.content.Intent
import android.os.Bundle
import android.app.ProgressDialog
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.navigation.NavigationBarView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InicioActivity : AppCompatActivity() {

    private enum class Estado { ALTO, BAJO, RANGO, SIN_UMBRAL }

    private data class VariableEval(
        val key: String,
        val nombre: String,
        val sensor: String,
        val categoria: String,
        val iconRes: Int,
        val valor: Double,
        val unidad: String,
        val decimales: Int,
        val estado: Estado,
        val labelUmbral: String,
        val umbral: String,
        val detalle: String,
        val delta: String
    )

    private data class NodoEstado(
        val nodo: String,
        val comentario: String,
        val fecha: String,
        val evaluadas: List<VariableEval>,
        val anomalias: List<VariableEval>
    )

    private lateinit var auth: FirebaseAuth
    private lateinit var dbRef: DatabaseReference
    private lateinit var configRef: DatabaseReference
    private lateinit var nodosInfoRef: DatabaseReference

    private var nodosListener: ValueEventListener? = null
    private var configListener: ValueEventListener? = null
    private var comentariosListener: ValueEventListener? = null
    private val queryPorNodo = mutableMapOf<String, Query>()
    private val listenerPorNodo = mutableMapOf<String, ValueEventListener>()

    private val nodosActuales = mutableListOf<String>()
    private val lecturaPorNodo = mutableMapOf<String, LecturaData>()
    private val comentariosPorNodo = mutableMapOf<String, String>()
    private var estadosPorNodo: List<NodoEstado> = emptyList()
    private var filtroActual = "todas"
    private var exportando = false

    private var tempMaxConfig: Double? = null
    private var tempMinConfig: Double? = null
    private var vpdMaxConfig: Double? = null
    private var vpdMinConfig: Double? = null
    private var humSueloMinConfig: Double? = null
    private var tempRadicularMinConfig: Double? = null
    private var tempRadicularMaxConfig: Double? = null

    private val bateriaMinima = 3.4

    private lateinit var tvHealthPct: TextView
    private lateinit var tvUltimaLectura: TextView
    private lateinit var tvCountBajo: TextView
    private lateinit var tvCountRango: TextView
    private lateinit var tvCountAlto: TextView
    private lateinit var progressSalud: ProgressBar
    private lateinit var chipGroupFiltros: ChipGroup
    private lateinit var llAnomalias: LinearLayout
    private lateinit var cardSinAnomalias: MaterialCardView
    private lateinit var tvSinAnomaliasTitulo: TextView
    private lateinit var tvSinAnomaliasDetalle: TextView
    private lateinit var tvAnomaliasCount: TextView
    private lateinit var tvNotaUmbrales: TextView
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var btnExportarExcel: Button

    private val navListener = NavigationBarView.OnItemSelectedListener { item ->
        when (item.itemId) {
            R.id.nav_resumen -> true
            R.id.nav_nodos -> {
                startActivity(Intent(this, MainActivity::class.java))
                true
            }
            R.id.nav_alertas -> {
                startActivity(Intent(this, HistorialActivity::class.java))
                true
            }
            R.id.nav_config -> {
                startActivity(Intent(this, ConfiguracionActivity::class.java))
                true
            }
            else -> false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inicio)

        auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: "DdaAJqXxIcadkzamNl16yWyy8k13"
        dbRef = FirebaseDatabase.getInstance().getReference("usuarios").child(uid).child("lecturas")
        configRef = FirebaseDatabase.getInstance().getReference("usuarios").child(uid).child("configuracion")
        nodosInfoRef = FirebaseDatabase.getInstance().getReference("usuarios").child(uid).child("nodos")

        tvHealthPct = findViewById(R.id.tvHealthPct)
        tvUltimaLectura = findViewById(R.id.tvUltimaLectura)
        tvCountBajo = findViewById(R.id.tvCountBajo)
        tvCountRango = findViewById(R.id.tvCountRango)
        tvCountAlto = findViewById(R.id.tvCountAlto)
        progressSalud = findViewById(R.id.progressSalud)
        chipGroupFiltros = findViewById(R.id.chipGroupFiltros)
        llAnomalias = findViewById(R.id.llAnomalias)
        cardSinAnomalias = findViewById(R.id.cardSinAnomalias)
        tvSinAnomaliasTitulo = findViewById(R.id.tvSinAnomaliasTitulo)
        tvSinAnomaliasDetalle = findViewById(R.id.tvSinAnomaliasDetalle)
        tvAnomaliasCount = findViewById(R.id.tvAnomaliasCount)
        tvNotaUmbrales = findViewById(R.id.tvNotaUmbrales)
        bottomNav = findViewById(R.id.bottomNavInicio)
        btnExportarExcel = findViewById(R.id.btnExportarExcel)

        findViewById<android.widget.Button>(R.id.btnConfigurarUmbrales).setOnClickListener {
            startActivity(Intent(this, ConfiguracionActivity::class.java))
        }

        btnExportarExcel.setOnClickListener { exportarExcel() }

        configurarFiltros()

        bottomNav.setOnItemSelectedListener(navListener)
        bottomNav.selectedItemId = R.id.nav_resumen

        escucharConfiguracion()
        escucharComentarios()
        escucharNodos()
        actualizarPantalla()
    }

    override fun onResume() {
        super.onResume()
        bottomNav.setOnItemSelectedListener(null)
        bottomNav.selectedItemId = R.id.nav_resumen
        bottomNav.setOnItemSelectedListener(navListener)
    }

    private fun configurarFiltros() {
        chipGroupFiltros.setOnCheckedChangeListener { _, checkedId ->
            filtroActual = when (checkedId) {
                R.id.chipTempAire -> "temp"
                R.id.chipHumedad -> "humedad"
                R.id.chipVpd -> "vpd"
                R.id.chipTempSuelo -> "tempSuelo"
                else -> "todas"
            }
            renderNodos()
        }
        chipGroupFiltros.check(R.id.chipTodas)
    }

    private fun escucharConfiguracion() {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                tempMaxConfig = snapshot.child("tempMax").value?.toString()?.toDoubleOrNull()
                tempMinConfig = snapshot.child("tempMin").value?.toString()?.toDoubleOrNull()
                vpdMaxConfig = snapshot.child("vpdMax").value?.toString()?.toDoubleOrNull()
                vpdMinConfig = snapshot.child("vpdMin").value?.toString()?.toDoubleOrNull()
                humSueloMinConfig = snapshot.child("humSueloMin").value?.toString()?.toDoubleOrNull()
                tempRadicularMinConfig = snapshot.child("tempRadicularMin").value?.toString()?.toDoubleOrNull()
                tempRadicularMaxConfig = snapshot.child("tempRadicularMax").value?.toString()?.toDoubleOrNull()
                actualizarPantalla()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("InicioActivity", "Error al cargar configuración: ${error.message}")
            }
        }
        configListener = listener
        configRef.addValueEventListener(listener)
    }

    private fun escucharComentarios() {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                comentariosPorNodo.clear()
                for (child in snapshot.children) {
                    val nodo = child.key ?: continue
                    val comentario = child.child("comentario").value?.toString()?.trim().orEmpty()
                    if (comentario.isNotEmpty()) {
                        comentariosPorNodo[nodo] = comentario
                    }
                }
                actualizarPantalla()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("InicioActivity", "Error al cargar comentarios: ${error.message}")
            }
        }
        comentariosListener = listener
        nodosInfoRef.addValueEventListener(listener)
    }

    private fun escucharNodos() {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val nuevosNodos = snapshot.children.mapNotNull { it.key }
                val eliminados = nodosActuales.filter { it !in nuevosNodos }

                eliminados.forEach { quitarNodo(it) }
                nodosActuales.clear()
                nodosActuales.addAll(nuevosNodos)

                nuevosNodos.filter { it !in listenerPorNodo }.forEach { escucharNodo(it) }
                actualizarPantalla()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("InicioActivity", "Error al consultar nodos: ${error.message}")
                actualizarPantalla()
            }
        }
        nodosListener = listener
        dbRef.addValueEventListener(listener)
    }

    private fun escucharNodo(nodo: String) {
        val query = dbRef.child(nodo).orderByKey().limitToLast(1)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    lecturaPorNodo.remove(nodo)
                } else {
                    snapshot.children.lastOrNull()?.let { child ->
                        lecturaPorNodo[nodo] = parseLectura(child)
                    }
                }
                actualizarPantalla()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("InicioActivity", "Error en el nodo $nodo: ${error.message}")
            }
        }

        queryPorNodo[nodo] = query
        listenerPorNodo[nodo] = listener
        query.addValueEventListener(listener)
    }

    private fun quitarNodo(nodo: String) {
        listenerPorNodo.remove(nodo)?.let { listener ->
            queryPorNodo.remove(nodo)?.removeEventListener(listener)
        }
        lecturaPorNodo.remove(nodo)
    }

    private fun parseLectura(child: DataSnapshot): LecturaData {
        fun valor(campo: String): Double =
            child.child(campo).value?.toString()?.toDoubleOrNull() ?: 0.0

        return LecturaData(
            hum15 = valor("hum15").toInt(),
            hum40 = valor("hum40").toInt(),
            humAire = valor("humAire"),
            tempAire = valor("tempAire"),
            tempSuelo = valor("tempSuelo"),
            bateria = valor("bateria"),
            vpd = valor("vpd"),
            presion = valor("presion"),
            fecha = child.key ?: ""
        )
    }

    private fun actualizarPantalla() {
        val estados = nodosActuales.mapNotNull { nodo ->
            val lectura = lecturaPorNodo[nodo] ?: return@mapNotNull null
            val evaluadas = evaluarVariables(lectura)
            NodoEstado(
                nodo = nodo,
                comentario = comentariosPorNodo[nodo].orEmpty(),
                fecha = lectura.fecha,
                evaluadas = evaluadas,
                anomalias = evaluadas.filter { it.estado == Estado.ALTO || it.estado == Estado.BAJO }
            )
        }
        estadosPorNodo = estados

        val total = nodosActuales.size
        val conDatos = estados.size
        val conAlertas = estados.count { it.anomalias.isNotEmpty() }
        val ok = conDatos - conAlertas
        val sinDatos = total - conDatos

        tvCountBajo.text = sinDatos.toString()
        tvCountRango.text = ok.toString()
        tvCountAlto.text = conAlertas.toString()

        val porcentaje = if (total == 0) 0 else (ok * 100) / total
        progressSalud.progress = porcentaje
        tvHealthPct.text = when {
            total == 0 -> "Sin nodos asignados"
            conDatos == 0 -> "Esperando lecturas..."
            else -> "$porcentaje% de nodos OK"
        }

        val totalTxt = if (total == 1) "1 nodo" else "$total nodos"
        val ultimoTimestamp = estados.mapNotNull { it.fecha.toLongOrNull() }.maxOrNull()
        val hora = ultimoTimestamp?.let {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
        }
        tvUltimaLectura.text = if (hora != null) "$totalTxt · Última $hora" else totalTxt

        val sinUmbral = estados.firstOrNull()
            ?.evaluadas
            ?.count { it.estado == Estado.SIN_UMBRAL && it.key != "bateria" }
            ?: 0

        if (sinUmbral > 0) {
            tvNotaUmbrales.visibility = View.VISIBLE
            tvNotaUmbrales.text = if (sinUmbral == 1) {
                "1 variable sin umbral configurado. Ajustala en Configuración para incluirla en el monitoreo."
            } else {
                "$sinUmbral variables sin umbral configurado. Ajustalas en Configuración para incluirlas en el monitoreo."
            }
        } else {
            tvNotaUmbrales.visibility = View.GONE
        }

        renderNodos()
    }

    private fun evaluarVariables(data: LecturaData): List<VariableEval> {
        val evaluadas = mutableListOf<VariableEval>()

        evaluadas.add(
            evaluarRango(
                key = "tempAire",
                nombre = "Temperatura del aire",
                sensor = "Sensor térmico • Ambiente exterior",
                categoria = "temp",
                iconRes = R.drawable.ic_thermostat,
                valor = data.tempAire,
                unidad = "°C",
                decimales = 1,
                min = tempMinConfig,
                max = tempMaxConfig,
                labelMin = "UMBRAL MÍNIMO",
                labelMax = "UMBRAL MÁXIMO"
            )
        )

        evaluadas.add(
            evaluarRango(
                key = "hum15",
                nombre = "Humedad de suelo 15 cm",
                sensor = "Sonda de humedad • Estrato somero",
                categoria = "humedad",
                iconRes = R.drawable.ic_water_drop,
                valor = data.hum15.toDouble(),
                unidad = "%",
                decimales = 0,
                min = humSueloMinConfig,
                max = null,
                labelMin = "MÍNIMO DE HUMEDAD",
                labelMax = "UMBRAL MÁXIMO"
            )
        )

        evaluadas.add(
            evaluarRango(
                key = "hum40",
                nombre = "Humedad de suelo 40 cm",
                sensor = "Sonda de humedad • Reserva profunda",
                categoria = "humedad",
                iconRes = R.drawable.ic_water_drop,
                valor = data.hum40.toDouble(),
                unidad = "%",
                decimales = 0,
                min = humSueloMinConfig,
                max = null,
                labelMin = "MÍNIMO DE HUMEDAD",
                labelMax = "UMBRAL MÁXIMO"
            )
        )

        evaluadas.add(
            evaluarRango(
                key = "vpd",
                nombre = "Déficit de presión de vapor",
                sensor = "Sensor ambiental • Estrés transpiratorio",
                categoria = "vpd",
                iconRes = R.drawable.ic_leaf,
                valor = data.vpd,
                unidad = "kPa",
                decimales = 2,
                min = vpdMinConfig,
                max = vpdMaxConfig,
                labelMin = "VPD MÍNIMO",
                labelMax = "VPD MÁXIMO"
            )
        )

        evaluadas.add(
            evaluarRango(
                key = "tempSuelo",
                nombre = "Temperatura radicular",
                sensor = "Sonda térmica • Zona de raíces",
                categoria = "tempSuelo",
                iconRes = R.drawable.ic_thermostat,
                valor = data.tempSuelo,
                unidad = "°C",
                decimales = 1,
                min = tempRadicularMinConfig,
                max = tempRadicularMaxConfig,
                labelMin = "MÍNIMO RADICULAR",
                labelMax = "MÁXIMO RADICULAR"
            )
        )

        evaluadas.add(
            if (data.bateria <= 0.0) {
                VariableEval(
                    key = "bateria",
                    nombre = "Tensión de batería",
                    sensor = "Hardware del nodo • Panel solar",
                    categoria = "bateria",
                    iconRes = R.drawable.ic_battery_alert,
                    valor = data.bateria,
                    unidad = "V",
                    decimales = 2,
                    estado = Estado.SIN_UMBRAL,
                    labelUmbral = "MÍNIMO SEGURO",
                    umbral = "Sin dato de batería",
                    detalle = "El nodo todavía no reportó la tensión de batería.",
                    delta = ""
                )
            } else {
                evaluarRango(
                    key = "bateria",
                    nombre = "Tensión de batería",
                    sensor = "Hardware del nodo • Panel solar",
                    categoria = "bateria",
                    iconRes = R.drawable.ic_battery_alert,
                    valor = data.bateria,
                    unidad = "V",
                    decimales = 2,
                    min = bateriaMinima,
                    max = null,
                    labelMin = "MÍNIMO SEGURO",
                    labelMax = "UMBRAL MÁXIMO"
                )
            }
        )

        return evaluadas
    }

    private fun evaluarRango(
        key: String,
        nombre: String,
        sensor: String,
        categoria: String,
        iconRes: Int,
        valor: Double,
        unidad: String,
        decimales: Int,
        min: Double?,
        max: Double?,
        labelMin: String,
        labelMax: String
    ): VariableEval {
        if (max != null && valor > max) {
            val exceso = valor - max
            val porcentaje = if (max != 0.0) (exceso / max) * 100.0 else 0.0
            return VariableEval(
                key, nombre, sensor, categoria, iconRes, valor, unidad, decimales,
                Estado.ALTO, labelMax, "${fmt(max, decimales)} $unidad",
                "Supera el máximo en ${fmt(exceso, decimales)} $unidad",
                "+${fmt(porcentaje, 1)}%"
            )
        }

        if (min != null && valor < min) {
            val deficit = min - valor
            val porcentaje = if (min != 0.0) (deficit / min) * 100.0 else 0.0
            return VariableEval(
                key, nombre, sensor, categoria, iconRes, valor, unidad, decimales,
                Estado.BAJO, labelMin, "${fmt(min, decimales)} $unidad",
                "Está ${fmt(deficit, decimales)} $unidad por debajo del mínimo",
                "-${fmt(porcentaje, 1)}%"
            )
        }

        if (min == null && max == null) {
            return VariableEval(
                key, nombre, sensor, categoria, iconRes, valor, unidad, decimales,
                Estado.SIN_UMBRAL, "SIN UMBRAL", "Sin límite configurado",
                "Configurá este límite en Ajustes para monitorearlo.",
                ""
            )
        }

        val rango = when {
            min != null && max != null -> "${fmt(min, decimales)} – ${fmt(max, decimales)} $unidad"
            max != null -> "Máx. ${fmt(max, decimales)} $unidad"
            else -> "Mín. ${fmt(min ?: 0.0, decimales)} $unidad"
        }

        return VariableEval(
            key, nombre, sensor, categoria, iconRes, valor, unidad, decimales,
            Estado.RANGO, "RANGO CONFIGURADO", rango,
            "Lectura dentro de los límites configurados.",
            "OK"
        )
    }

    private fun renderNodos() {
        llAnomalias.removeAllViews()

        val conProblemas = estadosPorNodo
            .map { estado ->
                estado to estado.anomalias.filter { filtroActual == "todas" || it.categoria == filtroActual }
            }
            .filter { (_, anomalias) -> anomalias.isNotEmpty() }
            .sortedWith(
                compareByDescending<Pair<NodoEstado, List<VariableEval>>> { it.second.size }
                    .thenBy { it.first.nodo }
            )

        val hayCritica = conProblemas.any { (_, anomalias) ->
            anomalias.any { it.estado == Estado.ALTO || it.key == "bateria" }
        }

        tvAnomaliasCount.apply {
            text = when {
                conProblemas.isEmpty() -> "SIN ALERTAS"
                conProblemas.size == 1 -> "1 NODO"
                else -> "${conProblemas.size} NODOS"
            }
            setTextColor(
                ContextCompat.getColor(
                    this@InicioActivity,
                    when {
                        conProblemas.isEmpty() -> R.color.green_neon
                        hayCritica -> R.color.danger_red
                        else -> R.color.alert_amber
                    }
                )
            )
            setBackgroundResource(
                when {
                    conProblemas.isEmpty() -> R.drawable.bg_badge_live
                    hayCritica -> R.drawable.bg_badge_danger
                    else -> R.drawable.bg_badge_warning
                }
            )
        }

        if (conProblemas.isEmpty()) {
            cardSinAnomalias.visibility = View.VISIBLE
            when {
                nodosActuales.isEmpty() -> {
                    tvSinAnomaliasTitulo.text = "Sin nodos asignados"
                    tvSinAnomaliasTitulo.setTextColor(ContextCompat.getColor(this, R.color.alert_amber))
                    tvSinAnomaliasDetalle.text = "No hay nodos asociados a esta cuenta."
                }
                estadosPorNodo.isEmpty() -> {
                    tvSinAnomaliasTitulo.text = "Esperando lecturas"
                    tvSinAnomaliasTitulo.setTextColor(ContextCompat.getColor(this, R.color.alert_amber))
                    tvSinAnomaliasDetalle.text = "Los nodos están registrados, pero todavía no enviaron datos."
                }
                estadosPorNodo.any { it.anomalias.isNotEmpty() } -> {
                    tvSinAnomaliasTitulo.text = "Sin alertas en este filtro"
                    tvSinAnomaliasTitulo.setTextColor(ContextCompat.getColor(this, R.color.alert_amber))
                    tvSinAnomaliasDetalle.text = "Hay nodos con alertas en otras variables. Probá con Todas."
                }
                else -> {
                    tvSinAnomaliasTitulo.text = "Red estable"
                    tvSinAnomaliasTitulo.setTextColor(ContextCompat.getColor(this, R.color.green_neon))
                    tvSinAnomaliasDetalle.text = "Todos los nodos monitoreados están dentro de los límites configurados."
                }
            }
            return
        }

        cardSinAnomalias.visibility = View.GONE
        val inflater = LayoutInflater.from(this)

        for ((estado, anomalias) in conProblemas) {
            val card = inflater.inflate(R.layout.item_nodo_alerta, llAnomalias, false)
            val critica = anomalias.any { it.estado == Estado.ALTO || it.key == "bateria" }
            val color = ContextCompat.getColor(this, if (critica) R.color.danger_red else R.color.alert_amber)
            val badgeFondo = if (critica) R.drawable.bg_badge_danger else R.drawable.bg_badge_warning

            card.findViewById<TextView>(R.id.tvNombreNodo).text =
                estado.nodo.uppercase(Locale.getDefault())

            card.findViewById<TextView>(R.id.tvSubtituloNodo).text = when {
                estado.comentario.isNotEmpty() -> estado.comentario
                anomalias.size == 1 -> "1 variable fuera de rango"
                else -> "${anomalias.size} variables fuera de rango"
            }

            card.findViewById<TextView>(R.id.tvBadgeNodo).apply {
                text = when {
                    anomalias.size > 1 -> "${anomalias.size} ALERTAS"
                    anomalias[0].estado == Estado.ALTO -> "EXCESO"
                    else -> "DÉFICIT"
                }
                setTextColor(color)
                setBackgroundResource(badgeFondo)
            }

            card.findViewById<ImageView>(R.id.imgIconoNodo).setColorFilter(color)

            val contenedor = card.findViewById<LinearLayout>(R.id.llVariablesNodo)

            for (item in anomalias) {
                val row = inflater.inflate(R.layout.item_variable_alerta, contenedor, false)
                val criticaVar = item.estado == Estado.ALTO || item.key == "bateria"
                val colorVar = ContextCompat.getColor(
                    this,
                    if (criticaVar) R.color.danger_red else R.color.alert_amber
                )
                val badgeVar = if (criticaVar) R.drawable.bg_badge_danger else R.drawable.bg_badge_warning

                row.findViewById<TextView>(R.id.tvNombreVariable).text = item.nombre

                row.findViewById<ImageView>(R.id.imgIconoVariable).apply {
                    setImageResource(item.iconRes)
                    setColorFilter(colorVar)
                }

                row.findViewById<TextView>(R.id.tvBadgeVariable).apply {
                    text = if (item.estado == Estado.ALTO) "EXCESO" else "DÉFICIT"
                    setTextColor(colorVar)
                    setBackgroundResource(badgeVar)
                }

                row.findViewById<TextView>(R.id.tvValorVariable).apply {
                    text = fmt(item.valor, item.decimales)
                    setTextColor(colorVar)
                }

                row.findViewById<TextView>(R.id.tvUnidadVariable).apply {
                    text = item.unidad
                    setTextColor(colorVar)
                }

                row.findViewById<TextView>(R.id.tvLabelUmbralVariable).text = item.labelUmbral
                row.findViewById<TextView>(R.id.tvUmbralVariable).text = item.umbral
                row.findViewById<TextView>(R.id.tvDetalleVariable).text = item.detalle
                row.findViewById<TextView>(R.id.tvDeltaVariable).apply {
                    text = item.delta
                    setTextColor(colorVar)
                }

                contenedor.addView(row)
            }

            llAnomalias.addView(card)
        }
    }

    private fun exportarExcel() {
        if (exportando) return

        exportando = true
        val progreso = ProgressDialog(this).apply {
            setTitle("Generando Excel")
            setMessage("Leyendo el historial de todos los nodos...")
            setCancelable(false)
            show()
        }

        dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val resumen = construirResumen()
                val historial = construirHistorial(snapshot)
                val umbrales = construirUmbrales()

                if (resumen.isEmpty() && historial.isEmpty()) {
                    if (!isFinishing) progreso.dismiss()
                    exportando = false
                    Toast.makeText(
                        this@InicioActivity,
                        "No hay datos para exportar",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                Thread {
                    try {
                        val archivo = ExcelExporter.generar(
                            applicationContext,
                            resumen,
                            historial,
                            umbrales
                        )
                        runOnUiThread {
                            if (isFinishing || isDestroyed) {
                                exportando = false
                                return@runOnUiThread
                            }
                            progreso.dismiss()
                            exportando = false
                            Toast.makeText(
                                this@InicioActivity,
                                "Excel listo con ${historial.size} lectura(s)",
                                Toast.LENGTH_SHORT
                            ).show()
                            compartirExcel(archivo)
                        }
                    } catch (e: Exception) {
                        Log.e("InicioActivity", "Error al generar Excel", e)
                        runOnUiThread {
                            if (isFinishing || isDestroyed) {
                                exportando = false
                                return@runOnUiThread
                            }
                            progreso.dismiss()
                            exportando = false
                            Toast.makeText(
                                this@InicioActivity,
                                "No se pudo generar el Excel",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }.start()
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isFinishing) progreso.dismiss()
                exportando = false
                Toast.makeText(
                    this@InicioActivity,
                    "Error al leer los datos: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun construirResumen(): List<FilaResumenExcel> {
        return nodosActuales.map { nodo ->
            val lectura = lecturaPorNodo[nodo]
            val estado = estadosPorNodo.find { it.nodo == nodo }

            FilaResumenExcel(
                nodo = nodo,
                comentario = comentariosPorNodo[nodo].orEmpty(),
                ultimaLectura = lectura?.let { formatearFecha(it.fecha) } ?: "Sin datos",
                tempAire = lectura?.tempAire,
                humAire = lectura?.humAire,
                presion = lectura?.presion,
                hum15 = lectura?.hum15?.toDouble(),
                hum40 = lectura?.hum40?.toDouble(),
                tempSuelo = lectura?.tempSuelo,
                vpd = lectura?.vpd,
                bateria = lectura?.bateria,
                estado = when {
                    lectura == null -> "Sin datos"
                    estado == null || estado.anomalias.isEmpty() -> "En rango"
                    else -> "Con alertas (${estado.anomalias.size})"
                }
            )
        }
    }

    private fun construirHistorial(snapshot: DataSnapshot): List<FilaHistorialExcel> {
        val filas = mutableListOf<FilaHistorialExcel>()
        val formatoFecha = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        for (nodoSnap in snapshot.children) {
            val nodo = nodoSnap.key ?: continue
            val comentario = comentariosPorNodo[nodo].orEmpty()

            for (lecturaSnap in nodoSnap.children) {
                fun valor(campo: String): Double? =
                    lecturaSnap.child(campo).value?.toString()?.toDoubleOrNull()

                val tempAire = valor("tempAire")
                val humAire = valor("humAire")
                val presion = valor("presion")
                val hum15 = valor("hum15")
                val hum40 = valor("hum40")
                val tempSuelo = valor("tempSuelo")
                val vpd = valor("vpd")
                val bateria = valor("bateria")

                if (listOf(tempAire, humAire, presion, hum15, hum40, tempSuelo, vpd, bateria)
                        .all { it == null }
                ) {
                    continue
                }

                val key = lecturaSnap.key.orEmpty()
                val fechaRaw = lecturaSnap.child("fecha").value?.toString().orEmpty()
                val timestamp = key.toLongOrNull()
                    ?: lecturaSnap.child("timestamp").value?.toString()?.toLongOrNull()
                    ?: 0L
                val fecha = when {
                    timestamp > 0L -> formatoFecha.format(Date(timestamp))
                    fechaRaw.isNotBlank() -> fechaRaw
                    else -> key
                }

                filas.add(
                    FilaHistorialExcel(
                        timestamp = timestamp,
                        nodo = nodo,
                        comentario = comentario,
                        fecha = fecha,
                        tempAire = tempAire,
                        humAire = humAire,
                        presion = presion,
                        hum15 = hum15,
                        hum40 = hum40,
                        tempSuelo = tempSuelo,
                        vpd = vpd,
                        bateria = bateria
                    )
                )
            }
        }

        return filas.sortedWith(
            compareBy<FilaHistorialExcel> { it.timestamp }.thenBy { it.nodo }
        )
    }

    private fun construirUmbrales(): List<FilaUmbralExcel> {
        return listOf(
            FilaUmbralExcel("Temperatura del aire", tempMinConfig, tempMaxConfig, "°C"),
            FilaUmbralExcel("Humedad de suelo 15 cm", humSueloMinConfig, null, "%"),
            FilaUmbralExcel("Humedad de suelo 40 cm", humSueloMinConfig, null, "%"),
            FilaUmbralExcel("Déficit de presión de vapor (VPD)", vpdMinConfig, vpdMaxConfig, "kPa"),
            FilaUmbralExcel("Temperatura radicular", tempRadicularMinConfig, tempRadicularMaxConfig, "°C"),
            FilaUmbralExcel("Tensión de batería", bateriaMinima, null, "V")
        )
    }

    private fun formatearFecha(valor: String): String {
        val millis = valor.toLongOrNull()
        return if (millis != null) {
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(millis))
        } else {
            valor.ifBlank { "Sin datos" }
        }
    }

    private fun compartirExcel(archivo: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                archivo
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Informe MedCamp")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Informe MedCamp con resumen de nodos, historial de lecturas y umbrales."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Guardar o compartir Excel"))
        } catch (e: Exception) {
            Log.e("InicioActivity", "Error al compartir Excel", e)
            Toast.makeText(this, "No se pudo abrir el archivo Excel", Toast.LENGTH_LONG).show()
        }
    }

    private fun fmt(valor: Double, decimales: Int): String =
        String.format(Locale.getDefault(), "%.${decimales}f", valor)

    override fun onDestroy() {
        super.onDestroy()

        nodosListener?.let { dbRef.removeEventListener(it) }
        configListener?.let { configRef.removeEventListener(it) }
        comentariosListener?.let { nodosInfoRef.removeEventListener(it) }

        for ((nodo, listener) in listenerPorNodo) {
            queryPorNodo[nodo]?.removeEventListener(listener)
        }
        listenerPorNodo.clear()
        queryPorNodo.clear()
    }
}
