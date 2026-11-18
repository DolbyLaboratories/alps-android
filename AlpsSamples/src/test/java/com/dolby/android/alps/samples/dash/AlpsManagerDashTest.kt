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

package com.dolby.android.alps.samples.dash

import androidx.media3.common.util.UnstableApi
import com.dolby.android.alps.samples.AlpsManager
import com.dolby.android.alps.samples.AlpsManagerTest

/**
 * Runs the shared [AlpsManagerTest] contract against [AlpsManagerDash].
 *
 * A "stream" maps to a DASH period: preparing it is what the
 * [AlpsDashChunkSourceFactory] does when it asks for the [com.dolby.android.alps.Alps] object of a
 * period, playing it is what happens on a position discontinuity.
 */
@UnstableApi
class AlpsManagerDashTest : AlpsManagerTest() {

    private val dashManager get() = manager as AlpsManagerDash

    override fun createManager(presentationSelectionPersistenceEnabled: Boolean): AlpsManager =
        AlpsManagerDash(presentationSelectionPersistenceEnabled)
            .apply { alpsFactory = ::createAlps }

    override fun prepareStream(index: Int) {
        dashManager.getAlps(index)
    }

    override fun playStream(index: Int) {
        dashManager.setCurrentPeriodIndex(index)
    }
}
