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
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.util.EventLogger
import com.dolby.android.alps.Alps
import com.dolby.android.alps.PresentationsChangedCallback
import com.dolby.android.alps.app.data.models.PresentationChangeStyle
import com.dolby.android.alps.app.databinding.ActivityPlayerBinding
import com.dolby.android.alps.app.ui.player.DialogEnhancementDialog
import com.dolby.android.alps.app.ui.player.PlayerErrorMessageProvider
import com.dolby.android.alps.app.ui.player.PresentationsListDialog
import com.dolby.android.alps.app.utils.DataSourceUtil
import com.dolby.android.alps.app.utils.IntentUtil
import com.dolby.android.alps.models.Presentation
import com.dolby.android.alps.models.hasChanged
import com.dolby.android.alps.samples.AlpsHttpDataSource
import com.dolby.android.alps.samples.utils.DashManifestAc4DataSourceDetector
import io.github.aakira.napier.Napier
import kotlin.math.max

@UnstableApi
class PlayerActivity : PresentationsChangedCallback, AppCompatActivity() {
    companion object {
        private const val KEY_TRACK_SELECTION_PARAMETERS = "track_selection_parameters"
        private const val KEY_ITEM_INDEX: String = "item_index"
        private const val KEY_POSITION: String = "position"
        private const val KEY_AUTO_PLAY: String = "auto_play"

        /**
         *  Active presentation ID set to -1 means that ALPS processing will be skipped. In such
         *  case decoder will choose presentation to decode based on device/TV settings.
         *  [TV_DEFAULT_PRESENTATION] represents such case.
         */
        private val TV_DEFAULT_PRESENTATION =  Presentation(
            id = -1,
            label = "TV Default",
            extendedLanguage = "unknown"
        )
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

    private var alps: Alps? = null
    @UnstableApi
    private var alpsDataSourceFactory: AlpsHttpDataSource.Factory? = null
    @UnstableApi
    private val ac4DataSourceDetector = DashManifestAc4DataSourceDetector(null)

    private var latestPresentationsList: List<Presentation> = emptyList()

    private val presentationsWithTvDefault: List<Presentation>
        get() = listOf(TV_DEFAULT_PRESENTATION) + latestPresentationsList
    private var selectedPresentation = TV_DEFAULT_PRESENTATION

    private var presentationButton: Button? = null
    private var presentationsDialog: PresentationsListDialog? = null

    private var deLevelToPresIdMap: Map<Int, Int> = emptyMap()
    private var deLevel: Int = 0

    private var deButton: Button? = null
    private var deDialog: DialogEnhancementDialog? = null
    private var shouldShowDeDialog = false

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            alps = Alps().apply {
                setPresentationsChangedCallback(this@PlayerActivity)
            }
            alpsDataSourceFactory = DataSourceUtil.getAlpsHttpDataSourceFactory(alps!!, ac4DataSourceDetector)
            dataSourceFactory = DataSourceUtil.getAlpsDataSourceFactory(this, alpsDataSourceFactory!!)
        } catch (e: Exception) {
            alps = null
            Napier.e("Alps creation failed. Exception message: ${e.message}")
            showToast(R.string.error_alps_init)
            finish()
        }

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.playerView. apply {
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
        alps?.close()
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
                setAudioAttributes(AudioAttributes.DEFAULT,true)
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

        return DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory!!)
            .setDrmSessionManagerProvider(drmSessionManagerProvider)
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
        if (alps == null) return

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

        val presentationChangeStyle = PresentationChangeStyle.entries.find {
                it.toString() == intent.getStringExtra(IntentUtil.PRESENTATION_CHANGE_STYLE_EXTRA)
            } ?: PresentationChangeStyle.LIST

