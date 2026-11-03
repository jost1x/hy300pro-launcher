# Análisis del backend privilegiado de imagen

> Actualización del 3 de noviembre de 2026: ya se integró y probó el servicio propio firmado para geometría y el acceso directo para orientación. Véase [Control de imagen integrado](integrated-image-control.md). Las recomendaciones anteriores de conservar ADB que aparecen más abajo son antecedentes históricos, sustituidos por esta integración.


Fecha de inspección: 2026-11-02. La inspección del dispositivo fue de solo lectura: no se instalaron módulos, no se escribieron particiones y no se alteraron ajustes del proyector.


## Prueba posterior completada — 2026-11-03

La hipótesis de firma coincidente ya fue probada: el APK aislado recibió `HARDWARE_TEST`, ejecutó 1050 con UID 10052 y restauró la geometría y las 15 propiedades iniciales. Ver [resultados y límites](signed-image-probe-verification.md). Las secciones siguientes documentan el análisis previo; las menciones a «prueba pendiente» quedan superadas por esta verificación. La integración en el backend habitual aún no se ha realizado.

## Hallazgo posterior: permiso y certificado — 2026-11-03

La inspección del binario real identificó la comprobación de geometría: UID 1000 o `android.permission.HARDWARE_TEST`, que el dispositivo declara `signature`. Además, el certificado de plataforma instalado coincide con el certificado de pruebas platform de AOSP. Esto abre una vía de firma coincidente que debe probarse antes de considerar cambios de firmware; corrige el supuesto previo de que solo dispondríamos de una firma privada inaccesible del fabricante. Ver [evidencia y siguiente prueba aislada](surfaceflinger-permission-analysis.md). La prueba firmada todavía no se instaló.

## Resultado del diagnóstico ejecutado — 2026-11-03

La prueba posterior desde el proceso real del launcher resolvió parte de la incertidumbre: **orientación permite lectura, cambio y restauración con UID 10074; SurfaceFlinger rechaza la transacción 1050 para ese mismo UID**. Las propiedades examinadas admitieron escritura y verificación, pero eso no sustituye la transformación de geometría. Los valores iniciales se restauraron y comprobaron. Ver [procedimiento, resultados y límites](direct-image-access-verification.md).

Puede prepararse un backend directo para orientación. Zoom y cuatro esquinas siguen necesitando resolver la autorización de SurfaceFlinger. Las secciones siguientes conservan el análisis previo y sus alternativas; la afirmación de que no se habían probado operaciones desde la app queda superada por este diagnóstico.

## Actualización: requisitos para eliminar ADB — 2026-11-03

Esta revisión incorpora el selector de orientación nativo y matiza la conclusión anterior: **todavía no se ha probado que el UID de una app normal esté impedido de usar todas estas operaciones en este firmware concreto**. Que la implementación actual exija root no demuestra que cada servicio lo exija. No se cambió el backend ni se modificaron ajustes del dispositivo en esta revisión.

### Evidencia actual

- El launcher usa ADB contra `127.0.0.1:5555`; no requiere un computador externo conectado durante el uso normal.
- La shell sigue teniendo UID 0 y contexto `u:r:su:s0`; SELinux está en `Permissive`.
- `/vendor/xbin/su` es un enlace a `./magisk`; `magisk -v` sigue devolviendo `30.6:MAGISK:R`. Sustituir ADB por ese `su` introduciría una dependencia explícita de Magisk, contraria a la preferencia registrada.
- El launcher tiene UID 10074 y la app OEM UID 1000 compartido con sistema.
- `run-as` devuelve UID 10074, pero usa el dominio `runas_app` y conserva grupos suplementarios de la sesión. **No equivale a una prueba desde el proceso normal del launcher.**
- Los componentes exportados inspeccionados del APK OEM no revelan una API de escritura de geometría u orientación: `StartActionReceiver` abre pantallas/entradas; `BusinessProvider` opera sobre una tabla SQLite; `BlueSpeakerService` corresponde a audio Bluetooth. No se ha identificado un sustituto autorizado del puente actual en esos componentes. Además, volver a usar ese APK como intermediario recuperaría la dependencia que se acaba de retirar para orientación.

### Dependencia por control

| Control | Operación real actual | Qué debe resolverse sin ADB |
| --- | --- | --- |
| Orientación | HIDL `ITvServer`, lectura 128/escritura 127, configuración 3; propiedad `persist.sys.panelvalue` | Acceso a HIDL desde la app, compatibilidad de interfaces no públicas y escritura persistente. Mantener lectura posterior y restauración ante error. |
| Zoom y cuatro esquinas | Transacción 1050 de SurfaceFlinger y propiedades privadas | Autorización de la transacción para el UID del launcher y permiso para todas las propiedades. Mantener composición, lotes y Cancelar. |
| Corrección automática | Propiedades privadas y broadcasts del firmware | Permiso de escritura y recepción/efecto de los broadcasts; el resultado óptico sigue pendiente. |
| Trapecio manual | Nodo sysfs ausente | Eliminar ADB no crea el nodo ni habilita esta función. |

