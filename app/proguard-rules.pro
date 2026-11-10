# Keep rules for the release build.
# Tink (security-crypto) references error-prone annotations at compile time only.
-dontwarn com.google.errorprone.annotations.**
# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.inc.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.inc.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
