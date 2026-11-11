package com.echofree.data.repository

import com.echofree.data.models.CreatePostRequest
import com.echofree.data.models.Post
import com.echofree.data.models.PostsResponse
import com.echofree.network.ApiService
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class PostRepositoryTest {
    private val draft = CreatePostRequest("A synthetic draft", listOf("writing"), "Tester")
    private val published = Post("1", draft.content, draft.tags, 1L, draft.authorName)

    @Test
    fun `publish only succeeds for a confirmed response with a post`() = runBlocking {
        val offline = PostRepository(api { throw IOException("Offline") })
        val rejected = PostRepository(api { Response.error(429, "".toResponseBody()) })
        val missing = PostRepository(api { Response.success(null) })
        val accepted = PostRepository(api { Response.success(published) })

        assertTrue(offline.createPost(draft).isFailure)
        assertTrue(rejected.createPost(draft).isFailure)
        assertTrue(missing.createPost(draft).isFailure)
        assertEquals(published, accepted.createPost(draft).getOrThrow())
    }

    @Test
    fun `cancellation propagates when publishing`() = runBlocking {
        val repository = PostRepository(api { throw CancellationException() })
        val failure = runCatching { repository.createPost(draft) }.exceptionOrNull()
        assertTrue(failure is CancellationException)
    }

    @Test
    fun `forced refresh reloads a previously empty feed`() = runBlocking {
        var requests = 0
        val service = object : ApiService {
            override suspend fun getPosts(limit: Int, offset: Int, tags: String?): Response<PostsResponse> {
                val posts = if (requests++ == 0) emptyList() else listOf(published)
                return Response.success(PostsResponse(posts, posts.size))
            }

            override suspend fun createPost(request: CreatePostRequest) = Response.success(published)
        }
        val repository = PostRepository(service)

        assertTrue(repository.getFeed().getOrThrow().isEmpty())
        assertTrue(repository.getFeed(exploreMode = true).getOrThrow().isEmpty())
        assertEquals(listOf(published.id), repository.getFeed(forceRefresh = true).getOrThrow().map { it.id })
        assertEquals(2, requests)
    }

    private fun api(publish: suspend () -> Response<Post>) = object : ApiService {
        override suspend fun getPosts(limit: Int, offset: Int, tags: String?) =
            Response.success(PostsResponse(emptyList(), 0))

        override suspend fun createPost(request: CreatePostRequest) = publish()
    }
}
