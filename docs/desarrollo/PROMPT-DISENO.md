/impeccable Diseña y aplica la estética «radio moderna» de SoundTouch Radio siguiendo la dirección **Radio Braun**, ya elegida y registrada en `.impeccable/surfaces/radio.md`. No repitas la ronda de direcciones: el contrato manda. Modo Operate, construcción code-led. Lee `reference/craft-floor.md` antes de tocar la UI y termina con la revisión final y `DESIGN.md`.

## Regla de oro (modo Operate)

La estructura, la navegación y los controles son **Material 3 nativo**: TopAppBar, listas, FAB, ModalBottomSheet, Slider, Snackbar y diálogos de Material. El mundo Braun solo aporta cuatro cosas:
1. Paleta.
2. Tipografía.
3. Densidad.
4. Un gesto propio.

Nada de dibujar perillas, carcasas ni texturas de plástico: eso sería un disfraz del aparato, no una app.

## Dirección elegida: Radio Braun

- **Paleta** (roles de Material, nunca hex sueltos en las pantallas):
  - Fondo de carcasa gris frío: `#ECEDEB` en tema claro, `#1B1C1E` en oscuro.
  - Contenedores en escalones tonales del mismo gris; texto casi negro o casi blanco.
  - **Un único color de mando**, verde piloto: `#2A6F47` en claro, `#8BD5A2` en oscuro. Solo para la acción principal y para «sonando confirmado».
  - Rojo solo para errores; ámbar solo para avisos (https, HLS).
  - El color dinámico del fondo de pantalla queda como opción en Ajustes, desactivado por defecto.
- **Tipografía:** la grotesca del sistema (Roboto), sin fuentes descargadas. Escala de Material en sp. Cifras tabulares (`tnum`) en kbps, porcentajes de volumen e IPs.
- **Forma y densidad:**
  - Rejilla de 8 dp, márgenes generosos, alineación estricta.
  - Radios moderados: 8, 12 y 16 dp; los mandos de reproducción son circulares.
  - Filas de emisora de 64–72 dp, con los metadatos técnicos (códec · kbps · http/https) alineados a la derecha.
- **Gesto propio, la rejilla de perforaciones del altavoz.** Es un indicador real del panel «Ahora suena», dibujado con un `Canvas` de unas 6×6 perforaciones:
  - Los puntos encendidos en verde son proporcionales al volumen.
  - Laten suavemente solo cuando el altavoz **confirma** que suena.
  - Barren por filas mientras carga.
  - Quedan grises si está detenido y con contorno rojo si hay error.
  - Con «Quitar animaciones» activado, todo es estático.
  - Lleva una descripción para lectores de pantalla («Volumen 35 %, sonando»).
- **Luz piloto:** un punto verde en el chip del altavoz activo y en la emisora que suena. Mismo significado en todas partes: confirmado por el altavoz.
- **Movimiento:** Material estándar (fade-through entre pantallas, transformación de contenedor del FAB al editor), con duraciones cortas y respetando «Quitar animaciones».
- **Disciplinas que conviene mantener** (tomadas de los mundos descartados en la ronda):
  - La acción destructiva va aislada: Borrar nunca está junto a Reproducir.
  - Los metadatos se alinean a la derecha con cifras tabulares.
  - Un cambio de estado restiliza la fila sin mover la lista.

## Primera pantalla

1. Barra superior «Radio» con el chip del altavoz activo (luz piloto y nombre) y un menú: Importar, Exportar JSON, Exportar M3U, Diagnóstico y Ajustes.
2. Lista de emisoras.
3. Panel «Ahora suena» con elevación tonal, anclado abajo: rejilla de perforaciones a la izquierda; nombre de la emisora y estado en una línea a la derecha; botón circular Stop (y Pausa si el altavoz la ofrece), mute y slider de volumen.
4. FAB «Añadir emisora» por encima del panel.

**Estados que hay que diseñar:**
- Buscando altavoces.
- Ninguno encontrado, con causas y botón «Añadir IP».
- Sin emisoras, con «Añadir» e «Importar».
- Estados de reproducción: cargando, sonando, detenido, en reposo y otra fuente.
- Error con causa y una acción: «Probar versión http», «Encender», «Reintentar» o «Diagnóstico».

**Comprobaciones:** tema claro y oscuro, escala de fuente 1.3, objetivos táctiles de 48 dp, edge-to-edge con insets y gesto Atrás predictivo.

## Opciones de la ronda (registro; adoptables si cambias de idea)

| Opción | Qué es | Veredicto |
|---|---|---|
| **Radio Braun** (elegida) | Gris de aparato, un solo color de mando y la rejilla del altavoz como indicador | Elegida por el usuario. Riesgo: minimalismo familiar |
| Pantalla RDS (la asignada por la tirada) | Panel LCD ámbar con el nombre de la emisora en 8 caracteres e indicadores que solo se encienden si son ciertos | Alternativa completa. Riesgo: en tema oscuro roza el tópico de «fondo negro y acento brillante» |
| Amarillo señal (catálogo: señalización de aeropuerto) | Banda amarilla solo para lo que suena, nombre de la emisora monumental, un único siguiente paso en cada error | Competitiva: muy clara, poco «radio» |
| Material 3 estándar | Material You canónico con color dinámico | La salida estándar: la elección anterior del usuario |
| Consola oscura (catálogo) | Grafito con separaciones de 1 px | Descartada; se queda su aislamiento de lo destructivo |
| Atlas estelar (catálogo) | Jerarquía por tamaño de punto | Descartada |
| Catálogo de personajes (catálogo) | Ficha fija por elemento | Descartada; se quedan los campos fijos por fila |
| Carátula de casete (catálogo) | Tiempos alineados a la derecha | Descartada; se quedan los metadatos alineados a la derecha |
| Panel de salidas de tren (catálogo) | Filas vivas que cambian de estado sin romper la rejilla | Descartada; se queda el cambio de estado sin mover la lista |

## Ampliaciones de diseño

- **Teclas de preset 1–10:** rejilla de 5×2 de teclas circulares de 56 dp (objetivo táctil de al menos 48 dp), con el número en cifras tabulares y el nombre corto de la emisora debajo. Llena: tonal. Vacía: contorno. La que suena: luz piloto verde.
  - Pulsar reproduce; mantener pulsado abre las opciones (asignar o vaciar).
  - Se colocan entre la barra superior y la lista, como el teclado frontal de un aparato.
- **Pantalla «Vincular altavoz»:** lista de pasos (Buscar en la red → API del altavoz → Reproductor UPnP → Nombre compatible), cada uno con su luz piloto (gris, verde o rojo) y una línea de resultado en cifras tabulares (IP, puertos, firmware).
  - Tarjeta del altavoz con el modelo en grande y el botón «Usar este altavoz» como única acción verde.
  - Sin resultados: lista de causas y campo de IP. Mismo lenguaje de luz piloto que el resto de la app.
