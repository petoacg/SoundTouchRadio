# Comprobaciones manuales con el SoundTouch 10

Nada de esta lista se ha podido probar contra un altavoz real durante el desarrollo: el entorno no tenía acceso a tu red.
Marca cada casilla y anota el resultado. **Diagnóstico → Banco de pruebas** automatiza las pruebas de formato y exporta una tabla en Markdown.

Antes de empezar: Pixel y altavoz en la misma Wi-Fi; app instalada; altavoz encendido.

## Prioridad 1: lo más incierto
- [ ] **1. RNE (preset 2) suena.** Esperado: luz verde en ≤15 s. Si falla con «la URL es https», el CDN de RTVE redirige a https y el altavoz no lo admite. Anótalo.
- [ ] **2. RNE Radio 5 (preset 3) suena.** Mismo caso que el 1.
- [ ] **3. Onda Cero (preset 1) suena.** Montura AAC de Triton. Esperado: luz verde. Si falla, prueba a pegar `http://playerservices.streamtheworld.com/api/livestream-redirect/OCAAC.aac` en el banco de pruebas y anota el resultado.
- [ ] **4. Cadena SER, COPE y esRadio (presets 4–6) suenan.**
- [ ] **5. Arranque desde reposo.** Con el altavoz en reposo (luz apagada), toca un preset. Esperado: se enciende y suena. Si no, la app muestra «El altavoz sigue en reposo» y el botón **Encender**: anota si este lo resuelve.

## Vinculación (Wi-Fi)
- [ ] **6. Descubrimiento automático.** Vincular altavoz → aparece el SoundTouch 10 en «En tu red» (vía SSDP y/o mDNS) en ≤5 s.
- [ ] **7. Comprobación en 3 pasos.** Al tocarlo: API 8090 en verde con «SoundTouch 10 · firmware …», UPnP 8091 en verde y nombre compatible en verde. Anota si la URL de control salió «según la descripción UPnP» o «ruta por defecto».
- [ ] **8. IP manual.** Escribe la IP del altavoz → mismo resultado que el 7.
- [ ] **9. Cambio de IP.** Reinicia el router o asigna otra IP al altavoz, vuelve a vincular: no se duplica y la app sigue funcionando.
- [ ] **10. Router sin aislamiento de clientes.** Si 6 y 8 fallan, revisa «AP isolation / aislamiento de clientes» en el router.

## Reproducción y estado
- [ ] **11. Stop** detiene el sonido y el estado pasa a «Detenido».
- [ ] **12. Pausa.** Solo aparece si el altavoz la ofrece; anota si aparece en radio en directo.
- [ ] **13. Volumen.** El slider cambia el volumen sin saltos; la rejilla de puntos refleja el nivel. Cambia el volumen con los botones físicos del altavoz: la app lo refleja en ≤3 s (o al instante si el WebSocket 8080 conecta; ver Diagnóstico).
- [ ] **14. Silencio** silencia y vuelve a activar el sonido.
- [ ] **15. Cerrar la app** con algo sonando: el altavoz sigue sonando.
- [ ] **16. Otra fuente.** Pon Bluetooth o AUX en el altavoz: la app muestra «Otra fuente: Bluetooth».

## Formatos y URLs (banco de pruebas)
- [ ] **17. https frente a http.** Prueba la misma emisora en https y en http (por ejemplo `https://playerservices.streamtheworld.com/api/livestream-redirect/CADENASER.mp3`). Esperado según la comunidad: https falla y http suena. Anota el resultado real.
- [ ] **18. Formatos.** Prueba una URL de cada tipo y rellena la tabla de README.es.md: MP3, AAC/HE-AAC, OGG/Vorbis, FLAC, HLS (.m3u8), WMA.
- [ ] **19. Listas .pls y .m3u.** Añade una emisora con URL .pls y otra .m3u: suenan (el móvil las resuelve).
- [ ] **20. Botón «Probar versión http»** en una emisora https que falle: cambia la URL y suena.

