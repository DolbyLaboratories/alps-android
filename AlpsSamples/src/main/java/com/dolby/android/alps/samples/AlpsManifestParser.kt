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

package com.dolby.android.alps.samples


import android.util.Pair
import androidx.media3.common.C
import androidx.media3.common.Label
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.XmlPullParserUtil
import androidx.media3.exoplayer.dash.manifest.AdaptationSet
import androidx.media3.exoplayer.dash.manifest.BaseUrl
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.dash.manifest.Descriptor
import androidx.media3.exoplayer.dash.manifest.EventStream
import androidx.media3.exoplayer.dash.manifest.Period
import androidx.media3.exoplayer.dash.manifest.SegmentBase
import com.dolby.android.alps.samples.models.PeriodWithPreselections
import com.dolby.android.alps.samples.models.Preselection
import com.google.common.collect.ImmutableList
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException


/**

 * Extension of Media3's [DashManifestParser] that adds support for parsing DASH manifest
 * `<Preselection>` elements.
 *
 * This custom parser extends the standard DASH manifest parsing capabilities to handle
 * additional metadata that can be used for content preselection and enhanced audio
 * configuration. It enables applications to access preselection information before
 * playback begins, allowing for more informed track selection decisions.
 *
 * The parser specifically handles:
 * - Preselection elements with ID, language, and presentation tag attributes
 * - Audio channel configuration descriptors within preselections
 * - Localized labels for preselection options
 * - Localized group labels
 * - Essential and Supplemental properties
 * - Roles
 * - Selection proprity
 *
 * For detailed explanation of the purpose of each of those properties, please refer to
 * the ISO/IEC 23009-1:2022, sections 5.3.10, 5.3.7.2 and 5.3.11
 *
 * Instead of creating standard Media3 [Period] objects, this parser generates
 * [PeriodWithPreselections] objects that contain the additional preselection metadata
 * alongside the standard DASH period information.
 *
 * @see DashManifestParser
 * @see PeriodWithPreselections
 * @see Preselection
 */
@UnstableApi
class AlpsManifestParser : DashManifestParser() {

