# Verificación de acceso directo a imagen — HY300 Pro

Fecha: 2026-11-03. Diagnóstico autorizado por el usuario para determinar si el launcher puede prescindir de ADB.

## Resultado

La orientación funciona desde el proceso normal del launcher. La transacción de geometría de SurfaceFlinger es rechazada por permisos. No se migró el backend de producción: el diagnóstico permite decidir qué controles pueden migrarse y cuáles requieren una identidad privilegiada.

| Operación | Resultado en el dispositivo |
| --- | --- |
| Leer modo HIDL del panel | Correcto, modo 0 |
| Cambiar orientación y volver al inicio | Correcto, 0 → 1 → 0; lectura posterior del HAL |
| Escribir y verificar `persist.sys.panelvalue` | Correcto durante el cambio anterior |
| Leer propiedades de imagen | Correcto |
| Escribir el mismo valor en propiedades de zoom, esquinas y automatización existentes | Correcto; no demuestra un cambio funcional del zoom o de la automatización |
| Localizar SurfaceFlinger y leer su descriptor | Correcto |
| Enviar la matriz inicial sin modificar, transacción 1050 | Rechazado: `SecurityException` |
| Cambiar una esquina mediante SurfaceFlinger | Omitido al fallar la matriz inicial |
| Activar corrección automática mediante broadcasts | No probado; puede recalibrar la imagen |

SurfaceFlinger registró: `Permission Denial: can't access SurfaceFlinger pid=10536, uid=10074`. El mensaje por sí solo no identificaba el permiso requerido. La [inspección posterior del binario y del certificado](surfaceflinger-permission-analysis.md) identificó UID 1000 o `HARDWARE_TEST` y una posible vía mediante firma de plataforma coincidente, aún pendiente de prueba. El modo SELinux permisivo no evita este control del servicio.

## Cómo se ejecutó

Se añadió `DirectAccessDiagnosticActivity` únicamente a `src/debug`, con `exported=false`. Se ejecuta en el mismo proceso y paquete del launcher, bajo UID 10074 y contexto `untrusted_app`; no usa `run-as`, `su`, `app_process` ni ADB para las operaciones examinadas. ADB se usó desde el computador para instalar/lanzar la prueba y recuperar su informe, no como backend del diagnóstico. La lectura del informe mediante `run-as` no cambia la identidad con la que se ejecutaron las pruebas.

La prueba reutiliza por reflexión el código HIDL del propio `ProjectorOrientationBridge`, evitando su entrada `main`, que exige root. Las restricciones de interfaces internas de Android siguieron activas: no se cambió la política de APIs ocultas, permisos de la aplicación, SELinux ni configuración del sistema.

Primero se probaron solo lecturas. La fase de escritura requirió el extra explícito `writes=true`: capturó el modo inicial, alternó al modo espejo, leyó la confirmación del servicio y restauró el modo y la propiedad originales en `finally`. Para geometría se intentó primero la matriz persistida sin cambios; el rechazo impidió ejecutar el movimiento previsto. Antes de la prueba se guardaron 15 propiedades de imagen en el host y, al terminar, se compararon todas: **idénticas al inicio**. No se activó la corrección automática.

Compilación e instalación de depuración correctas. Tras una instalación el framework Android falló durante `handleBindApplication`, antes de iniciar el diagnóstico; el relanzamiento completó la prueba. El informe válido identifica PID 10536 y finaliza en `COMPLETE_REVERSIBLE_WRITES`, por lo que no se confundió con el archivo anterior de solo lectura. La app se dejó nuevamente en su pantalla principal.

## Límites y siguiente decisión

- Es una prueba en este HY300 Pro y este firmware, con SELinux permisivo y el Magisk existente. No demuestra funcionamiento en un firmware stock diferente ni tras retirar Magisk.
- Se probó el APK de depuración con target SDK 32. Una migración debe verificar también la compilación de distribución y el funcionamiento tras reiniciar; no se desactivó ADB en esta sesión.
- La respuesta del HAL y la restauración de propiedades fueron verificadas; no se midió el efecto óptico en la pared.
- El siguiente cambio de software puede separar el cliente HIDL de la entrada root y usarlo directamente para orientación, manteniendo comprobaciones y restauración. No exige abrir la app OEM.
- Zoom y cuatro esquinas no pueden migrarse a esta misma ruta sin resolver el rechazo de SurfaceFlinger. Para eliminar ADB de todos los controles, manteniendo la preferencia de evitar Magisk, hace falta investigar una integración autorizada con permisos suficientes o un servicio propio integrado en firmware.
- No se debe sustituir la transformación por escrituras de propiedades solamente: guardar coordenadas no equivale a aplicarlas a la proyección.

## Informe del proceso de la app

```text
uid=10074 pid=10536
context: OK u:r:untrusted_app:s0:c74,c256,c512,c768
groups: OK Groups:	3003 9997 20074 50074
property.read: OK 0
property.writeMethod: OK public static void android.os.SystemProperties.set(java.lang.String,java.lang.String)
panel.read: OK 0
surface.lookup: OK android.ui.ISurfaceComposer
panel.changed=0->1 uid=10074
panel.restored=0
panel.writeAndRestore: OK readback confirmed
property.writeSame.persist.sys.zoom_value: OK confirmed
property.writeSame.persist.sys.zoom.value: OK confirmed
property.writeSame.persist.vendor.overscan.main: OK confirmed
property.writeSame.persist.sys.tpryauto: OK confirmed
property.writeSame.sys.temp_pass: OK SKIPPED unset property
property.writeSame.sys.auto_report_angle: OK confirmed
property.writeSame.persist.display.keystone_ltx: OK confirmed
property.writeSame.persist.display.keystone_lty: OK confirmed
property.writeSame.persist.display.keystone_rtx: OK confirmed
property.writeSame.persist.display.keystone_rty: OK confirmed
property.writeSame.persist.display.keystone_rbx: OK confirmed
property.writeSame.persist.display.keystone_rby: OK confirmed
property.writeSame.persist.display.keystone_lbx: OK confirmed
property.writeSame.persist.display.keystone_lby: OK confirmed
surface.writeAndRestore: DENIED_OR_UNAVAILABLE SecurityException: null
COMPLETE_REVERSIBLE_WRITES
```
