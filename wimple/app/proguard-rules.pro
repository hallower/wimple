# Wimple ProGuard / R8 optimization rules

# WebView & JavascriptInterface for Whooing Web Authentication
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class kr.blogspot.charlie0301.wimple.SplashScreenActivity$WebAppInterface { *; }
-keepclassmembers class kr.blogspot.charlie0301.wimple.SplashScreenActivity$WebAppInterface {
    public *;
}

# MPAndroidChart uses reflection for animation and renderers
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# Custom UI widgets referenced in layouts
-keep class kr.blogspot.charlie0301.wimple.widget.** { *; }

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

