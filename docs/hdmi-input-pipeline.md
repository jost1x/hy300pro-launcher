# Cómo llega HDMI a la proyección en el HY300 Pro

## Resumen

El launcher no captura HDMI ni decodifica el vídeo. Solo abre el selector de entradas. La imagen entra por el hardware HDMI del proyector y el firmware la publica como una entrada de Android TV (TIF). `AwLiveTv` pide esa entrada a `TvView`; `VideoInputService` crea una sesión, enruta una `Surface` al hardware de TV y activa por separado el audio de entrada. El firmware y el HAL hacen el trabajo específico del SoC.

```mermaid
sequenceDiagram
    actor Usuario
    participant Selector as AwSource
    participant Live as AwLiveTv / TvView
    participant TIF as Android TV Input Manager
    participant VIS as VideoInputService
    participant FW as HAL / TV server SoftWinner
    participant HW as Receptor HDMI y ruta de vídeo/audio
    participant Display as Salida de vídeo Android → proyector

    Usuario->>Selector: Elige HDMI1 (o CVBS)
    Selector->>TIF: getTvInputList(); valida etiqueta/ID
    Selector->>Live: Abre MainActivity(input_source="HDMI1")
    Live->>Live: Busca el origen por nombre o ID
    Live->>TIF: TvView.tune(inputId, channelUri)
    TIF->>VIS: Vincula el TV Input y crea una sesión
    VIS->>FW: Consulta estado de puerto/señal
    TIF->>VIS: Entrega Surface y configuración de stream
    VIS->>HW: TvInputHardware.setSurface(...)
    VIS->>FW: nativeSetVideoSurface(Surface)
    HW->>Display: Vídeo de entrada en la superficie/ruta TV
    VIS->>FW: Activa ruta de audio HDMI y libera mute al detectar señal
    FW->>HW: Audio de entrada hacia la ruta de audio del proyector
```

El diagrama representa el flujo deducido de los APK. La ruta nativa y la implementación interna del HAL son propietarias; no se puede concluir del APK si el `Surface` se presenta mediante overlay/sideband, una ruta duplicada o una operación específica de este SoC.

## 1. Selección: el launcher no cambia la ruta de vídeo

`AwSource` define cinco opciones visuales: `HDMI1`, `HDMI2`, `HDMI3`, `CVBS1` y `CVBS2`. Al seleccionar una, consulta `TvInputManager.getTvInputList()` y busca coincidencia parcial, sin distinguir mayúsculas, entre el nombre visible (`TvInputInfo.loadLabel()`) o el ID del input. Si encuentra una entrada compatible, abre explícitamente `com.softwinner.awlivetv.MainActivity` con el extra `input_source` que contiene el nombre elegido. No lee el puerto con código propio ni copia bytes de vídeo.

La lista del selector está fija en el APK; eso no significa que haya cinco conectores físicos. La comprobación de `TvInputManager` evita que el selector use un origen que el servicio no haya registrado. El número de puertos realmente disponibles debe comprobarse en el equipo con la lista de TV inputs.

## 2. `AwLiveTv`: UI y solicitud de sintonización

Al recibir `input_source`, `AwLiveTv` inicializa su `SourceManager`, resuelve el nombre contra las fuentes del sistema (`com.softwinner.inputsource.InputSourceManager`, de la biblioteca `com.softwinner.tv.jar`) y configura `TvView` con `tune(inputId, channelUri)`. Para HDMI/CVBS, el input ID identifica el puerto y el URI identifica el canal/input para el TIF; no es una URL de reproducción ni un archivo de vídeo. El código guarda la fuente seleccionada y presenta banner/estado de señal.

`AwLiveTv` también consulta `persist.sys.hdmi_source`; ciertos valores de arranque (`1`–`5`) pueden elegir HDMI1–3 o CVBS1–2 y luego se limpian. Por tanto, en ese caso el valor de firmware puede prevalecer sobre el extra enviado por el selector.

