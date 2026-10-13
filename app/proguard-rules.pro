# Keep file and line numbers in stack traces, under one placeholder file name.
# A crash report from a release build is then readable once retraced against
# that build's mapping.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# DjVuLibre's C++ is bound by symbol name - Java_com_rectobook_app_DjvuNative_*
# - so renaming the class or its natives would leave them unresolved at the
# first call. The default rules already keep natives; this states it where the
# reason can be read.
-keepclasseswithmembernames class com.rectobook.app.DjvuNative {
    native <methods>;
}
