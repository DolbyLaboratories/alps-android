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

package com.dolby.android.alps.samples

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.each
import assertk.assertions.extracting
import assertk.assertions.hasSize
import assertk.assertions.isIn
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.dolby.android.alps.Alps
import com.dolby.android.alps.models.Label
import com.dolby.android.alps.models.Presentation
import com.dolby.android.alps.samples.fakes.FakeAlpsNative
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [AlpsManager], no matter which implementation (DASH, HLS, etc.), must behave the same way.
 * Its basic job is the same, keep track of the currently playing stream (over DASH periods, HLS
 * interstitials etc.), and expose the presentations of that stream, the currently selected presentation,
 * and allow the selection to be changed. That is it, internal details of how the manager is implemented,
 * and how it knows about streams, are irrelevant to the user of the library.
 *
 * The tests here cover the public contract of [AlpsManager], and run for all of its implementations.
 */
abstract class AlpsManagerTest {
    companion object {
        /** Id of the presentation selected by the tests. Must exist in [PRESENTATIONS]. */
        const val SELECTED_PRESENTATION_ID = 2

        /** Id that no presentation of any stream used by the tests has. */
        const val UNKNOWN_PRESENTATION_ID = 99

        /** Index of a stream that is never passed to `prepareStream`. */
        const val UNPREPARED_STREAM_INDEX = 7

        val PRESENTATIONS = listOf(
            presentation(id = 0, language = "eng", label = "English"),
            presentation(id = 1, language = "deu", label = "Deutsch"),
            presentation(id = SELECTED_PRESENTATION_ID, language = "eng", label = "Commentary"),
        )

        val UPDATED_PRESENTATIONS = listOf(
            presentation(id = 7, language = "fra", label = "Francais"),
        )

        private fun presentation(id: Int, language: String, label: String) = Presentation(
            id = id,
            extendedLanguage = language,
            kinds = emptyList(),
            labels = listOf(
                Label(labelId = id, language = language, label = label, isGroupLabel = false)
            ),
            selectionPriority = 0,
            audioRenderingIndication = 0,
            dialogGain = 0f,
        )
    }
    protected val createdNatives = mutableListOf<FakeAlpsNative>()

    protected lateinit var manager: AlpsManager

    /**
     * Creates the implementation under test. The implementation must be wired so that every [Alps]
     * instance it creates comes from [createAlps].
     */
    protected abstract fun createManager(
        presentationSelectionPersistenceEnabled: Boolean,
    ): AlpsManager

    /**
     * Declares that a stream with a given [index] exists, and that the manager should create an [Alps] instance for it.
     * This would normally be done by the manager itself by parsing a DASH manifest or HLS playlist,
     * but that logic is not the subject of testing on the [AlpsManager] API level.
     */
    protected abstract fun prepareStream(index: Int)

    /** Makes the already prepared stream with the given [index] the currently playing one. */
    protected abstract fun playStream(index: Int)

    /** Creates an [Alps] object backed by a [FakeAlpsNative]. */
    protected fun createAlps(): Alps =
        FakeAlpsNative(PRESENTATIONS).let { native ->
            createdNatives += native
            Alps(native)
        }

    /** Replaces [manager] with an instance that does not persist selection across streams. */
    private fun recreateManagerWithoutPersistence() {
        manager.release()
        createdNatives.clear()
        manager = createManager(presentationSelectionPersistenceEnabled = false)
    }

    @BeforeEach
    fun setUp() {
        createdNatives.clear()
        manager = createManager(presentationSelectionPersistenceEnabled = true)
    }

    @AfterEach
    fun tearDown() {
        manager.release()
        createdNatives.clear()
    }

    // Positive / happy path

    @Test
    fun `TV_DEFAULT_PRESENTATION has id -1`() {
        // We rely on this contract in the code, so let's make sure it doesn't change
        assertEquals(-1, AlpsManager.TV_DEFAULT_PRESENTATION.id)
    }

    @Test
    fun `getActivePresentationId returns null before playback started`() {
        assertThat(manager.getActivePresentationId()).isNull()
    }

    @Test
    fun `isobmffPresentations is empty before playback started`() {
        assertThat(manager.isobmffPresentations.value).isEmpty()
    }

    @Test
    fun `setActivePresentationId does not throw before playback started`() {
        assertDoesNotThrow { manager.setActivePresentationId(SELECTED_PRESENTATION_ID) }
    }

    @Test
    fun `setActivePresentationId does not throw when setting TV_DEFAULT`() {
        prepareStream(0)
        playStream(0)

        assertDoesNotThrow {
            manager.setActivePresentationId(AlpsManager.TV_DEFAULT_PRESENTATION.id)
        }
        assertThat(manager.getActivePresentationId())
            .isEqualTo(AlpsManager.TV_DEFAULT_PRESENTATION.id)
    }

