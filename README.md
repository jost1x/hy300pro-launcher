# HY300 Launcher

Launcher minimalista para el proyector HY300 Pro. Está escrito en Kotlin con vistas nativas de Android; no usa Jetpack Compose ni bibliotecas externas.

## Funciones

- Descubre aplicaciones con categorías Leanback TV o Launcher.
- Abre apps con OK y navegación mediante D-pad.
- Mantén OK sobre una aplicación para añadirla o quitarla de favoritos.
- Entra en **Editar favoritos** y usa izquierda/derecha sobre un favorito para cambiar su orden.
- Abre los ajustes del sistema desde el acceso superior.

## Compilar

Requiere JDK 17 y Android SDK Platform 35.

```bash
./gradlew :app:assembleDebug
```

El APK de depuración queda en `app/build/outputs/apk/debug/app-debug.apk`. Para probar en un dispositivo conectado por ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Después selecciona **HY300 Launcher** como aplicación de inicio en Android. El comportamiento del firmware debe confirmarse en el proyector real.
