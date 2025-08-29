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

package com.dolby.android.alps

import com.dolby.android.alps.alpsnative.AlpsNative
import com.dolby.android.alps.alpsnative.DefaultAlpsNative
import com.dolby.android.alps.alpsnative.AlpsNativeInfo
import com.dolby.android.alps.logger.AlpsLoggerProvider
import com.dolby.android.alps.models.Presentation
import com.dolby.android.alps.utils.AlpsException
import java.nio.ByteBuffer

/**
 * Main ALPS library class - API of the library.
 *
 * Use it to:
 * * get library version
 * * initialize ALPS library
 * * set presentations list changed callback
 * * process MP4 segments buffers
 * * get available presentations list
 * * get active presentation ID
 * * set active presentation
 * * close ALPS library
 *
 * @param alpsNative object implementing [AlpsNative] interface.
 * Recommended to **use default value** [DefaultAlpsNative].
 *
 * @constructor Tries to initialize alpsNative object.
 * @throws AlpsException if querying memory, allocating it or initializing native library failed.
 */
class Alps(
    private val alpsNative: AlpsNative = DefaultAlpsNative()
) {
    companion object {
        /**
         * Returns version of the ALPS library.
         * @return version of ALPS library.
         */
        fun getVersion(): String {
            return BuildConfig.ALPS_VERSION
        }

        /**
         * Returns version of the native ALPS library.
         * @return version of native ALPS library.
         */
        fun getNativeLibraryVersion(): String {
            return AlpsNativeInfo.getVersion()
        }
    }

    init {
        alpsNative.initialize()
    }

    /**
     * Release resources. Must be called when object is no longer needed.
     */
    fun release() {
        alpsNative.release()
    }

    /**
     * Sets presentations list changed callback.
     *
     * Callback is triggered whenever new presentations list is detected during ISO BMFF segment
     * processing.
     *
     * It's up to ALPS library user to react properly to presentations list change. It is
     * recommended to fetch new presentations list using [getPresentations] and set new active
     * presentation ID using [setActivePresentationId].
     *
     * Without any reaction, active presentation ID will stay unchanged, which may lead to unwanted
     * presentation playback.
     *
     * **Warning!** Callback processing is blocking processIsobmffSegment call, so it should be
     * limited to light operations!
     *
     * @param callback callback function that will be invoked whenever presentations list change
     * @throws AlpsException.NotInitialized if Alps object is not initialized
     */
    fun setPresentationsChangedCallback(callback: PresentationsChangedCallback) {
        ifInitialized {
            alpsNative.setPresentationsChangedCallback(callback)
        }
    }

    /**
     * Processes buffer of fragmented MP4 segment. This method should be called for all audio
     * segments.
     *
     * Library uses buffer to analyze stream metadata and modifies it to decode active presentation.
     *
     * Active presentation can be set using [setActivePresentationId]. Before selecting any
     * presentation, stream will not be modified.
     *
     * @param segmentBuf fragmented MP4 segment bytes. **Must be direct.**
     * @throws AlpsException.Native if processing failed
     * @throws AlpsException.NotInitialized if Alps object is not initialized
     */
    fun processIsobmffSegment(segmentBuf: ByteBuffer) {
        ifInitialized {
            alpsNative.processIsobmffSegment(segmentBuf)
        }
    }

    /**
     * Fetches presentations list.
     *
     * To detect which of these presentations is active, use [getActivePresentationId] and find
     * matching presentation.
     *
     * @throws AlpsException.Native if getting presentations failed
     * @throws AlpsException.NotInitialized if Alps object is not initialized
     * @return list of [Presentation] if at least one was detected, [emptyList] otherwise
     */
    fun getPresentations(): List<Presentation> {
        return ifInitialized {
            alpsNative.getPresentations() ?: emptyList()
        }
    }

    /**
     * Fetches active presentation ID.
     *
     * @throws AlpsException.Native if getting active presentation ID failed
     * @throws AlpsException.NotInitialized if Alps object is not initialized
     * @return ID of active presentation, -1 if unset
     */
    fun getActivePresentationId(): Int {
        return ifInitialized {
            alpsNative.getActivePresentationId()
        }
    }

    /**
     * Sets active presentation to the one with matching ID.
     *
     * **Active** presentation means it's desired to be selected by AC-4 decoder. ALPS library will
     * skip buffer processing if no active presentation is selected or -1 value is set.
     *
     * @param presentationId ID of desired active presentation, set to -1 to skip processing and use
     * device default
     * @throws AlpsException.Native if setting failed
     * @throws AlpsException.NotInitialized if Alps object is not initialized
     */
    fun setActivePresentationId(presentationId: Int) {
        return ifInitialized {
            alpsNative.setActivePresentationId(presentationId)
        }
    }

    private fun <T>ifInitialized(
        block: () -> T
    ): T {
        return if (alpsNative.isInitialized()) {
            block.invoke()
        } else {
            throw AlpsException.NotInitialized()
        }
    }
}