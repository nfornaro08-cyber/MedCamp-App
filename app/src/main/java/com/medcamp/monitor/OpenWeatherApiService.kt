package com.medcamp.monitor

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenWeatherApiService {
    @GET("data/2.5/weather")
    fun getWeatherByCity(
        @Query("q") cityName: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric", // Para que nos devuelva en °C
        @Query("lang") lang: String = "es"        // Para que la descripción sea en español
    ): Call<WeatherResponse>
}