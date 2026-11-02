package com.mmgpt.shoplistgpt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.mmgpt.shoplistgpt.databinding.FragmentCategoryBinding

/** Shared view-binding, ViewModel and list setup for every page backed by fragment_category. */
abstract class BaseCategoryFragment : Fragment() {

    protected val viewModel: ShoppingListViewModel by activityViewModels()
    private var _binding: FragmentCategoryBinding? = null
    protected val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCategoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private const val ARG_INDEX = "index"

/** A [BaseCategoryFragment] for one category page; create instances with [withIndex]. */
abstract class IndexedCategoryFragment : BaseCategoryFragment() {

    protected var index: Int = 0
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        index = arguments?.getInt(ARG_INDEX) ?: 0
    }
}

fun <F : IndexedCategoryFragment> F.withIndex(index: Int): F =
    apply { arguments = bundleOf(ARG_INDEX to index) }
