'use strict';

// Actual production functions, isolated DOM/storage/transports; no HTTP or database.
// node --test personnel-server/src/test/frontend/accounts.test.cjs
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const base = resolve(__dirname, '../../main/resources/static');
const app = readFileSync(resolve(base, 'app.js'), 'utf8');
const ext = readFileSync(resolve(base, 'ext-features.js'), 'utf8');
function definition(source, name, indent = '') {
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const start = `^${indent}function ${escaped}\\([^\\n]*\\) \\{`;
  const match = source.match(new RegExp(`${start}[^\\n]*\\}$`, 'm'))
    || source.match(new RegExp(`${start}[\\s\\S]*?^${indent}\\}`, 'm'));
  assert.ok(match, `production function ${name} must exist`);
  return match[0];
}
const appNames = ['$', '$all', 'h', 'esc', 'badge', 'normalizeProfile', 'hasGlobalAccess', 'canAccessView',
  'saveSession', 'clearSession', 'refreshSessionProfile', 'forceLogin', 'preferenceKey'];
const extNames = ['accountIsDirectChild', 'accountDepartmentOptions', 'accountPayload', 'openAccountForm', 'renderAccountsPage'];
const functions = appNames.map((name) => definition(app, name)).concat(extNames.map((name) => definition(ext, name, '  '))).join('\n');
const flush = () => new Promise((done) => setImmediate(done));
const plain = (value) => JSON.parse(JSON.stringify(value));

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
    this.checked = false;
    this.disabled = false;
    this.readOnly = false;
    this._text = text || '';
    this.classList = {
      contains: (name) => this.className.split(/\s+/).includes(name),
      add: (name) => { if (!this.classList.contains(name)) this.className = `${this.className} ${name}`.trim(); },
      remove: (name) => { this.className = this.className.split(/\s+/).filter((item) => item !== name).join(' '); },
      toggle: (name, force) => { const add = force === undefined ? !this.classList.contains(name) : !!force; this.classList[add ? 'add' : 'remove'](name); },
    };
  }
  get children() { return this._nodes.filter((node) => node.nodeType === 1); }
  get className() { return this.attributes.class || ''; }
  set className(value) { this.attributes.class = value; }
  get firstChild() { return this._nodes[0] || null; }
  setAttribute(key, value) {
    this.attributes[key] = String(value);
    if (key === 'value') this.value = String(value);
    if (key === 'disabled') this.disabled = true;
    if (key === 'readonly') this.readOnly = true;
  }
  getAttribute(key) { return Object.hasOwn(this.attributes, key) ? this.attributes[key] : null; }
  hasAttribute(key) { return this.getAttribute(key) !== null; }
  appendChild(node) { node.remove(); this._nodes.push(node); node.parentNode = this; return node; }
  remove() { if (this.parentNode) { this.parentNode._nodes.splice(this.parentNode._nodes.indexOf(this), 1); this.parentNode = null; } }
  contains(node) { return node === this || this._nodes.some((child) => child.contains(node)); }
  get textContent() { return this._text + this._nodes.map((node) => node.textContent).join(''); }
  set textContent(text) { this.clear(); this._text = String(text); }
  clear() { for (const node of this._nodes) node.parentNode = null; this._nodes = []; this._text = ''; }
  set innerHTML(html) { this.clear(); this._text = String(html).replace(/<[^>]*>/g, ''); }
  get innerHTML() { return this._text; }
  matches(selector) {
    const match = /^(?:([a-z][\w-]*)|#([\w-]+))?((?:\.[\w-]+)*)(?:\[([\w-]+)(?:="([^"]*)")?\])?$/.exec(selector);
    assert.ok(match, `unsupported selector ${selector}`);
    return (!match[1] || match[1] === this.tagName) && (!match[2] || this.getAttribute('id') === match[2])
      && match[3].split('.').filter(Boolean).every((name) => this.classList.contains(name))
      && (!match[4] || (this.hasAttribute(match[4]) && (match[5] === undefined || this.getAttribute(match[4]) === match[5])));
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
    // Synthetic events intentionally bypass disabled to verify handler-level locks.
    for (const callback of this.listeners[type] || []) callback(event);
  }
  click() { if (!this.disabled) this.dispatch('click'); }
  focus() { this.focused = true; }
}
function storage(initial = {}) {
  const data = { ...initial };
  return { getItem: (key) => Object.hasOwn(data, key) ? data[key] : null,
    setItem: (key, value) => { data[key] = String(value); }, removeItem: (key) => { delete data[key]; } };
}
const user = { userId: 20, username: 'child', displayName: '当前账号', parentId: 1, isAdmin: false, departmentIds: [2] };
const child = { id: 30, username: 'child.next', displayName: '直属子账号', parentId: 20, enabled: true, departmentIds: [3], departmentNames: ['有效单位'] };
const tree = [{ id: 3, name: '有效单位', children: [{ id: 4, name: '下级单位' }] }, { id: 8, name: '另一单位' }];

