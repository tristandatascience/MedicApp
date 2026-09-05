# SQLCipher : l'API est chargée par réflexion côté SupportFactory
-keep class net.zetetic.database.** { *; }

# ML Kit (OCR embarqué)
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Room
-keep class * extends androidx.room.RoomDatabase
