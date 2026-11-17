# PDFBox-Android references an optional JPEG 2000 decoder that isn't bundled.
-dontwarn com.gemalto.jp2.**

# Ensure Room Database implementations instantiated via reflection keep their default constructor
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
