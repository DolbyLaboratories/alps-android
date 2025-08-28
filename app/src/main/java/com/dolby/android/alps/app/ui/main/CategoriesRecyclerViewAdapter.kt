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
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.recyclerview.widget.RecyclerView
import com.dolby.android.alps.app.R
import com.dolby.android.alps.app.data.models.Category
import com.dolby.android.alps.app.databinding.CategoryItemLayoutBinding
import com.dolby.android.alps.app.utils.replaceListItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class CategoriesRecyclerViewAdapter(
    private val categories: MutableList<Category>,
    private val externalScope: CoroutineScope,
    private val onCategoryClick: (Category) -> Unit
): RecyclerView.Adapter<CategoriesRecyclerViewAdapter.ViewHolder>() {
    private val _selectedCategoryFlow = MutableSharedFlow<Category>()
    private val selectedCategoryFlow = _selectedCategoryFlow.asSharedFlow()

    fun updateCategories(categories: List<Category>) {
        if (this.categories.size != categories.size) {
            this.categories.replaceListItems(categories)
            notifyDataSetChanged()
        } else {
            this.categories.replaceListItems(categories)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            CategoryItemLayoutBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount() = categories.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    private fun onCategorySelected(category: Category) {
        externalScope.launch {
            _selectedCategoryFlow.emit(category)
        }
        onCategoryClick(category)
    }

    inner class ViewHolder(
        private val binding: CategoryItemLayoutBinding,
    ): RecyclerView.ViewHolder(binding.root) {
        private lateinit var category: Category
        init {
            binding.text.setOnClickListener {
                onCategorySelected(category)
            }
            externalScope.launch {
                selectedCategoryFlow.collect {
                    binding.root.background = if (it == category) {
                        getDrawable(binding.root.context, R.drawable.category_selected)
                    } else {
                        null
                    }
                }
            }
        }

        fun bind(
            category: Category
        ) {
            this.category = category
            binding.apply {
                text.text = category.name
                root.background = if (category.isSelected) {
                    getDrawable(root.context, R.drawable.category_selected)
                } else {
                    null
                }
            }
        }
    }
}