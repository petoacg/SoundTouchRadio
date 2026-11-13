# Instalar SoundTouch Radio en tu Pixel

Hay dos caminos. El **A** es el más rápido: el `.zip` ya trae el APK compilado.

## A. Instalar el APK (5 minutos)

Descarga `app-debug.apk` de la página **Releases** del repositorio en GitHub (o usa la que viene en la carpeta `apk/` del zip).

1. Descomprime `SoundTouchRadio.zip`. El APK está en `SoundTouchRadio/apk/SoundTouchRadio-debug.apk`.
2. Pásalo al Pixel: por USB (copiar a «Descargas»), Google Drive o correo.
3. En el Pixel, abre «Archivos» → Descargas → toca `SoundTouchRadio-debug.apk`.
4. Android avisará de que no puede instalar apps de esta fuente. Toca **Ajustes**, activa **Permitir de esta fuente** y vuelve atrás.
5. Toca **Instalar**. Si aparece Play Protect, elige **Instalar de todas formas**: la app no está en Play Store y lleva firma de depuración.
6. Abre «SoundTouch Radio». La primera pantalla es **Vincular altavoz**.

## B. Compilar y ejecutar con Android Studio

Necesitas un ordenador con Windows, macOS o Linux y unos 10 GB libres.

1. Instala **Android Studio** (versión estable actual) desde developer.android.com/studio. En el asistente inicial elige «Standard»: instala el SDK de Android y el JDK incluido.
2. Descomprime `SoundTouchRadio.zip`.
3. En Android Studio: **File → Open…**, selecciona la carpeta `SoundTouchRadio` (la que contiene `settings.gradle.kts`) y pulsa **OK**. Si pregunta «Trust project?», elige **Trust Project**.
4. Espera a que termine la sincronización de Gradle (barra inferior). La primera vez descarga dependencias durante unos minutos. Android Studio crea `local.properties` con la ruta de tu SDK; no lo edites.
   - Si propone actualizar el «Android Gradle Plugin», puedes ignorarlo: el proyecto está fijado a versiones probadas (AGP 8.7.3, Gradle 8.11.1, Kotlin 2.0.21).
5. Prepara el Pixel:
   1. Ajustes → Información del teléfono → toca 7 veces **Número de compilación** (activa las opciones de desarrollador).
   2. Ajustes → Sistema → Opciones de desarrollador → activa **Depuración por USB**.
   3. Conecta el Pixel por USB y acepta «¿Permitir depuración USB?».
6. Arriba en Android Studio elige tu Pixel en la lista de dispositivos y pulsa **▶ Run**. La app se compila, se instala y se abre.

### Generar el APK sin instalarlo
- Menú **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. Al terminar, **locate** abre `app/build/outputs/apk/debug/app-debug.apk`.
- O en la terminal, dentro de la carpeta del proyecto:
  - macOS/Linux: `./gradlew assembleDebug`
  - Windows: `gradlew.bat assembleDebug`

### ¿Qué es la «firma debug»?
Android solo instala APKs firmados. Al compilar en modo debug, Gradle crea automáticamente una clave de depuración en tu ordenador (`~/.android/debug.keystore`) y firma con ella. Sirve para uso personal. Para publicar en una tienda harías falta una clave propia (no es necesario aquí). El modo `release` de este proyecto también usa la clave debug para que el APK sea instalable sin más pasos.

### Instalar con la línea de comandos (opcional)
Con el Pixel conectado y la depuración USB activa:
`adb install -r app/build/outputs/apk/debug/app-debug.apk`

## C. Firmar con tu propia clave (para publicar)

El APK release se firma con la **clave de desarrollo** mientras no configures la tuya. Sirve para tu uso, pero no para repartirla: cualquiera podría firmar otra versión con esa misma clave. Para publicarla:

1. Crea la clave una sola vez, en tu ordenador, desde la carpeta del proyecto (pide contraseñas y tu nombre):
   ```bash
   keytool -genkeypair -v -keystore soundtouchradio.jks -alias soundtouchradio -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Crea en la misma carpeta un archivo `keystore.properties`:
   ```
   storeFile=soundtouchradio.jks
   storePassword=tu-contraseña
   keyAlias=soundtouchradio
   keyPassword=tu-contraseña
   ```
3. Compila: `./gradlew assembleRelease`. El APK queda en `app/build/outputs/apk/release/`.

**Importante:**
- `soundtouchradio.jks` y `keystore.properties` no se suben nunca: `.gitignore` ya los excluye. Guarda una copia de la clave en un sitio seguro. Si la pierdes, no podrás publicar actualizaciones que se instalen encima.
- Una app firmada con tu clave no se puede instalar encima de una firmada con la de desarrollo, ni al revés. El cambio exige desinstalar, y al desinstalar se pierden emisoras, presets y despertadores. Antes, exporta las emisoras (menú ⋮ → Exportar a JSON).

## Primer uso
1. **Vincular altavoz**: la app busca tu SoundTouch 10 en la Wi-Fi. Toca el que aparezca → se comprueban API (8090) y reproductor UPnP (8091) → **Usar este altavoz**. Si no aparece, escribe su IP (la ves en la lista de dispositivos de tu router).
2. En **Radio** toca una tecla de preset (1 Onda Cero, 2 RNE, 3 RNE Radio 5, 4 Cadena SER, 5 COPE, 6 esRadio). La luz verde se enciende solo cuando el altavoz confirma que suena.
3. Puedes cerrar la app: el altavoz sigue sonando por su cuenta.

## Problemas típicos
| Síntoma | Solución |
|---|---|
| «SDK location not found» | Abre el proyecto con Android Studio (crea `local.properties`), o crea ese archivo con `sdk.dir=/ruta/a/tu/Android/Sdk`. |
| La sincronización falla al descargar | Revisa la conexión a internet y pulsa **File → Sync Project with Gradle Files**. |
| «Unsupported class file major version» / JDK | Settings → Build Tools → Gradle → **Gradle JDK**: elige el JDK incluido con Android Studio (17 o superior). |
| El Pixel no aparece en Android Studio | Cambia el modo USB a «Transferencia de archivos», revoca autorizaciones de depuración y vuelve a conectar. |
| «App no instalada» | Desinstala una versión anterior con otra firma y vuelve a instalar. |
| No encuentra el altavoz | Mismo Wi-Fi, sin aislamiento de clientes en el router; prueba con la IP manual. |
