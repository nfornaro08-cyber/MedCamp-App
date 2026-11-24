package com.medcamp.monitor

/**
 * Metadata de un nodo. Los datos de telemetría se guardan aparte (OFFLINE_CACHE),
 * acá solo se guarda lo que el usuario registra manualmente.
 */
data class NodoInfo(
    val id: String,
    val nombre: String,
    val descripcion: String = "",
    val comentarios: String = "",
    val fechaCreacion: String = ""
)
