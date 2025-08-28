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

package com.dolby.android.alps.app.data.datasource.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dolby.android.alps.app.configuration.Configurations
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface LocalDataSource {
    fun getAppSettingsUrl(): Flow<String>
    suspend fun updateAppSettingsUrl(url: String?)
}

private val SETTINGS_URL = stringPreferencesKey("settings_url")

class PreferencesDataSource(
    private val context: Context,
    private val defaultConfiguration: Configurations,
): LocalDataSource {
    private val Context.localUserDataStore by preferencesDataStore(name = "userSettings")
    private val json = Json { ignoreUnknownKeys = true }

    private inline fun <reified T> getModelFlowFromJsonString(
        key: Preferences.Key<String>
    ): Flow<T?> {
        return context.localUserDataStore.data
            .map {
                it[key]?.let { jsonString ->
                    json.decodeFromString<T>(jsonString)
                }
            }
    }

    private suspend inline fun <reified T> saveModelAsJsonString(
        key: Preferences.Key<String>,
        model: T
    ) {
        context.localUserDataStore.edit {
            val json = json.encodeToString(model)
            Napier.d("Saving json: $json for model: $model")
            it[key] = json
        }
    }

    private suspend fun <T> removePreference(key: Preferences.Key<T>) {
        context.localUserDataStore.edit { it.remove(key) }
    }

    private suspend inline fun <reified T> saveOrRemoveIfNull(key: Preferences.Key<String>, model: T?) {
        model?.let {
            saveModelAsJsonString(key, it)
        } ?: removePreference(key)
    }

    override fun getAppSettingsUrl(): Flow<String> =
        getModelFlowFromJsonString<String>(SETTINGS_URL)
            .map { it ?: defaultConfiguration.settingsJsonUrl }

    override suspend fun updateAppSettingsUrl(url: String?) {
        saveOrRemoveIfNull(SETTINGS_URL, url)
    }

}