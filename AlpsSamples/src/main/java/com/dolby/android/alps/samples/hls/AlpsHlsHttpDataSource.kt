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

package com.dolby.android.alps.samples

import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import com.dolby.android.alps.Alps
import com.dolby.android.alps.logger.AlpsLoggerProvider
import com.dolby.android.alps.samples.hls.AlpsManagerHls

/**
 * A [DataSource] used for HLS media segments that resolves whether ALPS processing should be
 * applied on the **first** [open] call, then delegates to either [AlpsProcessing] (AC-4 stream)
 * or a plain [HttpDataSource] (non-AC-4 stream) for all subsequent calls.
 *
 * @param alpsHlsManager Source of the rendition-URL→[Alps] map and playlist tracker.
 * @param defaultHttpDataSourceFactory Underlying HTTP factory.
 */
@UnstableApi
internal class AlpsHlsHttpDataSource(
    private val alpsHlsManager: AlpsManagerHls,
    private val defaultHttpDataSourceFactory: HttpDataSource.Factory,
) : DataSource {

    companion object Companion {
        /** Sentinel meaning "not yet resolved". Distinct from null (= resolved as non-AC-4). */
        private val UNRESOLVED = Any()
    }

    /**
     * Cached resolution result after the first [open] call.
     * - [UNRESOLVED]: [open] has not been called yet.
     * - `null`: stream is not AC-4 — use plain [delegateHttpDataSource].
     * - [AlpsHlsManager.ResolvedAlps]: stream is AC-4 — use [alpsProcessing].
     */
    private var resolvedAlps: Any? = UNRESOLVED

    /** Created lazily once [resolvedAlps] is confirmed non-null (AC-4 stream). */
    private var alpsProcessing: AlpsProcessing? = null

    /** Always-present plain HTTP data source. Used directly for non-AC-4 streams. */
    private val delegateHttpDataSource: HttpDataSource =
        defaultHttpDataSourceFactory.createDataSource()

    // -------------------------------------------------------------------------
    // DataSource
    // -------------------------------------------------------------------------

    override fun open(dataSpec: DataSpec): Long {
        ensureResolved(dataSpec.uri)
        return alpsProcessing?.open(dataSpec) ?: delegateHttpDataSource.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return alpsProcessing?.read(buffer, offset, length) ?: delegateHttpDataSource.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = delegateHttpDataSource.uri

    override fun close() {
        delegateHttpDataSource.close()
    }

    override fun addTransferListener(transferListener: TransferListener) {
        delegateHttpDataSource.addTransferListener(transferListener)
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Performs the one-time resolution on the first [open] call.
     *
     * Asks [AlpsHlsManager.resolveAlpsForSegmentUri] to find the [Alps] instance (if any)
     * responsible for the stream that produced [segmentUri]. Caches the result so that no
     * further lookup is done for subsequent segments of the same stream.
     */
    private fun ensureResolved(segmentUri: Uri) {
        if (resolvedAlps !== UNRESOLVED) return

        val alps: Alps? = alpsHlsManager.resolveAlpsForSegmentUri(segmentUri)
        resolvedAlps = alps
        if (alps != null) {
            alpsProcessing = AlpsProcessing(alps, delegateHttpDataSource)
        }
    }
}