function harness(t, profile = user) {
  const body = new Element('body');
  const host = new Element('div'); host.setAttribute('id', 'app'); body.appendChild(host);
  const document = { body, activeElement: null, createElement: (tag) => new Element(tag), createTextNode: (text) => new Element('#text', text),
    querySelector: (selector) => body.querySelector(selector), querySelectorAll: (selector) => body.querySelectorAll(selector),
    addEventListener: () => {} };
  const calls = [], messages = [], modals = [], renders = [], windowEvents = {};
  const context = vm.createContext({ document, USER: structuredClone(profile), TOKEN: 'session', redirectingToLogin: false,
    sessionStorage: storage(), localStorage: storage(),
    location: { hash: '' }, history: { replaceState(_a, _b, hash) { context.location.hash = hash; } },
    window: { innerWidth: 1200, addEventListener(type, callback) { (windowEvents[type] ||= []).push(callback); } },
    setTimeout: () => 0, toast: (text, type) => messages.push({ text, type }),
    SITE: {}, iconSvg: () => '', slideFrom() {},
    refreshDicts() {}, refreshAll() { renders.push('refreshAll'); return Promise.resolve(); },
    loadSiteSettings: () => Promise.resolve(), renderLogin: (message) => renders.push(['login', message]),
    renderShell: () => renders.push('shell'),
  });
  function request(method, path, data) {
    const call = { method, path, data: data === undefined ? undefined : plain(data), settled: false };
    call.promise = new Promise((resolve, reject) => {
      call.resolve = (value) => { call.settled = true; resolve(value); };
      call.reject = (error) => { call.settled = true; reject(error); };
    });
    calls.push(call);
    return call.promise;
  }
  context.GET = (path) => request('GET', path);
  context.POST = (path, data) => request('POST', path, data);
  context.PUT = (path, data) => request('PUT', path, data);
  context.api = request;
  context.openModal = (options) => {
    const dialog = new Element('div');
    dialog.appendChild(options.body);
    const footer = new Element('footer');
    for (const button of options.footer || []) footer.appendChild(button);
    dialog.appendChild(footer); body.appendChild(dialog);
    const modal = { dialog, options, close() { dialog.remove(); if (options.onClose) options.onClose(); } };
    modals.push(modal);
    return modal;
  };
  vm.runInContext(functions, context, { timeout: 1000 });
  t.after(() => { assert.ok(calls.every((call) => call.settled), `unsettled mocked requests: ${calls.filter((c) => !c.settled).map((c) => c.path)}`); });
  return { context, body, host, calls, messages, modals, renders, windowEvents,
    run(code) { return vm.runInContext(code, context, { timeout: 1000 }); },
    install(name) { vm.runInContext(definition(app, name), context); },
    form(row = null, action) { context.openAccountForm(row, () => renders.push('reload'), action); return modals.at(-1); },
  };
}
function controls(modal) {
  const dialog = modal.dialog;
  return { field: (name) => dialog.querySelector(`[data-account-field="${name}"]`),
    departments: () => dialog.querySelectorAll('input[data-department-id]'),
    select(...ids) { for (const cb of this.departments()) cb.checked = ids.map(String).includes(cb.getAttribute('data-department-id')); },
    save: dialog.querySelector('footer').children[1], error: dialog.querySelector('.account-error'), form: dialog.querySelector('form') };
}
function validValues(extra = {}) { return { username: 'new.child', displayName: '新账号', password: 'secret', enabled: true, departmentIds: [3], ...extra }; }

test('profile: preserve all permission fields, copy arrays, unknown isAdmin never grants globals', (t) => {
  const h = harness(t);
  for (const role of [undefined, null, false, 1, 'true']) {
    const value = h.context.normalizeProfile({ ...user, isAdmin: role, token: 'must-not-persist' });
    assert.equal(value.isAdmin, false);
    assert.equal(Object.hasOwn(value, 'token'), false);
    h.context.USER = value;
    for (const route of ['settings', 'backup', 'log', 'report', 'dict']) assert.equal(h.context.canAccessView(route), false);
    for (const route of ['accounts', 'personnel', 'military', 'fitness', 'leave-plan', 'department']) assert.equal(h.context.canAccessView(route), true);
  }
  const profile = { ...user, departmentNames: ['单位'], isAdmin: true };
  const value = h.context.normalizeProfile(profile);
  assert.deepEqual(plain(value), profile);
  assert.notEqual(value.departmentIds, profile.departmentIds);
  h.context.USER = value;
  assert.equal(h.context.canAccessView('report'), true);
});

test('session /me replaces forged cached role, keeps remember mode, never persists token in profile', async (t) => {
  const h = harness(t, { ...user, isAdmin: true });
  h.context.localStorage.setItem('tk', 'session');
  const pending = h.context.refreshSessionProfile();
  assert.equal(h.context.USER, null, 'discard cached permissions before response');
  assert.equal(h.calls[0].path, '/api/auth/me');
  h.calls[0].resolve({ ...user, departmentNames: ['单位'] }); await pending;
  assert.equal(h.context.USER.isAdmin, false);
  assert.equal(h.context.USER.userId, 20);
  assert.deepEqual(plain(h.context.USER.departmentIds), [2]);
  assert.equal(h.context.localStorage.getItem('tk'), 'session');
  assert.equal(JSON.parse(h.context.sessionStorage.getItem('usr')).parentId, 1);
  assert.equal(Object.hasOwn(JSON.parse(h.context.localStorage.getItem('usr')), 'token'), false);
});

