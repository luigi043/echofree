// src/__tests__/RankingEngine.test.js
const { preRank, diversifyPage, freshnessScore } = require('../services/RankingEngine');

// ── Helper ────────────────────────────────────────────────────────────────────
const hoursAgo = h => new Date(Date.now() - h * 3_600_000);

function makePost(overrides = {}) {
    return {
        id: Math.random().toString(36).slice(2),
        content: 'Test post',
        tags: [],
        authorName: 'Tester',
        createdAt: hoursAgo(1),
        toJSON() { return { ...this }; },
        ...overrides
    };
}

// ── freshnessScore ────────────────────────────────────────────────────────────
describe('freshnessScore', () => {
    test('brand new post scores close to 1.0', () => {
        const score = freshnessScore(new Date());
        expect(score).toBeCloseTo(1.0, 1);
    });

    test('post at half-life scores ~0.5', () => {
        const score = freshnessScore(hoursAgo(12));   // half-life = 12h
        expect(score).toBeCloseTo(0.5, 1);
        expect(score).toBeGreaterThan(0.45);
        expect(score).toBeLessThan(0.55);
    });

    test('old post (72h) scores very low', () => {
        const score = freshnessScore(hoursAgo(72));
        expect(score).toBeLessThan(0.05);
    });

    test('monotonically decreasing', () => {
        const scores = [0, 2, 6, 12, 24, 48].map(h => freshnessScore(hoursAgo(h)));
        for (let i = 1; i < scores.length; i++) {
            expect(scores[i]).toBeLessThan(scores[i - 1]);
        }
    });
});

// ── preRank ───────────────────────────────────────────────────────────────────
describe('preRank', () => {
    test('newer posts rank higher than identical old posts', () => {
        const old   = makePost({ tags: ['a'], createdAt: hoursAgo(48) });
        const fresh = makePost({ tags: ['a'], createdAt: hoursAgo(0.5) });
        const [first, second] = preRank([old, fresh]);
        expect(first.id).toBe(fresh.id);
        expect(second.id).toBe(old.id);
    });

    test('strips _preScore before returning', () => {
        const posts  = [makePost()];
        const ranked = preRank(posts);
        expect(ranked[0]._preScore).toBeUndefined();
    });

    test('handles empty array', () => {
        expect(preRank([])).toEqual([]);
    });
});

// ── diversifyPage ─────────────────────────────────────────────────────────────
describe('diversifyPage', () => {
    test('caps posts per tag when alternatives fill the page', () => {
        const posts = Array.from({ length: 20 }, (_, i) =>
            makePost({ id: String(i), tags: [i < 15 ? 'tech' : 'art'] })
        );
        const page = diversifyPage(posts, 10, 5);
        const techCount = page.filter(p => p.tags.includes('tech')).length;
        expect(page).toHaveLength(10);
        expect(techCount).toBe(5);
    });

    test('backfills to reach page size even if all tags saturated', () => {
        const posts = Array.from({ length: 15 }, (_, i) =>
            makePost({ id: String(i), tags: ['tech'] })
        );
        const page = diversifyPage(posts, 10, 3);
        expect(page.length).toBe(10);
    });

    test('no duplicates in page', () => {
        const posts = Array.from({ length: 30 }, (_, i) =>
            makePost({ id: String(i), tags: [i % 3 === 0 ? 'art' : 'tech'] })
        );
        const page = diversifyPage(posts, 15);
        const ids  = page.map(p => p.id);
        expect(new Set(ids).size).toBe(ids.length);
    });

    test('feed feels diverse across tags', () => {
        const posts = [
            ...Array.from({ length: 10 }, (_, i) => makePost({ id: `t${i}`, tags: ['tech'] })),
            ...Array.from({ length: 10 }, (_, i) => makePost({ id: `a${i}`, tags: ['art'] })),
            ...Array.from({ length: 10 }, (_, i) => makePost({ id: `s${i}`, tags: ['science'] }))
        ];
        const page = diversifyPage(posts, 15, 5);
        const uniqueTags = new Set(page.flatMap(p => p.tags));
        expect(uniqueTags.size).toBeGreaterThanOrEqual(2);
    });
});
