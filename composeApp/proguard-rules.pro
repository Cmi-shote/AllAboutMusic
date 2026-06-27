# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.allaboutmusic.**$$serializer { *; }
-keepclassmembers class com.example.allaboutmusic.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.allaboutmusic.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor / OkHttp
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Koin
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil
-keep class coil3.** { *; }
-dontwarn coil3.**

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**

# Keep Jamendo API DTOs (used with kotlinx.serialization)
-keep class com.example.allaboutmusic.data.api.** { *; }

# Keep domain models
-keep class com.example.allaboutmusic.domain.model.** { *; }

# Keep Room entities and DAOs
-keep class com.example.allaboutmusic.data.database.** { *; }
