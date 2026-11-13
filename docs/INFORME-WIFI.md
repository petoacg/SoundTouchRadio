# Informe de viabilidad y diseño: conectar el SoundTouch a la Wi-Fi desde la app

> **Estado: implementado en la 1.4.0** según este diseño (Vincular altavoz → «Configurar la Wi-Fi del altavoz»).
>
> **Corrección en la 1.6.0:** la vía `WifiNetworkSpecifier` descrita abajo **no funciona** con el SoundTouch 10 y el Pixel 10 (Android 17). Entra en bucle en «Obteniendo dirección IP…» y nunca muestra el aviso «sin acceso a Internet, ¿mantener la conexión?». La app de Bose no conecta el móvil ella misma: manda al usuario a Ajustes de Wi-Fi y le pide responder **Sí** a ese aviso (capturas del usuario, 9 de noviembre de 2026). Desde la 1.6.0, la vía principal es esa: panel de Wi-Fi de Android + «Sí» + detección continua (cada 2 s, 3 min). La conexión automática queda como opción secundaria.
>
> **Verificado en la 1.6.1 (9 de noviembre de 2026),** con el SoundTouch 10 (firmware 27.0.6) reseteado y un Pixel 10 con Android 17, sin cuenta de Bose: modo configuración → panel de Wi-Fi + «Sí» → detección → red de casa → envío → el altavoz se une → vinculación → suena RNE Radio 5. El protocolo de la tabla de abajo funciona tal como se describe, con un matiz: el altavoz tarda más de 4 s en contestar al escaneo y al envío de credenciales. La 1.6.0 cortaba a los 4 s; la 1.6.1 espera 25 s y 40 s.

Anexo a la auditoría `/impeccable audit` (SoundTouch Radio 1.3.1). Altavoz de referencia: SoundTouch 10, firmware 27.0.6.

## Veredicto

**Viable, sin riesgo técnico pendiente; utilidad moderada.** *(Actualizado: ver «Evidencia sin cuenta» y «Utilidad» más abajo).* El SoundTouch 10 admite configurar la Wi-Fi por la red local en su **modo punto de acceso (AP)**. Lo documenta la comunidad, que lo ha verificado en un ST10, y existe una implementación de referencia en Go (`gesellix/Bose-SoundTouch`, `pkg/service/setup/wifi_provision.go`).

## Evidencia sin cuenta (8 de noviembre de 2026)

El usuario usa siempre el altavoz **sin cuenta de Bose**, y su `/info` lo confirma: el campo `margeAccountUUID` está vacío. Con ese mismo SoundTouch 10 (firmware 27.0.6) se ha comprobado la vinculación completa (API 8090 y **reproductor UPnP 8091**) y la reproducción confirmada. Funcionar sin cuenta **no impide** nada de lo que usa esta app.

**Reset posterior al apagado, también verificado:** el usuario ha reseteado el altavoz varias veces en la semana del 8 de noviembre de 2026, ya sin la nube de Bose, y lo ha vuelto a conectar a la Wi-Fi **sin cuenta** con la app oficial de Bose recién instalada y sin datos previos. Un SoundTouch 10 reseteado tras el apagado se configura y funciona sin cuenta. Ya no queda ningún riesgo técnico abierto para esta función.

## Utilidad

La app oficial de Bose **todavía hace este paso** sin nube ni cuenta. Por tanto, esta función no desbloquea nada nuevo; lo que aporta es:
- **Independencia:** si la app de Bose desaparece de las tiendas o deja de funcionar en versiones futuras de Android, el altavoz se podría seguir configurando con una app libre.
- **Un solo flujo:** reset → Wi-Fi → vincular → sonar, todo en la misma app y con el mismo lenguaje visual.
- **Vía distinta:** el modo AP por HTTP no depende del Bluetooth de configuración propietario de Bose («Gabbo» por BLE), que no está documentado.

## Cómo funciona (protocolo)

| Paso | Qué ocurre | Fuente |
|---|---|---|
| 1. Modo configuración | Tras un reset de fábrica (ST10: mantener **Preset 1 + Volumen −** 10 s), el indicador Wi-Fi queda ámbar fijo y el altavoz crea una red **abierta** «Bose SoundTouch XXXX» | gesellix, DEVICE-INITIAL-SETUP |
| 2. Conectarse a esa red | El altavoz está en `192.0.2.1` y da al móvil `192.0.2.2` por DHCP | Verificado en ST10 (comunitario) |
| 3. Redes visibles (opcional) | `POST :8090/performWirelessSiteSurvey` con `<PerformWirelessSiteSurvey timeout="5"/>` devuelve las redes que **ve el altavoz**, con su intensidad | Comunitario; formato exacto de la respuesta sin documentar |
| 4. Credenciales | `POST :8090/addWirelessProfile` con `<AddWirelessProfile><profile ssid="…" password="…" securityType="wpa_or_wpa2"/></AddWirelessProfile>` | Comunitario + implementación de referencia |
| 5. Cerrar la configuración | `POST :8090/setup` con `<setupState state="SETUP_WIFI_LEAVE"/>` | Comunitario |
| 6. Se une a casa | Deja el modo AP y entra en la red de casa en unos 15–30 s | Comunitario |

**Detalle empírico importante:** el primer envío de credenciales suele agotar el tiempo de espera porque el altavoz aún no está listo. Un segundo intento, 2 s después, funciona. La app lo reintentaría sola.

**Alternativa que ya existe:** en modo AP, el propio altavoz sirve un formulario web en `http://192.0.2.1`. Con el móvil conectado a su red, se puede configurar desde el navegador sin ninguna app.

## Cómo encaja en Android (Pixel 10, Android 17)

