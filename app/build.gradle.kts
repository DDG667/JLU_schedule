import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "cn.jlu.schedule"
    compileSdk = 36

    defaultConfig {
        applicationId = "cn.jlu.schedule"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "2.2.4-tokyo"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val localSigningFile = rootProject.file("local-signing.properties")
    val localSigningProperties = Properties()
    if (localSigningFile.isFile) {
        localSigningFile.inputStream().use(localSigningProperties::load)
    }
    val envStorePath = System.getenv("SIGNING_STORE_FILE")?.takeIf { it.isNotBlank() }
    val envStorePassword = System.getenv("SIGNING_STORE_PASSWORD")?.takeIf { it.isNotBlank() }
    val envKeyAlias = System.getenv("SIGNING_KEY_ALIAS")?.takeIf { it.isNotBlank() }
    val envKeyPassword = System.getenv("SIGNING_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
    val hasEnvSigning = envStorePath != null && envStorePassword != null && envKeyAlias != null && envKeyPassword != null

    val hasLocalSigning = localSigningProperties.getProperty("storePassword")?.isNotBlank() == true &&
        localSigningProperties.getProperty("keyPassword")?.isNotBlank() == true

    signingConfigs {
        if (hasEnvSigning) {
            create("release") {
                storeFile = file(envStorePath!!)
                storePassword = envStorePassword
                keyAlias = envKeyAlias
                keyPassword = envKeyPassword
            }
        } else if (hasLocalSigning) {
            create("release") {
                val localStorePath = localSigningProperties.getProperty("storeFile")
                storeFile = if (localStorePath.isNullOrBlank()) {
                    rootProject.file("../apk-key/key.jks")
                } else {
                    file(localStorePath)
                }
                storePassword = localSigningProperties.getProperty("storePassword")
                keyAlias = localSigningProperties.getProperty("keyAlias")?.takeIf { it.isNotBlank() } ?: "ddg667-release"
                keyPassword = localSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (hasEnvSigning || hasLocalSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.github.yalantis:ucrop:2.2.11")
    // The uCrop JitPack POM omits these runtime dependencies; JPEG crops need ExifInterface.
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("androidx.transition:transition:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.2.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