    @Test
    fun `getActivePresentationId returns the id set with setActivePresentationId`() {
        prepareStream(0)
        playStream(0)

        manager.setActivePresentationId(SELECTED_PRESENTATION_ID)

        assertThat(manager.getActivePresentationId()).isEqualTo(SELECTED_PRESENTATION_ID)
        assertThat(createdNatives[0].activeId).isEqualTo(SELECTED_PRESENTATION_ID)
    }

    @Test
    fun `isobmffPresentations exposes presentations of the playing stream`() {
        prepareStream(0)
        playStream(0)

        assertThat(manager.isobmffPresentations.value)
            .extracting { it.id }
            .containsExactly(*PRESENTATIONS.map { it.id }.toTypedArray())
    }

    @Test
    fun `isobmffPresentations marks the selected presentation as active`() {
        prepareStream(0)
        playStream(0)

        manager.setActivePresentationId(SELECTED_PRESENTATION_ID)

        val presentations = manager.isobmffPresentations.value
        assertThat(presentations.single { it.isActive }.id).isEqualTo(SELECTED_PRESENTATION_ID)
        presentations.filter { it.id != SELECTED_PRESENTATION_ID }.forEach {
            assertThat(it.isActive).isFalse()
        }
    }

    @Test
    fun `isobmffPresentations follows the presentations changed callback`() {
        prepareStream(0)
        playStream(0)

        createdNatives[0].signalPresentationsChanged(UPDATED_PRESENTATIONS)

        assertThat(manager.isobmffPresentations.value)
            .extracting { it.id }
            .containsExactly(*UPDATED_PRESENTATIONS.map { it.id }.toTypedArray())
    }

    @Test
    fun `setActivePresentationId applies selection to all known streams when persistence enabled`() {
        prepareStream(0)
        prepareStream(1)
        playStream(0)

        manager.setActivePresentationId(SELECTED_PRESENTATION_ID)

        assertThat(createdNatives[0].activeId).isEqualTo(SELECTED_PRESENTATION_ID)
        assertThat(createdNatives[1].activeId).isEqualTo(SELECTED_PRESENTATION_ID)
    }

    @Test
    fun `setActivePresentationId applies selection only to playing stream when persistence disabled`() {
        recreateManagerWithoutPersistence()
        prepareStream(0)
        prepareStream(1)
        playStream(0)

        manager.setActivePresentationId(SELECTED_PRESENTATION_ID)

        assertThat(createdNatives[0].activeId).isEqualTo(SELECTED_PRESENTATION_ID)
        assertThat(createdNatives[1].activeId)
            .isEqualTo(FakeAlpsNative.NO_ACTIVE_PRESENTATION_ID)
    }

    @Test
    fun `release releases all Alps instances`() {
        prepareStream(0)
        prepareStream(1)
        playStream(0)

        manager.release()

        assertThat(createdNatives).each { it.transform { native -> native.isReleased }.isTrue() }
    }

    @Test
    fun `manager has no active presentation after release`() {
        prepareStream(0)
        playStream(0)
        manager.setActivePresentationId(SELECTED_PRESENTATION_ID)

        manager.release()

        assertThat(manager.getActivePresentationId()).isNull()
    }

    // Negative / edge cases

    /** The manager must not throw, in case the application wants to do its own
     * external signaling of presentations
     */
    @Test
    fun `setActivePresentationId does not throw for an unknown presentation id`() {
        prepareStream(0)
        playStream(0)

        assertDoesNotThrow { manager.setActivePresentationId(UNKNOWN_PRESENTATION_ID) }
    }

    @Test
    fun `getActivePresentationId returns the id set with setActivePresentationId even when it does not exist in the stream`() {
        prepareStream(0)
        playStream(0)

        manager.setActivePresentationId(UNKNOWN_PRESENTATION_ID)

        assertThat(manager.getActivePresentationId()).isEqualTo(UNKNOWN_PRESENTATION_ID)
        assertThat(createdNatives[0].activeId).isEqualTo(UNKNOWN_PRESENTATION_ID)
    }

    @Test
    fun `setActivePresentationId does not throw for a negative presentation id`() {
        prepareStream(0)
        playStream(0)

        assertDoesNotThrow { manager.setActivePresentationId(Int.MIN_VALUE) }
    }

