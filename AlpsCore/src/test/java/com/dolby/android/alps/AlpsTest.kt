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

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import com.dolby.android.alps.alpsnative.AlpsNative
import com.dolby.android.alps.models.Presentation
import com.dolby.android.alps.utils.AlpsException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.ByteBuffer

class AlpsTest {
    private lateinit var alps: Alps

    @Nested
    inner class Base {
        @Test
        fun `returned version matches SemVer schema`() {
            val version = Alps.getVersion()

            assertThat(version).isNotEmpty()
            assertThat(version.split('.').size).isEqualTo(3)
        }

        @Test
        fun `Exception thrown if native create failed during alps_query_mem`() {
            val mockedAlpsNative = getMockedAlpsNative(
                nativeInitializeException = AlpsException.Native.Undefined()
            )

            val exception = assertThrows<AlpsException>{
                alps = Alps(mockedAlpsNative)
            }
            assertThat(exception.message).isEqualTo(AlpsException.Native.Undefined().message)
        }

        @Test
        fun `Exception thrown if native create failed during alps_init`() {
            val nativeException = AlpsException.Native.BuffTooSmall()
            val mockedAlpsNative = getMockedAlpsNative(
                nativeInitializeException = nativeException
            )

            val exception = assertThrows<AlpsException>{
                alps = Alps(mockedAlpsNative)
            }
            assertThat(exception).isEqualTo(nativeException)
        }

        @Test
        fun `release calls native release`() {
            val mockedAlpsNative = getMockedAlpsNative()
            alps = Alps(mockedAlpsNative)

            alps.release()

            verify(exactly = 1) {
                mockedAlpsNative.release()
            }
        }
    }

    @Nested
    inner class BufferProcessing {
        @Test
        fun `process throws exception if native processing failed`() {
            val nativeException = AlpsException.Native.ParseFailed()
            val mockedAlpsNative = getMockedAlpsNative(
                nativeProcessBufferException = nativeException
            )
            alps = Alps(mockedAlpsNative)

            val exception = assertThrows<AlpsException> {
                alps.processIsobmffSegment(ByteBuffer.allocateDirect(0))
            }
            assertThat(exception).isEqualTo(nativeException)
        }
    }

    @Nested
    inner class Presentations {

        @Test
        fun `getPresentations returns empty list if native getPresentations failed`() {
            val mockedAlpsNative = getMockedAlpsNative(
                nativeGetPresentationsResponse = null
            )
            alps = Alps(mockedAlpsNative)

            val returnedPresentations = alps.getPresentations()
            assertThat(returnedPresentations).isEqualTo(emptyList())
        }

        @ParameterizedTest
        @ArgumentsSource(PresentationsProvider::class)
        fun `getPresentations returns the same list as native call`(
            presentationsReturnedByNative: List<Presentation>,
            expectedPresentations: List<Presentation>,
        ) {
            val mockedAlpsNative = getMockedAlpsNative(
                nativeGetPresentationsResponse = presentationsReturnedByNative
            )
            alps = Alps(mockedAlpsNative)

            val returnedPresentations = alps.getPresentations()
            assertThat(returnedPresentations).isEqualTo(expectedPresentations)
        }

        @ParameterizedTest(name = "{0} when native method returned {1}")
        @CsvSource("-1, -1", "0, 0", "2, 2", "15, 15")
        fun `getActivePresentationId returns`(
            presentationIdReturnedByNative: Int,
            expectedPresentationId: Int
        ) {
            val mockedAlpsNative = getMockedAlpsNative(
                nativeGetActivePresentationIdResponse = presentationIdReturnedByNative
            )
            alps = Alps(mockedAlpsNative)

            val returnedPresentationId = alps.getActivePresentationId()
            assertThat(returnedPresentationId).isEqualTo(expectedPresentationId)
        }

        @Test
        fun `setActivePresentationId throws exception if native setting failed`() {
            val nativeException = AlpsException.Native.BuffTooSmall()
            val mockedAlpsNative = getMockedAlpsNative(
                nativeSetActivePresentationIdException = nativeException
            )
            alps = Alps(mockedAlpsNative)

            val exception = assertThrows<AlpsException> {
                alps.setActivePresentationId(-15)
            }
            assertThat(exception).isEqualTo(nativeException)
        }

        @ParameterizedTest
        @ValueSource(ints = [-1, 0, 3, 10, 100])
        fun `setActivePresentationId calls native function with proper id`(
            presentationId: Int
        ) {
            val mockedAlpsNative = getMockedAlpsNative()
            alps = Alps(mockedAlpsNative)

            alps.setActivePresentationId(presentationId)

            verify(exactly = 1) {
                mockedAlpsNative.setActivePresentationId(presentationId)
            }
        }
    }

    @Nested
    inner class Callback {
        @Test
        fun `setPresentationChangedCallback calls native function with proper callback`() {
            val mockedAlpsNative = getMockedAlpsNative()
            alps = Alps(mockedAlpsNative)
            val callback = object: PresentationsChangedCallback {
                override fun onPresentationsChanged() {}
            }

            alps.setPresentationsChangedCallback(callback)

            verify(exactly = 1) {
                mockedAlpsNative.setPresentationsChangedCallback(callback)
            }
        }
    }
}

private fun getMockedAlpsNative(
    isInitializedResponse: Boolean = true,
    nativeInitializeException: AlpsException? = null,
    nativeProcessBufferException: AlpsException? = null,
    nativeGetPresentationsResponse: List<Presentation>? = emptyList(),
    nativeGetActivePresentationIdResponse: Int = 0,
    nativeSetActivePresentationIdException: AlpsException? = null,
) = mockk<AlpsNative>(relaxed = true) {
        every { isInitialized() } returns isInitializedResponse
        nativeInitializeException?.let { every { initialize() } throws it }
        nativeProcessBufferException?.let { every { processIsobmffSegment(any()) } throws it }
        every { getPresentations() } returns nativeGetPresentationsResponse
        every { getActivePresentationId() } returns nativeGetActivePresentationIdResponse
        nativeSetActivePresentationIdException?.let { every { setActivePresentationId(any()) } throws it }
    }