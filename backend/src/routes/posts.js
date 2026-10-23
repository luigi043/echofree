// src/routes/posts.js
const express = require('express');
const { getPosts, createPost } = require('../controllers/PostController');

const router = express.Router();

router.get('/',  getPosts);
router.post('/', createPost);

module.exports = router;
