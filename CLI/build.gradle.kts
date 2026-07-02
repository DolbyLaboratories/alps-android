import buildscriptutils.getVersionCode
import buildscriptutils.getVersionName

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.license)
}

private val appVersionName = getVersionName(rootDir)
private val appVersionCode = getVersionCode(rootDir)

android {
    namespace = "com.dolby.android.alps.cli"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dolby.android.alps.cli"
        minSdk = 28
        targetSdk = 35
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

    implementation(project(":AlpsCore"))
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
