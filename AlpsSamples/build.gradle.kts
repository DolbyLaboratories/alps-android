import buildscriptutils.Module
import buildscriptutils.getVersionName
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.archivesName

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.junit5)
    alias(libs.plugins.license)
}

private val libraryVersionName = getVersionName(rootDir, Module.LIBRARY)

android {
    namespace = "com.dolby.android.alps.samples"
    compileSdk = 34

    defaultConfig {
        minSdk = 29

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // Add version to library archive name
        archivesName.set("${project.name}-$libraryVersionName")
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
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":AlpsCore"))
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.kotlinx.coroutines.core)

    //Tests
    testImplementation(libs.jupiter.api)
    testRuntimeOnly(libs.jupiter.engine)
    testImplementation(libs.jupiter.params)
    testImplementation(libs.assertk)
    testImplementation(libs.mockk)
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed")
    }
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
