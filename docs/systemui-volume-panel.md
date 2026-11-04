# Panel de volumen lateral para HY300 Pro

El `SystemUI.apk` del HY300 Pro incluye `integer/volume_dialog_gravity`. Su variante `television` usa `0x51` (abajo y centrado), mientras que el valor general es `0x15` (derecha y centrado). La variante `layout-land-television-v8/volume_dialog_row.xml` usa una fila horizontal; el diseño genérico ya contiene una barra vertical rotada 90°.

`tools/patch-systemui-volume.py` parte del APK extraído del dispositivo, cambia el valor de gravedad de la variante TV, usa la fila vertical existente, alinea el archivo y firma el resultado con el certificado de plataforma que coincide con el firmware.

## Prueba en el proyector

El 4 de noviembre de 2026 se instaló el cambio como módulo Magisk `hy300-volume-panel`. SystemUI de fábrica permanece intacto debajo del montaje. Tras reiniciar:

- `cmd overlay lookup` resolvió `volume_dialog_gravity` a `21` (`0x15`, derecha-centro).
- `VOLUME_UP` mostró el panel estándar vertical en el borde derecho; `VOLUME_DOWN` respondió también.
- El proceso SystemUI permaneció activo y no se observaron excepciones ni cierres durante la prueba.

La prueba de teclas se hizo con eventos ADB; falta comprobar los botones del mando físico.

Para revertir, elimina `/data/adb/modules/hy300-volume-panel` y reinicia. El APK base también queda respaldado en `/data/local/tmp/SystemUI.apk.original`.

El panel vertical conserva el flujo de volumen de SystemUI; el APK `AwTvVolume` no participa en su dibujo.
