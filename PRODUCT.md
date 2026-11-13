# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Kotlin + Jetpack Compose + Material 3 (fijado por el usuario). minSdk 26, targetSdk 35. Gradle con wrapper; sin dependencias de Google Play Services ni Firebase.

## Users

Una sola persona: el propietario del altavoz, con soltura técnica pero que no es programador profesional. Usa la app en casa, en el móvil, para poner una emisora en su Bose SoundTouch después de que Bose apagara la nube (6 de mayo de 2026). Sabe lo que es una IP o una URL de stream; no quiere que se le escondan los diagnósticos.

## Product Purpose

Controlar un Bose SoundTouch por la red local y hacer que reproduzca radios cuyas URLs introduce el usuario, sin nube de Bose, sin cuentas y sin servidores intermedios. Éxito: elegir emisora → suena en el altavoz en segundos, y si no suena, la app dice por qué con el estado real del altavoz.

## Positioning

Cliente local y honesto: el altavoz descarga el stream directamente (el móvil puede cerrarse después de pulsar play). La app nunca da por buena una orden solo porque el altavoz respondió "OK": confirma contra el estado real del dispositivo.

## Operating Context

- Altavoz y móvil en la misma Wi-Fi doméstica (subred común, sin aislamiento de clientes: pendiente de confirmar por el usuario).
- Altavoz de referencia: Bose SoundTouch 10 (el único verificado). Desde la 1.9.3 la app no asume ese modelo: cualquier SoundTouch con la API local debería servir; ver «Modelos compatibles» en README.md. Móvil: Google Pixel 10 con la última versión de Android (Android 17 a noviembre de 2026).
- Uso típico: abrir, tocar una emisora, ajustar volumen, cerrar. Uso ocasional: dar de alta emisoras, importar/exportar, diagnosticar por qué un stream no suena.

## Capabilities and Constraints

- Control por API web local del SoundTouch (HTTP:8090, XML) y por UPnP AVTransport (HTTP:8091).
- Emisoras guardadas localmente (nombre + URL), con import/export JSON y M3U.
- Listas .pls/.m3u se resuelven en el móvil antes de enviar la URL.
- Multiroom solo como función experimental si la API local lo permite con contenido UPnP.
- Fuera de alcance: botones físicos de preset.
- Destino «Este móvil» (1.10.0, pedido por el usuario): reproducir en el teléfono, por su altavoz, auriculares o altavoces Bluetooth (los Edifier R1855DB del usuario). Reproductor Media3 ExoPlayer, de código abierto y sin Play Services. El despertador sigue solo en el SoundTouch.
- Búsqueda de emisoras (desde la 1.7.0, aprobada por el usuario el 9 de noviembre de 2026): directorio comunitario radio-browser.info, solo cuando el usuario busca.
- Prohibido: analítica, anuncios, cuentas, crash reporters remotos, fuentes o imágenes descargadas de terceros. Tráfico externo permitido: el propio stream/lista de la emisora y, solo al buscar, la consulta a radio-browser.info. Esa consulta envía la búsqueda y la identificación «SoundTouchRadio»; no descarga logotipos ni registra clics. Puente de redirección (activado por defecto desde la 1.9.6, se desactiva en Ajustes): un servidor local en la Wi-Fi que solo responde «302 → https» al altavoz.
- Licencia MIT.

## Brand Commitments

- Interfaz en español (por defecto) e inglés.
- Estética: «radio moderna» en la dirección «Radio Braun», elegida por el usuario en la ronda de Impeccable (sustituye a la elección previa de Material 3 canónico). Estructura, navegación y controles siguen siendo Material 3 nativo; el mundo aporta color, tipografía, densidad y un gesto propio. Contrato en `.impeccable/surfaces/radio.md`.

## Evidence on Hand

- Documento oficial "SoundTouch API WebServices v1.1.0" (Bose), obtenible del crate `bose_soundtouch-1.1.0` en crates.io, carpeta `docs/`.
- Proyectos comunitarios que documentan el uso de UPnP en :8091 `/AVTransport/Control`: github.com/codingmammoth/SoundTouchBridge, github.com/jsaavedrav/soundtouch-app.
- No hay altavoz real accesible desde el entorno de desarrollo: cualquier afirmación de hardware queda como "pendiente de verificación manual".

## Product Principles

1. El estado real del altavoz manda; la respuesta del protocolo no es prueba de que suene.
2. Nada sale de la LAN salvo el stream que el usuario eligió.
3. Cada fallo se explica con causa probable y siguiente paso concreto.
4. Lo verificado y lo supuesto se distinguen siempre, en la UI de diagnóstico y en la documentación.
5. Pocas pantallas, acción principal a un toque.

## Accessibility & Inclusion

Mínimos Material/Android: objetivos táctiles de 48 dp, texto en sp que respete la escala de fuente del sistema, contraste AA en ambos temas, descripciones de contenido en controles solo-icono.
