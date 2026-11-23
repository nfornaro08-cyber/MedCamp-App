package com.medcamp.monitor

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // 1. Referencias a los componentes del diseño (XML)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPass = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        // 2. Configuración de Retrofit
        // IMPORTANTE: Cambiá esta URL por la de ngrok que te pase Feli hoy
        val urlNgrok = "https://distract-overpass-commodity.ngrok-free.dev/"

        val retrofit = Retrofit.Builder()
            .baseUrl(urlNgrok)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(MedCampApiService::class.java)

        // 3. Acción del botón
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPass.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Por favor, completá los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Llamada al servidor
            val loginRequest = LoginRequest(email, pass)

            service.login(loginRequest).enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    if (response.isSuccessful) {
                        val loginResponse = response.body()
                        val nombreUsuario = loginResponse?.nombre ?: "Usuario"
                        // 🔴 NUEVO: Extraemos el token string que nos da el backend
                        val tokenRecibido = loginResponse?.token ?: ""

                        Toast.makeText(this@LoginActivity, "¡Bienvenido $nombreUsuario!", Toast.LENGTH_SHORT).show()

                        // Ir a la pantalla principal
                        val intent = Intent(this@LoginActivity, MainActivity::class.java)

                        // 🔴 NUEVO: Le pasamos el token al MainActivity para que pueda consultar los sensores
                        intent.putExtra("TOKEN_JWT", tokenRecibido)

                        startActivity(intent)
                        finish() // Cerramos el Login para que no puedan volver atrás
                    } else {
                        // El servidor respondió pero con error (ej: 401 clave mal)
                        Toast.makeText(this@LoginActivity, "Email o clave incorrectos", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    // No hubo conexión (server apagado o URL de ngrok mal)
                    Log.e("LoginActivity", "Error: ${t.message}")
                    Toast.makeText(this@LoginActivity, "Error de conexión con el servidor", Toast.LENGTH_LONG).show()
                }
            })
        }
    }
}