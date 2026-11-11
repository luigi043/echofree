package com.echofree.data.repository

import com.echofree.data.models.CreatePostRequest
import com.echofree.data.models.Post
import com.echofree.network.ApiService
import com.echofree.util.FeedRanker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response

class PostRepository(private val api: ApiService) {
    private var cache: List<Post>? = null

    suspend fun getFeed(
        forceRefresh: Boolean = false,
        exploreMode: Boolean = false,
        config: FeedRanker.RankConfig = FeedRanker.RankConfig()
    ): Result<List<Post>> = attempt {
        val posts = cache.takeUnless { forceRefresh } ?: api.getPosts(limit = 80)
            .requireBody().posts.also { cache = it }
        withContext(Dispatchers.Default) {
            FeedRanker.rank(posts, config, exploreMode)
        }
    }

    suspend fun createPost(request: CreatePostRequest): Result<Post> = attempt {
        api.createPost(request).requireBody()
    }

    private fun <T> Response<T>.requireBody(): T {
        if (!isSuccessful) throw HttpException(this)
        return checkNotNull(body()) { "Missing response body" }
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}
