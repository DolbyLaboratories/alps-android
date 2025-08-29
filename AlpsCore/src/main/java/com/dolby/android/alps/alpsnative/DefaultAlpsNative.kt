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

package com.dolby.android.alps.alpsnative;

import com.dolby.android.alps.PresentationsChangedCallback
import com.dolby.android.alps.models.Presentation
import com.dolby.android.alps.utils.AlpsException
import java.nio.ByteBuffer

/**
 * Default implementation of AlpsNative interface. Uses native C library wrapper.
 */
internal class DefaultAlpsNative: AlpsNative {
    companion object {
        init {
            System.loadLibrary("alpsnative")
        }

        private const val ALPS_NATIVE_NOT_INITIALIZED = 0L
        private const val NATIVE_ALPS_CREATE_FAILED = -1L
    }

    private var alpsNativeHandle: Long = ALPS_NATIVE_NOT_INITIALIZED
    private val lock = Any()

    override fun initialize() = synchronized(lock) {
        try {
            alpsNativeHandle = create()
            if (alpsNativeHandle == NATIVE_ALPS_CREATE_FAILED) {
                throw AlpsException.JNI("Native object creation failed")
            }
        } catch(e: AlpsException) {
            release()
            throw e
        }
    }

    override fun release() = synchronized(lock) {
        destroy(alpsNativeHandle)
        alpsNativeHandle = ALPS_NATIVE_NOT_INITIALIZED
    }

    override fun isInitialized() = alpsNativeHandle != ALPS_NATIVE_NOT_INITIALIZED

    override fun setPresentationsChangedCallback(callback: PresentationsChangedCallback) = synchronized(lock) {
        setPresentationsChangedCallback(alpsNativeHandle, callback)
    }

    override fun processIsobmffSegment(segmentBuf: ByteBuffer) = synchronized(lock) {
        processIsobmffSegment(alpsNativeHandle, segmentBuf)
    }

    override fun getPresentations(): List<Presentation>? = synchronized(lock) {
        getPresentations(alpsNativeHandle)
    }

    override fun getActivePresentationId(): Int = synchronized(lock) {
        getActivePresentationId(alpsNativeHandle)
    }

    override fun setActivePresentationId(id: Int) = synchronized(lock) {
        setActivePresentationId(alpsNativeHandle, id)
    }

    private external fun create(): Long
    private external fun destroy(
        alpsHandle: Long,
    )
    private external fun setPresentationsChangedCallback(
        alpsHandle: Long,
        callback: PresentationsChangedCallback,
    )
    private external fun processIsobmffSegment(
        alpsHandle: Long,
        segmentBuf: ByteBuffer,
    )
    private external fun getPresentations(
        alpsHandle: Long,
    ): List<Presentation>?
    private external fun getActivePresentationId(
        alpsHandle: Long,
    ): Int
    private external fun setActivePresentationId(
        alpsHandle: Long,
        id: Int,
    )
}
