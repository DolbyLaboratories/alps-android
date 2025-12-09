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

package com.dolby.android.alps.samples.models

import com.dolby.android.alps.models.Kind
import com.dolby.android.alps.models.Label
import com.dolby.android.alps.models.Presentation
import kotlin.Int

/**
 * Wrapper of [Presentation] class from AlpsCore library. Adds [isActive] information to
 * presentation object.
 *
 * @param id ID of the presentation
 * @param label label of the presentation
 * @param extendedLanguage language tag of the presentation
 */
data class AlpsPresentationWrapper(
    val id: Int,
    val extendedLanguage: String?,
    val kinds: List<Kind>,
    val labels: List<Label>,
    val selectionPriority: Int,
    val audioRenderingIndication: Int,
    val dialogGain: Float?,
    val isActive: Boolean,
) {
    companion object {
        fun from(
            presentation: Presentation,
            isActive: Boolean = false,
        ) = AlpsPresentationWrapper(
            id = presentation.id,
            extendedLanguage = presentation.extendedLanguage,
            kinds = presentation.kinds,
            labels = presentation.labels,
            selectionPriority = presentation.selectionPriority,
            audioRenderingIndication = presentation.audioRenderingIndication,
            dialogGain = presentation.dialogGain,
            isActive = isActive,
        )
    }
}
