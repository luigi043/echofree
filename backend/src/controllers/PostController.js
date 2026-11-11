// src/controllers/PostController.js
const Post    = require('../models/Post');
const { preRank, diversifyPage } = require('../services/RankingEngine');

// ── GET /posts ────────────────────────────────────────────────────────────────
async function getPosts(req, res) {
    try {
        const limit  = Math.max(1, Math.min(parseInt(req.query.limit, 10) || 50, 100));
        const offset = Math.max(parseInt(req.query.offset) || 0, 0);
        if (req.query.tags !== undefined && typeof req.query.tags !== "string") {
            return res.status(400).json({ error: "Tags must be a comma-separated string" });
        }
        const tags   = req.query.tags
            ? req.query.tags.split(',').map(t => t.trim().toLowerCase())
            : null;

        // Fetch a larger pool than requested so the diversity filter has
        // room to manoeuvre without shrinking the returned page
        const poolSize = limit * 3;

        const query = tags && tags.length > 0
            ? Post.find({ tags: { $in: tags } })
            : Post.find();

        const pool = await query
            .sort({ createdAt: -1 })
            .skip(offset)
            .limit(poolSize)
            .lean();    // lean() skips Mongoose hydration for speed

        // Convert lean objects to have the right shape (lean skips toJSON transform)
        const formatted = pool.map(p => ({
            id:         p._id.toString(),
            content:    p.content,
            tags:       p.tags,
            authorName: p.authorName,
            createdAt:  p.createdAt.getTime()
        }));

        // Pre-rank then diversify
        const preRanked  = preRank(formatted);
        const page       = diversifyPage(preRanked, limit);
        const totalCount = await Post.countDocuments(tags ? { tags: { $in: tags } } : {});

        res.json({ posts: page, total: totalCount });

    } catch (err) {
        console.error('getPosts error:', err);
        res.status(500).json({ error: 'Failed to fetch posts' });
    }
}

// ── POST /posts ───────────────────────────────────────────────────────────────
async function createPost(req, res) {
    try {
        const { content, tags = [], authorName = 'Anonymous' } = req.body || {};

        if (typeof content !== "string" || content.trim().length === 0) {
            return res.status(400).json({ error: 'Content is required' });
        }
        if (content.length > 280) {
            return res.status(400).json({ error: 'Content exceeds 280 characters' });
        }

        if (!Array.isArray(tags) || tags.some(tag => typeof tag !== "string")) {
            return res.status(400).json({ error: "Tags must be an array of strings" });
        }

        // Sanitise tags
        const cleanTags = [...new Set(
            tags
                .map(t => String(t).trim().toLowerCase().replace(/[^a-z0-9-]/g, ''))
                .filter(t => t.length > 0 && t.length <= 30)
        )].slice(0, 5);

        const post = await Post.create({
            content: content.trim(),
            tags: cleanTags,
            authorName: String(authorName).trim().slice(0, 50) || 'Anonymous'
        });

        res.status(201).json(post.toJSON());

    } catch (err) {
        if (err.name === 'ValidationError') {
            return res.status(400).json({ error: err.message });
        }
        console.error('createPost error:', err);
        res.status(500).json({ error: 'Failed to create post' });
    }
}

module.exports = { getPosts, createPost };
