package com.echofree.util

import com.echofree.data.models.Post
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the client-side FeedRanker.
 * Run with: ./gradlew test
 */
class FeedRankerTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun hoursAgo(h: Long) =
        System.currentTimeMillis() - h * 3_600_000

    private fun post(
        id: String,
        tags: List<String> = emptyList(),
        ageHours: Long = 1
    ) = Post(
        id        = id,
        content   = "Test content for $id",
        tags      = tags,
        createdAt = hoursAgo(ageHours)
    )

    // ── rank() ────────────────────────────────────────────────────────────────

    @Test
    fun `rank returns empty list for empty input`() {
        assertTrue(FeedRanker.rank(emptyList()).isEmpty())
    }

    @Test
    fun `rank assigns positive relevance scores`() {
        val posts  = listOf(post("a"), post("b"), post("c"))
        val ranked = FeedRanker.rank(posts)
        ranked.forEach { assertTrue("Score must be positive", it.relevanceScore > 0) }
    }

    @Test
    fun `rank sorts descending by relevance score`() {
        val posts  = listOf(post("a"), post("b"), post("c"))
        val ranked = FeedRanker.rank(posts)
        for (i in 0 until ranked.size - 1) {
            assertTrue(ranked[i].relevanceScore >= ranked[i + 1].relevanceScore)
        }
    }

    @Test
    fun `fresh post beats very old post most of the time`() {
        // Run 20 times — fresh post should win at least 15/20 given 70% weight
        var freshWins = 0
        repeat(20) {
            val fresh = post("fresh", ageHours = 0)
            val old   = post("old",   ageHours = 72)
            val ranked = FeedRanker.rank(listOf(old, fresh))
            if (ranked.first().id == "fresh") freshWins++
        }
        assertTrue("Fresh post should win majority: got $freshWins/20", freshWins >= 14)
    }

    @Test
    fun `explore mode produces different orderings`() {
        val posts = (1..10).map { post("p$it", ageHours = it.toLong()) }
        val results = (1..20).map {
            FeedRanker.rank(posts, exploreMode = true).map { p -> p.id }
        }
        val unique = results.toSet().size
        // With 20 random shuffles of 10 items we expect multiple unique orderings
        assertTrue("Explore mode should vary: got $unique unique orderings", unique > 3)
    }

    @Test
    fun `weights must sum to 1 - invalid config throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            FeedRanker.RankConfig(
                freshnessWeight = 0.5,
                diversityWeight = 0.5,
                noiseWeight     = 0.5   // sum = 1.5, invalid
            )
        }
    }

    // ── injectWildcards() ─────────────────────────────────────────────────────

    @Test
    fun `injectWildcards adds posts from the pool not in the feed`() {
        val feed = listOf(post("f1", tags = listOf("tech")))
        val pool = listOf(
            post("f1", tags = listOf("tech")),
            post("w1", tags = listOf("art")),
            post("w2", tags = listOf("music"))
        )
        val result = FeedRanker.injectWildcards(feed, pool, every = 1)
        val ids = result.map { it.id }
        assertTrue("Wildcard should be injected", ids.size > 1)
        assertFalse("Wildcards must not duplicate feed items", ids.count { it == "f1" } > 1)
    }

    @Test
    fun `injectWildcards handles empty inputs gracefully`() {
        assertEquals(emptyList<Post>(), FeedRanker.injectWildcards(emptyList(), emptyList()))
        val feed = listOf(post("x"))
        assertEquals(feed, FeedRanker.injectWildcards(feed, emptyList()))
    }

    @Test
    fun `wildcard injection preserves feed order between wildcards`() {
        val feed = (1..10).map { post("f$it", tags = listOf("tech")) }
        val pool = feed + listOf(post("wild", tags = listOf("art")))
        val result = FeedRanker.injectWildcards(feed, pool, every = 5)

        // Original feed posts should still be in order relative to each other
        val feedPosts = result.filter { it.id.startsWith("f") }
        val expectedOrder = feed.map { it.id }
        assertEquals(expectedOrder, feedPosts.map { it.id })
    }

    // ── diversity ─────────────────────────────────────────────────────────────

    @Test
    fun `heavily repeated tags receive lower diversity scores than new tags`() {
        // Posts 1-5 all tagged "tech"; post 6 tagged "art" — expect art post
        // to rank relatively higher due to diversity boost
        val techPosts = (1..5).map { post("t$it", tags = listOf("tech"), ageHours = 2) }
        val artPost   = post("art1", tags = listOf("art"), ageHours = 3)  // slightly older

        // Run 30 times to smooth out randomness
        var artRankSum = 0
        val runs = 30
        repeat(runs) {
            val ranked = FeedRanker.rank(techPosts + artPost)
            artRankSum += ranked.indexOfFirst { it.id == "art1" }
        }
        val avgRank = artRankSum.toDouble() / runs
        // Art post should not always end up last (index 5)
        assertTrue("Diversity should lift art post above dead last on average; avg=$avgRank", avgRank < 5.0)
    }
}
