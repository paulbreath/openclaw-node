# Add project specific ProGuard rules here.
-keep class com.openclaw.node.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
