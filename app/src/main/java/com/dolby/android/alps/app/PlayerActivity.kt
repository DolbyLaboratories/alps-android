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

package com.dolby.android.alps.app

import android.content.Intent
import android.content.pm.PackageManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.util.EventLogger
import com.dolby.android.alps.app.data.models.PresentationChangeStyle
import com.dolby.android.alps.app.databinding.ActivityPlayerBinding
import com.dolby.android.alps.app.ui.player.DialogEnhancementDialog
import com.dolby.android.alps.app.ui.player.PlayerErrorMessageProvider
import com.dolby.android.alps.app.ui.player.PresentationsListDialog
import com.dolby.android.alps.app.utils.DataSourceUtil
import com.dolby.android.alps.app.utils.IntentUtil
import com.dolby.android.alps.samples.AlpsDashChunkSourceFactory
import com.dolby.android.alps.samples.AlpsManager
import com.dolby.android.alps.samples.AlpsManager.Companion.TV_DEFAULT_PRESENTATION
import com.dolby.android.alps.samples.AlpsMediaSourceFactory
import com.dolby.android.alps.samples.AlpsManifestParser
import com.dolby.android.alps.samples.models.PeriodWithPreselections
import com.dolby.android.alps.samples.models.AlpsPresentationWrapper
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import kotlin.math.max

