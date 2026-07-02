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

/**
 * Loads GPG signing properties from local.properties and returns them as a map.
 * Returns an empty map if the file does not exist or keys are missing/still placeholders.
 *
 * Supported keys in local.properties:
 *   signing.keyId              - last 8 chars of the GPG key fingerprint
 *   signing.password           - passphrase of the GPG key
 *   signing.secretKeyRingFile  - absolute path to the exported .gpg secring file
 *
 * These are the same property names that Gradle's signing plugin reads from
 * gradle.properties. Loading them here and forwarding to ext[] makes signing work
 * without putting secrets into any committed file.
 *
 * NOTE: Maven Central credentials (mavenCentralUsername / mavenCentralPassword) are NOT
 * loaded here because the vanniktech maven-publish plugin reads them via
 * providers.gradleProperty(), which only resolves true Gradle properties — not ext[].
 * Store those credentials in ~/.gradle/gradle.properties instead.
 */
fun loadLocalSigningProperties(projectRootDir: File): Map<String, String> {
    val localProps = File(projectRootDir, "local.properties")
    if (!localProps.exists()) return emptyMap()

    val props = Properties().apply { localProps.inputStream().use { load(it) } }

    val keyId    = props.getProperty("signing.keyId")
    val password = props.getProperty("signing.password")
    val keyRing  = props.getProperty("signing.secretKeyRingFile")

    return if (
        !keyId.isNullOrBlank() &&
        !password.isNullOrBlank() &&
        !keyRing.isNullOrBlank()
    ) {
        mapOf(
            "signing.keyId"             to keyId,
            "signing.password"          to password,
            "signing.secretKeyRingFile" to keyRing,
        )
    } else {
        emptyMap()
    }
}