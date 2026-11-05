package com.pawviewer.core

import android.content.Context
import com.pawviewer.App
import com.pawviewer.core.db.Db
import com.pawviewer.core.db.Store
import com.pawviewer.core.model.Creator
import com.pawviewer.core.model.Post
import com.pawviewer.core.model.Profile
import com.pawviewer.core.model.TagCount
import com.pawviewer.core.net.PawApi

/**
 * 业务门面：所有「取数据」的动作都从这里走，UI 只跟它打交道。
 * 对应 EhViewer 里 EhClient 的位置，但这里是 suspend 函数而不是回调树。
 */
object Repo {

    fun init(context: Context) {
        Db.get(context)
    }

    suspend fun feed(query: String, offset: Int): List<Post> =
        PawApi.recent(query, offset)

    suspend fun creatorPosts(
        service: String,
        creatorId: String,
        query: String,
        tag: String,
        offset: Int,
    ): List<Post> = PawApi.creatorPosts(service, creatorId, query, tag, offset)

    suspend fun profile(service: String, creatorId: String): Profile? =
        PawApi.profile(service, creatorId)

    suspend fun tags(service: String, creatorId: String): List<TagCount> =
        PawApi.tags(service, creatorId)

    /** 列表接口给的帖子字段不全（没有正文、附件可能只有名字），进详情页时补一次。 */
    suspend fun detail(post: Post): Post {
        var creatorId = post.user.orEmpty()
        if (creatorId.isBlank()) {
            creatorId = PawApi.creatorIdFromPostPage(
                post.service.orEmpty(), post.id.orEmpty()
            ).orEmpty()
        }
        if (creatorId.isBlank()) return post
        val full = PawApi.post(
            post.service.orEmpty(),
            creatorId,
            post.id.orEmpty()
        )
        return full ?: post
    }

    suspend fun nextPost(post: Post, forward: Boolean): Post? {
        val id = (if (forward) post.next else post.prev) ?: return null
        return PawApi.post(post.service.orEmpty(), post.user.orEmpty(), id)
    }

    suspend fun favorites(): List<Post> = PawApi.favorites("post")

    suspend fun toggleFavorite(post: Post, favorite: Boolean): Boolean {
        val ok = PawApi.favoritePost(post, favorite)
        if (ok) Store.setLocalFavorite(App.ctx, post, favorite)
        return ok
    }

    suspend fun toggleCreatorFavorite(service: String, creatorId: String, favorite: Boolean): Boolean =
        PawApi.favoriteCreator(service, creatorId, favorite)

    suspend fun checkLogin(): Boolean {
        val ok = PawApi.checkLogin()
        Prefs.loggedIn = ok
        return ok
    }

    suspend fun login(username: String, password: String): Boolean {
        val ok = PawApi.login(username, password)
        Prefs.loggedIn = ok
        return ok
    }

    /** 全站创作者索引：流式拉取 + 分批入库。 */
    suspend fun buildCreatorIndex(onProgress: (Int) -> Unit): Int {
        val context = App.ctx
        Store.clearCreators(context)
        val batch = ArrayList<Creator>(1000)
        var total = 0
        val count = PawApi.streamCreators { creator ->
            batch.add(creator)
            total++
            if (batch.size >= 1000) {
                Store.insertCreators(context, batch)
                batch.clear()
                onProgress(total)
            }
        }
        if (batch.isNotEmpty()) Store.insertCreators(context, batch)
        onProgress(count)
        Prefs.creatorsIndexedAt = System.currentTimeMillis()
        return count
    }

    fun searchCreators(query: String, service: String?): List<Creator> =
        Store.searchCreators(App.ctx, query, service)

    fun creatorCount(): Int = Store.creatorCount(App.ctx)
}
