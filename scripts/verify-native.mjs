import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { mkdir } from 'node:fs/promises';
import { join } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { fixtureApi } from './fixture-api.mjs';

const serial = process.argv[2];
assert.match(serial ?? '', /^emulator-\d+$/, 'Pass an Android emulator serial');
const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
assert.ok(sdk, 'Set ANDROID_HOME');
const adb = join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
const run = promisify(execFile);
const device = async (...args) => (await run(adb, ['-s', serial, ...args])).stdout.trim();
const root = fileURLToPath(new URL('../', import.meta.url));
const output = join(root, 'docs/verification/native');
const pkg = 'com.echofree';
const apk = join(root, 'android/app/build/outputs/apk/debug/app-debug.apk');
const screenFile = '/data/local/tmp/echofree-screen.png';
const uiFile = '/sdcard/echofree-window.xml';

async function dump() {
  const result = await device('shell', 'uiautomator', 'dump', uiFile);
  if (!result.includes('dumped to:')) return '';
  return device('shell', 'cat', uiFile);
}

function node(xml, attribute, value) {
  return [...xml.matchAll(/<node\s[^>]+>/g)].map(match => match[0])
    .find(tag => tag.includes(attribute + '="' + value + '"'));
}

async function waitFor(attribute, value) {
  for (let attempt = 0; attempt < 7; attempt++) {
    const match = node(await dump(), attribute, value);
    if (match) return match;
    await delay(250);
  }
  throw new Error('Did not find ' + attribute + '=' + value);
}

async function tap(attribute, value) {
  const match = (await waitFor(attribute, value)).match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/);
  const [, left, top, right, bottom] = match.map(Number);
  await device('shell', 'input', 'tap', String((left + right) >> 1), String((top + bottom) >> 1));
}

async function capture(name) {
  await device('shell', 'screencap', '-p', screenFile);
  await device('pull', screenFile, join(output, name + '.png'));
  console.log('Captured ' + name);
}

const fixture = fixtureApi();
assert.equal(await device('shell', 'getprop', 'sys.boot_completed'), '1');
const installed = await device('shell', 'pm', 'list', 'packages', pkg);
if (installed && !process.argv.includes('--reset-synthetic')) {
  throw new Error('Use a fresh emulator install or pass --reset-synthetic for a disposable EchoFree install.');
}
await mkdir(output, { recursive: true });
await new Promise((resolve, reject) => {
  fixture.server.once('error', reject);
  fixture.server.listen(3000, '127.0.0.1', resolve);
});

try {
  fixture.failReads(true);
  await device('install', '-r', apk);
  if (installed) await device('shell', 'pm', 'clear', pkg);
  await device('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP');
  await device('shell', 'wm', 'dismiss-keyguard');
  await device('shell', 'am', 'start', '-n', pkg + '/.MainActivity');
  await waitFor('text', 'Try again');
  await capture('01-feed-error');

  fixture.failReads(false);
  await tap('text', 'Try again');
  await waitFor('text', 'No posts yet. Write the first one.');
  await capture('02-feed-empty');

  fixture.seed();
  const bounds = (await waitFor('resource-id', pkg + ':id/swipe_refresh'))
    .match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/).map(Number);
  const [, left, top, right, bottom] = bounds;
  const mid = String((left + right) >> 1);
  await device('shell', 'input', 'swipe', mid, String(top + 60), mid, String((top + bottom) >> 1), '450');
  await waitFor('text', 'Mira Chen');
  await capture('03-feed');

  await tap('text', 'Explore');
  assert.match(await waitFor('text', 'Explore'), /checked="true"/);
  await capture('04-explore');

  await tap('content-desc', 'Write a post');
  assert.match(await waitFor('resource-id', pkg + ':id/btn_post'), /enabled="false"/);
  await capture('05-compose');
  await tap('content-desc', 'Close composer');
  await waitFor('text', 'Explore');
  await tap('content-desc', 'Write a post');

  await tap('resource-id', pkg + ':id/et_content');
  await delay(600);
  await device('shell', 'input', 'text', 'Synthetic%snote');
  await waitFor('text', 'Synthetic note');
  const inputMethod = await device('shell', 'dumpsys', 'input_method');
  if (inputMethod.includes('mInputShown=true')) await device('shell', 'input', 'keyevent', '4');
  await waitFor('text', 'Synthetic note');
  await capture('06-compose-draft');

  fixture.failWrites(true);
  await tap('resource-id', pkg + ':id/btn_post');
  await waitFor('text', 'Could not confirm publication. Your text is still here.');
  assert.equal(fixture.posts.length, 3);
  await capture('07-compose-error');

  fixture.failWrites(false);
  await tap('resource-id', pkg + ':id/btn_post');
  await waitFor('content-desc', 'Write a post');
  if (!node(await dump(), 'text', 'Synthetic note')) {
    await device('shell', 'input', 'swipe', mid, String(bottom - 60), mid, String(top + 70), '450');
  }
  await waitFor('text', 'Synthetic note');
  assert.equal(fixture.posts.filter(post => post.content === 'Synthetic note').length, 1);
  assert.equal(fixture.posts[0].authorName, 'Anonymous');
  await capture('08-published');
  console.log('Native walkthrough passed.');
} finally {
  await device('shell', 'rm', '-f', screenFile, uiFile);
  fixture.server.closeAllConnections();
  await new Promise(resolve => fixture.server.close(resolve));
}