test('session refresh rejects changed token or malformed profile; no stale privilege assignment', async (t) => {
  const h = harness(t);
  const pending = h.context.refreshSessionProfile(); h.context.TOKEN = 'other';
  h.calls[0].resolve({ ...user, isAdmin: true });
  await assert.rejects(pending, /登录状态已变更/); assert.equal(h.context.USER, null);
  const malformed = h.context.refreshSessionProfile(); h.calls[1].resolve({ username: 'missing-id', isAdmin: true });
  await assert.rejects(malformed, /无法确认/); assert.equal(h.context.USER, null);
});

for (const failed of [false, true]) test(`boot waits for /me before cache/shell; failure=${failed}`, async (t) => {
  const h = harness(t, { ...user, isAdmin: true });
  const boot = app.slice(app.indexOf('(function boot()'));
  h.run(boot); await flush();
  assert.deepEqual(h.renders, []); assert.equal(h.calls[0].path, '/api/auth/me');
  if (failed) h.calls[0].reject(new Error('重启后令牌失效'));
  else h.calls[0].resolve(user);
  await flush();
  if (failed) { assert.equal(h.context.TOKEN, ''); assert.equal(h.context.USER, null); assert.equal(h.renders[0][0], 'login'); }
  else { assert.deepEqual(h.renders, ['refreshAll', 'shell']); assert.equal(h.context.USER.isAdmin, false); }
});

test('401 clears session even on login page and expires concurrency redirect guard', (t) => {
  const h = harness(t);
  const login = new Element('div'); login.className = 'login-wrap'; h.host.appendChild(login);
  let reset;
  h.context.setTimeout = (callback) => { reset = callback; };
  h.context.forceLogin('过期');
  assert.equal(h.context.USER, null); assert.equal(h.context.TOKEN, '');
  assert.equal(h.context.redirectingToLogin, true); assert.equal(h.messages.length, 1);
  reset(); assert.equal(h.context.redirectingToLogin, false);
});

test('login preserves profile, verifies /me, locks duplicate button/form/Enter submits', async (t) => {
  const h = harness(t, null); h.context.TOKEN = ''; h.install('renderLogin'); h.context.renderLogin();
  const fields = h.host.querySelectorAll('input');
  fields[0].value = 'child'; fields[1].value = 'secret';
  const button = h.host.querySelector('.login-btn'); button.dispatch('click');
  button.dispatch('click'); fields[1].dispatch('keydown', { key: 'Enter' });
  h.host.querySelector('form').dispatch('submit');
  assert.equal(h.calls.length, 1); assert.equal(h.calls[0].path, '/api/auth/login');
  h.calls[0].resolve({ ...user, token: 'new-session' }); await flush();
  assert.equal(h.calls[1].path, '/api/auth/me');
  const stored = JSON.parse(h.context.sessionStorage.getItem('usr'));
  assert.equal(stored.userId, 20); assert.equal(stored.parentId, 1); assert.equal(stored.isAdmin, false);
  assert.deepEqual(stored.departmentIds, [2]); assert.equal(stored.token, undefined);
  assert.deepEqual(h.renders, []);
  h.calls[1].resolve(user); await flush(); assert.deepEqual(h.renders, ['refreshAll', 'shell']);
});

test('login removes legacy plaintext password, remembers username only, and preserves password whitespace', async (t) => {
  const h = harness(t, null);
  h.context.localStorage.setItem('savedUser', 'remembered');
  h.context.localStorage.setItem('savedPass', 'legacy-secret');
  h.context.TOKEN = ''; h.install('renderLogin'); h.context.renderLogin();
  const fields = h.host.querySelectorAll('input');
  assert.equal(h.host.querySelector('.login-remember').textContent, '记住我');
  assert.equal(fields[1].getAttribute('type'), 'password');
  assert.equal(fields[1].value, '');
  assert.equal(h.context.localStorage.getItem('savedPass'), null);
  assert.equal(fields[0].value, 'remembered');
  fields[1].value = '  secret  ';
  h.host.querySelector('.login-btn').click();
  assert.equal(h.calls[0].data.password, '  secret  ');
  h.calls[0].resolve({ ...user, token: 'new-session' }); await flush();
  assert.equal(h.context.localStorage.getItem('savedPass'), null);
  assert.equal(h.context.localStorage.getItem('savedUser'), 'remembered');
  h.calls[1].resolve(user); await flush();
});

