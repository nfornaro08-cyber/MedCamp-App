package com.medcamp.monitor

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Guarda el alta de nodos (nombre, descripción, comentarios y fecha) en
 * SharedPreferences, sin depender del backend. Así el resumen y el Excel
 * funcionan también sin conexión.
 */
object NodeRepository {

    private const val PREFS = "NODOS_PREFS"
    private const val KEY_NODOS = "lista_nodos"
    private const val KEY_SERVIDOR = "nodos_servidor"

    private val gson = Gson()
    private val tipoNodos = object : TypeToken<MutableList<NodoInfo>>() {}.type
    private val tipoIds = object : TypeToken<MutableList<String>>() {}.type

    private fun fechaAhora(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun obtener(context: Context): MutableList<NodoInfo> {
        val raw = prefs(context).getString(KEY_NODOS, "[]") ?: "[]"
        return try {
            gson.fromJson<MutableList<NodoInfo>>(raw, tipoNodos) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun guardar(context: Context, nodos: List<NodoInfo>) {
        prefs(context).edit().putString(KEY_NODOS, gson.toJson(nodos)).apply()
    }

    /** Alta manual: nombre obligatorio, descripción opcional. */
    fun agregar(context: Context, nombre: String, descripcion: String): NodoInfo {
        val slug = nombre.trim().lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        val id = if (slug.isEmpty()) "nodo_${System.currentTimeMillis() / 1000}" else slug

        val nodo = NodoInfo(
            id = id,
            nombre = nombre.trim(),
            descripcion = descripcion.trim(),
            comentarios = "",
            fechaCreacion = fechaAhora()
        )

        val nodos = obtener(context).filter { it.id != id }.toMutableList()
        nodos.add(nodo)
        guardar(context, nodos)
        return nodo
    }

    /** Da de alta automáticamente los nodos que aparecen en el servidor. */
    fun registrarDesdeServidor(context: Context, ids: List<String>) {
        val idsServidor = ids.filter {
            it.isNotBlank() && it != "Sin nodos activos" && it != "Error de red"
        }
        prefs(context).edit().putString(KEY_SERVIDOR, gson.toJson(idsServidor)).apply()

        val existentes = obtener(context).map { it.id }.toSet()
        val nuevos = idsServidor.filter { it !in existentes }
        if (nuevos.isEmpty()) return

        val lista = obtener(context)
        val fecha = fechaAhora()
        nuevos.forEach { lista.add(NodoInfo(it, it, "", "", fecha)) }
        guardar(context, lista)
    }

    /** IDs que respondieron en el último consultado al servidor. */
    fun idsDelServidor(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_SERVIDOR, "[]") ?: "[]"
        return try {
            gson.fromJson<List<String>>(raw, tipoIds) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun actualizarComentarios(context: Context, id: String, comentarios: String) {
        val nodos = obtener(context)
        val indice = nodos.indexOfFirst { it.id == id }
        if (indice >= 0) {
            nodos[indice] = nodos[indice].copy(comentarios = comentarios.trim())
            guardar(context, nodos)
        }
    }
}