## Fallos documentados por la comunidad (solo si aparecen)
- [ ] **21. Renderer bloqueado.** Si una emisora http/MP3 conocida deja de sonar con «El altavoz no confirma que suene», desenchufa el altavoz 10 s y reintenta. Anota si se resuelve.
- [ ] **22. Nombre con «&».** Solo como prueba, renombra el altavoz con «&» (desde otra herramienta) y comprueba que la vinculación muestra el aviso ámbar y la reproducción falla con «nombre del altavoz». Después, deja el nombre como estaba.
- [ ] **23. Radio nativa activa.** Si usas la radio propia de Bose, al lanzar una emisora desde la app puede ignorarla: la app debe decirlo («otra radio activa»).

## Multiroom (solo si tienes 2+ altavoces)
- [ ] **24.** Ajustes → Multiroom (experimental) → en la hoja de altavoces agrupa dos. Anota si el segundo reproduce la emisora enviada por UPnP. Deshaz el grupo.

## Temporizador de apagado (1.1.0)
- [ ] **26.** Con algo sonando, pulsa la luna → 15 min → Iniciar. Aparece la notificación «se apaga a las …» y el panel dice «se apaga en 15 min».
- [ ] **27.** Deja el móvil con la pantalla apagada. A la hora: el volumen baja durante un minuto, se para, el altavoz pasa a reposo y, al encenderlo después, el volumen es el de antes.
- [ ] **28.** Inicia un temporizador y cambia el altavoz a Bluetooth: se cancela solo y lo dice.
- [ ] **29.** «Cancelar» desde la notificación cancela el temporizador.

## Encendido y hojas (1.2.0)
- [ ] **30.** Con el altavoz encendido, pulsa ⏻: pasa a reposo y el botón queda en contorno gris. Púlsalo otra vez: se enciende y vuelve el verde.
- [ ] **31.** Pulsa ⏻ dos veces seguidas muy rápido: no debe quedar en un estado distinto del pedido (el segundo toque se ignora mientras cambia).
- [ ] **32.** Deja el altavoz en reposo varias horas y enciéndelo desde la app: anota si responde (algunos Bose cortan el Wi-Fi en reposo prolongado).
- [ ] **33.** Abre la luna (temporizador) con la navegación de 3 botones: la hoja se abre entera y «Iniciar» queda por encima de la barra de Android.

## Despertador (1.3.0)
- [ ] **34.** Pon el despertador para dentro de 3 minutos con el altavoz en reposo y el móvil bloqueado: se enciende, suena la emisora y el volumen sube poco a poco. Aparece el icono de alarma en la barra de estado.
- [ ] **35.** «Probar ahora» con el altavoz en reposo: mismo resultado inmediato.
- [ ] **36.** Desenchufa el altavoz y prueba: tras ~1 minuto suena una alarma en el móvil con el motivo.
- [ ] **37.** Reinicia el móvil con el despertador activado: el icono de alarma sigue en la barra de estado.

