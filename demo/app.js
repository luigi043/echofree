'use strict';

const now = Date.now();
const sample = [
  ['Mira Chen', 'I took the long way home and found a shelf of books to borrow at the bus stop. What small things make your neighbourhood feel like yours?', ['neighbourhood', 'books']],
  ['Sam Rivera', 'A tiny ritual that helped me write this week: end each session in the middle of a sentence. Starting again felt much easier.', ['writing']],
  ['Nia Flores', 'Our community garden tried a seed swap instead of buying new packets. We came home with three kinds of beans and a lot of advice.', ['garden', 'community']],
  ['Theo Park', 'I finally fixed a wobbly chair with a piece of scrap wood. It felt better than ordering a replacement.', ['repair']],
  ['Amal Brooks', 'Question for anyone learning a language: which everyday phrase did you use first outside a lesson?', ['learning', 'language']],
  ['Jules Martin', 'I recorded the rain for two minutes with my phone. Listening back later, I heard things I missed while I was there.', ['sound', 'observation']]
].map(([author, content, tags], index) => ({ author, content, tags, at: now - (index + 1) * 720000 }));

const $ = id => document.getElementById(id);
const feedView = $('feed-view');
const composeView = $('compose-view');
const content = $('content');
let posts = sample.slice();
let mode = 'feed';

function element(tag, text, className) {
  const node = document.createElement(tag);
  if (text) node.textContent = text;
  if (className) node.className = className;
  return node;
}

function render() {
  const seen = new Map();
  const ranked = posts.map(post => {
    const variety = post.tags.length
      ? post.tags.reduce((sum, tag) => sum + ((seen.get(tag) || 0) >= 3 ? 0.7 : 1), 0) / post.tags.length
      : 0.7;
    for (const tag of post.tags) seen.set(tag, (seen.get(tag) || 0) + 1);
    const fresh = 2 ** (-(Date.now() - post.at) / 21600000);
    const score = mode === 'explore' ? Math.random() : 0.45 * fresh + 0.25 * variety + 0.3 * Math.random();
    return { post, score };
  }).sort((a, b) => b.score - a.score);
  $('posts').replaceChildren(...ranked.map(({ post }) => {
    const card = element('article');
    const head = element('header');
    const avatar = element('span', Array.from(post.author)[0], 'avatar');
    avatar.setAttribute('aria-hidden', 'true');
    const minutes = Math.floor((Date.now() - post.at) / 60000);
    const time = element('time', minutes ? minutes + 'm ago' : 'just now');
    time.dateTime = new Date(post.at).toISOString();
    head.append(avatar, element('strong', post.author), time);
    card.append(head, element('p', post.content));
    if (post.tags.length) {
      const tags = element('div', '', 'tags');
      tags.append(...post.tags.map(tag => element('span', '#' + tag, 'tag')));
      card.append(tags);
    }
    return card;
  }));
  $('posts').scrollTop = 0;
}

function setMode(value) {
  mode = value;
  for (const tab of ['feed', 'explore']) $(tab).setAttribute('aria-pressed', String(tab === mode));
  render();
}

function showComposer(open) {
  feedView.hidden = open;
  composeView.hidden = !open;
  $('refresh').hidden = open;
  (open ? $('compose-title') : $('write')).focus();
}

function updateCounter() {
  $('count').textContent = content.value.length + ' / 280';
  $('publish').disabled = !content.value.trim();
}

$('feed').addEventListener('click', () => setMode('feed'));
$('explore').addEventListener('click', () => setMode('explore'));
$('refresh').addEventListener('click', () => {
  render();
  $('status').textContent = 'Posts refreshed.';
});
$('write').addEventListener('click', () => {
  $('status').textContent = '';
  showComposer(true);
});
$('close').addEventListener('click', () => showComposer(false));
content.addEventListener('input', updateCounter);
$('compose').addEventListener('submit', event => {
  event.preventDefault();
  const draft = content.value.trim();
  if (!draft || draft.length > 280) return;
  const tags = $('tags').value.split(',')
    .map(tag => tag.trim().toLowerCase().replace(/[^a-z0-9-]/g, ''))
    .filter(tag => tag && tag.length <= 30);
  posts.unshift({
    author: $('author').value.trim() || 'Anonymous',
    content: draft,
    tags: [...new Set(tags)].slice(0, 5),
    at: Date.now()
  });
  event.currentTarget.reset();
  updateCounter();
  setMode('feed');
  showComposer(false);
  $('status').textContent = 'Post published in this demo.';
});
render();
