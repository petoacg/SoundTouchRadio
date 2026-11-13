# kotlinx.serialization: conservar los serializadores generados de nuestras clases @Serializable.
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class io.github.soundtouchradio.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.soundtouchradio.**$$serializer { *; }
-keepclasseswithmembers class io.github.soundtouchradio.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# javax.xml (parsers de la API de Bose) y OkHttp traen sus propias reglas o son de la plataforma.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
