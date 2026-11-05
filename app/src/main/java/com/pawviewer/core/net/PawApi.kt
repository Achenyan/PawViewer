package com.pawviewer.core.net

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.pawviewer.core.model.Creator
import com.pawviewer.core.model.Post
import com.pawviewer.core.model.Profile
import com.pawviewer.core.model.TagCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

object PawApi {

    private val gson = Gson()

    private inline fun <reified T> parse(json: String): T? =
        runCatching { gson.fromJson<T>(json, object : TypeToken<T>() {}.type) }
            .onFailure { android.util.Log.w("PawViewer", "parse ${T::class.java.simpleName} failed", it) }
            .getOrNull()

    // ------------------------------------------------------------- listing

    suspend fun recent(query: String, offset: Int): List<Post> {
        val params = LinkedHashMap<String, String>()
        params["o"] = offset.toString()
        if (query.isNotBlank()) params["q"] = query
        return parse<List<Post>>(Http.api("/posts", params)) ?: emptyList()
    }

    suspend fun creatorPosts(
        service: String,
        creatorId: String,
        query: String = "",
        tag: String = "",
        offset: Int = 0,
    ): List<Post> {
        val params = LinkedHashMap<String, String>()
        params["o"] = offset.toString()
        if (tag.isNotBlank()) params["tag"] = tag
        if (query.isNotBlank()) params["q"] = query
        return parse<List<Post>>(
            Http.api("/$service/user/$creatorId", params)
        ) ?: emptyList()
    }

    suspend fun post(service: String, creatorId: String, postId: String): Post? =
        parse<Post>(Http.api("/$service/user/$creatorId/post/$postId"))

    suspend fun profile(service: String, creatorId: String): Profile? =
        parse<Profile>(Http.api("/$service/user/$creatorId/profile"))

    suspend fun tags(service: String, creatorId: String): List<TagCount> =
        parse<List<TagCount>>(Http.api("/$service/user/$creatorId/tags")) ?: emptyList()

    // ------------------------------------------------------------ creators

    /**
     * /creators 一次性返回全站创作者（约 15 MB），用流式解析逐条吐出来，
     * 避免把整个数组读进内存。
     */
    suspend fun streamCreators(onEach: (Creator) -> Unit): Int = withContext(Dispatchers.IO) {
        var count = 0
        val bytes = Http.getBytes("${Http.API}/creators", Http.HOST, Lane.API)
        InputStreamReader(bytes.inputStream(), Charsets.UTF_8).use { reader ->
            JsonReader(reader).use { json ->
                json.beginArray()
                while (json.hasNext()) {
                    val creator = gson.fromJson<Creator>(json, Creator::class.java)
                    if (creator != null) {
                        onEach(creator)
                        count++
                    }
                }
                json.endArray()
            }
        }
        count
    }

    // ----------------------------------------------------------- favorites

    suspend fun favorites(type: String = "post"): List<Post> =
        parse<List<Post>>(Http.api("/account/favorites", mapOf("type" to type))) ?: emptyList()

    suspend fun favoritePost(post: Post, add: Boolean): Boolean {
        val path = "/favorites/post/${post.service}/${post.user}/${post.id}"
        return runCatching {
            if (add) Http.post(path) else Http.delete(path)
            true
        }.getOrDefault(false)
    }

    suspend fun favoriteCreator(service: String, creatorId: String, add: Boolean): Boolean {
        val path = "/favorites/creator/$service/$creatorId"
        return runCatching {
            if (add) Http.post(path) else Http.delete(path)
            true
        }.getOrDefault(false)
    }

    /** 用「取收藏列表」这个需要登录的接口来判定登录态，比猜 cookie 名可靠。 */
    suspend fun checkLogin(): Boolean = runCatching {
        Http.api("/account/favorites", mapOf("type" to "post"))
        true
    }.getOrDefault(false)

    /**
     * 原生表单登录：先取一次登录页，再 POST /account/login。
     * 全程走应用内 OkHttp（含代理），不经过系统 DNS，因此不会被劫持。
     */
    suspend fun login(username: String, password: String): Boolean {
        runCatching { Http.getText("${Http.HOST}/account/login?location=/", Http.HOST, Lane.API) }
        // 网络异常要往上抛，界面才能区分「网络失败」和「密码错误」
        Http.postForm(
            "${Http.HOST}/account/login",
            mapOf(
                "location" to "/",
                "username" to username,
                "password" to password,
            ),
        )
        return checkLogin()
    }

    // ---------------------------------------------------------------- html

    /** 帖子网页里才有真正的原图直链（带签名，约 6 小时过期）。 */
    suspend fun postPageHtml(service: String, postId: String): String =
        Http.getText("${Http.HOST}/$service/post/$postId", Http.HOST, Lane.API)

    /** 分享链接可能只有 post id，从帖子网页里反查创作者 id。 */
    suspend fun creatorIdFromPostPage(service: String, postId: String): String? = runCatching {
        val html = postPageHtml(service, postId)
        Regex("/$service/user/(\\d+)").find(html)?.groupValues?.get(1)
    }.getOrNull()
}
