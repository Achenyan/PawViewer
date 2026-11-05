package com.pawviewer.ui

import androidx.fragment.app.Fragment
import com.pawviewer.core.media.MediaItem
import com.pawviewer.core.model.Post

fun Fragment.host(): MainActivity = requireActivity() as MainActivity

fun Fragment.openCreator(service: String, creatorId: String, name: String) {
    host().pushDetail(CreatorFragment.newInstance(service, creatorId, name), name)
}

fun Fragment.openPost(post: Post) {
    host().pushDetail(PostFragment.newInstance(post), post.displayTitle)
}

fun Fragment.openReader(post: Post, items: List<MediaItem>, index: Int) {
    ReaderActivity.start(requireContext(), post, items, index)
}

fun Fragment.openAbout() {
    host().pushDetail(AboutFragment.newInstance(false), getString(com.pawviewer.R.string.about_title))
}
