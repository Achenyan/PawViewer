package com.pawviewer.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pawviewer.core.Util
import com.pawviewer.core.model.Creator
import com.pawviewer.databinding.ItemCreatorBinding

class CreatorAdapter(
    private val onClick: (Creator) -> Unit,
) : ListAdapter<Creator, CreatorAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemCreatorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemCreatorBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(creator: Creator) {
            val name = creator.displayName
            binding.name.text = name
            binding.initial.text = name.take(1).uppercase().ifBlank { "?" }
            binding.meta.text = buildString {
                append(Util.serviceName(creator.service))
                creator.favorited?.takeIf { it > 0 }?.let {
                    append(" · ")
                    append(it)
                    append(" 人收藏")
                }
            }
            binding.root.setOnClickListener { onClick(creator) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Creator>() {
        override fun areItemsTheSame(oldItem: Creator, newItem: Creator): Boolean =
            oldItem.key == newItem.key

        override fun areContentsTheSame(oldItem: Creator, newItem: Creator): Boolean =
            oldItem.name == newItem.name && oldItem.favorited == newItem.favorited
    }
}
