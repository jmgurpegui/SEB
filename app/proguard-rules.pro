# Proguard rules for EducaMadrid SEB Client

# Preserve WebView JavaScript interfaces and callbacks
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve WebSettings and WebView reflection
-keepattributes JavascriptInterface
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String);
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
}

# Preserve crypto algorithms & MessageDigest providers
-keep class java.security.MessageDigest { *; }

# Keep Activity and Android components
-keep public class es.educamadrid.seb.client.MainActivity
-keep public class es.educamadrid.seb.client.SebCryptoUtils { *; }

# General Android optimization rules
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
