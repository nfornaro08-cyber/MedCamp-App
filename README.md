# MedCamp-App

App Android (Kotlin) para el monitoreo agricola de MedCamp.

Recibe por HTTP los datos de un nodo de campo ESP32 que transmite por LoRa,
muestra las lecturas en tiempo real, grafica el historial y genera alertas
por VPD, heladas, calor extremo y suelo seco.

## Modulos

- LoginActivity: inicio de sesion.
- MainActivity: panel principal, nodos, clima y alertas.
- DetalleGraficoActivity: graficos historicos.
- HistorialActivity: historial local de alertas.

## Compilar

Abrir la carpeta en Android Studio y ejecutar, o:

    gradlew.bat assembleDebug