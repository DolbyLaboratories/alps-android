/***************************************************************************************************
 *                Copyright (C) 2024-2026 by Dolby International AB.
 *                All rights reserved.

 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:

 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 *    and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 *    conditions and the following disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 *    endorse or promote products derived from this software without specific prior written
 *    permission.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 **************************************************************************************************/

package buildscriptutils

import java.io.File
import java.util.Properties

data class Version(
    val major: Int,
    val minor: Int,
    val patch: String
)

fun getVersionName(projectRootDir: File): String {
    getVersion(projectRootDir).let {
        return "${it.major}.${it.minor}.${it.patch}"
    }
}

fun getVersionCode(projectRootDir: File): Int {
    getVersion(projectRootDir).let {
        return it.major * 10000 + it.minor * 100 + it.patch.replace(Regex("[^0-9]"), "").toInt()
    }
}

private fun getVersion(projectRootDir: File): Version {
    val properties = Properties()
    File(projectRootDir, "alps.properties").inputStream().use { properties.load(it) }
    val major = properties.getProperty("MAJOR").toInt()
    val minor = properties.getProperty("MINOR").toInt()
    val patch = properties.getProperty("PATCH").toString()

    val gitBranch = getGitBranch(projectRootDir)
    val patchWithBranch = if (gitBranch.isNotEmpty() && listOf(
            "main",
            "next",
            "release",
        ).contains(gitBranch).not()
    ) "$patch-$gitBranch" else patch

    return Version(major, minor, patchWithBranch)
}

private fun getGitBranch(projectRootDir: File): String {
    return try {
        val process = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
            .directory(projectRootDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        process.waitFor()
        if (process.exitValue() == 0) {
            val branch = process.inputStream.bufferedReader().readText().trim()
            // In GitLab CI the checkout is detached, so git returns the literal "HEAD".
            // On a tag pipeline CI_COMMIT_TAG is set — return empty so no suffix is appended.
            // On a branch pipeline fall back to CI_COMMIT_REF_NAME for the real branch name.
            if (branch == "HEAD") {
                if (!System.getenv("CI_COMMIT_TAG").isNullOrBlank()) {
                    ""
                } else {
                    System.getenv("CI_COMMIT_REF_NAME")?.replace("/", "-") ?: branch
                }
            } else {
                branch.replace("/", "-")
            }
        } else {
            val errorOutput = process.errorStream.bufferedReader().readText()
            throw IllegalStateException(
                "Failed to get git branch. Command failed with" +
                        " exit code ${process.exitValue()}: $errorOutput"
            )
        }
    } catch (e: Exception) {
        if (e is IllegalStateException) {
            throw e
        }
        throw IllegalStateException("Failed to get git branch: ${e.message}", e)
    }
}

fun updateVersion(projectRootDir: File, version: String?) {
    if (version == null || version.isEmpty()) {
        throw IllegalArgumentException("Version parameter is required")
    }

    val parts = version.split(".", limit = 3)
    if (parts.size != 3) {
        throw IllegalArgumentException("Version must be in format Major.Minor.Patch. Got $version")
    }

    val major = parts[0]
    val minor = parts[1]
    val patch = parts[2]

    val versionFile = File(projectRootDir, "alps.properties")
    versionFile.writeText(
        """
        MAJOR=$major
        MINOR=$minor
        PATCH=$patch
    """.trimIndent()
    )

    println("Updated version: MAJOR=$major, MINOR=$minor, PATCH=$patch")
}
