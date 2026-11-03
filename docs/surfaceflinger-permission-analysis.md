# Permiso de geometría y firma de plataforma — HY300 Pro

Inspección del 2026-11-03. Solo lectura: se extrajeron binarios/certificados y se consultó PackageManager. No se concedieron permisos, se modificaron particiones, se cambiaron firmas de aplicaciones instaladas ni se alteraron ajustes de imagen.

## Prueba posterior completada — 2026-11-03

La hipótesis de firma coincidente ya fue probada: el APK aislado recibió `HARDWARE_TEST`, ejecutó 1050 con UID 10052 y restauró la geometría y las 15 propiedades iniciales. Ver [resultados y límites](signed-image-probe-verification.md). Las secciones siguientes documentan el análisis previo; las menciones a «prueba pendiente» quedan superadas por esta verificación. La integración en el backend habitual aún no se ha realizado.

## Hallazgo

La operación de geometría **1050** de este firmware exige **UID 1000 o `android.permission.HARDWARE_TEST`** en la ruta de extensiones de SurfaceFlinger. El rechazo observado desde el launcher no se resuelve solicitando únicamente `ACCESS_SURFACE_FLINGER`.

PackageManager declara tanto `HARDWARE_TEST` como `ACCESS_SURFACE_FLINGER` con protección **signature**, no `signature|privileged`. Por ello, ni `pm grant` de permisos de ejecución ni copiar una app a `priv-app` constituyen una solución para estos permisos de firma.

El certificado de `framework-res.apk` y el de la app OEM coinciden con el certificado **platform de pruebas de AOSP**. Esto permite plantear una aplicación propia con firma coincidente y permiso `HARDWARE_TEST`, sin requerir UID compartido de sistema. La concesión y la operación aún deben verificarse instalando una prueba aislada; no se presentan como ya demostradas.

## Evidencia del binario exacto

Archivo extraído: `/system/lib/libsurfaceflinger.so`.

SHA-256:

```text
52b91851efe1153e93a2e1efb9bb5e560e79004f79f48ae345f330cf6277a984
```

El archivo es ELF32 ARM. Los símbolos comprimidos en `.gnu_debugdata` identifican `SurfaceFlinger::onTransact` en `0x000c6078`. Se examinó el código Thumb y su tabla de saltos:

- `0xc6372` admite el rango de códigos 1000–1050 en la comprobación inicial.
- Tras el despacho de `BnSurfaceComposer` y la comprobación del token de interfaz, `0xc6178` compara el UID llamante con **1000**.
- Para otro UID, `0xc6184` llama a `PermissionCache::checkCallingPermission` con el objeto `String16` de dirección `0x167c1c`.
- El inicializador `_GLOBAL__sub_I_SurfaceFlinger.cpp` en `0xd60c0` construye ese objeto a partir de la cadena **`android.permission.HARDWARE_TEST`**, en `0x57f9a`.
- La entrada de tabla para 1050 conduce a `0xc6420`, donde el manejador lee los ocho valores `float` que usa la transformación de cuatro esquinas.

Esto identifica la comprobación en el binario del HY300 Pro, sin extrapolar el código de otro proyector. Los nombres aproximados que muestra objdump entre símbolos públicos no se usaron para identificar funciones: se contrastaron con los símbolos comprimidos y las referencias a datos.

PackageManager devolvió para ambos permisos:

```text
sourcePackage=android
uid=1000 gids=null type=0 prot=signature
```

La sesión anterior ya había confirmado el rechazo de 1050 desde el proceso real del launcher con UID 10074 y contexto `untrusted_app`. SELinux permisivo no elimina este control de permisos dentro de SurfaceFlinger.

## Comparación del certificado público

Se compararon certificados públicos, no solo el nombre del firmante:

| Origen | SHA-256 del certificado |
| --- | --- |
| `/system/framework/framework-res.apk`, extraído del equipo | `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8` |
| `Ajustes-del-proyector.apk`, extraído anteriormente del equipo | Mismo digest |
| `platform.x509.pem`, etiqueta AOSP `android-11.0.0_r1` | Mismo digest |

La coincidencia hace viable investigar la firma de plataforma de pruebas para este firmware concreto. No se dedujo de `ro.build.tags` ni del nombre genérico «Android» del certificado. No se incorporaron claves de firma ni APKs generados al repositorio.

## Próxima prueba concreta

1. Crear un APK de diagnóstico con **identificador distinto** al launcher, sin `sharedUserId`, sin actividad Home y solicitando únicamente el permiso de firma necesario, `HARDWARE_TEST`.
2. Firmarlo para este perfil de firmware usando material de pruebas de AOSP gestionado fuera del repositorio. Verificar el certificado del artefacto antes de instalarlo.
3. Instalarlo junto al launcher y comprobar con PackageManager y desde su proceso que el permiso está concedido y que su UID sigue siendo el de una app independiente.
4. Guardar la geometría actual y enviar primero **la misma matriz** a SurfaceFlinger. Si se acepta, probar una variación mínima con restauración y comparación de propiedades. Un resultado Binder satisfactorio todavía requiere observar la imagen para validar el efecto óptico.
5. Si funciona, elegir la integración y verificar reconexión, funcionamiento después de reiniciar y uso sin ADB. El apagado de ADB no forma parte de esta inspección; se hará con recuperación disponible.

La autorización de esta revisión cubrió inspección. La instalación de esta nueva prueba con firma de plataforma todavía no se realizó.

## Integración si la prueba funciona

### Componente auxiliar propio

Mantener el launcher con su firma actual y añadir un pequeño servicio propio, instalado como APK normal y firmado para el firmware, que ejecute exclusivamente las operaciones de geometría autorizadas. Conectar mediante Binder y validar UID, paquete y certificado del launcher en cada operación; ambos APKs tendrían firmas diferentes, por lo que un permiso `signature` compartido entre ellos no basta como autenticación. No ofrecer ejecución de shell ni acceso arbitrario a propiedades.

Esta variante mantiene los datos y las actualizaciones actuales del launcher, y no depende de la app OEM. El servicio puede iniciarse mediante enlace explícito desde el launcher; no necesita un daemon root iniciado por `init` si el permiso de firma basta. La orientación puede continuar por acceso directo dentro del launcher, ya comprobado.

### Un único APK del launcher

Firmar el propio launcher para este firmware y solicitar `HARDWARE_TEST` evitaría un APK auxiliar. Sin embargo, su instalación actual tiene otra firma: **no puede sustituirse mediante una actualización normal**. Requiere diseñar una migración de datos y firma, o usar otra identidad de paquete; no se debe resolver desinstalando la app existente sin gestionar esa migración.

## Decisión actual

Antes de estudiar modificaciones de firmware o un servicio root, conviene validar el APK aislado con firma coincidente. Es una alternativa concreta descubierta mediante evidencia del equipo; aún no es una integración probada. La dependencia quedaría en el servicio y la firma de este firmware, no en ADB/Magisk durante la ejecución. No demuestra funcionamiento en otro firmware, con otra firma de plataforma o tras retirar Magisk y cambiar las políticas del sistema.

Referencias primarias:

- [Certificado público de plataforma de AOSP para Android 11](https://android.googlesource.com/platform/build/+/refs/tags/android-11.0.0_r1/target/product/security/platform.x509.pem).
- [Firma de versiones de Android y claves de prueba](https://source.android.com/docs/core/ota/sign_builds).
- [Protección de permisos Android](https://developer.android.com/guide/topics/manifest/permission-element).
