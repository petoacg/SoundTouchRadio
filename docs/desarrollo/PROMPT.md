/impeccable Construye desde cero, en este directorio, la app Android descrita en `PRODUCT.md` («SoundTouch Radio»): controla un Bose SoundTouch por la Wi-Fi local y le hace reproducir radios cuyas URLs introduzco yo. Sin nube, sin cuentas, licencia MIT. Actúa como ingeniero Android senior y como director de diseño de Impeccable a la vez.

## 0. Cómo trabajar

- `PRODUCT.md` ya existe y está confirmado: no repitas la entrevista de `init`.
- Superficie en modo **Operate**, construcción **code-led**.
- Estética **fijada por mí**: Material 3 canónico ejecutado a máxima fidelidad. Esa es la salida estándar de Impeccable («standing exit»): no hagas ronda de direcciones ni inventes un mundo visual. El listón de acabado es el de las apps propias de Google (Reloj, Files de Google, Google Home).
- Lee `reference/craft-floor.md` justo antes de la primera edición de UI.
- Termina con la revisión final de Impeccable y escribe `DESIGN.md` a partir de lo construido.
- **Antes de escribir código**, dame en 5–8 líneas el plan y los riesgos principales. Después construye sin pedirme confirmación. Solo te detienes si descubres que un requisito es inviable: entonces me lo dices y propones alternativas antes de parchear nada.
- Trabaja por fases y deja cada una compilando:
  1. Protocolos con tests.
  2. Datos.
  3. UI.
  4. Diagnóstico.
  5. Documentación.

## 1. Hechos de protocolo (punto de partida; vuelve a comprobarlos)

**Documentación oficial de Bose.** Es el PDF *SoundTouch API WebServices v1.1.0*, dentro del crate `bose_soundtouch-1.1.0` de crates.io, carpeta `docs/`. Descárgalo con:

```
curl -L https://static.crates.io/crates/bose_soundtouch/bose_soundtouch-1.1.0.crate | tar xz
```

Qué dice la documentación oficial:
- **API web en HTTP:8090, con XML.** Endpoints:
  - `GET /info`: nombre, tipo, MAC e IP.
  - `GET /now_playing`: `source` y `playStatus`, que puede ser `PLAY_STATE`, `PAUSE_STATE`, `STOP_STATE`, `BUFFERING_STATE` o `INVALID_PLAY_STATUS`.
  - `GET /volume` (devuelve `muteenabled`) y `POST /volume` con `<volume>N</volume>`, rango 0–100.
  - `POST /key`: enviar `press` y luego `release` con `sender="Gabbo"`. Teclas útiles: `POWER`, `MUTE`, `PLAY_PAUSE`.
  - Multiroom: `GET /getZone`, `POST /setZone`, `/addZoneSlave` y `/removeZoneSlave`.
  - Errores: vienen en `<errors><error value name severity>`.
- **WebSocket en `ws://IP:8080`** con subprotocolo `gabbo`: el altavoz envía eventos de estado sin que haya que preguntarle. Úsalo, con sondeo cada 2–3 s como respaldo y solo con la app en primer plano.

**Información comunitaria, sin respaldo oficial.** Fuentes: los proyectos `codingmammoth/SoundTouchBridge` y `jsaavedrav/soundtouch-app` y el hilo «SoundTouch Bridge» del foro de Home Assistant.
- Renderer UPnP en el puerto 8091, ruta de control `/AVTransport/Control`. Probado en SoundTouch 10 y 20.
- La reproducción se hace con `SetAVTransportURI` (con metadatos DIDL-Lite, `upnp:class` = `object.item.audioItem.audioBroadcast`, `protocolInfo` = `http-get:*:<mime>:*`) seguido de `Play`.
- HTTPS, HLS y las URLs de listas no se reproducen de forma fiable; las de `http://` con MP3 o AAC sí.
- El renderer puede quedarse bloqueado: acepta la orden SOAP y no reproduce nada. Se arregla reiniciando el altavoz.
- Si el nombre del altavoz contiene `&`, `<` o `>`, el renderer no arranca y responde 404.
- Si el altavoz está en su radio nativa, una orden UPnP encima responde "éxito" y se ignora.
- Guardar un elemento `source="UPNP"` como preset nativo puede bloquear el renderer. **Prohibido** hacerlo.
- El servicio mDNS anunciado es `_soundtouch._tcp`.

