import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    id("com.google.devtools.ksp") version "2.0.21-1.0.27"
}

// Load signing config from local.properties
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties()
if (localPropertiesFile.exists()) {
    FileInputStream(localPropertiesFile).use { stream ->
        localProperties.load(stream)
    }
}

android {
    namespace = "com.neubofy.watch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.neubofy.watch"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        setProperty("archivesBaseName", "NFWatch-v${versionName}")
    }

    signingConfigs {
        create("release") {
            val keystoreFile = localProperties.getProperty("KEYSTORE_FILE")
            val keystorePass = localProperties.getProperty("KEYSTORE_PASSWORD")
            val keyAliasName = localProperties.getProperty("KEY_ALIAS")
            val keyPass = localProperties.getProperty("KEY_PASSWORD")

            if (keystoreFile != null && keystorePass != null) {
                storeFile = file("${rootProject.projectDir}/$keystoreFile")
                storePassword = keystorePass
                keyAlias = keyAliasName ?: "reality_key"
                keyPassword = keyPass ?: keystorePass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            versionNameSuffix = "-DEBUG"
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output?.outputFileName = "NFWatch-v${versionName}-${buildType.name}.apk"
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
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Data - Local only
    implementation(libs.androidx.datastore.preferences)

    // Health Connect - OS-level health data storage
    implementation(libs.androidx.health.connect)

    // Room - Local Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager - Periodic background tasks
    implementation(libs.androidx.work.runtime.ktx)

}