## Configurar la Wi-Fi (1.4.0; conexión rehecha en 1.6.0, como la app de Bose)
> **Primera configuración completa** sin la app de Bose ni el navegador: 9 de noviembre de 2026, versión 1.6.1. Se configuró el altavoz reseteado, se unió a la Wi-Fi de casa y sonó RNE Radio 5 con confirmación del altavoz. Siguen pendientes la lista combinada (39b), la contraseña incorrecta (41) y el nombre al terminar (44).
- [x] **38.** Con el altavoz en modo configuración (indicador ámbar fijo): Vincular altavoz → «Configurar la Wi-Fi del altavoz» → **«Abrir Wi-Fi»**. Se abre el panel de Wi-Fi encima de la app: elige «Bose ST 10 (…)». Puede tardar en «Obteniendo dirección IP…»: es normal. ✅ **Verificado** el 9 de noviembre de 2026 (1.6.1, Pixel 10 con Android 17, SoundTouch 10 con firmware 27.0.6).
- [x] **38b.** Android avisa «Bose ST 10 (…) no tiene acceso a Internet»: toca el aviso y elige **Sí**. Cierra el panel. Sin tocar nada más, en unos segundos el paso 2 se pone en verde («SoundTouch 10 · desde el panel de Wi-Fi») y aparece la lista de redes. Apunta si hizo falta volver a la app o si lo detectó con el panel aún abierto. ✅ **Verificado** el 9 de noviembre de 2026 (1.6.1, Pixel 10 con Android 17, SoundTouch 10 con firmware 27.0.6).
- [ ] **38c.** Repite respondiendo **No** al aviso (o ignorándolo): a los 3 minutos el paso 2 falla con la explicación del «Sí». «Abrir Wi-Fi» vuelve a funcionar.
- [ ] **38d.** (Opcional) «Probar conexión automática»: en el Pixel 10 se espera que falle como en la 1.5.0 (bucle en «Obteniendo dirección IP…»). El error ya no dice «no se encontró la red» y remite a «Abrir Wi-Fi».
- [ ] **43.** Renombra el altavoz desde la hoja de altavoces (lápiz). El chip de la barra muestra el nombre nuevo y, tras volver a vincular, también.
- [x] **39.** Se elige la red de casa, se escribe la contraseña y se envía. ✅ **Verificado** el 9 de noviembre de 2026 (1.6.1, Pixel 10 con Android 17, SoundTouch 10 con firmware 27.0.6). Aparece la lista de redes que ve el altavoz (solo 2,4 GHz). Elige la tuya, escribe la contraseña y pulsa «Enviar a SoundTouch».
- [x] **40.** Tras la cuenta atrás, el paso 5 encuentra el altavoz en tu red y termina en «Usar este altavoz». Suena un preset. ✅ **Verificado** el 9 de noviembre de 2026 (1.6.1, Pixel 10 con Android 17, SoundTouch 10 con firmware 27.0.6).
- [ ] **41.** Repite con una contraseña incorrecta: el paso 5 falla con la explicación (indicador ámbar = contraseña).
- [ ] **39b.** (1.6.1) En «Tu red Wi-Fi» aparece una lista. Si Android pide «Dispositivos cercanos», concédelo: así se añaden las redes que ve el móvil. tu red de casa debe salir seleccionable y las redes de solo 5 GHz, en gris. «Actualizar» vuelve a buscar.
- [x] **40b.** (1.6.1) Tras «Enviar a SoundTouch», ya no aparece «addWirelessProfile: sin respuesta». La cuenta atrás puede decir «Probando… (el altavoz corta su red mientras tanto: es normal)». El paso 5 debe encontrarlo en casa. ✅ **Verificado** el 9 de noviembre de 2026 (1.6.1, Pixel 10 con Android 17, SoundTouch 10 con firmware 27.0.6).
- [ ] **44.** Al terminar, el campo «Nombre del altavoz» propone «SoundTouch 10» (tras un reset se llama «Bose SoundTouch XXXXXX»). Cámbialo, por ejemplo a «Salón», y pulsa «Usar este altavoz»: el chip de la barra y la app de Bose muestran el nombre nuevo.
- [ ] **42.** Comprueba que, después, el resto de la app (Radio, ⏻, temporizador, despertador) funciona igual: el móvil ha vuelto a tu Wi-Fi.

## Alarma «Una vez» y buscador (1.7.0)
- [ ] **45.** Despertador → pestaña «Una vez»: elige fecha (por ejemplo, el sábado) y hora, y actívala. La semanal no cambia: sus días siguen marcados.
- [ ] **46.** En Radio, la fila del despertador muestra la que suena antes. Si es «Una vez», debajo dice que después sigue la semanal.
- [ ] **47.** La alarma «Una vez» suena con su emisora y su volumen, y después se apaga sola. La semanal sigue activada y vuelve a ser la próxima.
- [ ] **48.** Radio → «+ Añadir» → «Buscar en el directorio»: aparecen emisoras de España, las más escuchadas primero. Prueba los chips de país, de género y una búsqueda por nombre.
- [ ] **49.** Toca una emisora → «Probar en el altavoz»: suena y la luz de la fila se pone verde. Luego «Guardar» en una tecla libre (7–10): aparece en la lista y en la tecla.
- [ ] **49b.** (1.7.1) En el buscador, prueba una emisora marcada «https», por ejemplo una de streamtheworld (LOS40, Cadena SER Barcelona) o COPE. Si suena, el texto dice «por su versión http» y es la que se guarda. Si no, dice «solo emite por https». El registro de Diagnóstico ya no debe mostrar `SetAVTransportURI · UPnP 402` seguidos.
- [ ] **50.** Sin internet en el móvil, el buscador dice que el directorio no responde y ofrece «Reintentar»; el resto de la app funciona igual.

