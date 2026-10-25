# VortexDL ProGuard / R8 rules

# Keep JavaScript interfaces exposed to the WebView by name.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Hilt / Dagger generated code is referenced by reflection at runtime.
-keep,allowobfuscation,allowshrinking @dagger.hilt.android.HiltAndroidApp class *
-keep,allowobfuscation,allowshrinking @dagger.hilt.android.AndroidEntryPoint class *

# Keep generic signatures used by Kotlin reflection on Hilt-generated factories.
-keepattributes Signature, InnerClasses, EnclosingMethod
