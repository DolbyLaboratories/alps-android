import buildscriptutils.getVersionName
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import buildscriptutils.Module
import buildscriptutils.checkAndFixCopyrightNoticesHeaders
import buildscriptutils.checkNewDependenciesOrLicensesInTheProject
import buildscriptutils.copyThirdPartyLicensesReports

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.navigation.safeargs) apply false
    alias(libs.plugins.dokka) apply true
}

subprojects {
    plugins.apply("org.jetbrains.dokka")

    tasks.withType<DokkaTaskPartial>().configureEach {
        dokkaSourceSets.configureEach {
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

tasks.dokkaHtmlMultiModule {
    moduleVersion.set(getVersionName(rootDir, Module.APP))
}

tasks.named<Delete>("clean") {
    delete(rootDir.resolve("build"))
}

tasks.register("checkCopyrightNotice") {
    group = "verification"
    description = "Check if files with .kt, .cpp, and .h extensions start with a required notice. " +
            "If not, throw an exception and add copyright notice where missing."

    doLast {
        checkAndFixCopyrightNoticesHeaders(projectDir)
    }
}

tasks.register("generateAndCopyDependenciesLicenses") {
    group = "reporting"
    description = "Generates licenses reports and copies them to proper location"

    // Execute licenseDebugUnitTestReport for each submodule. licenseDebugUnitTestReport is used
    // because it includes main configuration dependencies + unit tests related dependencies.
    subprojects.forEach { subproject ->
        if (subproject.name != "buildSrc") {
            dependsOn("${subproject.path}:licenseDebugUnitTestReport")
        }
    }

    doLast {
        subprojects.forEach { subproject ->
            if (subproject.name != "buildSrc") {
                copyThirdPartyLicensesReports(File(subproject.rootDir.path, subproject.name).path)
            }
        }
    }
}

tasks.register("checkThirdPartyLicenses") {
    group = "reporting"
    description = "Checks if there are any new third party dependencies/licenses used. If any new " +
            "dependency is added, exception will be thrown to verify it manually."

    // Execute licenseDebugUnitTestReport for each submodule. licenseDebugUnitTestReport is used
    // because it includes main configuration dependencies + unit tests related dependencies.
    subprojects.forEach { subproject ->
        if (subproject.name != "buildSrc") {
            dependsOn("${subproject.path}:licenseDebugUnitTestReport")
        }
    }

    doLast {
        subprojects.forEach { subproject ->
            if (subproject.name != "buildSrc") {
                println("Checking ${subproject.name}...")
                checkNewDependenciesOrLicensesInTheProject(File(subproject.rootDir.path, subproject.name).path)
            }
        }
    }
}
