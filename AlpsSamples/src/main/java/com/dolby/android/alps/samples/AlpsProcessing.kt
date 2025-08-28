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

package com.dolby.android.alps.samples

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.dolby.android.alps.Alps
import com.dolby.android.alps.logger.AlpsLoggerProvider
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import kotlin.math.min

/**
 * [AlpsProcessing] object opens http data source, downloads the whole segment and processes it
 * using ALPS library. After that it returns requested data portions of already processed segment.
 *
 * Usage of this class is similar to [HttpDataSource] implementations usage. For each segment [open]
 * method should be called first and then [read] method can be called until end of input will be
 * returned.
 *
 * @param alps ALPS core library object used for segments processing
 * @param defaultHttpDataSource [HttpDataSource] implementation, used for segments downloading
 */
@UnstableApi
internal class AlpsProcessing(
    private val alps: Alps,
    private val defaultHttpDataSource: HttpDataSource,
) {
    private var segmentSize = 0L
    private var loadedBytes = 0L
    private var isSegmentLoaded = false
    private var segmentBuffer: ByteArray? = null

    private var inputStream: ByteArrayInputStream? = null
    private var inputStreamRead = 0L

    /**
     * Opens the source to read the specified data. Should called first for each segment.
     *
     * @param dataSpec Defines the data to be read
     */
    fun open(dataSpec: DataSpec): Long {
        return defaultHttpDataSource.open(dataSpec).also {
            segmentSize = it
            prepareSegmentBuffer()
        }
    }

    /**
     * Works exactly as DataReader read method, but during first call for a segment, it downloads
     * the whole segment and processes it using ALPS library.
     *
     * @param buffer A target array into which data should be written
     * @param offset The offset into the target array at which to write
     * @param length The maximum number of bytes to read from the input
     * @return The number of bytes read, or [C.RESULT_END_OF_INPUT] if the input has ended
     */
    fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (isSegmentLoaded.not()) {
            readSegment()
            processSegment()
        }
        return readInternal(buffer, offset, length)
    }

    private fun prepareSegmentBuffer() {
        if (segmentSize in 0 .. Int.MAX_VALUE) {
            segmentBuffer = ByteArray(segmentSize.toInt())
            isSegmentLoaded = false
            loadedBytes = 0
            inputStreamRead = 0
        } else {
            //TODO handle segment with size larger then > Int.MAX_VALUE
            AlpsLoggerProvider.e("Segment with size > Int.MAX_VALUE detected.")
        }
    }

    private fun readSegment() {
        segmentBuffer?.let { segmentBuffer ->
            AlpsLoggerProvider.i("Loading segment of size: $segmentSize")
            while (loadedBytes < segmentSize) {
                loadedBytes += defaultHttpDataSource.read(
                    segmentBuffer,
                    loadedBytes.toInt(),
                    (segmentSize - loadedBytes).toInt()
                )
            }

            AlpsLoggerProvider.i("Segment loaded")
            isSegmentLoaded = true
        }
    }

    private fun processSegment() {
        segmentBuffer?.let { segmentBuffer ->
            try {
                val directByteBuffer = ByteBuffer.allocateDirect(segmentBuffer.size)
                directByteBuffer.put(segmentBuffer)
                alps.processIsobmffSegment(directByteBuffer)
                directByteBuffer.position(0)
                directByteBuffer.get(segmentBuffer)
                inputStream = ByteArrayInputStream(segmentBuffer)
                AlpsLoggerProvider.i("Segment processed successfully by ALPS")
            } catch (e: Exception) {
                AlpsLoggerProvider.e(e.message ?: "Exception without message")
                AlpsLoggerProvider.w(
                    "Exception thrown during ALPS segment processing. Unmodified segment will be provided."
                )
                inputStream = ByteArrayInputStream(segmentBuffer)
            }
        }
    }

    private fun readInternal(buffer: ByteArray, offset: Int, requestedReadLength: Int): Int {
        var readLength = requestedReadLength
        if (readLength == 0) {
            return 0
        }

        if (segmentSize != C.LENGTH_UNSET.toLong()) {
            val bytesRemaining = segmentSize - inputStreamRead
            if (bytesRemaining == 0L) {
                return C.RESULT_END_OF_INPUT
            }
            readLength = min(readLength.toDouble(), bytesRemaining.toDouble()).toInt()
        }
        val read = inputStream!!.read(buffer, offset, readLength)
        if (read == -1) {
            return C.RESULT_END_OF_INPUT
        }

        inputStreamRead += read.toLong()

        return read
    }
}