function shell(h, hash, lastView) {
  const c = h.context;
  for (const key of ['personnel', 'military', 'fitness', 'equipment', 'material', 'vehicle', 'leave', 'leavePlan', 'absence', 'phone']) c[`${key}Cfg`] = { key, title: key, icon: '' };
  for (const [name, key] of [['equipmentGunCfg', 'equipment-gun'], ['equipmentLargeCfg', 'equipment-large'], ['equipmentSmallCfg', 'equipment-small']]) c[name] = { key, title: key };
  for (const [name, key] of [['departmentView', 'department'], ['dictView', 'dict'], ['settingsView', 'settings'], ['dashboardView', 'stats'], ['backupView', 'backup'], ['logView', 'log'], ['accountsView', 'accounts']]) c[name] = (initialTab) => {
    if (key === 'settings') (h.settingsTabs ||= []).push(initialTab);
    return { render: () => h.renders.push(key) };
  };
  c.crudView = (cfg) => ({ render: () => h.renders.push(cfg.key) });
  c.openChangePassword = () => {};
  for (const name of ['itemLoanStatus', 'setupItemLoanForm', 'openItemLoanDetail', 'openItemLoanReturn']) h.install(name);
  h.run(app.slice(app.indexOf('var itemLoanCfg ='), app.indexOf('function pendingModuleView(')));
  // Route coverage isolates the shell; real double-excellence/annual DOM/form/transport coverage is in dedicated suites.
  c.doubleExcellenceView = () => ({ render: () => h.renders.push('double-excellence') });
  c.annualAppraisalView = () => ({ render: () => h.renders.push('annual-appraisal') });
  c.location.hash = hash;
  if (lastView) c.localStorage.setItem(c.preferenceKey('lastView'), lastView);
  h.run(app.slice(app.indexOf('var MODULES = [personnelCfg'), app.indexOf('function renderShell()')));
  h.install('pendingModuleView'); h.install('renderShell'); c.renderShell();
  return h.host.querySelector('.menu');
}
for (const route of ['settings', 'backup', 'log', 'report', 'dict']) test(`child route/menu: block #${route} on startup, history and lastView`, (t) => {
  const h = harness(t); const menu = shell(h, `#${route}`, route);
  assert.deepEqual(h.renders, ['stats']); assert.equal(h.context.location.hash, '#stats');
  const labels = menu.querySelectorAll('.txt').map((node) => node.textContent);
  assert.ok(labels.includes('账号管理')); assert.ok(labels.includes('单位管理'));
  for (const label of ['数据字典', '系统设置', '操作日志']) assert.ok(!labels.includes(label));
  h.context.location.hash = `#${route}`;
  for (const callback of h.windowEvents.hashchange) callback();
  assert.equal(h.renders.at(-1), 'stats'); assert.equal(h.context.location.hash, '#stats');
});

for (const admin of [false, true]) test(`personnel query route survives shell startup and same-view department/point navigation: admin=${admin}`, (t) => {
  const h = harness(t, { ...user, isAdmin: admin });
  shell(h, '#personnel?departmentId=22&includeDescendants=true');
  assert.equal(h.renders.at(-1), 'personnel'); assert.equal(h.context.location.hash, '#personnel?departmentId=22&includeDescendants=true');
  assert.equal(h.context.localStorage.getItem(h.context.preferenceKey('lastView')), 'personnel');
  for (const route of ['#personnel?departmentId=12&includeDescendants=true', '#personnel?locationPoint=A%26B', '#personnel']) {
    const before = h.renders.length; h.context.location.hash = route;
    for (const callback of h.windowEvents.hashchange) callback();
    assert.equal(h.renders.at(-1), 'personnel'); assert.equal(h.renders.length, before + 1); assert.equal(h.context.location.hash, route);
  }
});

test('accounts native route restores from lastView and menu; admin menus preserved', (t) => {
  const h = harness(t, { ...user, isAdmin: true }); const menu = shell(h, '', 'accounts');
  assert.deepEqual(h.renders, ['accounts']); assert.equal(h.context.location.hash, '#accounts');
  const labels = menu.querySelectorAll('.txt').map((node) => node.textContent);
  for (const label of ['账号管理', '系统设置', '数据字典', '操作日志']) assert.ok(labels.includes(label));
  const settings = menu.querySelectorAll('.menu-item').find((node) => node.getAttribute('data-tip') === '系统设置');
  settings.click(); assert.equal(h.renders.at(-1), 'settings');
});

for (const entry of ['hash', 'lastView', 'history']) test(`admin backup route explicitly requests backup tab from ${entry}`, (t) => {
  const h = harness(t, { ...user, isAdmin: true });
  h.context.localStorage.setItem('settingsTab', 'report');
  shell(h, entry === 'hash' ? '#backup' : entry === 'history' ? '#stats' : '', entry === 'lastView' ? 'backup' : undefined);
  if (entry === 'history') {
    h.context.location.hash = '#backup';
    for (const callback of h.windowEvents.hashchange) callback();
  }
  assert.equal(h.context.location.hash, '#backup');
  assert.equal(h.context.localStorage.getItem(h.context.preferenceKey('lastView')), 'backup');
  assert.equal(h.renders.at(-1), 'settings'); assert.deepEqual(h.settingsTabs, ['backup']);
});

