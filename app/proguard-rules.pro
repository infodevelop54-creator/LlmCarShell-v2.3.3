-keep class com.example.llmcar.** { *; }
-keep class rikka.shizuku.** { *; }
-keep class ai.picovoice.** { *; }
-keep class com.github.eltonvs.** { *; }
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.llmcar.**$$serializer { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class androidx.car.app.** { *; }
-keep class com.example.llmcar.car.** { *; }