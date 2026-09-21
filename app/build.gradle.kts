import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 签名凭据从 keystore.properties（不进版本库）或同名环境变量读取。
// 文件不存在时不报错 —— 缺凭据只影响 release 是否签名，不该阻断整个构建。
val keystoreProps = Properties()
run {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { keystoreProps.load(it) }
}
val cred: (String) -> String? = { key -> System.getenv(key) ?: keystoreProps.getProperty(key) }

android {
    namespace = "com.shen.screenfilter"
    compileSdk = 34
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.shen.screenfilter"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("zh", "en")
    }

    // 正式签名。debug 变体不用它（继续走 AGP 自动生成的 debug keystore）。
    signingConfigs {
        create("release") {
            cred("STORE_FILE")?.let { storeFile = file(it) }
            cred("STORE_PASSWORD")?.let { storePassword = it }
            cred("KEY_ALIAS")?.let { keyAlias = it }
            cred("KEY_PASSWORD")?.let { keyPassword = it }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 凭据齐全才挂签名；否则降级为未签名包并告警，而不是让构建失败。
            if (listOf("STORE_FILE", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
                    .all { !cred(it).isNullOrBlank() }
            ) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn(">>> 未检测到完整签名凭据（keystore.properties / 环境变量缺失），将产出未签名 APK")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)

    debugImplementation(libs.androidx.ui.tooling)
}
