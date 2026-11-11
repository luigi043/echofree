import http from 'node:http';
import { pathToFileURL } from 'node:url';

export function fixtureApi() {
  let readFailure = false;
  let writeFailure = false;
  const posts = [];
  let nextId = 1;

  const server = http.createServer(async (req, res) => {
    const send = (code, data) => {
      res.writeHead(code, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(data));
    };
    const path = new URL(req.url, 'http://localhost').pathname;
    if (req.method === 'GET' && path === '/health') return send(200, { status: 'ok' });
    if (path !== '/posts') return send(404, { error: 'Not found' });
    if (req.method === 'GET') {
      if (readFailure) return send(503, { error: 'Synthetic read failure' });
      return send(200, { posts, total: posts.length });
    }
    if (req.method !== 'POST') return send(405, { error: 'Method not allowed' });
    if (writeFailure) return send(503, { error: 'Synthetic write failure' });
    try {
      let body = '';
      for await (const chunk of req) {
        body += chunk;
        if (body.length > 16384) return send(413, { error: 'Draft too large' });
      }
      const { content, tags = [], authorName = 'Anonymous' } = JSON.parse(body);
      if (typeof content !== 'string' || !content.trim() || content.length > 280) {
        return send(400, { error: 'Invalid post text' });
      }
      const post = { id: String(nextId++), content, tags, authorName, createdAt: Date.now() };
      posts.unshift(post);
      return send(201, post);
    } catch {
      return send(400, { error: 'Invalid draft' });
    }
  });

  return {
    server, posts,
    failReads(value) { readFailure = value; },
    failWrites(value) { writeFailure = value; },
    seed() {
      const now = Date.now();
      posts.push(
        { id: 'garden', authorName: 'Nia Flores', content: 'Our community garden tried a seed swap. We came home with three kinds of beans and a lot of advice.', tags: ['garden', 'community'], createdAt: now - 240000 },
        { id: 'books', authorName: 'Mira Chen', content: 'I took the long way home and found a shelf of books to borrow at the bus stop.', tags: ['neighbourhood', 'books'], createdAt: now - 420000 },
        { id: 'repair', authorName: 'Theo Park', content: 'I finally fixed a wobbly chair with a piece of scrap wood.', tags: ['repair'], createdAt: now - 780000 }
      );
    }
  };
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  const fixture = fixtureApi();
  fixture.seed();
  fixture.server.listen(3000, '127.0.0.1', () => console.log('Synthetic EchoFree API at http://127.0.0.1:3000'));
}
