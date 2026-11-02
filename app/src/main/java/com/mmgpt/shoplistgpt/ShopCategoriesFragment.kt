package com.mmgpt.shoplistgpt

import android.os.Bundle
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import com.mmgpt.shoplistgpt.adapter.CategorySummaryAdapter

class ShopCategoriesFragment : BaseCategoryFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = CategorySummaryAdapter()
        binding.recyclerView.adapter = adapter

        binding.recyclerView.setOnRowDoubleTap { position ->
            requireActivity().findViewById<ViewPager2>(R.id.viewPager).currentItem = position
        }

        viewModel.categories.observe(viewLifecycleOwner) { list ->
            binding.tvHeading.text = getString(R.string.summary_heading)
            binding.tvCount.text = ""
            adapter.submitList(list)
        }
    }
}
