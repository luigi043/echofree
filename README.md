# EchoFree

> A social feed without followers, likes, or popularity signals.

EchoFree is an Android app that challenges the assumption that engagement metrics make social feeds better. Instead, the feed algorithm surfaces content through **freshness**, **tag diversity**, and **controlled randomness** — no echo chambers, no popularity spirals.

---

## Architecture

```
Android (Kotlin + MVVM)
  └── PostRepository ──HTTP──▶ Node.js REST API
                                 ├── RankingEngine (pre-rank)
                                 └── MongoDB (posts collection)

Client finalises ranking with per-device randomness → every user sees a different order.
```

---

## Ranking Algorithm

The feed is produced in two stages:

### Stage 1 — Server (RankingEngine.js)
| Signal | Weight | Formula |
|--------|--------|---------|
| Freshness | 60% | `e^(-λt)` — exponential decay, half-life 12h |
| Tag diversity | 40% | Penalty if a tag already has ≥ 4 posts in the result set |

The server returns posts in a pre-ranked order but does **not** inject randomness — that happens client-side so each device gets variety.

### Stage 2 — Client (FeedRanker.kt)
| Signal | Weight (default) |
|--------|-----------------|
| Freshness | 45% |
| Tag diversity | 25% |
| Random noise | 30% |

The random component is seeded fresh on every pull-to-refresh, so the same posts produce a different feed order each time.

### Wildcard Injection
Every 6th post in the final feed is replaced by a "wildcard" — a post whose primary tag is the least-seen tag in the current feed. This guarantees exposure to unfamiliar topics even in a small corpus.

### Explore Mode
Toggling Explore zeroes out freshness and diversity signals, leaving only random noise. The result is a fully shuffled feed — useful for discovery when the user is browsing without intent.

---

## Project Structure

```
echofree/
├── android/
│   └── app/src/main/java/com/echofree/
│       ├── data/
│       │   ├── models/Post.kt
│       │   └── repository/PostRepository.kt
│       ├── ui/
│       │   ├── feed/
│       │   │   ├── FeedFragment.kt
│       │   │   └── FeedViewModel.kt
│       │   └── create/
│       │       ├── CreatePostFragment.kt
│       │       └── CreatePostViewModel.kt
│       ├── network/ApiService.kt
│       └── util/FeedRanker.kt          ← core algorithm
└── backend/
    ├── server.js
    ├── src/
    │   ├── models/Post.js
    │   ├── services/RankingEngine.js   ← core algorithm
    │   ├── controllers/PostController.js
    │   └── routes/posts.js
    └── src/__tests__/RankingEngine.test.js
```

---

## Getting Started

### Backend

```bash
cd backend
cp .env.example .env
npm install
npm run dev          # requires MongoDB running locally
npm test             # run unit tests
```

### Android

1. Open `android/` in Android Studio (Hedgehog or later).
2. In `ApiService.kt`, set `BASE_URL` to your backend's address.
   - Emulator default: `http://10.0.2.2:3000/`
   - Physical device: use your machine's LAN IP.
3. Add dependencies to `build.gradle.kts`:
   ```kotlin
   implementation("com.squareup.retrofit2:retrofit:2.11.0")
   implementation("com.squareup.retrofit2:converter-gson:2.11.0")
   implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
   implementation("com.google.android.material:material:1.12.0")
   ```
4. Run on emulator or device.

---

## API

| Endpoint | Method | Params | Response |
|----------|--------|--------|----------|
| `/posts` | GET | `limit`, `offset`, `tags` | `{ posts: Post[], total: number }` |
| `/posts` | POST | `{ content, tags, authorName }` | `Post` |
| `/health` | GET | — | `{ status: "ok" }` |

---

## What's intentionally missing

| Feature | Why it's absent |
|---------|-----------------|
| Like counts | Amplify existing popularity; create anxiety |
| Follower counts | Reduce content to a vanity number |
| Share/view counts | Surface viral content regardless of quality |
| Trending topics | Concentrates attention on a narrow slice |
| Chronological "following" feed | Requires followers — eliminated by design |

---

## Tuning the algorithm

Both `RankConfig` (Kotlin) and `preRank` options (JS) accept weight overrides. A few starting points:

| Goal | freshnessWeight | diversityWeight | noiseWeight |
|------|----------------|----------------|-------------|
| Fast-moving news-style | 0.65 | 0.20 | 0.15 |
| Balanced default | 0.45 | 0.25 | 0.30 |
| Max randomness / Explore | 0.10 | 0.10 | 0.80 |
| Evergreen content focus | 0.20 | 0.40 | 0.40 |

---

## Roadmap

- [ ] Room database for offline-first persistence
- [ ] On-device tag interest model (no server tracking)
- [ ] Push notifications for new content in interested tags
- [ ] Image posts
- [ ] Configurable half-life per tag category
