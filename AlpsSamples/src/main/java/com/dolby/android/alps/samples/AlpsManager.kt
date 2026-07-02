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

package com.dolby.android.alps.samples

import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.dolby.android.alps.models.Label
import com.dolby.android.alps.models.Presentation
import com.dolby.android.alps.samples.models.AlpsPresentationWrapper
import com.dolby.android.alps.utils.AlpsException
import kotlinx.coroutines.flow.StateFlow

interface AlpsManager: AnalyticsListener {
    companion object {
        /**
         * Sentinel presentation meaning "let the device / TV choose" (ALPS processing disabled).
         */
        val TV_DEFAULT_PRESENTATION = Presentation(
            id = -1,
            labels = listOf(
                Label(
                    label = "TV Default",
                    language = "unknown",
                    labelId = 1,
                    isGroupLabel = false
                )
            ),
            kinds = emptyList(),
            audioRenderingIndication = 0,
            dialogGain = 0f,
            extendedLanguage = "unknown",
            selectionPriority = 0,
        )
    }

    /**
     * State keeping list of presentations signalled in ISOBMFF for currently playing stream
     */
    val isobmffPresentations: StateFlow<List<AlpsPresentationWrapper>>

    /**
     * Sets active presentation ID in Alps assigned to currently used Alps instance and other
     * instances if they include a presentation with the same id
     *
     * @param presentationId  ID of desired active presentation, set to [TV_DEFAULT_PRESENTATION] ID
     * to skip processing and use device default
     *
     * @throws AlpsException if setting failed
     */
    fun setActivePresentationId(presentationId: Int)

    /**
     * Returns ID of currently active presentation
     */
    fun getActivePresentationId(): Int?

    /**
     * Release resources. Must be called when object is no longer needed.  After this method is
     * called, this manager instance is considered invalidated and should not be used.
     */
    fun release()
}
