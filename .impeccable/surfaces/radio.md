# Surface: Radio (app Android completa)

Mode: Operate. Build path: code-led.

## Direction contract

THESIS: la app es un aparato de radio de sobremesa bien diseñado, no un reproductor de streaming. Rechaza la plantilla del sector (fondo negro, carátula gigante, barras de ecualizador neón) y también su opuesto (radio retro de madera y cromados).

OWN-WORLD: gris frío de carcasa de aparato (claro #ECEDEB, oscuro #1B1C1E), texto casi negro, un único color de mando: verde piloto Braun (#2A6F47 claro / #8BD5A2 oscuro), usado solo para la acción principal y el estado «sonando confirmado». Rojo solo para errores. Grotesca del sistema con cifras tabulares. Formas redondeadas moderadas; mandos circulares. Rejilla de 8 dp, orden estricto, nada decorativo.

STORY: el usuario ve qué altavoz controla y qué suena, toca una emisora, y la luz piloto verde solo se enciende cuando el altavoz confirma que suena; si no, la app explica la causa y el siguiente paso.

FIRST VIEWPORT: barra superior «Radio» con chip del altavoz activo (luz piloto) y menú. Lista de emisoras en filas con nombre y metadatos técnicos alineados a la derecha. Abajo, panel «Ahora suena» elevado: rejilla de perforaciones de altavoz a la izquierda, nombre de emisora y estado a la derecha, botón circular Stop, mute y slider de volumen. FAB «Añadir emisora» encima del panel.

FORM: «Radio Braun», candidato 1 de mi lista ordenada (pick del usuario frente a la asignación RDS). seed: seed:d12b0ed335807db0

SIGNATURE MOVE: la rejilla de perforaciones del altavoz es el indicador real: los puntos se encienden en verde en proporción al volumen, laten suavemente al confirmarse la reproducción (estático si «Quitar animaciones»), barren una fila mientras carga y quedan grises al detenerse.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
