# Análisis de los APK del proyector

Análisis estático de los APK extraídos del dispositivo conectado. Se usó JADX en `/tmp`; no se modificaron ni se ejecutaron los APK y no se escribieron ajustes del proyector.

## Identidad de los APK

| APK | Paquete | Versión | Observaciones |
| --- | --- | --- | --- |
| `Ajustes-del-proyector.apk` | `com.htc.htcsettings` | 1.9.5 | `HomeActivity` abre los ajustes. `ProjectionActivity` es la sección principal de proyección. |
| `Entradas-HDMI.apk` | `com.softwinner.awsource` | 0.0.1 | Selector con HDMI1–3 y CVBS1–2. |

El selector HDMI valida las entradas con `TvInputManager` y entrega la fuente elegida a `com.softwinner.awlivetv.MainActivity` mediante el extra `input_source` (`hdmi` o `cvbs`).

## Ajustes encontrados

La pantalla de proyección organiza controles para modo de proyección, zoom, imagen/color, brillo, temperatura de color, salvapantallas, ventilador, corrección trapezoidal, reconocimiento/corrección inteligente, velocidad del puntero y sonidos del sistema. La presencia de varios de ellos se decide leyendo `/oem/config.ini`, así que las funciones visibles varían según el firmware.

El APK usa varios mecanismos del sistema:

- Ajustes de imagen: servicio Binder de SoftWinner (clase `com.softwinner.a`) para leer y escribir brillo, contraste, tono, saturación, nitidez y temperatura de color.
- Configuración del panel y espejo: servicio Binder de SoftWinner (`com.softwinner.c.c`), `persist.sys.panelvalue` y un broadcast `android.intent.projective_mode`.
- Ventilador: API no estándar `android.os.PrjScreen.get_fan_level/set_fan_level`.
- Zoom y trapezoide: propiedades `persist.sys.*`; trapezoide también intenta escribir nodos concretos bajo `/sys/devices/ff140000.i2c/...`.
- Corrección inteligente: propiedades, cámara/motor y más nodos de `/sys`.
- Salvapantallas y tono de teclas: `Settings.System` más propiedades del firmware.
- Velocidad del puntero: `InputManager` del sistema.

## Brillo de imagen y lúmenes ANSI

Los lúmenes ANSI describen la salida luminosa del proyector medida como equipo; no son un ajuste de Android. Un menú puede cambiar el modo de funcionamiento del LED dentro de los límites del hardware, pero no convierte el proyector en un modelo con una clasificación ANSI superior.

La documentación pública de Magcubic no es consistente entre revisiones/listados del HY300 Pro: la ficha del producto indica **170 ANSI lm**, mientras que su página comparativa indica **200 ANSI lm**. Otras páginas de venta también publican cifras distintas. Por eso no se debe presentar una cifra única como medición confirmada de la unidad conectada; para este proyecto, el límite físico exacto queda **sin medir**.

El APK OEM incluye opciones de modo del dispositivo como estándar, ahorro y alto brillo, y el soporte de Magcubic indica que el brillo puede ajustarse en `Ajustes > Projection Settings > Device Mode`. La página de soporte también describe ajustes de imagen bajo `Ajustes > Projection Settings > Display Settings`. La disponibilidad y los nombres pueden variar por firmware. El modo alto brillo puede aumentar la salida respecto del modo ahorro/estándar si el firmware lo implementa, pero no aumenta la capacidad máxima ni la especificación ANSI del hardware. Los controles de brillo de imagen pueden cambiar cómo se representa la señal sin aumentar necesariamente la luz física del LED.

Medidas prácticas para que la proyección se vea más luminosa:

- Probar **Device Mode > High Brightness**, si esa opción está disponible en la unidad, y comparar con el modo actual.
- Reducir la luz ambiental y usar la proyección en una habitación oscura; Magcubic recomienda el HY300 Pro para uso en ambientes oscuros.
- Reducir el tamaño de la imagen proyectada cuando sea posible: concentra una salida luminosa similar en un área menor, elevando la luminosidad percibida por superficie.
- Ajustar imagen y color desde `Display Settings`, recordando que cambiar brillo/contraste de la señal no equivale necesariamente a aumentar la salida óptica.

