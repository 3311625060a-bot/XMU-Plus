# ==============================================================================
# XMU Plus R8 / ProGuard Configuration
# Designed for compact APK size & runtime performance while keeping stack traces
# readable and debuggable for the open-source community.
# ==============================================================================

# 1. Open-source crash readability: keep Kotlin source file names & line numbers
#    so user bug reports remain immediately actionable (pair with mapping.txt in Releases).
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# 2. AndroidX / App Entrypoints & Manifest Components
-keep class cn.edu.xmu.plus.MainActivity { *; }
-keep class cn.edu.xmu.plus.widget.** { *; }
-keep class cn.edu.xmu.plus.reminder.** { *; }

# 3. Kotlinx Serialization (@Serializable & generated $serializer / Companion)
-keepattributes *Annotation*
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class cn.edu.xmu.plus.auth.**$$serializer { *; }
-keepclassmembers class cn.edu.xmu.plus.auth.** {
    *** Companion;
}
-keep,includedescriptorclasses class cn.edu.xmu.plus.data.**$$serializer { *; }
-keepclassmembers class cn.edu.xmu.plus.data.** {
    *** Companion;
}
-keep @kotlinx.serialization.Serializable class cn.edu.xmu.plus.** { *; }

# 4. General Android / Keep annotation support
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep <fields>;
    @androidx.annotation.Keep <methods>;
}

# 5. OkHttp / Optional JVM security & platform libraries (standard R8 suppressions)
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.bouncycastle.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# 6. CameraX & ML Kit Barcode Scanning
-keepclassmembers class * extends androidx.camera.core.impl.Config$Option { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
