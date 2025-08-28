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

import androidx.media3.exoplayer.dash.manifest.DashManifest
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import java.util.stream.Stream

class ManifestWithUriProvider: ArgumentsProvider {
    override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
        val mockedManifest = DashManifestProvider().createMockedManifest()
        return Stream.of(
            ManifestWithUriAndResult(
                manifest = mockedManifest,
                uriForDetection = "/PATS/514_MED_5pres/stream.mpd",
                ac4DetectionResult = false
            ),
            ManifestWithUriAndResult(
                manifest = mockedManifest,
                uriForDetection = "/PATS/514_MED_5pres/video/avc1/init.mp4",
                ac4DetectionResult = false
            ),
            ManifestWithUriAndResult(
                manifest = mockedManifest,
                uriForDetection = "/PATS/514_MED_5pres/audio/und/ac-4/init.mp4",
                ac4DetectionResult = true
            ),
            ManifestWithUriAndResult(
                manifest = mockedManifest,
                uriForDetection = "/2.0/CM/2.0_CM_2pres_25fps_manual/stream.mpd",
                ac4DetectionResult = false
            ),
            ManifestWithUriAndResult(
                manifest = mockedManifest,
                uriForDetection = "/2.0/CM/2.0_CM_2pres_25fps_manual/video/avc1/init.mp4",
                ac4DetectionResult = false
            ),
            ManifestWithUriAndResult(
                manifest = mockedManifest,
                uriForDetection = "/2.0/CM/2.0_CM_2pres_25fps_manual/audio/und/ac-4.02.01.00/init.mp4",
                ac4DetectionResult = false
            ),
        ).map(Arguments::of)
    }
}

data class ManifestWithUriAndResult(
    val manifest: DashManifest,
    val uriForDetection: String,
    val ac4DetectionResult: Boolean
)