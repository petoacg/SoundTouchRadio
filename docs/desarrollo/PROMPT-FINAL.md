/impeccable Ejecuta de principio a fin el proyecto SoundTouch Radio y termina entregando **un único archivo `.zip` que se abra en Android Studio y ejecute la app sin tocar nada más**.

## Fuentes de verdad (léelas en este orden; no repitas entrevistas ni rondas ya cerradas)

1. `PRODUCT.md`: producto, usuario y restricciones. Altavoz SoundTouch 10, Pixel 10 con el último Android, interfaz en español.
2. `PROMPT.md`: requisitos funcionales, hechos de protocolo, correcciones, arquitectura, pruebas, entregables y reglas de transparencia.
3. `PROMPT-DISENO.md` y `.impeccable/surfaces/radio.md`: estética «Radio Braun», ya elegida.

Si dos fuentes chocan: el estado real del altavoz y la documentación oficial de Bose mandan sobre lo comunitario, y el contrato de diseño manda sobre los gustos.

## Pasos

1. **Plan y riesgos** en 5–8 líneas. Después, ejecución sin pausas. Solo te detienes si un requisito es inviable: entonces lo explicas y propones alternativas.
2. **Entorno:**
   - SDK de Android: plataforma 35 y build-tools 35.0.0, con `dl.google.com` accesible.
   - Gradle Wrapper 8.11.1 incluido, con el `.jar` dentro.
   - AGP 8.7.3, Kotlin 2.0.21, JDK 17 como objetivo y Compose BOM fijado.
   - `minSdk 26`, `targetSdk 35`. **No** declares `ACCESS_LOCAL_NETWORK` mientras `targetSdk` sea inferior a 37 (documentación oficial: las apps antiguas tienen acceso implícito; declararlo rompió la red local en Android 17).
3. **Protocolos con tests:**
   - Cliente de la API 8090 (`/info`, `/now_playing`, `/volume`, `/key`, zonas).
   - WebSocket en 8080 con el subprotocolo `gabbo`.
   - UPnP AVTransport en 8091: SOAP con DIDL-Lite y la URL de control tomada de la descripción del dispositivo, o `/AVTransport/Control` por defecto.
   - Controlador de reproducción con máquina de estados que **confirma** que suena contra `/now_playing`.
4. **Datos:**
   - Emisoras en JSON local con escritura atómica.
   - Importación y exportación en JSON y M3U por SAF.
   - Resolución de `.pls`/`.m3u` y detección de HLS.
   - Sonda del stream (MIME e icy) y paso de https a http.
5. **Descubrimiento:** SSDP con multicast lock, mDNS `_soundtouch._tcp`, IP manual con M-SEARCH unicast y altavoces recordados.
6. **UI «Radio Braun»:**
   - Pantallas: Radio, editor de emisora, hoja de altavoces, Diagnóstico (con banco de pruebas de formatos exportable a Markdown) y Ajustes/Acerca de.
   - Tema claro y oscuro con la paleta del contrato; el color dinámico es opcional.
   - El gesto propio: la rejilla de perforaciones del altavoz.
   - Edge-to-edge, gesto Atrás predictivo, 48 dp y textos en español concretos.
7. **Verificación:**
   - `./gradlew test assembleDebug` debe terminar en verde.
   - Cuenta los tests y apunta las cifras.
   - Si hay emulador, haz capturas; si no, dilo en una línea.
8. **Documentación:** `README.md`, `INSTALAR.md` (pensado para quien no es programador profesional: Android Studio y línea de comandos, firma debug, instalar en el Pixel), `COMPROBACIONES.md` (pruebas manuales con el SoundTouch 10), `LICENSE` (MIT) y `DESIGN.md` escrito a partir de lo construido, tras la revisión final de Impeccable.
9. **Empaquetado final:** `SoundTouchRadio.zip`, con el proyecto en una carpeta raíz `SoundTouchRadio/`.
   - **Incluye:** código, wrapper con su `.jar`, documentación, el APK debug ya compilado en `apk/SoundTouchRadio-debug.apk` y `.impeccable/`.
   - **Excluye:** `build/`, `.gradle/`, `.kotlin/`, `.idea/` y `local.properties`. Android Studio crea este último solo con la ruta del SDK.
   - **Comprobación del zip:** descomprímelo en una carpeta limpia, ejecuta `./gradlew assembleDebug` desde ella y confirma que compila.
   - En Android Studio se abre con *File → Open* sobre la carpeta `SoundTouchRadio`, se espera la sincronización y se pulsa ▶ con el Pixel conectado (depuración USB) o un emulador.

