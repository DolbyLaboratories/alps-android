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

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.dolby.android.alps.Alps
import com.dolby.android.alps.PresentationsChangedCallback
import com.dolby.android.alps.logger.AlpsLoggerProvider
import com.dolby.android.alps.models.Presentation
import com.dolby.android.alps.samples.models.AlpsPresentationWrapper
import com.dolby.android.alps.utils.AlpsException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 *  [AlpsManager] is a helper class that allows easier multi-period content handling.
 *
 *  [Alps] object should only process 1 period to avoid misalignment between buffered and
 *  currently playing data. [AlpsManager] holds a map of [Alps] objects assigned to specific period.
 *  It abstracts deciding which Alps object should be used to get presentation list or set active
 *  presentation. Assumption is presentation list is displayed for User watching the content so
 *  we should display presentations for currently playing content. Same for setting active
 *  presentation. After a presentation is selected, the [AlpsManager] will try to keep the same 
 *  presentation selected in following periods. If the subsequent period doesn't include
 *  presentation with the same id, TV default will be used.
 *
 *
 *  **Important!** For proper functioning, [AlpsManager] needs to know which period is currently
 *  playing. To achieve that there are 2 options:
 *  * Recommended - [AlpsManager] implements [AnalyticsListener] interface. Setting it as Player's
 *  AnalyticsListener allows it to detect proper Player Events and keep track of current playing
 *  period index.
 *  * Flexible - alternatively, user of [AlpsManager] can set current playing period index directly
 *  using [setCurrentPeriodIndex] method.
 *
 *
 *  @property presentationSelectionPersistanceEnabled If `true`, the [AlpsManager] will try to
 *  keep the same presentation selected after a period change. On by default
 *
 */
@UnstableApi
class AlpsManager(
    val presentationSelectionPersistanceEnabled: Boolean = true
): AnalyticsListener {
    companion object {
        /**
         *  Active presentation ID set to -1 means that ALPS processing will be skipped. In such
         *  case decoder will choose presentation to decode based on device/TV settings.
         *  [TV_DEFAULT_PRESENTATION] represents such case.
         */
        val TV_DEFAULT_PRESENTATION =  Presentation(
            id = -1,
            label = "TV Default",
            extendedLanguage = "unknown"
        )
    }


    private val _presentations = MutableStateFlow<List<AlpsPresentationWrapper>>(emptyList())
    /**
     * State keeping list of presentations for currently playing period
     */
    val presentations: StateFlow<List<AlpsPresentationWrapper>> = _presentations

    /**
     * State keeping the id of the last presentation selected with [setActivePresentationId]
     */
    private var userPreferredPresentationId: Int? = null

    /**
     * State keeping currently playing period index
     */
    private var currentPlayingPeriodIndex = 0

    /**
     * Map of Alps object for specific period
     */
    private val alpsPeriodMap = mutableMapOf<Int, Alps>()

    /**
     * Helper for getting Alps object assigned for currently playing period
     */
    private val currentAlps: Alps?
        get() = alpsPeriodMap.getOrDefault(currentPlayingPeriodIndex, null)

    /**
     * Provides [Alps] object assigned to given [periodIndex] or creates new [Alps] object if missing.
     */
    fun getAlps(periodIndex: Int): Alps? {
        return alpsPeriodMap.getOrElse(periodIndex) {
            try {
                Alps().apply {
                    setPresentationsChangedCallback(object : PresentationsChangedCallback {
                        override fun onPresentationsChanged() {
                            if (presentationSelectionPersistanceEnabled){
                                setActivePresentationId(userPreferredPresentationId ?: -1)
                            }
                            updatePresentationsState()
                        }
                    })
                }.also { newAlps ->
                    alpsPeriodMap[periodIndex] = newAlps
                }
            } catch (e: AlpsException) {
                AlpsLoggerProvider.e("AlpsManager failed to create Alps object. Error: ${e.message}")
                null
            }
        }
    }

    /**
     * Sets active presentation ID in Alps assigned to currently playing period and other periods if
     * they include a presentation with the same id
     *
     * @param presentationId  ID of desired active presentation, set to [TV_DEFAULT_PRESENTATION] ID
     * to skip processing and use device default
     *
     * @throws AlpsException is setting failed
     */
    fun setActivePresentationId(presentationId: Int) {
        if(presentationSelectionPersistanceEnabled){
            userPreferredPresentationId = presentationId
            alpsPeriodMap.values
                .forEach { alps ->
                    alps.setActivePresentationId(presentationId)
                }
        }
        else {
            currentAlps?.setActivePresentationId(presentationId)
        }
        updatePresentationsState()
    }
    /**
     * Sets current playing period index value to [periodIndex].
     *
     * This method should only be used if setting [AlpsManager] as Player's [AnalyticsListener] is
     * not possible.
     *
     * @param periodIndex index of period that is currently being played by player
     */
    fun setCurrentPeriodIndex(periodIndex: Int) {
        currentPlayingPeriodIndex = periodIndex
        updatePresentationsState()
        releaseUsedAlpsObjects()
    }

    /**
     * Release resources. Must be called when object is no longer needed.
     */
    fun release() {
        alpsPeriodMap.forEach {
            it.value.release()
        }
        userPreferredPresentationId = null
    }


    override fun onEvents(
        player: Player,
        events: AnalyticsListener.Events
    ) {
        if (events.contains(AnalyticsListener.EVENT_POSITION_DISCONTINUITY)) {
            setCurrentPeriodIndex(player.currentPeriodIndex)
        }
    }

    private fun updatePresentationsState() {
        _presentations.update {
            try {
                currentAlps?.getPresentations()?.let { presentations ->
                    val activePresentationId = currentAlps?.getActivePresentationId()
                        ?: TV_DEFAULT_PRESENTATION.id

                    presentations.map {
                        AlpsPresentationWrapper.from(
                            it,
                            it.id == activePresentationId
                        )
                    }
                } ?: emptyList()
            } catch (e: AlpsException) {
                AlpsLoggerProvider.e("AlpsManager failed to update presentation list state. " +
                        "Error: ${e.message}")
                emptyList()
            }
        }
    }

    private fun releaseUsedAlpsObjects() {
        val mapIterator = alpsPeriodMap.entries.iterator()
        while (mapIterator.hasNext()) {
            mapIterator.next().let {
                if (it.key < currentPlayingPeriodIndex) {
                    it.value.release()
                    mapIterator.remove()
                }
            }
        }
    }
}
