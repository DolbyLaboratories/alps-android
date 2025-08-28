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

package com.dolby.android.alps.app.ui.player

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import com.dolby.android.alps.app.R
import com.dolby.android.alps.app.databinding.DeLevelComponentBinding

class DialogEnhancementComponent @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet,
    defStyleAttr: Int = 0,
): ConstraintLayout(ctx, attrs, defStyleAttr) {
    companion object {
        private val SUPPORTED_MAX_DE_LEVEL_RANGE = 0..4
    }

    private var _binding: DeLevelComponentBinding? = null
    private val binding: DeLevelComponentBinding
        get() = _binding!!

    var maxDeLevel: Int? = null
        set(value) {
            if (value in SUPPORTED_MAX_DE_LEVEL_RANGE) {
                field = value
                adjustToMaxDeLevel()
            } else {
                throw IllegalArgumentException(
                    "${this.javaClass.name} can't handle maxDeLevel value of $value. " +
                            "Allowed values range: $SUPPORTED_MAX_DE_LEVEL_RANGE"
                )
            }
        }

    init {
        _binding = DeLevelComponentBinding.inflate(LayoutInflater.from(ctx), this, true)
    }

    fun updateDeLevel(newDeLevel: Int) {
        binding.apply {
            if (newDeLevel < 0) {
                deLevels.visibility = View.INVISIBLE
                deX.visibility = View.VISIBLE
            } else {
                deLevels.visibility = View.VISIBLE
                deX.visibility = View.INVISIBLE

                deLevel1Image.setImageResource(
                    DeLevelGroup.DIALOG_LOW_ENHANCEMENT.getGroupIconForCurrentEnhancementLevel(
                        newDeLevel
                    )
                )
                deLevel2Image.setImageResource(
                    DeLevelGroup.DIALOG_MEDIUM_ENHANCEMENT.getGroupIconForCurrentEnhancementLevel(
                        newDeLevel
                    )
                )
                deLevel3Image.setImageResource(
                    DeLevelGroup.DIALOG_HIGH_ENHANCEMENT.getGroupIconForCurrentEnhancementLevel(
                        newDeLevel
                    )
                )
                deLevel4Image.setImageResource(
                    DeLevelGroup.DIALOG_MAX_ENHANCEMENT.getGroupIconForCurrentEnhancementLevel(
                        newDeLevel
                    )
                )
            }
        }
    }

    private fun adjustToMaxDeLevel() {
        maxDeLevel?.let {
            binding.apply {
                deLevel1Image.visibility =
                    DeLevelGroup.DIALOG_LOW_ENHANCEMENT.getGroupVisibilityForMaxEnhancementLevel(it)
                deLevel2Image.visibility =
                    DeLevelGroup.DIALOG_MEDIUM_ENHANCEMENT.getGroupVisibilityForMaxEnhancementLevel(it)
                deLevel3Image.visibility =
                    DeLevelGroup.DIALOG_HIGH_ENHANCEMENT.getGroupVisibilityForMaxEnhancementLevel(it)
                deLevel4Image.visibility =
                    DeLevelGroup.DIALOG_MAX_ENHANCEMENT.getGroupVisibilityForMaxEnhancementLevel(it)
            }
        } ?: throw Exception("adjustToMaxDeLevel() called without maxDeLevel value set")
    }

    private enum class DeLevelGroup(
        val enhancement: Int,
        @DrawableRes val iconOff: Int,
        @DrawableRes val  iconOn: Int,
    ) {
        DIALOG_LOW_ENHANCEMENT(1, R.drawable.ic_de_level_1_off, R.drawable.ic_de_level_1_on),
        DIALOG_MEDIUM_ENHANCEMENT(2, R.drawable.ic_de_level_2_off, R.drawable.ic_de_level_2_on),
        DIALOG_HIGH_ENHANCEMENT(3, R.drawable.ic_de_level_3_off, R.drawable.ic_de_level_3_on),
        DIALOG_MAX_ENHANCEMENT(4, R.drawable.ic_de_level_4_off, R.drawable.ic_de_level_4_on);

        fun getGroupIconForCurrentEnhancementLevel(currentEnhancement: Int) =
            if(currentEnhancement >= enhancement) iconOn else iconOff

        fun getGroupVisibilityForMaxEnhancementLevel(maxEnhancementLevel: Int) =
            if (maxEnhancementLevel >= enhancement) View.VISIBLE else View.INVISIBLE
    }
}