plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.yanyuan.toolbox"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.yanyuan.toolbox"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "0.5.0003-bata"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    buildFeatures {
        compose = true
        aidl = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.0")

    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    // ★ 编译端与运行端的 Compose 版本必须一致。
    //   backdrop 的 Compose 依赖在 Gradle 元数据里是 implementation（只进 runtimeClasspath），
    //   冲突解析会把运行时的 ui/foundation/animation/runtime 抬到 1.10.3，
    //   若编译端仍按 BOM 的 1.5.4 编码，运行时会 NoClassDefFoundError
    //   （例：LocalSoftwareKeyboardController 在 1.10.3 中已改变形态）。此处把编译端钉到 1.10.3。
    implementation("androidx.compose.ui:ui:1.10.3")
    implementation("androidx.compose.ui:ui-graphics:1.10.3")
    implementation("androidx.compose.ui:ui-tooling-preview:1.10.3")
    implementation("androidx.compose.foundation:foundation:1.10.3")
    implementation("androidx.compose.animation:animation:1.10.3")
    implementation("androidx.compose.runtime:runtime:1.10.3")
    // material3 必须显式升级（覆盖 BOM 的 1.1.2）：foundation 1.10.3 的 clickable 要求
    // LocalIndication 提供 IndicationNodeFactory，而 M3 1.1.2 的 MaterialTheme 提供的
    // 是旧形态的 material.ripple.PlatformRipple（只实现 Indication）→ 启动即崩。
    // M3 1.4.0（最新稳定版）自带 RippleNodeFactory，由 MaterialTheme 提供合规的 LocalIndication。
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended")

    // Shizuku：仅用于用户明确授权后的受控系统操作
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // 网络
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // 莫奈取色：从上传的应用背景图提取主色（Palette）
    implementation("androidx.palette:palette-ktx:1.0.0")

    // 液态玻璃导航栏（Backdrop）
    // 2.0.1 用 Kotlin 2.4 构建（metadata 2.4.0），超出 AGP 9.4.1 内置 Kotlin 2.2.0 的读取上限；
    // 1.0.6 构建于 Kotlin 2.3（metadata 2.3.0），API 与本项目用法完全一致。
    implementation("io.github.kyant0:backdrop:1.0.6")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
