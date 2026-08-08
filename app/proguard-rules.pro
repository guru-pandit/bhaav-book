# Proguard rules for BhaavBook

# Room
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-dontwarn androidx.room.paging.**

# OpenCSV
-keep class com.opencsv.** { *; }

# Hilt / Dagger
-keep class * extends dagger.hilt.internal.UnsafeCasts { *; }
