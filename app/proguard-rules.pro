# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.personalmentor.app.**$$serializer { *; }
-keepclassmembers class com.personalmentor.app.** { *** Companion; }
-keepclasseswithmembers class com.personalmentor.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit (suspend functions + generic return types are read via reflection)
-keepattributes Signature, Exceptions, RuntimeVisibleAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# OkHttp / Okio optional platform classes
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keep the API DTOs readable in stack traces and safe from field renaming
-keep class com.personalmentor.app.data.remote.** { *; }

# Room entities / DAOs are accessed through generated code; keep names stable for migrations and debugging
-keep class com.personalmentor.app.data.local.*Entity { *; }

# PdfBox-Android (PDF text extraction): optional JPEG2000 decoder is not bundled
-dontwarn com.gemalto.jp2.**
-dontwarn com.tom_roush.pdfbox.**
-keep class com.tom_roush.pdfbox.** { *; }
