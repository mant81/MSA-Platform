const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const elements = new Map();
const replies = [];
const calls = [];
const context = vm.createContext({
  document: { getElementById(id) {
    if (!elements.has(id)) elements.set(id, { value: '', textContent: '', listeners: {}, addEventListener(event, fn) { this.listeners[event] = fn; } });
    return elements.get(id);
  } },
  localStorage: { getItem() { throw Error('Persistent token read forbidden'); }, setItem() { throw Error('Persistent token write forbidden'); } },
  fetch: async (url, options) => {
    calls.push({ url, options });
    const reply = replies.shift();assert.ok(reply, 'Expected fixture response');
    return { status: reply.status, json: async () => { assert.notEqual(reply.status, 204);return reply.body; } };
  }
});
vm.runInContext(fs.readFileSync(path.join(__dirname, '../../test-service/src/main/resources/static/js/app.js'), 'utf8'), context);
async function click(id, status, body) {
  replies.push({ status, body });await elements.get(id).listeners.click();return calls.at(-1);
}
(async () => {
  await click('btn-auth-signup', 200, { accessToken: 'fixture-access-1', refreshToken: 'fixture-refresh-1' });
  let call = await click('btn-config-select-all', 200, []);
  assert.equal(call.options.headers.Authorization, 'Bearer fixture-access-1');
  call = await click('btn-auth-refresh', 200, { accessToken: 'fixture-access-2', refreshToken: 'fixture-refresh-2' });
  assert.equal(call.options.headers.Authorization, undefined);
  assert.equal(JSON.parse(call.options.body).refreshToken, 'fixture-refresh-1');
  call = await click('btn-config-select-all', 200, []);
  assert.equal(call.options.headers.Authorization, 'Bearer fixture-access-2');
  await click('btn-auth-logout', 204);
  call = await click('btn-config-select-all', 401, { success: false });
  assert.equal(call.options.headers.Authorization, undefined);
  await click('btn-auth-login', 200, { accessToken: 'fixture-access-3', refreshToken: 'fixture-refresh-3' });
  await click('btn-auth-login', 401, { success: false });
  call = await click('btn-config-select-all', 401, { success: false });
  assert.equal(call.options.headers.Authorization, undefined);
  assert.equal(replies.length, 0);
  console.log('PASS: memory-only token handling, refresh header/body, rotation, logout 204, failed-login clearing');
})().catch(error => { console.error(error);process.exitCode = 1; });
