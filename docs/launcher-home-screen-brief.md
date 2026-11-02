# Brief: Inicio del launcher HY300 Pro

## Marco

- Producto y audiencia: launcher minimalista para quien usa el proyector HY300 Pro desde el sofá con un control remoto.
- Plataformas y prioridad: Android Views nativo, navegación de 10 pies con D-pad.
- Postura: Android-first, con foco visual adaptado a TV.
- Tipo: pantalla de trabajo para abrir apps y acceder a las acciones principales.
- Sistema visual: `LauncherPalette` en `ui/LauncherComponents.kt`; no hay archivo DESIGN ni tokens XML.

## Pregunta principal

¿Qué app favorita puedo abrir ahora?

## Hallazgo principal

Las apps favoritas ocupan el estante principal; el foco remoto debe señalar claramente la app activa.

## Bloques

| Bloque | Pregunta que responde | Prioridad | Mantiene o elimina |
|---|---|---|---|
| Reloj y fecha | ¿Qué hora y día es? | Secundaria | Mantiene contexto rápido |
| Acciones superiores | ¿Dónde están catálogo, entradas y ajustes? | Secundaria | Mantiene accesos existentes |
| Apps favoritas | ¿Qué puedo abrir? | Principal | Mantiene banners y desplazamiento horizontal |
| Estado vacío | ¿Cómo agrego una app? | Principal cuando no hay favoritos | Lleva directamente al catálogo |

## Tarea principal

Mover el foco a una app favorita y pulsar OK. Las acciones superiores y el catálogo siguen a un movimiento de D-pad.

## Navegación

Inicio permanece en pantalla. Catálogo y ajustes son overlays; Back los cierra y devuelve el foco al control que los abrió. La app seleccionada ocupa la pantalla completa según su propio comportamiento.

## Estados

- Inicio: contenido normal y foco D-pad claramente marcado.
- Sin favoritos: botón accesible «Añadir apps favoritas» que abre el catálogo.
- Catálogo y ajustes: overlays existentes con navegación por foco y cierre con Back.
- Carga/error/offline: no son estados propios de esta pantalla; las apps se consultan localmente y los errores de apertura dan feedback desde la actividad.
- Deshabilitado: no aplica a las acciones visibles habilitadas.

## Movimiento distintivo

El anillo y el aumento moderado acompañan el foco remoto para localizar el control activo a distancia.

## Supuestos

- El control remoto es el método principal de entrada.
- Se usa la paleta oscura existente; no se añade un modo o paleta nueva.
- La zona segura del 5% se deriva del tamaño de pantalla disponible.
- La captura `launcher-current.png` se usa como referencia visual aportada, no como evidencia de ejecución en el proyector.
