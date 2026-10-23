package com.echofree.network

import com.echofree.data.models.CreatePostRequest
import com.echofree.data.models.Post
import com.echofree.data.models.PostsResponse
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {

    /**
     * Fetch a page of posts.
     * The server returns posts roughly sorted by recency but NOT ranked —
     * final ranking happens client-side so each device sees a different order.
     *
     * @param limit   Page size (default 50 gives the ranker enough variety)
     * @param offset  Pagination offset
     * @param tags    Optional comma-separated tag filter for the Explore tab
     */
    @GET("posts")
    suspend fun getPosts(
        @Query("limit")  limit: Int    = 50,
        @Query("offset") offset: Int   = 0,
        @Query("tags")   tags: String? = null
    ): Response<PostsResponse>

    @POST("posts")
    suspend fun createPost(@Body request: CreatePostRequest): Response<Post>

    companion object {
        private const val BASE_URL = "http://10.0.2.2:3000/"  // Android emulator localhost

        fun create(): ApiService = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
