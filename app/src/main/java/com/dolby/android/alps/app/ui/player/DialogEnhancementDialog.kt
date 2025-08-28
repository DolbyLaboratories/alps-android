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

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.dolby.android.alps.app.R
import com.dolby.android.alps.app.databinding.DeDialogBinding
import com.dolby.android.alps.app.ui.base.BaseDialog

/**
 * @param maxDeLevel this value should be set to how many presentations with dialog enhancement are
 * available in AC-4 stream. It matches how many "waves" (active or not) will be shown in UI.
 */
class DialogEnhancementDialog(
    private var deLevel: Int,
    private var maxDeLevel: Int?,
): BaseDialog() {
    companion object {
        private const val REQUIRED_AMOUNT_OF_ORIGINAL_DIALOG_LEVEL_PRESENTATIONS_IN_DE_AC4_STREAM = 1
        private const val REQUIRED_AMOUNT_OF_DIALOG_OFF_PRESENTATIONS_IN_DE_AC4_STREAM = 1

        const val REQUIRED_AMOUNT_OF_BASE_PRESENTATIONS_IN_DE_AC4_STREAM =
            REQUIRED_AMOUNT_OF_ORIGINAL_DIALOG_LEVEL_PRESENTATIONS_IN_DE_AC4_STREAM +
            REQUIRED_AMOUNT_OF_DIALOG_OFF_PRESENTATIONS_IN_DE_AC4_STREAM

        /**
         * Current UI implementation is prepared for up to 4 levels of DE
         */
        private const val MAX_SUPPORTED_DE_LEVELS = 4

        val RANGE_OF_ALLOWED_PRESENTATIONS_COUNT_IN_DE_DEMO_CONTENT =
            REQUIRED_AMOUNT_OF_BASE_PRESENTATIONS_IN_DE_AC4_STREAM..
                    REQUIRED_AMOUNT_OF_BASE_PRESENTATIONS_IN_DE_AC4_STREAM+ MAX_SUPPORTED_DE_LEVELS

        const val DE_LEVEL_FOR_DIALOG_OFF = -1

        var myProvider: PropertyProvider? = null

        fun getDefaultDialogProvider(action: ((DeDialogAction) -> Unit)): PropertyProvider {
            return object : PropertyProvider {
                override val onAction: ((DeDialogAction) -> Unit)
                    get() = action
            }
        }
    }

    private var binding: DeDialogBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DeDialogBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onStart() {
        super.onStart()
        dimBehind()
    }

    override fun getTheme(): Int {
        return R.style.FullScreenDialog
    }

    /**
     * @param deLevel this value should be matching currently active presentation DE level.
     * It might be:
     * * -1 for dialog OFF presentation
     * * 0 for original dialog presentation
     * * 1..n for following dialog enhancements levels
     * @param maxDeLevel this value should be set to how many presentations with dialog enhancement
     * are available in AC-4 stream. It matches how many "waves" (active or not) will be shown in UI.
     */
    fun update(deLevel: Int, maxDeLevel: Int? = null) {
        maxDeLevel?.let {
            if (this.maxDeLevel != it) {
                this.maxDeLevel = it
                binding?.deComponent?.maxDeLevel = it
            }
        }
        this.deLevel = deLevel
        binding?.deComponent?.updateDeLevel(deLevel)
    }

    override fun setupUI() {
        binding?.apply {
            deComponent.maxDeLevel = maxDeLevel
            deComponent.updateDeLevel(deLevel)
        }

        dialog?.setOnKeyListener { _, keyCode, event ->
            return@setOnKeyListener if (event.action == KeyEvent.ACTION_UP) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_VOLUME_UP -> {
                        myProvider?.onAction?.let { it(DeDialogAction.DeUp) }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        myProvider?.onAction?.let { it(DeDialogAction.DeDown) }
                        true
                    }
                    else -> false
                }
            } else false
        }
    }

    sealed interface DeDialogAction {
        data object DeUp: DeDialogAction
        data object DeDown: DeDialogAction
    }

    interface PropertyProvider {
        val onAction: ((DeDialogAction) -> Unit)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }
}