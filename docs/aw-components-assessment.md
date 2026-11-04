# Evaluación de componentes Aw del HY300 Pro

Revisión estática de los APK extraídos y consulta de paquetes y servicios en el HY300 Pro conectado (`192.168.100.12:5555`). Los ejecutables copiados, sus rutas y hashes están en [inventario de APKs](../artifacts/projector-apks/AW-APKS.md) y [servicios extraídos](../artifacts/projector-apks/services/README.md). La revisión describe propósito y dependencias visibles; no es una auditoría completa de seguridad ni demuestra que un componente sea seguro o malicioso.

Para entender el recorrido de HDMI/CVBS desde el selector hasta la salida de vídeo, véase [Flujo de entradas HDMI](hdmi-input-pipeline.md).

## APKs

| Componente | Función observada | Evaluación para el launcher |
|---|---|---|
| `AwTvVolume.apk` (`com.softwinner.tvvolume`) | Actividad `MainActivity` y `VolumeService`; implementa la interfaz OEM que acompaña al control de volumen. Usa APIs de audio SoftWinner. | **No seguir adaptando por ahora.** Las teclas de volumen abren el panel de SystemUI y su posición vertical se resolvió en SystemUI; no se confirmó que el firmware reenvíe esas teclas a `VolumeService`. La app de volumen propia no es necesaria para el launcher. La copia OEM sigue en `/system`; la actualización de prueba está instalada en `/data/app`. Si se decide retirarla, se puede desinstalar la actualización y volver al APK de sistema; eso requiere una acción explícita en el proyector. |
| `AwTvProvision.apk` (`com.softwinner.provision`) | Una actividad `DefaultActivity`; su código fija el launcher por defecto y actualiza `persist.sys.LauncherPackage`/`device_provisioned`. Es una tarea de provisión inicial. | **No reemplazar como servicio residente.** El launcher propio ya se puede seleccionar como Inicio. Solo tendría sentido una herramienta de provisión propia si se automatiza la configuración inicial o el restablecimiento de fábrica. |
| `AwManager.apk` (`com.softwinner.awmanager`) | Panel para configurar el límite de procesos en segundo plano y listas blancas del gestor Awbms. Su `BackgroundManager` llama al Binder `background` (`android.aw.IBackgroundManager`), alojado en `system_server`; no es un daemon independiente. | **No reemplazar ahora. Auditar primero.** Cambia política global de procesos y el paquete corre con UID 1000. Solicita, entre otros, `INTERNET`, `RECEIVE_BOOT_COMPLETED`, `TRANSMIT_IR` y `CAPTURE_AUDIO_OUTPUT`. La concesión de esos permisos no demuestra uso indebido, pero hace que sea el APK prioritario para un análisis de código y tráfico antes de confiar en él o sustituirlo. El backend Awbms pertenece al sistema; copiar solo la interfaz no lo reemplaza. |
| `AwlogSettings.apk` (`com.softwinner.awlogsettings`) | Herramienta de diagnóstico con secciones para logcat, kernel, crash dump y logs ATF/SCP. | **No adaptar al launcher.** Es mantenimiento técnico, no una función cotidiana del proyector. Mantener acceso solo si se necesita para diagnóstico; no incluirlo en la interfaz principal. |
| `AwSource.apk` (`com.softwinner.awsource`) y `Entradas-HDMI.apk` | Son exactamente el mismo APK (coincide SHA-256): selector de HDMI1–3 y CVBS1–2. `Entradas-HDMI.apk` es una copia adicional con otro nombre local, no una segunda app. Inicia `AwLiveTv` con la fuente elegida. | **No duplicar mientras las entradas funcionen.** Si se busca una interfaz propia, se puede adaptar el selector; debe conservar los IDs de entrada y el enlace con el framework TV. |
| `AwLiveTv.apk` (`com.softwinner.awlivetv`) | Cliente de TV en vivo: muestra la entrada, señal, banner e información de canales/programas; usa `TvView`, base local de canales y el proveedor TV. | **Mantener si se usan HDMI/AV.** Una sustitución de la interfaz es posible, pero no reemplaza el acceso a la fuente física ni al proveedor de canales. Si no se usan entradas, no hay función del launcher que obligue a migrarla. |
| `VideoInputService.apk` (`com.softwinner.vis`) | Registra `HdmiInputService` y `CvbsInputService` como servicios `TvInputService`, con permiso `BIND_TV_INPUT`; usa APIs y biblioteca TV de SoftWinner y permisos de hardware HDMI/TV. | **No reemplazar desde el launcher.** Es el adaptador entre el framework Android TV y el hardware de entrada. Sustituirlo requeriría un backend TV compatible con el HAL y con las sesiones de HDMI/CVBS. Es un componente crítico si se usan esas entradas. |
| `AwIconShapeOverlay.apk`, `AwAccentColorOverlay.apk` | Overlays de recursos instalados en `/product/overlay`; aplican forma de iconos y color/acento. No contienen actividad, servicio ni código DEX. | **No requieren adaptación.** Solo considerar una alternativa si se personaliza el aspecto global de Android; no son servicios ni una ruta de control del proyector. |
| `Ajustes-del-proyector.apk` (`com.htc.htcsettings`) | Aplicación OEM de ajustes del proyector; no lleva `Aw` en el nombre, pero sus pantallas llaman APIs de SoftWinner y propiedades del firmware. | **No eliminar.** El launcher ya sustituyó algunas pantallas de imagen con control propio. Mantener la app como referencia y respaldo para las opciones aún no migradas. |

