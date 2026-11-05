package com.pawviewer.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pawviewer.R
import com.pawviewer.core.Util
import com.pawviewer.core.db.DownloadRow
import com.pawviewer.core.db.DownloadState
import com.pawviewer.databinding.ItemDownloadBinding

class DownloadAdapter(
    private val onAction: (DownloadRow) -> Unit,
) : RecyclerView.Adapter<DownloadAdapter.Holder>() {

    private var items: List<DownloadRow> = emptyList()

    fun submit(list: List<DownloadRow>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(private val binding: ItemDownloadBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: DownloadRow) {
            binding.title.text = row.title.ifBlank { row.pid }
            binding.meta.text = "${Util.serviceName(row.service)} · ${row.uid}"

            val progress = if (row.total > 0) row.done * 100 / row.total else 0
            binding.progress.max = 100
            binding.progress.progress = progress
            binding.progress.visibility =
                if (row.state == DownloadState.RUNNING || row.state == DownloadState.WAITING) View.VISIBLE
                else View.GONE

            binding.stateText.text = when (row.state) {
                DownloadState.WAITING -> binding.root.context.getString(R.string.downs_state_waiting)
                DownloadState.RUNNING -> binding.root.context.getString(
                    R.string.downs_progress, row.done, row.total
                )

                DownloadState.DONE -> binding.root.context.getString(R.string.downs_state_done)
                DownloadState.FAILED -> binding.root.context.getString(R.string.downs_state_failed)
                else -> binding.root.context.getString(R.string.downs_state_canceled)
            }

            val finished = row.state == DownloadState.DONE
            binding.btnAction.visibility = if (finished) View.GONE else View.VISIBLE
            binding.btnAction.setText(
                if (row.state == DownloadState.FAILED) R.string.downs_retry else R.string.downs_cancel
            )
            binding.btnAction.setOnClickListener { onAction(row) }
        }
    }
}
