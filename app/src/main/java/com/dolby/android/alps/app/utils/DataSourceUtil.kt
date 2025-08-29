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

package com.dolby.android.alps.app.utils

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy

@UnstableApi
class DataSourceUtil {
    companion object {
        private const val DOWNLOAD_CONTENT_DIRECTORY = "downloads"

        private var dataSourceFactory: DataSource.Factory? = null
        private var httpDataSourceFactory: DataSource.Factory? = null
        private var downloadCache: Cache? = null
        private var downloadDirectory: File? = null
        private var databaseProvider: DatabaseProvider? = null

        fun getDataSourceFactory(context: Context): DataSource.Factory {
            val appContext = context.applicationContext
            if (dataSourceFactory == null) {
                val upstreamFactory = DefaultDataSource.Factory(
                    appContext,
                    getHttpDataSourceFactory()
                )
                dataSourceFactory =
                    buildReadOnlyCacheDataSource(
                        upstreamFactory,
                        getDownloadCache(appContext)
                    )
            }
            return dataSourceFactory!!
        }

        fun getHttpDataSourceFactory(): DataSource.Factory {
            if (httpDataSourceFactory == null) {
                val cookieManager = CookieManager()
                cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER)
                CookieHandler.setDefault(cookieManager)
                httpDataSourceFactory = DefaultHttpDataSource.Factory()
            }
            return httpDataSourceFactory!!
        }

        private fun buildReadOnlyCacheDataSource(
            upstreamFactory: DataSource.Factory, cache: Cache
        ): CacheDataSource.Factory {
            return CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setCacheWriteDataSinkFactory(null)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }

        private fun getDownloadCache(context: Context): Cache {
            if (downloadCache == null) {
                val downloadContentDirectory = File(
                        getDownloadDirectory(context),
                        DOWNLOAD_CONTENT_DIRECTORY
                    )
                downloadCache =
                    SimpleCache(
                        downloadContentDirectory,
                        NoOpCacheEvictor(),
                        getDatabaseProvider(context)
                    )
            }
            return downloadCache!!
        }

        private fun getDownloadDirectory(context: Context): File {
            if (downloadDirectory == null) {
                downloadDirectory = context.getExternalFilesDir(null)
                if (downloadDirectory == null) {
                    downloadDirectory = context.filesDir
                }
            }
            return downloadDirectory!!
        }

        private fun getDatabaseProvider(context: Context): DatabaseProvider {
            if (databaseProvider == null) {
                databaseProvider = StandaloneDatabaseProvider(context)
            }
            return databaseProvider!!
        }
    }
}