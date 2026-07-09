# Keep kotlinx.serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class app.farmsy.android.** {
    *** Companion;
}
-keepclasseswithmembers class app.farmsy.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}
