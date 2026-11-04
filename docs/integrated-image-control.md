# Control de imagen integrado — HY300 Pro

## Estado (3 de noviembre de 2026)

El launcher controla la orientación directamente mediante el HAL del firmware. Zoom y cuatro esquinas usan un servicio propio, `io.jostx.hy300launcher.imagecontrol`, instalado como un segundo APK sin icono de inicio. Los ajustes de imagen ya no abren conexiones ADB ni ejecutan comandos de shell o clases del APK OEM. No se invoca Magisk ni se modifican las particiones del sistema.

El apagado también pasa por el servicio firmado: este comprueba `android.permission.SHUTDOWN` y abre la confirmación del sistema. Android 11 protege `ShutdownActivity` con ese permiso y lo concede por firma de plataforma. La pantalla pide confirmar antes de apagar. En el HY300 Pro se comprobó `SHUTDOWN: granted=true`, se abrió el diálogo y se canceló de forma segura. La implementación y su prueba actual dependen de este permiso y componente OEM de Android 11; véase el [manifiesto AOSP de Android 11](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android11-release/core/res/AndroidManifest.xml) y [su actividad de apagado](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android11-release/core/java/com/android/internal/app/ShutdownActivity.java). La corrección automática mantiene las señales y broadcasts del firmware; su efecto óptico no se volvió a verificar en esta integración. Trapecio manual sigue figurando como no disponible porque el HY300 Pro examinado no expone su nodo.

## Instalación y firma

Son dos APK: se conserva la firma del launcher para actualizarlo sin borrar datos. El servicio pide `android.permission.HARDWARE_TEST` y `android.permission.SHUTDOWN` para la confirmación de apagado. En este firmware, `HARDWARE_TEST` autoriza la transacción 1050 de SurfaceFlinger y `SHUTDOWN` autoriza abrir la pantalla del sistema. El certificado de plataforma coincide con el certificado público de pruebas AOSP documentado en [el análisis del permiso](surfaceflinger-permission-analysis.md).

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
./gradlew :app:assembleDebug :image-control:assembleRelease
python3 tools/sign-image-control.py
adb -s 192.168.100.12:5555 install -r image-control/build/outputs/apk/release/image-control-hy300pro.apk
adb -s 192.168.100.12:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

El script descarga el material público de firma de la etiqueta Android 11 de AOSP, comprueba la huella del certificado y elimina las claves temporales. Los APK quedan en carpetas ignoradas. Este procedimiento sirve para el firmware verificado: no convierte una clave pública de pruebas en una identidad privada de producción ni garantiza compatibilidad con otros firmwares.

El servicio autoriza exclusivamente al paquete `io.jostx.hy300launcher` con un único UID no compartido y la firma SHA-256 configurada en su compilación. El valor predeterminado corresponde al launcher instalado en este proyector. Para otro certificado del launcher, recompilar el servicio pasando `-PlauncherCertificateSha256=<huella_sha256_en_minúsculas>`; no cambiar la firma del launcher instalado sin preparar una migración de datos. El cliente comprueba la firma de plataforma esperada del servicio antes de conectarse. La confianza en esa firma tiene el límite de que la clave de plataforma de este firmware es pública.

ADB se usa aquí para instalar y recoger evidencia; no forma parte de las llamadas de imagen. Si falta el servicio, zoom y esquinas quedan inhabilitados con un aviso, mientras la orientación se puede consultar directamente.

## Contrato y recuperación

- Binder explícito mediante AIDL compartido, sin dependencias nuevas.
- Operaciones de control: comprobar disponibilidad, aplicar una matriz de ocho enteros con zoom opcional y abrir la confirmación de apagado. No acepta comandos ni nombres de propiedades.
- Coordenadas interiores entre 0 y 500; orden inferior izquierda, superior izquierda, superior derecha, inferior derecha; cada coordenada se divide por 1000 al enviarla a SurfaceFlinger. Zoom entre 0 y 20, o −1 para conservarlo.
- Se valida el UID, paquete y certificado en cada llamada, antes de limpiar la identidad Binder y tocar el firmware.
- Escrituras serializadas. Antes de modificar se guarda un registro atómico con los valores originales y la matriz de recuperación. Ante un error se intenta restaurar; si el proceso termina durante la operación, la siguiente llamada autenticada recupera el registro pendiente antes de aceptar otra escritura.
- El cliente limita la espera de conexión a cuatro segundos, libera cada enlace y no reenvía automáticamente escrituras cuya respuesta se perdió. Ese límite no es un timeout de la transacción Binder del firmware.
- Los errores se muestran como cambios no aplicados o no confirmados. Una pérdida de conexión no permite asegurar que el hardware no recibió la operación.

La retícula conserva el patrón fijo 16:9 de 9 × 5 celdas, de proporción 80:81. Solo el firmware transforma la proyección. Se conservan los movimientos agrupados del D-pad, Aplicar y la restauración de la sesión mediante Cancelar o Atrás.

## Verificación realizada en el proyector

Compilación: `:app:assembleDebug` y `:image-control:assembleRelease` completadas, incluida la comprobación vital de lint del servicio. Instalación: ambos APK instalados como actualización/servicio adicional, sin borrar datos ni cambiar el inicio predeterminado.

El diagnóstico privado de debug `ImageBackendDiagnosticActivity` ejecutó el mismo repositorio que utiliza la interfaz, con UID del launcher **10074**. El servicio obtuvo UID **10066**, sin UID de sistema, y `HARDWARE_TEST: granted=true`, `SHUTDOWN: granted=true`.

Resultado de la prueba reversible:

```text
geometry=true; zoom=true; detail=null
orientation=FRONT
invalidGeometry=rejected
corner=applied
corner=restored
zoom=applied
zoom=restored
orientation=applied
all=restored
PASS_RESTORED
```

Comprobaciones adicionales:

- Sin el servicio instalado: capacidades de geometría y zoom desactivadas y mensaje «Falta instalar el servicio Control de imagen HY300 Pro».
- Desde la interfaz y con eventos del mando: superior izquierda X 100 → 105; Atrás restauró X 100. Se repitió usando el botón Cancelar con el mismo resultado.
- Después de detener el servicio y arrancar nuevamente el launcher: conexión y lectura correctas; coordenadas inválidas rechazadas.
- Desde el botón **Apagar**, el servicio abrió la pantalla real del sistema: «Tu dispositivo se apagará», con **CANCELAR** y **ACEPTAR**. Se eligió **CANCELAR**; el proyector siguió encendido y `sys.powerctl` quedó vacío.
- Comparación externa de las 15 propiedades de geometría, orientación y corrección automática: idénticas al inicio después de los diagnósticos y de la prueba de interfaz. Sin registro de recuperación pendiente.

Límites: el diálogo de apagado se comprobó y se canceló en el HY300 Pro; no se confirmó un apagado real. La calibración óptica de la proporción sobre la pared sigue pendiente. La recuperación por muerte durante una escritura y el rechazo de una app con otro certificado están implementados e inspeccionados, pero no se provocaron en esta sesión. El diagnóstico de debug no forma parte de la versión release ni tiene componentes exportados.
