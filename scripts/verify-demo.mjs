import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { setTimeout as delay } from "node:timers/promises";

const browser = process.env.ECHOFREE_BROWSER ||
  "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe";
const url = new URL(process.argv[2] || new URL("../demo/index.html", import.meta.url));
const phoneOnly = url.searchParams.get('view') === 'phone';
const profile = await mkdtemp(join(tmpdir(), "echofree-preview-"));
const processHandle = spawn(browser, [
  "--headless=new", "--disable-gpu", "--no-first-run", "--remote-debugging-port=0",
  "--user-data-dir=" + profile, "about:blank"
], { stdio: "ignore", windowsHide: true });
const exited = new Promise(resolve => {
  processHandle.once("exit", resolve);
  processHandle.once("error", resolve);
});

try {
  let port;
  for (let i = 0; i < 80 && !port; i++) {
    try { port = (await readFile(join(profile, "DevToolsActivePort"), "utf8")).split("\n")[0]; }
    catch { await delay(250); }
  }
  assert.ok(port, "Browser DevTools port");
  const pages = await (await fetch("http://127.0.0.1:" + port + "/json/list")).json();
  const socket = new WebSocket(pages.find(page => page.type === "page").webSocketDebuggerUrl);
  await new Promise((resolve, reject) => {
    socket.addEventListener("open", resolve, { once: true });
    socket.addEventListener("error", reject, { once: true });
  });
  let id = 0;
  const pending = new Map();
  const errors = [];
  socket.addEventListener("message", ({ data }) => {
    const response = JSON.parse(data);
    if (response.method === "Runtime.exceptionThrown") errors.push(response.params.exceptionDetails);
    const callback = pending.get(response.id);
    if (!callback) return;
    pending.delete(response.id);
    if (response.error) callback.reject(new Error(JSON.stringify(response.error)));
    else callback.resolve(response.result);
  });
  function send(method, params = {}) {
    return new Promise((resolve, reject) => {
      pending.set(++id, { resolve, reject });
      socket.send(JSON.stringify({ id, method, params }));
    });
  }
  async function evaluate(expression) {
    const result = await send("Runtime.evaluate", { expression, returnByValue: true });
    if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description);
    return result.result.value;
  }
  await send("Page.enable");
  await send("Runtime.enable");
  for (const width of [320, 390, 700, 1280]) {
    const mobile = width <= 700;
    await send('Emulation.setDeviceMetricsOverride', {
      width, height: 850, deviceScaleFactor: 1, mobile
    });
    const loaded = new Promise(resolve => {
      socket.addEventListener('message', function load({ data }) {
        if (JSON.parse(data).method === 'Page.loadEventFired') {
          socket.removeEventListener('message', load);
          resolve();
        }
      });
    });
    await send('Page.navigate', { url: url.href });
    await Promise.race([loaded, delay(8000, undefined, { ref: false })]);
    assert.equal(await evaluate('document.querySelectorAll("article").length'), 6);
    const layout = await evaluate(`(() => {
      const phone = document.querySelector('.phone');
      return {
        frame: parseFloat(getComputedStyle(phone).borderTopWidth),
        width: phone.getBoundingClientRect().width,
        page: document.documentElement.scrollWidth
      };
    })()`);
    assert.equal(layout.frame > 0, !mobile);
    assert.equal(await evaluate("getComputedStyle(document.querySelector('.intro')).display === 'none'"), mobile || phoneOnly);
    assert.ok(layout.page <= width, 'Page fits viewport');
    if (mobile) assert.equal(layout.width, width, 'Phone fills mobile viewport');

    assert.equal(await evaluate(`(() => {
      document.querySelector('#explore').click();
      return document.querySelector('#explore').getAttribute('aria-pressed');
    })()`), 'true');
    assert.equal(await evaluate(`(() => {
      document.querySelector('#refresh').click();
      return document.querySelector('#status').textContent;
    })()`), 'Posts refreshed.');
    assert.equal(await evaluate(`(() => {
      document.querySelector('#write').click();
      const content = document.querySelector('#content');
      content.value = '   ';
      content.dispatchEvent(new Event('input'));
      return document.querySelector('#publish').disabled;
    })()`), true);
    assert.equal(await evaluate(`(() => {
      const content = document.querySelector('#content');
      content.value = 'Synthetic <b>post</b>';
      content.dispatchEvent(new Event('input'));
      document.querySelector('#tags').value = 'Writing, writing, tech';
      document.querySelector('#publish').click();
      return document.querySelectorAll('article').length;
    })()`), 7);
    assert.deepEqual(await evaluate(`(() => {
      const card = Array.from(document.querySelectorAll('article'))
        .find(item => item.querySelector('p').textContent === 'Synthetic <b>post</b>');
      return [
        card.querySelector('strong').textContent,
        card.querySelector('p').childElementCount,
        [...card.querySelectorAll('.tag')].map(tag => tag.textContent),
        document.querySelector('#feed').getAttribute('aria-pressed'),
        document.querySelector('#status').textContent
      ];
    })()`), ['Anonymous', 0, ['#writing', '#tech'], 'true', 'Post published in this demo.']);
    console.log(width + 'px: layout, Explore, refresh, validation and publish passed');
  }
  assert.deepEqual(errors, []);
  socket.close();
} finally {
  processHandle.kill();
  await exited;
  await rm(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 300 });
}
