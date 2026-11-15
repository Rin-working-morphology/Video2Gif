plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 版本号默认值供本地构建使用；发布流水线用 -PversionName/-PversionCode 覆盖。
val injectedVersionName: String? = providers.gradleProperty("versionName").orNull
val injectedVersionCode: Int? = providers.gradleProperty("versionCode").orNull?.toIntOrNull()

// 发布签名只在四个环境变量齐全时启用；本地没有 keystore 时仍可构建未签名 APK。
val signingKeystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")
val signingStorePassword: String? = System.getenv("SIGNING_STORE_PASSWORD")
val signingKeyAlias: String? = System.getenv("SIGNING_KEY_ALIAS")
val signingKeyPassword: String? = System.getenv("SIGNING_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    signingKeystorePath,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.example.video2gif"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.video2gif"
        minSdk = 24
        targetSdk = 37
        versionCode = injectedVersionCode ?: 1
        versionName = injectedVersionName ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingKeystorePath!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            // 缺少 secrets 时保持未签名；CI 中始终保持签名，保证可覆盖升级。
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
            optimization {
                enable = true
                // 不要设置 packageScope：它开启 AGP 9 的渐进式裁剪（gradual shrinking）实验特性，
                // 会让 R8 合并 Kotlin 标准库的包内私有类，运行时抛 IllegalAccessError。
                // 保持默认的 "**"（完整 R8 优化）。
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
