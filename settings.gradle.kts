pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// The native sources are vendored in as the `repos/dlb_alps_native` submodule. A plain `git clone`
// leaves that directory empty, which only surfaces much later as an opaque CMake `add_subdirectory`
// error. Restore it here instead, so a fresh checkout builds without any extra steps. A submodule
// that is already populated is deliberately left untouched, so in-progress work is never reset.
run {
    val nativeRoot = File(settingsDir, "repos/dlb_alps_native")
    if (!File(nativeRoot, "CMakeLists.txt").exists()) {
        if (!File(settingsDir, ".git").exists()) {
            throw GradleException(
                "The ALPS Native sources are missing from $nativeRoot and this is not a git " +
                    "checkout, so they cannot be restored automatically.",
            )
        }
        println("Initializing the ALPS Native submodule in repos/dlb_alps_native...")
        val exitCode = ProcessBuilder("git", "submodule", "update", "--init", "--recursive")
            .directory(settingsDir)
            .inheritIO()
            .start()
            .waitFor()
        if (exitCode != 0) {
            throw GradleException(
                "'git submodule update --init --recursive' failed with exit code $exitCode. " +
                    "Run it manually to restore the ALPS Native sources in repos/dlb_alps_native.",
            )
        }
    }
}

rootProject.name = "Dolby ALPS"
include(":AlpsCore")
include(":AlpsSamples")
include(":CLI")