Estas acciones no se han probado en la unidad conectada durante esta revisión; no afirmar que el modo de alto brillo esté disponible ni que aumente efectivamente la salida hasta comprobarlo allí. No se recomienda escribir propiedades privadas ni intentar sobrealimentar el LED desde el launcher.

Fuentes consultadas:

- [Ficha oficial del HY300 Pro](https://www.magcubicbiz.com/product/hd-series/hy-300-pro-portable-projector): publica 170 ANSI lm y enfoque manual.
- [Comparación oficial de proyectores Magcubic](https://www.magcubic.com/pt/pages/compare-magcubic-projectors): publica 200 ANSI lm para HY300 Pro.
- [Soporte Magcubic para HY300 Pro](https://www.magcubic-support.com/HY300pr): indica el menú `Projection Settings > Display Settings` para ajustar la imagen.
- [FAQ de Magcubic](https://magcubic-support.com/cjwt): indica `Projection Settings > Device Mode` para seleccionar un modo de brillo.
- [Artículo de Magcubic sobre HY320Pro y HY300 Pro](https://www.magcubic.com/blogs/about-projector/magcubic-hy320pro-vs-hy300-pro-whats-new-in-the-latest-upgrade/): describe el HY300 Pro como adecuado para habitaciones oscuras.

## Alcance elegido: configuración del proyector

Solo se integrarán opciones que cambian la proyección o el funcionamiento físico del proyector: modo/orientación, zoom, trapezoide, imagen y color, brillo, ventilador, y corrección/enfoque cuando el firmware las ofrezca. El APK filtra varias opciones según `/oem/config.ini` y propiedades del sistema; la lista no es universal.

### Preferencia de integración para los controles de imagen

La preferencia acordada es integrar **zoom, trapezoide manual, ajuste manual de cuatro esquinas y corrección automática en una interfaz propia del launcher**, sin convertirlos en accesos directos a pantallas OEM. El enfoque por motor queda excluido para este HY300 Pro. La interfaz nativa debe reflejar resultados reales del firmware y no simular que un ajuste se aplicó.

El APK OEM ejecuta estas operaciones con propiedades privadas (`persist.sys.*` / `persist.display.*`), una transacción específica de `SurfaceFlinger` y, en algunas rutas, nodos `/sys`. La app OEM tiene UID compartido de sistema; el launcher corre como UID 10074. El backend actual del launcher usa el ADB local del proyector para ejecutar comandos como root; se verificaron escrituras graduales de esquinas y aplicación inmediata. No copiar esas escrituras a una app normal suponiendo que tendrá permisos. La posibilidad de sustituir ADB por `su` de Magisk está en análisis; ver [análisis del backend privilegiado](privileged-image-control-backend-analysis.md).

El launcher incluye una pantalla nativa de **Imagen** con vista geométrica 16:9, selección de modo, zoom y restauración, además del editor D-pad de cuatro esquinas. Los valores de esquina y zoom se envían al firmware a través del backend ADB local. El trapecio manual queda deshabilitado porque falta el nodo sysfs requerido; la corrección automática aún no tiene efecto óptico confirmado en esta unidad.

Red, Wi-Fi, Bluetooth, fecha/hora, idioma, administrador de aplicaciones, volumen, tono de teclas y opciones generales del dispositivo quedan en Ajustes de Android. Algunas aparecen dentro de las pantallas OEM, pero usan `Settings.System`, `AudioManager` o servicios Android estándar y no justifican duplicarlas en el launcher. Los modos de salida de audio que escriben propiedades privadas `media.cfg.*` también quedan fuera del alcance elegido.

El menú del launcher ya separa “Ajustes Android” de “Ajustes del proyector”; este último entra directamente a `ProjectionActivity` en vez de la portada `HomeActivity`, que mezcla ambas categorías. Por ahora esa pantalla es OEM; los controles nativos se portarán uno por uno solo cuando se haya confirmado su backend en el firmware del HY300 Pro.

## Cómo implementa el APK la corrección de imagen

El APK contiene varias rutas que suelen llamarse “corrección”, pero no son el mismo control:

- **Zoom:** `ScreenZoomActivity` cambia un valor de 0 a 20 y delega el cálculo a `com.htc.util.k`. Según las capacidades del firmware, actualiza overscan (`persist.vendor.overscan.main`) o manda una transformación de ocho coordenadas normalizadas al servicio Binder `SurfaceFlinger`, transacción `1050`. También persiste valores en `persist.sys.zoom_value` y `persist.sys.zoom.value`.
- **Trapezoide manual:** `KeystonecorrectionActivity` cambia `persist.sys.keystone.lt/lb/rt/rb`, activa la actualización con `persist.sys.keystone.update` y escribe el modo en propiedades `persist.sys.prj.*`. También intenta escribir nodos bajo `/sys/devices/ff140000.i2c/i2c-2/2-001b/` para activar el panel y el modo manual. La actividad no está exportada.
- **Ajuste manual de cuatro esquinas:** `CorrectionActivity` permite mover cada vértice en incrementos y restringe las coordenadas a 0–500. Su helper lee/escribe coordenadas `persist.display.keystone_*` y usa la transacción Binder de `SurfaceFlinger`; otra ruta de firmware calcula overscan.
- **Corrección automática:** `CorrectionIndexActivity` cambia propiedades como `persist.sys.tpryauto`, `persist.sys.tpryxcrt` y `persist.sys.vafocus`, y emite broadcasts internos para iniciar la rutina automática. La disponibilidad depende de flags de `/oem/config.ini` y propiedades como `persist.sys.camok` y `persist.sys.focusupdn`.
- **Enfoque/corrección por motor:** `CorrectActivity` lee y escribe `/sys/devices/platform/motor_ctr/motor_ctrl` con comandos numéricos, y consulta propiedades para reconocer pantalla/obstáculos. Parte de la calibración abre otra app (`com.hysd.vafocus`).

### Integración nativa en el launcher

La pantalla “Imagen” ofrece zoom y selección de corrección; la opción “Cuatro esquinas” abre un patrón de calibración a pantalla completa. Su retícula tiene 9 columnas × 5 filas: en un marco 16:9, cada celda resulta casi cuadrada (relación 80:81), útil para comparar proporciones. La esquina activa se distingue con el punto azul; abajo se muestra una indicación compacta con iconos para mover y pasar a la siguiente. Cada flecha escribe la nueva coordenada y aplica la transformación inmediatamente; los movimientos se encolan en orden para no perder pulsaciones mientras responde ADB. Enter/OK solo avanza en ciclo horario por las cuatro esquinas; no confirma ni aplica movimientos. Atrás vuelve al resumen de Imagen. Zoom y corrección automática siguen en la vista principal porque no requieren edición detallada. El puente usa el ADB local del propio proyector, autenticado con una clave privada de la app; las acciones de firmware se ejecutan fuera del proceso Android con UID root. El overlay hace la lectura y escrituras en segundo plano, informa fallos y no cambia automáticamente ningún ajuste al abrirse. Si el ADB local deja de estar disponible, los controles no se presentan como aplicados.

El control de trapezoide manual se muestra como no disponible: aunque `config.ini` declara `manualKeystone:true`, el nodo `/sys/devices/ff140000.i2c/i2c-2/2-001b/manual_Keystone_mode` no existe en el firmware conectado. No se simula el resultado escribiendo solo propiedades.

Zoom y cuatro esquinas llaman a la misma transacción `SurfaceFlinger` 1050 con vectores distintos. En esta versión, el último de esos controles usado prevalece y no se promete que sus efectos se compongan. La corrección automática usa propiedades y broadcasts OEM; se deja a elección explícita del usuario y aún falta observar su efecto óptico en esta unidad.

### Verificación en el proyector conectado

En la sesión del 2026-11-02, ADB volvió a conectar. La pantalla OEM `CorrectionIndexActivity` mostró trapezoide automático/manual y ángulo inicial; no mostró el control de enfoque automático. `CorrectActivity` mostró reconocimiento de obstáculos y de pantalla; la fila de enfoque manual estaba oculta. El APK tiene ambas filas en sus layouts, pero `/product/etc/config.ini` y las propiedades del firmware deciden si aparecen.

La ruta `/sys/devices/platform/motor_ctr/motor_ctrl` existe y se pudo leer como root; inicialmente devolvió `00.400.01`. Las propiedades `persist.sys.vafocus`, `persist.sys.camok`, `persist.sys.focusupdn` y `persist.sys.tpryxcrt` estaban en `0` o vacías. Con autorización del usuario se ejecutó un ciclo de 500 iteraciones por sentido; tardó 2:02 por la latencia de ADB y terminó de nuevo en `00.400.01`. El usuario no observó cambio de imagen en una segunda prueba. Se detuvo ese segundo tramo y se invirtió el movimiento hasta regresar otra vez a `00.400.01`. No se activó la corrección automática. Esto confirma que el controlador acepta escrituras y vuelve al mismo valor, pero no confirma que haya un motor físico de enfoque conectado ni que esos comandos afecten la lente.

La ficha oficial de [comparación Magcubic HY300](https://www.magcubic.com/pt/pages/compare-magcubic-projectors) especifica **enfoque manual** para HY300 Pro y HY300 Pro+, y distingue HY300X (enfoque eléctrico), HY300 Ultra (enfoque eléctrico por control remoto) e HY300MAX (autofocus). La ficha de producto del fabricante también indica enfoque manual para HY300 Pro ([Magcubic HY300 Pro](https://www.magcubicbiz.com/product/hd-series/hy-300-pro-portable-projector/)). Esto coincide con la prueba visual del usuario: el HY300 Pro conectado debe tratarse como enfoque óptico manual. El código de motor del APK parece corresponder a variantes/perfiles de firmware y no debe exponerse como control de enfoque en el launcher para esta unidad.

El manifiesto OEM pide permisos privilegiados, incluida escritura de ajustes seguros. En el dispositivo, el paquete está instalado con UID compartido `android.uid.system` (1000); el launcher corre con UID 10074. Añadir esos permisos al manifiesto del launcher no le daría el UID ni la firma del fabricante.

## Identidad y alcance de la verificación

El usuario confirma que el dispositivo conectado es un HY300 Pro y que el fabricante entrega una identificación de vendor incorrecta. ADB informa `ADT-3/rk3566_rgo/adt3`, Android SDK 30 y `persist.sys.prj.type=hy260`; su shell devuelve UID 0. Estos identificadores reflejan lo que publica el firmware y se registran sin sustituir la identificación física confirmada por el usuario.

En la verificación del 2026-11-02, la app se compiló e instaló correctamente sin cambiar el launcher predeterminado. La pantalla nativa leyó zoom `5/20`, mostró “Conectado” y detectó la ausencia del nodo de trapezoide manual. Con D-pad se abrió la vista de “Cuatro esquinas”; la captura confirma el patrón 9 × 5 a pantalla completa y la guía compacta con iconos abajo. La jerarquía UI confirmó foco en el editor. Con la esquina inferior izquierda activa, flecha derecha cambió X de 120 a 121 sin pulsar Enter y flecha izquierda la restauró a 120, verificado por `getprop`; Enter solo cambia la esquina activa. Una prueba anterior sobre la esquina superior izquierda también cambió X `133 → 134 → 133` sin Enter. No se activó la corrección automática. El paso de un punto es demasiado pequeño para afirmar un cambio óptico visible; falta esa confirmación visual. Capturas: `artifacts/projector-image-overview.png` y `artifacts/projector-image-controls.png`.

El ADB local, las propiedades privadas y la transacción SurfaceFlinger son específicos de este firmware. La app comprueba root antes de habilitar cambios; esto no es un método portable a otros dispositivos Android. Zoom y cuatro esquinas comparten el mismo vector de transformación y no se componen en la integración actual.
