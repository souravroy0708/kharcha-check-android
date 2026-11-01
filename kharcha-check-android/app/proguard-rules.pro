# Release builds are obfuscated (R8). No SMS text or amounts in release logs.
-keep class com.kharchacheck.app.MainActivity
-keep class com.kharchacheck.app.SmsReceiver
-keep class com.kharchacheck.app.ReminderWorker { <init>(...); }
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}
