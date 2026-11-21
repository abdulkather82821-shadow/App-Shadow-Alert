-keep class com.shadowalert.app.data.local.entity.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <init>();
}
