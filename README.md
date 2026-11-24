# MedCamp-App

App Android (Kotlin) para el monitoreo agrícola de MedCamp.

Recibe por HTTP los datos de un nodo de campo ESP32 que transmite por LoRa,
muestra las lecturas en tiempo real, grafica el historial y genera alertas
por VPD, heladas, calor extremo y suelo seco.

## Estructura (reorganizada)

1. **Inicio** (`InicioActivity`): pantalla de entrada con resumen general —
   total de nodos, nodos activos, alertas, nodos sin datos y últimos nodos
   agregados. Desde acá se accede al resto de las secciones.
2. **Panel Principal** (`MainActivity`): la telemetría en vivo de siempre,
   ahora como sección secundaria del menú. Se accede desde *Inicio →
   2 · Panel Principal* o con el botón *← Inicio* para volver.
3. **Agregar nodo** (`AgregarNodoActivity`): alta manual con dos campos —
   *Nombre del nodo* (obligatorio) y *Descripción* (opcional). Los comentarios
   se editan tocando "Editar comentarios" en la lista de últimos nodos del Inicio.
4. **Exportar a Excel** (`ExcelExporter`): botón que genera un `.xlsx` real con
   tres hojas:
   - `Nodos`: ID, nombre, descripción, comentarios, estado, fecha de creación,
     última actualización y todas las variables de telemetría disponibles.
   - `Alertas`: registro y detalle de cada alerta.
   - `Resumen`: métricas generales y fecha de exportación.

   El archivo se guarda en `Descargas/MedCamp_Nodos_AAAAmmdd_HHMMss.xlsx`.
   No requiere permisos ni librerías adicionales (se genera con `java.util.zip`).

Los nodos que informa el servidor se registran automáticamente en el resumen.
Los datos de sesión y de nodos se guardan en `SharedPreferences`, así que el
resumen y la exportación funcionan también sin conexión.

## Módulos

- `InicioActivity`: resumen y menú.
- `LoginActivity`: inicio de sesión.
- `MainActivity`: panel principal, nodos, clima y alertas.
- `AgregarNodoActivity`: alta de nodos.
- `DetalleGraficoActivity`: gráficos históricos.
- `HistorialActivity`: historial local de alertas.
- `NodeRepository` / `NodoInfo`: persistencia de metadata de nodos.
- `ExcelExporter`: generación del `.xlsx`.

## Compilar

Abrir la carpeta en Android Studio y ejecutar, o:

    gradlew.bat assembleDebug
