# The modern Xposed API is provided by the framework at run time. Referencing
# its members during shrinking would strip the entry class that the framework
# instantiates by name.
-keep class com.edgeflow.hook.EdgeFlowModule { *; }
-keep class * extends io.github.libxposed.api.XposedModule { *; }
-keep class io.github.libxposed.api.** { *; }

# Reflection targets framework internals that R8 cannot see; without this the
# class names are rewritten and every lookup fails.
-keepnames class com.edgeflow.hook.** { *; }
-keepclassmembers class com.edgeflow.hook.** {
    <fields>;
    <methods>;
}

# kotlinx.serialization generates a companion serialiser per @Serializable type.
# The serialisers are resolved reflectively, so they must survive shrinking.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.edgeflow.core.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.edgeflow.core.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.edgeflow.core.model.**$$serializer { *; }

# Keep the module metadata files; the framework reads them by exact path.
-keep class ** { @kotlinx.serialization.Serializable <fields>; }

-dontwarn io.github.libxposed.**
-dontwarn org.jetbrains.annotations.**
