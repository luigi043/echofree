// src/services/RankingEngine.js
/**
 * Server-side ranking engine.
 */

const HALF_LIFE_HOURS  = 12;    // posts lose half their freshness after 12h
const LN2              = Math.LN2;
const TAG_CAP          = 4;     // max posts per tag before penalty
const DIVERSITY_PENALTY = 0.35; // score reduction per over-cap tag

function freshnessScore(createdAt, now = Date.now()) {
    const ageMs    = Math.max(0, now - new Date(createdAt).getTime());
    const ageHours = ageMs / 3_600_000;
    const lambda   = LN2 / HALF_LIFE_HOURS;
    return Math.exp(-lambda * ageHours);
}

function diversityScore(tags, tagCounts) {
    if (!tags || tags.length === 0) return 0.7;

    const penalty = tags
        .map(tag => (tagCounts[tag] || 0) >= TAG_CAP ? DIVERSITY_PENALTY : 0)
        .reduce((a, b) => a + b, 0) / tags.length;

    return Math.max(0.1, 1 - penalty);
}

function preRank(posts, { freshnessWeight = 0.6, diversityWeight = 0.4 } = {}) {
    const now      = Date.now();
    const tagCounts = Object.create(null);

    return posts
        .map(post => {
            const f = freshnessScore(post.createdAt, now);
            const d = diversityScore(post.tags, tagCounts);

            (post.tags || []).forEach(tag => {
                tagCounts[tag] = (tagCounts[tag] || 0) + 1;
            });

            return {
                ...post,
                _preScore: f * freshnessWeight + d * diversityWeight
            };
        })
        .sort((a, b) => b._preScore - a._preScore)
        .map(({ _preScore, ...rest }) => rest);
}

function diversifyPage(posts, pageSize, maxPerTag = 5) {
    const tagCounts = Object.create(null);
    const page      = [];

    for (const post of posts) {
        if (page.length >= pageSize) break;

        const tags = post.tags || [];
        const dominated = tags.some(
            tag => (tagCounts[tag] || 0) >= maxPerTag
        );

        if (!dominated || tags.length === 0) {
            page.push(post);
            tags.forEach(tag => { tagCounts[tag] = (tagCounts[tag] || 0) + 1; });
        }
    }

    if (page.length < pageSize) {
        const inPage = new Set(page.map(p => p.id));
        for (const post of posts) {
            if (page.length >= pageSize) break;
            if (!inPage.has(post.id)) page.push(post);
        }
    }

    return page;
}

module.exports = { preRank, diversifyPage, freshnessScore };
