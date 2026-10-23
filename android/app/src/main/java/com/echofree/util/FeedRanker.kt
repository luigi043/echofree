package com.echofree.util

import com.echofree.data.models.Post
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * EchoFree Feed Ranking Algorithm
 * ================================
 */
object FeedRanker {

    data class RankConfig(
        val freshnessWeight: Double = 0.45,
        val diversityWeight: Double = 0.25,
        val noiseWeight: Double = 0.30,
        val halfLifeHours: Double = 6.0,
        val tagSaturationLimit: Int = 3
    ) {
        init {
            require(freshnessWeight + diversityWeight + noiseWeight in 0.99..1.01)
        }
    }

    fun rank(
        posts: List<Post>,
        config: RankConfig = RankConfig(),
        exploreMode: Boolean = false
    ): List<Post> {
        if (posts.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()

        // First pass: compute intermediate scores
        val intermediate = posts.map { post ->
            val freshness = if (exploreMode) 0.0 else freshnessScore(post.createdAt, now, config.halfLifeHours)
            val noise = Math.random()
            Triple(post, freshness, noise)
        }

        // Second pass: tag diversity penalty
        val tagCount = mutableMapOf<String, Int>()
        val ranked = intermediate.map { (post, freshness, noise) ->
            val diversityScore = if (exploreMode) {
                Math.random()
            } else {
                computeDiversityScore(post.tags, tagCount, config.tagSaturationLimit)
            }
            
            // Increment counts for this post's tags
            post.tags.forEach { tagCount[it] = (tagCount[it] ?: 0) + 1 }

            val combined = freshness * config.freshnessWeight +
                           diversityScore * config.diversityWeight +
                           noise * config.noiseWeight

            post.copy(relevanceScore = combined)
        }

        return ranked.sortedByDescending { it.relevanceScore }
    }

    private fun freshnessScore(createdAt: Long, now: Long, halfLifeHours: Double): Double {
        val ageMs = max(0L, now - createdAt).toDouble()
        val ageHours = ageMs / 3_600_000.0
        val lambda = ln(2.0) / halfLifeHours
        return exp(-lambda * ageHours)
    }

    private fun computeDiversityScore(
        tags: List<String>,
        seenTagCounts: Map<String, Int>,
        saturationLimit: Int
    ): Double {
        if (tags.isEmpty()) return 0.7

        val penalty = tags
            .map { tag -> seenTagCounts[tag] ?: 0 }
            .map { count -> if (count >= saturationLimit) 0.3 else 0.0 }
            .average()

        return max(0.1, 1.0 - penalty)
    }

    fun injectWildcards(feed: List<Post>, pool: List<Post>, every: Int = 5): List<Post> {
        if (feed.isEmpty() || pool.isEmpty()) return feed

        val feedIds = feed.map { it.id }.toSet()
        val feedTags = feed.flatMap { it.tags }.groupingBy { it }.eachCount()

        val wildcards = pool
            .filter { it.id !in feedIds }
            .sortedBy { post ->
                post.tags.minOfOrNull { tag -> feedTags[tag] ?: 0 } ?: 0
            }
            .take(feed.size / every + 1)
            .shuffled()
            .iterator()

        val result = mutableListOf<Post>()
        feed.forEachIndexed { i, post ->
            result.add(post)
            if ((i + 1) % every == 0 && wildcards.hasNext()) {
                result.add(wildcards.next())
            }
        }
        return result
    }
}
