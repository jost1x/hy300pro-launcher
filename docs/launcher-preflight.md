# Preflight: Inicio del launcher

## Proceso

- Emulador o dispositivo mínimo y paisaje: no verificado.
- Texto de sistema máximo: no verificado.
- Quitar animaciones: no verificado.
- Modos claro y oscuro: la pantalla conserva el fondo oscuro existente; modo claro no verificado.
- Lector de pantalla en la tarea principal: no verificado.
- Unidad conectada por ADB (`rk3566_rgo` / `adt3`, Android 11): APK instalado, pantalla abierta y captura revisada a 1280×720; el modelo HY300 Pro no se pudo confirmar por las propiedades del sistema.
- D-pad físico, lectura a 3 metros, rendimiento sostenido y lector de pantalla: no verificados. Se inyectó D-pad RIGHT con ADB y el foco cambió a la app siguiente.
- Build debug: pasó con Android SDK en `/home/jostx/Android/Sdk`; `/opt/android-sdk` era una ruta incompleta en el entorno.

## Plataforma

- Objetivos: acciones superiores de 48 dp (72 px a 240 dpi) y tarjetas superiores a 64 dp; medidas revisadas en código.
- Navegación D-pad: se comprobó en el dispositivo que UP alcanza la barra y RIGHT mueve el foco a la siguiente acción. Back en overlays no verificado.
- Área segura: padding derivado del 5% de ancho y alto disponibles; recorte real no verificado.
- Ventanas amplias y orientación: no verificado.
- TV: foco de tarjetas, acciones y estado vacío visible en código; lectura a 3 metros y control remoto real no verificados.

## Calidad

- Pregunta principal: apps favoritas; el estado vacío enlaza al catálogo.
- Estados de pantalla: default y vacío definidos; carga, error, offline y stale no aplican a esta pantalla local.
- Colores: roles reutilizados de `LauncherPalette`; no hay hex nuevos en los componentes.
- Movimiento reducido: no verificado en dispositivo.
- Contenido: navegación superior solo con iconos; cada acción conserva nombre accesible para tecnologías de asistencia.

## Reporte

`ux-acceptance.csv` contiene filas observables; su validador pasó. Build, instalación y recorrido D-pad por ADB pasaron en la unidad conectada. No se cambió la app de inicio predeterminada. La identidad HY300 Pro no está confirmada.
