# Retrofit/Gson models use concrete fields. Keep DTO names in release builds.
-keep class com.classpulse.app.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
