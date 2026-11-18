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

package com.dolby.android.alps.samples.fakes

import com.dolby.android.alps.PresentationsChangedCallback
import com.dolby.android.alps.alpsnative.AlpsNative
import com.dolby.android.alps.models.Presentation
import java.nio.ByteBuffer

/**
 * In-memory stand-in for the JNI backed [com.dolby.android.alps.alpsnative.DefaultAlpsNative].
 *
 * It allows unit tests to exercise the real [com.dolby.android.alps.Alps] object - and therefore
 * the real [com.dolby.android.alps.samples.AlpsManager] implementations - on the JVM, without
 * loading the native ALPS library.
 */
class FakeAlpsNative(
    presentations: List<Presentation> = emptyList(),
) : AlpsNative {

    var availablePresentations: List<Presentation> = presentations
        private set

    var activeId: Int = NO_ACTIVE_PRESENTATION_ID
        private set

    var isReleased: Boolean = false
        private set

    private var initialized = false
    private var callback: PresentationsChangedCallback? = null

    override fun initialize() {
        initialized = true
        isReleased = false
    }

    override fun release() {
        initialized = false
        isReleased = true
    }

    override fun isInitialized(): Boolean = initialized

    override fun setPresentationsChangedCallback(callback: PresentationsChangedCallback) {
        this.callback = callback
    }

    override fun processIsobmffSegment(segmentBuf: ByteBuffer) = Unit

    override fun getPresentations(): List<Presentation> = availablePresentations

    override fun getActivePresentationId(): Int = activeId

    override fun setActivePresentationId(id: Int) {
        activeId = id
    }

    /**
     * Simulates the native library detecting a new presentations list while processing an ISOBMFF
     * segment, which is the only situation in which the presentations changed callback fires.
     */
    fun signalPresentationsChanged(newPresentations: List<Presentation> = availablePresentations) {
        availablePresentations = newPresentations
        callback?.onPresentationsChanged()
    }

    companion object {
        // Intentionally not using the value from [AlpsManager] here, native code uses -1, so I
        // repeat it here in case we accidentally change it in AlpsManager
        const val NO_ACTIVE_PRESENTATION_ID = -1
    }
}
