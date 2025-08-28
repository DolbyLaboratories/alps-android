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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Scale
import coil3.transform.RoundedCornersTransformation
import com.dolby.android.alps.app.data.models.Content
import com.dolby.android.alps.app.databinding.ContentItemLayoutBinding
import com.dolby.android.alps.app.utils.replaceListItems

class ContentRecyclerViewAdapter(
    private val content: MutableList<Content>,
    private val imageCornerInPx: Int,
    private val onContentFocused: (Content) -> Unit,
    private val onContentClick: (Content) -> Unit,
): RecyclerView.Adapter<ContentRecyclerViewAdapter.ViewHolder>() {

    fun updateContent(content: List<Content>) {
        if (this.content != content) {
            this.content.replaceListItems(content)
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ContentItemLayoutBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount() = content.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(content[position])
    }

    inner class ViewHolder(
        private val binding: ContentItemLayoutBinding,
    ): RecyclerView.ViewHolder(binding.root) {
        private lateinit var content: Content
        init {
            binding.thumbnail.apply {
                setOnClickListener {
                    onContentClick(content)
                }
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        onContentFocused(content)
                    }
                }
            }
        }

        fun bind(
            content: Content
        ) {
            this.content = content
            binding.apply {
                thumbnail.load(
                    data = content.thumbnail
                ) {
                    transformations(RoundedCornersTransformation(imageCornerInPx.toFloat()))
                    scale(Scale.FILL)
                    crossfade(true)
                }
            }
        }
    }
}