    override fun parsePeriod(
        xpp: XmlPullParser,
        parentBaseUrls: List<BaseUrl>,
        defaultStartMs: Long,
        baseUrlAvailabilityTimeOffsetUs: Long,
        availabilityStartTimeMs: Long,
        timeShiftBufferDepthMs: Long,
        dvbProfileDeclared: Boolean
    ): Pair<Period, Long> {
        var baseUrlAvailabilityTimeOffsetUs = baseUrlAvailabilityTimeOffsetUs
        val id = xpp.getAttributeValue(null, "id")
        val startMs = parseDuration(xpp, "start", defaultStartMs)
        val periodStartUnixTimeMs =
            if (availabilityStartTimeMs != C.TIME_UNSET) availabilityStartTimeMs + startMs else C.TIME_UNSET
        val durationMs = parseDuration(xpp, "duration", C.TIME_UNSET)
        var segmentBase: SegmentBase? = null
        var assetIdentifier: Descriptor? = null
        val adaptationSets: MutableList<AdaptationSet> = java.util.ArrayList()
        val eventStreams: MutableList<EventStream> = java.util.ArrayList()
        val preselections: MutableList<Preselection> = java.util.ArrayList()
        val baseUrls = java.util.ArrayList<BaseUrl>()
        var seenFirstBaseUrl = false
        var segmentBaseAvailabilityTimeOffsetUs = C.TIME_UNSET
        do {
            xpp.next()
            if (XmlPullParserUtil.isStartTag(xpp, "BaseURL")) {
                if (!seenFirstBaseUrl) {
                    baseUrlAvailabilityTimeOffsetUs =
                        parseAvailabilityTimeOffsetUs(xpp, baseUrlAvailabilityTimeOffsetUs)
                    seenFirstBaseUrl = true
                }
                baseUrls.addAll(parseBaseUrl(xpp, parentBaseUrls, dvbProfileDeclared))
            } else if (XmlPullParserUtil.isStartTag(xpp, "AdaptationSet")) {
                adaptationSets.add(
                    parseAdaptationSet(
                        xpp,
                        if (!baseUrls.isEmpty()) baseUrls else parentBaseUrls,
                        segmentBase,
                        durationMs,
                        baseUrlAvailabilityTimeOffsetUs,
                        segmentBaseAvailabilityTimeOffsetUs,
                        periodStartUnixTimeMs,
                        timeShiftBufferDepthMs,
                        dvbProfileDeclared
                    )
                )
            } else if (XmlPullParserUtil.isStartTag(xpp, "EventStream")) {
                eventStreams.add(parseEventStream(xpp))
            } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentBase")) {
                segmentBase = parseSegmentBase(xpp,  /* parent= */null)
            } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentList")) {
                segmentBaseAvailabilityTimeOffsetUs =
                    parseAvailabilityTimeOffsetUs(
                        xpp,  /* parentAvailabilityTimeOffsetUs= */
                        C.TIME_UNSET
                    )
                segmentBase =
                    parseSegmentList(
                        xpp,  /* parent= */
                        null,
                        periodStartUnixTimeMs,
                        durationMs,
                        baseUrlAvailabilityTimeOffsetUs,
                        segmentBaseAvailabilityTimeOffsetUs,
                        timeShiftBufferDepthMs
                    )
            } else if (XmlPullParserUtil.isStartTag(xpp, "SegmentTemplate")) {
                segmentBaseAvailabilityTimeOffsetUs =
                    parseAvailabilityTimeOffsetUs(
                        xpp,  /* parentAvailabilityTimeOffsetUs= */
                        C.TIME_UNSET
                    )
                segmentBase =
                    parseSegmentTemplate(
                        xpp,  /* parent= */
                        null,
                        ImmutableList.of(),
                        periodStartUnixTimeMs,
                        durationMs,
                        baseUrlAvailabilityTimeOffsetUs,
                        segmentBaseAvailabilityTimeOffsetUs,
                        timeShiftBufferDepthMs
                    )
            } else if (XmlPullParserUtil.isStartTag(xpp, "AssetIdentifier")) {
                assetIdentifier = parseDescriptor(xpp, "AssetIdentifier")
            } else if (XmlPullParserUtil.isStartTag(xpp, "Preselection")) {
                preselections.add(parsePreselection(xpp))
            } else {
                maybeSkipTag(xpp)
            }
        } while (!XmlPullParserUtil.isEndTag(xpp, "Period"))

        return Pair.create(
            buildPeriod(id, startMs, adaptationSets, eventStreams, assetIdentifier, preselections),
            durationMs
        )
    }

    private fun parseGroupLabel(xpp: XmlPullParser): Label {
        val lang = xpp.getAttributeValue(null, "lang")
        val value = parseText(xpp, "GroupLabel")
        return Label(lang, value)
    }

    private fun parsePreselection(xpp: XmlPullParser): Preselection {
        val id: String = parseString(xpp, "id", "1")

        val lang: String? = runCatching {
            xpp.getAttributeValue(null, "tag")
        }.getOrNull()

        val presentationTag: Int? = runCatching {
            Integer.parseInt(xpp.getAttributeValue(null, "tag"))
        }.getOrNull()

        val selectionPriority: Int = parseInt(xpp, "selectionPriority", 1)

        val audioChannelConfiguration = ArrayList<Descriptor>()
        val essentialProperties = ArrayList<Descriptor>()
        val supplementalProperties = ArrayList<Descriptor>()
        val roles = ArrayList<Descriptor>()

        val labels = ArrayList<Label>()
        val groupLabels = ArrayList<Label>()

        do {
            xpp.next()
            if (XmlPullParserUtil.isStartTag(xpp)) {
                when (xpp.name) {
                    "Label" ->
                        labels.add(parseLabel(xpp))
                    "GroupLabel" ->
                        groupLabels.add(parseGroupLabel(xpp))
                    "AudioChannelConfiguration" ->
                        audioChannelConfiguration.add( parseDescriptor(xpp, "AudioChannelConfiguration"))
                    "EssentialProperty" ->
                        essentialProperties.add( parseDescriptor(xpp, "EssentialProperty" ))
                    "SupplementalProperty" ->
                        supplementalProperties.add( parseDescriptor(xpp, "SupplementalProperty"))
                    "Role" ->
                        roles.add( parseDescriptor(xpp, "Role" ))
                    else -> maybeSkipTag(xpp)
                }
            }
        } while (!XmlPullParserUtil.isEndTag(xpp, "Preselection"))
        return Preselection(
            id = id,
            labels = labels,
            lang = lang,
            tag = presentationTag,
            audioChannelConfiguration = audioChannelConfiguration,
            groupLabels = groupLabels,
            essentialProperties = essentialProperties,
            supplementalProperties = supplementalProperties,
            roles = roles,
            selectionPriority = selectionPriority,
        )
    }

    private fun buildPeriod(
        id: String?,
        startMs: Long,
        adaptationSets: List<AdaptationSet>,
        eventStreams: List<EventStream>,
        assetIdentifier: Descriptor?,
        preselections: List<Preselection>
    ): Period {
        return PeriodWithPreselections(
            id,
            startMs,
            adaptationSets,
            eventStreams,
            assetIdentifier,
            preselections
        )
    }
}
