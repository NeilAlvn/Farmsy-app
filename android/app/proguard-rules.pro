# Keep kotlinx.serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class app.farmsy.android.** {
    *** Companion;
}
-keepclasseswithmembers class app.farmsy.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Play Install Referrer binds to the Play Store over AIDL; R8 must not rename
# the interface it talks through, or every release build reads no referrer.
-keep class com.google.android.finsky.externalreferrer.** { *; }
