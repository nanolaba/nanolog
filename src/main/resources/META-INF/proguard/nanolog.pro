# Rules for R8 and ProGuard that the Android Gradle plugin and R8 take from the jar by themselves.

# nanolog.logger creates a logger through its public no-argument constructor, and LoggerIntrospection looks up
# isEnabled by name.
-keep class com.nanolaba.logging.*Logger { public <init>(); }
-keepclassmembers class * implements com.nanolaba.logging.ILogger {
    public boolean isEnabled(com.nanolaba.logging.LogEntry$LogEntryLevel, java.lang.Class);
}

# Caller detection counts a nested class as its enclosing class, through getEnclosingClass, which needs these
# attributes.
-keepattributes InnerClasses,EnclosingMethod

# Slf4jLogger, NanologExtension and SummaryListener compile against SLF4J and JUnit, which an application may lack.
-dontwarn org.slf4j.**
-dontwarn org.junit.**
