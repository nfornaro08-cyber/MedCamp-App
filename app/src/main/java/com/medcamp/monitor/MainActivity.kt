package com.medcamp.monitor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var dbRef: DatabaseReference
    private lateinit var configRef: DatabaseReference
    private lateinit var nodosInfoRef: DatabaseReference

    private var lecturaQuery: Query? = null
    private var lecturaListener: ValueEventListener? = null
    private var configListener: ValueEventListener? = null
    private var comentariosListener: ValueEventListener? = null
    private var adapterNodos: ArrayAdapter<String>? = null

    private var nodoSeleccionadoActual: String? = null
    private val listaNodosActual = mutableListOf<String>()
    private val comentariosPorNodo = mutableMapOf<String, String>()

    // Vistas principales
    private lateinit var spinnerNodos: Spinner
    private lateinit var imgWifi: ImageView
    private lateinit var tvVPD: TextView
    private lateinit var tvTempAire: TextView
    private lateinit var tvHumAire: TextView
    private lateinit var tvPresion: TextView
    private lateinit var tvHum15: TextView
    private lateinit var tvHum40: TextView
    private lateinit var tvTempSuelo: TextView
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var etComentarioNodo: EditText
    private lateinit var btnGuardarComentario: Button

    private val navListener = NavigationBarView.OnItemSelectedListener { item ->
        when (item.itemId) {
            R.id.nav_nodos -> true
            R.id.nav_resumen -> {
                val intent = Intent(this, InicioActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
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

    // Umbrales de MedCamp
    private var tempMaxConfig: Double? = null
    private var tempMinConfig: Double? = null
    private var vpdMaxConfig: Double? = null
    private var vpdMinConfig: Double? = null
    private var humSueloMinConfig: Double? = null
    private var tempRadicularMinConfig: Double? = null
    private var tempRadicularMaxConfig: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: "DdaAJqXxIcadkzamNl16yWyy8k13"

        // Referencia directa al UID activo del usuario logueado
        dbRef = FirebaseDatabase.getInstance().getReference("usuarios").child(uid).child("lecturas")
        configRef = FirebaseDatabase.getInstance().getReference("usuarios").child(uid).child("configuracion")
        nodosInfoRef = FirebaseDatabase.getInstance().getReference("usuarios").child(uid).child("nodos")

        crearCanalNotificaciones()
        solicitarPermisoNotificaciones()
        escucharConfiguracion()

        // Inicialización de componentes XML
        spinnerNodos = findViewById(R.id.spinnerNodos)
        imgWifi = findViewById(R.id.imgWifi)
        val btnAjustes = findViewById<ImageView>(R.id.btnAjustes)

        tvVPD = findViewById(R.id.tvVPD)
        tvTempAire = findViewById(R.id.tvTempAire)
        tvHumAire = findViewById(R.id.tvHumAire)
        tvPresion = findViewById(R.id.tvPresion)
        tvHum15 = findViewById(R.id.tvHum15)
        tvHum40 = findViewById(R.id.tvHum40)
        tvTempSuelo = findViewById(R.id.tvTempSuelo)
        bottomNav = findViewById(R.id.bottomNavNodos)
        val btnIrHistorial = findViewById<Button>(R.id.btnIrHistorial)
        etComentarioNodo = findViewById(R.id.etComentarioNodo)
        btnGuardarComentario = findViewById(R.id.btnGuardarComentario)

        btnGuardarComentario.setOnClickListener { guardarComentario() }

        escucharComentariosNodos()
        cargarNodosEnSpinner()

        btnAjustes?.setOnClickListener {
            val intent = Intent(this, ConfiguracionActivity::class.java)
            startActivity(intent)
        }

        btnIrHistorial?.setOnClickListener {
            val intent = Intent(this, HistorialActivity::class.java)
            startActivity(intent)
        }

        bottomNav.setOnItemSelectedListener(navListener)
        bottomNav.selectedItemId = R.id.nav_nodos
    }

    override fun onResume() {
        super.onResume()
        bottomNav.setOnItemSelectedListener(null)
        bottomNav.selectedItemId = R.id.nav_nodos
        bottomNav.setOnItemSelectedListener(navListener)
    }

    private fun solicitarPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun crearCanalNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "CANAL_ALERTAS_MEDCAMP",
                "Alertas MedCamp",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisos de estrés hídrico, sanidad y mantenimiento de nodo"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun lanzarNotificacionEstado(titulo: String, mensaje: String) {
        val builder = NotificationCompat.Builder(this, "CANAL_ALERTAS_MEDCAMP")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensaje))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun escucharConfiguracion() {
        configListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    tempMaxConfig = snapshot.child("tempMax").value?.toString()?.toDoubleOrNull()
                    tempMinConfig = snapshot.child("tempMin").value?.toString()?.toDoubleOrNull()
                    vpdMaxConfig = snapshot.child("vpdMax").value?.toString()?.toDoubleOrNull()
                    vpdMinConfig = snapshot.child("vpdMin").value?.toString()?.toDoubleOrNull()
                    humSueloMinConfig = snapshot.child("humSueloMin").value?.toString()?.toDoubleOrNull()
                    tempRadicularMinConfig = snapshot.child("tempRadicularMin").value?.toString()?.toDoubleOrNull()
                    tempRadicularMaxConfig = snapshot.child("tempRadicularMax").value?.toString()?.toDoubleOrNull()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MainActivity", "Error al cargar configuración: ${error.message}")
            }
        }
        configRef.addValueEventListener(configListener as ValueEventListener)
    }

    private fun cargarNodosEnSpinner() {
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val nuevosNodos = mutableListOf<String>()
                for (child in snapshot.children) {
                    child.key?.let { nuevosNodos.add(it) }
                }

                if (nuevosNodos.isNotEmpty()) {
                    // Si la lista de nodos es idéntica a la que ya está cargada, no reiniciamos el Spinner
                    if (nuevosNodos == listaNodosActual) return

                    listaNodosActual.clear()
                    listaNodosActual.addAll(nuevosNodos)

                    val adapter = object : ArrayAdapter<String>(
                        this@MainActivity,
                        android.R.layout.simple_spinner_item,
                        listaNodosActual
                    ) {
                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val v = super.getView(position, convertView, parent)
                            (v as? TextView)?.apply {
                                setTextColor(Color.WHITE)
                                textSize = 16f
                            }
                            return v
                        }

                        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val v = this@MainActivity.layoutInflater.inflate(R.layout.item_nodo_spinner, parent, false)
                            val nodo = getItem(position).orEmpty()
                            v.findViewById<TextView>(R.id.tvNombreNodoSpinner).text = nodo

                            val descripcion = comentariosPorNodo[nodo].orEmpty().trim()
                            v.findViewById<TextView>(R.id.tvDescripcionNodoSpinner).apply {
                                text = descripcion
                                visibility = if (descripcion.isEmpty()) View.GONE else View.VISIBLE
                            }
                            return v
                        }
                    }

                    adapterNodos = adapter
                    spinnerNodos.adapter = adapter

                    // Recuperar la posición del nodo que el usuario tenía seleccionado
                    val indicePrevio = listaNodosActual.indexOf(nodoSeleccionadoActual)
                    if (indicePrevio >= 0) {
                        spinnerNodos.setSelection(indicePrevio, false)
                    } else {
                        nodoSeleccionadoActual = listaNodosActual[0]
                        spinnerNodos.setSelection(0, false)
                        escucharUltimaLectura(listaNodosActual[0])
                        cargarComentario(listaNodosActual[0])
                    }

                    spinnerNodos.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            val seleccionado = listaNodosActual[position]
                            if (seleccionado != nodoSeleccionadoActual) {
                                nodoSeleccionadoActual = seleccionado
                                escucharUltimaLectura(seleccionado)
                                cargarComentario(seleccionado)
                            }
                        }

                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
                } else {
                    Toast.makeText(this@MainActivity, "No hay nodos asignados a esta cuenta", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MainActivity", "Error al consultar Firebase: ${error.message}")
                Toast.makeText(this@MainActivity, "Error Firebase: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun escucharComentariosNodos() {
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
                adapterNodos?.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MainActivity", "Error al cargar descripciones: ${error.message}")
            }
        }
        comentariosListener = listener
        nodosInfoRef.addValueEventListener(listener)
    }

    private fun cargarComentario(nodo: String) {
        nodosInfoRef.child(nodo).child("comentario")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (nodo != nodoSeleccionadoActual) return
                    etComentarioNodo.setText(snapshot.getValue(String::class.java).orEmpty())
                    etComentarioNodo.setSelection(etComentarioNodo.text.length)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("MainActivity", "Error al cargar comentario: ${error.message}")
                }
            })
    }

    private fun guardarComentario() {
        val nodo = nodoSeleccionadoActual ?: run {
            Toast.makeText(this, "Seleccioná un nodo primero", Toast.LENGTH_SHORT).show()
            return
        }

        val comentario = etComentarioNodo.text.toString().trim()
        btnGuardarComentario.isEnabled = false

        nodosInfoRef.child(nodo).child("comentario").setValue(comentario)
            .addOnCompleteListener { task ->
                btnGuardarComentario.isEnabled = true
                if (task.isSuccessful) {
                    if (comentario.isEmpty()) {
                        comentariosPorNodo.remove(nodo)
                    } else {
                        comentariosPorNodo[nodo] = comentario
                    }
                    adapterNodos?.notifyDataSetChanged()
                    Toast.makeText(this, "Comentario guardado", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "No se pudo guardar el comentario", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun escucharUltimaLectura(idNodo: String) {
        lecturaListener?.let { listener ->
            lecturaQuery?.removeEventListener(listener)
        }

        lecturaQuery = dbRef.child(idNodo).orderByKey().limitToLast(1)
        lecturaListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (child in snapshot.children) {
                        val tempAire = child.child("tempAire").value?.toString()?.toDoubleOrNull() ?: 0.0
                        val humAire = child.child("humAire").value?.toString()?.toDoubleOrNull() ?: 0.0
                        val presion = child.child("presion").value?.toString()?.toDoubleOrNull() ?: 0.0
                        val hum15 = child.child("hum15").value?.toString()?.toDoubleOrNull() ?: 0.0
                        val hum40 = child.child("hum40").value?.toString()?.toDoubleOrNull() ?: 0.0
                        val tempSuelo = child.child("tempSuelo").value?.toString()?.toDoubleOrNull() ?: 0.0
                        val vpd = child.child("vpd").value?.toString()?.toDoubleOrNull() ?: 0.0
                        val bateria = child.child("bateria").value?.toString()?.toDoubleOrNull() ?: 0.0

                        tvVPD.text = String.format(Locale.getDefault(), "%.1f", vpd)
                        tvTempAire.text = String.format(Locale.getDefault(), "%.1f °C", tempAire)
                        tvHumAire.text = String.format(Locale.getDefault(), "%.0f %%", humAire)
                        tvPresion.text = String.format(Locale.getDefault(), "%.0f hPa", presion)
                        tvHum15.text = String.format(Locale.getDefault(), "%.0f%%", hum15)
                        tvHum40.text = String.format(Locale.getDefault(), "%.0f%%", hum40)
                        tvTempSuelo.text = String.format(Locale.getDefault(), "%.1f °C", tempSuelo)

                        val verdeNeon = ContextCompat.getColor(this@MainActivity, R.color.green_neon)
                        imgWifi.setColorFilter(verdeNeon)

                        val lecturaData = LecturaData(
                            bateria = bateria,
                            hum15 = hum15.toInt(),
                            hum40 = hum40.toInt(),
                            humAire = humAire,
                            presion = presion,
                            tempAire = tempAire,
                            tempSuelo = tempSuelo,
                            vpd = vpd
                        )
                        evaluarYGuardarAlertasMedCamp(idNodo, lecturaData)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MainActivity", "Error en lecturas: ${error.message}")
                Toast.makeText(this@MainActivity, "Error en nodo $idNodo: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }

        lecturaQuery?.addValueEventListener(lecturaListener as ValueEventListener)
    }

    private fun evaluarYGuardarAlertasMedCamp(idNodo: String, data: LecturaData) {
        tempMaxConfig?.let { max ->
            if (data.tempAire > max) {
                procesarAlerta("🚨 Alerta de Golpe de Calor", "Temp. Aire en $idNodo de ${data.tempAire}°C (> $max°C). Riesgo de aborto de flores.")
            }
        }
        tempMinConfig?.let { min ->
            if (data.tempAire < min) {
                procesarAlerta("❄️ Alerta Temprana de Helada", "Temp. Aire en $idNodo cayó a ${data.tempAire}°C (< $min°C). Activar protección.")
            }
        }

        humSueloMinConfig?.let { minHum ->
            if (data.hum15 < minHum) {
                procesarAlerta("💧 Alerta Humedad Crítica", "Humedad 15cm en $idNodo cayó a ${data.hum15}% (< $minHum%). Riego urgente requerido.")
            }
        }

        vpdMaxConfig?.let { maxVpd ->
            if (data.vpd > maxVpd) {
                procesarAlerta("☀️ Estrés Hídrico (VPD)", "VPD en $idNodo alcanzó ${data.vpd} kPa (> $maxVpd kPa). Cierre estomático activo.")
            }
        }

        vpdMinConfig?.let { minVpd ->
            if (data.vpd < minVpd && data.humAire > 90.0) {
                procesarAlerta("🍄 Alerta Sanitaria Fúngica", "VPD en $idNodo es ${data.vpd} kPa y humedad ${data.humAire}%. Monitorear hongos.")
            }
        }

        tempRadicularMinConfig?.let { minRad ->
            if (data.tempSuelo < minRad) {
                procesarAlerta("🥶 Bloqueo Nutricional", "Temp. de raíz en $idNodo es ${data.tempSuelo}°C (< $minRad°C). Asimilación suspendida.")
            }
        }
        tempRadicularMaxConfig?.let { maxRad ->
            if (data.tempSuelo > maxRad) {
                procesarAlerta("🔥 Calentamiento Radicular", "Temp. de raíz en $idNodo es ${data.tempSuelo}°C (> $maxRad°C). Riesgo de anoxia.")
            }
        }

        if (data.bateria in 0.1..3.4) {
            procesarAlerta("🔋 Alerta de Hardware / Batería", "Tensión en $idNodo bajo nivel crítico (${data.bateria}V). Limpiar panel solar.")
        }
    }

    private fun procesarAlerta(titulo: String, mensaje: String) {
        val prefs = getSharedPreferences("HISTORIAL_PREFS", Context.MODE_PRIVATE)
        val historialPrevio = prefs.getString("lista_alertas", "") ?: ""

        val formatoFecha = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val fechaHora = formatoFecha.format(Date())
        val nuevaEntrada = "⚠️ [$fechaHora] $titulo: $mensaje"

        if (historialPrevio.contains(nuevaEntrada)) return

        val historialNuevo = if (historialPrevio.isEmpty()) {
            nuevaEntrada
        } else {
            "$historialPrevio|$nuevaEntrada"
        }

        prefs.edit().putString("lista_alertas", historialNuevo).apply()
        lanzarNotificacionEstado(titulo, mensaje)
    }

    override fun onDestroy() {
        super.onDestroy()
        lecturaListener?.let { listener ->
            lecturaQuery?.removeEventListener(listener)
        }
        configListener?.let { listener ->
            configRef.removeEventListener(listener)
        }
        comentariosListener?.let { listener ->
            nodosInfoRef.removeEventListener(listener)
        }
    }
}