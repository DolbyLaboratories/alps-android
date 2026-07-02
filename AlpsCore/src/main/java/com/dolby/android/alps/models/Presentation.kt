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

package com.dolby.android.alps.models

/**
 * Data class representing a Kind box
 * @param schemeUri the identifier of the naming scheme for the following value
 * @param value a name from the declared scheme
 */
data class Kind(
    val schemeUri: String,
    val value: String,
)

/**
 * Data class representing Label box
 * @param labelId an integer that contains an identifier for the label. Labels with the same value belong to a label group
 * @param language a IETF BCP 47 compliant language tag string
 * @param label the textual description
 * @param isGroupLabel The value `true` specifies that the label contains a summary label for a group of labels
 */
data class Label(
    val labelId: Int,
    val language: String,
    val label: String,
    val isGroupLabel: Boolean,
)

/**
 * Data class representing AC-4 presentation.
 * @param id ID of the presentation
 * @param extendedLanguage extendedLanguage language tag of the presentation
 * @param kinds list of [Kind] boxes associated with this [Presentation]
 * @param labels list of [Label] boxes associated with this [Presentation]
 * @param selectionPriority the priority of the [Presentation]
 * @param audioRenderingIndication a hint for a preferred reproduction channel layout
 * @param dialogGain gain applied to the dialog component compared to the default mix, in dB
 */
data class Presentation(
    val id: Int,
    val extendedLanguage: String?,
    val kinds: List<Kind>,
    val labels: List<Label>,
    val selectionPriority: Int,
    val audioRenderingIndication: Int,
    val dialogGain: Float?,
)

fun List<Presentation>.hasChanged(other: List<Presentation>): Boolean {
    return  size != other.size ||
            withIndex().any { (index, pres) -> pres != other[index] }
}
