# Keep Hilt generated code
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep class * extends dagger.hilt.android.components.** { *; }

# Keep Kotlin metadata for reflection-based libs (rare but safe)
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.coroutines.flow.**