## Volumen fino (1.8.0)
- [ ] **51.** (1.9.0, sin teclas − / +) Arrastra el tirador de la barra larga en el tramo bajo: 1, 2 y 3 se aciertan y la cifra pasa de tenue a normal al confirmar.
- [ ] **52.** Arrastra la barra en el tramo bajo: 1, 2 y 3 se aciertan con el dedo y notas una vibración en cada punto.
- [ ] **53.** Con la app abierta, los botones de volumen del móvil suben y bajan el altavoz de punto en punto; el volumen del móvil no cambia.
- [ ] **51c.** (1.8.1) Al mover la barra o pulsar los botones del móvil, la cifra se ve tenue un instante y pasa a normal cuando el altavoz confirma. De noche, con el paso en 1, anota en qué valores (1, 2, 3, 4, 5…) notas el cambio de sonido. Si solo lo notas cada 2 o 3, pon ese paso en Ajustes → Volumen.
- [ ] **52b.** (1.8.2) Toca la pista vacía de la barra, a la derecha del tirador y junto a «+»: el volumen no cambia. Arrastrando el tirador sí se mueve, poco a poco.
- [ ] **55.** (1.9.0) Pantalla Radio: el despertador ocupa una línea, las teclas de preset quedan alineadas y el panel tiene dos filas, con los controles arriba y el volumen abajo. Nada se ve apretado.
- [ ] **56.** (1.9.1) Arrastra el tirador de 0 a 5 despacio: se mueve continuo, sin saltos, y la cifra va 0, 1, 2… Al soltar, se asienta suavemente.
- [ ] **57.** (1.9.1) Activa la semanal y una de «una vez»: en Radio aparecen las dos líneas, la de «una vez» con icono de calendario y la próxima en color normal.
- [ ] **58.** (1.9.1) Con una emisora sonando, la primera vez Android pide permiso de notificaciones. Después, en la persiana y en la pantalla de bloqueo aparecen la emisora, el altavoz, pausa y detener. «Detener» para el altavoz y quita la notificación. Con la pantalla bloqueada, las teclas de volumen mueven el altavoz.
- [ ] **54.** (Fase 2, sin verificar) Con la radio sonando, apaga la pantalla y pulsa los botones de volumen. Lo esperado: cambia el volumen del altavoz y el panel de Android muestra «SoundTouchRadio» o el altavoz. Si cambia el del móvil, anótalo: Android 17 no envía las teclas a sesiones remotas de apps de terceros.

## Otros modelos de SoundTouch (1.9.3)
Para quien use la app con un modelo distinto del SoundTouch 10. Anota modelo y firmware (Diagnóstico → Altavoz).
- [ ] **59.** Vincular altavoz: aparece solo o por IP, y los tres pasos se ponen en verde.
- [ ] **60.** Suena un preset con confirmación (luz verde) y el volumen responde.
- [ ] **61.** Encendido y reposo desde el botón ⏻.
- [ ] **62.** (Si se hace un reset) El asistente de la Wi-Fi encuentra la red «Bose…», lista las redes y el altavoz se une a la de casa. Anota cómo se restableció tu modelo y si ve redes de 5 GHz.

## Publicación (1.9.4)
- [ ] **63.** Diagnóstico → «Copiar informe» y pégalo en una nota: aparecen modelo, firmware, estado y peticiones; la MAC sale acortada (40BD…BFBE) y la IP como 192.168.1.x.
- [ ] **64.** Todos los textos de la app se ven igual que en la 1.9.3: Radio, despertador, buscador, asistente de Wi-Fi, notificación y avisos.

