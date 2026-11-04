# APKs de componentes Aw del HY300 Pro

Extraídos por ADB del proyector `192.168.100.12:5555` el 3 de noviembre de 2026. Se copiaron los APKs tal como están instalados; no se modificaron ni se instalaron de vuelta.

| Archivo local | Paquete | Origen en el proyector | Componentes identificados | SHA-256 |
|---|---|---|---|---|
| `AwTvVolume.apk` | `com.softwinner.tvvolume` | `/system/priv-app/AwTvVolume/AwTvVolume.apk` | `MainActivity`; servicio `VolumeService` | `da0e104a4fd8f4d09f2cc9abfe715a5887342827f0414b6eeb240eb206714f5a` |
| `AwTvProvision.apk` | `com.softwinner.provision` | `/system/priv-app/AwTvProvision/AwTvProvision.apk` | `DefaultActivity` | `083ab3384d0c70fa461afae31a1f80e39cf2c8b164aba2365244c6d4a89b5120` |
| `AwlogSettings.apk` | `com.softwinner.awlogsettings` | `/system/app/AwlogSettings/AwlogSettings.apk` | `MainActivity` | `4283756ca7cadb19b3ad523c213ccf032735d6db1b21c58801fe4494aaac4298` |
| `AwLiveTv.apk` | `com.softwinner.awlivetv` | `/system/app/AwLiveTv/AwLiveTv.apk` | `MainActivity` | `e863bba403f5a321724df48be425bbcfe0ee2e9a9f3cc51763d4b57adf1bc055` |
| `AwSource.apk` | `com.softwinner.awsource` | `/system/app/AwSource/AwSource.apk` | `MainActivity` | `6423c572dafaa947c2e2bef625a8379abcb432be6c4ccd50c226828b0cedd074` |
| `AwManager.apk` | `com.softwinner.awmanager` | `/system/priv-app/AwManager/AwManager.apk` | `AwManager`; `GCMBroadcastReceiver` | `8b2271a0ec5d37962dbe6f12c4f3cdcaadccb50fbbb0c8ad11148552f3fe311b` |
| `AwIconShapeOverlay.apk` | `com.softwinner.theme.icon` | `/product/overlay/AwIconShape/AwIconShapeOverlay.apk` | Resource overlay; no app components | `311208a662c34418eabc6b805979ee3663d913c159cf985048013c9a4698fe5c` |
| `AwAccentColorOverlay.apk` | `com.softwinner.theme.color` | `/product/overlay/AwAccentColor/AwAccentColorOverlay.apk` | Resource overlay; no app components | `29be75e75c90da79185319ee6fb504a86048345940db9c83cafaf51a655e3bd0` |
| `VideoInputService.apk` | `com.softwinner.vis` | `/system/priv-app/VideoInputService/VideoInputService.apk` | `HdmiInputService`; `CvbsInputService` (process `com.softwinner.awtvinputservice`) | `c4e2495003abb12283bf99511b768421f1f557ac2959fdaf1bb7b559ea5d9f94` |

Los cinco daemons ejecutables con `-aw` están descargados en [`services/`](services/README.md). Los servicios Binder `android.aw.IBackgroundManager`, `softwinner.gpio`, `softwinner.isomountmanager`, `softwinner.multi_ir` y `softwinner.systemmix` aparecen en `service list`; no son APK independientes y no se copiaron contenedores generales del sistema para representarlos.

La evaluación funcional y las recomendaciones de adaptación están en [Evaluación de componentes Aw](../../docs/aw-components-assessment.md).
