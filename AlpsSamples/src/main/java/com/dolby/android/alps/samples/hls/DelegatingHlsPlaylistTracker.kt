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

package com.dolby.android.alps.samples.hls

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker
import androidx.media3.exoplayer.source.MediaSourceEventListener

/**
 * A [androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker] that delegates all calls to [delegate] and additionally fires
 * [onMultivariantPlaylist] with the **initial playlist URI** and the [androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist]
 * as soon as the primary playlist is first refreshed (which is when the multivariant playlist
 * is guaranteed to be available).
 */
@UnstableApi
internal class DelegatingHlsPlaylistTracker(
    private val delegate: HlsPlaylistTracker,
    private val onMultivariantPlaylist: (Uri, HlsMultivariantPlaylist) -> Unit,
    private val onMediaPlaylist: ((Uri, HlsMediaPlaylist) -> Unit)? = null,
) : HlsPlaylistTracker by delegate {

    override fun start(
        initialPlaylistUri: Uri,
        eventDispatcher: MediaSourceEventListener.EventDispatcher,
        primaryPlaylistListener: HlsPlaylistTracker.PrimaryPlaylistListener,
    ) {
        delegate.start(
            initialPlaylistUri,
            eventDispatcher,
        ) { mediaPlaylist ->
            primaryPlaylistListener.onPrimaryPlaylistRefreshed(mediaPlaylist)
            delegate.multivariantPlaylist?.let {
                onMultivariantPlaylist(initialPlaylistUri, it)
            }
            onMediaPlaylist?.invoke(initialPlaylistUri, mediaPlaylist)
        }
    }

    override fun deactivatePlaylistForPlayback(url: Uri) {
        delegate.deactivatePlaylistForPlayback(url)
    }
}
