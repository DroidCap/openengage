# 1. Keep legible exception class names (allows tracking custom errors by name)
-keepnames class * extends java.lang.Throwable { *; }

# 2. Keep legible screen names (Activities & Fragments used for tracking)
-keepnames class * extends android.app.Activity
-keepnames class * extends androidx.fragment.app.Fragment

# 3. Ensure stack traces retain line numbers and source signatures
-keepattributes Exceptions,Signature,InnerClasses,SourceFile,LineNumberTable
