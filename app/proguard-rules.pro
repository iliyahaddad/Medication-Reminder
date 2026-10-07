# Med Reminder release/R8 rules.

# Hilt-generated components.
# Hilt ships its own consumer rules; no blanket keeps needed.

# Room entities and generated implementations.
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Database class * { *; }

# kotlinx.serialization generated serializers used by persisted backups.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers class com.medreminder.**$$serializer { *; }
-keepclassmembers class com.medreminder.domain.model.** { *; }

# Enum keys are persisted in Room and must remain discoverable.
-keepclassmembers enum com.medreminder.domain.model.** { *; }

# Manifest-declared receivers/activities are kept by the Android build tools;
# no broad package-wide keep rule is necessary.

# kotlinx.serialization: keep serializer() lookups on @Serializable companions.
-keepclassmembers @kotlinx.serialization.Serializable class com.medreminder.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn kotlinx.serialization.**
