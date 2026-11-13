---
name: SoundTouch Radio
description: Mando local para Bose SoundTouch con estética «Radio Braun»
colors:
  primary: "#2A6F47"
  primary-dark: "#8BD5A2"
  on-primary: "#FFFFFF"
  carcasa: "#ECEDEB"
  carcasa-dark: "#1B1C1E"
  ink: "#1A1C1D"
  ink-dark: "#E3E3E0"
  ink-muted: "#45474A"
  ink-muted-dark: "#C5C6C9"
  key-tonal: "#D9DDDF"
  key-tonal-dark: "#3F4447"
  panel: "#DFE0DE"
  panel-dark: "#2D2E30"
  outline: "#75777A"
  outline-variant: "#C5C6C4"
  warning: "#7A4600"
  warning-dark: "#FFB870"
  error: "#B3261E"
  error-dark: "#FFB4AB"
typography:
  title:
    fontFamily: "Roboto, sans-serif"
    fontSize: "22sp"
    fontWeight: 400
    lineHeight: 1.27
  station:
    fontFamily: "Roboto, sans-serif"
    fontSize: "16sp"
    fontWeight: 500
    lineHeight: 1.5
  body:
    fontFamily: "Roboto, sans-serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: 1.43
  data:
    fontFamily: "Roboto, sans-serif"
    fontSize: "12sp"
    fontWeight: 500
    fontFeature: "tnum"
  key-number:
    fontFamily: "Roboto, sans-serif"
    fontSize: "16sp"
    fontWeight: 500
    fontFeature: "tnum"
rounded:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "24dp"
  full: "9999dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "24dp"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.full}"
  stop-key:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.full}"
    size: "56dp"
  preset-key-filled:
    backgroundColor: "{colors.key-tonal}"
    textColor: "{colors.ink}"
    typography: "{typography.key-number}"
    rounded: "{rounded.full}"
    size: "56dp"
  preset-key-empty:
    textColor: "{colors.ink-muted}"
    typography: "{typography.key-number}"
    rounded: "{rounded.full}"
    size: "56dp"
  now-playing-panel:
    backgroundColor: "{colors.panel}"
    rounded: "{rounded.xl}"
    padding: "16dp"
  pilot-light:
    backgroundColor: "{colors.primary}"
    rounded: "{rounded.full}"
    size: "10dp"
---

# Design System: SoundTouch Radio

## Overview

**«Radio Braun».** La app es un aparato de radio de sobremesa bien diseñado, no un reproductor de streaming: carcasa gris fría, texto casi negro, un único color de mando y orden estricto. La estructura, la navegación y los controles son Material 3 nativo; el mundo solo aporta paleta, tipografía, densidad y un gesto propio. Rechaza la plantilla del sector (fondo negro, carátula gigante, ecualizador neón) y la radio retro de madera.

Principio rector: **el verde significa «confirmado por el altavoz»**. Nada se pinta de verde por decoración.

## Colors

### Primary
- **Verde piloto** (`#2A6F47` claro, `#8BD5A2` oscuro): acción principal (Stop, «Usar este altavoz», «Vincular»), luz piloto, puntos encendidos de la rejilla y el texto «Sonando». Contraste 5,2:1 sobre la carcasa clara y 9,9:1 sobre la oscura.

### Tertiary
- **Ámbar de aviso** (`#7A4600` / `#FFB870`): solo avisos de stream (https, HLS, redirección a https).

### Neutral
- **Carcasa** (`#ECEDEB` / `#1B1C1E`): fondo de toda la app; gris frío, sin calidez.
- **Tinta** (`#1A1C1D` / `#E3E3E0`) y **tinta atenuada** (`#45474A` / `#C5C6C9`, 7,9:1 y 10:1).
- **Panel** (`#DFE0DE` / `#2D2E30`): «Ahora suena», un escalón tonal por encima de la carcasa.
- **Tecla tonal** (`#D9DDDF` / `#3F4447`): presets asignados.

### Named Rules
- **Regla del piloto:** verde = el altavoz confirma que suena. Gris = apagado o pendiente. Ámbar = aviso. Rojo = fallo.
- **Regla del color único:** no hay un segundo acento. El color dinámico (Material You) es una opción del usuario y está desactivado por defecto.