@UnstableApi
class PlayerActivity : AppCompatActivity() {
    companion object {
        private const val KEY_TRACK_SELECTION_PARAMETERS = "track_selection_parameters"
        private const val KEY_ITEM_INDEX: String = "item_index"
        private const val KEY_POSITION: String = "position"
        private const val KEY_AUTO_PLAY: String = "auto_play"
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null

    private lateinit var trackSelectionParameters: TrackSelectionParameters
    private var lastSeenTracks: Tracks = Tracks.EMPTY
    private var startAutoPlay = true
    private var startItemIndex = C.INDEX_UNSET
    private var startPosition = C.TIME_UNSET

    private var dataSourceFactory: DataSource.Factory? = null
    private var mediaItems = emptyList<MediaItem>()

    private var isAlpsEnabled = false
    private val alpsManager = AlpsManager()

    private var latestPresentationsList: List<AlpsPresentationWrapper> = emptyList()
    private val latestPresentationsWithTvDefault
        get() = latestPresentationsList.addTvDefaultPresentation()

    private var presentationButton: Button? = null
    private var presentationsDialog: PresentationsListDialog? = null
    private var presentationChangeStyle: PresentationChangeStyle? = null

    private var deLevelToPresIdMap: Map<Int, Int> = emptyMap()
    private var deLevel: Int = 0

    private var deButton: Button? = null
    private var deDialog: DialogEnhancementDialog? = null
    private var shouldShowDeDialog = false

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isAlpsEnabled = intent.getBooleanExtra(IntentUtil.IS_ALPS_ENABLED, true)

        dataSourceFactory = DataSourceUtil.getDataSourceFactory(this@PlayerActivity)

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.playerView.apply {
            setErrorMessageProvider(PlayerErrorMessageProvider(context))
            requestFocus()
        }

        if (savedInstanceState != null) {
            savedInstanceState.getBundle(KEY_TRACK_SELECTION_PARAMETERS)?.let {
                trackSelectionParameters = TrackSelectionParameters.fromBundle(it)
            }
            startAutoPlay = savedInstanceState.getBoolean(KEY_AUTO_PLAY)
            startItemIndex = savedInstanceState.getInt(KEY_ITEM_INDEX)
            startPosition = savedInstanceState.getLong(KEY_POSITION)
        } else {
            trackSelectionParameters = TrackSelectionParameters.Builder(this).build()
            clearStartPosition()
        }

        setupCustomControls()

        /* Observe presentations state */
        lifecycleScope.launch {
            alpsManager.presentations.collect { presentations ->
                latestPresentationsList = presentations
                if (presentationChangeStyle == null) {
                    decidePresentationSelectionStyle()
                }
                presentationChangeStyle?.let {
                    when (it) {
                        PresentationChangeStyle.DIALOG_ENHANCEMENT_ICON -> {
                            mapPresentationsToDeLevels()
                            if (!shouldShowDeDialog) {
                                deDialog?.dismiss()
                                deButton?.visibility = View.GONE
                            } else {
                                deButton?.visibility = View.VISIBLE
                            }
                            deDialog?.update(
                                deLevel = deLevel,
                                maxDeLevel = deLevelToPresIdMap.size -
                                        DialogEnhancementDialog.REQUIRED_AMOUNT_OF_BASE_PRESENTATIONS_IN_DE_AC4_STREAM
                            )
                        }

                        PresentationChangeStyle.LIST -> {
                            presentationsDialog?.updatePresentations(
                                latestPresentationsWithTvDefault
                            )
                        }

                        PresentationChangeStyle.HIDDEN -> {}
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        releasePlayer()
        clearStartPosition()
        setIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
        binding.playerView.onResume()
    }

    override fun onResume() {
        super.onResume()
        if (player == null) {
            initializePlayer()
            binding.playerView.onResume()
        }
    }

    override fun onStop() {
        super.onStop()
        binding.playerView.onPause()
        releasePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        alpsManager.release()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isEmpty()) {
            // Empty results are triggered if a permission is requested while another request was already
            // pending and can be safely ignored in this case.
            return
        }
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializePlayer()
        } else {
            showToast(R.string.storage_permission_denied)
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        updateTrackSelectorParameters()
        updateStartPosition()
        outState.putBundle(KEY_TRACK_SELECTION_PARAMETERS, trackSelectionParameters.toBundle())
        outState.putBoolean(KEY_AUTO_PLAY, startAutoPlay)
        outState.putInt(KEY_ITEM_INDEX, startItemIndex)
        outState.putLong(KEY_POSITION, startPosition)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return binding.playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    private fun initializePlayer(): Boolean {
        if (player == null) {
            mediaItems = createMediaItems()
            if (mediaItems.isEmpty()) {
                return false
            }

            lastSeenTracks = Tracks.EMPTY
            val playerBuilder =
                ExoPlayer.Builder(this)
                    .setMediaSourceFactory(createMediaSourceFactory())

            setRenderersFactory(playerBuilder)

            player = playerBuilder.build().apply {
                trackSelectionParameters = trackSelectionParameters
                addListener(PlayerEventListener())
                addAnalyticsListener(EventLogger())
                if (isAlpsEnabled) {
                    addAnalyticsListener(alpsManager)
                }
                setAudioAttributes(AudioAttributes.DEFAULT, true)
                playWhenReady = startAutoPlay
            }
            binding.playerView.setPlayer(player)
        }
        val haveStartPosition = startItemIndex != C.INDEX_UNSET
        if (haveStartPosition) {
            player!!.seekTo(startItemIndex, startPosition)
        }
        player!!.setMediaItems(mediaItems, haveStartPosition.not())
        player!!.prepare()
        return true
    }

    private fun createMediaSourceFactory(): MediaSource.Factory {
        val drmSessionManagerProvider = DefaultDrmSessionManagerProvider()
        drmSessionManagerProvider.setDrmHttpDataSourceFactory(
            DataSourceUtil.getHttpDataSourceFactory()
        )

        val defaultMediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory!!)
            .setDrmSessionManagerProvider(drmSessionManagerProvider)

        return if (isAlpsEnabled) {
            val alpsChunkSourceFactory = AlpsDashChunkSourceFactory(
                alpsManager,
                DefaultHttpDataSource.Factory(),
            )

            val dashMediaSourceFactory = DashMediaSource.Factory(
                alpsChunkSourceFactory,
                DefaultHttpDataSource.Factory(),
            )

            val parser = AlpsManifestParser()
            dashMediaSourceFactory.setManifestParser(parser)

            AlpsMediaSourceFactory(
                dashMediaSourceFactory,
                defaultMediaSourceFactory
            )
        } else {
            defaultMediaSourceFactory
        }
    }

    private fun setRenderersFactory(
        playerBuilder: ExoPlayer.Builder
    ) {
        val renderersFactory: RenderersFactory = DefaultRenderersFactory(applicationContext)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        playerBuilder.setRenderersFactory(renderersFactory)
    }

    private fun createMediaItems(): List<MediaItem> {
        intent.action.let {
            if (IntentUtil.ACTION_VIEW != it) {
                showToast(getString(R.string.unexpected_intent_action, it))
                finish()
                return emptyList()
            }
        }

        val mediaItems: List<MediaItem> = IntentUtil.createMediaItemsFromIntent(intent)
        for (i in mediaItems.indices) {
            val mediaItem = mediaItems[i]

            if (!Util.checkCleartextTrafficPermitted(mediaItem)) {
                showToast(R.string.error_cleartext_not_permitted)
                finish()
                return emptyList()
            }
            if (Util.maybeRequestReadStoragePermission(this, mediaItem)) {
                return emptyList()
            }

            val drmConfiguration = mediaItem.localConfiguration!!.drmConfiguration
            if (drmConfiguration != null) {
                if (!FrameworkMediaDrm.isCryptoSchemeSupported(drmConfiguration.scheme)) {
                    showToast(R.string.error_drm_unsupported_scheme)
                    finish()
                    return emptyList()
                }
            }
        }
        return mediaItems
    }

    private fun releasePlayer() {
        if (player != null) {
            updateTrackSelectorParameters()
            updateStartPosition()
            player!!.release()
            player = null
            binding.playerView.setPlayer(null)
            mediaItems = emptyList()
        }
    }

    private fun updateTrackSelectorParameters() {
        if (player != null) {
            trackSelectionParameters = player!!.trackSelectionParameters
        }
    }

    private fun updateStartPosition() {
        if (player != null) {
            startAutoPlay = player!!.playWhenReady
            startItemIndex = player!!.currentMediaItemIndex
            startPosition = max(0, player!!.contentPosition)
        }
    }

    private fun clearStartPosition() {
        startAutoPlay = true
        startItemIndex = C.INDEX_UNSET
        startPosition = C.TIME_UNSET
    }

    private fun showToast(messageId: Int) {
        showToast(getString(messageId))
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }

    private fun setupCustomControls() {
        if (!isAlpsEnabled) return

        if (deButton == null) {
            deButton = binding.playerView.findViewById(R.id.de_button)

            deButton?.setOnClickListener {
                showDeDialog()
            }
        }

        if (presentationButton == null) {
            presentationButton = binding.playerView.findViewById(R.id.presentation_button)

            presentationButton?.setOnClickListener {
                showPresentationsDialog()
            }
        }

    }

    private fun decidePresentationSelectionStyle() {
        val changeStyle = PresentationChangeStyle.entries.find {
            it.toString() == intent.getStringExtra(IntentUtil.PRESENTATION_CHANGE_STYLE_EXTRA)
        } ?: PresentationChangeStyle.LIST
        if (latestPresentationsList.isEmpty()) return
        mapPresentationsToDeLevels()
        if (
            changeStyle == PresentationChangeStyle.DIALOG_ENHANCEMENT_ICON
            && !shouldShowDeDialog
        ) {
            // If the first period is not compatible with DE, fallback to the list-style UI
            presentationChangeStyle = PresentationChangeStyle.LIST
        } else {
            presentationChangeStyle = changeStyle
        }
        updatePresentationSelectionButton(
            presentationChangeStyle ?: PresentationChangeStyle.LIST
        )
    }

    private fun updatePresentationSelectionButton(presentationChangeStyle: PresentationChangeStyle) {
        when (presentationChangeStyle) {
            PresentationChangeStyle.DIALOG_ENHANCEMENT_ICON -> {
                presentationButton?.visibility = View.GONE
                deButton?.visibility = View.VISIBLE

                setDeDialogListener()
            }

            PresentationChangeStyle.LIST -> {
                presentationButton?.visibility = View.VISIBLE
                deButton?.visibility = View.GONE

                setPresentationsDialogListener()
            }

            PresentationChangeStyle.HIDDEN -> {
                presentationButton?.visibility = View.GONE
                deButton?.visibility = View.GONE
            }
        }
    }

    private fun showPresentationsDialog() {
        if (presentationsDialog == null) {
            presentationsDialog = PresentationsListDialog(
                latestPresentationsWithTvDefault
            )
        }

        presentationsDialog?.show(
            supportFragmentManager,
            null
        )
    }

    private fun setPresentationsDialogListener() {
        PresentationsListDialog.myProvider =
            PresentationsListDialog.getDefaultDialogProvider { presentation ->
                trySetPresentation(presentation)
            }
    }

    private fun trySetPresentation(newPresentation: AlpsPresentationWrapper) {
        try {
            Napier.d("Trying to set presentation: $newPresentation")
            alpsManager.setActivePresentationId(newPresentation.id)
            flushBuffer()
            Napier.d("Presentation setting success")
        } catch (e: Exception) {
            Napier.w("Changing presentation failed. ${e.message}")
        }
    }

    private fun showDeDialog() {
        mapPresentationsToDeLevels()

        if (shouldShowDeDialog) {
            if (deDialog == null) {
                deDialog = DialogEnhancementDialog(
                    deLevel = deLevel,
                    maxDeLevel = deLevelToPresIdMap.size -
                            DialogEnhancementDialog.REQUIRED_AMOUNT_OF_BASE_PRESENTATIONS_IN_DE_AC4_STREAM,
                )
            }

            deDialog?.show(
                supportFragmentManager,
                null
            )

            deDialog?.update(
                deLevel = deLevel,
                maxDeLevel = deLevelToPresIdMap.size -
                        DialogEnhancementDialog.REQUIRED_AMOUNT_OF_BASE_PRESENTATIONS_IN_DE_AC4_STREAM
            )
        } else {
            showToast(
                "AC-4 stream doesn't meet DE content requirements. " +
                        "Fallback to classic presentation selection list."
            )
            presentationChangeStyle = PresentationChangeStyle.LIST
            updatePresentationSelectionButton(
                PresentationChangeStyle.LIST
            )
        }
    }

    private fun mapPresentationsToDeLevels() {
        if (latestPresentationsList.size in
            DialogEnhancementDialog.RANGE_OF_ALLOWED_PRESENTATIONS_COUNT_IN_DE_DEMO_CONTENT
        ) {
            deLevelToPresIdMap = latestPresentationsList.mapIndexed { index, pres ->
                if (index != latestPresentationsList.size - 1) {
                    index to pres.id
                } else {
                    DialogEnhancementDialog.DE_LEVEL_FOR_DIALOG_OFF to pres.id
                }
            }.toMap()
            val activePresentationId = latestPresentationsList.find { it.isActive }?.id ?: 0

            deLevel = 0
            for (level in deLevelToPresIdMap) {
                if (level.value == activePresentationId) {
                    deLevel = level.key
                    break
                }
            }

            shouldShowDeDialog = true
        } else {
            shouldShowDeDialog = false
            Napier.w(
                "${latestPresentationsList.size} presentations available. " +
                        "Not in supported range for DIALOG_ENHANCEMENT_ICON demoing."
            )
        }
    }

    private fun setDeDialogListener() {
        DialogEnhancementDialog.myProvider =
            DialogEnhancementDialog.getDefaultDialogProvider { action ->
                when (action) {
                    DialogEnhancementDialog.DeDialogAction.DeDown -> {
                        tryChangeDeLevel(-1)
                        deDialog?.update(deLevel)
                    }

                    DialogEnhancementDialog.DeDialogAction.DeUp -> {
                        tryChangeDeLevel(1)
                        deDialog?.update(deLevel)
                    }
                }
            }
    }

    private fun tryChangeDeLevel(change: Int) {
        deLevel += change
        try {
            deLevelToPresIdMap.getOrElse(deLevel) {
                throw Exception("Trying to set DE level ($deLevel) that is not on the list.")
            }.let { presId ->
                Napier.d("Trying to set presentation: ${latestPresentationsList.find { it.id == presId }}")
                alpsManager.setActivePresentationId(presId)
                flushBuffer()
                Napier.d("DE level (presentation) setting success")
            }
        } catch (e: Exception) {
            Napier.w("Changing DE level failed. ${e.message}")
            deLevel -= change
        }
    }

    private fun flushBuffer() {
        if (player != null) {
            val currentPosition = player!!.currentPosition
            player!!.stop()

            player!!.seekTo(currentPosition)
            player!!.prepare()

            player!!.playWhenReady = true
        }
    }

    private inner class PlayerEventListener : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                player?.seekToDefaultPosition()
                player?.prepare()
            }
        }


        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                player?.let {
                    when (val manifest = it.currentManifest) {
                        is DashManifest -> {
                            for (i in 0..<manifest.periodCount) {
                                when (val period = manifest.getPeriod(i)) {
                                    is PeriodWithPreselections -> {
                                        Napier.d("Preselections in period $i")
                                        Napier.d(period.preselections.toString())
                                    }
                                    else -> {
                                        Napier.d("Period $i missing information about preselections")
                                    }
                                }
                            }
                        }
                        else -> {
                            Napier.d("Not a DASH manifest. Can't extract preselections")
                        }
                    }
                } ?: Napier.d("Failed. Player is null")

            super.onTimelineChanged(timeline, reason)
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (tracks === lastSeenTracks) return

            if (tracks.containsType(C.TRACK_TYPE_VIDEO)
                && !tracks.isTypeSupported(C.TRACK_TYPE_VIDEO, true)
            ) {
                showToast(R.string.error_unsupported_video)
            }
            if (tracks.containsType(C.TRACK_TYPE_AUDIO)
                && !tracks.isTypeSupported(C.TRACK_TYPE_AUDIO, true)
            ) {
                showToast(R.string.error_unsupported_audio)
            }
            lastSeenTracks = tracks
        }

    }

    private fun List<AlpsPresentationWrapper>.addTvDefaultPresentation(): List<AlpsPresentationWrapper> {
        val isTvDefaultPresentationActive = this.none { it.isActive }
        return listOf(
            AlpsPresentationWrapper.from(
                presentation = TV_DEFAULT_PRESENTATION,
                isActive = isTvDefaultPresentationActive
            )
        ) + this
    }
}
