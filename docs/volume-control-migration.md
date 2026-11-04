# Migración de AwTvVolume

## Objetivo

`volume-control` es una aplicación Kotlin independiente que prepara un reemplazo para `artifacts/projector-apks/AwTvVolume.apk`. No añade controles de volumen al launcher.

El APK original usa el paquete `com.softwinner.tvvolume` y expone `MainActivity` y `VolumeService`. El módulo conserva el paquete, los nombres de esos componentes y la acción del servicio para facilitar una sustitución compatible con el firmware HY300 Pro.

## Qué reemplaza y qué conserva

El módulo reemplaza el código de la interfaz y del servicio de volumen de AwTvVolume. Incluye un panel de volumen propio y el servicio que procesa el extra `keyevent` y muestra el indicador en pantalla.

El control de audio sigue dependiendo del firmware: consulta `AudioManagerEx` y envía `speaker_volume`, `spk_mute` y `set_volume` mediante la API de audio de SoftWinner. No reemplaza `AudioManager`, `AudioFlinger`, el servicio de audio del sistema ni el HAL del fabricante. El APK requiere la biblioteca `com.softwinner.tv` que proporciona el firmware.

## Firma y despliegue

El manifiesto conserva `android.uid.system` y el script firma con el certificado de plataforma de prueba público de AOSP usado por el firmware verificado. Eso permite comprobar compatibilidad de firma con esa imagen concreta; no es una identidad privada ni una cadena de confianza apropiada para publicar una aplicación. El script descarga la clave a un directorio temporal con permisos restringidos y no la guarda en el repositorio.

La compilación local por sí sola no confirma que el firmware reenvíe las teclas al nuevo servicio o permita mostrar la ventana de tipo `2020`. La instalación de prueba se aplica como actualización de la app del sistema; el APK original permanece en `/system/priv-app/AwTvVolume/AwTvVolume.apk` para poder restaurarlo quitando la actualización del paquete.

## Prueba inicial en HY300 Pro

El 4 de noviembre de 2026 se instaló el candidato (`versionCode` 31) en el HY300 Pro conectado por ADB. Android lo registró como `UPDATED_SYSTEM_APP`, manteniendo el APK de fábrica disponible.

- `MainActivity` abrió correctamente y leyó `speaker_volume` y `spk_mute`.
- Los controles D-pad de la app cambiaron el nivel, y el botón alternó silencio; la prueba terminó en 20% y con sonido activado.
- Al inyectar `VOLUME_UP` desde ADB apareció el panel estándar de Android. No se confirmó que ese evento llegue a `VolumeService` ni se verificó el overlay propio con el control remoto físico.
- No aparecieron excepciones de la app en el logcat durante estas pruebas.

Por tanto, la pantalla y las llamadas al backend de audio están comprobadas en el equipo, pero la integración de teclas globales y el overlay del servicio siguen pendientes de validación.

## Compilar y firmar

```bash
./gradlew :volume-control:assembleRelease
python3 tools/sign-volume-control.py
```

El candidato firmado queda en `volume-control/build/outputs/apk/release/volume-control-hy300pro.apk`, dentro del directorio ignorado de compilación.