## Typography

Roboto del sistema en todos los papeles, con la escala de Material 3 en sp, de modo que respeta la escala de fuente del sistema. Las cifras de IP, kbps, volumen, números de preset y tiempos usan cifras tabulares (`tnum`).

### Hierarchy
- Título de pantalla: titleLarge (TopAppBar).
- Encabezados de sección («Presets», «Emisoras», «En tu red»): titleSmall en tinta atenuada, con 24 dp encima y 8 dp debajo.
- Nombre de emisora: bodyLarge / titleMedium en el panel.
- Datos técnicos: labelMedium tabular, alineados a la derecha.

### Named Rules
- **Regla de las cifras:** todo número que pueda cambiar o compararse lleva `tnum`.

## Layout

Rejilla de 8 dp. Márgenes laterales de 16 dp. Presets en una rejilla de 5×2 repartida a lo ancho. La lista de emisoras queda entre los presets y el panel «Ahora suena», anclado abajo con los insets de la barra de navegación. «Añadir» va en la cabecera de «Emisoras» (sin FAB: tapaba filas sobre el panel, 1.6.2). Edge-to-edge.

## Elevation & Depth

Sin sombras decorativas: la profundidad es tonal. La carcasa, el panel (surfaceContainerHigh) y los contenedores de hojas y avisos son escalones de gris. No hay ninguna sombra (desde la 1.6.2, sin FAB).

## Shapes

Radios de 4/8/12/16/24 dp. Los mandos de reproducción y las teclas de preset son circulares. El panel «Ahora suena» tiene 24 dp arriba. Los avisos usan 12 dp.

## Components

### Buttons
- Botón principal relleno en verde piloto. Una sola acción verde por vista.
- Botones secundarios en contorno o tonales.
- Stop: botón circular de 56 dp en verde, desactivado (gris) cuando no hay nada sonando.

### Chips
- Chip del altavoz activo en la barra superior: luz piloto, nombre y flecha de desplegable. Abre la hoja de altavoces.

### Cards / Containers
- Avisos: superficie de 12 dp en tertiaryContainer (aviso) o errorContainer (fallo), con texto de causa y una única acción alineada a la derecha. Sin bordes laterales de color.

### Inputs / Fields
- OutlinedTextField de Material. URL e IP en cifras tabulares, con texto de ayuda siempre visible.

### Navigation
- Pila de pantallas con NavHost y fade-through de 200 ms (instantáneo con «Quitar animaciones»).
- Gesto Atrás predictivo activado.
- Hojas inferiores para altavoces y presets.

### Teclas de preset
- Teclas circulares de 56 dp con el número tabular dentro y el nombre (hasta 2 líneas) debajo.
- Asignada: tonal. Vacía: contorno de 1 dp. Sonando: luz piloto de 7 dp dentro de la tecla, arriba.
- Tocar reproduce; mantener pulsado abre las opciones.

### Rejilla de perforaciones (gesto propio)
- Canvas de 6×6 puntos (64 dp en el panel), que se llena desde abajo en proporción al volumen.
- **Sonando:** los puntos encendidos son verdes y laten (alfa 0,72–1, 1,4 s).
- **Cargando:** una fila verde barre la rejilla.
- **Detenido:** gris.
- **Error:** contorno rojo en todos los puntos.
- Estática con «Quitar animaciones». Tiene descripción para TalkBack.

### Encendido
- Botón ⏻ de 48 dp en la barra superior, junto al chip del altavoz (el encendido es del altavoz, no de la emisora).
- Encendido confirmado: relleno **verde** (misma semántica que la luz piloto). En reposo: contorno gris. Cambiando: anillo de progreso, no se puede pulsar, y el panel dice «Encendiendo…» o «Pasando a reposo…» con la rejilla en barrido. Sin conexión: desactivado.
- Sin diálogo de confirmación (se deshace con otro toque); un aviso breve confirma el resultado.

