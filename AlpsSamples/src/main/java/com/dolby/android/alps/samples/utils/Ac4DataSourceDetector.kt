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

package com.dolby.android.alps.samples.utils

import android.net.Uri
import androidx.media3.common.C.TRACK_TYPE_AUDIO
import androidx.media3.common.C.TRACK_TYPE_CAMERA_MOTION
import androidx.media3.common.C.TRACK_TYPE_VIDEO
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.dash.manifest.DashManifest
import com.dolby.android.alps.logger.AlpsLoggerProvider
import com.dolby.android.alps.samples.models.AdaptationSet

/**
 * Interface used by [AlpsHttpDataSource] to detect whether data source (represented by uri) is an
 * AC-4 stream.
 */
interface Ac4DataSourceDetector {
    /**
     * @param uri URI of data source
     * @return true if [uri] points to AC-4 stream, false otherwise
     */
    fun isAc4DataSource(uri: Uri): Boolean
}

/**
 * Example implementation of [Ac4DataSourceDetector] interface. Uses DASH manifest to deduce whether
 * data source (uri) is an AC-4 stream. [DashManifest] object must be provided during initialization
 * or later. Before providing manifest, [isAc4DataSource] will always return false.
 *
 * @param manifest DASH manifest
 */
@UnstableApi
class DashManifestAc4DataSourceDetector(
    manifest: DashManifest?
): Ac4DataSourceDetector {
    var manifest: DashManifest? = manifest
        set(value) {
            if (field != value) {
                field = value
                AlpsLoggerProvider.i("DashManifestAc4DataSourceDetector manifest set to $value")
                extractDashManifestData()
            }
        }
    private val adaptationSets = mutableListOf<AdaptationSet>()
    private val uriDetectionResultMap = mutableMapOf<String, Boolean>()

    init {
        manifest?.let {
            extractDashManifestData()
        }
    }

    override fun isAc4DataSource(uri: Uri): Boolean {
        uri.path?.let { uriPath ->
            uriDetectionResultMap[uriPath]?.let { return it }

            return checkNewUri(uriPath).also {
                uriDetectionResultMap[uriPath] = it
            }
        }
        return false
    }

    private fun checkNewUri(uri: String): Boolean {
        if (uri.endsWith("mp4") || uri.endsWith("m4s")) {
            adaptationSets.forEach { adaptationSet ->
                val representationsMatchingUri = adaptationSet.representations
                    .filter { it.initSegmentUrl != null }
                    .filter { it.initSegmentUrl!!.contains(uri) }
                    .ifEmpty {
                        // No matching representation found in this adaptation set, check next one
                        AlpsLoggerProvider.i("Uri: $uri no match found in adaptation set: $adaptationSet")
                        return@forEach
                    }

                if (adaptationSet.isAudioSet == false) {
                    AlpsLoggerProvider.i("Uri: $uri matches non Audio set.")
                    return false
                }

                if(representationsMatchingUri.any {
                        it.containerMimeType == "audio/mp4" &&
                                it.sampleMimeType == "audio/ac4"
                    }) {
                    AlpsLoggerProvider.i("Uri: $uri detected  as AC4 source.")
                    return true
                }
                AlpsLoggerProvider.i("Uri: $uri found matching representation but not AC4.")
            }
        }
        return false
    }

    private fun extractDashManifestData() {
        manifest?.let { manifest ->
            for (periodIndex in 0 until manifest.periodCount) {
                manifest.getPeriod(periodIndex).adaptationSets.forEach { adaptationSet ->
                    val isAudioSet = when (adaptationSet.type) {
                        TRACK_TYPE_AUDIO -> true
                        in TRACK_TYPE_VIDEO..TRACK_TYPE_CAMERA_MOTION -> false
                        else -> null
                    }
                    val representationsList = mutableListOf<AdaptationSet.Representation>()

                    adaptationSet.representations.forEach { representation ->
                        val uri = representation.initializationUri?.resolveUriString(
                            representation.baseUrls.getOrNull(0)?.url ?: ""
                        )

                        representationsList.add(
                            AdaptationSet.Representation(
                                representation.format.containerMimeType,
                                representation.format.sampleMimeType,
                                uri
                            )
                        )
                    }

                    adaptationSets.add(
                        AdaptationSet(
                            isAudioSet,
                            representationsList
                        )
                    )
                }
            }
        }
    }
}