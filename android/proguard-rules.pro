# LibGDX ProGuard rules

# Keep LibGDX classes
-keep class com.badlogic.** { *; }
-keep class com.badlogicgames.** { *; }
-dontwarn com.badlogic.**

# Keep Ashley ECS
-keep class com.badlogic.ashley.** { *; }

# Keep game classes
-keep class com.eucleantoomuch.game.** { *; }

# gdx-gltf: GLB/GLTF loader fills net.mgsx.gltf.data.* via libGDX Json reflection,
# and PBR shaders/scene classes are looked up at runtime - must not be stripped/renamed
-keep class net.mgsx.gltf.** { *; }
-dontwarn net.mgsx.**

# KTX extensions
-keep class ktx.** { *; }
-dontwarn ktx.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