La orientación ya no usa la app OEM, pero `ProjectorOrientationBridge` se inicia con `app_process` desde ADB root y exige UID 0 expresamente. Para investigar acceso directo habría que separar la operación HIDL de esa entrada de consola, sin confundir ese guard propio con una denegación del HAL. Ejecutar una shell o `app_process` desde una app normal conserva sus privilegios; no sustituye por sí mismo ADB root.

### Alternativas y requisitos

1. **Acceso directo desde el launcher, primera opción a investigar.** Preparar un diagnóstico ejecutado en el proceso real de la app: registrar UID/contexto, probar disponibilidad y lectura HIDL y acceso a propiedades. Después, con verificación específica, probar una escritura reversible por control y confirmar tanto el servicio como la persistencia. Las restricciones de APIs no públicas, los controles de UID/permisos en los servicios y SELinux son capas diferentes. Que SELinux esté permisivo no elimina las otras. Puede resultar viable para orientación y no para geometría; solo las pruebas por operación permiten decidirlo.
2. **Servicio propio integrado en el firmware.** Si la app no obtiene acceso suficiente, incorporar un servicio iniciado por `init` con los permisos y el dominio SELinux necesarios. El launcher enviaría operaciones concretas por Binder o un socket local autenticado. Validar identidad/firma del cliente, modos, rangos y límites; no exponer comandos shell arbitrarios. Exige integración de arranque, reinicio ante fallos, compatibilidad con la versión del firmware y un procedimiento de instalación/recuperación. Es una vía para funcionar sin ADB ni Magisk durante el uso diario, pero aún no está validada en esta unidad.
3. **Integración firmada por el fabricante.** Una app/servicio firmado y configurado por el fabricante podría tener los permisos adecuados. No disponemos de esa clave ni de una API acordada con el fabricante. Copiar el APK a `priv-app`, conceder permisos ordinarios o seleccionar el launcher como Home no le da automáticamente UID 1000 ni todos los permisos del panel.
4. **Root mediante `su`.** Evitaría el transporte ADB, pero aquí ese ejecutable pertenece a Magisk. Requiere autorización de superusuario y adaptar el backend. Se documenta como alternativa técnica, no como ruta elegida; no se ejecutó `su` ni se solicitó autorización a Magisk.

### Cambios de arquitectura si se confirma una alternativa

- Separar las operaciones de imagen de su transporte mediante una interfaz común (`readState`, `setCorners`, `setZoom`, `readOrientation`, `setOrientation`).
- Mantener ADB como implementación transitoria mientras se valida la alternativa; no cambiar la lógica de la retícula ni la navegación D-pad.
- Conservar orden de escrituras, agrupación de movimientos, comprobaciones del servicio y restauración al cancelar o ante error. Informar de estados inciertos si se pierde la comunicación después de una escritura.
- Si hay servicio persistente, mantener la conexión y evitar crear un proceso `app_process` por cada operación de orientación. La mejora de latencia debe medirse, no asumirse.
- Criterio final: todos los controles soportados funcionan con el backend nuevo tras reiniciar el equipo y con ADB desactivado. Para esa prueba se necesita un método de recuperación ya comprobado; una sesión iniciada mediante ADB que solo sobrevive hasta reiniciar no satisface el objetivo.

**Recomendación:** hacer primero el diagnóstico dentro del proceso real del launcher. Si falla el acceso necesario y se mantiene la preferencia de no usar Magisk, estudiar un servicio integrado en firmware con respaldo y recuperación. No hay evidencia suficiente para prometer que bastará un cambio en el APK, ni para afirmar todavía que sea obligatorio modificar el firmware.