    @Test
    fun `an unknown presentation id leaves no presentation marked as active`() {
        prepareStream(0)
        playStream(0)

        manager.setActivePresentationId(UNKNOWN_PRESENTATION_ID)

        manager.isobmffPresentations.value.forEach { assertThat(it.isActive).isFalse() }
    }


    @Test
    fun `selecting TV_DEFAULT clears the previously active presentation`() {
        prepareStream(0)
        playStream(0)
        manager.setActivePresentationId(SELECTED_PRESENTATION_ID)

        manager.setActivePresentationId(AlpsManager.TV_DEFAULT_PRESENTATION.id)

        manager.isobmffPresentations.value.forEach { assertThat(it.isActive).isFalse() }
    }

    @Test
    fun `selecting TV_DEFAULT clears the previously active presentation on all Alps instances`() {
        prepareStream(0)
        prepareStream(1)
        prepareStream(2)
        playStream(0)
        manager.setActivePresentationId(SELECTED_PRESENTATION_ID)

        manager.setActivePresentationId(AlpsManager.TV_DEFAULT_PRESENTATION.id)

        createdNatives.forEach {
            assertThat(it.getActivePresentationId()).isEqualTo(
                FakeAlpsNative.NO_ACTIVE_PRESENTATION_ID
            )
        }
    }

    @Test
    fun `switching to an unprepared stream does not throw`() {
        prepareStream(0)
        playStream(0)

        assertDoesNotThrow { playStream(UNPREPARED_STREAM_INDEX) }
    }

    @Test
    fun `an unprepared stream has no active presentation`() {
        prepareStream(0)
        playStream(0)
        manager.setActivePresentationId(SELECTED_PRESENTATION_ID)

        playStream(UNPREPARED_STREAM_INDEX)

        assertThat(manager.getActivePresentationId()).isNull()
    }

    @Test
    fun `an unprepared stream exposes no presentations`() {
        prepareStream(0)
        playStream(0)

        playStream(UNPREPARED_STREAM_INDEX)

        // The presentations of the previous stream say nothing about the one playing now.
        assertThat(manager.isobmffPresentations.value).isEmpty()
    }

    @Test
    fun `setActivePresentationId does not throw while an unprepared stream is playing`() {
        // This should never happen, but...
        playStream(UNPREPARED_STREAM_INDEX)

        assertDoesNotThrow { manager.setActivePresentationId(SELECTED_PRESENTATION_ID) }
    }

    @Test
    fun `preparing the same stream twice does not create a second Alps instance`() {
        prepareStream(0)
        prepareStream(0)
        playStream(0)

        assertThat(createdNatives).hasSize(1)
    }

    @Test
    fun `a stream prepared after the selection is also switched to the selected presentation`() {
        prepareStream(0)
        playStream(0)
        manager.setActivePresentationId(SELECTED_PRESENTATION_ID)

        // Persistence is enabled, so the selection must survive moving to a stream that the
        // manager did not even know about when the user made the selection.
        prepareStream(1)
        // Normally this would fire when the new stream init segment is processed, but [FakeAlpsNative] does not do that
        createdNatives[1].signalPresentationsChanged(PRESENTATIONS)
        playStream(1)


        assertThat(manager.getActivePresentationId()).isEqualTo(SELECTED_PRESENTATION_ID)
    }

    @Test
    fun `a stream prepared after the selection is switched to the selected presentation without presentations changed callback`() {
        prepareStream(0)
        playStream(0)
        manager.setActivePresentationId(SELECTED_PRESENTATION_ID)

        // Presentations signalled only in the manifest never fire the presentations changed
        // callback, so the selection must be applied when the new stream's Alps is created.
        prepareStream(1)
        playStream(1)

        assertThat(manager.getActivePresentationId()).isEqualTo(SELECTED_PRESENTATION_ID)
    }

    @Test
    fun `an empty presentations list from the callback clears isobmffPresentations`() {
        prepareStream(0)
        playStream(0)

        createdNatives[0].signalPresentationsChanged(emptyList())

        assertThat(manager.isobmffPresentations.value).isEmpty()
    }

    @Test
    fun `release can be called twice`() {
        prepareStream(0)
        playStream(0)

        manager.release()

        assertDoesNotThrow { manager.release() }
    }

    @Test
    fun `setActivePresentationId after release does not throw`() {
        prepareStream(0)
        playStream(0)
        manager.release()

        assertDoesNotThrow { manager.setActivePresentationId(SELECTED_PRESENTATION_ID) }
    }

    @Test
    fun `isobmffPresentations is empty after release`() {
        prepareStream(0)
        playStream(0)

        manager.release()

        // A released manager holds no streams any more, so it has nothing to show.
        assertThat(manager.isobmffPresentations.value).isEmpty()
    }
}
