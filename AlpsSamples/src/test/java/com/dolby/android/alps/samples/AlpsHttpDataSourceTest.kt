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

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.dolby.android.alps.Alps
import com.dolby.android.alps.samples.utils.Ac4DataSourceDetector
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AlpsHttpDataSourceTest {
    companion object {
        private const val EXAMPLE_SEGMENT_SIZE = 100L
        private const val EXAMPLE_SINGLE_READ_LENGTH = (EXAMPLE_SEGMENT_SIZE/2).toInt()
        private const val AMOUNT_OF_PROCESS_ISOBMFF_SEGMENT_CALLS_PER_SEGMENT = 1
        private const val AMOUNT_OF_HTTP_READ_CALLS_FOR_EXAMPLE_SEGMENT = 2
    }
    private lateinit var alpsHttpDataSource: AlpsHttpDataSource

    @Nested
    inner class OpenMethod {
        @Test
        fun `isAc4DataSource called only for first open call`() {
            val mockedDetector = getMockedAc4DataSourceDetector()
            val mockedDataSpec = getMockedDataSpec()
            alpsHttpDataSource = createAlpsHttpDataSource(
                getMockedAlps(),
                mockedDetector,
                getMockedDefaultHttpDataSourceFactory()
            )

            alpsHttpDataSource.open(mockedDataSpec)
            alpsHttpDataSource.open(mockedDataSpec)

            verify(exactly = 1) {
                mockedDetector.isAc4DataSource(mockedDataSpec.uri)
            }
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource("off, false", "on, true")
        fun `open returns same value as defaultHttpDataSource when alps processing is`(
            alpsProcessing: String,
            isAc4DataSourceResult: Boolean
        ) {
            alpsHttpDataSource = createAlpsHttpDataSource(
                getMockedAlps(),
                getMockedAc4DataSourceDetector(
                    isAc4DataSourceResult = isAc4DataSourceResult
                ),
                getMockedDefaultHttpDataSourceFactory(
                    getMockedDefaultHttpDataSource(
                        openReturnValue = EXAMPLE_SEGMENT_SIZE
                    )
                )
            )

            val returnedValue = alpsHttpDataSource.open(getMockedDataSpec())

            assertThat(returnedValue).isEqualTo(EXAMPLE_SEGMENT_SIZE)
        }
    }

    @Nested
    inner class ReadMethod {
        @Test
        fun `alps processing not used when ac4DataSource was not detected`() {
            val mockedDefaultHttpDataSource = getMockedDefaultHttpDataSource(
                openReturnValue = EXAMPLE_SEGMENT_SIZE,
            )
            val fakeBuffer = ByteArray(EXAMPLE_SEGMENT_SIZE.toInt())
            val fakeOffset = 0
            val fakeLength = EXAMPLE_SINGLE_READ_LENGTH
            alpsHttpDataSource = createAlpsHttpDataSource(
                getMockedAlps(),
                getMockedAc4DataSourceDetector(
                    isAc4DataSourceResult = false
                ),
                getMockedDefaultHttpDataSourceFactory(
                    mockedDefaultHttpDataSource
                )
            )

            alpsHttpDataSource.open(getMockedDataSpec())
            alpsHttpDataSource.read(fakeBuffer, fakeOffset, fakeLength)

            verify(exactly = 1) {
                mockedDefaultHttpDataSource.read(fakeBuffer, fakeOffset, fakeLength)
            }
        }

        @Test
        fun `alps processing used when ac4DataSource was detected`() {
            val mockedDefaultHttpDataSource = getMockedDefaultHttpDataSource(
                openReturnValue = EXAMPLE_SEGMENT_SIZE,
                readReturnValue = EXAMPLE_SINGLE_READ_LENGTH
            )
            alpsHttpDataSource = createAlpsHttpDataSource(
                getMockedAlps(),
                getMockedAc4DataSourceDetector(
                    isAc4DataSourceResult = true
                ),
                getMockedDefaultHttpDataSourceFactory(
                    mockedDefaultHttpDataSource
                )
            )

            alpsHttpDataSource.open(getMockedDataSpec())
            alpsHttpDataSource.read(ByteArray(0), 0, 0)

            verify(atLeast = 2) {
                mockedDefaultHttpDataSource.read(any(), any(), any())
            }
        }

        @Test
        fun `segment loading and processing happens on first read call only`() {
            val mockedAlps = getMockedAlps()
            val mockedDefaultHttpDataSource = getMockedDefaultHttpDataSource(
                openReturnValue = EXAMPLE_SEGMENT_SIZE,
                readReturnValue = EXAMPLE_SINGLE_READ_LENGTH
            )
            val fakeBuffer = ByteArray(EXAMPLE_SEGMENT_SIZE.toInt())
            val fakeOffset = 0
            val fakeLength = EXAMPLE_SINGLE_READ_LENGTH
            alpsHttpDataSource = createAlpsHttpDataSource(
                mockedAlps,
                getMockedAc4DataSourceDetector(
                    isAc4DataSourceResult = true
                ),
                getMockedDefaultHttpDataSourceFactory(
                    mockedDefaultHttpDataSource
                )
            )

            alpsHttpDataSource.open(getMockedDataSpec())
            alpsHttpDataSource.read(fakeBuffer, fakeOffset, fakeLength)

            verify(atLeast = AMOUNT_OF_HTTP_READ_CALLS_FOR_EXAMPLE_SEGMENT) {
                mockedDefaultHttpDataSource.read(any(), any(), any())
            }
            verify(exactly = AMOUNT_OF_PROCESS_ISOBMFF_SEGMENT_CALLS_PER_SEGMENT) {
                mockedAlps.processIsobmffSegment(any())
            }

            clearMocks(mockedDefaultHttpDataSource, mockedAlps)

            alpsHttpDataSource.read(fakeBuffer, fakeOffset, fakeLength)

            verify(exactly = 0) {
                mockedDefaultHttpDataSource.read(any(), any(), any())
                mockedAlps.processIsobmffSegment(any())
            }
        }

        @Test
        fun `alps processing resets after open call`() {
            val mockedAlps = getMockedAlps()
            val mockedDefaultHttpDataSource = getMockedDefaultHttpDataSource(
                openReturnValue = EXAMPLE_SEGMENT_SIZE,
                readReturnValue = EXAMPLE_SINGLE_READ_LENGTH
            )
            val fakeBuffer = ByteArray(EXAMPLE_SEGMENT_SIZE.toInt())
            val fakeOffset = 0
            val fakeLength = EXAMPLE_SINGLE_READ_LENGTH
            alpsHttpDataSource = createAlpsHttpDataSource(
                mockedAlps,
                getMockedAc4DataSourceDetector(
                    isAc4DataSourceResult = true
                ),
                getMockedDefaultHttpDataSourceFactory(
                    mockedDefaultHttpDataSource
                )
            )

            alpsHttpDataSource.open(getMockedDataSpec())
            alpsHttpDataSource.read(fakeBuffer, fakeOffset, fakeLength)

            verify(atLeast = AMOUNT_OF_HTTP_READ_CALLS_FOR_EXAMPLE_SEGMENT) {
                mockedDefaultHttpDataSource.read(any(), any(), any())
            }
            verify(exactly = AMOUNT_OF_PROCESS_ISOBMFF_SEGMENT_CALLS_PER_SEGMENT) {
                mockedAlps.processIsobmffSegment(any())
            }

            alpsHttpDataSource.open(getMockedDataSpec())
            alpsHttpDataSource.read(fakeBuffer, fakeOffset, fakeLength)

            verify(atLeast = AMOUNT_OF_HTTP_READ_CALLS_FOR_EXAMPLE_SEGMENT * 2) {
                mockedDefaultHttpDataSource.read(any(), any(), any())
            }
            verify(exactly = AMOUNT_OF_PROCESS_ISOBMFF_SEGMENT_CALLS_PER_SEGMENT * 2) {
                mockedAlps.processIsobmffSegment(any())
            }
        }
    }

    @Nested
    inner class UnmodifiedMethods {
        @Test
        fun `getUri calls defaultHttpDataSource getUri`() {
            val mockedUri = mockk<Uri>(relaxed = true)
            val mockedDefaultHttpDataSource = getMockedDefaultHttpDataSource(
                uriReturnValue = mockedUri
            )
            alpsHttpDataSource = createAlpsHttpDataSource(
                getMockedAlps(),
                getMockedAc4DataSourceDetector(),
                getMockedDefaultHttpDataSourceFactory(
                    mockedDefaultHttpDataSource
                )
            )

            val returnedUri = alpsHttpDataSource.uri

            verify(exactly = 1) {
                mockedDefaultHttpDataSource.uri
            }
            assertThat(returnedUri).isEqualTo(mockedUri)
        }

        @Test
        fun `close calls defaultHttpDataSource close`() {
            val mockedDefaultHttpDataSource = getMockedDefaultHttpDataSource()
            alpsHttpDataSource = createAlpsHttpDataSource(
                getMockedAlps(),
                getMockedAc4DataSourceDetector(),
                getMockedDefaultHttpDataSourceFactory(
                    mockedDefaultHttpDataSource
                )
            )

            alpsHttpDataSource.close()

            verify(exactly = 1) {
                mockedDefaultHttpDataSource.close()
            }
        }

        @Test
        fun `getResponseHeaders calls defaultHttpDataSource getResponseHeaders`() {
            val mockedResponseHeaders = mockk<MutableMap<String, MutableList<String>>>(relaxed = true)
            val mockedDefaultHttpDataSource = getMockedDefaultHttpDataSource(
                responseHeadersReturnValue = mockedResponseHeaders
            )
            alpsHttpDataSource = createAlpsHttpDataSource(
                getMockedAlps(),
                getMockedAc4DataSourceDetector(),
                getMockedDefaultHttpDataSourceFactory(
                    mockedDefaultHttpDataSource
                )
            )

            val returnedResponseHeaders = alpsHttpDataSource.responseHeaders

            verify(exactly = 1) {
                mockedDefaultHttpDataSource.responseHeaders
            }
            assertThat(returnedResponseHeaders).isEqualTo(mockedResponseHeaders)
        }

        @Test
        fun `setRequestProperty calls defaultHttpDataSource setRequestProperty`() {
            val mockedName = "PropertyName"
            val mockedValue = "PropertyValue"
            val mockedDefaultHttpDataSource = getMockedDefaultHttpDataSource()
            alpsHttpDataSource = createAlpsHttpDataSource(
                getMockedAlps(),
                getMockedAc4DataSourceDetector(),
                getMockedDefaultHttpDataSourceFactory(
                    mockedDefaultHttpDataSource
                )
            )

            alpsHttpDataSource.setRequestProperty(mockedName, mockedValue)

            verify(exactly = 1) {
                mockedDefaultHttpDataSource.setRequestProperty(mockedName, mockedValue)
            }
        }

        @Test
        fun `clearRequestProperty calls defaultHttpDataSource clearRequestProperty`() {
            val mockedName = "PropertyName"
            val mockedDefaultHttpDataSource = getMockedDefaultHttpDataSource()
            alpsHttpDataSource = createAlpsHttpDataSource(
                getMockedAlps(),
                getMockedAc4DataSourceDetector(),
                getMockedDefaultHttpDataSourceFactory(
                    mockedDefaultHttpDataSource
                )
            )

            alpsHttpDataSource.clearRequestProperty(mockedName)

            verify(exactly = 1) {
                mockedDefaultHttpDataSource.clearRequestProperty(mockedName)
            }
        }

        @Test
        fun `clearAllRequestProperties calls defaultHttpDataSource clearAllRequestProperties`() {
            val mockedDefaultHttpDataSource = getMockedDefaultHttpDataSource()
            alpsHttpDataSource = createAlpsHttpDataSource(
                getMockedAlps(),
                getMockedAc4DataSourceDetector(),
                getMockedDefaultHttpDataSourceFactory(
                    mockedDefaultHttpDataSource
                )
            )

            alpsHttpDataSource.clearAllRequestProperties()

            verify(exactly = 1) {
                mockedDefaultHttpDataSource.clearAllRequestProperties()
            }
        }

        @Test
        fun `getResponseCode calls defaultHttpDataSource getResponseCode`() {
            val mockedResponseCode = 200
            val mockedDefaultHttpDataSource = getMockedDefaultHttpDataSource(
                responseCodeReturnValue = mockedResponseCode
            )
            alpsHttpDataSource = createAlpsHttpDataSource(
                getMockedAlps(),
                getMockedAc4DataSourceDetector(),
                getMockedDefaultHttpDataSourceFactory(
                    mockedDefaultHttpDataSource
                )
            )

            val returnedResponseCode = alpsHttpDataSource.responseCode

            verify(exactly = 1) {
                mockedDefaultHttpDataSource.responseCode
            }
            assertThat(returnedResponseCode).isEqualTo(mockedResponseCode)
        }
    }

    private fun createAlpsHttpDataSource(
        alps: Alps,
        detector: Ac4DataSourceDetector,
        defaultHttpDataSourceFactory: DefaultHttpDataSource.Factory,
    ): AlpsHttpDataSource {
        return AlpsHttpDataSource.Factory(
            alps,
            detector,
            defaultHttpDataSourceFactory,
        ).createDataSource() as? AlpsHttpDataSource
            ?: throw Exception("AlpsHttpDataSource creation failed")
    }

    private fun getMockedAlps() = mockk<Alps>(relaxed = true)

    private fun getMockedAc4DataSourceDetector(
        isAc4DataSourceResult: Boolean = true
    ) = mockk<Ac4DataSourceDetector>(relaxed = true) {
        every { isAc4DataSource(any()) } returns isAc4DataSourceResult
    }

    private fun getMockedDataSpec() = DataSpec(mockk<Uri>(relaxed = true))

    private fun getMockedDefaultHttpDataSourceFactory(
        defaultHttpDataSource: DefaultHttpDataSource = getMockedDefaultHttpDataSource()
    ) = mockk<DefaultHttpDataSource.Factory>(relaxed = true) {
        every { createDataSource() } returns defaultHttpDataSource
    }

    private fun getMockedDefaultHttpDataSource(
        openReturnValue: Long = 0L,
        readReturnValue: Int = 0,
        uriReturnValue: Uri = mockk<Uri>(relaxed = true),
        responseHeadersReturnValue: MutableMap<String, MutableList<String>> =
            mockk<MutableMap<String, MutableList<String>>>(relaxed = true),
        responseCodeReturnValue: Int = 200,
    ) = mockk<DefaultHttpDataSource>(relaxed = true) {
        every { open(any()) } returns openReturnValue
        every { read(any(), any(), any()) } returns readReturnValue
        every { uri } returns uriReturnValue
        every { responseHeaders } returns responseHeadersReturnValue
        every { responseCode } returns  responseCodeReturnValue
    }
}