package com.medcamp.monitor

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MedCampApiService {

    // Recibe el token de sesión y el filtro opcional del nodo activo
    @GET("api/datos_app")
    fun getDatos(
        @Header("Authorization") token: String,
        @Query("idNodo") idNodo: String?
    ): Call<LecturaData>

    // Para traer el listado de todos los nodos que pertenecen al usuario
    @GET("api/nodos")
    fun obtenerNodos(
        @Header("Authorization") token: String
    ): Call<List<String>>

    // 🔴 CORREGIDO: Ahora sí acepta el idNodo para no mezclar gráficos de lotes distintos
    @GET("api/historial/{variable}")
    fun getHistorial(
        @Header("Authorization") token: String,
        @Path("variable") variable: String,
        @Query("horas") horas: Int,
        @Query("idNodo") idNodo: String? // <-- Faltaba esta línea crucial
    ): Call<List<HistorialData>>

    // Endpoint para la autenticación de usuarios al iniciar la app
    @POST("api/login")
    fun login(@Body campos: LoginRequest): Call<LoginResponse>
}

// === CLASES DE DATOS ===
data class HistorialData(
    val fecha: String,
    val valor: Double
)

data class LecturaData(
    val vpd: Double,
    val tempAire: Double,
    val humAire: Double,
    val presion: Double,
    val hum15: Int,
    val hum40: Int,
    val tempSuelo: Double,
    val bateria: Double,
    val wifiCon: Int,
    val loraCon: Int
)

data class LoginRequest(val email: String, val pass: String)
data class LoginResponse(val token: String, val nombre: String, val error: String?)