for (const isAdmin of [false, true]) test(`annual-appraisal real menu route navigates without business requests: admin=${isAdmin}`, (t) => {
  const h = harness(t, { ...user, isAdmin });
  const menu = shell(h, '#stats');
  const item = menu.querySelectorAll('.menu-item').find((node) => node.getAttribute('data-tip') === '年度考评');
  assert.ok(item, '年度考评 menu exists');
  item.click();
  assert.equal(h.context.location.hash, '#annual-appraisal');
  assert.equal(h.context.localStorage.getItem(h.context.preferenceKey('lastView')), 'annual-appraisal');
  assert.equal(item.classList.contains('active'), true);
  assert.equal(h.host.querySelector('.topbar-title').textContent, '年度考评');
  assert.doesNotMatch(h.host.querySelector('.content').textContent, /具体功能待完善/);
  assert.ok(h.context.MODULES.some((cfg) => cfg.key === 'annual-appraisal'));
  assert.deepEqual(plain(h.context.PENDING_MODULES).map((cfg) => cfg.key), [], 'no module stays pending');
  assert.equal(h.calls.length, 0, 'stubbed view issues no business requests');
});

for (const restore of ['hash', 'lastView']) test(`annual-appraisal real route restores from ${restore}`, (t) => {
  const h = harness(t);
  shell(h, restore === 'hash' ? '#annual-appraisal' : '', restore === 'lastView' ? 'annual-appraisal' : undefined);
  assert.equal(h.context.location.hash, '#annual-appraisal');
  assert.equal(h.renders.at(-1), 'annual-appraisal');
  assert.doesNotMatch(h.host.querySelector('.content').textContent, /具体功能待完善/);
  assert.deepEqual(plain(h.context.PENDING_MODULES).map((cfg) => cfg.key), []);
});

for (const isAdmin of [false, true]) {
  for (const restore of ['hash', 'lastView', 'menu', 'history']) test(`double-excellence real view route restores from ${restore}: admin=${isAdmin}`, (t) => {
    const h = harness(t, { ...user, isAdmin });
    const menu = shell(h, restore === 'hash' ? '#double-excellence' : restore === 'lastView' ? '' : '#stats', restore === 'lastView' ? 'double-excellence' : undefined);
    const item = menu.querySelectorAll('.menu-item').find((node) => node.getAttribute('data-tip') === '双争评比');
    assert.ok(item);
    if (restore === 'menu') item.click();
    if (restore === 'history') { h.context.location.hash = '#double-excellence'; for (const callback of h.windowEvents.hashchange) callback(); }
    assert.equal(h.renders.at(-1), 'double-excellence');
    assert.equal(h.context.location.hash, '#double-excellence');
    assert.equal(h.context.localStorage.getItem(h.context.preferenceKey('lastView')), 'double-excellence');
    assert.equal(h.host.querySelector('.topbar-title').textContent, '双争评比');
    assert.equal(item.classList.contains('active'), true);
    assert.ok(h.context.MODULES.includes(h.context.doubleExcellenceCfg));
    assert.ok(!h.context.PENDING_MODULES.some((cfg) => cfg.key === 'double-excellence'));
    assert.deepEqual(plain(h.context.PENDING_MODULES).map((cfg) => cfg.key), []);
    assert.doesNotMatch(h.host.querySelector('.content').textContent, /具体功能待完善/);
  });
}

for (const restore of ['hash', 'lastView', 'menu']) test(`item-loans real ledger route restores from ${restore}`, (t) => {
  const h = harness(t);
  const menu = shell(h, restore === 'hash' ? '#item-loans' : restore === 'menu' ? '#stats' : '', restore === 'lastView' ? 'item-loans' : undefined);
  if (restore === 'menu') menu.querySelectorAll('.menu-item').find((node) => node.getAttribute('data-tip') === '物品借还').click();
  assert.equal(h.renders.at(-1), 'item-loans');
  assert.equal(h.context.location.hash, '#item-loans');
  assert.equal(h.context.itemLoanCfg.pending, undefined);
  assert.equal(h.context.itemLoanCfg.excel, false);
  const loanFields = h.context.itemLoanCfg.form.map((field) => field.key);
  assert.ok(!loanFields.includes('departmentId') && !loanFields.includes('ownerUserId'));
  assert.ok(h.context.MODULES.includes(h.context.itemLoanCfg));
  assert.ok(!h.context.PENDING_MODULES.some((cfg) => cfg.key === 'item-loans'));
  assert.doesNotMatch(h.host.querySelector('.content').textContent, /具体功能待完善/);
});

test('detached old shell listeners cannot navigate after logout or replacement', (t) => {
  const h = harness(t); const old = shell(h, '#personnel');
  h.host.innerHTML = ''; h.context.TOKEN = ''; h.context.USER = null;
  const count = h.renders.length;
  old.querySelectorAll('.menu-item').find((node) => node.getAttribute('data-tip') === '账号管理').dispatch('click');
  assert.equal(h.renders.length, count);
});