**Desconocido. Debe quedar como comprobación manual mía:**
- Qué formatos suenan realmente.
- Si UPnP despierta al altavoz cuando está en `STANDBY`.
- Si un grupo multiroom propaga a los esclavos el contenido UPnP.
- Si `Pause` funciona en un stream en directo.
- Cómo reacciona con streams de bitrate alto.

## 2. Correcciones a mi enunciado original (aplícalas)

1. **SBC no es un formato de stream.** Es un códec de Bluetooth: quítalo de la matriz de formatos. ALAC, FLAC, WMA y ASF por URL se tratan como «declarados, sin verificar».
2. **Pausa en radio en directo:** el control principal es **Stop**. Muestra Pausa solo si `GetCurrentTransportActions` la ofrece.
3. **URL de control UPnP:** léela de la descripción del dispositivo cuando SSDP dé `LOCATION`. `http://IP:8091/AVTransport/Control` queda como valor por defecto.
4. **«Reproduciendo» se confirma, no se supone.** Tras pulsar Play, la app espera hasta ~15 s a que `/now_playing` diga `source="UPNP"` con `PLAY_STATE`, o `BUFFERING_STATE` y luego `PLAY_STATE`. Si no llega, muestra el error con su causa probable, en este orden:
   - URL https.
   - Formato o HLS.
   - Nombre del altavoz con caracteres que rompen el renderer.
   - Radio nativa activa.
   - Renderer bloqueado: hay que reiniciar el altavoz.
5. **HTTPS:** aviso visible en el formulario y en el reproductor, con la acción «Probar versión http». Antes de ofrecerla, el móvil comprueba que la variante http responde con audio.
6. **`.m3u8` (HLS) es distinto de `.m3u` (lista).** Detecta HLS por la extensión o por `#EXT-X-` en el contenido, y avisa de que probablemente no funcionará.
7. **Antes de enviar una URL, el móvil hace una comprobación rápida** contra el servidor de la emisora, que es el único tráfico externo permitido:
   - Que responde.
   - Su `Content-Type`.
   - Las cabeceras `icy-name` e `icy-br`, si las hay.
   - Si redirige a https.

   Con eso se elige el MIME para el DIDL y se anticipan problemas.
8. **Cuando el altavoz está en reposo**, prueba primero UPnP directamente. Si no arranca, ofrece «Encender» (tecla `POWER`). No envíes `POWER` a ciegas: alterna entre encendido y apagado.
9. **Multiroom:** solo como **experimental**, desactivado por defecto, con aviso de que no está verificado con contenido UPnP.
10. **Android:**
    - `network_security_config` que permita cleartext, necesario para la LAN y los streams http.
    - `WifiManager.MulticastLock` para SSDP.
    - `NsdManager` para mDNS.
    - Revisa qué exige la versión actual de Android para acceder a la red local (permisos tipo `NEARBY_WIFI_DEVICES` o el nuevo permiso de red local) y documenta qué pasa al subir el `targetSdk`.
11. **El móvil no hace de proxy ni retransmite.** El altavoz descarga el stream directamente; la app puede cerrarse tras dar a Play, sin servicio en primer plano.

## 3. Requisitos funcionales

1. **Descubrimiento:**
   - SSDP (M-SEARCH de `urn:schemas-upnp-org:device:MediaRenderer:1`) y mDNS `_soundtouch._tcp`, en paralelo.
   - Cada candidato se valida con `GET :8090/info`.
   - Entrada manual de IP como respaldo, con M-SEARCH unicast a esa IP para encontrar la descripción UPnP.
   - Se recuerdan los altavoces conocidos.
2. **Emisoras:**
   - Alta, edición y borrado, con deshacer en un snackbar.
   - Orden manual.
   - Importación y exportación en JSON (formato propio y versionado) y en M3U, mediante el selector de archivos del sistema (SAF), sin permisos de almacenamiento.
   - Almacenamiento local en un fichero JSON con escritura atómica.
