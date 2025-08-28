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

package com.dolby.android.alps.cli

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.dolby.android.alps.Alps
import com.dolby.android.alps.logger.AlpsLogger
import com.dolby.android.alps.logger.AlpsLoggerProvider
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

class AlpsCliBroadcastReceiver: BroadcastReceiver() {
    companion object {
        private const val ALPS_LIBRARY_LOGS_TAG = "ALPS Library"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        initializeNapier()
        Napier.i("ALPS CLI v${BuildConfig.VERSION_NAME} received broadcast")
        Napier.i("ALPS CLI is using ALPS library v${Alps.getVersion()}")
        try {
            val processingParams = intent?.let {
                ProcessingParams.extractFromIntent(it)
            } ?: throw Exception("Missing intent data")

            val workRequest = OneTimeWorkRequestBuilder<AlpsCliWorker>()
                .setInputData(processingParams.toData())
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        } catch (e: Exception) {
            Napier.e("ALPS CLI Failed. Error: ${e.message}")
        }
    }

    private fun initializeNapier() {
        if (BuildConfig.DEBUG) {
            Napier.takeLogarithm()
            Napier.base(DebugAntilog("ALPS_CLI"))

            AlpsLoggerProvider.logger = object: AlpsLogger {
                override fun logInfo(message: String) {
                    Napier.i(
                        tag = ALPS_LIBRARY_LOGS_TAG,
                        message = message
                    )
                }

                override fun logWarn(message: String) {
                    Napier.w(
                        tag = ALPS_LIBRARY_LOGS_TAG,
                        message = message
                    )
                }

                override fun logError(message: String) {
                    Napier.e(
                        tag = ALPS_LIBRARY_LOGS_TAG,
                        message = message
                    )
                }
            }
        }
    }
}