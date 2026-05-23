import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 读取local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    FileInputStream(localPropertiesFile).use(localProperties::load)
}

// Helper to read properties while providing a default fallback
fun getProperty(key: String, defaultValue: String = ""): String =
    localProperties.getProperty(key) ?: defaultValue

android {
    namespace = "com.hwb.aianswerer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hwb.aianswerer"
        minSdk = 30
        targetSdk = 34
        versionCode = 13
        versionName = "1.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            //noinspection ChromeOsAbiSupport
            // 支持arm64-v8a(真机)和x86_64(模拟器)
            abiFilters += setOf("arm64-v8a", "x86_64")
        }

        // BuildConfig字段 - 从local.properties读取
        val apiUrl = getProperty("api.url", "https://api.openai.com/v1/chat/completions")
        val apiKey = getProperty("api.key", "")
        val apiModel = getProperty("api.model", "gpt-4")
        buildConfigField("String", "API_URL", "\"$apiUrl\"")
        buildConfigField("String", "API_KEY", "\"$apiKey\"")
        buildConfigField("String", "API_MODEL", "\"$apiModel\"")
    }

    // Release签名配置
    signingConfigs {
        create("release") {
            val storeFile = getProperty("signing.storeFile")
            val storePassword = getProperty("signing.storePassword")
            val keyAlias = getProperty("signing.keyAlias")
            val keyPassword = getProperty("signing.keyPassword")

            if (storeFile.isNotEmpty() && storePassword.isNotEmpty() && keyAlias.isNotEmpty() && keyPassword.isNotEmpty()) {
                this.storeFile = file(storeFile)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                println("Release signing configuration loaded from local.properties")
            } else {
                println("Warning: Release signing configuration incomplete, using debug key")
            }
        }
    }

    // APK命名规则
    applicationVariants.all {
        val buildTypeName = buildType.name
        val versionNameValue = versionName
        outputs.all {
            // 使用安全的方式重命名APK，避免依赖AGP内部API
            try {
                val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                val date = SimpleDateFormat("yyyyMMdd-HHmm").format(Date())
                outputImpl.outputFileName =
                    "${date}_AIAnswerer_v${versionNameValue}.apk"
            } catch (e: Exception) {
                println("Warning: Could not rename APK output: ${e.message}")
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true  // 启用R8代码混淆和优化
            isShrinkResources = true  // 启用资源压缩
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release签名配置：如果签名配置不完整，使用debug签名
            val releaseSigningConfig = signingConfigs.getByName("release")
            if (releaseSigningConfig.storeFile != null) {
                signingConfig = releaseSigningConfig
            } else {
                signingConfig = signingConfigs.getByName("debug")
                println("Warning: Using debug signing config for release build")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // ML Kit for text recognition (Chinese recognizer supports Latin text)
    implementation(libs.mlkit.text.recognition.chinese)

    // OkHttp for HTTP requests
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Gson for JSON parsing
    implementation(libs.gson)

    // Kotlin Coroutines
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)

    // Lifecycle components
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)

    // Jetpack Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    // implementation("androidx.compose.material:material-icons-extended") // 移除：使用本地图标定义，减少13.1 MB
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.test.manifest)

    implementation(libs.mmkv)

    // Security - EncryptedSharedPreferences for API Key storage
    implementation(libs.security.crypto)
}