        updatePresentationSelectionButton(presentationChangeStyle)
    }

    private fun updatePresentationSelectionButton(presentationChangeStyle: PresentationChangeStyle) {
        when(presentationChangeStyle) {
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
        updatePresentationsState(onNewPresentationsCallback = null)
        /* If active presentation is not on the presentations list we assume that TV default
        presentation is active */
        selectedPresentation = presentationsWithTvDefault.find {
            it.id == alps!!.getActivePresentationId()
        } ?: TV_DEFAULT_PRESENTATION

        if (presentationsDialog == null) {
            presentationsDialog = PresentationsListDialog(presentationsWithTvDefault, selectedPresentation)
        }

        presentationsDialog?.show(
            supportFragmentManager,
            null
        )
    }

    private fun setPresentationsDialogListener() {
        PresentationsListDialog.myProvider = PresentationsListDialog.getDefaultDialogProvider { presentation ->
            trySetPresentation(presentation)
            presentationsDialog?.updateSelectedPresentation(selectedPresentation)
        }
    }

    private fun trySetPresentation(newPresentation: Presentation) {
        try {
            Napier.d("Trying to set presentation: $newPresentation")
            alps!!.setActivePresentationId(newPresentation.id)
            flushBuffer()
            selectedPresentation = newPresentation
            Napier.d("Presentation setting success")
        } catch (e: Exception) {
            Napier.w("Changing presentation failed. ${e.message}")
        }
    }

    private fun showDeDialog() {
        updatePresentationsState {
            mapPresentationsToDeLevels()
        }

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
            showToast("AC-4 stream doesn't meet DE content requirements. " +
                    "Fallback to classic presentation selection list.")
            updatePresentationSelectionButton(
                PresentationChangeStyle.LIST
            )
        }
    }

    private fun updatePresentationsState(
        onNewPresentationsCallback: (() -> Unit)?
    ) {
        val newPresentationsList = alps!!.getPresentations()
        if (latestPresentationsList.hasChanged(newPresentationsList)) {
            Napier.i("New presentations list detected: $newPresentationsList")
            latestPresentationsList = newPresentationsList

            onNewPresentationsCallback?.invoke()
        }
    }

    private fun mapPresentationsToDeLevels() {
        if (latestPresentationsList.size in
            DialogEnhancementDialog.RANGE_OF_ALLOWED_PRESENTATIONS_COUNT_IN_DE_DEMO_CONTENT) {
            deLevelToPresIdMap = latestPresentationsList.mapIndexed { index, pres ->
                if (index != latestPresentationsList.size - 1) {
                    index to pres.id
                } else {
                    DialogEnhancementDialog.DE_LEVEL_FOR_DIALOG_OFF to pres.id
                }
            }.toMap()
            val activePresentationId = alps?.getActivePresentationId()

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
            Napier.w("${latestPresentationsList.size} presentations available. " +
                    "Not in supported range for DIALOG_ENHANCEMENT_ICON demoing.")
        }
    }

    private fun setDeDialogListener() {
        DialogEnhancementDialog.myProvider = DialogEnhancementDialog.getDefaultDialogProvider { action ->
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
                alps!!.setActivePresentationId(presId)
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

        override fun onTracksChanged(tracks: Tracks) {
            if (tracks === lastSeenTracks) return

            if (tracks.containsType(C.TRACK_TYPE_VIDEO)
                && !tracks.isTypeSupported(C.TRACK_TYPE_VIDEO,true)
            ) {
                showToast(R.string.error_unsupported_video)
            }
            if (tracks.containsType(C.TRACK_TYPE_AUDIO)
                && !tracks.isTypeSupported(C.TRACK_TYPE_AUDIO,true)
            ) {
                showToast(R.string.error_unsupported_audio)
            }
            lastSeenTracks = tracks
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            super.onTimelineChanged(timeline, reason)
            if (player?.currentManifest is DashManifest) {
                val manifest = player?.currentManifest as? DashManifest
                manifest?.let {
                    ac4DataSourceDetector.manifest = it
                }
            }
        }
    }

    override fun onPresentationsChanged() {
        Napier.i("Received presentations list changed callback")
        /* Example reaction to new presentations list */
        alps?.let {
            val presentations = it.getPresentations()
            /* Setting active presentation to the first on the list or TV_DEFAULT if list is empty */
            it.setActivePresentationId(presentations.firstOrNull()?.id ?: TV_DEFAULT_PRESENTATION.id)
        }
    }
}