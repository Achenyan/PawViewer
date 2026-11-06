package com.pawviewer.core

import com.pawviewer.core.model.Post

/** 帖子类型 / 时间筛选。 */
data class PostFilter(
    /** 0 = 全部，1 = 图包（不含视频），2 = 视频 */
    val type: Int = TYPE_ALL,
    /** 0 = 不限，1 = 某日期之前，2 = 某日期之后 */
    val dateMode: Int = DATE_ANY,
    /** 参考时间戳（毫秒），dateMode != 0 时有效 */
    val epoch: Long = 0L,
    /** 过滤掉标题/正文里带大写 AI 标记的作品（实验性）。 */
    val excludeAi: Boolean = false,
) {

    val isActive: Boolean get() = type != TYPE_ALL || dateMode != DATE_ANY || excludeAi

    fun matches(post: Post): Boolean {
        if (type != TYPE_ALL) {
            val video = Util.isVideoPost(post)
            if (type == TYPE_VIDEO && !video) return false
            if (type == TYPE_PACK && video) return false
        }
        if (excludeAi && Util.hasAiTag(post)) return false
        if (dateMode != DATE_ANY && epoch > 0) {
            val t = Util.epochOf(post.published) ?: Util.epochOf(post.added)
            if (t == null) return false
            if (dateMode == DATE_BEFORE && t >= epoch) return false
            if (dateMode == DATE_AFTER && t <= epoch) return false
        }
        return true
    }

    /**
     * 始终返回**新的**列表。
     *
     * 调用方（列表页）会把结果直接交给 ListAdapter，而 ListAdapter 内部是异步 diff；
     * 如果这里在「未启用筛选」时把原 ArrayList 原样返回，调用方之后 clear()/add()
     * 就会在 diff 过程中改动同一个实例，导致列表崩溃。
     */
    fun apply(list: List<Post>): List<Post> =
        if (!isActive) ArrayList(list) else list.filter { matches(it) }

    companion object {
        const val TYPE_ALL = 0
        const val TYPE_PACK = 1
        const val TYPE_VIDEO = 2

        const val DATE_ANY = 0
        const val DATE_BEFORE = 1
        const val DATE_AFTER = 2
    }
}
