import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Ключ release-сборки: keystore.properties в корне (не в git) — storeFile (путь от корня), storePassword, keyAlias,
// keyPassword. Нет файла — отладочный ключ. Обновление ставится поверх установленного только с тем же ключом.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.alaev.rezkatv"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.alaev.rezkatv"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (!keystoreProps.isEmpty) create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val media3 = "1.11.1"

dependencies {
    implementation("androidx.leanback:leanback:1.2.0")
    implementation("androidx.leanback:leanback-preference:1.2.0")
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui-leanback:$media3")
    implementation("androidx.media3:media3-ui:$media3") // только SubtitleView
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("com.google.zxing:core:3.5.3") // QR-код для входа с телефона
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    // В юнит-тестах android.jar — заглушки, org.json нужен настоящий
    testImplementation("org.json:json:20251224")
}
