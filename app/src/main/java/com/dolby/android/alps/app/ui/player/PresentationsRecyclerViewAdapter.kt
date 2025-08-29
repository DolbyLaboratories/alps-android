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

package com.dolby.android.alps.app.ui.player

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dolby.android.alps.app.R
import com.dolby.android.alps.app.databinding.PresentationItemLayoutBinding
import com.dolby.android.alps.samples.models.AlpsPresentationWrapper

class PresentationsRecyclerViewAdapter(
    private var presentations: List<AlpsPresentationWrapper>,
    private val onPresentationClick: (AlpsPresentationWrapper) -> Unit
): RecyclerView.Adapter<PresentationsRecyclerViewAdapter.ViewHolder>() {

    fun updatePresentations(presentations: List<AlpsPresentationWrapper>) {
        this.presentations = presentations
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            PresentationItemLayoutBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount() = presentations.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(
            presentation = presentations[position]
        )
    }

    inner class ViewHolder(
        private val binding: PresentationItemLayoutBinding,
    ): RecyclerView.ViewHolder(binding.root) {
        private lateinit var presentation: AlpsPresentationWrapper
        init {
            binding.root.apply {
                requestFocus()
                setOnClickListener {
                    onPresentationClick(presentation)
                }
            }
        }

        fun bind(
            presentation: AlpsPresentationWrapper
        ) {
            this.presentation = presentation
            binding.apply {
                label.text = presentation.label
                selectionIcon.setImageResource(
                    if (presentation.isActive) {
                        R.drawable.ic_selected
                    } else {
                        R.drawable.ic_selectable
                    }
                )
            }
        }
    }
}