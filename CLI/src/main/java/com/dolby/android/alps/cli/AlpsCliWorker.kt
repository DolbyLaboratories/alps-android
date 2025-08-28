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

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.dolby.android.alps.Alps
import io.github.aakira.napier.Napier
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class AlpsCliWorker(
    private val context: Context,
    workerParams: WorkerParameters
): Worker(context, workerParams) {
    override fun doWork(): Result {
        try {
            val processingParams = inputData.toProcessingParams()

            val inputFilesDir = prepareInputFilesDir(processingParams.input)

            val outputFilesDir = prepareOutputFilesDir(processingParams.output)

            val inputFiles = inputFilesDir.listFiles()?.toList()
            if(inputFiles.isNullOrEmpty())
                throw Exception("No files found in input directory")

            val initFile = inputFiles.find { it.name.contains("init") }
                ?: throw Exception("Input directory must contain init file")

            val alps = Alps()

            processSegment(initFile, alps).also {
                saveSegmentToFile(it, File(outputFilesDir, initFile.name))
            }

            Napier.i("Detected presentations: ${alps.getPresentations()}")

            Napier.d("Setting active presentation ID to ${processingParams.pres}")
            alps.setActivePresentationId(processingParams.pres)

            inputFiles.filter { it.extension == "m4s" }.forEach { segmentFile ->
                processSegment(segmentFile, alps).also {
                    saveSegmentToFile(it, File(outputFilesDir, segmentFile.name))
                }
            }

            alps.close()
            Napier.i("ALPS CLI processing success. Output files saved in ${outputFilesDir.path}")
            return Result.success()
        } catch (e: Exception) {
            Napier.e("ALPS CLI Failed. Error: ${e.message}")
            return Result.failure()
        }
    }

    private fun processSegment(segment: File, alps: Alps): ByteArray {
        val segmentBytes = FileInputStream(segment).use {
            it.readBytes()
        }

        try {
            val directByteBuffer = ByteBuffer.allocateDirect(segmentBytes.size)
            directByteBuffer.put(segmentBytes)
            alps.processIsobmffSegment(directByteBuffer)
            directByteBuffer.position(0)
            directByteBuffer.get(segmentBytes)
        } catch (e: Exception) {
            Napier.e(e.message ?: "Exception without message")
            Napier.w("Exception thrown during ALPS segment processing. Unmodified segment will be provided.")
        }

        return segmentBytes
    }

    private fun saveSegmentToFile(segmentBytes: ByteArray, outputFile: File) {
        FileOutputStream(outputFile).use {
            it.write(segmentBytes)
        }
    }

    private fun prepareInputFilesDir(inputDir: String): File {
        val inputFilesDir = File(context.getExternalFilesDir(null), inputDir)
        if (inputFilesDir.exists().not() || inputFilesDir.isDirectory.not()) {
            throw Exception("Input dir doesn't exist")
        }
        return inputFilesDir
    }

    private fun prepareOutputFilesDir(outputDir: String): File {
        val outputFilesDir = File(context.getExternalFilesDir(null), outputDir)
        if (outputFilesDir.exists()) {
            if (outputFilesDir.deleteRecursively().not()) {
                throw Exception("Failed to clean output directory")
            }
        }
        if (outputFilesDir.mkdirs()) {
            Napier.d("Output directory created at: ${outputFilesDir.path}")
        } else {
            Napier.e("Failed to create output directory at ${outputFilesDir.path}")
            throw Exception("Failed to create output directory")
        }
        return outputFilesDir
    }
}