## Daemons HAL extraídos de `/vendor/bin/hw`

Los cinco ejecutables son ELF de 32 bits ARM y dependen de bibliotecas del firmware. La función se identifica por el HAL declarado y sus bibliotecas enlazadas; no se dispone del código fuente ni de una auditoría de implementación.

| Daemon | Función del HAL | Evaluación |
|---|---|---|
| `android.hardware.keymaster@4.1-service-aw` | Operaciones criptográficas y gestión de claves de Android Keystore mediante el HAL Keymaster; enlaza bibliotecas `keymaster4_*_aw`. | **Mantener.** Base de seguridad del sistema; no es una función del launcher. Reemplazarlo requiere compatibilidad criptográfica y con el proveedor de hardware. |
| `android.hardware.gatekeeper@1.0-service-aw` | Verificación de credenciales/estado de bloqueo de usuario mediante Gatekeeper y la biblioteca `libgatekeeper`. | **Mantener.** HAL de seguridad del sistema. No sustituir por una app. |
| `android.hardware.power-service-aw` | HAL Power de Android; comunica solicitudes de energía/rendimiento del framework. | **Mantener.** Parte de la política de energía de la plataforma. |
| `android.hardware.thermal@2.0-service.aw` | HAL Thermal; informa temperaturas y estados térmicos al framework. | **Mantener.** Sustituirlo podría ocultar sobretemperatura o alterar límites térmicos; no migrar al launcher. |
| `android.hardware.usb@1.0-service.aw` | HAL USB; expone estado y configuración USB al framework. | **Mantener.** Requerido para las funciones USB del sistema y periféricos. |

## Servicios Binder SoftWinner adicionales

Estos nombres aparecen registrados en `service list`, pero no hay un APK independiente ni un binario con el mismo nombre en el inventario. La descripción funcional siguiente se limita a los nombres de interfaz registrados; no se inspeccionó la implementación que vive en el firmware.

| Servicio Binder | Lectura prudente del propósito | Evaluación |
|---|---|---|
| `background` — `android.aw.IBackgroundManager` | Política Awbms de procesos en segundo plano. `dumpsys background` muestra límites, comprobaciones de memoria y listas blancas. | **No reemplazar en una app.** Es política de sistema; el launcher no debe modificarla. Priorizar auditoría del APK `AwManager` por su UID de sistema y permisos. |
| `softwinner.gpio` — `com.softwinner.IGpioService` | Acceso a pines GPIO. La interfaz por sí sola no identifica qué líneas controla esta unidad. | **No adaptar.** No hay una función de launcher que lo necesite; cambiar GPIO sin mapa de placa podría afectar hardware. |
| `softwinner.isomountmanager` — `com.softwinner.IISOMountManagerService` | Montaje/gestión de imágenes ISO, inferido del nombre de interfaz. | **No adaptar** salvo que se desarrolle explícitamente una función de montaje multimedia. |
| `softwinner.multi_ir` — `com.softwinner.IMultiirService` | Funciones de infrarrojo multimodo, probablemente asociadas al receptor/transmisor IR; el detalle de comandos de esta unidad no está documentado. | **No adaptar.** La navegación actual usa los eventos remotos de Android. Investigar solo si se necesita aprender, reenviar o transmitir IR desde una app. |
| `softwinner.systemmix` — `com.softwinner.ISystemMixService` | Servicio de mezcla/ruteo del sistema según su nombre; no se verificaron métodos ni consumidores. | **Propósito exacto pendiente.** No tocar hasta identificar llamadas y efecto en el firmware; el launcher no lo necesita para mostrar un volumen vertical. |

