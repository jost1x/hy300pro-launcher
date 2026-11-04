# HY300 Launcher

Launcher minimalista para el proyector HY300 Pro. Está escrito en Kotlin con vistas nativas de Android; no usa Jetpack Compose ni bibliotecas externas.

## Funciones

- Descubre aplicaciones con categorías Leanback TV o Launcher.
- Abre apps con OK y navegación mediante D-pad.
- Mantén OK sobre una aplicación para añadirla o quitarla de favoritos.
- Entra en **Editar favoritos** y usa izquierda/derecha sobre un favorito para cambiar su orden.
- Abre los ajustes del sistema desde el acceso superior.
- Ajusta orientación, zoom y cuatro esquinas desde Imagen, con retícula 16:9 y restauración al cancelar.
- Los controles de imagen funcionan sin ADB mediante acceso directo al firmware y un servicio propio instalado aparte.

## Compilar

Requiere JDK 17 y Android SDK Platform 35.

```bash
./gradlew :app:assembleDebug
```

El APK de depuración queda en `app/build/outputs/apk/debug/app-debug.apk`. Puedes instalarlo con Android Studio o `adb install -r app/build/outputs/apk/debug/app-debug.apk`. La instalación y la selección inicial del launcher requieren acceso administrativo al dispositivo; las funciones del launcher ya no invocan ADB en tiempo de uso.

## Servicio de imagen

Zoom y cuatro esquinas requieren el APK complementario **Control de imagen HY300 Pro**, firmado para el firmware verificado. La orientación se controla directamente. El launcher conserva su firma y datos; no utiliza la aplicación OEM para estas operaciones. El botón de apagado también abre la confirmación del sistema mediante el servicio firmado; ya no requiere ADB durante el uso. La confirmación se verificó en el HY300 Pro y se canceló para mantenerlo encendido.

Consulta [instalación, firma y verificación en el proyector](docs/integrated-image-control.md).

## Comprobaciones antes de cada commit

Usamos [pre-commit](https://pre-commit.com/) para revisar espacios, finales de archivo, sintaxis YAML/JSON/XML, conflictos de Git, nombres de archivos, archivos nuevos mayores de 1 MiB y claves privadas. Estos hooks no compilan Android ni verifican el comportamiento del proyector.

Instala la herramienta una vez y activa el hook en cada clon:

```bash
pipx install pre-commit
pre-commit install
pre-commit run --all-files
```

Si un hook corrige archivos, revisa los cambios y vuelve a añadirlos al commit. Las versiones de los hooks están fijadas en `.pre-commit-config.yaml`.

## Kotlin y Java

La interfaz, la coordinación del launcher y el cliente del servicio están escritos en Kotlin. El puente de orientación, el servicio de geometría, el acceso compartido a propiedades y la prueba aislada están en Java.

Java se utilizó para trasladar las llamadas de bajo nivel verificadas en el firmware y mantener el servicio y la prueba aislada sin el plugin ni el runtime de Kotlin. El puente de orientación comenzó como un punto de entrada `app_process` y después pasó a ejecutarse directamente desde el launcher.

No es un requisito del HY300 Pro: Kotlin también puede utilizar Binder, AIDL y reflexión. Mantener esas clases en Java fue una decisión de implementación y reutilización de código ya probado, no una necesidad de permisos ni una ventaja de rendimiento demostrada. Se pueden migrar a Kotlin si se prefiere uniformidad.
