import buildscriptutils.Module
import buildscriptutils.getLibraryAarFilePath
import buildscriptutils.getVersionCode
import buildscriptutils.getVersionName

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.license)
}

private val appVersionName = getVersionName(rootDir, Module.CLI)
private val appVersionCode = getVersionCode(rootDir, Module.CLI)

android {
    namespace = "com.dolby.android.alps.cli"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dolby.android.alps.cli"
        minSdk = 29
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work)
    implementation(libs.androidx.work.ktx)
    implementation(libs.napier)

    /* Use prebuilt library module */
    debugImplementation(files(getLibraryAarFilePath(rootDir, "AlpsCore", "debug")))
    releaseImplementation(files(getLibraryAarFilePath(rootDir,"AlpsCore", "release")))
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