test('role wrappers hide only global toolbar actions; keep photo import and native business configs', (t) => {
  const h = harness(t); const c = h.context;
  let invoked = 0;
  const button = (text) => c.h('button', { text });
  c.personnelCfg = { toolbarExtra(bar) { bar.appendChild(button('删除撤回')); bar.appendChild(button('导入照片')); } };
  c.fitnessCfg = { toolbarExtra(bar) { bar.appendChild(button('体能标准')); bar.appendChild(button('评定标准')); } };
  c.militaryCfg = { toolbarExtra(bar) { bar.appendChild(button('考核方案设置')); } };
  for (const name of ['openPersonnelUndo', 'openFitnessStdManager', 'openGradeRuleManager', 'openBodyStdManager', 'importStdExcel']) c[name] = () => invoked++;
  h.run(definition(ext, 'installAccountRoleGuards', '  ')); c.installAccountRoleGuards();
  const bar = new Element('div'); c.personnelCfg.toolbarExtra(bar);
  assert.equal(bar.getAttribute('data-personnel-toolbar'), '1'); assert.equal(bar.textContent, '导入照片');
  c.fitnessCfg.toolbarExtra(bar); c.militaryCfg.toolbarExtra(bar); assert.equal(bar.textContent, '导入照片');
  c.openPersonnelUndo(); c.openFitnessStdManager(); c.importStdExcel(); assert.equal(invoked, 0);
  c.USER = { ...user, isAdmin: true };
  c.personnelCfg.toolbarExtra(bar); c.fitnessCfg.toolbarExtra(bar); c.militaryCfg.toolbarExtra(bar);
  for (const label of ['删除撤回', '体能标准', '评定标准', '考核方案设置']) assert.ok(bar.textContent.includes(label));
  c.openPersonnelUndo(); c.openFitnessStdManager(); c.importStdExcel(); assert.equal(invoked, 3);
});

test('effective tree remains grantable after ancestor shrinks scope and original root disappears', (t) => {
  const h = harness(t); const options = h.context.accountDepartmentOptions(tree);
  assert.deepEqual(plain(options).map((item) => item.id), [3, 4, 8]);
  assert.deepEqual(plain(options)[1].ancestors, [3]);
  const payload = h.context.accountPayload(validValues({ departmentIds: [4, '3', 8, 3] }), null, user, tree);
  assert.deepEqual(plain(payload.departmentIds), [3, 8]);
  assert.deepEqual(user.departmentIds, [2], 'raw profile roots must not be needed to grant effective subtree');
});

test('payload: whitelist only; no parent/admin/self manipulation; edit username immutable, blank password unchanged', (t) => {
  const h = harness(t);
  const result = h.context.accountPayload(validValues({ username: 'tampered', password: '', isAdmin: true, parentId: 999, userId: 1 }), child, user, tree);
  assert.deepEqual(plain(result), { username: child.username, displayName: '新账号', password: '', enabled: true, departmentIds: [3] });
  for (const row of [{ ...child, id: user.userId }, { ...child, id: user.parentId }, { ...child, parentId: user.parentId }, { ...child, parentId: 30 }]) {
    assert.equal(h.context.accountIsDirectChild(row, user), false);
    assert.throws(() => h.context.accountPayload(validValues(), row, user, tree), /直属子账号/);
  }
  assert.throws(() => h.context.accountPayload(validValues({ departmentIds: [2] }), null, user, tree), /不得超出/);
  assert.throws(() => h.context.accountPayload(validValues({ departmentIds: [0] }), null, user, tree), /不得超出/);
  assert.throws(() => h.context.accountPayload(validValues({ password: 'short' }), null, user, tree), /6至256/);
  assert.throws(() => h.context.accountPayload(validValues({ password: '' }), child, user, tree, true), /6至256/);
  assert.throws(() => h.context.accountPayload(validValues({ displayName: ' ' }), null, user, tree), /显示名称/);
  assert.throws(() => h.context.accountPayload(validValues({ username: 'bad name' }), null, user, tree), /账号须/);
  assert.deepEqual(plain(h.context.accountPayload(validValues({ departmentIds: [] }), child, user, [], false).departmentIds), []);
});

test('create form: effective checkbox roots, blank/short validation, repeated clicks lock, server errors visible and retryable', async (t) => {
  const h = harness(t); const modal = h.form(); const ui = controls(modal);
  assert.equal(ui.save.disabled, true); ui.save.dispatch('click'); assert.equal(h.calls.length, 1);
  h.calls[0].resolve(tree); await flush(); assert.equal(ui.save.disabled, false);
  ui.save.click(); assert.match(ui.error.textContent, /请输入账号/); assert.equal(h.calls.length, 1);
  ui.field('username').value = 'new.child'; ui.field('displayName').value = '新增账号'; ui.field('password').value = 'short';
  ui.save.click(); assert.match(ui.error.textContent, /6至256/);
  ui.field('password').value = 'secret'; ui.select(3, 4, 8);
  ui.save.dispatch('click'); ui.save.dispatch('click'); ui.form.dispatch('submit');
  assert.equal(h.calls.length, 2); assert.equal(ui.save.disabled, true);
  assert.deepEqual(h.calls[1].data, { username: 'new.child', displayName: '新增账号', password: 'secret', enabled: true, departmentIds: [3, 8] });
  assert.equal(h.calls[1].path, '/api/accounts'); assert.equal(h.calls[1].method, 'POST');
  h.calls[1].reject(new Error('账号已存在')); await flush();
  assert.equal(ui.error.textContent, '账号已存在'); assert.equal(ui.save.disabled, false); assert.ok(h.body.contains(modal.dialog));
  ui.save.click(); h.calls[2].resolve({}); await flush(); assert.deepEqual(h.renders, ['reload']); assert.equal(h.body.contains(modal.dialog), false);
});

