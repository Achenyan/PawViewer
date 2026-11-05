package com.pawviewer.core.model

import com.google.gson.annotations.SerializedName

data class FileRef(
    @SerializedName("name") val name: String? = null,
    @SerializedName("path") val path: String? = null,
    @SerializedName("node") val node: Int? = null,
    @SerializedName("preview_only") val previewOnly: Boolean? = null,
)

data class Attachment(
    @SerializedName("name") val name: String? = null,
    @SerializedName("path") val path: String? = null,
    @SerializedName("deferred") val deferred: Boolean? = null,
)

data class Embed(
    @SerializedName("url") val url: String? = null,
    @SerializedName("subject") val subject: String? = null,
    @SerializedName("provider") val provider: String? = null,
    @SerializedName("description") val description: String? = null,
)

data class Post(
    @SerializedName("id") val id: String? = null,
    @SerializedName("user") val user: String? = null,
    @SerializedName("service") val service: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("substring") val substring: String? = null,
    @SerializedName("published") val published: String? = null,
    @SerializedName("added") val added: String? = null,
    @SerializedName("edited") val edited: String? = null,
    @SerializedName("file") val file: FileRef? = null,
    @SerializedName("attachments") val attachments: List<Attachment>? = null,
    @SerializedName("embed") val embed: Embed? = null,
    @SerializedName("has_full") val hasFull: Boolean? = null,
    @SerializedName("preview_state") val previewState: String? = null,
    @SerializedName("next") val next: String? = null,
    @SerializedName("prev") val prev: String? = null,
    // 站点有时返回 "{tag1}{tag2}" 这样的字符串，有时是数组，用 JsonElement 兜住
    @SerializedName("tags") val tags: com.google.gson.JsonElement? = null,
) {
    val key: String get() = "${service.orEmpty()}/${user.orEmpty()}/${id.orEmpty()}"
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: "(无标题)"
    val attachmentsOrEmpty: List<Attachment> get() = attachments ?: emptyList()
    val webUrl: String get() = "https://pawchive.pw/${service.orEmpty()}/post/${id.orEmpty()}"
    val previewOnly: Boolean get() = file?.previewOnly == true
}

data class Creator(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("public_id") val publicId: String? = null,
    @SerializedName("service") val service: String? = null,
    @SerializedName("indexed") val indexed: String? = null,
    @SerializedName("updated") val updated: String? = null,
    @SerializedName("favorited") val favorited: Long? = null,
    @SerializedName("ever_imported") val everImported: Boolean? = null,
) {
    val key: String get() = "${service.orEmpty()}/${id.orEmpty()}"
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: publicId.orEmpty()
}

data class Profile(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("public_id") val publicId: String? = null,
    @SerializedName("service") val service: String? = null,
    @SerializedName("indexed") val indexed: String? = null,
    @SerializedName("updated") val updated: String? = null,
    @SerializedName("favorited") val favorited: Long? = null,
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: publicId.orEmpty()
}

data class TagCount(
    @SerializedName("tag") val tag: String? = null,
    @SerializedName("post_count") val postCount: Long? = null,
)
