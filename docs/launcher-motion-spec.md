# Movimiento: Inicio del launcher

## Tesis

- Momento focal: el foco del D-pad resalta el control que recibirá OK.
- Continuidad: el aumento muestra qué elemento tomó el foco.
- Feedback: anillo azul de 4 dp y cambio de superficie mantienen el foco reconocible.
- Presupuesto: animación corta en el elemento enfocado; no se animan los banners ni el desplazamiento del estante.

## Filtro

| Movimiento | Propósito | Frecuencia | Decisión |
|---|---|---|---|
| Foco de tarjeta | Identifica el destino actual del control remoto | Cada movimiento del D-pad | Mínimo y ligado al estado |
| Foco del botón de estado vacío | Hace visible la selección antes de abrir el catálogo | Ocasional | Mínimo y ligado al estado |
| Tooltip existente | Nombra la acción enfocada | Ocasional | Entrada/salida corta; se oculta automáticamente |

## Contrato del foco

| Campo | Valor |
|---|---|
| Disparador | Cambio de foco D-pad |
| Elemento y propiedad | Tarjeta: escala; banner: borde, superficie y elevación |
| Inicio y fin | Escala 1.0 → 1.08; borde transparente → acento de 4 dp |
| Duración | `LauncherPalette.focusMotionDurationMs` (210 ms) |
| Interrupción | Se cancela la animación previa antes de aplicar el nuevo destino |
| Estado final | Control enfocado ampliado y anillo visible; los demás vuelven a reposo |
| Foco durante movimiento | La navegación sigue activa |
| Movimiento reducido | Android aplica la escala global de duración de animadores; el estado de foco permanece visible |
| Plataforma | Android Views para TV |

## Comprobaciones

- El movimiento tiene propósito y usa una constante de la paleta existente.
- No se verificó la reproducción en el HY300 Pro ni con «Quitar animaciones» activo.
