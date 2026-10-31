'use strict';

// Top-right avatar menu (修改账号 / 修改密码 / 退出登录): dialog behavior + wiring.
// node --test personnel-server/src/test/frontend/account-menu.test.cjs
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');

const base = resolve(__dirname, '../../main/resources/static');
const app = readFileSync(resolve(base, 'app.js'), 'utf8');
const css = readFileSync(resolve(base, 'app.css'), 'utf8');

function definition(name) {
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = app.match(new RegExp(`^function ${escaped}\\([^\\n]*\\) \\{[\\s\\S]*?^\\}`, 'm'));
  assert.ok(match, `production function ${name} must exist`);
  return match[0];
}
const names = ['h', 'esc', 'hideFieldBubble', 'showFieldBubble', 'buildForm', 'forceLogin',
  'openChangePassword', 'openChangeUsername'];
const functions = names.map(definition).join('\n');
const flush = () => new Promise((done) => setImmediate(done));

class Element {
  constructor(tag, text) {
    this.tagName = tag;
    this.nodeType = tag === '#text' ? 3 : 1;
    this._nodes = [];
    this.parentNode = null;
    this.attributes = {};
    this.style = {};
    this.listeners = {};
    this.value = '';
    this.disabled = false;
    this.readOnly = false;
    this.offsetWidth = 0;
    this.offsetHeight = 0;
    this._text = text || '';
    this.classList = {
      contains: (name) => this.className.split(/\s+/).includes(name),
      add: (name) => { if (!this.classList.contains(name)) this.className = `${this.className} ${name}`.trim(); },
      remove: (name) => { this.className = this.className.split(/\s+/).filter((item) => item !== name).join(' '); },
      toggle: (name, force) => {
        const add = force === undefined ? !this.classList.contains(name) : !!force;
        this.classList[add ? 'add' : 'remove'](name);
        return add;
      },
    };
  }
  get children() { return this._nodes.filter((node) => node.nodeType === 1); }
  get className() { return this.attributes.class || ''; }
  set className(value) { this.attributes.class = value; }
  setAttribute(key, value) {
    this.attributes[key] = String(value);
    if (key === 'value') this.value = String(value);
    if (key === 'disabled') this.disabled = true;
  }
  getAttribute(key) { return Object.hasOwn(this.attributes, key) ? this.attributes[key] : null; }
  hasAttribute(key) { return this.getAttribute(key) !== null; }
  appendChild(node) { node.remove(); this._nodes.push(node); node.parentNode = this; return node; }
  remove() { if (this.parentNode) { this.parentNode._nodes.splice(this.parentNode._nodes.indexOf(this), 1); } this.parentNode = null; }
  contains(node) { return node === this || this._nodes.some((child) => child.contains(node)); }
  get textContent() { return this._text + this._nodes.map((node) => node.textContent).join(''); }
  set textContent(text) { this._nodes = []; this._text = String(text); }
  matches(selector) {
    const match = /^(?=[.#a-z])(?:([a-z][\w-]*)|#([\w-]+))?((?:\.[\w-]+)*)$/.exec(selector);
    assert.ok(match, `unsupported selector ${selector}`);
    return (!match[1] || match[1].toLowerCase() === this.tagName.toLowerCase()) && (!match[2] || this.getAttribute('id') === match[2])
      && match[3].split('.').filter(Boolean).every((name) => this.classList.contains(name));
  }
  querySelectorAll(selector) {
    const matches = [];
    for (const node of this.children) { if (node.matches(selector)) matches.push(node); matches.push(...node.querySelectorAll(selector)); }
    return matches;
  }
  querySelector(selector) { return this.querySelectorAll(selector)[0] || null; }
  addEventListener(type, callback) { (this.listeners[type] ||= []).push(callback); }
  dispatch(type, extra = {}) {
    const event = { target: this, preventDefault() {}, stopPropagation() {}, ...extra };
    for (const callback of this.listeners[type] || []) callback(event);
  }
  click() { if (!this.disabled) this.dispatch('click'); }
  focus() { this.focused = true; }
  scrollIntoView() {}
  getBoundingClientRect() { return { left: 0, top: 0, right: 0, bottom: 0, width: 0, height: 0 }; }
}

const profile = { userId: 7, username: 'admin', displayName: '管理员甲', isAdmin: true };

function harness(t) {
  const body = new Element('body');
  const document = { body, activeElement: null, createElement: (tag) => new Element(tag),
    createTextNode: (text) => new Element('#text', text),
    querySelector: (selector) => body.querySelector(selector), querySelectorAll: (selector) => body.querySelectorAll(selector) };
  const calls = [], messages = [], renders = [], cleared = [], modals = [], timers = [];
  const context = vm.createContext({ document, USER: structuredClone(profile), TOKEN: 'session', redirectingToLogin: false,
    window: { innerWidth: 1200 },
    confirm: () => true,
    $: (selector) => body.querySelector(selector), $all: (selector) => body.querySelectorAll(selector),
    clearSession: () => cleared.push('session'),
    toast: (text, type) => messages.push({ text, type }),
    renderLogin: (message) => renders.push(['login', message]),
    setTimeout: (callback) => { timers.push(callback); return 0; },
  });
  function request(method, path, data) {
    const call = { method, path, data: data === undefined ? undefined : JSON.parse(JSON.stringify(data)), settled: false };
    call.promise = new Promise((resolve, reject) => {
      call.resolve = (value) => { call.settled = true; resolve(value); };
      call.reject = (error) => { call.settled = true; reject(error); };
    });
    calls.push(call);
    return call.promise;
  }
  context.POST = (path, data) => request('POST', path, data);
  context.openModal = (options) => {
    const dialog = new Element('div', '');
    dialog.classList.add('dialog');
    dialog.appendChild(options.body);
    const footer = new Element('div', '');
    for (const button of options.footer || []) footer.appendChild(button);
    dialog.appendChild(footer);
    body.appendChild(dialog);
    const modal = { dialog, options, close(force) {
      if (force !== true && options.beforeClose && options.beforeClose() === false) return;
      dialog.remove();
      if (options.onClose) options.onClose();
    } };
    modals.push(modal);
    return modal;
  };
  vm.runInContext(functions, context, { timeout: 1000 });
  t.after(() => { assert.ok(calls.every((call) => call.settled), `unsettled mocked requests: ${calls.filter((c) => !c.settled).map((c) => c.path)}`); });
  return { context, body, calls, messages, renders, cleared, modals,
    dialog() { return modals.at(-1).dialog; },
    save() { return modals.at(-1).dialog.querySelector('.btn-primary'); },
    inputs() { return modals.at(-1).dialog.querySelectorAll('input'); } };
}

test('openChangeUsername prefills the readonly current account with the login username, not the display name', (t) => {
  const h = harness(t);
  h.context.openChangeUsername();
  const inputs = h.inputs();
  assert.equal(h.modals.at(-1).options.title, '修改账号');
  assert.equal(inputs.length, 3);
  assert.equal(inputs[0].value, 'admin');
  assert.equal(inputs[0].readOnly, true, 'current account is readonly');
  assert.ok(inputs[0].classList.contains('ro'));
  assert.equal(inputs[1].value, '', 'new username starts empty');
  assert.equal(inputs[2].value, '', 'password starts empty');
});

test('openChangeUsername stays local on empty input and on unchanged name', async (t) => {
  const h = harness(t);
  h.context.openChangeUsername();
  const inputs = h.inputs();
  inputs[2].value = 'secret123';
  h.save().click();
  await flush();
  assert.equal(h.calls.length, 0, 'no request while a required field is empty');
  assert.ok(h.dialog().textContent.includes('请填写'));

  inputs[1].value = 'admin';   // same as login username, different from displayName
  h.save().click();
  await flush();
  assert.equal(h.calls.length, 0, 'no request when the name is unchanged');
  assert.ok(h.dialog().textContent.includes('新账号不能与当前账号相同'));
});

test('successful rename posts the payload, closes the dialog and forces relogin', async (t) => {
  const h = harness(t);
  h.context.openChangeUsername();
  const inputs = h.inputs();
  inputs[1].value = ' admin.new ';
  inputs[2].value = 'secret123';
  h.save().click();
  await flush();
  assert.equal(h.save().disabled, true, 'save button locks while pending');
  assert.equal(h.calls.length, 1);
  assert.equal(h.calls[0].method, 'POST');
  assert.equal(h.calls[0].path, '/api/auth/change-username');
  assert.deepEqual(h.calls[0].data, { newUsername: 'admin.new', oldPassword: 'secret123' });
  h.calls[0].resolve({});
  await flush(); await flush();
  assert.equal(h.dialog().parentNode, null, 'dialog closed after success');
  assert.deepEqual(h.renders, [['login', '账号已修改，请使用新账号重新登录']]);
  assert.deepEqual(h.cleared, ['session'], 'session cleared');
});

test('rejected rename surfaces the server message and re-enables the save button', async (t) => {
  const h = harness(t);
  h.context.openChangeUsername();
  const inputs = h.inputs();
  inputs[1].value = 'taken.name';
  inputs[2].value = 'wrong-pass';
  h.save().click();
  await flush();
  h.calls[0].reject(new Error('用户名已存在'));
  await flush(); await flush();
  assert.equal(h.save().disabled, false, 'button re-enabled after failure');
  assert.ok(h.dialog().textContent.includes('用户名已存在'));
  assert.equal(h.renders.length, 0, 'no relogin on failure');
});

test('openChangePassword still posts and toasts (menu regression)', async (t) => {
  const h = harness(t);
  h.context.openChangePassword();
  assert.equal(h.modals.at(-1).options.title, '修改密码');
  const inputs = h.inputs();
  inputs[0].value = 'old-secret';
  inputs[1].value = 'new-secret';
  inputs[2].value = 'new-secret';
  h.save().click();
  await flush();
  assert.equal(h.calls.length, 1);
  assert.equal(h.calls[0].method, 'POST');
  assert.equal(h.calls[0].path, '/api/auth/change-password');
  assert.deepEqual(h.calls[0].data, { oldPassword: 'old-secret', newPassword: 'new-secret' });
  h.calls[0].resolve({});
  await flush(); await flush();
  assert.equal(h.dialog().parentNode, null);
  assert.ok(h.messages.some((m) => m.text.includes('密码修改成功')));
});

function topbarSource() {
  const start = app.indexOf("var topbar = h('div', { class: 'topbar' }");
  const end = app.indexOf("var avatarMenu = topbar.querySelector('.topbar-menu');", start);
  assert.ok(start >= 0 && end > start, 'topbar block must exist');
  return app.slice(start, end);
}

test('topbar renders an avatar with a three-item dropdown instead of text buttons', () => {
  const source = topbarSource();
  assert.ok(source.includes("class: 'topbar-avatar-wrap'"));
  assert.ok(source.includes("class: 'topbar-avatar'"));
  assert.ok(source.includes("'aria-label': '账号菜单'"));
  assert.ok(source.includes("'aria-haspopup': 'true'"));
  assert.ok(source.includes("'aria-expanded': 'false'"));
  assert.ok(source.includes('who.slice(0, 1).toUpperCase()'), 'avatar letter comes from the displayed name');
  assert.ok(source.includes("class: 'topbar-menu'"));
  const items = ['修改账号', '修改密码', '退出登录'];
  let cursor = 0;
  for (const item of items) {
    const at = source.indexOf(`text: '${item}'`, cursor);
    assert.ok(at > 0, `menu item ${item} present in order`);
    cursor = at;
  }
  const logoutAt = source.indexOf("text: '退出登录'");
  const logout = source.slice(source.lastIndexOf("class: 'topbar-menu-item", logoutAt));
  assert.ok(logout.includes('topbar-menu-item danger'));
  assert.ok(logout.includes('logoutFn()'));
  assert.ok(source.includes('closeMenu(); openChangeUsername()'));
  assert.ok(source.includes('closeMenu(); openChangePassword()'));
  assert.ok(!/"btn |'btn'/.test(source.slice(source.indexOf("class: 'topbar-user'"))), 'no leftover text buttons in the user area');
  assert.ok(app.includes("document.addEventListener('click', closeMenu)"), 'outside click closes the menu');
  assert.ok(/addEventListener\('keydown'[\s\S]{0,120}Escape/.test(app), 'Escape closes the menu');
});

test('avatar menu styles exist and the new code stays ES5', () => {
  for (const rule of ['.topbar-avatar-wrap', '.topbar-avatar', '.topbar-menu', '.topbar-menu-item']) {
    assert.ok(css.includes(rule), `css rule ${rule} exists`);
  }
  assert.ok(css.includes('.topbar-menu-item.danger'));
  const region = definition('openChangeUsername') + topbarSource();
  assert.doesNotMatch(region, /\b(?:let|const|class|async|await)\s+\w|=>|`|\.padStart\(/);
});
