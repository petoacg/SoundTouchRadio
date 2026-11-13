# Informe de viabilidad y diseño: temporizador de apagado («sleep»)

Anexo a la auditoría `/impeccable audit` del 8 de noviembre de 2026. Altavoz: SoundTouch 10, firmware 27.0.6.

## Veredicto

**Es viable, con una condición: el temporizador vive en el móvil, no en el altavoz.** La API local oficial de Bose (PDF «SoundTouch API WebServices v1.1.0») no tiene temporizador ni apagado programado. Lo único útil que ofrece es la tecla `POWER` (`POST /key`), que alterna entre encendido y reposo. Por tanto, el móvil tiene que estar en casa, en la Wi-Fi, en el momento de apagar.

## Cómo funcionaría (técnica)

1. Al activarlo, la app arranca un **servicio en primer plano** de tipo `connectedDevice`, el tipo que Android prevé para controlar un aparato en la red. Ese servicio muestra una notificación fija: «SoundTouch 10 se apaga a las 23:45 · Cancelar».
   - **Por qué un servicio:** una alarma normal (`AlarmManager` inexacta o WorkManager) puede retrasarse muchos minutos con el móvil en reposo nocturno (Doze). Las alarmas exactas requieren un permiso especial (`SCHEDULE_EXACT_ALARM`), denegado por defecto desde Android 14 para apps que no son de alarmas.
   - **Permisos nuevos:** `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` y `POST_NOTIFICATIONS`. Este último se pide la primera vez; si se deniega, el temporizador funciona igual, pero la notificación no se ve.
2. **Último minuto: bajada gradual del volumen.** Desde el volumen actual hasta 0, un paso cada pocos segundos (`POST /volume`).
3. **Al llegar a cero:**
   1. `Stop` por UPnP.
   2. Se **restaura el volumen original**, para que mañana no esté a 0.
   3. Se pone el altavoz en reposo con la tecla `POWER`, **solo** si `/now_playing` no está ya en `STANDBY`. `POWER` alterna, así que pulsarla con el altavoz ya en reposo lo encendería.
4. **Cancelación automática:** si antes de la hora el altavoz pasa a reposo o a otra fuente (Bluetooth, AUX), el temporizador se anula solo y avisa. No apaga algo que ya no es la radio.
5. **Si el móvil no está en la Wi-Fi a la hora:** no puede apagar. La notificación lo dice («No se pudo apagar: sin conexión con el altavoz»).

## Riesgos y límites

| Riesgo | Mitigación |
|---|---|
| Móvil fuera de casa o sin Wi-Fi a la hora de apagar | Aviso en la notificación. Es un límite inherente: no hay temporizador en el altavoz. |
| Reiniciar el móvil cancela el temporizador | Aceptable para una función de una noche; se documenta. |
| `POWER` alterna en vez de apagar | Se comprueba `/now_playing` justo antes de enviarla. |
| Ahorro de batería agresivo de algunos fabricantes | En Pixel no aplica; con un servicio en primer plano, Android no lo para. |
| Volumen a 0 al día siguiente | Se restaura el volumen original antes del reposo. |

**Sin verificar en tu altavoz:** que el SoundTouch 10 acepte `POWER` para entrar en reposo estando en la fuente UPnP. Lo dice la documentación oficial para la tecla en general; con UPnP en concreto no está probado.

## Diseño («Radio Braun», Material 3 nativo)

- **Entrada:** un botón de icono **«Temporizador»** (luna, `Bedtime`) en la fila del volumen del panel «Ahora suena», a la izquierda del silencio. Solo está activo cuando algo suena.
- **Hoja inferior «Apagar en…»:**
  - Fila de **chips de filtro** de Material: **15 · 30 · 45 · 60 · 90 min**, con los números en cifras tabulares.
  - Interruptor **«Bajar el volumen poco a poco el último minuto»**, activado por defecto.
  - Botón principal **«Iniciar»**, la única acción verde de la hoja.
  - Con un temporizador activo: «Se apaga a las 23:45» y el botón **«Cancelar temporizador»** (contorno, separado).
- **Estado en el panel:** la línea de estado añade el tiempo restante, «Sonando · MP3 · se apaga en 23 min», en cifras tabulares. El icono de la luna pasa a relleno tonal. **No se usa el verde**: en esta app, el verde significa «confirmado por el altavoz», y el temporizador es del móvil.
- **Gesto propio:** durante la bajada final, la rejilla de perforaciones se va apagando punto a punto, porque refleja el volumen real. No hace falta inventar otra animación: el indicador ya cuenta la historia.
- **Al terminar:** aviso breve (snackbar) «Apagado por el temporizador · volumen restaurado a 16» y notificación equivalente si la app está cerrada.
- **Accesibilidad:** el botón describe su estado («Temporizador: se apaga en 23 minutos»), los chips son seleccionables con rol de opción, y el tiempo restante se anuncia sin interrumpir, como región «polite».

## Coste estimado

- **Código:** servicio de temporizador, una hoja y un estado en el panel. Es de tamaño medio y reutiliza `SoundTouchApi`, `AvTransport` y la máquina de estados existentes.
- **Tests:** la lógica pura (cuenta atrás, bajada gradual, «no pulsar POWER si ya está en reposo», restaurar el volumen) se prueba con tiempo virtual. Contra el altavoz simulado: el apagado completo y la cancelación al cambiar de fuente.
- **Sin verificar hasta probarlo en tu Pixel:** el comportamiento del servicio con la pantalla apagada toda la noche.

## Recomendación

Implementarlo tal como está descrito. Es la única forma fiable sin tocar el altavoz y encaja en el diseño actual sin pantallas nuevas. Si se quiere que funcione con el móvil fuera de casa, haría falta un aparato siempre encendido en casa (ver el informe de presets físicos). Google Home no sirve para esto: no puede hablar con la API local del SoundTouch.
