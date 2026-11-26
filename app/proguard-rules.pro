# Модели, которые читаются из JSON
-keep class com.mogg.app.data.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature
# YouTube-плеер общается с JS через WebView
-keep class com.pierfrancescosoffritti.androidyoutubeplayer.** { *; }
-dontwarn org.slf4j.**
# Health Connect (внутри protobuf, читается через рефлексию)
-keep class androidx.health.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn androidx.health.**
