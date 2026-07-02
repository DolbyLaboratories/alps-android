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
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.UriUtil
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistTracker
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker
import com.dolby.android.alps.Alps
import com.dolby.android.alps.PresentationsChangedCallback
import com.dolby.android.alps.logger.AlpsLoggerProvider
import com.dolby.android.alps.models.Label
import com.dolby.android.alps.models.Presentation
import com.dolby.android.alps.samples.AlpsManager
import com.dolby.android.alps.samples.AlpsManager.Companion.TV_DEFAULT_PRESENTATION
import com.dolby.android.alps.samples.models.AlpsPresentationWrapper
import com.dolby.android.alps.utils.AlpsException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onErrorReturn
import kotlinx.coroutines.flow.update
import kotlin.collections.iterator

@UnstableApi
class AlpsManagerHls(
    val presentationSelectionPersistenceEnabled: Boolean = true
) : AlpsManager {
    private val _isobmffPresentations =
        MutableStateFlow<List<AlpsPresentationWrapper>>(emptyList())
    override val isobmffPresentations: StateFlow<List<AlpsPresentationWrapper>> =
        _isobmffPresentations

    private val _playlistPresentations =
        MutableStateFlow<Map<Uri,List<AlpsPresentationWrapper>>>(emptyMap())
    /**
     * State keeping lists of presentations signalled in HLS playlists for all loaded streams
     */
    val playlistPresentations: StateFlow<Map<Uri,List<AlpsPresentationWrapper>>> = _playlistPresentations

    private var _activeStreamUri = MutableStateFlow<Uri?>(null)

    /**
     * State keeping the URI of the currently playing stream (content or interstitial)
     */
    val activeStreamUri: StateFlow<Uri?> = _activeStreamUri
    // -------------------------------------------------------------------------
    // Playlist-tracker factory
    // -------------------------------------------------------------------------

    /**
     * A [androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker.Factory] that wraps
     * [androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistTracker.FACTORY].
     *
     * Inject this into `HlsMediaSource.Factory.setPlaylistTrackerFactory(...)` so the manager
     * can intercept the multivariant playlist to build the AC-4 rendition map, and hold
     * a reference to the tracker for [resolveAlpsForSegmentUri] lookups.
     */
    val playlistTrackerFactory: HlsPlaylistTracker.Factory =
        HlsPlaylistTracker.Factory { dataSourceFactory, loadErrorHandlingPolicy, playlistParserFactory, cmcdConfiguration ->
            val realTracker = DefaultHlsPlaylistTracker.FACTORY.createTracker(
                dataSourceFactory,
                loadErrorHandlingPolicy,
                playlistParserFactory,
                cmcdConfiguration,
            )
            DelegatingHlsPlaylistTracker(
                realTracker,
                onMultivariantPlaylist = { initialPlaylistUri, multivariantPlaylist ->
                    onMultivariantPlaylistLoaded(initialPlaylistUri, multivariantPlaylist)
                },
            ).also { wrapper ->
                playlistTrackers.add(wrapper)
            }
        }

    // -------------------------------------------------------------------------
    // Internal state
    // -------------------------------------------------------------------------

    private val isobmffPresentationsMap: MutableMap<Uri, List<Presentation>> = HashMap()
    private val playlistPresentationsMap: MutableMap<Uri, List<Presentation>> = HashMap()


    /** Last presentation ID set by the user. Re-applied on stream change if
     * [presentationSelectionPersistenceEnabled] is true */
    private var userPreferredPresentationId: Int? = null

    /**
     * All active [HlsPlaylistTracker]s — one per [HlsMediaSource] created via our factory.
     * With interstitials there will be one for the content stream plus one per interstitial.
     * Used by [resolveAlpsForSegmentUri] to query media playlist snapshots from any stream.
     */
    private val playlistTrackers: MutableList<HlsPlaylistTracker> = mutableListOf()

    /**
     * Per-stream AC-4 rendition data.
     * Key = multivariant playlist URI (unique per HLS stream — content or interstitial).
     * Value = map from AC-4 rendition media-playlist URI → dedicated [com.dolby.android.alps.Alps] instance.
     */
    private val streamAlpsMaps: MutableMap<Uri, MutableMap<Uri, Alps>> = mutableMapOf()

    // -------------------------------------------------------------------------
    // Public methods
    // -------------------------------------------------------------------------

    override fun setActivePresentationId(presentationId: Int) {
        userPreferredPresentationId = presentationId
        if (presentationSelectionPersistenceEnabled) {
            streamAlpsMaps.values.forEach { alpsMap ->
                alpsMap.values.forEach { it.setActivePresentationId(presentationId) }
            }
        } else {
            _activeStreamUri.value?.let { uri ->
                resolveAlpsInstanceForStreamUri(uri)?.setActivePresentationId(presentationId)
            }
        }
        updatePresentationsState()
    }

    override fun getActivePresentationId(): Int? =
        activeStreamUri.value?.let { uri ->
            resolveAlpsInstanceForStreamUri(uri)?.getActivePresentationId()
        }


    /**
     * Sets current playing stream URI value to [uri].
     *
     * This method should only be used if setting [AlpsManagerHls] as Player's [AnalyticsListener] is
     * not possible.
     *
     * @param uri uri of stream that is currently being played by player
     */
    fun setCurrentStreamUri(uri: Uri?) {
        _activeStreamUri.update { uri }
        updatePresentationsState()
    }

    override fun release() {
        streamAlpsMaps.values.forEach { alpsMap ->
            alpsMap.values.forEach { it.release() }
        }
        streamAlpsMaps.clear()
        playlistTrackers.clear()
        _activeStreamUri.update { null }
        playlistPresentationsMap.clear()
        isobmffPresentationsMap.clear()
        userPreferredPresentationId = null
    }

    // -------------------------------------------------------------------------
    // AnalyticsListener
    // -------------------------------------------------------------------------

    override fun onEvents(player: Player, events: AnalyticsListener.Events) {
        // Detect content ↔ interstitial transitions by resolving the currently *playing*
        // stream's multivariant playlist URI from the Player's timeline.
        if (events.contains(AnalyticsListener.EVENT_POSITION_DISCONTINUITY)
            || events.contains(AnalyticsListener.EVENT_TIMELINE_CHANGED)
            || events.contains(AnalyticsListener.EVENT_IS_PLAYING_CHANGED)
        ) {
            val newStreamUri = resolveCurrentPlayingStreamUri(player)
            if (newStreamUri != null && newStreamUri != activeStreamUri) {
                setCurrentStreamUri(newStreamUri)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Called by AlpsHlsSegmentDataSource
    // -------------------------------------------------------------------------

    /**
     * Resolves the [Alps] instance for the given [segmentUri], or `null` if the segment does not
     * belong to an AC-4 audio rendition.
     *
     * The lookup works as follows:
     * 1. For each AC-4 rendition URI in [streamAlpsMaps], fetch the current
     *    [androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist] snapshot from any tracker.
     * 2. Check whether any segment in that playlist, resolved against the playlist's `baseUri`,
     *    equals [segmentUri]. Also check the `#EXT-X-MAP` init segment URI.
     * 3. On match, return the [Alps] from the corresponding stream's map.
     *
     * @param segmentUri The fully-resolved URI of the segment (or init segment) being opened.
     * @return The [Alps] instance for this stream, or `null` if not AC-4.
     */
    internal fun resolveAlpsForSegmentUri(segmentUri: Uri): Alps? {
        if (playlistTrackers.isEmpty()) {
            return null
        }

        for ((_, alpsMap) in streamAlpsMaps) {
            for ((renditionUri, alps) in alpsMap) {
                for (tracker in playlistTrackers) {
                    val snapshot =
                        runCatching { tracker.getPlaylistSnapshot(renditionUri, false) }.getOrNull()
                            ?: continue

                    for (segment in snapshot.segments) {
                        val resolvedSegmentUri = UriUtil.resolveToUri(snapshot.baseUri, segment.url)
                        if (resolvedSegmentUri == segmentUri) {
                            return alps
                        }
                        segment.initializationSegment?.let { initSeg ->
                            val resolvedInitUri =
                                UriUtil.resolveToUri(snapshot.baseUri, initSeg.url)
                            if (resolvedInitUri == segmentUri) {
                                return alps
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    // -------------------------------------------------------------------------
    // Multivariant playlist analysis
    // -------------------------------------------------------------------------

    /**
     * Called by [DelegatingHlsPlaylistTracker] each time a multivariant playlist is loaded —
     * once for the **content** stream and once for **each interstitial**.
     *
     * The [streamUri] (the `initialPlaylistUri` from the tracker's [HlsPlaylistTracker.start]
     * call) uniquely identifies the HLS stream. AC-4 renditions are registered under this URI
     * in [streamAlpsMaps].
     *
     * Duplicate rendition URLs (from a live playlist refresh) are naturally prevented by
     * checking [streamAlpsMaps] membership before registration.
     */
    internal fun onMultivariantPlaylistLoaded(streamUri: Uri, playlist: HlsMultivariantPlaylist) {
        val alpsMap = streamAlpsMaps.getOrPut(streamUri) { mutableMapOf() }

        for (rendition in playlist.audios) {
            val url = rendition.url ?: continue
            if (url in alpsMap) continue
            val isAc4 = rendition.format.sampleMimeType == MimeTypes.AUDIO_AC4
            if (!isAc4) {
                continue
            }
            val alps = createAlpsInstance() ?: continue
            alpsMap[url] = alps
        }
        val presentations = playlist.tags
            .filter { it.startsWith("#EXT-X-MEDIA:TYPE=AUDIO") }
            .takeIf { tags ->
                tags.any { it.contains("INSTREAM-ID") }
            }
            ?.mapIndexed(::parseAudioTagToPresentation) ?: emptyList()
        playlistPresentationsMap[streamUri] = presentations
        updatePresentationsState(skipIsobmff = true)
    }

    /** Scratch [androidx.media3.common.Timeline.Period] to avoid allocations in [resolveCurrentPlayingStreamUri]. */
    private val scratchPeriod = Timeline.Period()

    /**
     * Resolves the multivariant playlist URI of the stream the player is currently **playing**
     * (not buffering).
     *
     * - **Content**: returns `player.currentMediaItem.localConfiguration.uri`.
     * - **Ad (interstitial)**: reads the ad's [MediaItem] from the timeline's
     *   [AdPlaybackState] and returns its URI. This is the interstitial's asset URI that
     *   [HlsInterstitialsAdsLoader] set when it called `withAvailableAdMediaItem(...)`.
     *
     * Returns `null` if the URI cannot be determined (e.g., empty timeline).
     */
    private fun resolveCurrentPlayingStreamUri(player: Player): Uri? {
        if (!player.isPlayingAd) {
            return player.currentMediaItem?.localConfiguration?.uri
        }
        // Playing an ad — resolve the ad's MediaItem URI from the timeline.
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return null
        return try {
            timeline.getPeriod(player.currentPeriodIndex, scratchPeriod)
            val adGroup = scratchPeriod.adPlaybackState.getAdGroup(player.currentAdGroupIndex)
            val adIndex = player.currentAdIndexInAdGroup
            if (adIndex < adGroup.mediaItems.size) {
                adGroup.mediaItems[adIndex]?.localConfiguration?.uri
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Extracts the value of the given key from the tag, or `null` if not found. Assumes the tag
     * is a comma-separated list of key=value pairs, where values may be quoted. */
    private fun extractTagAttribute(tag: String, key: String): String? =
        tag.split(",")
            .firstOrNull { it.trimStart().startsWith(key) }
            ?.substringAfter("=")
            ?.removeSurrounding("\"")

    /** Parses an `#EXT-X-MEDIA:TYPE=AUDIO` tag to a [Presentation]. Only the attributes relevant
     * to presentation selection are parsed; others are ignored. */
    private fun parseAudioTagToPresentation(index: Int, tag: String): Presentation {
        val language = extractTagAttribute(tag, "LANGUAGE") ?: ""

        return Presentation(
            id = extractTagAttribute(tag, "INSTREAM-ID")?.toIntOrNull() ?: index,
            extendedLanguage = language,
            kinds = emptyList(),
            labels =
                listOf(
                    Label(
                        labelId = 0,
                        language = language,
                        label = extractTagAttribute(tag, "NAME") ?: "",
                        isGroupLabel = false,
                    )
                ),
            selectionPriority = 0,
            audioRenderingIndication = 0,
            dialogGain = extractTagAttribute(tag, "X-DIALOG-GAIN")?.toFloatOrNull(),
        )
    }

    private fun createAlpsInstance(): Alps? {
        return try {
            Alps().apply {
                setPresentationsChangedCallback(object : PresentationsChangedCallback {
                    override fun onPresentationsChanged() {
                        updatePresentationsState()
                    }
                })
            }
        } catch (e: AlpsException) {
            AlpsLoggerProvider.e("failed to create Alps: ${e.message}")
            null
        }
    }

    private fun resolveAlpsInstanceForStreamUri(streamUri: Uri): Alps? {
        val alpsMap = streamAlpsMaps[streamUri] ?: return null
        return alpsMap.values.firstOrNull()
    }

    private fun updateIsobmffPresentations(currentStreamUri: Uri, alps: Alps) {
        try {
            val presentations = alps.getPresentations()
            isobmffPresentationsMap[currentStreamUri] = presentations
        } catch (e: AlpsException) {
            AlpsLoggerProvider.e("Failed to update presentations: ${e.message}")
        }
    }

    private fun updatePresentationsState(skipIsobmff: Boolean = false) {
        val currentStreamUri = _activeStreamUri.value

        var activePresentationId = TV_DEFAULT_PRESENTATION.id

        currentStreamUri?.let {
            resolveAlpsInstanceForStreamUri(currentStreamUri)?.let { alps ->
                try {
                    activePresentationId = alps.getActivePresentationId()
                } catch (e: AlpsException) {
                    AlpsLoggerProvider.e("Failed to fetch active presentation ID: ${e.message}")
                }

                if (!skipIsobmff) updateIsobmffPresentations(currentStreamUri, alps)
            }
        }

        val isobmffPresentations =
            isobmffPresentationsMap
                .getOrDefault(currentStreamUri, emptyList())
                .map {
                    AlpsPresentationWrapper.from(
                        it,
                        it.id == activePresentationId
                    )
                }

        val playlistPresentations =
            playlistPresentationsMap.map { (uri, presentations) ->
                uri to presentations.map {
                    AlpsPresentationWrapper.from(
                        presentation = it,
                        isActive = it.id == activePresentationId
                    )
                }
            }.toMap()
        _playlistPresentations.update {
            playlistPresentations
        }
        _isobmffPresentations.update { isobmffPresentations }
    }
}
