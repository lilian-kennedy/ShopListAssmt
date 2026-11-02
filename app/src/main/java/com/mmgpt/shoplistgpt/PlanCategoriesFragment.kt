package com.mmgpt.shoplistgpt

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.mmgpt.shoplistgpt.adapter.ItemAdapter
import com.mmgpt.shoplistgpt.ShoppingItem

class PlanCategoriesFragment : BaseCategoryFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lateinit var adapter: ItemAdapter
        val dragHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                val itemFrom = adapter.currentList.getOrNull(from)
                val itemTo = adapter.currentList.getOrNull(to)
                if (itemFrom?.isPlaceholder == true || itemTo?.isPlaceholder == true) return false
                viewModel.reorderCategory(from - 1, to - 1)
                adapter.moveItem(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean = false
        })

        adapter = ItemAdapter(
            onAction = { item, action ->
                val index = adapter.currentList.indexOf(item) - 1
                when (action) {
                    ItemAdapter.Action.EDIT -> showEditDialog(index, item.name)
                    ItemAdapter.Action.DELETE -> viewModel.deleteCategory(index)
                    else -> {}
                }
            },
            mode = ItemAdapter.Mode.PLAN,
            dragStart = { vh -> dragHelper.startDrag(vh) },
            onAdd = { position, name ->
                viewModel.addCategory(position - 1, name)
            }
        )
        binding.recyclerView.adapter = adapter
        dragHelper.attachToRecyclerView(binding.recyclerView)

        binding.recyclerView.setOnRowDoubleTap { position ->
            if (adapter.currentList.getOrNull(position)?.isHeading == true) {
                // Row 0 is the add placeholder, so category i is row i + 1 and pager page i + 1.
                requireActivity().findViewById<ViewPager2>(R.id.viewPager).currentItem = position
            }
        }

        binding.btnClearAll.visibility = View.VISIBLE
        binding.btnClearAll.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.confirm_clear_all))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewModel.clearAllCategories()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        viewModel.categories.observe(viewLifecycleOwner) { list ->
            binding.tvHeading.text = getString(R.string.categories_heading)
            binding.tvCount.text = ""
            val items = mutableListOf<ShoppingItem>()
            items.add(ShoppingItem(-1, "", isPlaceholder = true))
            list.forEach { cat ->
                items.add(ShoppingItem(cat.id, cat.heading, isHeading = true))
            }
            items.add(ShoppingItem(-2, "", isPlaceholder = true))
            adapter.submitList(items)
        }
    }

    private fun showEditDialog(index: Int, current: String) {
        val input = android.widget.EditText(requireContext()).apply { setText(current) }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.edit))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    viewModel.editCategory(index, text)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