## Decisión y prioridad

1. **Volumen:** detener la migración de `AwTvVolume` y no seguir desarrollando su reemplazo por ahora. La solución del panel vertical vive en SystemUI. La actualización propia puede retirarse más adelante para volver al APK OEM; no se ha hecho en esta revisión.
2. **Seguridad:** si la preocupación principal es malware, auditar primero `AwManager`: revisar manifiesto completo, llamadas Binder, destinos de red y comportamiento durante arranque. `INTERNET` más UID de sistema es una superficie sensible, no prueba de exfiltración. Un análisis estático parcial tampoco demuestra que el APK sea confiable.
3. **Entradas:** mantener `VideoInputService` y `AwLiveTv` si se usa HDMI/AV. Personalizar el selector (`AwSource`) es una adaptación de UI de menor alcance que reimplementar el servicio de entrada.
4. **Sin migración:** provisión inicial, panel de logs, overlays y HALs no aportan una función que el launcher deba poseer. Los HALs de seguridad, energía, temperatura y USB deben permanecer como componentes del firmware.

## Funciones propias recomendadas para el launcher

La meta no es sustituir cualquier componente cuyo nombre empiece por `Aw`, sino retirar las pantallas de uso diario cuando el launcher pueda ofrecer una experiencia mejor y comprobable.

| Prioridad | Función | Integración propuesta | Qué sigue dependiendo del firmware |
|---|---|---|---|
| 1 | Seleccionar HDMI/CVBS desde el launcher | Mostrar las entradas que `TvInputManager` tenga registradas y, al elegir una, abrir directamente `com.softwinner.awlivetv.MainActivity` con `input_source`. Así se elimina el selector `AwSource` sin reconstruir el backend de vídeo. | `VideoInputService` mantiene la sesión física y `AwLiveTv` muestra la imagen en pantalla completa. La lista efectiva se debe verificar en el HY300 Pro con una fuente conectada. |
| 2 | Recordar y reabrir la última entrada | Guardar la preferencia en el launcher y volver a ofrecerla si el framework todavía registra ese input. | Presencia real del puerto y detección de señal. La preferencia guardada no debe presentarse como señal disponible. |
| 3 | Mostrar estado de entrada | Indicar estados solo cuando los entregue el framework/servicio, por ejemplo entrada registrada o vídeo disponible/no disponible. | El estado de señal lo originan el HAL y `VideoInputService`; no se debe inferir solo por el nombre HDMI. |
| 4 | Preview dentro de Inicio | Más adelante, integrar `TvView` en una tarjeta del launcher en vez de abrir `AwLiveTv`. Probar sesión, permisos, tamaño/orden de superficies y audio antes de dejarla activa permanentemente. | Se conserva `VideoInputService`; el launcher no captura ni decodifica HDMI. El servicio OEM puede activar audio al detectar señal aunque su callback de volumen solo guarde el nivel. |

Los ajustes de imagen, orientación y apagado ya tienen superficies nativas en el proyecto y deben seguir mostrando el estado real del firmware. El volumen debe quedarse en SystemUI; el launcher no necesita duplicar `AwTvVolume`. No conviene llevar al launcher la política de procesos de `AwManager`, la provisión inicial ni la configuración global de logs.

## Alcance y límites

La comprobación del equipo confirmó paquetes instalados y servicios Binder registrados. Se inspeccionaron estáticamente nombres de componentes, permisos, clases/funciones relevantes y dependencias ELF. No se capturó tráfico de red, no se hizo análisis dinámico/instrumentado, no se comparó el firmware con una imagen oficial conocida y no se verificó la procedencia de cada firma criptográfica. Por ello, esta evaluación sirve para priorizar adaptación y auditoría; no certifica ausencia ni presencia de malware.
