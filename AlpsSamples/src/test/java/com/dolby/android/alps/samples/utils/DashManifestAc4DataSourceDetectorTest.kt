/***************************************************************************************************
 *                Copyright (C) 2024 by Dolby International AB.
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

package com.dolby.android.alps.samples.utils

import android.net.Uri
import androidx.media3.exoplayer.dash.manifest.DashManifest
import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ArgumentsSource

class DashManifestAc4DataSourceDetectorTest {
    private lateinit var ac4DataSourceDetector: DashManifestAc4DataSourceDetector

    @Nested
    inner class Base {
        @ParameterizedTest
        @ArgumentsSource(DashManifestProvider::class)
        fun `manifest set correctly on constructor call`(dashManifest: DashManifest?) {
            ac4DataSourceDetector = DashManifestAc4DataSourceDetector(dashManifest)

            assertThat(ac4DataSourceDetector.manifest).isEqualTo(dashManifest)
        }

        @ParameterizedTest
        @ArgumentsSource(DashManifestProvider::class)
        fun `manifest set correctly after constructor call`(dashManifest: DashManifest?) {
            ac4DataSourceDetector = DashManifestAc4DataSourceDetector(null)

            ac4DataSourceDetector.manifest = dashManifest

            assertThat(ac4DataSourceDetector.manifest).isEqualTo(dashManifest)
        }
    }

    @Nested
    inner class Ac4Detection {
        @Test
        fun `isAc4DataSource returns false if Uri does not provide path`() {
            ac4DataSourceDetector = DashManifestAc4DataSourceDetector(null)
            val mockedUri = mockk<Uri>(relaxed = true) {
                every { path } returns null
            }

            val detectionResult = ac4DataSourceDetector.isAc4DataSource(mockedUri)

            assertThat(detectionResult).isEqualTo(false)
        }

        @ParameterizedTest
        @ArgumentsSource(UriProvider::class)
        fun `isAc4DataSource returns false if manifest was not provided`(mockedUri: Uri) {
            ac4DataSourceDetector = DashManifestAc4DataSourceDetector(null)

            val detectionResult = ac4DataSourceDetector.isAc4DataSource(mockedUri)

            assertThat(detectionResult).isEqualTo(false)
        }

        @ParameterizedTest
        @ArgumentsSource(ManifestWithUriProvider::class)
        fun `isAc4DataSource returns correct detection result`(manifestWithUrisTestCases: ManifestWithUriAndResult) {
            ac4DataSourceDetector = DashManifestAc4DataSourceDetector(manifestWithUrisTestCases.manifest)
            val mockedUri = mockk<Uri>(relaxed = true) {
                every { path } returns manifestWithUrisTestCases.uriForDetection
            }

            val detectionResult = ac4DataSourceDetector.isAc4DataSource(mockedUri)

            assertThat(detectionResult).isEqualTo(manifestWithUrisTestCases.ac4DetectionResult)
        }
    }
}