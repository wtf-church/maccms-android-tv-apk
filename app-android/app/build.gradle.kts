plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.maccms.tv"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.maccms.tv"
        minSdk = 23
        targetSdk = 30
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // 写入最小占位 .so 使 APK 的 native-code 显式包含 armeabi-v7a/arm64-v8a（真机 32 位兼容）
    val abiDir = layout.buildDirectory.dir("generated/jniLibs/debug")
    tasks.matching { it.name == "preDebugBuild" }.configureEach {
        doLast {
            listOf("armeabi-v7a" to "libplaceholder.so", "arm64-v8a" to "libplaceholder.so", "x86" to "libplaceholder.so").forEach { (abi, name) ->
                val out = abiDir.get().file("$abi/$name").asFile
                out.parentFile.mkdirs()
                out.writeBytes(byteArrayOf(0x7F, 0x45, 0x4C, 0x46, 0x01, 0x01, 0x01, 0x00))
            }
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(layout.buildDirectory.dir("generated/jniLibs/debug"))
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packagingOptions {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2023.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.7.2")
    implementation("androidx.navigation:navigation-compose:2.6.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.1")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

    implementation("io.coil-kt:coil-compose:2.4.0")

    implementation("androidx.media3:media3-exoplayer:1.1.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.1.1")
    implementation("androidx.media3:media3-ui:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