for (const action of ['edit', 'reset', 'toggle']) test(`edit form ${action}: readonly username, encoded PUT, correct status/password`, async (t) => {
  const h = harness(t); const modal = h.form(child, action); const ui = controls(modal);
  assert.equal(ui.field('username').readOnly, true); h.calls[0].resolve(tree); await flush();
  ui.field('username').value = 'cannot-change';
  if (action === 'reset') {
    ui.save.click(); assert.match(ui.error.textContent, /6至256/); assert.equal(h.calls.length, 1);
    ui.field('password').value = 'newpass';
  }
  ui.save.click(); assert.equal(h.calls[1].method, 'PUT'); assert.equal(h.calls[1].path, '/api/accounts/30');
  assert.equal(h.calls[1].data.username, child.username);
  assert.equal(h.calls[1].data.password, action === 'reset' ? 'newpass' : '');
  assert.equal(h.calls[1].data.enabled, action !== 'toggle');
  h.calls[1].resolve({}); await flush();
});

test('out-of-effective-scope legacy grant stays visible and blocks save until explicitly unchecked', async (t) => {
  const h = harness(t); const modal = h.form({ ...child, departmentIds: [2], departmentNames: ['旧根'] }); const ui = controls(modal);
  h.calls[0].resolve(tree); await flush(); assert.ok(modal.dialog.textContent.includes('旧根(已不在本人范围,请取消)'));
  ui.save.click(); assert.match(ui.error.textContent, /不得超出/); assert.equal(h.calls.length, 1);
  ui.select(3); ui.save.click(); assert.deepEqual(h.calls[1].data.departmentIds, [3]);
  h.calls[1].resolve({}); await flush();
});

test('empty effective scope supports explicit zero grants and disabling a child', async (t) => {
  const h = harness(t); const modal = h.form({ ...child, departmentIds: [] }, 'toggle'); const ui = controls(modal);
  h.calls[0].resolve([]); await flush(); ui.save.click();
  assert.deepEqual(h.calls[1].data.departmentIds, []); assert.equal(h.calls[1].data.enabled, false);
  h.calls[1].resolve({}); await flush();
});

test('department load error stays in modal, save unavailable, retry without closing; session change cannot submit', async (t) => {
  const h = harness(t); const modal = h.form(child); const ui = controls(modal);
  h.calls[0].reject(new Error('单位加载失败')); await flush();
  assert.equal(ui.save.disabled, true); assert.match(ui.error.textContent, /单位加载失败/);
  modal.dialog.querySelector('.account-departments').querySelector('button').click();
  h.calls[1].resolve(tree); await flush(); h.context.TOKEN = 'other'; ui.save.dispatch('click');
  assert.match(ui.error.textContent, /登录状态已变更/); assert.equal(h.calls.length, 2);
});

test('accounts table exposes only direct children, no delete, list error and refresh; stale requests cannot replace latest', async (t) => {
  const h = harness(t); h.context.renderAccountsPage(h.host);
  assert.equal(h.calls[0].path, '/api/accounts');
  h.calls[0].resolve([child, { ...child, id: 20 }, { ...child, id: 1 }, { ...child, id: 40, parentId: 1 }]); await flush();
  const table = h.host.querySelector('table'); assert.equal(table.querySelector('tbody').children.length, 1);
  const actions = table.querySelectorAll('button').map((node) => node.textContent);
  assert.deepEqual(actions, ['编辑', '重置密码', '停用', '授权预览']); assert.ok(table.textContent.includes('有效单位'));
  const refresh = h.host.querySelector('.toolbar').querySelector('button');
  refresh.dispatch('click'); refresh.dispatch('click');
  h.calls[2].resolve([{ ...child, username: 'latest' }]); await flush();
  h.calls[1].reject(new Error('stale error')); await flush();
  assert.ok(table.textContent.includes('latest')); assert.equal(h.host.querySelector('.account-error').textContent, '');
  refresh.click(); h.calls[3].reject(new Error('列表失败')); await flush();
  assert.equal(h.host.querySelector('.account-error').textContent, '列表失败'); assert.equal(refresh.disabled, false);
});

