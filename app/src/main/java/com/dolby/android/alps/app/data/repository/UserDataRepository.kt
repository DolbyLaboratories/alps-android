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

package com.dolby.android.alps.app.data.repository

import com.dolby.android.alps.app.configuration.Configurations
import com.dolby.android.alps.app.data.datasource.local.LocalDataSource
import com.dolby.android.alps.app.data.datasource.network.NetworkDataSource
import com.dolby.android.alps.app.data.models.AppSettings
import com.dolby.android.alps.app.data.models.Content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface UserDataRepository {
    suspend fun getContentList(url: String): List<Content>?
    suspend fun getAppSettings(): AppSettings
    suspend fun getAppSettingsUrl(): Flow<String>
    suspend fun updateAppSettingsUrl(url: String?)
}

class UserDataRepositoryImpl(
    private val networkDataSource: NetworkDataSource,
    private val localDataSource: LocalDataSource,
    private val defaultConfiguration: Configurations,
): UserDataRepository {
    override suspend fun getAppSettings(): AppSettings {
        localDataSource.getAppSettingsUrl().first().apply {
            return networkDataSource.getAppSettings(this)
                ?: defaultConfiguration.appSettings
        }
    }

    override suspend fun getContentList(url: String): List<Content>? =
        networkDataSource.getContentList(url)

    override suspend fun getAppSettingsUrl(): Flow<String> =
        localDataSource.getAppSettingsUrl()


    override suspend fun updateAppSettingsUrl(url: String?) {
        localDataSource.updateAppSettingsUrl(url)
    }
}