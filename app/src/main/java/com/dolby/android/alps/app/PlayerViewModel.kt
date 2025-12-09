/***************************************************************************************************
 *                Copyright (C) 2024-2025 by Dolby International AB.
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

import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.dolby.android.alps.app.data.models.DeState
import com.dolby.android.alps.app.data.models.PresentationChangeStyle
import com.dolby.android.alps.app.ui.base.BaseViewModel
import com.dolby.android.alps.app.ui.player.DialogEnhancementDialog
import com.dolby.android.alps.app.utils.isDeCompatible
import com.dolby.android.alps.models.Presentation
import com.dolby.android.alps.samples.AlpsManager
import com.dolby.android.alps.samples.models.AlpsPresentationWrapper
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.collections.sortedBy


@UnstableApi
class PlayerViewModel(
    private val alpsManager: AlpsManager,
    private val afterPresentationChanges: () -> Unit,
) : BaseViewModel() {

    private var preferredPresentationChangeStyle: PresentationChangeStyle =
        PresentationChangeStyle.LIST;
    private var currentPeriodIndex: Int = 0;
    private var isobmffPresentations: HashMap<Int, List<AlpsPresentationWrapper>> = HashMap()
    private var dashPresentations: HashMap<Int, List<Presentation>> = HashMap()

    private val _presentations = MutableStateFlow<List<AlpsPresentationWrapper>>(emptyList());
    val presentations: StateFlow<List<AlpsPresentationWrapper>>
        get() = _presentations

    private val _presentationChangeStyle =
        MutableStateFlow<PresentationChangeStyle>(PresentationChangeStyle.LIST);
    val presentationChangeStyle: StateFlow<PresentationChangeStyle>
        get() = _presentationChangeStyle

    private val _deState = MutableStateFlow<DeState?>(null)
    val deState: StateFlow<DeState?> get() = _deState

    init {
        viewModelScope.launch {
            alpsManager.presentations.collect { presentations ->
                setIsobmffPresentations(currentPeriodIndex, presentations)
            }
        }
    }

    fun setPreferredPresentationChangeStyle(style: PresentationChangeStyle) {
        preferredPresentationChangeStyle = style
    }

    fun updatePresentationsState() {
        val activePresentationId =
            alpsManager.getAlps(currentPeriodIndex)?.getActivePresentationId()

        val newPresentationList: List<AlpsPresentationWrapper> =
            dashPresentations[currentPeriodIndex]?.takeIf { it.isNotEmpty() }?.map { pres ->
                AlpsPresentationWrapper.from(pres, pres.id == activePresentationId)
            } ?: isobmffPresentations[currentPeriodIndex] ?: emptyList()


        _deState.update {
            if (newPresentationList.isDeCompatible()) {
                newPresentationList.sortedBy { it.dialogGain }.let { sorted ->
                    DeState(
                        level = sorted.indexOfFirst { pres -> pres.id == activePresentationId },
                        presentationLevels = sorted,
                    )
                }
            } else {
                null
            }
        }

        val newPresentationChangeStyle =
            when (preferredPresentationChangeStyle) {
                PresentationChangeStyle.DIALOG_ENHANCEMENT_ICON -> if (_deState.value != null) {
                    PresentationChangeStyle.DIALOG_ENHANCEMENT_ICON
                } else {
                    Napier.w { "Content does not meet DE requirements. Falling back to LIST selection style" }
                    PresentationChangeStyle.LIST
                }

                PresentationChangeStyle.LIST -> PresentationChangeStyle.LIST
                PresentationChangeStyle.HIDDEN -> PresentationChangeStyle.HIDDEN
            }

        _presentations.update {
            newPresentationList
        }
        _presentationChangeStyle.update {
            newPresentationChangeStyle
        }
    }

    private fun setIsobmffPresentations(
        periodIndex: Int,
        presentations: List<AlpsPresentationWrapper>
    ) {
        isobmffPresentations[periodIndex] = presentations
        updatePresentationsState()
    }

    fun setDashPresentations(periodIndex: Int, presentations: List<Presentation>) {
        dashPresentations[periodIndex] = presentations
        updatePresentationsState()
    }

    fun setCurrentPeriodIndex(periodIndex: Int) {
        Napier.d { "Setting period index to $periodIndex" }
        currentPeriodIndex = periodIndex
        alpsManager.setCurrentPeriodIndex(periodIndex)
        updatePresentationsState()
    }

    private fun mapPresentationsToDeLevels(presentations: List<AlpsPresentationWrapper>): List<AlpsPresentationWrapper>? =
        if (presentations.size !in DialogEnhancementDialog.RANGE_OF_ALLOWED_PRESENTATIONS_COUNT_IN_DE_DEMO_CONTENT) {
            null
        } else if (presentations.any { it.dialogGain == null }) {
            null
        } else {
            presentations.sortedBy { it.dialogGain }
        }

    fun setActivePresentationId(id: Int) {
        alpsManager.setActivePresentationId(id)
        updatePresentationsState()
        afterPresentationChanges()
    }

    fun changeDeLevel(changeBy: Int) {
        _deState.value?.let { state ->
            val newLevel = state.level + changeBy
            if (newLevel !in 0..state.presentationLevels.size) {
                return
            }
            if (newLevel >= state.presentationLevels.size) {
                return
            }
            val newPresentation = state.presentationLevels[newLevel]
            setActivePresentationId(newPresentation.id)
            _deState.update {
                it?.copy(level = newLevel)
            }
        }
    }
}