test('child department editing locks parent and ignores tampered parent payload; creation requires an in-scope parent', async (t) => {
  const h = harness(t); const c = h.context;
  c.Ctx = { deptFlat: [{ id: 3, name: '有效单位' }] };
  const forms = [];
  c.buildForm = (fields, values) => {
    const form = { fields, values, data: { ...values }, el: new Element('div'), error: '',
      get() { return { ...this.data }; }, setError(message) { this.error = message; } };
    forms.push(form); return form;
  };
  h.run(definition(definition(app, 'departmentView'), 'openForm', '  '));
  c.openForm({ id: 3, name: '有效单位', parentId: 0 });
  assert.equal(forms[0].fields.find((field) => field.key === 'parentId').type, 'hidden');
  forms[0].data.parentId = 99;
  h.modals[0].options.footer[1].click();
  assert.equal(h.calls[0].path, '/api/departments/3'); assert.equal(h.calls[0].data.parentId, 0);
  h.calls[0].reject(new Error('模拟拒绝')); await flush();
  c.openForm(null, null);
  const create = forms[1];
  assert.equal(create.fields.find((field) => field.key === 'parentId').required, true);
  create.data = { name: '新单位', parentId: 99 };
  h.modals[1].options.footer[1].click(); assert.match(create.error, /本人授权范围/); assert.equal(h.calls.length, 1);
  create.data.parentId = 3; h.modals[1].options.footer[1].click();
  assert.equal(h.calls[1].path, '/api/departments'); assert.equal(h.calls[1].data.parentId, 3);
  h.calls[1].reject(new Error('模拟拒绝')); await flush();
});

test('account panel: summary counts only direct children, resets on refresh and keeps the table header when empty', async (t) => {
  const h = harness(t); h.context.renderAccountsPage(h.host);
  const counts = () => h.host.querySelectorAll('.account-metric').map((node) => node.querySelector('strong').textContent);
  assert.equal(h.host.querySelector('h1'), null, 'do not render the removed child-account page title');
  assert.deepEqual(counts(), ['—', '—', '—']);
  h.calls[0].resolve([child, { ...child, id: 31, enabled: false }, { ...child, id: 40, parentId: 1 }]); await flush();
  assert.deepEqual(counts(), ['2', '1', '1']);
  assert.equal(h.host.querySelector('.account-list-count').textContent, '共 2 个账号');
  h.host.querySelector('.toolbar').querySelector('button').click();
  assert.deepEqual(counts(), ['—', '—', '—']);
  h.calls[1].resolve([]); await flush();
  assert.deepEqual(counts(), ['0', '0', '0']);
  assert.equal(h.host.querySelectorAll('th').length, 5);
  assert.match(h.host.querySelector('.account-empty').textContent, /暂无直属子账号/);
  assert.equal(h.host.querySelectorAll('.account-avatar').length, 0);
});

test('account panel: labels remain text, scopes use chips, request failures do not masquerade as an empty list', async (t) => {
  const h = harness(t); h.context.renderAccountsPage(h.host);
  h.calls[0].resolve([{ ...child, username: '<script>bad</script>', departmentNames: ['<img src=x>', '单位二'] }]); await flush();
  assert.equal(h.host.querySelector('.account-username').textContent, '<script>bad</script>');
  assert.deepEqual(h.host.querySelectorAll('.account-scope-chip').map((node) => node.textContent), ['<img src=x>', '单位二']);
  assert.equal(h.host.querySelector('.account-status').classList.contains('is-active'), true);
  h.host.querySelector('.toolbar').querySelector('button').click();
  h.calls[1].reject(new Error('网络异常')); await flush();
  assert.equal(h.host.querySelector('.account-list-count').textContent, '加载失败');
  assert.equal(h.host.querySelector('.account-empty'), null);
  assert.equal(h.host.querySelectorAll('th').length, 5);
  assert.equal(h.host.querySelector('.account-error').textContent, '网络异常');
});

test('account modal: grouped fields retain accessible labels and selection count follows checkbox changes', async (t) => {
  const h = harness(t); const modal = h.form(child); const ui = controls(modal);
  h.calls[0].resolve(tree); await flush();
  assert.equal(modal.dialog.querySelectorAll('.account-form-section').length, 2);
  assert.equal(ui.field('username').getAttribute('aria-label'), '账号');
  assert.equal(ui.field('password').getAttribute('aria-label'), '新密码');
  assert.equal(modal.dialog.querySelector('.account-selection').textContent, '已选 1 个单位');
  const options = ui.departments();
  assert.equal(options[0].parentNode.classList.contains('selected'), true);
  options[1].checked = true; options[1].dispatch('change');
  assert.equal(modal.dialog.querySelector('.account-selection').textContent, '已选 2 个单位');
  assert.equal(options[1].parentNode.classList.contains('selected'), true);
  options[0].checked = false; options[0].dispatch('change');
  assert.equal(modal.dialog.querySelector('.account-selection').textContent, '已选 1 个单位');
  assert.equal(options[0].parentNode.classList.contains('selected'), false);
});

test('resources keep at least the current versions; production additions stay ES5 without external dependencies', () => {
  const html = readFileSync(resolve(base, 'index.html'), 'utf8');
  const version = (pattern, label) => { const match = html.match(pattern); assert.ok(match, `${label} versioned`); return Number(match[1]); };
  assert.ok(version(/app\.js\?v=(\d+)/, 'app.js') >= 316);
  assert.ok(version(/ext-features\.js\?v=(\d+)/, 'ext-features.js') >= 82);
  assert.ok(version(/app\.css\?v=(\d+)/, 'app.css') >= 259);
  const addition = ext.slice(ext.indexOf('  function accountIsDirectChild'), ext.indexOf('  /* ================= 字典下拉'));
  assert.doesNotMatch(addition, /\b(?:let|const|class|async|await)\s|=>|\.includes\(|\.finally\(|fetch\(/);
});
