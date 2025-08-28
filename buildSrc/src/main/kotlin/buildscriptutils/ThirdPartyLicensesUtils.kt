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

package buildscriptutils

import buildscriptutils.Const.GENERATED_HTML_LICENSE_REPORT_NAME
import buildscriptutils.Const.GENERATED_JSON_LICENSE_REPORT_NAME
import buildscriptutils.Const.GENERATED_LICENSE_REPORTS_PATH
import buildscriptutils.Const.LICENSE_REPORT_FILE_NAME
import buildscriptutils.Const.REFERENCE_REPORT_SUB_DIR
import buildscriptutils.Const.THIRD_PARTY_SUB_DIR_NAME
import kotlinx.serialization.json.Json
import org.gradle.api.GradleException
import java.io.File

private object Const {
    const val GENERATED_LICENSE_REPORTS_PATH = "build/reports/licenses/"
    const val GENERATED_HTML_LICENSE_REPORT_NAME = "licenseDebugUnitTestReport.html"
    const val GENERATED_JSON_LICENSE_REPORT_NAME = "licenseDebugUnitTestReport.json"

    const val THIRD_PARTY_SUB_DIR_NAME = "thirdPartyLicenses"
    const val LICENSE_REPORT_FILE_NAME = "thirdPartyLicenses.html"

    const val REFERENCE_REPORT_SUB_DIR = "reference/"
}

fun copyThirdPartyLicensesReports(subprojectDir: String) {
    val sourceFile = File(subprojectDir, GENERATED_LICENSE_REPORTS_PATH + GENERATED_HTML_LICENSE_REPORT_NAME)
    val targetDir = File(subprojectDir, THIRD_PARTY_SUB_DIR_NAME)
    val renamedFile = File(targetDir, LICENSE_REPORT_FILE_NAME)

    if (sourceFile.exists()) {
        println("Copying and renaming file from $sourceFile to $renamedFile")
        targetDir.mkdirs()
        sourceFile.copyTo(renamedFile, overwrite = true)
    } else {
        throw GradleException("Missing licenses report $sourceFile")
    }
}

fun checkNewDependenciesOrLicensesInTheProject(subprojectDir: String) {
    val referenceLicensesJson = Json.parseToJsonElement(
        File(
            subprojectDir,
            "/$THIRD_PARTY_SUB_DIR_NAME/$REFERENCE_REPORT_SUB_DIR/$GENERATED_JSON_LICENSE_REPORT_NAME"
        ).readText()
    )

    val latestLicensesJson = Json.parseToJsonElement(
        File(
            subprojectDir,
            "/${GENERATED_LICENSE_REPORTS_PATH}$GENERATED_JSON_LICENSE_REPORT_NAME"
        ).readText()
    )

    if (referenceLicensesJson != latestLicensesJson) {
        throw GradleException("Licenses report differs from reference. Make sure new dependency or " +
                "license is allowed.")
    } else {
        println("Generated licenses report matches reference.")
    }
}