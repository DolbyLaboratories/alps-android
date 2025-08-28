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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.dolby.android.alps.app.MainActivity
import com.dolby.android.alps.app.R
import com.dolby.android.alps.app.data.models.Content
import com.dolby.android.alps.app.databinding.FragmentMainBinding
import com.dolby.android.alps.app.ui.base.BaseFragment
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainFragment : BaseFragment() {
    companion object {
        private const val NUMBER_OF_CONTENT_COLUMNS = 4
    }

    private val viewModel by viewModel<MainViewModel>()
    private var _binding: FragmentMainBinding? = null
    private val binding: FragmentMainBinding
        get() = _binding!!

    private var categoriesAdapter: CategoriesRecyclerViewAdapter? = null
    private var contentAdapter: ContentRecyclerViewAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.checkIntent(requireActivity().intent)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.apply {
            navigationState.subscribe()
        }

        setupUI()

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.categories.collect {
                    Napier.d("Collected categories $it")
                    categoriesAdapter?.updateCategories(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.filteredContent.collect {
                    Napier.d("Collected filtered content: $it")
                    contentAdapter?.updateContent(it)
                    updateHighlightedContent(null)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.highlightedContent.collect {
                    updateHighlightedContent(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errorEvent.collect {
                    handleErrorEvent(it)
                }
            }
        }
    }

    private fun setupUI() {
        val imageCornerInDp = binding.root.context.resources.getDimension(R.dimen.small_50)
        val density = binding.root.context.resources.displayMetrics.density
        val imageCornerInPx = Math.round(imageCornerInDp * density)

        categoriesAdapter = CategoriesRecyclerViewAdapter(
            mutableListOf(),
            lifecycleScope,
            viewModel::onCategoryClicked)

        contentAdapter = ContentRecyclerViewAdapter(
            mutableListOf(),
            imageCornerInPx,
            viewModel::onContentFocused
        ) { content ->
            viewModel.onContentClicked(
                content,
                requireContext()
            ) { intent ->
                activity?.startActivityForResult(
                    intent,
                    MainActivity.REQUEST_PLAY_CONTENT
                )
            }
        }

        binding.apply {
            categoriesRecycler.apply {
                layoutManager =
                    LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                adapter = categoriesAdapter
            }
            contentRecycler.apply {
                layoutManager =
                    GridLayoutManager(requireContext(), NUMBER_OF_CONTENT_COLUMNS)
                adapter = contentAdapter
            }

            settingsButton.setOnClickListener {
                viewModel.onSettingsButtonClicked()
            }
        }
    }

    private fun updateHighlightedContent(content: Content?) {
        val contentToHighlight =
            if (content == null || viewModel.filteredContent.value.any { it == content }.not()) {
                viewModel.filteredContent.value.getOrNull(0)
            } else content

        contentToHighlight?.let {
            binding.apply {
                title.text = contentToHighlight.title
                description.text = contentToHighlight.description
                poster.updatePoster(contentToHighlight.poster)
            }
        }
    }

    private fun handleErrorEvent(event: MainViewModel.ErrorEvent) {
        val message = when(event) {
            MainViewModel.ErrorEvent.ContentRefreshFailed -> getString(R.string.content_refresh_failed_message)
            MainViewModel.ErrorEvent.MissingManifestPath -> getString(R.string.missing_manifest_path_message)
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}