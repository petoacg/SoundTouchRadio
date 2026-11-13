# Informe de viabilidad: guardar los presets 1–6 de la app en los botones físicos del SoundTouch 10

Anexo a la auditoría `/impeccable audit` del 8 de noviembre de 2026. Altavoz: SoundTouch 10, firmware 27.0.6.

## Veredicto

**No es viable hacerlo solo desde la app de forma que los botones funcionen con el móvil apagado o fuera de casa.**
Grabar los presets en el altavoz es fácil. El problema está en lo que hace el altavoz al pulsar un botón: necesita un servicio que Bose ha apagado. Hay tres caminos que sí funcionan, y todos exigen o un aparato siempre encendido en casa o modificar el altavoz.

## Por qué no basta con grabar el preset

1. **Grabar sí se puede.** La API local admite `POST :8090/storePreset` con `<preset id="N"><ContentItem source="LOCAL_INTERNET_RADIO" type="stationurl" location="…">`. *Comunitario:* no aparece en el PDF oficial v1.1.0, pero lo usa SoundTouchBridge (publicada en 2026), que lo describe como seguro.
2. **Reproducir al pulsar, no.** Para un preset `LOCAL_INTERNET_RADIO`, el altavoz pregunta a su registro de servicios (`bmxRegistryUrl`, de Bose) dónde resolver la `location`. *Comunitario, soundtouch-localradio:* «Bose's registry is gone, so presets died». Las URLs envoltorio de Bose (`content.api.bose.io/…/orion/station?data=…`) dependen de esa nube.
3. **Guardar un preset `source="UPNP"` está descartado.** *Comunitario, SoundTouchBridge:* recuperar ese preset puede bloquear el reproductor UPnP hasta reiniciar, y es justo el reproductor que usa esta app.

## Opciones

| Opción | Cómo funciona | ¿Sin móvil? | Riesgo | Esfuerzo en la app | Veredicto |
|---|---|---|---|---|---|
| **A. Puente en el móvil** | La app graba la etiqueta con `/storePreset`, escucha el WebSocket 8080 y, al detectar la pulsación, envía la emisora por UPnP (como SoundTouchBridge). | **No**: el móvil tiene que estar en casa y la app viva, con un servicio en primer plano y notificación permanente. Android puede matarlo. | Bajo para el altavoz; batería del móvil. | Medio | Funciona, pero da una experiencia poco fiable. No recomendado como única vía. |
| **B. Puente siempre encendido** | Lo mismo, pero en un aparato fijo: Home Assistant (add-on «SoundTouch Bridge»), Homey («SoundTouch Bridge») o un ESP32. | **Sí** | Bajo | Ninguno en la app, que convive con el puente. | Recomendado si ya tienes uno de esos aparatos. |
| **C. Modificar el altavoz** | `soundtouch-localradio`: por la consola telnet del altavoz (puerto 17000) instala un servidor web en el propio altavoz (:8181) y apunta `bmxRegistryUrl` a `127.0.0.1`. Después cada preset es `LOCAL_INTERNET_RADIO` con `location=/cgi-bin/s?<JSON base64>`. | **Sí**: el altavoz funciona solo. | **Medio-alto:** ejecuta comandos como root; su autor avisa de que «en el peor caso» puede dejar el altavoz inservible. Probado en un SoundTouch 10 con firmware **27.0.3**; el tuyo tiene la **27.0.6**, sin probar. Admite desinstalación. | Bajo, solo como **integración opcional**: si la app detecta `:8181` en el altavoz, puede grabar los presets 1–6 en ese formato. | El único camino autónomo. Decisión tuya, fuera del alcance acordado en PRODUCT.md. |
| **D. Emular la nube de Bose** | soundcork / AfterTouch: redirigir por DNS los dominios de Bose a un servidor local, con certificados. | Necesita un servidor siempre encendido. | Medio | Ninguno | Desproporcionado para este caso. |
| **E. Preset UPNP o envoltorio de Bose** | Ver los puntos 2 y 3 de la sección anterior. | — | Bloquea el UPnP / no suena | — | **Descartado.** |

## ¿Y TuneIn?

La app de Bose asociaba los botones a emisoras de **TuneIn**, pero un preset de TuneIn no guarda la URL del stream: guarda un **identificador de emisora** (`source="TUNEIN"`, del tipo `/v1/playback/station/s24939`) que el altavoz resolvía **a través de los servidores de Bose**. Esos servidores son justo los que se apagaron el 6 de mayo de 2026. Según la comunidad, los presets antiguos «apuntaban a un ID en la nube, no a un stream», y por eso no vuelven. TuneIn no es una vía para los botones físicos sin un servidor sustituto (opción D).

## Tu caso: tienes Google Home

**Google Home no sirve como puente (opción B).** El SoundTouch no es un dispositivo de Google Home, así que ninguna automatización suya puede activarse al pulsar un botón del altavoz: no escucha el WebSocket 8080. Tampoco consta que el editor de scripts de Google Home pueda enviar peticiones HTTP a la red local; no lo he podido confirmar, pero aunque pudiera, faltaría el disparador. Para la opción B haría falta otro aparato siempre encendido: una Raspberry Pi con Home Assistant, un móvil Android viejo enchufado o un ESP32 de pocos euros (proyecto «Bring Back Your Bose with an ESP32»).

## Recomendación

1. **Hoy no tocar los botones físicos desde la app.** Las 10 teclas de la app cubren el caso de uso con el móvil en la mano.
2. **Si quieres botones físicos sin depender del móvil:** opción **B** si tienes Home Assistant u Homey; si no, opción **C** asumiendo su riesgo.
3. **Mejora opcional para la app (bajo esfuerzo y bajo riesgo para la app):** detectar `soundtouch-localradio` (`GET http://IP:8181/registry.json`) y, solo entonces, ofrecer «Grabar presets 1–6 en los botones del altavoz» con su formato de `location`. Sin ese complemento instalado, la opción no aparece.

## Qué queda sin verificar
- Que `/storePreset` responda en tu firmware 27.0.6 (comunitario; no está en el PDF oficial).
- Que el registro BMX de Bose esté caído para tu unidad (lo afirma una fuente comunitaria; no lo he podido comprobar).
- El comportamiento de soundtouch-localradio con el firmware 27.0.6.
- Las páginas de Mast (getmast.be) y de la guía de supervivencia de gesellix no se pudieron leer: la red de este entorno no las alcanza.

## Fuentes
- SoundTouchBridge (`README.md`, `Overview.md`, `lib/soundtouch-client.js`): https://github.com/codingmammoth/SoundTouchBridge
- soundtouch-localradio: https://github.com/nhouba/soundtouch-localradio
- Hilo de Home Assistant: https://community.home-assistant.io/t/bose-soundtouch-bridge-revive-preset-buttons-after-the-2026-cloud-retirement/1009933
- Métodos de redirección (gesellix): https://gesellix.github.io/Bose-SoundTouch/docs/analysis/DEVICE-REDIRECT-METHODS/
- soundcork: https://github.com/timvw/soundcork
