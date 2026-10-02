# Add project specific ProGuard rules here.
# Keep Firebase models
-keep class com.athlink.app.data.model.** { *; }
-keepnames class com.google.firebase.** { *; }
