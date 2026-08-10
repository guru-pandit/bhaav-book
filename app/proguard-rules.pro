# ---------------------------------------------------------------------------
# Chaitanya Stores (BhaavBook) — R8 rules
#
# Room, Hilt, DataStore and Compose all ship consumer rules, so this file only
# has to cover what R8 cannot see: reflection inside OpenCSV, and the optional
# dependencies OpenCSV declares but this app never calls.
# ---------------------------------------------------------------------------

# --- Room -------------------------------------------------------------------
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-dontwarn androidx.room.paging.**

# --- OpenCSV ----------------------------------------------------------------
# The parser and writer are used directly, but OpenCSV reaches for its bean
# machinery reflectively during initialisation.
-keep class com.opencsv.** { *; }
-keepclassmembers class com.opencsv.** { *; }

# OpenCSV declares these but only touches them on code paths this app does not
# use (bean binding, validators, locale-aware conversion).
-dontwarn org.apache.commons.beanutils.**
-dontwarn org.apache.commons.collections4.**
-dontwarn org.apache.commons.lang3.**
-dontwarn org.apache.commons.text.**
-dontwarn java.beans.**

# --- Kotlin / coroutines ----------------------------------------------------
# Keeps stack traces from coroutines readable in a crash report.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Enums stored as strings ------------------------------------------------
# ProductUnit, ThemeOption and PriceFontSize round-trip through DataStore and
# CSV by name, so their constants must keep their names.
-keepclassmembers enum com.bhaavbook.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
