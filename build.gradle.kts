import buildscriptutils.getVersionName
import buildscriptutils.loadLocalSigningProperties
import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.publish.PublishingExtension
import org.gradle.authentication.http.HttpHeaderAuthentication
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.gradle.DokkaTaskPartial
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
    alias(libs.plugins.maven.publish) apply false
}

// Set VERSION_NAME for all projects so the vanniktech maven-publish plugin can read it automatically.
allprojects {
    extra["VERSION_NAME"] = getVersionName(rootDir)
}

subprojects {
    plugins.apply("org.jetbrains.dokka")

    // Shared Maven Central publishing setup — applied automatically to any module
    // that uses the com.vanniktech.maven.publish plugin. POM metadata is read from
    // gradle.properties (root + per-module).
    pluginManager.withPlugin("com.vanniktech.maven.publish") {
        loadLocalSigningProperties(rootDir).forEach { (key, value) ->
            extensions.extraProperties.set(key, value)
        }

        val signingEnabled = project.findProperty("signing.keyId") != null ||
                project.findProperty("signingInMemoryKey") != null

        extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
            configure(AndroidSingleVariantLibrary(variant = "release", sourcesJar = true, publishJavadocJar = true))
            publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
            if (signingEnabled) {
                signAllPublications()
            }
        }

        // Register the GitLab Package Registry as an additional Maven repository for internal
        // alpha publishing. Only active in CI.
        val gitlabApiUrl = System.getenv("CI_API_V4_URL")
        val gitlabProjectId = System.getenv("CI_PROJECT_ID")
        val gitlabJobToken = System.getenv("CI_JOB_TOKEN")
        if (gitlabApiUrl != null && gitlabProjectId != null && gitlabJobToken != null) {
            extensions.configure<PublishingExtension>("publishing") {
                repositories {
                    maven {
                        name = "GitLab"
                        url = uri("$gitlabApiUrl/projects/$gitlabProjectId/packages/maven")
                        credentials(HttpHeaderCredentials::class) {
                            name = "Job-Token"
                            value = gitlabJobToken
                        }
                        authentication {
                            create<HttpHeaderAuthentication>("header")
                        }
                    }
                }
            }
        }
    }

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
    moduleName.set("ALPS Android")
    moduleVersion.set(getVersionName(rootDir))
    includes.from("README.md")
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


tasks.register("updateVersion") {
    val versionParam = project.findProperty("version")?.toString()

    doLast {
        buildscriptutils.updateVersion(rootDir, versionParam)
    }
}
