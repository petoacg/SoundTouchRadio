# Contribuir

Gracias por ayudar. Unas pocas reglas, que vienen de [PRODUCT.md](PRODUCT.md):

- **Sin tráfico a terceros.** Ni analítica, ni anuncios, ni cuentas, ni informes de errores remotos. Solo se permite hablar con el altavoz en la red local, con el servidor de la emisora y, al buscar, con radio-browser.info.
- **Lo que dice el altavoz manda.** Una emisora solo está «sonando» cuando el altavoz (o el reproductor del móvil) lo confirma; una respuesta «OK» no basta.
- **Separa lo verificado de lo supuesto.** Si algo no se ha probado con un altavoz real, dilo en el código, en la documentación y en [COMPROBACIONES.md](COMPROBACIONES.md).
- **Textos en `res/values/strings.xml`.** Para traducir, añade `values-xx/strings.xml`.
- **Diseño:** [DESIGN.md](DESIGN.md). El verde significa «confirmado».

Antes de enviar cambios: `./gradlew test assembleDebug`.

Para informar de un fallo, usa Diagnóstico → «Copiar informe»: oculta la MAC y la IP de tu altavoz.
