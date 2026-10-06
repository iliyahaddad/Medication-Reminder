# Add project specific ProGuard rules here.
# R8 runs in "release" builds (minify + shrinkResources enabled).

# Keep generated Hilt components reachable via reflection-based lookup.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }

# Room: keep entities and generated DAO implementations.
-keep @androidx.room.Entity class *
-keepclassmembers class androidx.room.RoomDatabase { *; }

# kotlinx.serialization: keep generated serializers for backup DTOs.
# (The official plugin ships rules, these are a safety net.)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.medreminder.backup.**$$serializer { *; }
-keepclassmembers class com.medreminder.backup.** { *** Companion; }
-keepclasseswithmembers class com.medreminder.backup.** { kotlinx.serialization.KSerializer serializer(...); }

# Keep enum values used in DB/JSON mapping.
-keepclassmembers enum com.medreminder.domain.model.** { *; }

# Broadcast receivers referenced from the manifest are kept automatically by
# the Android plugin; no explicit rules needed.
