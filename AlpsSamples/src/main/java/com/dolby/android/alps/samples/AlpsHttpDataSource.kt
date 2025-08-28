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
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.dolby.android.alps.Alps
import com.dolby.android.alps.samples.utils.Ac4DataSourceDetector

/**
 * AlpsHttpDataSource is a sample class that allows easy integration of ALPS library with Exoplayer.
 *
 * AlpsHttpDataSource is an alternative for DefaultHttpDataSource. It adds ALPS library processing
 * on top of DefaultHttpDataSource (or other implementation of HttpDataSource interface) operations.
 *
 * Actual job of AlpsHttpDataSource is to decide whether ALPS library processing should be applied
 * to currently processed data source, and the processing is hidden [AlpsProcessing] object.
 * Exoplayer will use this HttpDataSource to download not only AC-4 audio stream, but also video
 * stream and other audio streams that ALPS library should not process. [AlpsHttpDataSource] decides
 * whether it should pass data to [AlpsProcessing] or not, using injected implementation of
 * [Ac4DataSourceDetector] interface.
 *
 * @param alps [Alps] object that will be used for ALPS processing of AC-4 sources
 * @param ac4DataSourceDetector [Ac4DataSourceDetector] implementation that is used to decide
 * whether source is AC-4
 * @param defaultHttpDataSource [HttpDataSource] implementation, can be Default or some custom if
 * needed. Used for data downloading.
 */
@UnstableApi
class AlpsHttpDataSource(
    alps: Alps,
    private val ac4DataSourceDetector: Ac4DataSourceDetector,
    private val defaultHttpDataSource: HttpDataSource
): BaseDataSource(true), HttpDataSource {
    /**
     * Factory class for [AlpsHttpDataSource].
     *
     * Used by Exoplayer to create [AlpsHttpDataSource] objects.
     *
     * @param alps [Alps] object that will be passed to [AlpsHttpDataSource]
     * @param ac4DataSourceDetector [Ac4DataSourceDetector] implementation that is passed to
     * [AlpsHttpDataSource]
     * @param defaultHttpDataSourceFactory used to create [HttpDataSource] implementation objects
     * that will be used in [AlpsHttpDataSource]
     */
    class Factory(
        private val alps: Alps,
        private val ac4DataSourceDetector: Ac4DataSourceDetector,
        private val defaultHttpDataSourceFactory: HttpDataSource.Factory,
    ): DataSource.Factory {
        override fun createDataSource(): DataSource {
            return AlpsHttpDataSource(
                alps,
                ac4DataSourceDetector,
                defaultHttpDataSourceFactory.createDataSource()
            )
        }
    }
    private val alpsProcessing = AlpsProcessing(alps, defaultHttpDataSource)
    private var shouldProcess: Boolean? = null

    override fun open(dataSpec: DataSpec): Long {
        if (shouldProcess == null) {
            /* Based on the init segment URI - detect AC4 data source */
            shouldProcess = ac4DataSourceDetector.isAc4DataSource(dataSpec.uri)
        }
        return if (shouldProcess == true) {
            alpsProcessing.open(dataSpec)
        } else {
            defaultHttpDataSource.open(dataSpec)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return if (shouldProcess == true) {
            alpsProcessing.read(buffer, offset, length)
        } else {
            defaultHttpDataSource.read(buffer, offset, length)
        }
    }

    // Below methods don't need custom implementation

    override fun getUri(): Uri? = defaultHttpDataSource.uri

    override fun close() {
        defaultHttpDataSource.close()
    }

    override fun getResponseHeaders(): MutableMap<String, MutableList<String>> {
        return defaultHttpDataSource.responseHeaders
    }

    override fun setRequestProperty(name: String, value: String) {
        defaultHttpDataSource.setRequestProperty(name, value)
    }

    override fun clearRequestProperty(name: String) {
        defaultHttpDataSource.clearRequestProperty(name)
    }

    override fun clearAllRequestProperties() {
        defaultHttpDataSource.clearAllRequestProperties()
    }

    override fun getResponseCode(): Int {
        return defaultHttpDataSource.responseCode
    }
}