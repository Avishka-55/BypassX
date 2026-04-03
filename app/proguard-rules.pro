# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Reduce log leakage in release builds.
-assumenosideeffects class android.util.Log {
	public static int v(...);
	public static int d(...);
	public static int i(...);
	public static int w(...);
	public static int e(...);
	public static int println(...);
}

# Keep only annotations/signatures needed by common Android/JSON tooling.
-keepattributes *Annotation*,Signature

# Remove source file names from stack traces in release builds.
-renamesourcefileattribute ""