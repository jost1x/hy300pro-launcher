# Servicios Aw extraídos del HY300 Pro

Binarios ejecutables copiados de `/vendor/bin/hw` por ADB el 3 de noviembre de 2026. Son servicios de Android vendor, no APKs. Se identificaron como procesos activos con `ps` y se comprobó su ruta ejecutable bajo `/proc/<pid>/exe`.

| Archivo | Proceso | PID durante la captura | SHA-256 |
|---|---|---:|---|
| `android.hardware.keymaster@4.1-service-aw` | `android.hardware.keymaster@4.1-service-aw` | 2345 | `1d2a335e75af226140f0cb8c96b8a3beb9a2c2bf041a91aabd38d9025d1ea914` |
| `android.hardware.gatekeeper@1.0-service-aw` | `android.hardware.gatekeeper@1.0-service-aw` | 2451 | `ca1ae2430c5d2fb75d360f31f8eded461932680eefea29b6b87cabc4a7ed56fb` |
| `android.hardware.power-service-aw` | `android.hardware.power-service-aw` | 2457 | `b28c419f5ae3a8a11ddeb129957b0678f9cf875f635078c5ca03517d0c3bbc32` |
| `android.hardware.thermal@2.0-service.aw` | `android.hardware.thermal@2.0-service.aw` | 2458 | `11bc8fbb53d48c89a87afbd87c32eb89cbbc05f377546bd65807b29c31e602c7` |
| `android.hardware.usb@1.0-service.aw` | `android.hardware.usb@1.0-service.aw` | 2461 | `d6159aa132347717850f559d013f7e82c719dafb02bd6b619fe638f6ae834255` |

También se verificaron los servicios Binder `softwinner.gpio`, `softwinner.isomountmanager`, `softwinner.multi_ir`, `softwinner.systemmix` y `background` (`android.aw.IBackgroundManager`). Están registrados en el gestor Binder; `background` responde a `dumpsys background` con información Awbms y corre dentro de `system_server`. No tienen un APK independiente con esos nombres. No se copiaron `services.jar` ni otros contenedores generales del sistema como si fueran el binario de un servicio Aw concreto.