`TvView` es una vista de alto nivel: solicita el input al framework TV, que enlaza el servicio y gestiona la sesión y recursos. Esa separación es propia del Android TV Input Framework (TIF): la app de TV se comunica con el input mediante el manager del sistema, mientras que el servicio del fabricante integra el hardware. [AOSP: arquitectura TIF](https://source.android.com/docs/devices/tv) · [API `TvView`](https://developer.android.com/reference/android/media/tv/TvView)

## 3. `VideoInputService`: descripción del hardware y sesión

El APK `com.softwinner.vis` declara `HdmiInputService` y `CvbsInputService` como servicios `android.media.tv.TvInputService`, protegidos por `android.permission.BIND_TV_INPUT`. El APK se instala como servicio privilegiado, usa las bibliotecas SoftWinner `com.softwinner.tv` y requiere permisos de hardware TV/HDMI.

Cuando el framework informa un `TvInputHardwareInfo` de tipo HDMI (tipo 9), `HdmiInputService.onHardwareAdded()` crea un `TvInputInfo`, lo etiqueta `HDMI<deviceId>` y guarda las relaciones entre ID de Android, ID de hardware y `hdmiPortId`. También publica un registro de canal sintético con resolución declarada 1920×1080 y audio de dos canales. Ese registro ayuda a TV Provider/TV UI a representar la entrada; no significa que el receptor convierta todas las señales a 1080p ni limita por sí solo los formatos que acepta el hardware.

Al seleccionar el input, el framework llama `onCreateSession()`. La sesión obtiene la `Surface` asociada a `TvView` y una `TvStreamConfig`. En `onSetSurface(surface)`, el servicio realiza dos llamadas relevantes:

1. `mHardware.setSurface(surface, mTvStreamConfig[0])`, mediante `TvInputHardware` de Android.
2. `HdmiInputSetupNative.nativeSetVideoSurface(surface)`, un puente nativo del fabricante.

Al recibir `null` o liberar la sesión, desconecta la superficie y libera la ruta de audio. Android define la `Surface` como el destino al que se renderiza el input; en una sesión de hardware, el framework puede reenviar la superficie de la app al input físico. [API `TvInputService.Session.onSetSurface`](https://developer.android.com/reference/android/media/tv/TvInputService.Session#onSetSurface(android.view.Surface)) · [API `TvInputService.HardwareSession`](https://developer.android.com/reference/android/media/tv/TvInputService.HardwareSession)

La existencia de las dos llamadas (`TvInputHardware` y puente nativo) deja claro que hay integración de firmware adicional, pero el APK no contiene la implementación nativa. Por eso no se puede precisar si el segundo paso configura el receptor, un overlay, una ruta sideband o sincroniza el motor de vídeo con SurfaceFlinger. La superficie termina en la ruta de salida Android que alimenta el proyector; el detalle de composición, escalado, formatos, HDCP y planos de hardware queda dentro del HAL/firmware.

## 4. Detección de señal y hotplug

El servicio HDMI consulta el HIDL `vendor.aw.homlet.tvsystem.tvserver@1.0::ITvServer` y el método `SubDeviceHDMIGetPortStatus()`. Interpreta los bits del estado por ID de puerto. También registra receptores para `android.intent.action.TIF_HOTPLUG_CHANGE` y `android.intent.action.TIF_SIGNAL_CHANGE`. Cuando el estado cambia, la sesión llama `notifyVideoAvailable()` o `notifyVideoUnavailable()` y muestra/oculta su overlay “sin señal”. En el evento de señal HDMI llama además `nativeHDMISignalChange(deviceId, signalId)`.

La ruta CVBS sigue el mismo patrón general, con dos diferencias: consulta `SubDeviceCvbsPlugStatus()` usando un ID SVP traducido desde el ID Android y notifica cambios mediante `nativeCVBSSignalChange()`. También publica `CvbsInputService` como input TV propio.

La detección de cable conectado (hotplug) y la detección de señal válida son estados distintos. Puede existir un input enumerado aunque no haya señal; en ese caso `AwLiveTv`/el servicio muestra “sin señal”.

## 5. Audio y control remoto HDMI-CEC

El vídeo viaja por la superficie TV; el audio se maneja aparte. En la sesión HDMI, el servicio usa parámetros privados de `AudioManager`:

- al preparar entrada: `audio_mute=true` y `hdmi_input=on`;
- al recibir señal válida: `audio_mute=false`;
- al liberar: `audio_mute=false` y `hdmi_input=off`.

CVBS usa `cvbs_input=on/off` de forma parecida. Esto selecciona la ruta del audio dentro del firmware; no es mezcla de audio hecha por `AwLiveTv`. La sesión recibe `onSetStreamVolume()` pero el código inspeccionado solo guarda el valor, así que la implementación efectiva del nivel relativo depende de la ruta privada/HAL.

La sesión HDMI también reenvía `onKeyDown` y `onKeyUp` a `HdmiTvClient.sendKeyEvent()`, permitiendo que teclas del control remoto lleguen al equipo HDMI mediante CEC cuando esa ruta y el dispositivo externo lo soportan. Eso es control, no transporte de imagen.

## Qué se puede reemplazar

- **Selector:** sí, el launcher podría ofrecer su propia UI y llamar `TvInputManager`/`TvView`, si se conserva la selección del ID correcto y un TV input registrado.
- **`AwLiveTv`:** se puede reemplazar como interfaz. La nueva app debe usar el TIF; no debería implementar captura de HDMI.
- **`VideoInputService`:** no es una simple pantalla. Para reemplazarlo hay que reproducir su registro de hardware, sesiones, `TvInputHardware`, señales, audio privado, puente nativo y compatibilidad con el HAL del HY300 Pro. No hay evidencia de una API pública sencilla que permita omitirlo.
- **HAL/TV server y hardware:** quedan fuera del launcher y son imprescindibles para la captura física. No están incluidos en estos APK y no se deben sustituir sin fuente/código del firmware, esquema de la placa y recuperación probada.

## Evidencia y límites de esta revisión

La reconstrucción estática se basa en los APK extraídos del HY300 Pro guardados en `artifacts/projector-apks/`, su manifiesto, componentes, clases y llamadas decodificadas. La sesión ADB actual no tiene el proyector conectado, así que no se verificó en vivo el inventario de puertos, el estado de señal ni la imagen/audio con una fuente HDMI activa. Tampoco se inspeccionó el contenido de `com.softwinner.tv.jar`, las bibliotecas nativas del firmware o la implementación del HAL. Por tanto, el flujo de alto nivel está respaldado por el código Android observado; la ruta interna final del chip está descrita solo hasta el límite que exponen esas APIs.
