# Cómo llega HDMI a la proyección en el HY300 Pro

## Resumen

El launcher no captura HDMI ni decodifica el vídeo. El usuario confirmó que su HY300 Pro funciona con una fuente conectada a HDMI 1 y que no tiene otras entradas utilizables. La lista del selector OEM contiene más opciones, pero no representa los conectores disponibles en esta unidad. Para HDMI 1, el firmware publica la entrada como Android TV (TIF); `AwLiveTv` la pide a `TvView`, y `VideoInputService` crea una sesión, enruta una `Surface` al hardware de TV y activa por separado el audio de entrada. El firmware y el HAL hacen el trabajo específico del SoC.

```mermaid
sequenceDiagram
    actor Usuario
    participant Selector as Launcher
    participant Live as AwLiveTv / TvView
    participant TIF as Android TV Input Manager
    participant VIS as VideoInputService
    participant FW as HAL / TV server SoftWinner
    participant HW as Receptor HDMI y ruta de vídeo/audio
    participant Display as Salida de vídeo Android → proyector

    Usuario->>Selector: Pulsa HDMI 1 en el launcher
    Selector->>TIF: getTvInputList(); valida ID HW1
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

## 1. Selección: el launcher abre HDMI 1 directamente

El launcher abre directamente `com.softwinner.awlivetv.MainActivity` con `input_source="HDMI1"`, después de comprobar que `TvInputManager.getTvInputList()` contenga el ID `com.softwinner.vis/.HdmiInputService/HW1`. Si falta el reproductor o el input, muestra un mensaje y no intenta abrir un puerto distinto. Esta acción evita el selector OEM.

El selector `AwSource` sigue definiendo cinco opciones visuales (`HDMI1`, `HDMI2`, `HDMI3`, `CVBS1`, `CVBS2`) y valida sus elecciones con coincidencia parcial sobre la etiqueta o el ID del TV input. Su código pasa el nombre de la opción, por ejemplo `HDMI1`, como `input_source`. Esas opciones son etiquetas codificadas en el APK, no evidencia de que todos esos conectores estén disponibles. No lee el puerto con código propio ni copia bytes de vídeo.

La lista del selector OEM está fija en el APK; eso no significa que haya cinco conectores físicos. La comprobación de `TvInputManager` evita abrir un input no registrado.

## 2. `AwLiveTv`: UI y solicitud de sintonización

Al recibir `input_source`, `AwLiveTv` inicializa su `SourceManager`, resuelve el nombre contra las fuentes del sistema (`com.softwinner.inputsource.InputSourceManager`, de la biblioteca `com.softwinner.tv.jar`) y configura `TvView` con `tune(inputId, channelUri)`. Para HDMI 1, el selector y el input ID corresponden a `com.softwinner.vis/.HdmiInputService/HW1`; la app OEM sintoniza una URI passthrough (`TvContract.buildChannelUriForPassthrough(inputId)`), no `content://android.media.tv/channel/2`. El canal TV normal con ID 2 también aparece en el proveedor como registro sintético, pero no es la URI usada por la ruta passthrough. La URI no es una URL de reproducción ni un archivo de vídeo. El código guarda la fuente seleccionada y presenta banner/estado de señal.

`AwLiveTv` también consulta `persist.sys.hdmi_source`; ciertos valores de arranque (`1`–`5`) pueden elegir HDMI1–3 o CVBS1–2 y luego se limpian. Por tanto, en ese caso el valor de firmware puede prevalecer sobre el extra enviado por el selector. La compatibilidad con esos valores no implica que todos esos puertos existan en el HY300 Pro.

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

- **Selector:** sí, el launcher podría abrir HDMI 1 directamente o mostrar una opción única, siempre comprobando que el TIF registre el input. En esta unidad no tiene sentido ofrecer HDMI 2/3 ni CVBS como entradas disponibles; la etiqueta múltiple del APK OEM no demuestra que existan.
- **`AwLiveTv`:** se puede reemplazar como interfaz. La nueva app debe usar el TIF; no debería implementar captura de HDMI.
- **`VideoInputService`:** no es una simple pantalla. Para reemplazarlo hay que reproducir su registro de hardware, sesiones, `TvInputHardware`, señales, audio privado, puente nativo y compatibilidad con el HAL del HY300 Pro. No hay evidencia de una API pública sencilla que permita omitirlo.
- **HAL/TV server y hardware:** quedan fuera del launcher y son imprescindibles para la captura física. No están incluidos en estos APK y no se deben sustituir sin fuente/código del firmware, esquema de la placa y recuperación probada.

## Evidencia y límites de esta revisión

La reconstrucción estática se basa en los APK extraídos del HY300 Pro guardados en `artifacts/projector-apks/`, su manifiesto, componentes, clases y llamadas decodificadas. Además, el usuario comprobó que una fuente HDMI funciona por HDMI 1 y reportó que no dispone de otras entradas utilizables. En una comprobación ADB posterior, el TIF registró `HW1` conectado y `HW2`/`HW3` desconectados. Una primera prueba de `TvView` embebido usó por error `content://android.media.tv/channel/2` en vez de la URI passthrough que emplea `AwLiveTv`; durante esa prueba el proceso OEM `com.softwinner.awtvinputservice` terminó con `SIGSEGV` en `libhdmi_jni.so`, dentro de `__HVIS_CaptureThread`. Luego se usó `content://android.media.tv/passthrough/` seguido del ID escapado (`Uri.encode(inputId)`): el TIF creó una sesión para el UID del launcher y no se observó otro crash. La tarjeta actual aparece solo al enfocar o pasar el puntero por el botón HDMI 1 y libera la sesión al dejarlo. La captura de pantalla ADB no muestra la superficie de vídeo, así que falta confirmar visualmente la imagen en el proyector. El botón abre `AwLiveTv` a pantalla completa sin pasar por `AwSource`. Tampoco se inspeccionó el contenido de `com.softwinner.tv.jar`, las bibliotecas nativas o la implementación del HAL.
