# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html


# ========================================
# 通用规则
# ========================================

# 保留源文件名和行号信息，便于调试崩溃堆栈
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 保留注解信息
-keepattributes *Annotation*

# 保留泛型签名信息
-keepattributes Signature

# 保留异常信息
-keepattributes Exceptions

# ========================================
# MMKV 相关规则
# ========================================
# MMKV 使用 JNI 和反射，需要保护相关类
-keep class com.tencent.mmkv.** { *; }
-dontwarn com.tencent.mmkv.**

# ========================================
# Google ML Kit 相关规则
# ========================================
# ML Kit 文字识别需要保护模型和相关类
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# TensorFlow Lite（ML Kit 底层）
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# ========================================
# OkHttp 相关规则
# ========================================
# OkHttp 使用反射和平台特定代码
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep interface okhttp3.** { *; }

# OkHttp 的平台特定实现
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ========================================
# Gson 相关规则
# ========================================
# Gson 使用反射进行序列化和反序列化
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# 保护自定义数据类（Gson 序列化/反序列化使用）
# 根据实际使用的数据类调整
-keep class com.hwb.aianswerer.api.** { *; }
-keep class com.hwb.aianswerer.config.** { *; }
-keep class com.hwb.aianswerer.models.** { *; }

# Gson 的内部类
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ========================================
# Kotlin 相关规则
# ========================================
# Kotlin 协程
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Kotlin 反射
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# ========================================
# Jetpack Compose 相关规则
# ========================================
# Compose 编译器插件会自动生成正确的 keep 规则，无需手动保留所有类
# 仅保留运行时必要的注解
-keepattributes *Annotation*

# ========================================
# Android 平台相关规则
# ========================================
# 保护 Service 和 BroadcastReceiver
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# 保护自定义 View
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ========================================
# 项目特定规则
# ========================================
# 保护应用的主要类
-keep class com.hwb.aianswerer.MainActivity { *; }
-keep class com.hwb.aianswerer.FloatingWindowService { *; }
-keep class com.hwb.aianswerer.MyApplication { *; }

# 保护配置类
-keep class com.hwb.aianswerer.Constants { *; }

# ScreenReaderService — 无障碍服务，通过 manifest 注册
-keep class com.hwb.aianswerer.ScreenReaderService { *; }

# Vision 模块 — 工厂模式和反射创建
-keep class com.hwb.aianswerer.api.vision.VisionProviderFactory { *; }
-keep class com.hwb.aianswerer.api.vision.OpenAIVisionProvider { *; }

# 广播接收 Activity — 通过 Intent 通信
-keep class com.hwb.aianswerer.ConfirmTextActivity { *; }
-keep class com.hwb.aianswerer.ImageCropActivity { *; }

# ========================================
# 调试和警告抑制
# ========================================
# 忽略一些无害的警告
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn sun.misc.**
