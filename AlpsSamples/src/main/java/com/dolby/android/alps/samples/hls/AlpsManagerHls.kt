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
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.UriUtil
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistTracker
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker
import androidx.media3.exoplayer.hls.playlist.HlsRedundantGroup
import com.dolby.android.alps.Alps
import com.dolby.android.alps.PresentationsChangedCallback
import com.dolby.android.alps.annotations.ExperimentalAlpsApi
import com.dolby.android.alps.logger.AlpsLoggerProvider
import com.dolby.android.alps.models.Label
import com.dolby.android.alps.models.Presentation
import com.dolby.android.alps.samples.AlpsManager
import com.dolby.android.alps.samples.AlpsManager.Companion.TV_DEFAULT_PRESENTATION
import com.dolby.android.alps.samples.models.AlpsPresentationWrapper
import com.dolby.android.alps.utils.AlpsException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.to

@UnstableApi
class AlpsManagerHls(
    val presentationSelectionPersistenceEnabled: Boolean = true,
) : AlpsManager {
    companion object {
        /**
         * List of [AnalyticsListener] events that may indicate a change in the active audio
         * rendition.
         */
        private val renditionChangedEvents = listOf(
            AnalyticsListener.EVENT_TRACKS_CHANGED,
            AnalyticsListener.EVENT_POSITION_DISCONTINUITY,
            AnalyticsListener.EVENT_TIMELINE_CHANGED,
        )

        /**
         * List of [AnalyticsListener] events that may indicate a change in the active stream
         * (content ↔ interstitial).
         */
        private val activeStreamUriChangedEvents = listOf(
            AnalyticsListener.EVENT_POSITION_DISCONTINUITY,
            AnalyticsListener.EVENT_TIMELINE_CHANGED,
            AnalyticsListener.EVENT_IS_PLAYING_CHANGED,
        )
    }

    private val _isobmffPresentations =
        MutableStateFlow<List<AlpsPresentationWrapper>>(emptyList())
    override val isobmffPresentations: StateFlow<List<AlpsPresentationWrapper>> =
        _isobmffPresentations

    private val _playlistPresentations =
        MutableStateFlow<Map<Uri, List<AlpsPresentationWrapper>>>(emptyMap())

    /**
     * State keeping lists of presentations signalled in HLS playlists for all loaded streams
     */
    @Deprecated(
        message = "playlistPresentations merges presentations from all audio renditions, " +
                "use playlistPresentationsForActiveRendition instead",
        replaceWith = ReplaceWith("playlistPresentationsForActiveRendition"),
        level = DeprecationLevel.WARNING,
    )
    @ExperimentalAlpsApi(reason = "Dolby AC-4 presentations signalling in HLS playlists " +
            "definition is experimental")
    val playlistPresentations: StateFlow<Map<Uri, List<AlpsPresentationWrapper>>> =
        _playlistPresentations

    private val _playlistPresentationsForActiveRendition =
        MutableStateFlow<Map<Uri, List<AlpsPresentationWrapper>>>(emptyMap())

    /**
     * State keeping lists of presentations signalled in HLS playlists for all loaded streams.
     * Presentations list will only contain presentations for the currently selected audio rendition,
     * or presentations of all renditions if no selection is known yet.
     */
    @ExperimentalAlpsApi(reason = "Dolby AC-4 presentations signalling in HLS playlists " +
            "definition is experimental")
    val playlistPresentationsForActiveRendition: StateFlow<Map<Uri, List<AlpsPresentationWrapper>>> =
        _playlistPresentationsForActiveRendition

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
        HlsPlaylistTracker.Factory { dataSourceFactory, loadErrorHandlingPolicy, playlistParserFactory, cmcdConfiguration, downloadExecutorSupplier ->
            val realTracker = delegateTrackerFactory.createTracker(
                dataSourceFactory,
                loadErrorHandlingPolicy,
                playlistParserFactory,
                cmcdConfiguration,
                downloadExecutorSupplier,
            )
            DelegatingHlsPlaylistTracker(
                realTracker,
                onMultivariantPlaylist = { tracker, initialPlaylistUri, multivariantPlaylist ->
                    streamPlaylistTrackers[initialPlaylistUri] = tracker
                    onMultivariantPlaylistLoaded(initialPlaylistUri, multivariantPlaylist)
                },
            ).also { wrapper -> playlistTrackers.add(wrapper) }
        }

    // -------------------------------------------------------------------------
    // Internal state
    // -------------------------------------------------------------------------

    private val isobmffPresentationsMap: MutableMap<Uri, List<Presentation>> = HashMap()
    private val playlistPresentationsMap: MutableMap<Uri, Map<String, List<Presentation>>> =
        HashMap()

    /**
     * Per-stream [HlsPlaylistTracker], used to map a selected audio track back to its rendition
     * media-playlist URI through the tracker's audio [HlsRedundantGroup]s.
     * Key = multivariant playlist URI (unique per HLS stream — content or interstitial).
     */
    private val streamPlaylistTrackers: MutableMap<Uri, HlsPlaylistTracker> = mutableMapOf()

    /**
     * Media-playlist URI of the currently selected audio rendition for the active stream, or `null`
     * when it has not been resolved yet (e.g. before the first `EVENT_TRACKS_CHANGED`). When `null`,
     * control-path lookups fall back to the first AC-4 rendition, preserving single-rendition
     * behavior.
     *
     * For an AC-4 rendition this is the URI registered in [streamAlpsMaps], not the URI of the
     * pathway currently in use, so it stays stable across content-steering pathway switches.
     */
    private var activeRenditionUri: Uri? = null

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

    internal var alpsFactory: () -> Alps = { Alps() }

    /** Factory of the real trackers wrapped by [playlistTrackerFactory]. Replaceable in tests. */
    internal var delegateTrackerFactory: HlsPlaylistTracker.Factory = DefaultHlsPlaylistTracker.FACTORY

    // -------------------------------------------------------------------------
    // Public methods
    // -------------------------------------------------------------------------

    override fun setActivePresentationId(presentationId: Int) {
        userPreferredPresentationId = presentationId
        if (presentationSelectionPersistenceEnabled) {
            allAlpsInstances().forEach { it.setActivePresentationId(presentationId) }
        } else {
            _activeStreamUri.value?.let { uri ->
                resolveActiveAlpsInstance(uri)?.setActivePresentationId(presentationId)
            }
        }
        updatePresentationsState()
    }

    override fun getActivePresentationId(): Int? =
        activeStreamUri.value?.let { uri ->
            resolveActiveAlpsInstance(uri)?.getActivePresentationId()
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
        allAlpsInstances().forEach { it.release() }
        streamAlpsMaps.clear()
        streamPlaylistTrackers.clear()
        activeRenditionUri = null
        playlistTrackers.clear()
        _activeStreamUri.update { null }
        playlistPresentationsMap.clear()
        isobmffPresentationsMap.clear()
        _isobmffPresentations.update { emptyList() }
        userPreferredPresentationId = null
    }

    // -------------------------------------------------------------------------
    // AnalyticsListener
    // -------------------------------------------------------------------------

    override fun onEvents(player: Player, events: AnalyticsListener.Events) {
        // Detect content ↔ interstitial transitions by resolving the currently *playing*
        // stream's multivariant playlist URI from the Player's timeline.
        if (activeStreamUriChangedEvents.any { events.contains(it) }) {
            val newStreamUri = resolveCurrentPlayingStreamUri(player)
            if (newStreamUri != null && newStreamUri != _activeStreamUri.value) {
                setCurrentStreamUri(newStreamUri)
            }
        }

        // Resolve the currently selected audio rendition so control-path lookups target the
        // correct AC-4 instance when a stream carries multiple AC-4 (or AC-4 + non-AC-4) tracks.
        if (renditionChangedEvents.any { events.contains(it) }) {
            updateActiveRendition(player)
        }
    }

    /**
     * Resolves the media-playlist URI of the audio rendition currently selected in [player] for the
     * active stream and stores it in [activeRenditionUri]. When it changes, presentation state is
     * recomputed so the exposed presentations follow the selected audio track.
     *
     * Since media3 1.11 HLS track formats no longer carry a `GROUP-ID:NAME`
     * [androidx.media3.common.Format.id], so the selected track is mapped back to its rendition by
     * mirroring how media3 builds audio track groups (see
     * `HlsMediaPeriod.buildAndPrepareAudioSampleStreamWrappers`): one track group per rendition
     * `NAME` (exposed as [androidx.media3.common.Format.label]), whose `i`-th track is the `i`-th
     * audio [HlsRedundantGroup] with that name, in playlist order.
     *
     * A non-AC-4 selection resolves to a URI absent from the stream's AC-4 map, which
     * [resolveActiveAlpsInstance] then reports as "no ALPS instance", i.e. no presentations.
     *
     * An AC-4 selection resolves to the rendition URI registered in [streamAlpsMaps] rather than
     * the group's current pathway URL, so a content-steering pathway switch (including one to a
     * pathway cloned at runtime) keeps resolving to the same [Alps] instance.
     */
    private fun updateActiveRendition(player: Player) {
        val streamUri = _activeStreamUri.value ?: return
        val audioRedundantGroups =
            streamPlaylistTrackers[streamUri]
                ?.getRedundantGroups(HlsRedundantGroup.AUDIO_RENDITION)
                ?: return
        val newRenditionUri =
            player.currentTracks.groups
                .firstOrNull { group -> group.type == C.TRACK_TYPE_AUDIO && group.isSelected }
                ?.let { group ->
                    (0 until group.length)
                        .firstOrNull { index -> group.isTrackSelected(index) }
                        ?.let { index ->
                            group.getTrackFormat(index).label?.let { label ->
                                resolveRenditionGroup(
                                    audioRedundantGroups,
                                    renditionName = label,
                                    indexInTrackGroup = index,
                                )
                            }
                        }
                }
                ?.let { renditionGroup -> stableRenditionUri(streamUri, renditionGroup) }

        if (newRenditionUri != activeRenditionUri) {
            activeRenditionUri = newRenditionUri
            updatePresentationsState()
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
     * 1. For each AC-4 rendition URI in [streamAlpsMaps], and every other pathway URL of its audio
     *    [HlsRedundantGroup] (including pathways cloned by content steering at runtime), fetch the
     *    current [androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist] snapshot from any tracker.
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

        for ((streamUri, alpsMap) in streamAlpsMaps) {
            val alpsByPathwayUrl = LinkedHashMap<Uri, Alps>()
            for ((renditionUri, alps) in alpsMap) {
                pathwayUrlsOf(streamUri, renditionUri).forEach { alpsByPathwayUrl.putIfAbsent(it, alps) }
            }
            for ((renditionUri, alps) in alpsByPathwayUrl) {
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
     *
     * Every pathway URL of an AC-4 rendition's audio [HlsRedundantGroup] known at this point is
     * registered against a single shared [Alps] instance. The control path resolves the instance
     * from the rendition URL captured at track selection, while the data path resolves it from the
     * URL of the pathway currently being loaded; sharing the instance keeps both in sync across
     * pathway switches, which do not trigger a track-selection update.
     *
     * Pathways cloned by content steering after this call are not registered here; they are added
     * to the same [HlsRedundantGroup] by media3 and resolved through it at lookup time (see
     * [updateActiveRendition] and [resolveAlpsForSegmentUri]).
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
            val pathwayUrls = pathwayUrlsOf(streamUri, url)
            val alps = pathwayUrls.firstNotNullOfOrNull { alpsMap[it] }
                ?: createAlpsInstance()
                ?: continue
            pathwayUrls.forEach { alpsMap.getOrPut(it) { alps } }
            alpsMap[url] = alps
            if (presentationSelectionPersistenceEnabled) {
                userPreferredPresentationId?.let { alps.setActivePresentationId(it) }
            }
        }
        val presentationsByRenditionUriMap = playlist.tags
            .filter { it.startsWith("#EXT-X-MEDIA:TYPE=AUDIO") }
            .filter { it.contains("INSTREAM-ID") }
            .mapIndexed { index, tag ->
                tag.substringAfter("URI=\"").substringBefore("\"") to
                        parseAudioTagToPresentation(index, tag)
            }.groupBy({ it.first }, { it.second })

        playlistPresentationsMap[streamUri] = presentationsByRenditionUriMap
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
                    ),
                ),
            selectionPriority = 0,
            audioRenderingIndication = 0,
            dialogGain = extractTagAttribute(tag, "X-DIALOG-GAIN")?.toFloatOrNull(),
        )
    }

    /** Returns every registered [Alps] instance once, even when shared by several pathway URLs. */
    private fun allAlpsInstances(): Set<Alps> =
        streamAlpsMaps.values.flatMapTo(LinkedHashSet()) { it.values }

    /**
     * Returns the audio [HlsRedundantGroup] exposed as track [indexInTrackGroup] of the track group
     * for [renditionName], or `null` if it cannot be resolved (e.g. the selected audio is muxed into
     * the variants and has no rendition).
     */
    private fun resolveRenditionGroup(
        audioRedundantGroups: List<HlsRedundantGroup>,
        renditionName: String,
        indexInTrackGroup: Int,
    ): HlsRedundantGroup? =
        audioRedundantGroups
            .filter { it.groupKey.name == renditionName }
            .getOrNull(indexInTrackGroup)

    /**
     * Returns the URI of [renditionGroup] registered in the AC-4 map of [streamUri], or the group's
     * current pathway URL when none of its URLs is registered (i.e. a non-AC-4 rendition).
     */
    private fun stableRenditionUri(streamUri: Uri, renditionGroup: HlsRedundantGroup): Uri? {
        val alpsMap = streamAlpsMaps[streamUri].orEmpty()
        return runCatching {
            renditionGroup.allPlaylistUrls.firstOrNull { it in alpsMap }
                ?: renditionGroup.currentPlaylistUrl
        }.getOrNull()
    }

    /**
     * Returns every pathway URL of the audio [HlsRedundantGroup] of [streamUri] containing [url],
     * including pathways cloned by content steering after the multivariant playlist was loaded, or
     * just [url] when no such group is known.
     *
     * The groups are owned and mutated by the playback thread's tracker, while this may be called
     * from a loading thread, so a failed read falls back to [url].
     */
    private fun pathwayUrlsOf(streamUri: Uri, url: Uri): Set<Uri> =
        runCatching {
            streamPlaylistTrackers[streamUri]
                ?.getRedundantGroups(HlsRedundantGroup.AUDIO_RENDITION)
                ?.firstOrNull { url in it.allPlaylistUrls }
                ?.allPlaylistUrls
        }.getOrNull() ?: setOf(url)

    private fun createAlpsInstance(): Alps? {
        return try {
            alpsFactory().apply {
                setPresentationsChangedCallback(
                    object : PresentationsChangedCallback {
                        override fun onPresentationsChanged() {
                            updatePresentationsState()
                        }
                    },
                )
            }
        } catch (e: AlpsException) {
            AlpsLoggerProvider.e("failed to create Alps: ${e.message}")
            null
        }
    }

    /**
     * Resolves the [Alps] instance for the audio rendition currently selected on [streamUri].
     *
     * When [activeRenditionUri] is known, the instance registered for that rendition is returned;
     * a selection that is not an AC-4 rendition therefore yields `null`.
     * When it is not yet resolved (`null`), the first AC-4 instance is returned as a fallback so
     * single-rendition streams behave exactly as before track selection is known.
     */
    private fun resolveActiveAlpsInstance(streamUri: Uri): Alps? {
        val alpsMap = streamAlpsMaps[streamUri] ?: return null
        return activeRenditionUri
            ?.let { renditionUri -> alpsMap[renditionUri] }
            ?: if (activeRenditionUri == null) alpsMap.values.firstOrNull() else null
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
            resolveActiveAlpsInstance(currentStreamUri)?.let { alps ->
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
                        it.id == activePresentationId,
                    )
                }

        val playlistPresentations =
            playlistPresentationsMap.mapValues { (_, presentationsByRenditionUri) ->
                presentationsByRenditionUri.values
                    .flatten()
                    .map { presentation ->
                        AlpsPresentationWrapper.from(
                            presentation = presentation,
                            isActive = presentation.id == activePresentationId,
                        )
                    }
            }


        val playlistPresentationsForActiveRendition =
            playlistPresentationsMap.mapValues { (_, presentationsByRenditionUri) ->
                presentationsByRenditionUri
                    .filter {
                        // Find presentations for the currently active audio rendition.
                        // If no active rendition is known yet, return all presentations for the stream.
                            (uri, _) ->
                        activeRenditionUri?.toString()?.contains(uri) ?: true
                    }.values
                    .flatten()
                    .map { presentation ->
                        AlpsPresentationWrapper.from(
                            presentation = presentation,
                            isActive = presentation.id == activePresentationId,
                        )
                    }
            }

        _playlistPresentationsForActiveRendition.update {
            playlistPresentationsForActiveRendition
        }

        _playlistPresentations.update {
            playlistPresentations
        }
        _isobmffPresentations.update { isobmffPresentations }
    }
}
