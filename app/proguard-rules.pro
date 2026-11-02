# Room Database rules
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep class com.gudangjoker.ararnote.data.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class com.gudangjoker.ararnote.data.** { *; }

# ViewModels and Lifecycle
-keep class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}
-keep class * extends androidx.lifecycle.ViewModelProvider$Factory {
    public <init>(...);
}

# Kotlin Coroutines and Flows
-keepnames class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Data models and JSON Serialization
-keepclassmembers class com.gudangjoker.ararnote.data.ChecklistItem {
    public <fields>;
    public <methods>;
}
-keepclassmembers class com.gudangjoker.ararnote.data.NoteEntity {
    public <fields>;
    public <methods>;
}
-keepclassmembers enum com.gudangjoker.ararnote.data.NoteColor { *; }
-keepclassmembers enum com.gudangjoker.ararnote.data.NoteType { *; }

# Widget and RemoteViews Service
-keep class com.gudangjoker.ararnote.widget.** { *; }
-keepclassmembers class com.gudangjoker.ararnote.widget.** { *; }

# ViewBinding
-keep class com.gudangjoker.ararnote.databinding.** { *; }

# Custom Views
-keep class com.gudangjoker.ararnote.ui.view.LinedEditText { *; }