## Cierre

Resumen final en 6–10 líneas:
- Ruta del `.zip` y del APK.
- Tests que pasan (cifras).
- Qué no se pudo probar contra el SoundTouch 10 real, remitiendo a `COMPROBACIONES.md`.
- Los 3 riesgos que conviene comprobar primero.

## Ampliaciones (añadidas después; también obligatorias)

### A. 10 presets y emisoras precargadas
- La app tiene **10 presets propios**: teclas numeradas 1–10 en una rejilla de 5×2, con el estilo de las teclas de un aparato Braun: circulares, número en cifras tabulares y nombre corto de la emisora debajo. No son los 6 botones físicos del altavoz, que siguen fuera de alcance.
- Tocar una tecla reproduce. Mantenerla pulsada permite asignar una emisora o vaciar la tecla. Desde el menú de cada emisora se puede elegir «Guardar en preset…».
- Una tecla vacía se ve en contorno con su número. La que suena lleva la luz piloto.
- Al borrar una emisora, su preset queda vacío.
- **Primer arranque**, presets 1–6 precargados con URLs http directas, sin HLS, verificadas en radio-browser.info y en sus propios servidores en noviembre de 2026:
  1. Onda Cero: `http://playerservices.streamtheworld.com/api/livestream-redirect/OCAAC.aac`
  2. RNE: `http://dispatcher.rndfnk.com/crtve/rne1/main/mp3/high`
  3. RNE Radio 5: `http://dispatcher.rndfnk.com/crtve/rne5/main/mp3/high`
  4. Cadena SER: `http://playerservices.streamtheworld.com/api/livestream-redirect/CADENASER.mp3`
  5. COPE: `http://flucast12-h-cloud.flumotion.com/cope/net1.mp3`
  6. esRadio: `http://livestreaming.esradio.fm/stream64.mp3`
- **Redirecciones:** el móvil las sigue y envía al altavoz la URL final cuando sigue siendo http.
- **RNE y Radio 5:** su CDN redirige a https con token. Va en primer lugar de `COMPROBACIONES.md`, y la app muestra la causa si no suenan.

### B. Vinculación con el SoundTouch 10 por Wi-Fi
- **Técnica:**
  - No hay «emparejamiento» de protocolo: se vincula un altavoz que **ya está en la Wi-Fi**.
  - Descubrimiento con SSDP y mDNS en paralelo, más IP manual.
  - Comprobación en tres pasos:
    1. API 8090: `GET /info` devuelve modelo, nombre, MAC y firmware.
    2. Renderer UPnP 8091: `GetTransportInfo`, que no hace sonar nada. La URL de control sale de la descripción SSDP o, si no, de la ruta por defecto.
    3. Nombre del altavoz sin `&`, `<` ni `>`.
  - Se guarda con la MAC como identificador y, si la IP cambia, se actualiza al volver a descubrirlo.
  - Configurar desde cero la Wi-Fi de un altavoz reseteado (modo de configuración del propio Bose) queda fuera de alcance y se explica en la ayuda.
- **Visual:**
  - Pantalla «Vincular altavoz» con una lista de pasos, cada uno con su luz piloto: gris pendiente, verde correcto, rojo fallo con causa y acción.
  - Tarjeta del altavoz encontrado: modelo («SoundTouch 10»), nombre, IP y firmware, y el botón «Usar este altavoz».
  - Si no aparece ninguno: causas concretas (otra subred, aislamiento de clientes en el router, altavoz en reposo profundo o sin Wi-Fi) y el campo de IP manual.
  - Es la primera pantalla mientras no haya ningún altavoz vinculado, y se puede abrir desde el chip del altavoz.
