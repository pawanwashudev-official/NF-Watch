# ProGuard rules for NF Watch

# Keep Health Connect
-keep class androidx.health.connect.** { *; }
-keep class androidx.health.platform.** { *; }

# Keep models for Room and DataStore
-keepclassmembers class * extends androidx.room.RoomDatabase {
    *;
}
-keep class * implements androidx.room.RoomDatabase {
    *;
}
-keepclassmembers class * {
    @androidx.room.Query *;
    @androidx.room.Insert *;
    @androidx.room.Update *;
    @androidx.room.Delete *;
}

# Keep Compose
-keep class androidx.compose.ui.platform.** { *; }
-keep class androidx.compose.runtime.** { *; }

# Keep Bluetooth Callbacks (Ensure methods aren't stripped)
-keepclassmembers class * extends android.bluetooth.BluetoothGattCallback {
    <methods>;
}
-keepclassmembers class * extends android.bluetooth.le.ScanCallback {
    <methods>;
}

# Keep Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# Optimization settings
-repackageclasses ''
-allowaccessmodification
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
