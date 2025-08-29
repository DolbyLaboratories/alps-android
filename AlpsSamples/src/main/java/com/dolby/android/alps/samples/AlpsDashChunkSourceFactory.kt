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

package com.dolby.android.alps.samples

import android.media.MediaFormat
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.dash.BaseUrlExclusionList
import androidx.media3.exoplayer.dash.DashChunkSource
import androidx.media3.exoplayer.dash.DefaultDashChunkSource
import androidx.media3.exoplayer.dash.PlayerEmsgHandler
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.source.chunk.BundledChunkExtractor
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.CmcdConfiguration
import androidx.media3.exoplayer.upstream.LoaderErrorThrower
import com.dolby.android.alps.logger.AlpsLoggerProvider
import com.dolby.android.alps.Alps

/**
 * [AlpsDashChunkSourceFactory] is a helper class that allows using ALPS for proper DASH chunks.
 * Main responsibility of this class is to detect whether ALPS processing should be applied to given
 * chunk. ALPS will be applied only to AC-4 audio bitstream chunks.
 *
 * For chunks that should be processed by ALPS, this class uses [alpsManager] to get ALPS object
 * assigned to proper DASH period and uses [AlpsHttpDataSource.Factory] to create [AlpsHttpDataSource]
 * and inject it into [DefaultDashChunkSource] object returned by [createDashChunkSource] method.
 *
 * For non-ALPS chunks [defaultHttpDataSourceFactory] will be used to create data source that will
 * be injected into [DefaultDashChunkSource].
 *
 * @param alpsManager needed to fetch [Alps] object assigned to specific period
 * @param defaultHttpDataSourceFactory used to create data source for non-ALPS chunks and injected
 * into [AlpsHttpDataSource.Factory] for ALPS chunks
 */
@UnstableApi
class AlpsDashChunkSourceFactory(
    private val alpsManager: AlpsManager,
    private val defaultHttpDataSourceFactory: HttpDataSource.Factory,
): DashChunkSource.Factory {
    companion object {
        /**
         * ALPS expects 1 segment per load
         */
        private const val MAX_SEGMENTS_PER_LOAD = 1

        private const val TAG = "AlpsDashChunkSourceFactory"
    }

    override fun createDashChunkSource(
        manifestLoaderErrorThrower: LoaderErrorThrower,
        manifest: DashManifest,
        baseUrlExclusionList: BaseUrlExclusionList,
        periodIndex: Int,
        adaptationSetIndices: IntArray,
        trackSelection: ExoTrackSelection,
        trackType: Int,
        elapsedRealtimeOffsetMs: Long,
        enableEventMessageTrack: Boolean,
        closedCaptionFormats: MutableList<Format>,
        playerEmsgHandler: PlayerEmsgHandler.PlayerTrackEmsgHandler?,
        transferListener: TransferListener?,
        playerId: PlayerId,
        cmcdConfiguration: CmcdConfiguration?
    ): DashChunkSource {
        val dataSource = createDataSource(
            trackSelection = trackSelection,
            periodIndex = periodIndex,
        )

        if (transferListener != null) {
            dataSource.addTransferListener(transferListener)
        }
        return DefaultDashChunkSource(
            BundledChunkExtractor.FACTORY,
            manifestLoaderErrorThrower,
            manifest,
            baseUrlExclusionList,
            periodIndex,
            adaptationSetIndices,
            trackSelection,
            trackType,
            dataSource,
            elapsedRealtimeOffsetMs,
            MAX_SEGMENTS_PER_LOAD,
            enableEventMessageTrack,
            closedCaptionFormats,
            playerEmsgHandler,
            playerId,
            cmcdConfiguration,
        )
    }

    private fun createDataSource(
        trackSelection: ExoTrackSelection,
        periodIndex: Int,
    ): DataSource {
        trackSelection.selectedFormat.sampleMimeType?.let { mimeType ->
            AlpsLoggerProvider.i(
                "$TAG-createDataSource mimeType: $mimeType"
            )

            if (mimeType.contains(MediaFormat.MIMETYPE_AUDIO_AC4)) {
                alpsManager.getAlps(periodIndex)?.let { alps ->
                    return AlpsHttpDataSource.Factory(
                        alps,
                        defaultHttpDataSourceFactory,
                    ).createDataSource()
                } ?: AlpsLoggerProvider.e("$TAG-createDataSource AC-4 track detected but failed to" +
                        "get Alps object. AlpsProcessing will not be applied to this track.")
            }
        } ?: AlpsLoggerProvider.e(
            "$TAG-createDataSource missing mimeType info - malformed content. ALPS processing" +
                    "will not be applied to this track."
        )

        return defaultHttpDataSourceFactory.createDataSource()
    }
}