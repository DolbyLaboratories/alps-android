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

import androidx.media3.common.C
import androidx.media3.common.C.TRACK_TYPE_AUDIO
import androidx.media3.common.C.TRACK_TYPE_VIDEO
import androidx.media3.common.Format
import androidx.media3.exoplayer.dash.manifest.AdaptationSet
import androidx.media3.exoplayer.dash.manifest.BaseUrl
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.dash.manifest.Period
import androidx.media3.exoplayer.dash.manifest.RangedUri
import androidx.media3.exoplayer.dash.manifest.Representation
import androidx.media3.exoplayer.dash.manifest.SegmentBase
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import java.util.stream.Stream

class DashManifestProvider: ArgumentsProvider {

    override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
        return Stream.of(
            null,
            mockedRelaxedManifest,
            createMockedManifest()
        ).map(Arguments::of)
    }

    private val mockedRelaxedManifest = mockk<DashManifest>(relaxed = true)

    fun createMockedManifest() = DashManifest(0, 0, 0, false, 0, 0L, 0L, 0L, null, null, null, null,
        listOf(
            createMockedPeriod(
                adaptationSetsList = listOf(
                    createMockedAdaptationSet(
                        trackType = TRACK_TYPE_VIDEO,
                        containerMimeType = "video/mp4",
                        sampleMimeType = "video/avc",
                        baseUrl = "https://hobbitwo-content.s3.eu-central-1.amazonaws.com/PATS/514_MED_5pres/video/avc1/",
                        initUrl = "https://hobbitwo-content.s3.eu-central-1.amazonaws.com/PATS/514_MED_5pres/video/avc1/init.mp4",
                    ),
                    createMockedAdaptationSet(
                        trackType = TRACK_TYPE_AUDIO,
                        containerMimeType = "audio/mp4",
                        sampleMimeType = "audio/ac4",
                        baseUrl = "https://hobbitwo-content.s3.eu-central-1.amazonaws.com/PATS/514_MED_5pres/audio/und/ac-4/",
                        initUrl = "https://hobbitwo-content.s3.eu-central-1.amazonaws.com/PATS/514_MED_5pres/audio/und/ac-4/init.mp4",
                    ),
                    createMockedAdaptationSet(
                        trackType = TRACK_TYPE_AUDIO,
                        containerMimeType = "audio/mp4",
                        sampleMimeType = "audio/ac4",
                        baseUrl = "https://hobbitwo-content.s3.eu-central-1.amazonaws.com/PATS/514_MED_5pres/audio/und/ac-4/",
                        initUrl = "https://hobbitwo-content.s3.eu-central-1.amazonaws.com/PATS/514_MED_5pres/audio/und/ac-4/init.mp4",
                    )
                )
            )
        )
    )

    private fun createMockedPeriod(adaptationSetsList: List<AdaptationSet>) = Period(
        "0",
        0,
        adaptationSetsList
    )

    private fun createMockedAdaptationSet(
        trackType: @C.TrackType Int,
        containerMimeType: String,
        sampleMimeType: String,
        baseUrl: String,
        initUrl: String,
    ) = AdaptationSet(
        0L,
        trackType,
        listOf(
            Representation.newInstance(
                1,
                Format.Builder().setContainerMimeType(containerMimeType).setSampleMimeType(sampleMimeType).build(),
                listOf(
                    BaseUrl(baseUrl)
                ),
                SegmentBase.SingleSegmentBase(
                    mockk<RangedUri>(relaxed = true) {
                        every { resolveUriString(any()) } returns initUrl },
                    1,
                    1,
                    1,
                    1
                )
            )
        ),
        emptyList(),
        emptyList(),
        emptyList()
    )
}
