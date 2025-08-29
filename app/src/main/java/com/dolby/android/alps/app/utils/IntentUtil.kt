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

package com.dolby.android.alps.app.utils

import android.content.Intent
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.Util
import com.google.common.base.Preconditions

class IntentUtil {
    companion object {
        const val ACTION_VIEW = "com.dolby.android.alps.app.action.VIEW"

        const val PRESENTATION_CHANGE_STYLE_EXTRA = "presentation_change_style"
        const val IS_ALPS_ENABLED = "is_alps_enabled"

        private const val TITLE_EXTRA = "title"
        private const val DESCRIPTION_EXTRA = "description"
        private const val MIME_TYPE_EXTRA = "mime_type"

        private const val DRM_SCHEME_EXTRA = "drm_scheme"
        private const val DRM_LICENSE_URI_EXTRA = "drm_license_uri"
        private const val DRM_KEY_REQUEST_PROPERTIES_EXTRA = "drm_key_request_properties"
        private const val DRM_SESSION_FOR_CLEAR_CONTENT = "drm_session_for_clear_content"
        private const val DRM_MULTI_SESSION_EXTRA = "drm_multi_session"
        private const val DRM_FORCE_DEFAULT_LICENSE_URI_EXTRA = "drm_force_default_license_uri"

        fun createMediaItemsFromIntent(intent: Intent): List<MediaItem> {
            val mediaItems: MutableList<MediaItem> = ArrayList()
            val uri = intent.data ?: return emptyList()
            mediaItems.add(
                createMediaItemFromIntent(
                    uri,
                    intent,
                )
            )

            return mediaItems
        }

        fun addMediaItemToIntent(mediaItem: MediaItem, intent: Intent) {
            val localConfiguration = checkNotNull(mediaItem.localConfiguration)
            intent.setAction(ACTION_VIEW).data = mediaItem.localConfiguration!!.uri
            mediaItem.mediaMetadata.title?.let {
                intent.putExtra(TITLE_EXTRA, it)
            }
            mediaItem.mediaMetadata.description?.let {
                intent.putExtra(DESCRIPTION_EXTRA, it)
            }

            intent.putExtra(MIME_TYPE_EXTRA, localConfiguration.mimeType)
            localConfiguration.drmConfiguration?.let {
                addDrmConfigurationToIntent(it, intent)
            }
        }

        private fun createMediaItemFromIntent(
            uri: Uri,
            intent: Intent,
        ): MediaItem {
            val title = intent.getStringExtra(TITLE_EXTRA)
            val description = intent.getStringExtra(DESCRIPTION_EXTRA)
            val mimeType = intent.getStringExtra(MIME_TYPE_EXTRA)
            val builder =
                MediaItem.Builder()
                    .setUri(uri)
                    .setMimeType(mimeType)
                    .setMediaMetadata(MediaMetadata.Builder()
                        .setTitle(title)
                        .setDescription(description)
                        .build()
                    )

            return populateDrmPropertiesFromIntent(builder, intent).build()
        }

        private fun populateDrmPropertiesFromIntent(
            builder: MediaItem.Builder,
            intent: Intent,
        ): MediaItem.Builder {
            val drmSchemeExtra = intent.getStringExtra(DRM_SCHEME_EXTRA) ?: return builder
            val headers: MutableMap<String, String> = HashMap()
            val keyRequestPropertiesArray =
                intent.getStringArrayExtra(DRM_KEY_REQUEST_PROPERTIES_EXTRA)
            if (keyRequestPropertiesArray != null) {
                var i = 0
                while (i < keyRequestPropertiesArray.size) {
                    headers[keyRequestPropertiesArray[i]] = keyRequestPropertiesArray[i + 1]
                    i += 2
                }
            }
            val drmUuid = Util.getDrmUuid(drmSchemeExtra)
            if (drmUuid != null) {
                builder.setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(drmUuid)
                        .setLicenseUri(intent.getStringExtra(DRM_LICENSE_URI_EXTRA))
                        .setMultiSession(
                            intent.getBooleanExtra(DRM_MULTI_SESSION_EXTRA, false)
                        )
                        .setForceDefaultLicenseUri(
                            intent.getBooleanExtra(DRM_FORCE_DEFAULT_LICENSE_URI_EXTRA, false)
                        )
                        .setLicenseRequestHeaders(headers)
                        .setForceSessionsForAudioAndVideoTracks(
                            intent.getBooleanExtra(DRM_SESSION_FOR_CLEAR_CONTENT, false)
                        )
                        .build()
                )
            }
            return builder
        }

        private fun addDrmConfigurationToIntent(
            drmConfiguration: MediaItem.DrmConfiguration,
            intent: Intent,
        ) {
            intent.putExtra(DRM_SCHEME_EXTRA, drmConfiguration.scheme.toString())
            intent.putExtra(DRM_LICENSE_URI_EXTRA,
                if (drmConfiguration.licenseUri != null) drmConfiguration.licenseUri.toString()
                else null
            )
            intent.putExtra(DRM_MULTI_SESSION_EXTRA, drmConfiguration.multiSession)
            intent.putExtra(DRM_FORCE_DEFAULT_LICENSE_URI_EXTRA, drmConfiguration.forceDefaultLicenseUri)

            val drmKeyRequestProperties =
                arrayOfNulls<String>(drmConfiguration.licenseRequestHeaders.size * 2)
            var index = 0
            for ((key, value) in drmConfiguration.licenseRequestHeaders) {
                drmKeyRequestProperties[index++] = key
                drmKeyRequestProperties[index++] = value
            }
            intent.putExtra(DRM_KEY_REQUEST_PROPERTIES_EXTRA, drmKeyRequestProperties)

            val forcedDrmSessionTrackTypes: List<Int> = drmConfiguration.forcedSessionTrackTypes
            if (forcedDrmSessionTrackTypes.isNotEmpty()) {
                // Only video and audio together are supported.
                Preconditions.checkState(
                    forcedDrmSessionTrackTypes.size == 2 &&
                            forcedDrmSessionTrackTypes.contains(C.TRACK_TYPE_VIDEO) &&
                            forcedDrmSessionTrackTypes.contains(C.TRACK_TYPE_AUDIO)
                )
                intent.putExtra(DRM_SESSION_FOR_CLEAR_CONTENT, true)
            }
        }
    }
}