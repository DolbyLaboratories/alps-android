import buildscriptutils.Module
import buildscriptutils.getVersionName
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.archivesName

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.junit5)
    alias(libs.plugins.license)
}

private val libraryVersionName = getVersionName(rootDir, Module.LIBRARY)

android {
    namespace = "com.dolby.android.alps"
    compileSdk = 34

    defaultConfig {
        minSdk = 29

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // Access to library version from code
        buildConfigField("String", "ALPS_VERSION", "\"$libraryVersionName\"")

        // Add version to library archive name
        archivesName.set("${project.name}-$libraryVersionName")

        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=c++_shared")
                cppFlags("-std=c++14")
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildFeatures {
        buildConfig = true
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
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
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
    //Tests
    testImplementation(libs.jupiter.api)
    testRuntimeOnly(libs.jupiter.engine)
    testImplementation(libs.jupiter.params)
    testImplementation(libs.assertk)
    testImplementation(libs.mockk)
}

tasks.withType<DokkaTask>().configureEach {
    moduleVersion.set(libraryVersionName)
    // This configuration makes internal objects included in generated documentation
    dokkaSourceSets {
        configureEach {
            documentedVisibilities.set(
                setOf(
                    DokkaConfiguration.Visibility.PUBLIC,
                    DokkaConfiguration.Visibility.PROTECTED,
                    DokkaConfiguration.Visibility.INTERNAL,
                    )
            )
        }
    }
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.named<Delete>("clean") {
    delete(projectDir.resolve(".cxx"))
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