3. **Reproducción:**
   - Play, Stop, Pausa cuando esté disponible, volumen con un slider que no inunde el altavoz (debounce o throttle) y mute.
   - Estado real mostrado: conectando, cargando, reproduciendo, detenido, en reposo, error con causa y acción.
4. **Listas `.pls` y `.m3u`:** se resuelven en el móvil (anidadas hasta 3 niveles, con codificación tolerante) y se elige la primera entrada que pase la comprobación de stream.
5. **Varios altavoces:** selector del altavoz activo y grupo multiroom experimental (punto 9 de la sección 2).
6. **Privacidad:** cero analítica, anuncios, cuentas o crash reporters. Sin fuentes ni imágenes remotas: usa iconos del sistema y la tipografía del sistema.
7. **Pantalla Diagnóstico** (es «solo yo», así que puede mostrar datos técnicos):
   - IP, MAC, firmware (de `/info`), URL de control UPnP, último `now_playing` en bruto y registro de las últimas ~50 peticiones con su resultado.
   - Un **banco de pruebas de formatos**: lanza una lista de URLs que yo pegue, una tras otra, y apunta para cada una si llegó a `PLAY_STATE`, el MIME detectado y el tiempo que tardó. Se puede exportar a Markdown para pegarlo en el README.

## 4. Arquitectura

- Un solo módulo `app`, sin framework de inyección de dependencias (contenedor manual), con `ViewModel` y `StateFlow`.
- Librerías: OkHttp para HTTP y WebSocket, y `kotlinx.serialization`.
- **Parsers de XML con `javax.xml`**, nada de `android.util.Xml`, para que se puedan probar en la JVM.
- Paquetes:
  - `soundtouch`: API de 8090 y WebSocket.
  - `upnp`: SOAP, DIDL y descripción del dispositivo.
  - `discovery`.
  - `playlist`: resolución de listas y comprobación de streams.
  - `data`: emisoras, importación y exportación.
  - `ui`.
- **Un controlador de reproducción por altavoz** con máquina de estados explícita: `Idle`, `Resolving`, `Sending`, `AwaitingConfirmation`, `Playing`, `Buffering`, `Stopped`, `Standby` y `Failed(causa)`. Une los eventos del WebSocket, el sondeo y `GetTransportInfo`.
- Timeouts cortos para la LAN (2–4 s) y reintentos acotados. Al pasar la app a segundo plano no queda nada abierto.

## 5. Diseño (Material 3 canónico, en español)

- **Pantalla principal «Radio»:**
  - Barra superior con un chip del altavoz activo, que abre una hoja inferior con los altavoces y la opción «Añadir IP».
  - Lista de emisoras: tocar una la reproduce.
  - Barra de «ahora suena» persistente abajo, con estado, Stop, mute y volumen.
  - FAB «Añadir emisora».
- **Pantallas secundarias:** edición de emisora (pantalla completa o diálogo, según el tamaño), Diagnóstico, y Ajustes/Acerca de con la licencia MIT y la declaración de privacidad. Usa la navegación de Material adaptada al tamaño de pantalla y con el gesto Atrás predictivo.
- **Estados diseñados de verdad, no huecos en blanco:**
  - Sin altavoces: buscando, ninguno encontrado (con causas: aislamiento de clientes, otra subred) y botón de IP manual.
  - Sin emisoras: con «Importar» y «Añadir».
  - Altavoz sin conexión, cargando, y error con acción.
  - Aviso HTTPS o HLS en el formulario.
- **Material:**
  - Color dinámico en Android 12+ y un esquema estático propio como respaldo.
  - Tema oscuro de primera clase; elevación tonal.
  - Escala tipográfica de Material, en sp.
  - Edge-to-edge con insets.
  - Objetivos táctiles de 48 dp.
  - Snackbars para mensajes transitorios; diálogos solo para decisiones.
  - Movimiento de Material que respete «Quitar animaciones».
- **Textos:** frases concretas en español, del tipo: «El altavoz aceptó la orden pero no está sonando. Suele ser una URL https: prueba la versión http».
- **Verificación:** capturas desde emulador en tema claro y oscuro y con escala de fuente 1.3. Si no hay emulador disponible, dilo y deja la lista de capturas pendientes.

## 6. Entorno de compilación

