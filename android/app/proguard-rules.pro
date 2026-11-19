# JavaScript bridge (fallback path when WebMessageListener is unavailable)
-keepclassmembers class dev.lumora.app.web.WebBridge$LegacyInterface {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
