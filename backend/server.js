// server.js — EchoFree API entry point
const express    = require('express');
const mongoose   = require('mongoose');
const helmet     = require('helmet');
const rateLimit  = require('express-rate-limit');
const postRoutes = require('./src/routes/posts');
require('dotenv').config();

const app  = express();
const PORT = process.env.PORT || 3000;

// ── Middleware ────────────────────────────────────────────────────────────────
app.use(helmet());
app.use(express.json({ limit: '16kb' }));

// Basic rate limiting — adjust for production
app.use(rateLimit({
    windowMs: 60 * 1000,   // 1 minute
    max: 60,
    message: { error: 'Too many requests, slow down' }
}));

// ── Routes ────────────────────────────────────────────────────────────────────
app.use('/posts', postRoutes);

app.get('/health', (_, res) => res.json({ status: 'ok' }));

// ── Database ──────────────────────────────────────────────────────────────────
const MONGO_URI = process.env.MONGO_URI || 'mongodb://127.0.0.1:27017/echofree';

mongoose.connect(MONGO_URI)
    .then(() => {
        console.log('Connected to MongoDB');
        app.listen(PORT, () => console.log(`EchoFree API listening on :${PORT}`));
    })
    .catch(err => {
        console.error('MongoDB connection failed:', err.message);
        process.exit(1);
    });

module.exports = app;   // for testing