- **Red:** hacen falta `dl.google.com` (SDK, plugin de Android para Gradle y AndroidX), Maven Central y el portal de plugins de Gradle. Si alguno está bloqueado, pide el acceso; no lo esquives.
- **SDK:** `commandlinetools-linux-*_latest.zip`, más `platform-tools`, `platforms;android-35` y `build-tools;35.0.0`. `ANDROID_HOME` va en `local.properties`, que no se sube al repositorio.
- **Versiones fijas y compatibles entre sí:** Gradle Wrapper 8.x, AGP 8.7.x, Kotlin 2.0.x con el plugin del compilador de Compose, JDK 17 como objetivo y Compose BOM fijado.
- **Comprobación final obligatoria:** `./gradlew test assembleDebug` termina en verde y deja el APK en `app/build/outputs/apk/debug/`.

## 7. Pruebas

- **Tests unitarios en la JVM:**
  - Parsers de `/info`, `/now_playing`, `/volume` y `/getZone`.
  - Construcción del SOAP y del DIDL, con escapado de `&` y `<`.
  - Parseo de fallos SOAP.
  - PLS y M3U, incluidos casos anidados, rotos y HLS.
  - Importación y exportación en JSON y M3U, de ida y vuelta.
  - Variante https a http.
  - Transiciones de la máquina de estados.
- **Altavoz simulado con `MockWebServer`:** sirve `/info`, `/now_playing`, `/volume` y `AVTransport` y prueba el flujo completo «play → confirmado». Incluye el caso «SOAP dice OK pero nunca llega a `PLAY_STATE`», que debe acabar en `Failed` con la causa correcta.
- **No inventes resultados de hardware.** Todo lo que dependa del altavoz real va a la lista de comprobación manual.

## 8. Entregables

1. El proyecto completo, compilando, con `LICENSE` (MIT) y `.gitignore`.
2. **`INSTALAR.md`**, para alguien que no es programador profesional, con dos caminos:
   - **Android Studio:** abrir el proyecto, esperar a la sincronización y usar *Build → Build APK(s)*.
   - **Línea de comandos:** `./gradlew assembleDebug`.

   En ambos casos explica qué es la firma debug (Gradle la genera sola en `~/.android/debug.keystore`) y cómo instalar el APK: pasarlo al móvil y permitir «instalar apps desconocidas», o usar `adb install -r`. Añade los errores típicos y cómo resolverlos.
3. **`README.md`:**
   - Arquitectura (diagrama en texto).
   - Protocolos con ejemplos de peticiones y respuestas.
   - Tabla de **verificado / documentado / comunitario / supuesto**.
   - Cómo ejecutar los tests y el altavoz simulado.
   - Matriz de formatos a rellenar con el banco de pruebas.
   - Limitaciones conocidas, sin maquillar.
4. **`COMPROBACIONES.md`:** lista numerada de pruebas manuales contra el altavoz real, cada una con su resultado esperado y una casilla. Debe cubrir:
   - Descubrimiento SSDP y mDNS, e IP manual.
   - Play, Stop, volumen y mute.
   - Arranque desde reposo.
   - https frente a http.
   - Cada formato.
   - `.pls` y `.m3u`.
   - Renderer bloqueado.
   - Nombre con `&`.
   - Radio nativa activa.
   - Multiroom.
   - Comprobar que el router no aísla clientes.
5. **`DESIGN.md`**, escrito al final a partir de lo construido, y el veredicto de la revisión final de Impeccable.

## 9. Transparencia (obligatoria)

- Separa siempre lo **verificado** (con qué: test, documentación oficial o ejecución) de lo **supuesto**.
- No digas «funciona» de nada que no hayas ejecutado.
- Si algo falla o se queda a medias, dilo con la salida del error.
- Si un requisito es inviable con la API local o con UPnP, dilo antes de implementar un parche.
- Resumen final:
  - Qué compila y qué pasa los tests (con cifras).
  - Qué no has podido probar.
  - Ruta del APK.
  - Los 3 riesgos que más me conviene comprobar primero con el altavoz.

## Datos de mi entorno (complétalos si los sabes)

- Altavoz: Bose SoundTouch 10.
- Móvil: Google Pixel 10, última versión de Android.
- Red: Wi-Fi doméstica, misma subred. Que el router no aísla clientes: [confirmar].
