# Análisis del backend privilegiado de imagen

Fecha de inspección: 2026-11-02. La inspección del dispositivo fue de solo lectura: no se instalaron módulos, no se escribieron particiones y no se alteraron ajustes del proyector.

## Hallazgo principal

En el dispositivo conectado hay Magisk 30.6 y `su`, pero la preferencia del usuario es **evitar Magisk**. Por tanto, no se propone que el launcher invoque `su`, solicite una autorización a Magisk ni instale módulos Magisk.

El backend actualmente validado usa el cliente ADB propio del launcher contra `127.0.0.1:5555`; las operaciones se ejecutan en la shell privilegiada del firmware. La app no invoca la API de Magisk. Sin embargo, el dispositivo sí tiene Magisk instalado y el contexto root de ADB es `u:r:su:s0`, así que no se ha demostrado que ese ADB root seguiría disponible si Magisk se retirara del firmware.

Si la meta es eliminar también ADB y no usar Magisk, las opciones que quedan son encontrar una interfaz OEM autorizada o modificar el firmware para incorporar un servicio privilegiado independiente. Esta segunda ruta es más compleja y no debe empezarse sin una imagen recuperable y un método de restauración comprobado.

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

Con la preferencia de evitar Magisk, lo adecuado es conservar por ahora el ADB local validado y revisar si el APK OEM ofrece una interfaz autorizada. Eliminar ADB sin usar Magisk requiere un servicio agregado al firmware y es una intervención considerable; solo conviene avanzar después de confirmar que se puede respaldar y restaurar el equipo. No se ha implementado ni probado ese servicio.
