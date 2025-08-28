import buildscriptutils.Module
import buildscriptutils.getLibraryAarFilePath
import buildscriptutils.getVersionCode
import buildscriptutils.getVersionName

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.serialization)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.license)
}

private val appVersionName = getVersionName(rootDir, Module.APP)
private val appVersionCode = getVersionCode(rootDir, Module.APP)

android {
    namespace = "com.dolby.android.alps.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dolby.android.alps.app"
        minSdk = 29
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "CONTENT_URL", "\"https://json-lists.s3.eu-central-1.amazonaws.com/android_sample_app_content.json\"")
        buildConfigField("String", "SETTINGS_URL", "\"https://json-lists.s3.eu-central-1.amazonaws.com/android_sample_app_settings.json\"")
    }

    signingConfigs {
        create("appDebug") {
            keyAlias = "debugKey"
            keyPassword = "HvVe26c5ss%87Cd3n#Vf"
            storeFile = file("../keys/debugKey.jks")
            storePassword ="HvVe26c5ss%87Cd3n#Vf"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            signingConfig = signingConfigs.getByName("appDebug")
        }
        release {
            isMinifyEnabled = false
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.database)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.koin.android)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil)
    implementation(libs.coil.network)
    implementation(libs.napier)

    /* Use prebuilt libraries modules */
    debugImplementation(files(getLibraryAarFilePath(rootDir, "AlpsCore", "debug")))
    debugImplementation(files(getLibraryAarFilePath(rootDir, "AlpsSamples", "debug")))

    releaseImplementation(files(getLibraryAarFilePath(rootDir,"AlpsCore", "release")))
    releaseImplementation(files(getLibraryAarFilePath(rootDir, "AlpsSamples", "release")))
}

licenseReport {
    generateCsvReport = false
    generateHtmlReport = true
    generateJsonReport = true
    generateTextReport = false

    copyCsvReportToAssets = false
    copyHtmlReportToAssets = false
    copyJsonReportToAssets = false
    copyTextReportToAssets = false
    useVariantSpecificAssetDirs = false

    showVersions = true
}