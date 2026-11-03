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

El launcher incluye una pantalla nativa de **Imagen** con selección de modo y zoom mediante slider D-pad. El modo Cuatro esquinas abre un editor con retícula geométrica 16:9. Los valores de esquina y zoom se envían al firmware a través del backend ADB local. El trapecio manual queda deshabilitado porque falta el nodo sysfs requerido; la corrección automática aún no tiene efecto óptico confirmado en esta unidad.

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

La pantalla “Imagen” ofrece zoom y selección de corrección; “Cuatro esquinas” abre un patrón fijo de calibración 16:9 a pantalla completa. Su retícula tiene 9 columnas × 5 filas, por lo que las celdas base miden casi lo mismo en ambos ejes (ancho/alto 80:81). La app no desplaza las líneas según las coordenadas guardadas: SurfaceFlinger transforma la retícula una sola vez junto con el resto de la salida. La persona ajusta cada esquina con el D-pad y compara la forma proyectada de las celdas; la esquina activa se marca en azul. Los movimientos se agrupan con 150 ms de espera y un máximo de 300 ms durante pulsaciones repetidas, y solo hay una transacción ADB en curso. Cada lote se escribe y se vuelve a leer desde el firmware antes de mostrarse como enviado. **Aplicar** conserva el ajuste visible y vuelve al resumen de Imagen; **Cancelar** o Atrás restaura las esquinas que estaban al iniciar la sesión. Si falla una escritura, se descartan los movimientos pendientes y se intenta restaurar el estado inicial. Enter abre la selección de esquina y las acciones. Zoom y corrección automática siguen en la vista principal. El puente usa el ADB local del proyector; las acciones de firmware se ejecutan fuera del proceso Android con UID root.

El control de trapezoide manual se muestra como no disponible: aunque `config.ini` declara `manualKeystone:true`, el nodo `/sys/devices/ff140000.i2c/i2c-2/2-001b/manual_Keystone_mode` no existe en el firmware conectado. No se simula el resultado escribiendo solo propiedades.

Zoom y cuatro esquinas llaman a la misma transacción `SurfaceFlinger` 1050. Para componer ambos ajustes, el zoom escala uniformemente los cuatro vértices alrededor del centro de la imagen y guarda el resultado en las propiedades `persist.display.keystone_*` y en la matriz de SurfaceFlinger. Así, las cuatro esquinas siguen definiendo la forma y el zoom conserva sus proporciones mientras cambia su tamaño. Si el firmware tiene un zoom anterior sin coordenadas guardadas, la app materializa primero el rectángulo neutral equivalente a ese nivel. La corrección automática usa propiedades y broadcasts OEM; se deja a elección explícita del usuario y aún falta observar su efecto óptico en esta unidad.

### Verificación en el proyector conectado

En la sesión del 2026-11-02, ADB volvió a conectar. La pantalla OEM `CorrectionIndexActivity` mostró trapezoide automático/manual y ángulo inicial; no mostró el control de enfoque automático. `CorrectActivity` mostró reconocimiento de obstáculos y de pantalla; la fila de enfoque manual estaba oculta. El APK tiene ambas filas en sus layouts, pero `/product/etc/config.ini` y las propiedades del firmware deciden si aparecen.

La ruta `/sys/devices/platform/motor_ctr/motor_ctrl` existe y se pudo leer como root; inicialmente devolvió `00.400.01`. Las propiedades `persist.sys.vafocus`, `persist.sys.camok`, `persist.sys.focusupdn` y `persist.sys.tpryxcrt` estaban en `0` o vacías. Con autorización del usuario se ejecutó un ciclo de 500 iteraciones por sentido; tardó 2:02 por la latencia de ADB y terminó de nuevo en `00.400.01`. El usuario no observó cambio de imagen en una segunda prueba. Se detuvo ese segundo tramo y se invirtió el movimiento hasta regresar otra vez a `00.400.01`. No se activó la corrección automática. Esto confirma que el controlador acepta escrituras y vuelve al mismo valor, pero no confirma que haya un motor físico de enfoque conectado ni que esos comandos afecten la lente.

