# Proguard rules for libxposed & legacy xposed
-dontwarn io.github.libxposed.annotation.**
-dontwarn io.github.libxposed.api.**
-dontwarn de.robv.android.xposed.**

-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule { public <init>(...); }
-keep,allowoptimization,allowobfuscation class * implements de.robv.android.xposed.IXposedHookLoadPackage { public <init>(); }

-keep class com.wetype.liquid.hook.** { *; }
-keep class com.wetype.liquid.config.** { *; }
-keep class com.wetype.liquid.glass.** { *; }
-keep class com.wetype.liquid.discovery.** { *; }