Referencias de Android usadas para interpretar las restricciones generales (no prueban permisos concretos del HY300 Pro): [interfaces no públicas](https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces), [permisos privilegiados](https://source.android.com/docs/core/permissions/perms-allowlist) y [SELinux](https://source.android.com/docs/security/features/selinux).

## Hallazgo principal — análisis inicial del 2026-11-02

En el dispositivo conectado hay Magisk 30.6 y `su`, pero la preferencia del usuario es **evitar Magisk**. Por tanto, no se propone que el launcher invoque `su`, solicite una autorización a Magisk ni instale módulos Magisk.

El backend actualmente validado usa el cliente ADB propio del launcher contra `127.0.0.1:5555`; las operaciones se ejecutan en la shell privilegiada del firmware. La app no invoca la API de Magisk. Sin embargo, el dispositivo sí tiene Magisk instalado y el contexto root de ADB es `u:r:su:s0`, así que no se ha demostrado que ese ADB root seguiría disponible si Magisk se retirara del firmware.

Si la meta es eliminar también ADB y no usar Magisk, primero hay que comprobar el acceso directo a los servicios del firmware desde el UID real del launcher. Si no es suficiente, las opciones son una interfaz autorizada o incorporar un servicio privilegiado independiente al firmware. Esta segunda ruta es más compleja y no debe empezarse sin una imagen recuperable y un método de restauración comprobado.

## Estado observado

- Identidad que anuncia ADB: `rk3566_rgo` / `adt3`; el usuario identifica físicamente la unidad como HY300 Pro.
- Android SDK 30, `ro.build.type=user` y `ro.debuggable=1`.
- La shell ADB devuelve UID `0`, contexto `u:r:su:s0`.
- Magisk está instalado como `com.topjohnwu.magisk`; `magisk -v` reporta `30.6:MAGISK:R` y `magisk -V` reporta `30600`.
- `su` está disponible en `/vendor/xbin/su`.
- SELinux está en modo permisivo.
- `/`, `/vendor` y `/product` están montados de solo lectura desde dispositivos `dm-*`. El almacenamiento usa particiones dinámicas y hay slots `_a` / `_b` para `boot` y `vbmeta`.
- `/data/adb/modules`, `service.d` y `post-fs-data.d` no contienen módulos ni scripts propios.
- El APK OEM `com.htc.htcsettings` usa `android.uid.system` (1000). El launcher instalado usa UID 10074. Copiar el launcher a `priv-app` no le daría la firma ni el UID de sistema del fabricante.
- El launcher actual abre una conexión ADB a `127.0.0.1:5555`, autentica una clave propia y ejecuta los comandos con UID root. Ese backend ya probó escrituras de esquina y aplicación inmediata en el equipo.

## Opciones

### A. Mantener el ADB local actual — opción recomendada por ahora

Conserva la ruta ya probada para leer estado y aplicar ajustes sin que un computador externo permanezca conectado. No requiere que el launcher invoque Magisk. Su límite es que depende de que el firmware mantenga disponible y autorizado su ADB local con permisos root.

### B. Encontrar una interfaz OEM

Revisar componentes exportados, servicios Binder, broadcasts y permisos del APK OEM para saber si existe un canal compatible con una app normal. El análisis del APK encontró llamadas privilegiadas internas; no ha confirmado una API pública que las reemplace. Es la primera alternativa que conviene agotar para retirar ADB sin añadir un componente root propio.

### C. Incorporar un servicio privilegiado al firmware, sin Magisk

Un servicio nativo iniciado por `init` podría recibir operaciones tipadas mediante un canal local y ejecutar la transformación de imagen. Para ello habría que construir y desplegar una imagen de firmware que incorpore el ejecutable, la configuración de inicio y los permisos adecuados. Las particiones actuales son de solo lectura y dinámicas; el paquete OEM usa UID compartido de sistema. No basta con copiar el APK a `priv-app`.

Antes de esta ruta se necesita identificar el método de desbloqueo/flasheo, respaldar las particiones críticas y probar cómo recuperar el equipo. El estado actual de ADB root no confirma por sí solo que se pueda escribir o arrancar una imagen personalizada.

El sistema está en particiones dinámicas de solo lectura y el paquete OEM usa UID compartido de sistema. Esta ruta exigiría modificar y posiblemente volver a firmar o parchear imágenes; el acceso root actual no elimina esos requisitos. Es más invasiva que usar el Magisk que ya está instalado.

## Siguiente secuencia de trabajo

1. Mantener el backend ADB actual y no modificar las particiones del equipo.
2. Revisar el manifiesto y las llamadas del APK OEM para confirmar si hay un servicio o componente exportado utilizable. Si aparece una API, probarla primero de forma controlada.
3. Si no existe API, decidir si retirar ADB justifica crear y flashear un firmware personalizado con servicio `init`; antes de construirlo, obtener una copia recuperable y documentar el método de restauración.
4. En firmware personalizado, hacer que el helper exponga solo operaciones tipadas y valide UID/valores. Probar primero lectura, después una modificación de una esquina con reversión, y finalmente la navegación D-pad en tiempo real.
5. Si no se autoriza o no se puede recuperar una imagen personalizada, conservar ADB local. No usar `su` desde la app ni instalar módulos Magisk.

## Conclusión

Con la preferencia de evitar Magisk, lo adecuado es conservar por ahora el ADB local validado y revisar si el APK OEM ofrece una interfaz autorizada. Si se descarta el acceso directo desde la app, eliminar ADB sin usar Magisk requeriría una integración privilegiada en el firmware y sería una intervención considerable; solo conviene avanzar después de confirmar que se puede respaldar y restaurar el equipo. No se ha implementado ni probado ese servicio.
