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

package com.dolby.android.alps.utils

/**
 * ALPS core library custom exceptions
 */
sealed class AlpsException(
    override val message: String? = null,
): Exception() {
    /**
     * AlpsException.Undefined() is thrown in scenarios that should never happen.
     */
    class Undefined : AlpsException("ALPS: Undefined exception reason")

    class NotInitialized : AlpsException("ALPS object not initialized")
    /**
     * AlpsException.Native sealed class groups all Exceptions that represent ALPS Native library
     * error codes - see alps_ret enum.
     */
    sealed class Native(
        error: String,
    ): AlpsException(
        message = "AlpsNative error: $error"
    ) {
        class Undefined(): Native("Undefined")
        class InvalidArg(): Native("Invalid Arg")
        class BuffTooSmall(): Native("Buffer too small")
        class ParseFailed(): Native("Parsing failed")
        class NextSegment(): Native("Next segment")
        class NoMovieInfo(): Native("No Movie info")
        class PresIdNotFound(): Native("Pres ID not found")
    }

    /**
     * AlpsException.JNI() is thrown when something failed in JNI layer
     */
    class JNI(error: String?): AlpsException(
        message = "JNI error: $error"
    )
}