### Configurar la Wi-Fi
- Asistente de cinco pasos con la misma lista de luces piloto que la vinculación (componente `StepList` compartido).
- Una sola acción verde por etapa: «Conectar con el altavoz», «Enviar a SoundTouch», «Usar este altavoz».
- Redes con candado (protegida) y barras de señal de Material; «Otra red…» para redes ocultas; contraseña con mostrar/ocultar.
- Espera de 30 s con la rejilla de perforaciones en barrido y la cuenta atrás en grande, en cifras tabulares.
- Dos puertas al mismo flujo, con igual dignidad: «Conectar con el altavoz» (aviso de Android) y «Abrir ajustes de Wi-Fi» (conectarse a «Bose ST 10 (…)» y volver; la app lo detecta al volver y continúa). Tras un fallo de conexión, las dos siguen ahí («Reintentar» y «Abrir ajustes de Wi-Fi»).
- El formulario web del propio altavoz (192.0.2.1) es un último recurso, plegado bajo «Si nada de esto funciona…».

### Renombrar altavoz
- Icono de lápiz en cada altavoz de la hoja de altavoces → diálogo con el nombre; se guarda en el propio altavoz. No admite & < > (rompen la reproducción UPnP).

### Despertador
- Hora en grande (displayLarge, cifras tabulares), como un despertador de mesa; al tocarla se abre el selector de hora de Material.
- Días L M X J V S D como teclas redondas de 44 dp: tonal = suena; contorno = no suena (mismo lenguaje que las teclas de preset).
- Emisoras con su número de preset a la derecha; volumen al despertar con su cifra; interruptor «Subir el volumen poco a poco».
- En Radio, con el despertador activado: fila «7:30 · L–V · Cadena SER» con icono de alarma, encima del teclado.
- El interruptor de activación es un control Material (usa el color primario como cualquier interruptor); no es una luz piloto.
- Desde la 1.7.0, dos pestañas: «Semanal» (la rutina) y «Una vez» (fecha concreta con el selector de fecha de Material). Conviven: una alarma suelta no obliga a desmarcar la rutina. El interruptor de la barra es el de la pestaña visible, y la pestaña lleva un icono de alarma cuando la suya está activada. Días marcados: relleno tonal y borde de 2 dp en el color del texto.
- En Radio, la fila muestra la alarma que suena antes; si es «Una vez», debajo dice que después sigue la semanal.

### Buscar emisoras (1.7.0)
- Pantalla propia con campo de búsqueda, chip de país (menú; España por defecto), «Solo compatibles» (activado: oculta HLS, caídas y formatos que el altavoz no reproduce) y chips de género.
- Filas sin logotipos (son imágenes de terceros): nombre; «ES · MP3 · 128 kbps · etiquetas» en cifras tabulares; «Guardada» si ya está en la lista.
- Al tocar una fila: «Probar en el altavoz» (contorno) y «Guardar» (relleno). La luz piloto de la fila se pone verde solo si el altavoz confirma que suena.
- «Guardar» abre una hoja: solo en la lista (por defecto) o en una tecla, con las libres primero.
- «+ Añadir» de Radio abre una hoja con tres entradas: buscar en el directorio, escribir una URL o importar archivo.

### Hojas inferiores
- Siempre se abren desplegadas del todo. El margen inferior es el mayor entre la barra de navegación de la ventana principal y la de la hoja, más 16 dp.

### Temporizador
- Botón de luna (`Bedtime`) en la fila del volumen: contorno si está inactivo; relleno tonal si hay un temporizador activo. **Nunca verde:** el temporizador es del móvil, no lo confirma el altavoz.
- Hoja «Apagar en…»: chips de filtro con 15/30/45/60/90 min en cifras tabulares, un interruptor para la bajada gradual y un único botón verde «Iniciar».
- Estado en el panel: «Sonando · se apaga en 23 min». El tiempo restante sustituye al códec para no cortarse.
- Durante la bajada final, la rejilla se apaga sola porque refleja el volumen real.

### Vista ancha (≥ 600 dp)
- Izquierda (45 %): «Ahora suena» como tarjeta (24 dp en las cuatro esquinas) y debajo el teclado. Derecha (55 %): emisoras.

