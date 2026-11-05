package com.pawviewer.ui

import android.view.View
import com.pawviewer.databinding.ViewStateBinding

fun ViewStateBinding.hideState() {
    root.visibility = View.GONE
}

fun ViewStateBinding.showLoading(text: CharSequence = "") {
    root.visibility = View.VISIBLE
    stateProgress.visibility = View.VISIBLE
    stateText.text = text
    stateButton.visibility = View.GONE
}

fun ViewStateBinding.showEmpty(text: CharSequence) {
    root.visibility = View.VISIBLE
    stateProgress.visibility = View.GONE
    stateText.text = text
    stateText.setTextColor(androidx.core.content.ContextCompat.getColor(root.context, com.pawviewer.R.color.on_surface_variant))
    stateText.textSize = 14f
    stateButton.visibility = View.GONE
}

fun ViewStateBinding.showError(text: CharSequence, action: CharSequence, onAction: () -> Unit) {
    root.visibility = View.VISIBLE
    stateProgress.visibility = View.GONE
    stateText.text = text
    stateButton.visibility = View.VISIBLE
    stateButton.text = action
    stateButton.setOnClickListener { onAction() }
}
/** 彩蛋样式：主题色、稍大字号。 */
fun ViewStateBinding.showEmptyBrand(text: CharSequence) {
    root.visibility = View.VISIBLE
    stateProgress.visibility = View.GONE
    stateText.text = text
    stateText.setTextColor(androidx.core.content.ContextCompat.getColor(root.context, com.pawviewer.R.color.brand))
    stateText.textSize = 20f
    stateButton.visibility = View.GONE
}
