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
    private val onCancel: (DownloadRow) -> Unit,
    private val onResume: (DownloadRow) -> Unit,
    private val onOpen: (DownloadRow) -> Unit,
    private val hasFiles: (DownloadRow) -> Boolean,
    private val onToggleSelect: (DownloadRow) -> Unit,
) : RecyclerView.Adapter<DownloadAdapter.Holder>() {

    private var items: List<DownloadRow> = emptyList()

    var selectionMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var selectedIds: Set<Long> = emptySet()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

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
            val context = binding.root.context
            binding.title.text = row.title.ifBlank { row.pid }
            binding.meta.text = "${Util.serviceName(row.service)} · ${row.uid}"

            val progress = if (row.total > 0) row.done * 100 / row.total else 0
            binding.progress.max = 100
            binding.progress.progress = progress
            binding.progress.visibility =
                if (row.state == DownloadState.RUNNING || row.state == DownloadState.WAITING) View.VISIBLE
                else View.GONE

            binding.stateText.text = when (row.state) {
                DownloadState.WAITING -> context.getString(R.string.downs_state_waiting)
                DownloadState.RUNNING -> context.getString(R.string.downs_progress, row.done, row.total)
                DownloadState.DONE -> context.getString(R.string.downs_state_done)
                DownloadState.FAILED -> context.getString(R.string.downs_failed_hint)
                else -> context.getString(R.string.downs_state_paused)
            }

            // 选择模式
            binding.check.visibility = if (selectionMode) View.VISIBLE else View.GONE
            binding.check.isChecked = selectedIds.contains(row.id)
            binding.btnAction.visibility = if (selectionMode) View.GONE else View.VISIBLE

            if (!selectionMode) {
                binding.btnAction.setText(
                    when (row.state) {
                        DownloadState.DONE -> R.string.downloads_open
                        DownloadState.FAILED, DownloadState.CANCELED -> R.string.downloads_resume
                        else -> R.string.downs_cancel
                    }
                )
                binding.btnAction.setOnClickListener {
                    when (row.state) {
                        DownloadState.DONE -> onOpen(row)
                        DownloadState.FAILED, DownloadState.CANCELED -> onResume(row)
                        else -> onCancel(row)
                    }
                }
            }

            binding.root.setOnClickListener {
                if (selectionMode) {
                    onToggleSelect(row)
                } else {
                    when {
                        row.state == DownloadState.DONE -> onOpen(row)
                        hasFiles(row) -> onOpen(row)
                        row.state == DownloadState.FAILED || row.state == DownloadState.CANCELED -> onResume(row)
                    }
                }
            }
        }
    }
}