### Volumen (1.8.0; 1.9.0)
- Panel en dos filas (1.9.0). Arriba, la rejilla, la emisora y su estado, y los controles: temporizador, pausa y Stop, todos de 48 dp con 4 dp entre ellos. Abajo, silencio · barra larga · cifra. Sin teclas − / + (las hubo en la 1.8.x); la barra larga da el recorrido fino.
- La cifra tiene dos estados: tenue mientras está pedida y normal cuando el altavoz confirma el valor. No es verde: el verde queda para la luz piloto.
- El tamaño del paso de los botones del móvil se elige en Ajustes → Volumen (1, 2, 3 o 5).
- La barra solo se mueve arrastrando el tirador, en relativo. Un toque en la pista no salta (seguridad nocturna, 1.8.2).
- La barra usa una curva (volumen = 100·p²): el tramo bajo, el de la noche, ocupa más recorrido. Vibración en cada punto.
- Los botones de volumen del móvil mueven el altavoz de punto en punto con la app abierta y, mientras suena, también con la pantalla bloqueada (sesión multimedia remota, sin notificación).

### Pantalla Radio (1.9.0)
- Despertadores (1.9.1): una línea por cada alarma activada, ordenadas por hora. Semanal con icono de alarma; «una vez» con icono de calendario y «· una vez». La próxima va en onSurface y la otra en onSurfaceVariant.
- Etiquetas de preset siempre con dos líneas reservadas, para que las teclas queden alineadas.
- Filas de emisora: nombre y una sola línea secundaria «códec · kbps · dominio», con los avisos https y HLS en ámbar al principio. A la derecha, solo ⋮.
- Entre el encendido y el chip del altavoz, 8 dp.
- **Decisión del usuario (10 de noviembre de 2026):** el verde se queda tal como está, también en Stop, en la barra de volumen y en «Añadir». No proponerlo como hallazgo en futuras auditorías.

### Pantalla de bloqueo y persiana (1.9.1)
- Notificación multimedia de Android con la sesión de la app: título = emisora, subtítulo = «Suena en SoundTouch 10», acciones pausa/continuar y detener. Icono pequeño: la rejilla de perforaciones. Carátula: la rejilla en gris con la fila inferior verde.
- Solo mientras suena o está en pausa; al detener, desaparece.

### Emisoras solo https (1.9.6)
- Ajustes: interruptor «Emisoras que solo emiten por https», activado por defecto, con una línea que explica el puerto local.
- Diagnóstico: luz verde («activado» o «verificado») o gris («desactivado»), botón «Probar redirección» y, en el registro, líneas «PUENTE».
- Si una emisora solo https falla con el puente desactivado, el aviso de error ofrece «Activar el puente y reintentar».

### Este móvil (1.10.0)
- Chip de la barra: con «Este móvil» muestra un icono de teléfono en lugar de la luz piloto, y el texto «Este móvil». La hoja se llama «Dónde suena»: los altavoces vinculados y, debajo, «Este móvil» con la salida actual.
- El panel trata al móvil como un altavoz más: título «Este móvil · Edifier R1855DB», el mismo verde de «Sonando» (confirmado por el reproductor), pausa siempre y sin botón de encendido. Fuera de la Wi-Fi, una línea ámbar: «Usando datos móviles».

### Sonido (1.10.2)
- Botón de ecualizador en la fila de controles del panel, solo con «Este móvil».
- Hoja «Sonido»: selector segmentado con los cuatro ajustes, y «Ajuste fino» plegado con deslizadores verticales en gris (sin verde), cifra en dB arriba y frecuencia abajo, en cifras tabulares, como el ecualizador gráfico de un equipo de música.
- Despertador sin emisora: la línea en ámbar (tertiary), «sin emisora: toca para elegir una».

### Luz piloto
- Punto de 10 dp (8 dp en listas densas).
- Misma semántica en el chip, las filas, los presets, los pasos de vinculación, el banco de pruebas y el registro.

## Do's and Don'ts

### Do:
- Usar el verde solo para lo confirmado y para la acción principal.
- Dar a cada fallo su causa y una acción concreta.
- Alinear los datos técnicos a la derecha, con cifras tabulares.
- Aislar Borrar al final del menú, tras un separador y en rojo.

### Don't:
- No dibujar perillas, carcasas, plástico ni texturas: el mundo no se disfraza.
- No usar un segundo color de acento ni gradientes.
- No marcar «http» como si fuera noticia: solo se marcan https y HLS.
- No usar sombras como decoración.
