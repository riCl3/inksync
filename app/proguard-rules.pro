# Keep rules for the release build (expanded in Milestone 12).
# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.inc.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.inc.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
