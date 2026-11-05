package com.pawviewer.ui.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pawviewer.R
import com.pawviewer.core.Util
import com.pawviewer.core.img.ImageLoader
import com.pawviewer.core.media.MediaResolver
import com.pawviewer.core.model.Post
import com.pawviewer.databinding.ItemPostBinding

class PostAdapter(
    private val onClick: (Post) -> Unit,
) : ListAdapter<Post, PostAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemPostBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(post: Post) {
            binding.title.text = post.displayTitle
            binding.meta.text = "${Util.serviceName(post.service)} · ${post.user}"

            binding.sub.text = buildString {
                val date = Util.formatDate(post.published ?: post.added)
                if (date.isNotBlank()) append(date.take(16))
                val count = post.attachmentsOrEmpty.size
                if (count > 0) {
                    if (isNotEmpty()) append(" · ")
                    append(count)
                    append(" 附件")
                }
            }

            val thumbPath = post.file?.path
            val width = Util.dp(binding.root.context, 88)
            val height = Util.dp(binding.root.context, 118)
            if (thumbPath.isNullOrBlank()) {
                binding.thumb.scaleType = ImageView.ScaleType.CENTER
                binding.thumb.setImageResource(R.drawable.ic_image_off)
                binding.thumb.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(binding.root.context, R.color.on_surface_variant)
                )
            } else {
                binding.thumb.scaleType = ImageView.ScaleType.CENTER_CROP
                binding.thumb.imageTintList = null
                ImageLoader.load(
                    url = MediaResolver.thumbUrl(thumbPath),
                    cacheKey = "thumb:${post.key}",
                    view = binding.thumb,
                    reqWidth = width,
                    reqHeight = height,
                )
            }

            binding.root.setOnClickListener { onClick(post) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean =
            oldItem.key == newItem.key

        override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean =
            oldItem.title == newItem.title &&
                oldItem.published == newItem.published &&
                oldItem.attachmentsOrEmpty.size == newItem.attachmentsOrEmpty.size
    }
}
