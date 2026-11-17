# Kharcha Check release rules. Nothing about money or messages is ever logged in release.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# SQLCipher uses JNI and reflection from native code.
-keep,includedescriptorclasses class net.zetetic.database.** { *; }
-keep,includedescriptorclasses interface net.zetetic.database.** { *; }

# Type-safe navigation routes are kotlinx.serialization classes.
-keepclassmembers @kotlinx.serialization.Serializable class com.kharchacheck.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.kharchacheck.app.ui.nav.** { *; }

# Tesseract / Leptonica JNI bridges (offline OCR)
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }

# PdfBox-Android: optional JPEG2000 decoder is not bundled (JPX images are skipped)
-dontwarn com.gemalto.jp2.**
