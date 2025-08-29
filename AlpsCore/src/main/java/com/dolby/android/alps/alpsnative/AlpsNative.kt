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

package com.dolby.android.alps.alpsnative

import com.dolby.android.alps.PresentationsChangedCallback
import com.dolby.android.alps.models.Presentation
import com.dolby.android.alps.utils.AlpsException
import java.nio.ByteBuffer

/**
 * AlpsNative interface - defines usage of Native ALPS library.
 *
 * The default implementation is [DefaultAlpsNative].
 */
interface AlpsNative {
    /**
     * Creating Native object includes querying memory size required by native library, trying
     * to allocate it and initialize native object.
     *
     * @throws AlpsException.Native if memory querying or initializing failed
     * @throws AlpsException.JNI if memory allocating failed
     */
    fun initialize()

    /**
     * Release resources. Must be called when object is no longer needed.
     */
    fun release()

    /**
     * Checks whether AlpsNative is initialized.
     *
     * @return true if initialized, false otherwise
     */
    fun isInitialized(): Boolean

    /**
     * Sets presentations list changed callback.
     *
     * Callback is triggered whenever new presentations list is detected during ISO BMFF segment
     * processing.
     *
     * @param callback callback function that will be invoked whenever presentations list change
     */
    fun setPresentationsChangedCallback(callback: PresentationsChangedCallback)

    /**
     * Processes buffer of fragmented MP4 segment.
     *
     * @param segmentBuf **direct** ByteBuffer with segment bytes
     * @throws AlpsException.Native if processing failed
     */
    fun processIsobmffSegment(segmentBuf: ByteBuffer)

    /**
     * Fetches presentations list.
     *
     * @throws AlpsException.Native if getting presentations list failed
     * @return list of [Presentation] objects returned by native library (might be empty),
     * null if native call failed
     */
    fun getPresentations(): List<Presentation>?

    /**
     * Fetches ID of active Presentation.
     *
     * @throws AlpsException.Native if getting active presentation ID failed
     * @return ID of active Presentation if native call was successful, -1 otherwise
     */
    fun getActivePresentationId(): Int

    /**
     * Sets active presentation to the one matching ID value.
     *
     * @param id ID of desired active presentation
     * @throws AlpsException.Native if setting active presentation ID failed
     */
    fun setActivePresentationId(id: Int)
}