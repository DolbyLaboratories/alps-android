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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dolby.android.alps.app.R
import com.dolby.android.alps.app.databinding.PresentationsListDialogBinding
import com.dolby.android.alps.app.ui.base.BaseDialog
import com.dolby.android.alps.models.Presentation

class PresentationsListDialog(
    private val presentationsList: List<Presentation>,
    private var selectedPresentation: Presentation,
): BaseDialog() {
    companion object {
        var myProvider: PropertyProvider? = null

        fun getDefaultDialogProvider(action: ((Presentation) -> Unit)): PropertyProvider {
            return object : PropertyProvider {
                override val onPresentationSelected: ((Presentation) -> Unit)
                    get() = action
            }
        }
    }
    private var binding: PresentationsListDialogBinding? = null

    private var presentationsAdapter: PresentationsRecyclerViewAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = PresentationsListDialogBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onStart() {
        super.onStart()
        dimBehind()
    }

    override fun getTheme(): Int {
        return R.style.FullScreenDialog
    }

    fun updateSelectedPresentation(presentation: Presentation) {
        selectedPresentation = presentation
        presentationsAdapter?.updateSelectedPresentation(selectedPresentation)
    }

    override fun setupUI() {
        presentationsAdapter = PresentationsRecyclerViewAdapter(
            presentationsList,
            selectedPresentation,
            lifecycleScope
        ) { presentation ->
            myProvider?.onPresentationSelected?.let { it(presentation) }
        }

        binding?.presentationsRecycler?.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            adapter = presentationsAdapter
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    interface PropertyProvider {
        val onPresentationSelected: ((Presentation) -> Unit)
    }
}