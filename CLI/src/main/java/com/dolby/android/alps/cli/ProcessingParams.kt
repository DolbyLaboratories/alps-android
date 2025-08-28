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

import android.content.Intent
import androidx.work.Data
import java.security.InvalidParameterException

data class ProcessingParams(
    val input: String,
    val output: String,
    val pres: Int
) {
    companion object {
        fun extractFromIntent(intent: Intent): ProcessingParams {
            return ProcessingParams(
                input = intent.getStringExtra(ProcessingParam.INPUT.fullName)
                    ?: intent.getStringExtra(ProcessingParam.INPUT.shortName)
                    ?: throw InvalidParameterException(
                        "Parameter ${ProcessingParam.INPUT.fullName} is required"
                    ),
                output = intent.getStringExtra(ProcessingParam.OUTPUT.fullName)
                    ?: intent.getStringExtra(ProcessingParam.OUTPUT.shortName)
                    ?: throw InvalidParameterException(
                        "Parameter ${ProcessingParam.OUTPUT.fullName} is required"
                    ),
                pres = getPresFromArgs(intent)
            )
        }

        private const val PRES_DEFAULT_VALUE_WHEN_MISSING_PARAM = -2

        private fun getPresFromArgs(intent: Intent): Int {
            val pres = intent.getIntExtra(ProcessingParam.PRES.fullName,
                intent.getIntExtra(ProcessingParam.PRES.shortName, PRES_DEFAULT_VALUE_WHEN_MISSING_PARAM))
            if (pres == PRES_DEFAULT_VALUE_WHEN_MISSING_PARAM) {
                throw InvalidParameterException(
                    "Parameter ${ProcessingParam.PRES.fullName} is required"
                )
            }
            return pres
        }
    }

    fun toData(): Data = Data.Builder()
        .putString(ProcessingParam.INPUT.fullName, input)
        .putString(ProcessingParam.OUTPUT.fullName, output)
        .putInt(ProcessingParam.PRES.fullName, pres)
        .build()
}

fun Data.toProcessingParams() = ProcessingParams(
    input = getString(ProcessingParam.INPUT.fullName)!!,
    output = getString(ProcessingParam.OUTPUT.fullName)!!,
    pres = getInt(ProcessingParam.PRES.fullName, 0)
)

enum class ProcessingParam(
    val fullName: String,
    val shortName: String,
) {
    INPUT("input", "i"),
    OUTPUT("output", "o"),
    PRES("pres", "p")
}

