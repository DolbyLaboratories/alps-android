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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * [AlpsMediaSourceFactory] is a helper class that allows using custom [DashMediaSource.Factory] for
 * DASH content.
 *
 * @param alpsDashFactory Factory that will be used to create media source for DASH content type
 * @param defaultFactory Factory that will be used to create media source for non-DASH content types
 */
@UnstableApi
class AlpsMediaSourceFactory(
    private val alpsDashFactory: DashMediaSource.Factory,
    private val defaultFactory: MediaSource.Factory,
): MediaSource.Factory {
    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        return when (Util.inferContentType(mediaItem.localConfiguration?.uri ?: Uri.EMPTY)) {
            C.CONTENT_TYPE_DASH -> alpsDashFactory.createMediaSource(mediaItem)
            else -> defaultFactory.createMediaSource(mediaItem)
        }
    }

    override fun getSupportedTypes(): IntArray {
        return (defaultFactory.supportedTypes + C.CONTENT_TYPE_DASH).distinct().toIntArray()
    }

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider) =
        defaultFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy) =
        defaultFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
}