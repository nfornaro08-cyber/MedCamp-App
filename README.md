# MedCamp App

Aplicación Android (Kotlin) para el monitoreo de nodos agrícolas MedCamp.

## Funciones

- **Resumen**: estado global de todos los nodos, con cantidad de nodos estables, con alertas y sin datos, porcentaje de nodos en rango y última lectura. Las tarjetas muestran cada nodo con problemas y el detalle de las variables fuera de rango (valor, umbral y exceso o déficit).
- **Nodos**: panel con la telemetría del nodo seleccionado. El desplegable muestra el nombre de cada nodo y su descripción, y permite editar el comentario/identificación del nodo.
- **Alertas**: historial local de alertas.
- **Configuración**: umbrales de temperatura del aire, VPD, humedad de suelo y temperatura radicular.
- **Exportación a Excel**: genera un archivo `.xlsx` real, sin librerías externas, con tres hojas:
  - `Resumen`: estado actual de cada nodo, comentario, última lectura y variables.
  - `Historial`: todas las lecturas registradas.
  - `Umbrales`: límites configurados y batería mínima.
- **Sin gráficos**: la app se concentra en el resumen, las alertas y los datos actuales.

## Datos en Firebase

Firebase Realtime Database:

- `usuarios/{uid}/lecturas/{nodo}/{timestamp}`: lecturas de los sensores.
- `usuarios/{uid}/configuracion`: umbrales configurados.
- `usuarios/{uid}/nodos/{nodo}/comentario`: descripción o comentario del nodo.

## Compilar

Requisitos: Android Studio, JDK 11 o superior y Android SDK 34.

```bash
gradlew.bat assembleDebug
```

El APK de depuración queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Descargar APK

[MedCamp.apk](https://raw.githubusercontent.com/nfornaro08-cyber/medcamp-apk/main/MedCamp.apk)
