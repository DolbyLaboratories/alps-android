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

package com.dolby.android.alps.app.ui.main

import android.content.Context
import android.content.Intent
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.dolby.android.alps.app.MainActivity.Companion.SETTINGS_URL_EXTRA_KEY
import com.dolby.android.alps.app.PlayerActivity
import com.dolby.android.alps.app.data.models.Category
import com.dolby.android.alps.app.data.models.Content
import com.dolby.android.alps.app.data.repository.UserDataRepository
import com.dolby.android.alps.app.ui.base.BaseViewModel
import com.dolby.android.alps.app.utils.DrmUtil
import com.dolby.android.alps.app.utils.IntentUtil
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: UserDataRepository
) : BaseViewModel() {
    private val contentList = MutableStateFlow(emptyList<Content>())

    private val _categories = MutableStateFlow(emptyList<Category>())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _filteredContent = MutableStateFlow(emptyList<Content>())
    val filteredContent: StateFlow<List<Content>> = _filteredContent.asStateFlow()

    private val _highlightedContent = MutableStateFlow<Content?>(null)
    val highlightedContent: StateFlow<Content?> = _highlightedContent.asStateFlow()

    private val _errorEvent = MutableSharedFlow<ErrorEvent>()
    val errorEvent: SharedFlow<ErrorEvent> = _errorEvent.asSharedFlow()

    init {
        refreshContent()
    }

    fun checkIntent(intent: Intent) {
        intent.getStringExtra(SETTINGS_URL_EXTRA_KEY)?.let {
            viewModelScope.launch {
                repository.updateAppSettingsUrl(it)
                refreshContent()
            }
        }
    }

    fun onCategoryClicked(category: Category) {
        val updatedCategories = _categories.value.map {
            it.copy(
                isSelected = it.name == category.name
            )
        }
        _categories.update {
            updatedCategories
        }
        filterContent()
    }

    fun onContentFocused(content: Content) {
        _highlightedContent.update {
            content
        }
    }

    fun onContentClicked(
        content: Content,
        context: Context,
        startActivityCallback: (intent: Intent) -> Unit
    ) {
        if (content.src.isNotEmpty()) {
            val mediaItem = MediaItem.Builder()
                .setUri(content.src)
                .setMediaMetadata(MediaMetadata.Builder()
                    .setTitle(content.title)
                    .setDescription(content.description)
                    .build()
                )

            content.drmLicenceServerURL?.let { drmUri ->
                mediaItem.setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(DrmUtil.deduceDrmUuid(drmUri))
                        .setLicenseUri(drmUri)
                        .build()
                )
            }

            val intent = Intent(context, PlayerActivity::class.java)
            intent.putExtra(IntentUtil.PRESENTATION_CHANGE_STYLE_EXTRA, content.presentationChangeStyle?.toString())

            viewModelScope.launch {
                val isAlpsEnabled = repository.getIsAlpsEnabled().first()
                intent.putExtra(IntentUtil.IS_ALPS_ENABLED, isAlpsEnabled)

                IntentUtil.addMediaItemToIntent(mediaItem.build(), intent)

                startActivityCallback(intent)
            }
        } else {
            viewModelScope.launch {
                _errorEvent.emit(ErrorEvent.MissingManifestPath)
            }
        }
    }

    fun onSettingsButtonClicked() {
        navigateTo(MainFragmentDirections.actionMainFragmentToSettingsDialog())
    }

    private fun refreshContent() {
        Napier.d("Trying to refresh content")
        viewModelScope.launch {
            repository.getAppSettings().let {
                repository.getContentList(it.contentListUrl)?.let { list ->
                    contentList.update {
                        list
                    }
                    handleNewContent(list)
                } ?: _errorEvent.emit(ErrorEvent.ContentRefreshFailed)
            }
        }
    }

    private fun handleNewContent(contentList: List<Content>) {
        val categories = contentList.map {
            it.category.trimStart().trimEnd()
        }.toSet().mapIndexed { index, name ->
            Category(
                name,
                index == 0
            )
        }
        _categories.update {
            categories
        }
        filterContent()
    }

    private fun filterContent() {
        val activeCategoryName = categories.value.find { it.isSelected }?.name

        _filteredContent.update {
            if (activeCategoryName == null) {
                contentList.value
            } else {
                contentList.value.filter {
                    it.category == activeCategoryName
                }
            }
        }
    }

    sealed interface ErrorEvent {
        data object ContentRefreshFailed: ErrorEvent
        data object MissingManifestPath: ErrorEvent
    }
}