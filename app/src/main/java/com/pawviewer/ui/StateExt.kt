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
