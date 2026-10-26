# MapLibre native code calls back into Java classes through JNI.
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

# Car App Library templates are serialized through reflection by the host.
-keep class androidx.car.app.** { *; }

# Keep line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
