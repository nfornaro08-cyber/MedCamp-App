package com.medcamp.monitor

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Alta de nodos: nombre obligatorio + descripción opcional. */
class AgregarNodoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agregar_nodo)

        val etNombre = findViewById<EditText>(R.id.etNombreNodo)
        val etDescripcion = findViewById<EditText>(R.id.etDescripcionNodo)

        findViewById<Button>(R.id.btnGuardarNodo).setOnClickListener {
            val nombre = etNombre.text.toString().trim()
            if (nombre.isEmpty()) {
                etNombre.error = "Ingresá un nombre para el nodo"
                return@setOnClickListener
            }
            val nodo = NodeRepository.agregar(this, nombre, etDescripcion.text.toString())
            Toast.makeText(this, "Nodo “${nodo.nombre}” agregado", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.btnCancelarNodo).setOnClickListener { finish() }
    }
}
