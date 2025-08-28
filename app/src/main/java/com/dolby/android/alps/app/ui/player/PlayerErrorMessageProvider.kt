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

package com.dolby.android.alps.app.ui.player

import android.content.Context
import android.util.Pair
import androidx.media3.common.ErrorMessageProvider
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import com.dolby.android.alps.app.R

class PlayerErrorMessageProvider(private val context: Context): ErrorMessageProvider<PlaybackException> {
    override fun getErrorMessage(e: PlaybackException): Pair<Int, String> {
        var errorString = context.getString(R.string.error_generic)
        val throwable = e.cause
        if (throwable is MediaCodecRenderer.DecoderInitializationException) {
            // Special case for decoder initialization failures.
            val decoderInitializationException = throwable
            if (decoderInitializationException.codecInfo == null) {
                if (decoderInitializationException.cause is MediaCodecUtil.DecoderQueryException) {
                    errorString = context.getString(R.string.error_querying_decoders)
                } else if (decoderInitializationException.secureDecoderRequired) {
                    errorString =
                        context.getString(
                            R.string.error_no_secure_decoder,
                            decoderInitializationException.mimeType
                        )
                } else {
                    errorString =
                        context.getString(
                            R.string.error_no_decoder,
                            decoderInitializationException.mimeType
                        )
                }
            } else {
                errorString =
                    context.getString(
                        R.string.error_instantiating_decoder,
                        decoderInitializationException.codecInfo?.name
                    )
            }
        }
        return Pair.create(0, errorString)
    }
}