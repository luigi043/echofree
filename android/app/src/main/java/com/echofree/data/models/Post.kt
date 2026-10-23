package com.echofree.data.models

import com.google.gson.annotations.SerializedName

/**
 * Core domain model for a post in EchoFree.
 * The 'id' matches the transformed '_id' from the backend.
 */
data class Post(
    val id: String,

    val content: String,

    /** Free-form tags the author attached */
    val tags: List<String> = emptyList(),

    /** Unix epoch milliseconds */
    val createdAt: Long,

    /** Author display name */
    val authorName: String = "Anonymous",

    /**
     * Computed client-side for ranking.
     */
    val relevanceScore: Double = 0.0
)

data class CreatePostRequest(
    val content: String,
    val tags: List<String>,
    val authorName: String
)

data class PostsResponse(
    val posts: List<Post>,
    val total: Int
)
