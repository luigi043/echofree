package com.echofree.data.repository

import com.echofree.data.models.CreatePostRequest
import com.echofree.data.models.Post
import com.echofree.network.ApiService
import com.echofree.util.FeedRanker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PostRepository is the single source of truth for post data.
 *
 * It owns three responsibilities:
 *   1. Fetch posts from the API (with a simple in-memory cache)
 *   2. Delegate ranking to [FeedRanker]
 *   3. Queue posts created while offline and flush them on reconnect
 */
class PostRepository(private val api: ApiService) {

    // Simple in-memory cache — replace with Room for persistence
    private val cache = mutableListOf<Post>()
    private val offlineQueue = mutableListOf<CreatePostRequest>()

    // ── Fetching ──────────────────────────────────────────────────────────────

    /**
     * Returns a ranked feed. Always re-ranks on each call so that
     * randomness produces a fresh ordering every pull-to-refresh.
     */
    suspend fun getFeed(
        forceRefresh: Boolean = false,
        exploreMode: Boolean = false,
        config: FeedRanker.RankConfig = FeedRanker.RankConfig()
    ): Result<List<Post>> = withContext(Dispatchers.IO) {
        try {
            if (forceRefresh || cache.isEmpty()) {
                val response = api.getPosts(limit = 80)
                if (response.isSuccessful) {
                    val posts = response.body()?.posts ?: emptyList()
                    cache.clear()
                    cache.addAll(posts)
                } else {
                    // Return stale cache rather than an error when possible
                    if (cache.isEmpty()) {
                        return@withContext Result.failure(
                            Exception("Server error ${response.code()}")
                        )
                    }
                }
            }

            val ranked = FeedRanker.rank(cache, config, exploreMode)
            val withWildcards = FeedRanker.injectWildcards(ranked, cache, every = 6)
            Result.success(withWildcards)

        } catch (e: Exception) {
            if (cache.isNotEmpty()) {
                // Offline: serve stale cache, re-ranked with new random seed
                Result.success(FeedRanker.rank(cache, config, exploreMode))
            } else {
                Result.failure(e)
            }
        }
    }

    // ── Creating ──────────────────────────────────────────────────────────────

    suspend fun createPost(request: CreatePostRequest): Result<Post> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.createPost(request)
                if (response.isSuccessful) {
                    val post = response.body()!!
                    cache.add(0, post)   // optimistic: prepend to cache immediately
                    // Flush any queued posts now we know we're online
                    flushOfflineQueue()
                    Result.success(post)
                } else {
                    Result.failure(Exception("Create failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                // Offline: queue the post and return a synthetic local copy
                offlineQueue.add(request)
                val localPost = Post(
                    id = "local-${System.currentTimeMillis()}",
                    content = request.content,
                    tags = request.tags,
                    createdAt = System.currentTimeMillis(),
                    authorName = request.authorName
                )
                cache.add(0, localPost)
                Result.success(localPost)   // caller doesn't need to know it's queued
            }
        }

    private suspend fun flushOfflineQueue() {
        val queued = offlineQueue.toList()
        offlineQueue.clear()
        queued.forEach { req ->
            try { api.createPost(req) } catch (_: Exception) { offlineQueue.add(req) }
        }
    }

    fun clearCache() = cache.clear()
}
