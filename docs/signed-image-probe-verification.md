# Prueba de geometría con firma de plataforma — HY300 Pro

Fecha: 2026-11-03. Instalación y prueba aislada autorizadas por el usuario.

## Resultado comprobado

**El paquete separado recibió `android.permission.HARDWARE_TEST` y pudo ejecutar la transacción 1050 de SurfaceFlinger con su propio UID de aplicación.** No hizo falta usar root, ADB ni la app OEM dentro del proceso que realizó esas operaciones.

- Compilación `:imageProbe:assembleRelease`: correcta, incluyendo lint de release. Se exceptuó únicamente la exigencia de target SDK de Google Play, que no aplica a esta prueba privada; se conservó target SDK 32 para comparar con el launcher.
- Firma: certificado platform de pruebas AOSP, SHA-256 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`, coincidente con el Android del equipo. Se verificó antes de instalar. El material de firma temporal se eliminó tras firmar; no se guardó en Git.
- Instalación: paquete nuevo `io.jostx.hy300launcher.imageprobe`; no se reemplazó ni se volvió a firmar el launcher.
- PackageManager: UID **10052**, `HARDWARE_TEST: granted=true`, sin UID compartido de sistema.
- Proceso real: contexto **`platform_app`**, UID 10052. Su único permiso solicitado es `HARDWARE_TEST`.
- SurfaceFlinger aceptó primero la matriz actual sin cambios, luego el cambio mínimo de esquina superior izquierda **X=100 → 101**, y finalmente la matriz original **X=100**.
- La comparación independiente desde el host confirmó que las **15 propiedades de imagen** guardadas estaban exactamente iguales al inicio. No se necesitó restauración de emergencia desde el host.

La lectura de permisos y el envío de las transacciones se ejecutaron dentro del APK. ADB se utilizó desde el computador para instalar/iniciar la prueba, recopilar evidencia y comprobar las propiedades después. La actividad no está exportada ni aparece como launcher/Home.

El primer intento de lanzar la fase de escritura sobre la actividad ya abierta solo entregó un nuevo intent: no volvió a ejecutar la prueba. Se detectó porque el informe seguía siendo `PASS_PERMISSION_ONLY`, sin cambios en propiedades. Se relanzó únicamente el paquete de prueba con `am start -S -W`; el informe válido es el siguiente.

## Informe del proceso

```text
uid=10052 pid=6868
context=u:r:platform_app:s0:c512,c768
HARDWARE_TEST=true
surface.sameMatrixAccepted=true
surface.changedTL=100->101
surface.originalMatrixAccepted=true
properties.allRestored=true
PASS_RESTORED
```

## Alcance

Se comprobó la aceptación de las transacciones por el servicio real y la restauración de propiedades. No se midió la forma óptica proyectada. Tampoco se reinició el proyector, se desactivó ADB ni se retiró Magisk; la independencia funcional después de esos cambios todavía debe verificarse. El resultado corresponde al firmware instalado y su certificado de plataforma.

No se modificaron particiones, permisos globales ni el launcher predeterminado. El launcher y sus datos permanecieron instalados con su firma anterior. El APK aislado quedó instalado para reproducir el diagnóstico, sin servicios en segundo plano; la pantalla volvió al launcher al terminar.

## Consecuencia para la integración

Ya hay evidencia de que un componente propio firmado para este firmware puede aplicar la geometría sin utilizar ADB como backend. El siguiente trabajo es convertir la prueba en una integración mantenible:

1. Servicio auxiliar con operaciones tipadas y autenticación del paquete/certificado del launcher, o migración planificada de firma si se quiere un único APK.
2. Lectura/escritura de geometría, composición del zoom, agrupación de movimientos y restauración al cancelar, manteniendo los controles existentes.
3. Gestión de desconexión y errores sin dar por aplicados movimientos no confirmados.
4. Verificación desde la interfaz del launcher, después de reiniciar y finalmente con ADB desactivado y recuperación disponible.

El launcher habitual todavía utiliza su backend anterior; el éxito de este APK no significa que esa migración ya esté hecha.
