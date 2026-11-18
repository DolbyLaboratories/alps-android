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

package com.dolby.android.alps.samples.hls

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker
import androidx.media3.exoplayer.hls.playlist.HlsRedundantGroup
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.dolby.android.alps.samples.AlpsManager
import com.dolby.android.alps.samples.AlpsManagerTest
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test

/**
 * Runs the shared [AlpsManagerTest] contract against [AlpsManagerHls].
 *
 * A "stream" maps to an HLS multivariant playlist (content or interstitial) carrying a single AC-4
 * audio rendition: preparing it is what the manager's playlist tracker does when the multivariant
 * playlist is loaded, playing it is what happens when playback moves to that stream.
 */
@UnstableApi
class AlpsManagerHlsTest : AlpsManagerTest() {

    private val hlsManager get() = manager as AlpsManagerHls

    private val streamUris = mutableMapOf<Int, Uri>()
    private val renditionUris = mutableMapOf<Int, Uri>()

    override fun createManager(presentationSelectionPersistenceEnabled: Boolean): AlpsManager =
        AlpsManagerHls(presentationSelectionPersistenceEnabled)
            .apply { alpsFactory = ::createAlps }

    override fun prepareStream(index: Int) {
        hlsManager.onMultivariantPlaylistLoaded(streamUri(index), multivariantPlaylist(index))
    }

    override fun playStream(index: Int) {
        hlsManager.setCurrentStreamUri(streamUri(index))
    }

    @Test
    fun `selection keeps working after content steering switches to a cloned pathway`() {
        val audioGroup = HlsRedundantGroup(
            HlsRedundantGroup.GroupKey(AC4_TRACK_FORMAT, /* stableId = */ null, AC4_RENDITION_NAME),
            /* pathwayId = */ "CDN-A",
            renditionUri(0),
            /* indexInMultivariantPlaylist = */ 0,
        )
        loadStreamThroughPlaylistTracker(index = 0, audioGroups = listOf(audioGroup))
        playStream(0)
        val player = playerSelectingAudioTrack(AC4_TRACK_FORMAT)
        hlsManager.onEvents(player, eventsOf(AnalyticsListener.EVENT_TRACKS_CHANGED))
        manager.setActivePresentationId(SELECTED_PRESENTATION_ID)

        // Content steering clones the pathway at runtime and switches playback to it.
        audioGroup.put("CDN-C", uriMock("cdn-c/stream0/ac4.m3u8"))
        audioGroup.setCurrentPathwayId("CDN-C")
        hlsManager.onEvents(player, eventsOf(AnalyticsListener.EVENT_TRACKS_CHANGED))

        assertThat(manager.getActivePresentationId()).isEqualTo(SELECTED_PRESENTATION_ID)
        assertThat(manager.isobmffPresentations.value.single { it.isActive }.id)
            .isEqualTo(SELECTED_PRESENTATION_ID)
    }

    /**
     * Loads stream [index] the way a player does: through [AlpsManagerHls.playlistTrackerFactory],
     * with a delegate tracker that exposes [audioGroups] as its audio redundant groups.
     */
    private fun loadStreamThroughPlaylistTracker(index: Int, audioGroups: List<HlsRedundantGroup>) {
        val primaryPlaylistListener = slot<HlsPlaylistTracker.PrimaryPlaylistListener>()
        val delegate = mockk<HlsPlaylistTracker>(relaxed = true) {
            every { start(any(), any(), capture(primaryPlaylistListener), any()) } just Runs
            every { multivariantPlaylist } returns multivariantPlaylist(index)
            every { getRedundantGroups(HlsRedundantGroup.AUDIO_RENDITION) } returns audioGroups
        }
        hlsManager.delegateTrackerFactory = HlsPlaylistTracker.Factory { _, _, _, _, _ -> delegate }

        val tracker = hlsManager.playlistTrackerFactory.createTracker(
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            /* cmcdConfiguration = */ null,
            mockk(relaxed = true),
        )
        tracker.start(streamUri(index), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
        primaryPlaylistListener.captured.onPrimaryPlaylistRefreshed(mockk(relaxed = true))
    }

    private fun playerSelectingAudioTrack(format: Format): Player {
        val audioGroup = Tracks.Group(
            TrackGroup(format),
            /* adaptiveSupported = */ false,
            intArrayOf(C.FORMAT_HANDLED),
            booleanArrayOf(true),
        )
        return mockk { every { currentTracks } returns Tracks(listOf(audioGroup)) }
    }

    private fun eventsOf(event: Int): AnalyticsListener.Events = mockk {
        every { contains(any()) } answers { firstArg<Int>() == event }
    }

    private fun streamUri(index: Int): Uri = streamUris.getOrPut(index) {
        uriMock("stream$index/main.m3u8")
    }

    private fun renditionUri(index: Int): Uri = renditionUris.getOrPut(index) {
        uriMock("stream$index/ac4.m3u8")
    }

    // Manager only uses `.path` of Uri's, so this is enough, and avoids pulling
    // in Roboelectric (Uri is generally supplied by the Android framework.)
    private fun uriMock(path: String): Uri = mockk<Uri>(relaxed = true, name = path) {
        every { this@mockk.path } returns "/$path"
    }

    private fun multivariantPlaylist(index: Int): HlsMultivariantPlaylist {
        val ac4Rendition = HlsMultivariantPlaylist.Rendition(
            renditionUri(index),
            Format.Builder()
                .setId("audio:ac4-$index")
                .setSampleMimeType(MimeTypes.AUDIO_AC4)
                .build(),
            "audio",
            AC4_RENDITION_NAME,
            /* stableRenditionId = */ null,
        )
        return HlsMultivariantPlaylist(
            /* baseUri = */ "https://example.com/stream$index/main.m3u8",
            /* tags = */ emptyList(),
            /* variants = */ emptyList(),
            /* videos = */ emptyList(),
            /* audios = */ listOf(ac4Rendition),
            /* subtitles = */ emptyList(),
            /* closedCaptions = */ emptyList(),
            /* muxedAudioFormat = */ null,
            /* muxedCaptionFormats = */ emptyList(),
            /* hasIndependentSegments = */ false,
            /* variableDefinitions = */ emptyMap(),
            /* sessionKeyDrmInitData = */ emptyList(),
            /* contentSteeringInfo = */ null,
        )
    }

    private companion object {
        const val AC4_RENDITION_NAME = "AC-4"

        /** Audio track format as media3 exposes it: the rendition `NAME` becomes the label. */
        val AC4_TRACK_FORMAT: Format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AC4)
            .setLabel(AC4_RENDITION_NAME)
            .build()
    }
}
