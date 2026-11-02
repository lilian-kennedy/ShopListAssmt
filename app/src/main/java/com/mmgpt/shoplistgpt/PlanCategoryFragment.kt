package com.mmgpt.shoplistgpt

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.mmgpt.shoplistgpt.adapter.ItemAdapter
import com.mmgpt.shoplistgpt.ShoppingItem

class PlanCategoryFragment : IndexedCategoryFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.chkSelectAll.visibility = View.VISIBLE
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
                viewModel.reorderItem(index, from - 1, to - 1)
                adapter.moveItem(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean = false
        })

        adapter = ItemAdapter(
            onAction = { item, action ->
                when (action) {
                    ItemAdapter.Action.CHECK -> viewModel.checkItem(item)
                    ItemAdapter.Action.EDIT -> showEditDialog(item)
                    ItemAdapter.Action.DELETE -> viewModel.deleteItem(item)
                    else -> {}
                }
            },
            mode = ItemAdapter.Mode.PLAN,
            dragStart = { vh -> dragHelper.startDrag(vh) },
            onAdd = { position, name ->
                viewModel.addItem(index, position - 1, name)
            }
        )
        binding.recyclerView.adapter = adapter
        dragHelper.attachToRecyclerView(binding.recyclerView)

        viewModel.categories.observe(viewLifecycleOwner) { list ->
            val cat = list.getOrNull(index)
            if (cat != null) {
                binding.tvHeading.text = cat.heading
                binding.tvCount.text = ""

                val allChecked = cat.items.filterNot { it.isHeading }.all { it.isChecked }
                binding.chkSelectAll.setOnCheckedChangeListener(null)
                binding.chkSelectAll.isChecked = allChecked
                binding.chkSelectAll.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.setAllChecked(index, isChecked)
                }

                val items = mutableListOf<ShoppingItem>()
                items.add(ShoppingItem(-1, "", isPlaceholder = true))
                items.addAll(cat.items)
                items.add(ShoppingItem(-2, "", isPlaceholder = true))
                adapter.submitList(items)
            }
        }
    }

    private fun showEditDialog(item: ShoppingItem) {
        val input = android.widget.EditText(requireContext()).apply { setText(item.name) }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.edit))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    viewModel.editItem(item, text)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        fun newInstance(index: Int): PlanCategoryFragment = PlanCategoryFragment().withIndex(index)
    }
}