## Emisoras solo https (1.9.5; verificado y activado por defecto en la 1.9.6)
- [x] **65.** Diagnóstico → «Emisoras que solo emiten por https» → «Probar redirección», con la dirección que viene puesta (Radio alHara en https). Anota si suena: la luz pasa a verde y dice «Activado». ✅ **Verificado** el 12 de noviembre de 2026 (SoundTouch 10, firmware 27.0.6).
- [x] **66.** Si funcionó, prueba una emisora del buscador marcada «https» que antes fallaba, como COPE Madrid FM. Debe sonar, y en Diagnóstico «El altavoz reproduce» muestra una dirección del móvil (http://192.168.x.x:8765/r/…). ✅ **Verificado** el 12 de noviembre de 2026 (SoundTouch 10, firmware 27.0.6).
- [x] **66b.** ✅ Verificado el 12 de noviembre de 2026: con la dirección http de Radio alHara (que su servidor redirige a https), suena y sigue sonando tras forzar el cierre de la app.
- [x] **67.** Con la emisora sonando así, cierra la app del todo: debe seguir sonando. Si el altavoz pierde la conexión más tarde, necesitará el móvil en casa para volver a arrancar. ✅ **Verificado** el 12 de noviembre de 2026 (SoundTouch 10, firmware 27.0.6).

## Este móvil (1.10.0)
- [ ] **68.** Chip de arriba → «Este móvil». Toca un preset: suena en el teléfono, la luz se pone verde y el panel muestra la salida («Este móvil · Altavoz del teléfono»).
- [ ] **69.** Empareja los Edifier R1855DB en Ajustes de Android → Bluetooth. Con «Este móvil» elegido, la radio sale por ellos y el panel muestra su nombre.
- [ ] **70.** Con algo sonando en el SoundTouch, cambia a «Este móvil»: se para en el altavoz y sigue en el móvil. Y al revés.
- [ ] **71.** Apaga los Edifier o desconecta el Bluetooth con la radio sonando: se pausa, no salta al altavoz del teléfono.
- [ ] **72.** Pantalla bloqueada: aparecen la emisora, pausa y detener. La barra de volumen y los botones del móvil cambian el volumen multimedia.
- [ ] **73.** Una emisora que solo emite por https o HLS suena en el móvil.
- [ ] **73b.** (1.10.1) Radio alHara guardada como `http://stream.radioalhara.net/ra`, con «Este móvil»: suena. En Diagnóstico, «Suena desde» muestra la dirección https.
- [ ] **74.** Temporizador de 15 min con «Este móvil»: baja el volumen el último minuto y se pausa.
- [x] **75.** Fuera de casa, con datos: suena y el panel dice «Usando datos móviles». ✅ **Verificado** el 13 de noviembre de 2026 (NTS Radio 1 con 5G).

## Sonido y despertador (1.10.2)
- [ ] **76.** Con «Este móvil» y los Edifier: botón del ecualizador (junto a la luna) → prueba «Voz» y «Más graves». El cambio debe notarse en segundos.
- [ ] **77.** Cambia a auriculares: se aplica el ajuste de esa salida, no el de los Edifier.
- [ ] **78.** Ajuste fino: mueve una banda. Aparece «Mío» seleccionado. Prueba «Voz» y vuelve a «Mío»: tu ajuste sigue igual. Cierra la app y vuelve: se conserva.
- [ ] **79.** Borra la emisora que usa el despertador: el aviso dice a cuál pasa, y la línea de Radio la muestra.

## Idioma (1.11.0)
- [ ] **80.** Ajustes → Idioma → English: la app se redibuja en inglés al momento (Radio, Despertador, Diagnóstico). La notificación de reproducción también sale en inglés.
- [ ] **81.** Despertador en inglés: la línea dice, por ejemplo, «Rings on Monday at 7:30 (in 3 days)».
- [ ] **82.** «Como el móvil»: vuelve al idioma del teléfono. En Ajustes de Android → Aplicaciones → SoundTouch Radio aparece también «Idioma».

## Privacidad
- [ ] **25. Sin tráfico a terceros.** (Opcional, con un monitor de red como PCAPdroid.) Solo debe haber tráfico al altavoz, al servidor de la emisora que suena en el móvil con «Este móvil», a los servidores de las emisoras que reproduces o compruebas y, únicamente al usar «Buscar emisoras», a `*.api.radio-browser.info`.