- **Unirse a la red del altavoz:** `WifiNetworkSpecifier` (Android 10+), con prefijo de SSID «Bose SoundTouch». Android muestra su propio diálogo de «Conectar con dispositivo». La app habla con `192.0.2.1` **solo a través de esa red**, usando su `socketFactory`, sin tocar la Wi-Fi de casa.
- **Permiso nuevo:** `NEARBY_WIFI_DEVICES` (Android 13+, con `neverForLocation`, sin pedir ubicación).
  - **Cuidado:** pertenece al mismo grupo «Dispositivos cercanos» que el permiso de red local que rompió la 1.0.0. Son permisos distintos y este sí está documentado para este uso, pero se probaría primero en tu Pixel.
- **Lista de redes:** la del escaneo **del altavoz**. Así solo aparecen las redes que el altavoz puede ver: el SoundTouch 10 trabaja en 2,4 GHz y una red solo de 5 GHz no saldría. Si el formato de respuesta no se puede leer, queda la entrada manual del nombre de red.
- **Después:** la app suelta la red del altavoz, el móvil vuelve a la de casa y **enlaza sola con la vinculación de 3 pasos que ya existe** (mDNS, API 8090, UPnP 8091).
- **Android 9 o anterior:** sin `WifiNetworkSpecifier`, se guía al usuario para conectarse a mano en Ajustes. No afecta a tu Pixel.

## Riesgos y límites

| Riesgo | Gravedad | Mitigación |
|---|---|---|
| Altavoz reseteado tras el apagado de la nube, sin cuenta | **Resuelto y verificado con esta app (1.6.1)** (resets reales tras el apagado, sin cuenta) | La comprobación de 3 pasos lo confirma igualmente al final. |
| La contraseña viaja por una red abierta, en HTTP sin cifrar (unos 30 s) | Media | Es como lo hacía la app oficial. Se avisa en pantalla; conviene hacerlo con el móvil cerca del altavoz. |
| Red de casa solo de 5 GHz | Media | La lista del altavoz solo muestra lo que puede usar. Mensaje explícito si la red elegida no aparece. |
| Permiso «Dispositivos cercanos» | Baja | Se pide solo al entrar en esta función, nunca al arrancar la app. |
| El primer envío falla por tiempo | Baja | Reintento automático, como la implementación de referencia. |

## Diseño («Radio Braun», Material 3)

- **Entrada:** en «Vincular altavoz», la sección «¿Altavoz recién reseteado?» pasa de ser solo texto a ofrecer el botón **«Configurar la Wi-Fi del altavoz»**, con la etiqueta «Experimental».
- **Asistente en una pantalla:** la misma lista de pasos con luces piloto que la vinculación (gris pendiente, verde correcto, rojo fallo con causa y acción):
  1. **Modo configuración:** instrucciones para el ST10 y confirmación «El indicador Wi-Fi está ámbar fijo».
  2. **Conectar con el altavoz:** diálogo del sistema. Luz verde al alcanzar `192.0.2.1:8090/info`.
  3. **Tu red:** lista de las redes que ve el altavoz, con intensidad en barras y candado. Contraseña con botón para mostrarla.
  4. **Enviando y esperando:** cuenta atrás de 30 s, con la rejilla de perforaciones en barrido.
  5. **Encontrar y vincular:** se lanza sola la vinculación existente, y termina en «Usar este altavoz».
- **Una sola acción verde por paso:** «Conectar», «Enviar a SoundTouch» o «Usar este altavoz».
- **Escape siempre visible:** «Hacerlo desde el navegador del altavoz» abre `http://192.0.2.1` con la red del altavoz activa.
- **Lo que no promete:** recuperar presets, cuenta de Bose ni servicios en la nube. Solo meter el altavoz en tu Wi-Fi para controlarlo con esta app.

## Coste estimado

- **Código:**
  - Petición de red con `WifiNetworkSpecifier` y cliente HTTP atado a esa red.
  - Tres llamadas: escaneo, credenciales y fin de configuración.
  - Pantalla de asistente que reutiliza la lista de pasos y la vinculación existentes.
- **Tests:** contra un «altavoz en modo AP» simulado. Cubren el escaneo, las credenciales con caracteres especiales (`&`, `"` en la contraseña, ya escapados por el XML), el reintento tras un fallo por tiempo y la transición a la vinculación.
- **Sin poder verificar aquí:** el comportamiento real del modo AP de tu altavoz. Solo se puede probar con un SoundTouch reseteado, idealmente uno de repuesto.

## Recomendación

1. **Ahora mismo, sin código:** si alguna vez reseteas el altavoz, conéctate a su red «Bose SoundTouch…» y abre `http://192.0.2.1` en el navegador del móvil. Es el formulario del propio altavoz. Después, «Vincular altavoz» en esta app.
2. **Mientras la app de Bose siga funcionando, es una mejora de independencia, no una necesidad.** Si se implementa, puede ir sin la etiqueta «Experimental» en lo relativo a la cuenta. Solo queda por comprobar en la práctica el modo AP por HTTP desde Android, que es la vía distinta a la de la app de Bose.
3. **Prioridad sugerida:** media-baja, por detrás de confirmar el despertador (pruebas 32 y 34).

## Fuentes
- gesellix/Bose-SoundTouch: `docs/content/docs/guides/DEVICE-INITIAL-SETUP.md`, `guides/SURVIVAL-GUIDE.md`, `appendix/UNIMPLEMENTED-ENDPOINTS.md` y `pkg/service/setup/wifi_provision.go` (https://github.com/gesellix/Bose-SoundTouch)
- soundtouch-localradio, sobre el reset de fábrica: https://github.com/nhouba/soundtouch-localradio