La ficha oficial de [comparación Magcubic HY300](https://www.magcubic.com/pt/pages/compare-magcubic-projectors) especifica **enfoque manual** para HY300 Pro y HY300 Pro+, y distingue HY300X (enfoque eléctrico), HY300 Ultra (enfoque eléctrico por control remoto) e HY300MAX (autofocus). La ficha de producto del fabricante también indica enfoque manual para HY300 Pro ([Magcubic HY300 Pro](https://www.magcubicbiz.com/product/hd-series/hy-300-pro-portable-projector/)). Esto coincide con la prueba visual del usuario: el HY300 Pro conectado debe tratarse como enfoque óptico manual. El código de motor del APK parece corresponder a variantes/perfiles de firmware y no debe exponerse como control de enfoque en el launcher para esta unidad.

El manifiesto OEM pide permisos privilegiados, incluida escritura de ajustes seguros. En el dispositivo, el paquete está instalado con UID compartido `android.uid.system` (1000); el launcher corre con UID 10074. Añadir esos permisos al manifiesto del launcher no le daría el UID ni la firma del fabricante.

## Identidad y alcance de la verificación

El usuario confirma que el dispositivo conectado es un HY300 Pro y que el fabricante entrega una identificación de vendor incorrecta. ADB informa `ADT-3/rk3566_rgo/adt3`, Android SDK 30 y `persist.sys.prj.type=hy260`; su shell devuelve UID 0. Estos identificadores reflejan lo que publica el firmware y se registran sin sustituir la identificación física confirmada por el usuario.

En la verificación del 2026-11-02, la app se compiló e instaló correctamente sin cambiar el launcher predeterminado. La pantalla nativa leyó zoom `5/20`, mostró “Conectado” y detectó la ausencia del nodo de trapezoide manual. Con D-pad se abrió la vista de “Cuatro esquinas”; la captura confirma el patrón 9 × 5 a pantalla completa y la guía compacta con iconos abajo. La jerarquía UI confirmó foco en el editor. Con la esquina inferior izquierda activa, flecha derecha cambió X de 120 a 121 sin pulsar Enter y flecha izquierda la restauró a 120, verificado por `getprop`; Enter solo cambia la esquina activa. Una prueba anterior sobre la esquina superior izquierda también cambió X `133 → 134 → 133` sin Enter. No se activó la corrección automática. El paso de un punto es demasiado pequeño para afirmar un cambio óptico visible; falta esa confirmación visual. Capturas: `artifacts/projector-image-overview.png` y `artifacts/projector-image-controls.png`.

En una verificación posterior del mismo día se repitió el movimiento con `BL_X=120`. Al principio el editor estaba visible, pero la jerarquía mostraba el foco en el botón “Ajustes”; la flecha derecha no cambió la propiedad. Se volvió al resumen y se reabrió “Cuatro esquinas” usando D-pad; esta vez la jerarquía confirmó el foco en el editor. Sin pulsar Enter, flecha derecha dejó `BL_X=121` en la primera lectura de la propiedad y flecha izquierda lo restauró a `120`; `BL_Y` permaneció en `125`. Esto confirma que la escritura es inmediata cuando el editor recibe el foco y también que una flecha puede ignorarse si el foco queda detrás del overlay.

El indicador superior **“Conectado”** describe el estado del backend ADB después de leer correctamente los controles del firmware; no es un indicador de que el último movimiento ya se aplicó. En una prueba anterior, `TL_X` pasó de `25` a `26` y se restauró a `25` con flechas, sin Enter. La medición desde el host incluyó el envío de una tecla por ADB (1,13 s); el valor ya había cambiado al primer sondeo posterior, unos 1,19 s desde el inicio de esa operación. Esa cifra está dominada por el inyector ADB y no mide de forma limpia la latencia de un mando físico. El firmware confirmó los valores, pero esto no mide el retraso óptico de la imagen proyectada.

En la actualización del editor instalada el 2026-11-02 se reforzó el foco del panel y se añadió feedback en la cápsula inferior: “Aplicando…” al recibir una flecha y “Enviado · X, Y” al completar el comando ADB/SurfaceFlinger. Con el editor enfocado, `TL_X` cambió de `26` a `27` por flecha derecha, sin Enter, y volvió a `26` por flecha izquierda. Luego Enter seleccionó la esquina superior derecha; flecha derecha cambió `TR_X` de `43` a `42` sin otro Enter y flecha izquierda lo restauró a `43`. La jerarquía UI confirmó el foco en el editor. “Enviado” confirma que el comando terminó sin error detectado, no la latencia óptica de la proyección.

En la actualización del 2026-11-02, el control de zoom de la pantalla Imagen se cambió de botones menos/más a un slider nativo con pasos de una unidad. La jerarquía UI confirmó que recibe foco D-pad; en el HY300 Pro conectado, el ajuste pasó de `17/20` a `18/20` con Derecha y volvió a `17/20` con Izquierda. La propiedad `persist.sys.zoom_value` cambió `3 → 2 → 3`, equivalente a esos valores de zoom; la aplicación real de la transformación queda sujeta al firmware. La cápsula del slider tiene un foco redondeado y un riel fino; no se añadió ninguna dependencia.

En la actualización del 2026-11-02 se retiró de la pantalla Imagen la vista previa geométrica ilustrativa porque era estática y no representaba el estado óptico real. Las opciones y el slider ahora usan una sola columna amplia; la retícula calibrada permanece dentro del editor Cuatro esquinas. El asistente de medidas muestra un esquema de referencia 5 × 3; es una guía para tomar medidas, no una vista previa óptica del resultado. Se comprobó en el proyector que el foco D-pad aún alcanza las opciones y el slider, y que la retícula se abre con foco en la esquina superior izquierda.

En la actualización del 2026-11-02, se verificó la composición en el HY300 Pro conectado. Con zoom `20/20` y coordenadas inicialmente en cero, se ajustó temporalmente `TL_X=20`; al cambiar zoom a `19/20`, las coordenadas quedaron `TL=(28,8)` y las otras tres esquinas `(8,8)`. La diferencia horizontal de 20 unidades se conservó mientras el zoom añadió el mismo margen a todos los lados. Después se restauró zoom `20/20` y se confirmó con `getprop` que las ocho coordenadas y `persist.sys.zoom.value` volvieron a cero. Esta verificación confirma la composición numérica del firmware; no mide por sí sola el resultado óptico proyectado.

El ADB local, las propiedades privadas y la transacción SurfaceFlinger son específicos de este firmware. La app comprueba root antes de habilitar cambios; esto no es un método portable a otros dispositivos Android.

En la actualización de calibración visual del 2026-11-02, se instaló el APK de depuración en el HY300 Pro conectado sin cambiar la app de inicio predeterminada. La captura del patrón confirma una retícula fija 16:9 de 9 × 5 con el marco azul central casi cuadrado. Con `TL_X=87`, una flecha cambió la propiedad a `92`; Atrás la restauró a `87` y las ocho coordenadas quedaron iguales al estado inicial. También se comprobó que **Aplicar** conserva un movimiento; luego se revirtió mediante otra sesión y se confirmó nuevamente el estado inicial. La captura de ADB valida la interfaz digital, no la forma óptica ni que las celdas se vean cuadradas sobre la pared.
