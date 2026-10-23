// src/models/Post.js
const mongoose = require('mongoose');

/**
 * The schema deliberately excludes:
 *   - likes / reactions
 *   - view counts
 *   - follower references
 *   - share counts
 *
 * The only metrics stored are for the ranking algorithm itself.
 */
const PostSchema = new mongoose.Schema({
    content: {
        type: String,
        required: true,
        minlength: 1,
        maxlength: 280,
        trim: true
    },
    tags: {
        type: [String],
        default: [],
        validate: {
            validator: tags => tags.length <= 5,
            message: 'Maximum 5 tags per post'
        }
    },
    authorName: {
        type: String,
        default: 'Anonymous',
        maxlength: 50,
        trim: true
    },

    // Ranking signal: raw quality hint (0–1) set at creation time.
    // Could be computed from content length, tag coherence, etc.
    // Never exposed to the client.
    _qualityHint: {
        type: Number,
        default: 0.5,
        min: 0,
        max: 1
    }
}, {
    timestamps: true,   // createdAt + updatedAt managed by Mongoose
    toJSON: {
        transform(_, ret) {
            ret.id = ret._id;
            // Strip internal fields before sending to clients
            delete ret._id;
            delete ret.__v;
            delete ret._qualityHint;
            delete ret.updatedAt;
            return ret;
        }
    }
});

// Index on createdAt for fast recency queries
PostSchema.index({ createdAt: -1 });
// Index on tags for tag-filtered explore queries
PostSchema.index({ tags: 1 });

module.exports = mongoose.model('Post', PostSchema);
