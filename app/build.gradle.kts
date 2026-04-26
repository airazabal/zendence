import java.util.Properties
import java.io.FileInputStream
import java.io.FileOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
}

val versionPropsFile = file("version.properties")
val versionProps = Properties()

if (!versionPropsFile.exists()) {
    versionProps["VERSION_CODE"] = "1"
    versionProps["VERSION_NAME"] = "1.0"
    versionProps.store(FileOutputStream(versionPropsFile), null)
}

versionProps.load(FileInputStream(versionPropsFile))

val currentVersionCode = versionProps["VERSION_CODE"].toString().toInt()
val nextVersionCode = currentVersionCode + 1
val nextVersionName = "1.$nextVersionCode"

// Update the file for the next build
versionProps["VERSION_CODE"] = nextVersionCode.toString()
versionProps["VERSION_NAME"] = nextVersionName
versionProps.store(FileOutputStream(versionPropsFile), null)

android {
    namespace = "com.alex.zendence"
    // API 35 is the current stable maximum. 36 does not exist yet.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alex.zendence"
        // minSdk 36 prevents the app from running on any current tablet or emulator.
        // 24 (Android 7.0) or 26 (Android 8.0) is recommended.
        minSdk = 26
        targetSdk = 35
        versionCode = nextVersionCode
        versionName = nextVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Removing the custom renaming block for now to fix the "Waiting for Debugger" crash.
    // You can rename the APK manually or use a simpler property later.

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // Room 2.7.0-alpha and modern Compose require Java 17
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val media3Version = "1.3.1"

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.kotlinx.serialization.json)

    // Audio
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")

    // Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
