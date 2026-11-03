'use strict';
// Actual production functions only; no bootstrap, browser, HTTP or business data.
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const source = readFileSync(resolve(__dirname, '../../main/resources/static/app.js'), 'utf8');
function definition(name) {
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = source.match(new RegExp(`^function ${escaped}\\([^\\n]*\\) \\{[^\\n]*\\}$`, 'm'))
    || source.match(new RegExp(`^function ${escaped}\\([^\\n]*\\) \\{[\\s\\S]*?^\\}`, 'm'));
  assert.ok(match, `production function ${name}`);
  return match[0];
}
const names = ['$', '$all', 'h', 'esc', 'openModal', 'parseCalendarDay', 'formatCalendarDay',
  'makeDateField', 'buildForm', 'setupPersonLookup', 'setupLeaveAutoCalc', 'saveSubRecordBatch',
  'crudView', 'buildTrendChart', 'preferenceKey', 'importRuleText', 'importFileErrorMessage', 'importResultSummary', 'openImportResultModal', 'dashMigrate', 'markDashDriverLayout', 'dashDriverLayout', 'dashboardView', 'openChangePassword', 'subRecordsPanel',
  'makeDropdown', 'itemLoanStatus', 'setupItemLoanForm', 'openItemLoanDetail', 'openItemLoanReturn', 'fmtSize', 'backupView',
  'dictItems', 'dictView', 'settingsView', 'readPersonnelRouteFilters', 'kindsForDim', 'dashCardLabel',
  'departmentDepthColor', 'drawDepartmentHierarchy', 'dashBarList', 'openDrillModal', 'openPersonPicker', 'openDetailModal', 'statusOptions', 'badge', 'driverLicenseOptions',
  'drawDonut', 'drawColumns', 'drawLine', 'drawRose', 'drawTreemap', 'drawGauge',
  'attachEasterEgg', 'prefersReducedMotion', 'countUp', 'skeletonRows'];

test('easter egg fires on five rapid brand clicks only and rearms', () => {
  const h = harness();
  let fired = 0;
  h.c.attachEasterEgg(h.host, () => fired++);
  for (let i = 0; i < 4; i++) { h.host.dispatch('click'); }
  assert.equal(fired, 0, 'four clicks must not fire the egg');
  h.host.dispatch('click');
  assert.equal(fired, 1, 'the fifth rapid click fires the egg');
  for (let i = 0; i < 5; i++) { h.host.dispatch('click'); }
  assert.equal(fired, 2, 'the counter rearms after firing');
  assert.equal(h.host.style.cursor, 'pointer', 'brand area signals interactivity');
});
// 动效令牌与函数同源提取:crudView 的面板滑行/骨架屏、dashboardView 的数字滚动按名引用 MOTION
const motion = source.match(/^var MOTION = \{[\s\S]*?^\};$/m);
assert.ok(motion, 'production MOTION exists');
const script = new vm.Script(motion[0] + '\n' + names.map(definition).join('\n'));
const flush = () => new Promise((done) => setImmediate(done));
const plain = (value) => JSON.parse(JSON.stringify(value));
const decode = (value) => value.replace(/&(amp|lt|gt|quot|#39);/g, (_, key) => ({ amp: '&', lt: '<', gt: '>', quot: '"', '#39': "'" })[key]);
class Element {
  constructor(tag, doc, text = '') {
    this.tagName = tag.toUpperCase(); this.ownerDocument = doc; this.nodeType = tag === '#text' ? 3 : 1;
    this._nodes = []; this._text = text; this._html = ''; this.attributes = {}; this.listeners = {};
    this.style = {}; this.value = ''; this.checked = false; this.disabled = false; this.readOnly = false;
    this.classList = {
      contains: (key) => this.className.split(/\s+/).includes(key),
      add: (...keys) => { for (const key of keys) if (!this.classList.contains(key)) this.className = `${this.className} ${key}`.trim(); },
      remove: (...keys) => { this.className = this.className.split(/\s+/).filter((key) => !keys.includes(key)).join(' '); },
      toggle: (key, force) => { const add = force === undefined ? !this.classList.contains(key) : !!force; this.classList[add ? 'add' : 'remove'](key); },
    };
  }
  get children() { return this._nodes.filter((node) => node.nodeType === 1); }
  get firstChild() { return this._nodes[0] || null; }
  get nextSibling() { return this.parentNode?._nodes[this.parentNode._nodes.indexOf(this) + 1] || null; }
  get parentElement() { return this.parentNode; }
  get className() { return this.attributes.class || ''; }
  set className(value) { this.attributes.class = value; }
  setAttribute(key, value) {
    this.attributes[key] = String(value);
    if (['value', 'type', 'id'].includes(key)) this[key] = String(value);
    if (key === 'disabled' || key === 'checked') this[key] = true;
  }
  getAttribute(key) { return Object.hasOwn(this.attributes, key) ? this.attributes[key] : null; }
  appendChild(node) { node.remove(); this._nodes.push(node); node.parentNode = this; return node; }
  insertBefore(node, reference) {
    if (!reference) return this.appendChild(node);
    assert.ok(this._nodes.includes(reference)); node.remove(); this._nodes.splice(this._nodes.indexOf(reference), 0, node); node.parentNode = this; return node;
  }
  replaceChild(next, old) { const at = this._nodes.indexOf(old); next.remove(); this._nodes[at] = next; next.parentNode = this; old.parentNode = null; }
  remove() { if (this.parentNode) { const nodes = this.parentNode._nodes; nodes.splice(nodes.indexOf(this), 1); this.parentNode = null; } }
  contains(node) { return node === this || this._nodes.some((child) => child.contains(node)); }
  clear() { for (const node of this._nodes) node.parentNode = null; this._nodes = []; this._text = ''; this._html = ''; }
  get textContent() { return this._text + this._nodes.map((node) => node.textContent).join(''); }
  set textContent(value) { this.clear(); this._text = String(value); }
  get innerHTML() { return this._html; }
  set innerHTML(value) {
    this.clear(); this._html = String(value); const stack = [this];
    for (const token of this._html.match(/<[^>]*>|[^<]+/g) || []) {
      if (!token.startsWith('<')) { stack.at(-1)._text += decode(token); continue; }
      if (token.startsWith('</')) { assert.ok(stack.length > 1, token); stack.pop(); continue; }
      const tag = /^<([\w-]+)\b([\s\S]*?)\/?\s*>$/.exec(token); assert.ok(tag, token);
      const node = new Element(tag[1], this.ownerDocument);
      let attrs = tag[2].trim();
      while (attrs) {
        const attr = /^([\w:-]+)(?:="([^"]*)")?(?:\s+|$)/.exec(attrs); assert.ok(attr, attrs);
        node.setAttribute(attr[1], decode(attr[2] || '')); attrs = attrs.slice(attr[0].length);
      }
      stack.at(-1).appendChild(node);
      if (!/^(input|img|br|hr|meta|link)$/.test(tag[1]) && !token.endsWith('/>')) stack.push(node);
    }
    assert.equal(stack.length, 1, 'balanced production HTML');
  }
  simpleMatches(selector) {
    const bare = selector.replace(/\[[^\]]*\]/g, ''); // Dots in attribute values (e.g. accept=".zip") are not classes.
    const tag = /^([a-z][\w-]*)/i.exec(bare);
    if (tag && this.tagName !== tag[1].toUpperCase()) return false;
    const id = /#([\w-]+)/.exec(bare);
    if (id && this.id !== id[1]) return false;
    for (const match of bare.matchAll(/\.([\w-]+)/g)) if (!this.classList.contains(match[1])) return false;
    for (const match of selector.matchAll(/\[([\w-]+)(?:="([^"]*)")?\]/g)) {
      const attr = this.getAttribute(match[1]); if (attr === null || (match[2] !== undefined && attr !== match[2])) return false;
    }
    return true;
  }
  matches(selector) {
    return selector.split(',').some((item) => {
      const parts = item.trim().split(/\s+(?=(?:[^"]*"[^"]*")*[^"]*$)/); let el = this;
      if (!el.simpleMatches(parts.pop())) return false;
      while (parts.length) { const part = parts.pop(); el = el.parentNode; while (el && !el.simpleMatches(part)) el = el.parentNode; if (!el) return false; }
      return true;
    });
  }
  querySelectorAll(selector) {
    const result = []; for (const child of this.children) { if (child.matches(selector)) result.push(child); result.push(...child.querySelectorAll(selector)); } return result;
  }
  querySelector(selector) { return this.querySelectorAll(selector)[0] || null; }
  closest(selector) { return this.matches(selector) ? this : this.parentNode?.closest(selector) || null; }
  addEventListener(type, callback) { (this.listeners[type] ||= []).push(callback); }
  removeEventListener(type, callback) { this.listeners[type] = (this.listeners[type] || []).filter((fn) => fn !== callback); }
  dispatch(type, extra = {}) {
    let stopped = false;
    const event = { type, target: this, preventDefault() { this.defaultPrevented = true; }, stopPropagation() { stopped = true; }, ...extra };
    for (let el = this; el; el = el.parentNode) { for (const callback of el.listeners[type] || []) callback.call(el, event); if (stopped) break; }
    return event;
  }
  dispatchEvent(event) { return this.dispatch(event.type, event); }
  click() { if (!this.disabled) this.dispatch('click'); }
  focus() { this.ownerDocument.activeElement = this; }
  blur() { this.dispatch('blur'); }
  scrollIntoView() {}
  scrollTo() {}
  getClientRects() { return this.style.display === 'none' ? [] : [{}]; }
  getBoundingClientRect() { return { left: 100, top: 100, bottom: 120 }; }
}
function storage() {
  const data = {};
  return { data, getItem: (key) => Object.hasOwn(data, key) ? data[key] : null,
    setItem: (key, value) => { data[key] = String(value); }, removeItem: (key) => { delete data[key]; } };
}
class Multipart {
  constructor() { this.fields = []; }
  append(key, value) { this.fields.push([key, value]); }
  get(key) { return this.fields.find(([name]) => name === key)?.[1] || null; }
}
function harness(options = {}) {
  const document = {}; document.body = new Element('body', document);
  Object.assign(document, { createElement: (tag) => new Element(tag, document), createTextNode: (text) => new Element('#text', document, text),
    querySelector: (sel) => document.body.querySelector(sel), querySelectorAll: (sel) => document.body.querySelectorAll(sel), contains: (el) => document.body.contains(el) });
  const calls = [], toasts = [], confirmations = [], observers = [], renders = [];
  function request(method, path, data) {
    const call = { method, path, data: data instanceof Multipart ? data : data === undefined ? undefined : plain(data) };
    call.promise = new Promise((resolve, reject) => { call.resolve = resolve; call.reject = reject; }); calls.push(call); return call.promise;
  }
  const c = vm.createContext({ document, window: { innerWidth: 1280, innerHeight: 900 },
    setTimeout: () => 0, clearTimeout: () => {}, // 彩蛋连击计时等生产代码所需,测试中不真正等待
    localStorage: storage(), sessionStorage: storage(), USER: { userId: 7 }, TOKEN: 'token-7', SITE: { pageSize: 2 },
    FormData: Multipart, redirectingToLogin: false,
    refreshAll: () => { renders.push('refreshAll'); return Promise.resolve(); },
    renderLogin: (message) => renders.push(['login', message]),
    dictLabels: () => [], Ctx: { deptFlat: [], personnelOptions: [] }, TREND_COLORS: ['#123'], DASH_DEFAULT: [], DASH_DIMS: {}, DASH_OLD_WIDGET: {}, subUid: 0,
    setTimeout: () => 0, clearTimeout() {}, iconSvg: () => '', showFieldBubble() {}, hideFieldBubble() {},
    toast: (text, type) => toasts.push({ text, type }), confirm: () => false,
    confirmBox: (message) => { const call = {}; call.promise = new Promise((resolve) => { call.resolve = resolve; }); confirmations.push({ message, ...call }); return call.promise; },
    GET: (path) => request('GET', path), POST: (path, data) => request('POST', path, data), PUT: (path, data) => request('PUT', path, data), DEL: (path) => request('DEL', path),
    refreshDepts: () => Promise.resolve(), refreshPersonnelOptions: () => Promise.resolve(), slideFrom() {},
    downloadBlob() {}, showProgressModal: () => ({ setStatus() {}, setProgress() {}, close() {} }),
    apiBlobWithProgress: (path) => request('BLOB', path),
    IntersectionObserver: function (callback) { observers.push(callback); this.observe = () => {}; },
    CustomEvent: function (type) { this.type = type; }, Event: function (type) { this.type = type; },
  });
  script.runInContext(c);
  if (options.realApi) {
    // Run the production multipart/token and login helpers, not a substitute upload implementation.
    c.fetch = (path, init) => {
      const data = init.body instanceof Multipart ? init.body : init.body === undefined ? undefined : JSON.parse(init.body);
      const result = request(init.method, path, data);
      Object.assign(calls.at(-1), { headers: plain(init.headers), body: init.body });
      return result.then((data) => ({ ok: true, status: 200, json: () => Promise.resolve({ code: 0, data }) }));
    };
    vm.runInContext(['api', 'GET', 'POST', 'clearSession', 'forceLogin'].map(definition).join('\n'), c);
  }
  c.attachPersonPick = (_el, callback) => { c.pick = callback; };
  const host = c.h('main', { class: 'content' }); document.body.appendChild(host);
  return { c, calls, toasts, confirmations, observers, renders, document, host,
    queue(method) { return calls.filter((call) => call.method === method); },
    modal() { return document.body.querySelectorAll('.dialog').at(-1); },
    button(root, text) { const button = root.querySelectorAll('button').find((el) => el.textContent === text); assert.ok(button, text); return button; },
    form(fields, values = {}) { const form = c.buildForm(fields, values); host.appendChild(form.el); return form; },
    page(extra = {}) {
      const cfg = { key: 'test', title: '测试管理', endpoint: '/stub/items', form: [{ key: 'name', label: '姓名' }],
        columns: [{ key: 'name', label: '姓名' }], filters: [{ key: 'name', label: '姓名' }], ...extra };
      c.crudView(cfg).render(host); return cfg;
    },
    query() { this.button(host.querySelector('.filter-actions'), '查询').click(); },
    choose(id, checked = true) { const cb = host.querySelectorAll('.cb-row').find((el) => el.getAttribute('data-id') === String(id)); assert.ok(cb); cb.checked = checked; cb.dispatch('change'); },
    rows() { return host.querySelectorAll('.cb-row').map((el) => el.getAttribute('data-id')); },
  };
}
const rows = (...ids) => ({ list: ids.map((id) => ({ id, name: `人员${id}` })), total: ids.length });

test('import file parse errors explain the fix without counting a fake failed row', () => {
  const h = harness();
  const raw = '文件解析失败：Your InputStream was neither an OLE2 stream, nor an OOXML stream';
  const summary = h.c.importResultSummary({ error: raw }, true);
  assert.match(summary, /无法读取导入文件/);
  assert.match(summary, /未导入任何人员/);
  assert.match(summary, /系统提供的导入模板/);
  assert.match(h.c.importResultSummary({ error: raw }), /未导入任何数据/);
  h.c.openImportResultModal(summary, [], raw);
  const text = h.modal().textContent;
  assert.doesNotMatch(text, /以下 \d+ 条记录未导入/);
  assert.match(text, /技术详情/);
  assert.match(text, /Your InputStream/);
});

test('row-level import errors retain a count of records not imported', () => {
  const h = harness();
  h.c.openImportResultModal('导入完成', ['第 3 行：姓名不能为空']);
  assert.match(h.modal().textContent, /以下 1 条记录未导入/);
  assert.match(h.modal().textContent, /第 3 行：姓名不能为空/);
});

test('leave management exposes optional leave location for entry and display', () => {
  const match = /var leaveCfg = \{([\s\S]*?)\n\};/.exec(source);
  assert.ok(match, 'leave configuration exists');
  const fields = match[1];
  assert.equal((fields.match(/key: 'leaveLocation'/g) || []).length, 2);
  const columns = fields.slice(fields.indexOf('columns: ['), fields.indexOf('rowTitle:'));
  assert.ok(columns.indexOf("key: 'leaveLocation'") > columns.indexOf("key: 'reason'"), 'location column follows reason');
  assert.match(fields, /key: 'leaveLocation', label: '休假地点', placeholder: '如：北京市', span: 'full'/);
  assert.match(fields, /key: 'leaveLocation', label: '休假地点', render: function \(r\) \{ return esc\(r\.leaveLocation \|\| '—'\); \}/);
  const form = fields.slice(fields.indexOf('form: ['));
  assert.ok(form.indexOf("{ key: 'leaveLocation'") > form.indexOf("{ key: 'reason'"), 'location follows reason');
  assert.match(form, /\{ key: 'reason', label: '事由' \}/, 'reason uses a standard-width field');
});

test('vehicle management exposes inline fault history rows and binds them to the vehicle endpoint', async () => {
  const cfg = /var VEHICLE_FAULT_SUB = \{([\s\S]*?)\n\};/.exec(source);
  assert.ok(cfg, 'vehicle fault row configuration exists');
  assert.match(cfg[1], /key: 'faultDate', label: '故障时间', type: 'date', required: true/);
  assert.match(cfg[1], /key: 'description', label: '故障情况', required: true/);
  assert.match(source, /subRecordsPanel\('vehicle-fault', row && row\.id, VEHICLE_FAULT_SUB\)/);
  assert.match(source, /stagedMeta: \{ 'vehicle-fault': \{ endpoint: VEHICLE_FAULT_SUB\.endpoint, parentKey: VEHICLE_FAULT_SUB\.parentKey \} \}/);

  const h = harness();
  const panel = h.c.subRecordsPanel('vehicle-fault', 42, {
    name: '历史故障', parentKey: 'vehicleId', endpoint: (id) => `/api/vehicle/${id}/fault-history`,
    columns: [{ key: 'faultDate', label: '故障时间', type: 'date', required: true }, { key: 'description', label: '故障情况', required: true }],
  });
  assert.equal(h.calls[0].path, '/api/vehicle/42/fault-history');
  h.calls[0].resolve([]);
  await flush();
  h.button(panel, '添加历史故障').click();
  assert.equal(panel.__staged().length, 1);

  const saved = h.c.saveSubRecordBatch([{
    endpoint: (id) => `/api/vehicle/${id}/fault-history`, parentKey: 'vehicleId',
    record: { faultDate: '2026-10-17', description: '发动机异响' },
  }], 42);
  await flush();
  assert.equal(h.calls[1].path, '/api/vehicle/42/fault-history');
  assert.deepEqual(h.calls[1].data, { faultDate: '2026-10-17', description: '发动机异响', vehicleId: 42 });
  h.calls[1].resolve({ id: 1 });
  assert.equal((await saved).success, 1);
});

for (const row of [null, { id: 42, plateNo: '测试车辆' }]) test(`vehicle ${row ? 'edit' : 'create'} form mounts the actual fault-history panel and supports adding a row`, async () => {
  const h = harness();
  h.c.dictLabels = () => [];
  h.c.makeDropdown = () => h.c.h('input', { type: 'text' });
  vm.runInContext(source.slice(source.indexOf('var VEHICLE_FAULT_SUB ='), source.indexOf('/** 节假日配色:')), h.c);
  const cfg = h.c.vehicleCfg;
  const extra = cfg.subPanels(row);
  const form = h.c.buildForm(cfg.form, row || {}, extra.panels);
  h.host.appendChild(form.el);
  if (row) {
    assert.equal(h.calls[0].path, '/api/vehicle/42/fault-history');
    h.calls[0].resolve([]);
    await flush();
  } else {
    assert.equal(h.calls.length, 0, 'new vehicle history remains local');
  }
  const tabs = form.el.querySelectorAll('.tab');
  assert.deepEqual(tabs.map((tab) => tab.textContent), ['基本信息', '历史故障']);
  const panel = extra.panels['历史故障'];
  assert.equal(form.el.contains(panel), true, 'fault panel is mounted inside the actual form');
  tabs[1].click();
  assert.equal(panel.parentNode.classList.contains('active'), true);
  assert.equal(form.el.querySelectorAll('.tab-panel')[0].classList.contains('active'), false);
  h.button(panel, '添加历史故障').click();
  assert.equal(panel.__staged().length, 1);
  assert.match(panel.textContent, /故障时间/);
  assert.match(panel.textContent, /故障情况/);
  h.button(panel, '删除').click();
  assert.equal(panel.__staged().length, 0);
  assert.equal(h.queue('POST').length, 0, 'blank row never saves by itself');
});

test('strict calendar parser validates leap days and low years, never rolls invalid input', () => {
  const { c } = harness();
  for (const value of ['2023-02-29', '2026-04-31', '2026-13-01', '2026-00-01', '2026-01-00', '0000-01-01', 'garbage', '2026-01']) assert.equal(c.parseCalendarDay(value), null, value);
  for (const [value, expected] of [['2024-2-29', '2024-02-29'], ['20260228', '2026-02-28'], ['0001-01-01', '0001-01-01']]) assert.equal(c.formatCalendarDay(c.parseCalendarDay(value)), expected);
});

for (const value of ['2026-02-30', 'not-a-date', '2026-02']) test(`optional date rejects raw invalid input: ${value}`, () => {
  const h = harness(); const form = h.form([{ key: 'date', label: '可选日期', type: 'date' }]);
  form.inputs.date.querySelector('input').value = value;
  assert.equal(form.get(), null); assert.match(form.el.textContent, /请检查日期/);
  form.inputs.date.querySelector('input').value = ''; assert.deepEqual(plain(form.get()), { date: '' });
  form.inputs.date.value = value; assert.equal(form.get(), null, 'invalid setter must not silently clear the source value');
  form.inputs.date.value = '2024-2-29'; assert.deepEqual(plain(form.get()), { date: '2024-02-29' });
});

test('buildForm creates actual password inputs and preserves leading/trailing spaces', () => {
  const h = harness(); const form = h.form([{ key: 'password', label: '密码', type: 'password', required: true }, { key: 'name', label: '姓名' }]);
  assert.equal(form.inputs.password.type, 'password'); form.inputs.password.value = '  secret  '; form.inputs.name.value = ' 名字 ';
  assert.deepEqual(plain(form.get()), { password: '  secret  ', name: '名字' });
});

for (const list of [false, true]) test(`person lookup ${list ? 'leave' : 'generic'} clears immediately and rejects stale/pending/tampered matches`, async () => {
  const h = harness(); const form = h.form([{ key: 'card', label: '身份证' }, { key: 'name', label: '姓名' }, { key: 'pid', type: 'hidden' }], { card: 'old', name: '旧人', pid: 1 });
  h.c.setupPersonLookup(form, { idCard: 'card', name: 'name', pid: 'pid' }, list ? { endpoint: '/api/leave/find-person', list: true } : {});
  assert.equal(form.inputs.pid.value, ''); assert.equal(form.get(), null);
  form.inputs.card.value = 'next'; form.inputs.card.dispatch('input'); form.inputs.card.dispatch('change');
  h.calls[1].resolve(list ? [{ id: 2, name: '新', idCard: 'next' }] : { id: 2, name: '新', idCard: 'next' }); await flush();
  assert.equal(form.get().pid, '2');
  h.calls[0].resolve(list ? [{ id: 1, name: '旧', idCard: 'old' }] : { id: 1, name: '旧', idCard: 'old' }); await flush();
  assert.equal(form.inputs.pid.value, '2');
  form.inputs.card.value = 'programmatic-no-event'; assert.equal(form.get(), null, 'save checks the current input snapshot');
  form.inputs.card.value = 'next'; form.inputs.pid.value = '999'; assert.equal(form.get(), null, 'hidden PID tampering cannot pass');
  form.inputs.card.value = 'failure'; form.inputs.card.dispatch('change'); h.calls[2].reject(new Error('offline')); await flush();
  assert.equal(form.inputs.pid.value, ''); assert.equal(form.get(), null); assert.match(form.el.textContent, /尚未匹配/);
  h.c.pick({ id: 3, idCard: 'chosen', name: '选择' }); assert.equal(form.get().pid, '3');
});

test('picker invalidates an in-flight lookup, including a same-input ABA response', async () => {
  const h = harness(); const form = h.form([{ key: 'card' }, { key: 'name' }, { key: 'pid', type: 'hidden' }]);
  h.c.setupPersonLookup(form, { idCard: 'card', name: 'name', pid: 'pid' });
  form.inputs.card.value = 'A'; form.inputs.card.dispatch('change');
  form.inputs.card.value = 'B'; form.inputs.card.dispatch('input');
  h.c.pick({ id: 9, idCard: 'A', name: '已选' });
  h.calls[0].resolve({ id: 1, idCard: 'A', name: '过期' }); await flush(); assert.equal(form.get().pid, '9');
});

function leaveForm(h) {
  return h.form(['idCard', 'personnelName', 'personnelId', 'year', 'startDate', 'endDate', 'days', 'travelDays', 'holidayDays', 'totalDays'].map((key) => ({ key, label: key, type: key.endsWith('Date') ? 'date' : key === 'personnelId' ? 'hidden' : 'text' })));
}
test('leave calculation clears stale end dates, rejects overflow dates and handles leap/fractional days without padStart', () => {
  const h = harness(); const form = leaveForm(h); h.c.setupLeaveAutoCalc(form, null);
  form.inputs.days.value = '1.5'; form.inputs.startDate.value = '2024-02-28'; form.inputs.startDate.dispatch('change');
  assert.equal(form.inputs.endDate.value, '2024-02-29');
  for (const value of ['', '2023-02-29', '2026-02']) { form.inputs.startDate.value = value; form.inputs.startDate.dispatch('change'); assert.equal(form.inputs.endDate.value, ''); }
});

test('leave travel/reference responses cannot restore a previous person after identity input clears', async () => {
  const h = harness(); const form = leaveForm(h); form.inputs.year.value = '2026'; h.c.setupLeaveAutoCalc(form, null);
  h.c.pick({ id: 8, idCard: 'eight', name: '八' });
  const travel = h.calls.find((call) => call.path.includes('check-travel'));
  const refs = h.calls.filter((call) => !call.path.includes('check-travel'));
  form.inputs.idCard.value = 'nine'; form.inputs.idCard.dispatch('input');
  travel.resolve([{ id: 99, start_date: '2026-01-01' }]);
  refs.forEach((call) => call.resolve({ list: [{ startDate: 'STALE' }] })); await flush();
  assert.equal(form.inputs.personnelId.value, ''); assert.equal(form.inputs.travelDays.readOnly, false); assert.doesNotMatch(h.host.textContent, /STALE/);
});

for (const staleFails of [false, true]) test(`CRUD latest-query generation suppresses stale ${staleFails ? 'failure' : 'success'}`, async () => {
  const h = harness(); h.page(); h.query(); h.calls[1].resolve(rows(2)); await flush(); h.choose(2);
  if (staleFails) h.calls[0].reject(new Error('stale')); else h.calls[0].resolve(rows(1)); await flush();
  assert.deepEqual(h.rows(), ['2']); assert.equal(h.host.querySelector('.cb-row').checked, true); assert.equal(h.toasts.length, 0);
});

test('CRUD pagination uses the committed filter/page snapshot; old append never contaminates a refreshed query', async () => {
  const h = harness(); h.page(); h.calls[0].resolve({ ...rows(1, 2), total: 6 }); await flush();
  h.host.querySelector('[data-fkey]').value = 'not submitted'; h.observers[0]([{ isIntersecting: true }]);
  assert.match(h.calls[1].path, /page=2/); assert.doesNotMatch(h.calls[1].path, /not%20submitted/);
  h.query(); assert.match(h.calls[2].path, /name=not%20submitted/);
  h.calls[2].resolve({ ...rows(9), total: 3 }); await flush(); h.calls[1].resolve({ ...rows(3, 4), total: 6 }); await flush();
  assert.deepEqual(h.rows(), ['9']); h.observers[0]([{ isIntersecting: true }]); assert.match(h.calls[3].path, /page=2/);
  h.calls[3].resolve({ ...rows(10), total: 3 }); await flush(); assert.deepEqual(h.rows(), ['9', '10']);
});

test('CRUD filter reload clears header/batch immediately, prevents export/deletes from stale selection and retries page errors', async () => {
  const h = harness(); h.page(); h.calls[0].resolve(rows(1, 2)); await flush(); h.choose(1);
  assert.equal(h.host.querySelector('.cb-all').indeterminate, true);
  h.query(); assert.equal(h.button(h.host, '删除所选').disabled, true); assert.equal(h.host.querySelector('.cb-all').checked, false);
  h.button(h.host, '删除所选').dispatch('click');
  h.host.querySelectorAll('.dd-item').find((el) => el.textContent === '导出 Excel').click();
  assert.equal(h.confirmations.length, 0); assert.equal(h.queue('BLOB').length, 0);
  h.calls[1].resolve({ ...rows(3), total: 5 }); await flush(); h.observers[0]([{ isIntersecting: true }]); h.calls[2].reject(new Error('page offline')); await flush();
  h.observers[0]([{ isIntersecting: true }]); assert.equal(h.calls.length, 4); assert.match(h.calls[3].path, /page=2/); h.calls[3].resolve(rows(4)); await flush();
});

test('CRUD selected batch snapshots IDs, guards duplicate confirmation and continues serially after failure', async () => {
  const h = harness(); h.page(); h.calls[0].resolve(rows('id/1', 2, 3)); await flush(); h.choose('id/1'); h.choose(2);
  const batch = h.host.querySelector('.toolbar').querySelectorAll('button')[0]; batch.click(); batch.dispatch('click');
  assert.equal(h.confirmations.length, 1); assert.equal(batch.disabled, true);
  h.choose(3); assert.equal(h.host.querySelectorAll('.cb-row')[2].checked, false);
  h.query(); assert.equal(h.queue('GET').length, 1);
  h.confirmations[0].resolve(true); await flush(); assert.equal(h.queue('DEL').length, 1);
  h.queue('DEL')[0].reject(new Error('first failure')); await flush(); assert.equal(h.queue('DEL').length, 2);
  h.queue('DEL')[1].resolve({}); await flush();
  assert.deepEqual(h.queue('DEL').map((call) => call.path).sort(), ['/stub/items/2', '/stub/items/id%2F1']);
  assert.match(h.toasts.at(-1).text, /成功 1 条,失败 1 条。first failure/); h.queue('GET')[1].resolve(rows(3)); await flush();
  assert.equal(batch.disabled, true);
});

test('CRUD export encodes only selected loaded rows and cancels leave selection intact', async () => {
  const h = harness(); h.page(); h.calls[0].resolve(rows('a&b', 2)); await flush(); h.choose('a&b');
  h.host.querySelectorAll('.dd-item').find((el) => el.textContent === '导出 Excel').click();
  assert.equal(h.queue('BLOB')[0].path, '/stub/items/export?ids=a%26b'); h.queue('BLOB')[0].resolve({ blob: {}, filename: 'stub.xlsx' }); await flush();
  const batch = h.host.querySelector('.toolbar').querySelectorAll('button')[0]; batch.click(); h.confirmations[0].resolve(false); await flush();
  assert.equal(h.queue('DEL').length, 0); assert.equal(h.host.querySelector('.cb-row').checked, true); assert.equal(batch.disabled, false);
});

test('CRUD edits issue PUT exactly once; successful close bypasses dirty guard; request locks cancellation', async () => {
  const h = harness(); h.page(); h.calls[0].resolve(rows(5)); await flush();
  h.button(h.host, '编辑').click(); const modal = h.modal(); modal.querySelector('input').value = '修改';
  h.button(modal, '保存').click(); h.button(modal, '保存').dispatch('click'); h.button(modal, '取消').click();
  assert.equal(h.queue('PUT').length, 1); assert.equal(h.queue('PUT')[0].path, '/stub/items/5'); assert.ok(h.document.body.contains(modal));
  h.queue('PUT')[0].resolve({ id: 5 }); await flush(); assert.equal(h.document.body.contains(modal), false);
  h.queue('GET')[1].resolve(rows(5)); await flush();
});

test('CRUD partial staged failures keep modal and retry only failed children without re-POSTing main or successful items', async () => {
  const h = harness(); const staged = [{ kind: 'family', record: { name: 'first' } }, { kind: 'family', record: { name: 'second' } }];
  h.page({ subPanels: () => ({ panels: {}, staged: { family: () => staged.map((item) => item.record) } }) }); h.calls[0].resolve(rows()); await flush();
  h.button(h.host, '新增测试').click(); const modal = h.modal(); modal.querySelector('input').value = '主档';
  h.button(modal, '保存').click(); h.queue('POST')[0].resolve({ id: 88 }); await flush();
  assert.equal(h.queue('POST').length, 2); h.queue('POST')[1].resolve({ id: 101 }); await flush();
  h.queue('POST')[2].reject(new Error('暂时失败')); await flush();
  assert.match(modal.textContent, /成功 1 条、失败 1 条/); assert.equal(modal.querySelector('input').disabled, true);
  h.button(modal, '取消').click(); assert.ok(h.document.body.contains(modal), 'failed child discard requires confirmation');
  h.queue('GET')[1].resolve(rows(88)); await flush(); h.button(modal, '重试子记录').click(); await flush();
  assert.equal(h.queue('POST').length, 4); assert.equal(h.queue('POST').filter((call) => call.path === '/stub/items').length, 1);
  assert.deepEqual(h.queue('POST')[3].data, { name: 'second', personnelId: 88 });
  h.queue('POST')[3].resolve({ id: 102 }); await flush(); assert.equal(h.document.body.contains(modal), false);
  h.queue('GET')[2].resolve(rows(88)); await flush();
});

test('CRUD main-save failures unlock inputs and allow safe retry before any parent was created', async () => {
  const h = harness(); h.page(); h.calls[0].resolve(rows()); await flush(); h.button(h.host, '新增测试').click(); const modal = h.modal();
  modal.querySelector('input').value = '主档'; h.button(modal, '保存').click(); h.queue('POST')[0].reject(new Error('未创建')); await flush();
  assert.equal(modal.querySelector('input').disabled, false); assert.match(modal.textContent, /未创建/);
  h.button(modal, '保存').click(); assert.equal(h.queue('POST').length, 2); h.queue('POST')[1].resolve({ id: 1 }); await flush(); h.queue('GET')[1].resolve(rows(1)); await flush();
});

test('dirty raw-date form blocks cancel, close button, backdrop and Escape, then allows explicit discard', async () => {
  const h = harness(); h.page({ form: [{ key: 'date', label: '可选日期', type: 'date' }] }); h.calls[0].resolve(rows()); await flush(); h.button(h.host, '新增测试').click();
  const modal = h.modal(); modal.querySelector('input').value = 'invalid-date';
  h.button(modal, '取消').click(); modal.querySelector('.dialog-close').click(); modal.parentNode.dispatch('mousedown'); modal.dispatch('keydown', { key: 'Escape' });
  assert.ok(h.document.body.contains(modal)); h.c.confirm = () => true; h.button(modal, '取消').click(); assert.equal(h.document.body.contains(modal), false);
});

test('dialogs expose names, trap Tab, isolate nested Escape, restore focus and close idempotently', () => {
  const h = harness(); const trigger = h.c.h('button'); h.host.appendChild(trigger); trigger.focus(); let closed = 0;
  const modal = h.c.openModal({ title: '可访问窗口', body: h.c.h('input'), onClose: () => closed++ });
  assert.equal(modal.dialog.getAttribute('role'), 'dialog'); assert.equal(modal.dialog.getAttribute('aria-modal'), 'true');
  assert.equal(h.document.querySelector('#' + modal.dialog.getAttribute('aria-labelledby')).textContent, '可访问窗口');
  const buttons = modal.dialog.querySelectorAll('button'); const input = modal.dialog.querySelector('input'); input.focus();
  assert.equal(input.dispatch('keydown', { key: 'Tab' }).defaultPrevented, true); assert.equal(h.document.activeElement, buttons[0]);
  buttons[0].dispatch('keydown', { key: 'Tab', shiftKey: true }); assert.equal(h.document.activeElement, input);
  const nested = h.c.openModal({ title: '第二层' }); modal.dialog.dispatch('keydown', { key: 'Escape' }); assert.ok(h.document.body.contains(modal.dialog));
  nested.dialog.dispatch('keydown', { key: 'Escape' }); assert.equal(h.document.body.contains(nested.dialog), false);
  modal.close(); modal.close(); assert.equal(closed, 1); assert.equal(h.document.activeElement, trigger);
});

test('change-password uses real password fields, preserves whitespace and prevents duplicate submissions', async () => {
  const h = harness(); h.c.openChangePassword(); const modal = h.modal(); const inputs = modal.querySelectorAll('input');
  assert.ok(inputs.every((input) => input.type === 'password')); inputs[0].value = ' old '; inputs[1].value = inputs[2].value = ' new-pass ';
  h.button(modal, '确定修改').click(); h.button(modal, '确定修改').dispatch('click'); h.button(modal, '取消').click();
  assert.equal(h.queue('POST').length, 1); assert.deepEqual(h.queue('POST')[0].data, { oldPassword: ' old ', newPassword: ' new-pass ' });
  h.queue('POST')[0].resolve({}); await flush(); assert.equal(h.document.body.contains(modal), false);
});

test('fitness SVG tooltip escapes hostile dates and score text', () => {
  const h = harness(); const hostile = '2026</title><script>boom</script>&"';
  const chart = h.c.buildTrendChart([{ name: '<name>', data: [{ date: hostile, score: '80</title><img>' }] }], { legend: false });
  assert.ok(chart.innerHTML.includes('2026&lt;/title&gt;&lt;script&gt;boom&lt;/script&gt;&amp;&quot;'));
  assert.doesNotMatch(chart.innerHTML, /<script>|<img>/);
});

for (const change of ['user', 'token']) test(`dashboard preferences ignore shared keys and stale ${change} responses without seeding another user`, async () => {
  const h = harness(); h.c.localStorage.setItem('dashWidgets', '[{"dim":"secret","kind":"donut"}]'); h.c.localStorage.setItem('dashStatCards', '["secret"]');
  h.c.dashboardView(); assert.deepEqual(h.calls.map((call) => call.path), ['/api/preferences/dashWidgets', '/api/preferences/dashStatCards']);
  if (change === 'user') h.c.USER = { userId: 8 }; else h.c.TOKEN = 'new-token';
  h.calls[0].resolve('[{"dim":"secret","kind":"donut"}]'); h.calls[1].resolve('["secret"]'); await flush();
  assert.equal(h.queue('PUT').length, 0); assert.equal(h.c.localStorage.getItem('user:8:dashStatCards'), null); assert.equal(h.c.localStorage.getItem('user:7:dashStatCards'), null);
});

test('current dashboard stores remote cards only under current user and never deletes shared legacy settings', async () => {
  const h = harness(); h.c.localStorage.setItem('dashStatCards', '["legacy"]'); h.c.dashboardView();
  h.calls[0].resolve(''); h.calls[1].resolve('["onDuty"]'); await flush();
  assert.equal(h.c.localStorage.getItem('user:7:dashStatCards'), '["onDuty"]'); assert.equal(h.c.localStorage.getItem('dashStatCards'), '["legacy"]');
  h.c.USER = { userId: 8 }; assert.equal(h.c.preferenceKey('lastView'), 'user:8:lastView');
});

test('import rule hints state the matching key per module and mark append-only modules', () => {
  const h = harness();
  const matched = { personnel: '身份证号', 'equipment-gun': '装备编号', equipment: '识别码', vehicle: '车架号', phone: '身份证号', fitness: '覆盖更新' };
  for (const [key, token] of Object.entries(matched)) {
    const text = h.c.importRuleText(key);
    assert.ok(text.includes(token), `${key} mentions ${token}`);
    assert.ok(!text.includes('增量'), `${key} is not described as append-only`);
  }
  for (const key of ['military', 'leave', 'leave-plan', 'material']) {
    const text = h.c.importRuleText(key);
    assert.ok(text.includes('增量') && text.includes('新增'), `${key} is append-only`);
    assert.ok(!text.includes('匹配：') && !text.includes('匹配:'), `${key} has no match key`);
  }
  assert.ok(h.c.importRuleText('unknown-module').includes('模板'));
});

const importDateHint = '日期列支持20160901、2016-09-01、2016.09.01、2016/9/1、2016年9月1日及Excel日期单元格，统一识别真实日期，非法日期会报错。';
const dateImportModules = ['personnel', 'equipment-gun', 'equipment', 'equipment-large', 'equipment-small',
  'vehicle', 'phone', 'fitness', 'military', 'leave', 'leave-plan'];

test('import date hints cover calendar-date modules only and preserve equipment matching rules', () => {
  const h = harness();
  for (const key of dateImportModules) {
    const text = h.c.importRuleText(key);
    assert.ok(text.endsWith('\n' + importDateHint), `${key} explains supported dates and validation`);
    assert.equal(text.split(importDateHint).length, 2, `${key} shows the hint once`);
  }
  for (const key of ['equipment-large', 'equipment-small']) {
    assert.equal(h.c.importRuleText(key), h.c.importRuleText('equipment'));
  }
  assert.equal(h.c.importRuleText('material'), '物资为增量导入：每个有效行都新增一条记录，不匹配旧记录、不修改已有库存数量。');
  for (const key of ['unknown-module', 'dict', 'fitness-std', 'matrix', 'body']) {
    assert.equal(h.c.importRuleText(key), '请先下载模板，按模板列头填写后导入。');
  }
  assert.doesNotMatch(definition('importStdExcel'), /importRuleText|日期列支持/);
  assert.doesNotMatch(definition('importRuleText'), /\b(?:let|const|class|async|await)\s|=>|\.includes\(/);
});

test('CRUD import titles and confirmation dialogs reuse the date hint without widening file types', async () => {
  for (const key of dateImportModules.filter((key) => key !== 'leave-plan').concat('material')) {
    const h = harness(); h.page({ key }); h.calls[0].resolve(rows()); await flush();
    const input = h.host.querySelector('input[type="file"]');
    assert.equal(input.getAttribute('accept'), '.xlsx');
    const action = h.host.querySelectorAll('.dd-item').find((el) => el.textContent === '导入 Excel');
    const rule = h.c.importRuleText(key);
    assert.equal(action.getAttribute('title'), rule);
    action.click();
    assert.equal(h.confirmations.length, 1);
    assert.equal(h.confirmations[0].message, rule + '\n\n确定要继续选择文件导入吗？');
    h.confirmations[0].resolve(false); await flush();
    assert.equal(h.calls.length, 1, 'cancel does not upload or reload');
  }
});

function itemLoans(departments = [{ id: 3, name: '一连', depth: 0 }]) {
  const h = harness();
  h.c.Ctx.deptFlat = departments;
  h.c.Ctx.personnel = [];
  h.c.Ctx.personnelOptions = [];
  const attach = vm.runInContext(`(${definition('attachPersonPick')})`, h.c);
  h.c.attachPersonPick = (el, callback) => {
    assert.equal(el.parentElement.querySelector('label').textContent, '经手人', 'only the handler may attach a personnel picker');
    assert.equal(el.classList.contains('dd'), true, 'reuse the personnel dropdown');
    attach(el, callback);
  };
  h.c.openPersonPicker = (callback) => { h.handlerPick = callback; };
  h.c.validateIdCard = () => assert.fail('borrower ID is free text, not a personnel ID card');
  vm.runInContext(source.slice(source.indexOf('var itemLoanCfg ='), source.indexOf('/* ================= 双争评比')), h.c);
  const build = h.c.buildForm;
  h.c.buildForm = (...args) => { h.formInstance = build(...args); return h.formInstance; };
  h.c.crudView(h.c.itemLoanCfg).render(h.host);
  return h;
}
const loan = (extra = {}) => ({ id: 7, itemName: '工具箱', specification: 'A-1', quantity: 2.25, unit: '套',
  borrowerName: '张三（外来借用人）', borrowerIdCard: '00123abcX', borrowerPhone: '00086-extA', borrowerUnit: '外部单位 A',
  registrationDepartment: '外部登记单位 A', departmentId: 3, departmentName: '一连', borrowDate: '2026-01-01',
  returnCondition: '', returnRemark: '', remark: '训练使用', status: 'borrowing', ...extra });
function fillLoan(h, extra = {}) {
  const data = { itemName: '工具箱', quantity: '2.25', unit: '套', borrowerName: '张三（外来借用人）', registrationDepartment: '外部登记单位 A', ...extra };
  for (const [key, value] of Object.entries(data)) h.formInstance.inputs[key].value = value;
}

test('item loans render only borrow date and borrowing/returned statuses without Excel entry points', async () => {
  const h = itemLoans();
  assert.equal(h.calls[0].path, '/api/item-loans?page=1&size=2');
  h.calls[0].resolve({ list: [loan(), loan({ id: 8 }), loan({ id: 9, status: 'returned' })], total: 3 }); await flush();
  const headers = h.host.querySelectorAll('th').map((el) => el.textContent);
  assert.deepEqual(headers.slice(1, -1), ['物品名称', '快捷归还', '规格型号', '借用人单位', '借用人', '借用人身份证号', '借用人联系方式', '登记单位', '经手人', '经手人身份证号', '数量', '物品单位', '借出日期', '状态']);
  for (const text of ['工具箱', 'A-1', '张三（外来借用人）', '00123abcX', '00086-extA', '外部单位 A', '外部登记单位 A', '2.25', '套', '2026-01-01', '借用中', '已归还']) assert.ok(h.host.textContent.includes(text), text);
  assert.doesNotMatch(h.host.textContent, /预计归还日期|实际归还日期|已逾期/);
  assert.equal(h.button(h.host, '新增借用登记').textContent.length, 6);
  assert.equal(h.host.querySelectorAll('input[type="file"], .io-dd').length, 0);
  assert.doesNotMatch(h.host.textContent, /下载模板|导入导出|导入 Excel|导出 Excel/);
  const bodyRows = h.host.querySelectorAll('tbody tr');
  assert.deepEqual(bodyRows[0].querySelectorAll('button').map((el) => el.textContent), ['快捷归还', '查看', '编辑', '登记归还', '删除']);
  assert.equal(bodyRows[1].querySelectorAll('[data-act="return"]').length, 2, 'borrowing loans retain both return entries');
  assert.equal(bodyRows[2].children[2].textContent, '已归还');
  assert.equal(bodyRows[2].children[2].querySelector('button'), null);
  assert.deepEqual(bodyRows[2].querySelectorAll('button').map((el) => el.textContent), ['查看', '删除']);
});

test('item loan list and detail display the server handler ID card snapshot escaped', async () => {
  const h = itemLoans();
  const card = '<b>110101199001010011</b>';
  h.calls[0].resolve({ list: [loan({ handlerId: 23, handlerName: '经手人甲', handlerIdCard: card })], total: 1 }); await flush();
  const cardCell = h.host.querySelectorAll('tbody td').find((el) => el.textContent === card);
  assert.ok(cardCell);
  assert.equal(cardCell.querySelectorAll('b, script').length, 0, 'server snapshot is rendered as text');
  h.button(h.host, '查看').click();
  const item = h.modal().querySelectorAll('.detail-item').find((el) => el.querySelector('.dl').textContent === '经手人身份证号');
  assert.equal(item.querySelector('.dv').textContent, card);
  assert.equal(h.modal().querySelectorAll('b').length, 0);
});

test('item loans filters only free-text loan fields/status and pagination preserves committed criteria', async () => {
  const h = itemLoans(); h.calls[0].resolve({ list: [], total: 0 }); await flush();
  assert.equal(h.host.querySelector('[data-fkey="personnelName"]'), null);
  assert.equal(h.host.querySelector('[data-fkey="departmentId"]'), null);
  const registration = h.host.querySelector('[data-fkey="registrationDepartment"]');
  assert.equal(registration.tagName, 'INPUT'); assert.equal(registration.type, 'text');
  assert.equal(registration.getAttribute('aria-label'), '登记单位');
  const statusFilter = h.c.itemLoanCfg.filters.find((field) => field.key === 'status');
  assert.equal(statusFilter.type, 'select');
  assert.deepEqual(plain(statusFilter.options), [{ v: 'borrowing', l: '借用中' }, { v: 'returned', l: '已归还' }]);
  const status = h.host.querySelector('[data-fkey="status"]');
  assert.equal(status.classList.contains('dd'), true);
  assert.equal(status.querySelector('.dd-btn').getAttribute('aria-label'), '状态');
  assert.deepEqual(status.querySelectorAll('.dd-item').map((item) => [item.getAttribute('data-v'), item.textContent]), [['', '全部'], ['borrowing', '借用中'], ['returned', '已归还']]);
  assert.equal(status.querySelector('[data-v="overdue"]'), null);
  for (const [key, value] of Object.entries({ itemName: '箱 & 绳', borrowerName: '张/三 & A', registrationDepartment: '外单位 & / A', status: 'returned' })) h.host.querySelector(`[data-fkey="${key}"]`).value = value;
  h.query();
  const url = new URL(h.calls[1].path, 'http://test');
  assert.deepEqual(Object.fromEntries(url.searchParams), { itemName: '箱 & 绳', borrowerName: '张/三 & A', registrationDepartment: '外单位 & / A', status: 'returned', page: '1', size: '2' });
  h.calls[1].resolve({ list: [loan(), loan({ id: 8 })], total: 3 }); await flush();
  h.host.querySelector('[data-fkey="itemName"]').value = '未提交';
  registration.value = '未提交的登记单位';
  h.observers[0]([{ isIntersecting: true }]);
  assert.equal(new URL(h.calls[2].path, 'http://test').searchParams.get('itemName'), '箱 & 绳');
  assert.equal(new URL(h.calls[2].path, 'http://test').searchParams.get('borrowerName'), '张/三 & A');
  assert.equal(new URL(h.calls[2].path, 'http://test').searchParams.get('registrationDepartment'), '外单位 & / A');
  assert.equal(new URL(h.calls[2].path, 'http://test').searchParams.get('status'), 'returned');
  assert.equal(new URL(h.calls[2].path, 'http://test').searchParams.has('departmentId'), false);
  assert.equal(new URL(h.calls[2].path, 'http://test').searchParams.get('page'), '2');
  h.calls[2].resolve({ list: [loan({ id: 9 })], total: 3 }); await flush();
  h.button(h.host, '重置').click(); assert.equal(h.calls[3].path, '/api/item-loans?page=1&size=2');
  h.calls[3].resolve({ list: [], total: 0 }); await flush();
});

test('item loan custom status dropdown selects both states and clears to all without overdue', async () => {
  const h = itemLoans(); h.calls[0].resolve({ list: [], total: 0 }); await flush();
  const status = h.host.querySelector('[data-fkey="status"]');
  const button = status.querySelector('.dd-btn');
  assert.equal(status.value, ''); assert.equal(button.textContent, '全部▾');
  for (const [value, label] of [['borrowing', '借用中'], ['returned', '已归还'], ['', '全部']]) {
    const callCount = h.calls.length;
    button.click(); assert.equal(status.classList.contains('open'), true);
    status.querySelector(`.dd-item[data-v="${value}"]`).click();
    assert.equal(status.value, value); assert.equal(button.textContent, `${label}▾`);
    assert.equal(status.classList.contains('open'), false);
    assert.deepEqual(status.querySelectorAll('.dd-item.cur').map((item) => item.getAttribute('data-v')), [value]);
    const request = h.calls.at(-1);
    assert.equal(h.calls.length, callCount + 1, 'one query per selection');
    const params = Object.fromEntries(new URL(request.path, 'http://test').searchParams);
    assert.deepEqual(params, { ...(value ? { status: value } : {}), page: '1', size: '2' });
    assert.doesNotMatch(status.textContent, /逾期|overdue/);
    request.resolve({ list: [], total: 0 }); await flush();
  }
});

test('item loan form has no management-department selector and POSTs only safe loan fields', async () => {
  const h = itemLoans(); h.calls[0].resolve({ list: [], total: 0 }); await flush(); h.button(h.host, '新增借用登记').click();
  const form = h.formInstance, modal = h.modal();
  const borrowerFields = { borrowerName: ['借用人', 100], borrowerIdCard: ['借用人身份证号', 50], borrowerPhone: ['借用人联系方式', 50], borrowerUnit: ['借用人单位', 200], registrationDepartment: ['登记单位', 200] };
  for (const [key, [label, max]] of Object.entries(borrowerFields)) {
    assert.equal(form.inputs[key].tagName, 'INPUT'); assert.equal(form.inputs[key].type, 'text');
    assert.equal(form.inputs[key].getAttribute('aria-label'), label);
    assert.equal(form.inputs[key].getAttribute('maxlength'), String(max));
    assert.equal(form.inputs[key].readOnly, false);
  }
  assert.equal(form.inputs.personnelId, undefined); assert.equal(form.inputs.personnelName, undefined);
  assert.equal(form.inputs.departmentId, undefined); assert.equal(form.el.querySelectorAll('.dd').length, 1);
  assert.equal(form.inputs.handlerId.classList.contains('dd'), true);
  assert.equal(form.inputs.handlerId.querySelector('.dd-btn').getAttribute('aria-label'), '经手人');
  assert.equal(form.inputs.handlerId.parentElement.querySelector('.pick-btn').getAttribute('aria-label'), '经手人');
  assert.equal(form.inputs.handlerName, undefined);
  assert.doesNotMatch(modal.textContent, /管理单位|权限归属/);
  assert.deepEqual(modal.querySelectorAll('button').map((el) => el.textContent).filter((text) => /选择人员|新增人员|选择借用人/.test(text)), []);
  assert.equal(form.inputs.borrowDate.querySelector('input').getAttribute('aria-label'), '借出日期');
  assert.equal(form.inputs.borrowDate.value, h.c.formatCalendarDay(vm.runInContext('new Date()', h.c)));
  assert.equal(form.inputs.quantity.getAttribute('step'), '0.01');
  h.button(modal, '保存').click(); assert.equal(h.queue('POST').length, 0);
  const borrower = { borrowerName: '自由输入 A-0', borrowerIdCard: '0000abcXYZ0123456789', borrowerPhone: '00086-extA', borrowerUnit: '外部单位 A（非登记单位）' };
  fillLoan(h, { specification: 'A型', remark: '训练', ...borrower });
  form.inputs.borrowerIdCard.dispatch('change'); form.inputs.borrowerPhone.dispatch('input');
  assert.equal(form.inputs.borrowerIdCard.classList.contains('bad'), false);
  h.button(modal, '保存').click(); h.button(modal, '保存').dispatch('click');
  assert.equal(h.queue('POST').length, 1); const request = h.queue('POST')[0]; assert.equal(request.path, '/api/item-loans');
  assert.equal(form.inputs.expectedReturnDate, undefined); assert.equal(form.inputs.actualReturnDate, undefined);
  assert.doesNotMatch(modal.textContent, /预计归还日期|实际归还日期/);
  assert.deepEqual(request.data, { itemName: '工具箱', specification: 'A型', quantity: 2.25, unit: '套', ...borrower, registrationDepartment: '外部登记单位 A', handlerId: 0, borrowDate: form.inputs.borrowDate.value, remark: '训练' });
  for (const key of ['departmentId', 'departmentName', 'ownerUserId']) assert.equal(Object.hasOwn(request.data, key), false, key);
  assert.ok(h.calls.every((call) => call.path.startsWith('/api/item-loans')), 'no personnel lookup requests');
  request.resolve(loan(borrower)); await flush(); assert.equal(h.document.body.contains(modal), false); assert.equal(h.queue('GET').length, 2);
  h.queue('GET')[1].resolve({ list: [loan(borrower)], total: 1 }); await flush();
});

for (const departments of [[], [{ id: 3, name: '一连', depth: 0 }, { id: 4, name: '二连', depth: 1 }]]) test(`item loan form and payload ignore ${departments.length} configured departments`, async () => {
  const h = itemLoans(departments); h.calls[0].resolve({ list: [], total: 0 }); await flush();
  assert.equal(h.host.querySelector('[data-fkey="departmentId"]'), null);
  h.button(h.host, '新增借用登记').click(); const form = h.formInstance, modal = h.modal();
  assert.equal(form.inputs.departmentId, undefined);
  assert.equal(form.el.querySelector('.item-loan-auto-department'), null);
  fillLoan(h, { borrowerUnit: '手填单位独立文本', registrationDepartment: '一连' });
  h.button(modal, '保存').click(); const request = h.queue('POST')[0]; assert.ok(request);
  assert.equal(request.data.registrationDepartment, '一连');
  assert.equal(request.data.borrowerUnit, '手填单位独立文本');
  for (const key of ['departmentId', 'departmentName', 'ownerUserId']) assert.equal(Object.hasOwn(request.data, key), false, key);
  request.resolve(loan()); await flush(); h.queue('GET')[1].resolve({ list: [], total: 0 }); await flush();
});

for (const value of ['', '0', '0000abcXYZ0123456789']) test(`item loan optional borrower ID/phone/unit accept text verbatim: ${JSON.stringify(value)}`, async () => {
  const h = itemLoans(); h.calls[0].resolve({ list: [], total: 0 }); await flush(); h.button(h.host, '新增借用登记').click();
  fillLoan(h, { borrowerIdCard: value, borrowerPhone: value, borrowerUnit: value });
  h.formInstance.inputs.borrowerIdCard.dispatch('change');
  h.button(h.modal(), '保存').click(); const request = h.queue('POST')[0]; assert.ok(request);
  for (const key of ['borrowerIdCard', 'borrowerPhone', 'borrowerUnit']) assert.equal(request.data[key], value);
  request.resolve(loan()); await flush(); h.queue('GET')[1].resolve({ list: [], total: 0 }); await flush();
});

for (const departments of [[], [{ id: 3, name: '一连', depth: 0 }, { id: 4, name: '二连', depth: 1 }]]) test(`item loan details and edit retain free-text fields with ${departments.length} configured departments`, async () => {
  const h = itemLoans(departments);
  const borrower = { borrowerName: '<外部借用人> A-0', borrowerIdCard: '0000abcXYZ0123456789', borrowerPhone: '00086-ExtA', borrowerUnit: '手填单位 <A>' };
  const row = loan({ ...borrower, departmentId: 4, departmentName: '二连' });
  h.calls[0].resolve({ list: [row], total: 1 }); await flush();
  h.host.querySelector('[data-view-id]').click(); const detail = h.modal();
  const values = Object.fromEntries(detail.querySelectorAll('.detail-item').map((el) => [el.querySelector('.dl').textContent, el.querySelector('.dv').textContent]));
  for (const field of h.c.itemLoanCfg.columns.filter((field) => !field.action)) {
    assert.equal(values[field.label], field.key === 'status' ? '借用中' : String(row[field.key] || '—'), field.label);
  }
  assert.equal(values['快捷归还'], undefined, 'actions are never shown as business values');
  assert.equal(values['管理单位（权限归属）'], undefined);
  assert.equal(values['用途备注'], row.remark); assert.equal(detail.querySelectorAll('script').length, 0);
  assert.equal(h.calls.length, 1, 'details use server record snapshots without personnel queries');
  h.button(detail, '编辑').click(); const form = h.formInstance;
  for (const field of h.c.itemLoanCfg.form) assert.equal(String(form.inputs[field.key].value), String(row[field.key] || ''), field.key);
  assert.equal(form.inputs.departmentId, undefined);
  const edited = { borrowerName: '另一个自由借用人', borrowerIdCard: '000-newID', borrowerPhone: '000-newPhone', borrowerUnit: '另一个手填单位', registrationDepartment: '另一个外部登记单位' };
  fillLoan(h, edited); form.inputs.borrowerIdCard.dispatch('change');
  h.button(h.modal(), '保存').click(); const request = h.queue('PUT')[0]; assert.equal(request.path, '/api/item-loans/7');
  assert.deepEqual(request.data, { itemName: row.itemName, specification: row.specification, quantity: row.quantity, unit: row.unit, ...edited, handlerId: 0, borrowDate: row.borrowDate, remark: row.remark });
  for (const key of ['departmentId', 'departmentName', 'ownerUserId']) assert.equal(Object.hasOwn(request.data, key), false, key);
  request.resolve({ ...row, ...request.data }); await flush();
  h.queue('GET')[1].resolve({ list: [{ ...row, ...request.data }], total: 1 }); await flush();
  h.button(h.host, '查看').click();
  for (const value of Object.values(edited)) assert.ok(h.modal().textContent.includes(value), value);
  assert.ok(h.calls.every((call) => call.path.startsWith('/api/item-loans')));
});

test('item loan handler dropdown and picker submit only the chosen ID without changing borrower text', async () => {
  const h = itemLoans();
  h.c.Ctx.personnelOptions = [{ id: 23, name: '经手甲', idCard: '110101199001010011', departmentName: '一连' }, { id: 24, name: '经手乙', idCard: '220202199002020022' }];
  h.calls[0].resolve({ list: [], total: 0 }); await flush();
  h.button(h.host, '新增借用登记').click();
  const form = h.formInstance, handler = form.inputs.handlerId;
  assert.equal(h.c.itemLoanCfg.form.find((field) => field.key === 'handlerId').required, undefined);
  assert.deepEqual(handler.querySelectorAll('.dd-item').map((el) => [el.getAttribute('data-v'), el.textContent]), [['', '(不指定)'], ['23', '经手甲 (一连)'], ['24', '经手乙']]);
  const handlerCard = form.inputs.handlerIdCard;
  assert.equal(handlerCard.tagName, 'INPUT');
  assert.equal(handlerCard.readOnly, true);
  assert.equal(handlerCard.closest('.form-item').querySelector('label').textContent, '经手人身份证号');
  assert.equal(handlerCard.value, '');
  const borrower = { borrowerName: '独立借用人', borrowerIdCard: '000-外来证件', borrowerPhone: '000-ext', borrowerUnit: '外部单位' };
  fillLoan(h, borrower);
  handler.querySelector('[data-v="23"]').click();
  assert.equal(handler.value, '23');
  assert.equal(handler.querySelector('.dd-btn').textContent, '经手甲 (一连)▾');
  assert.equal(handlerCard.value, '110101199001010011');
  handler.parentElement.querySelector('.pick-btn').click();
  assert.equal(typeof h.handlerPick, 'function');
  h.handlerPick({ id: 24, name: '经手乙', idCard: '不应回填', phone: '不应回填', departmentName: '不应回填' });
  assert.equal(handler.value, '24');
  assert.equal(handler.querySelector('.dd-btn').textContent, '经手乙▾');
  assert.equal(handlerCard.value, '220202199002020022');
  for (const [key, value] of Object.entries(borrower)) assert.equal(form.inputs[key].value, value);
  assert.equal(form.inputs.personnelId, undefined);
  h.button(h.modal(), '保存').click(); const request = h.queue('POST')[0];
  assert.equal(request.data.handlerId, 24);
  for (const [key, value] of Object.entries(borrower)) assert.equal(request.data[key], value);
  for (const key of ['handlerName', 'handlerIdCard', 'personnelId', 'personnelName']) assert.equal(Object.hasOwn(request.data, key), false);
  request.resolve(loan({ ...borrower, handlerId: 24, handlerName: '经手乙' })); await flush();
  h.queue('GET')[1].resolve({ list: [], total: 0 }); await flush();
});

for (const visible of [false, true]) for (const choice of ['retain', 'clear', 'replace']) test(`item loan editing ${visible ? 'visible' : 'deleted or invisible'} handler supports ${choice}`, async () => {
  const h = itemLoans();
  const options = [{ id: 24, name: '新经手人' }, ...(visible ? [{ id: 23, name: '当前档案姓名' }] : [])];
  h.c.Ctx.personnelOptions = options;
  const row = loan({ handlerId: 23, handlerName: '已有经手人快照', personnelId: 88, personnelName: '旧借用人关联' });
  h.calls[0].resolve({ list: [row], total: 1 }); await flush();
  h.button(h.host, '编辑').click(); const form = h.formInstance, handler = form.inputs.handlerId;
  assert.equal(handler.value, '23');
  assert.equal(handler.querySelector('.dd-btn').textContent, `${row.handlerName}▾`);
  assert.equal(handler.querySelectorAll('[data-v="23"]').length, 1);
  assert.equal(h.c.Ctx.personnelOptions, options);
  assert.equal(options.length, visible ? 2 : 1);
  if (choice !== 'retain') handler.querySelector(`[data-v="${choice === 'clear' ? '' : '24'}"]`).click();
  h.button(h.modal(), '保存').click(); const request = h.queue('PUT')[0];
  assert.equal(request.data.handlerId, { retain: 23, clear: 0, replace: 24 }[choice]);
  for (const key of ['borrowerName', 'borrowerIdCard', 'borrowerPhone', 'borrowerUnit']) assert.equal(request.data[key], row[key]);
  for (const key of ['handlerName', 'handlerIdCard', 'personnelId', 'personnelName']) assert.equal(Object.hasOwn(request.data, key), false);
  request.resolve({ ...row, ...request.data }); await flush();
  h.queue('GET')[1].resolve({ list: [], total: 0 }); await flush();
});

for (const visible of [false, true]) test(`item loan same-ID snapshots stay local across edit/create forms with ${visible ? 'visible' : 'missing'} personnel`, async () => {
  const h = itemLoans();
  const options = [{ id: 24, name: '其他人员' }, ...(visible ? [{ id: 23, name: '可见人员真实姓名' }] : [])];
  h.c.Ctx.personnelOptions = options; const original = plain(options);
  const first = loan({ handlerId: 23, handlerName: '外来快照甲' });
  const second = loan({ id: 8, handlerId: 23, handlerName: '外来快照乙' });
  h.calls[0].resolve({ list: [first, second], total: 2 }); await flush();
  const tableRows = h.host.querySelectorAll('tbody tr');
  h.button(tableRows[0], '编辑').click(); const firstHandler = h.formInstance.inputs.handlerId;
  const secondForm = h.c.buildForm(h.c.itemLoanCfg.form, second); h.c.setupItemLoanForm(secondForm, second);
  assert.equal(firstHandler.querySelector('[data-v="23"]').textContent, '外来快照甲');
  assert.equal(secondForm.inputs.handlerId.querySelector('[data-v="23"]').textContent, '外来快照乙');
  assert.doesNotMatch(secondForm.el.textContent, /外来快照甲/);
  assert.doesNotMatch(firstHandler.textContent, /外来快照乙/);
  assert.equal(h.c.Ctx.personnelOptions, options); assert.deepEqual(plain(options), original);
  h.button(h.modal(), '取消').click(); h.button(h.host, '新增借用登记').click();
  const newHandler = h.formInstance.inputs.handlerId;
  assert.equal(newHandler.value, '');
  assert.doesNotMatch(newHandler.textContent, /外来快照甲|外来快照乙/);
  assert.equal(!!newHandler.querySelector('[data-v="23"]'), visible);
  assert.deepEqual(plain(h.c.Ctx.personnelOptions), original);
});

test('item loan handler form shows saved ID card snapshot then current card of a reselected person and clears', async () => {
  const h = itemLoans();
  h.c.Ctx.personnelOptions = [
    { id: 23, name: '登记后已改名', idCard: '当前证件不应覆盖历史' },
    { id: 24, name: '新经手人', idCard: '220202199002020022' }
  ];
  const row = loan({ handlerId: 23, handlerName: '登记时姓名', handlerIdCard: '历史证件快照' });
  h.calls[0].resolve({ list: [row], total: 1 }); await flush();
  h.button(h.host, '编辑').click();
  const handler = h.formInstance.inputs.handlerId;
  const card = h.formInstance.inputs.handlerIdCard;
  assert.equal(card.tagName, 'INPUT');
  assert.equal(card.readOnly, true);
  assert.equal(card.value, '历史证件快照');
  handler.querySelector('[data-v="24"]').click();
  assert.equal(card.value, '220202199002020022');
  handler.querySelector('[data-v=""]').click();
  assert.equal(card.value, '');
});

test('item loan renamed visible handler retains its snapshot while changing ID shows the new handler current name', async () => {
  const h = itemLoans();
  const options = [{ id: 23, name: '原经手人已改名', departmentName: '当前单位' }, { id: 24, name: '新经手人当前名', departmentName: '二连' }];
  h.c.Ctx.personnelOptions = options; const original = plain(options);
  const row = loan({ handlerId: 23, handlerName: '借用时经手人姓名' });
  h.calls[0].resolve({ list: [row], total: 1 }); await flush();
  h.button(h.host, '编辑').click(); const handler = h.formInstance.inputs.handlerId;
  assert.equal(handler.value, '23');
  assert.equal(handler.querySelector('.dd-btn').textContent, `${row.handlerName}▾`);
  assert.equal(handler.querySelector('[data-v="23"]').textContent, row.handlerName);
  assert.doesNotMatch(handler.textContent, /原经手人已改名/);
  handler.querySelector('[data-v="24"]').click();
  assert.equal(handler.value, '24');
  assert.equal(handler.querySelector('.dd-btn').textContent, '新经手人当前名 (二连)▾');
  assert.equal(h.c.Ctx.personnelOptions, options); assert.deepEqual(plain(options), original);
  h.button(h.modal(), '保存').click(); const request = h.queue('PUT')[0];
  assert.equal(request.data.handlerId, 24);
  assert.equal(Object.hasOwn(request.data, 'handlerName'), false);
  assert.equal(Object.hasOwn(request.data, 'handlerIdCard'), false);
  request.resolve({ ...row, ...request.data }); await flush();
  h.queue('GET')[1].resolve({ list: [], total: 0 }); await flush();
  h.button(h.host, '新增借用登记').click();
  assert.equal(h.formInstance.inputs.handlerId.querySelector('[data-v="23"]').textContent, '原经手人已改名 (当前单位)');
});

for (const handlerId of [undefined, null, 0]) test(`item loan historical empty handler ${handlerId} stays optional and submits zero`, async () => {
  const h = itemLoans(); h.calls[0].resolve({ list: [loan({ handlerId })], total: 1 }); await flush();
  h.button(h.host, '编辑').click();
  assert.equal(h.formInstance.inputs.handlerId.value, '');
  assert.equal(h.formInstance.inputs.handlerId.querySelector('.dd-btn').textContent, '(不指定)▾');
  h.button(h.modal(), '保存').click(); assert.equal(h.queue('PUT')[0].data.handlerId, 0);
  h.queue('PUT')[0].resolve(loan()); await flush(); h.queue('GET')[1].resolve({ list: [], total: 0 }); await flush();
});

test('item loan handler list/detail/edit use escaped server snapshots even when personnel are unavailable', async () => {
  const h = itemLoans();
  const name = '<img src="x" onerror="boom()"> & <script>经手人</script>';
  h.calls[0].resolve({ list: [loan({ handlerId: 23, handlerName: name })], total: 1 }); await flush();
  assert.ok(h.host.textContent.includes(name)); assert.equal(h.host.querySelectorAll('img, script').length, 0);
  h.button(h.host, '查看').click(); const detail = h.modal();
  const item = detail.querySelectorAll('.detail-item').find((el) => el.querySelector('.dl').textContent === '经手人');
  assert.equal(item.querySelector('.dv').textContent, name);
  assert.equal(detail.querySelectorAll('img, script').length, 0);
  h.button(detail, '编辑').click(); const handler = h.formInstance.inputs.handlerId;
  assert.equal(handler.querySelector('.dd-btn').textContent, `${name}▾`);
  assert.equal(handler.querySelector('[data-v="23"]').textContent, name);
  assert.equal(h.modal().querySelectorAll('img, script').length, 0);
  assert.deepEqual(plain(h.c.Ctx.personnelOptions), []);
  assert.equal(h.calls.length, 1, 'no additional personnel lookup for historical snapshots');
});

test('item loan production configuration remains ES5 and only the handler uses a personnel selector', () => {
  const config = source.slice(source.indexOf('var itemLoanCfg ='), source.indexOf('/* ================= 双争评比'));
  assert.doesNotMatch(config, /personnelId|personnelName|personLookup/);
  assert.equal((config.match(/type: 'personnel'/g) || []).length, 1);
  assert.match(config, /key: 'handlerId', label: '经手人', type: 'personnel'/);
  const implementation = source.slice(source.indexOf('function itemLoanStatus('), source.indexOf('/* ================= 双争评比'));
  assert.doesNotMatch(implementation, /\b(?:let|const|class|async|await)\s|=>|\.includes\(|\.finally\(|fetch\(/);
});

for (const quantity of ['0', '-1', '1.234', '1000000000', '1e2', 'NaN']) test(`item loan form rejects invalid quantity ${quantity}`, async () => {
  const h = itemLoans(); h.calls[0].resolve({ list: [], total: 0 }); await flush(); h.button(h.host, '新增借用登记').click();
  fillLoan(h, { quantity }); h.button(h.modal(), '保存').click(); assert.equal(h.queue('POST').length, 0);
  assert.match(h.modal().textContent, /请填写:数量|数量必须大于0/);
});

for (const key of ['itemName', 'quantity', 'unit', 'borrowerName', 'registrationDepartment', 'borrowDate']) test(`item loan form requires ${key}`, async () => {
  const h = itemLoans(); h.calls[0].resolve({ list: [], total: 0 }); await flush(); h.button(h.host, '新增借用登记').click();
  fillLoan(h, { [key]: '' }); h.button(h.modal(), '保存').click(); assert.equal(h.queue('POST').length, 0); assert.match(h.modal().textContent, /请填写:/);
});

test('item loan form validates lengths and quantity boundaries without either return-date field', async () => {
  const h = itemLoans(); h.calls[0].resolve({ list: [loan({ returnCondition: '不会提交', returnRemark: '不会提交' })], total: 1 }); await flush();
  h.button(h.host, '编辑').click(); const modal = h.modal(), form = h.formInstance;
  for (const [key, max] of Object.entries({ itemName: 100, specification: 200, unit: 20, borrowerName: 100, borrowerIdCard: 50, borrowerPhone: 50, borrowerUnit: 200, registrationDepartment: 200, remark: 1000 })) {
    assert.equal(form.inputs[key].getAttribute('maxlength'), String(max));
    const original = form.inputs[key].value; form.inputs[key].value = '字'.repeat(max);
    assert.equal(form.get()[key], '字'.repeat(max), `${key} accepts exact max length`);
    form.inputs[key].value = '字'.repeat(max + 1);
    h.button(modal, '保存').click(); assert.equal(h.queue('PUT').length, 0); assert.match(modal.textContent, /不能超过/); form.inputs[key].value = original;
  }
  assert.equal(form.inputs.expectedReturnDate, undefined); assert.equal(form.inputs.actualReturnDate, undefined);
  form.inputs.quantity.value = '999999999.99';
  assert.equal(form.get().quantity, 999999999.99); form.inputs.quantity.value = '0.01'; assert.equal(form.get().quantity, 0.01);
  h.button(modal, '保存').click(); assert.equal(h.queue('PUT')[0].path, '/api/item-loans/7');
  assert.deepEqual(Object.keys(h.queue('PUT')[0].data).sort(), ['itemName', 'specification', 'quantity', 'unit', 'borrowerName', 'borrowerIdCard', 'borrowerPhone', 'borrowerUnit', 'registrationDepartment', 'handlerId', 'borrowDate', 'remark'].sort());
  const cleaned = h.c.itemLoanCfg.beforeSave({ ...loan(), itemName: 'only-borrow', handlerId: '23', handlerName: '不提交的经手人姓名', handlerIdCard: '不提交的经手人证件', personnelId: 99, personnelName: '旧人员快照', departmentId: 99, departmentName: '不提交', ownerUserId: 999, expectedReturnDate: '2026-01-02', actualReturnDate: '2026-01-03', arbitrary: '不应提交' });
  for (const key of ['borrowerName', 'borrowerIdCard', 'borrowerPhone', 'borrowerUnit', 'registrationDepartment']) assert.equal(cleaned[key], loan()[key]);
  assert.equal(cleaned.handlerId, 23);
  for (const key of ['id', 'status', 'expectedReturnDate', 'actualReturnDate', 'returnCondition', 'returnRemark', 'handlerName', 'handlerIdCard', 'personnelId', 'personnelName', 'departmentId', 'departmentName', 'ownerUserId', 'arbitrary']) assert.equal(Object.hasOwn(cleaned, key), false, key);
  h.queue('PUT')[0].resolve(loan()); await flush(); h.queue('GET')[1].resolve({ list: [loan()], total: 1 }); await flush();
});

test('item loan return posts no date, guards duplicates/cancel and refreshes into immutable history details', async () => {
  const h = itemLoans(); const row = loan({ id: 'id/7' }); h.calls[0].resolve({ list: [row], total: 1 }); await flush();
  h.button(h.host, '登记归还').click(); const modal = h.modal(), form = h.formInstance;
  assert.match(modal.textContent, /整笔归还 2.25 套，不支持部分归还/);
  assert.equal(form.inputs.actualReturnDate, undefined); assert.equal(form.inputs.expectedReturnDate, undefined);
  assert.doesNotMatch(modal.textContent, /预计归还日期|实际归还日期/);
  h.button(modal, '确认归还').click(); assert.equal(h.queue('POST').length, 0);
  form.inputs.returnCondition.value = '完好'; form.inputs.returnRemark.value = '已验收';
  h.button(modal, '确认归还').click(); h.button(modal, '确认归还').dispatch('click'); h.button(modal, '取消').dispatch('click');
  assert.ok(h.document.body.contains(modal)); assert.equal(h.queue('POST').length, 1);
  const request = h.queue('POST')[0]; assert.equal(request.path, '/api/item-loans/id%2F7/return');
  assert.deepEqual(request.data, { returnCondition: '完好', returnRemark: '已验收' });
  request.resolve({ ...row, status: 'returned' }); await flush(); assert.equal(h.document.body.contains(modal), false);
  h.queue('GET')[1].resolve({ list: [{ ...row, status: 'returned', ...request.data }], total: 1 }); await flush();
  assert.equal(h.host.querySelectorAll('[data-act="edit"], [data-act="return"]').length, 0);
  h.button(h.host, '查看').click(); const detail = h.modal();
  for (const text of ['完好', '已验收', '训练使用', '张三（外来借用人）', '00123abcX', '00086-extA', '外部单位 A', '登记单位', '已归还']) assert.ok(detail.textContent.includes(text), text);
  assert.doesNotMatch(detail.textContent, /管理单位|一连|预计归还日期|实际归还日期/);
  assert.equal(detail.querySelectorAll('button').some((el) => el.textContent === '编辑'), false);
  assert.equal(h.queue('DEL').length, 0); assert.equal(h.confirmations.length, 0);
});

test('item loan return validates text lengths and preserves modal on API failure for a safe retry', async () => {
  const h = itemLoans(); h.calls[0].resolve({ list: [loan()], total: 1 }); await flush(); h.button(h.host, '登记归还').click();
  const form = h.formInstance, modal = h.modal();
  form.inputs.returnCondition.value = '字'.repeat(501); h.button(modal, '确认归还').click(); assert.equal(h.queue('POST').length, 0);
  form.inputs.returnCondition.value = '完好'; form.inputs.returnRemark.value = '字'.repeat(1001); h.button(modal, '确认归还').click(); assert.equal(h.queue('POST').length, 0);
  form.inputs.returnRemark.value = ''; h.button(modal, '确认归还').click(); h.queue('POST')[0].reject(new Error('归还请求失败')); await flush();
  assert.match(modal.textContent, /归还请求失败/); assert.equal(h.button(modal, '确认归还').disabled, false); assert.equal(h.queue('GET').length, 1);
  h.button(modal, '确认归还').click(); assert.equal(h.queue('POST').length, 2); h.queue('POST')[1].resolve({}); await flush();
  h.queue('GET')[1].resolve({ list: [], total: 0 }); await flush();
});

for (const value of ['外单位 无内部单位映射', '0', '字'.repeat(200), '<script>单位</script> & "A"']) test(`item loan registration department accepts independent free text: ${JSON.stringify(value)}`, async () => {
  const h = itemLoans(); h.calls[0].resolve({ list: [], total: 0 }); await flush(); h.button(h.host, '新增借用登记').click();
  fillLoan(h, { registrationDepartment: value });
  h.button(h.modal(), '保存').click(); const request = h.queue('POST')[0]; assert.ok(request);
  assert.equal(request.data.registrationDepartment, value); assert.equal(Object.hasOwn(request.data, 'departmentId'), false);
  request.resolve(loan({ registrationDepartment: value })); await flush();
  h.queue('GET')[1].resolve({ list: [loan({ registrationDepartment: value })], total: 1 }); await flush();
  assert.ok(h.host.textContent.includes(value)); assert.equal(h.host.querySelectorAll('script').length, 0);
});

for (const value of ['   ', '字'.repeat(201)]) test(`item loan registration department rejects invalid text length ${value.length}`, async () => {
  const h = itemLoans(); h.calls[0].resolve({ list: [], total: 0 }); await flush(); h.button(h.host, '新增借用登记').click();
  fillLoan(h, { registrationDepartment: value }); h.button(h.modal(), '保存').click();
  assert.equal(h.queue('POST').length, 0); assert.match(h.modal().textContent, /请填写:登记单位|登记单位不能超过200字/);
});

for (const method of ['POST', 'PUT']) test(`item loan ${method} failure preserves free-text fields and blocks duplicate submits/cancel until retry`, async () => {
  const h = itemLoans(); h.calls[0].resolve({ list: method === 'PUT' ? [loan()] : [], total: method === 'PUT' ? 1 : 0 }); await flush();
  h.button(h.host, method === 'PUT' ? '编辑' : '新增借用登记').click();
  const modal = h.modal(); fillLoan(h, { registrationDepartment: '外单位 待登记' });
  h.button(modal, '保存').click(); h.button(modal, '保存').dispatch('click'); h.button(modal, '取消').dispatch('click');
  assert.equal(h.queue(method).length, 1); assert.equal(h.document.body.contains(modal), true);
  h.queue(method)[0].reject(new Error('登记暂时失败')); await flush();
  assert.match(modal.textContent, /登记暂时失败/); assert.equal(h.formInstance.inputs.registrationDepartment.value, '外单位 待登记');
  assert.equal(h.formInstance.inputs.departmentId, undefined); assert.equal(h.queue('GET').length, 1);
  assert.equal(h.button(modal, '保存').disabled, false);
  h.button(modal, '保存').click(); assert.equal(h.queue(method).length, 2);
  assert.equal(h.queue(method)[1].data.registrationDepartment, '外单位 待登记');
  h.queue(method)[1].resolve(loan()); await flush(); h.queue('GET')[1].resolve({ list: [], total: 0 }); await flush();
});

for (const entry of ['快捷归还', '详情归还']) test(`item loan ${entry} targets the clicked loan, confirms explicitly, retains failure guards and refreshes after retry`, async () => {
  const h = itemLoans(); const row = loan({ id: 'target/8', itemName: '第二件物品', status: 'borrowing' });
  h.calls[0].resolve({ list: [loan(), row], total: 2 }); await flush();
  const tr = h.host.querySelectorAll('tbody tr')[1];
  if (entry === '快捷归还') {
    const quick = h.button(tr, '快捷归还'); assert.equal(quick.getAttribute('data-id'), row.id);
    assert.equal(quick.getAttribute('aria-label'), '快捷归还'); assert.equal(quick.classList.contains('btn-primary'), true); quick.click();
  } else {
    h.button(tr, '查看').click(); const detail = h.modal();
    assert.equal(detail.querySelectorAll('.dl').some((el) => el.textContent === '快捷归还'), false);
    h.button(detail, '登记归还').click(); assert.equal(h.document.body.contains(detail), false);
  }
  const form = h.formInstance, modal = h.modal(); assert.match(modal.textContent, /第二件物品/);
  assert.equal(h.queue('POST').length, 0, 'entry opens confirmation form only');
  for (const [key, label] of Object.entries({ returnCondition: '归还情况', returnRemark: '归还备注' })) {
    assert.equal(form.inputs[key].getAttribute('aria-label'), label);
  }
  form.inputs.returnCondition.value = '完好'; form.inputs.returnRemark.value = '验收完成';
  h.button(modal, '确认归还').click(); h.button(modal, '确认归还').dispatch('click'); h.button(modal, '取消').dispatch('click');
  assert.equal(h.queue('POST').length, 1); assert.equal(h.document.body.contains(modal), true);
  assert.equal(h.queue('POST')[0].path, '/api/item-loans/target%2F8/return');
  assert.deepEqual(h.queue('POST')[0].data, { returnCondition: '完好', returnRemark: '验收完成' });
  h.queue('POST')[0].reject(new Error('归还暂时失败')); await flush();
  assert.match(modal.textContent, /归还暂时失败/); assert.equal(form.inputs.returnRemark.value, '验收完成');
  assert.equal(h.button(modal, '确认归还').disabled, false); assert.equal(h.queue('GET').length, 1);
  h.button(modal, '确认归还').click(); assert.equal(h.queue('POST').length, 2);
  h.queue('POST')[1].resolve({}); await flush(); assert.equal(h.document.body.contains(modal), false);
  assert.equal(h.queue('GET').length, 2);
  h.queue('GET')[1].resolve({ list: [loan(), { ...row, status: 'returned' }], total: 2 }); await flush();
  const returned = h.host.querySelectorAll('tbody tr')[1]; assert.equal(returned.querySelectorAll('[data-act="return"], [data-act="edit"]').length, 0);
  h.button(returned, '查看').click(); assert.equal(h.modal().querySelectorAll('button').some((el) => /编辑|登记归还/.test(el.textContent)), false);
});

test('item loan quick-return renderer escapes hostile IDs and never renders an active return control for history', () => {
  const h = itemLoans(); const column = h.c.itemLoanCfg.columns.find((field) => field.key === 'quickReturn');
  const id = 'id/" onclick="evil()"><script>bad</script>&';
  const html = column.render(loan({ id })); assert.doesNotMatch(html, /<script>| onclick="/);
  const cell = h.c.h('td', { html }); const button = cell.querySelector('button');
  assert.equal(cell.querySelectorAll('button').length, 1); assert.equal(button.getAttribute('data-id'), id);
  assert.equal(button.getAttribute('onclick'), null); assert.equal(button.getAttribute('data-act'), 'return');
  assert.equal(column.render(loan({ status: 'returned' })).includes('button'), false);
  h.c.openItemLoanReturn(loan({ status: 'returned' }), { refresh: () => assert.fail('no refresh') });
  assert.equal(h.modal(), undefined); assert.equal(h.queue('POST').length, 0);
});

test('item loan department labels are hidden while free-text snapshots remain escaped throughout', async () => {
  const hostile = '<img src="x" onerror="boom()"> & <script>坏</script>';
  const h = itemLoans([{ id: 3, name: hostile, depth: 0 }]);
  const row = loan({ registrationDepartment: hostile, departmentName: hostile, itemName: hostile });
  h.calls[0].resolve({ list: [row], total: 1 }); await flush();
  assert.equal(h.host.querySelectorAll('img, script').length, 0);
  h.button(h.host, '查看').click(); const detail = h.modal();
  const values = Object.fromEntries(detail.querySelectorAll('.detail-item').map((el) => [el.querySelector('.dl').textContent, el.querySelector('.dv').textContent]));
  assert.equal(values['登记单位'], hostile); assert.equal(values['管理单位（权限归属）'], undefined);
  assert.equal(detail.querySelectorAll('img, script').length, 0);
  h.button(detail, '编辑').click(); assert.equal(h.formInstance.inputs.registrationDepartment.value, hostile);
  assert.equal(h.modal().querySelectorAll('img, script').length, 0); h.button(h.modal(), '取消').click();
  h.button(h.host, '新增借用登记').click();
  assert.equal(h.formInstance.inputs.departmentId, undefined);
  assert.equal(h.formInstance.el.querySelector('.item-loan-auto-department'), null);
  assert.equal(h.modal().querySelectorAll('img, script').length, 0);
});

test('returned item loan rejects forged edit/return actions, still permits history viewing and deletion', async () => {
  const h = itemLoans(); h.calls[0].resolve({ list: [loan({ status: 'returned', returnCondition: '<script>坏</script>' })], total: 1 }); await flush();
  const button = h.button(h.host, '删除');
  for (const key of ['edit', 'return', 'unknown']) { button.setAttribute('data-act', key); button.click(); }
  const quickCell = h.host.querySelector('tbody tr').children[2];
  const forged = h.c.h('button', { 'data-act': 'return', 'data-id': '7', text: '伪造快捷归还' });
  quickCell.appendChild(forged); forged.click(); forged.remove();
  assert.equal(h.modal(), undefined); assert.equal(h.confirmations.length, 0); assert.equal(h.queue('POST').length + h.queue('PUT').length + h.queue('DEL').length, 0);
  h.host.querySelector('[data-view-id]').click(); const modal = h.modal(); assert.ok(modal.textContent.includes('<script>坏</script>')); assert.equal(modal.querySelectorAll('script').length, 0);
  h.button(modal, '关闭').click(); button.setAttribute('data-act', 'del'); button.click(); h.confirmations[0].resolve(true); await flush();
  assert.equal(h.queue('DEL')[0].path, '/api/item-loans/7'); h.queue('DEL')[0].resolve({}); await flush(); h.queue('GET')[1].resolve({ list: [], total: 0 }); await flush();
});

test('CRUD unknown data-act never falls through to deletion in default or configured row actions', async () => {
  for (const configured of [false, true]) {
    const h = harness(); h.page(configured ? { rowActions: [{ key: 'custom', label: '自定义', onClick: (_row, actions) => actions.refresh() }] } : {});
    h.calls[0].resolve(rows(1)); await flush(); const button = h.host.querySelector('button[data-act]'); const original = button.getAttribute('data-act');
    button.setAttribute('data-act', 'unexpected'); button.click(); assert.equal(h.confirmations.length, 0); assert.equal(h.queue('DEL').length, 0);
    if (configured) { button.setAttribute('data-act', original); button.click(); assert.equal(h.queue('GET').length, 2); h.queue('GET')[1].resolve(rows(1)); await flush(); }
  }
});

const backupModules = [{ key: 'personnel', label: '人员管理' }, { key: 'item-loans', label: '物品借还' }, { key: 'dynamic-extra', label: '动态新增模块' }];
async function backupPage(settings, list = []) {
  const h = harness({ realApi: true }); h.c.backupView('backup').render(h.host);
  assert.deepEqual(h.calls.map((call) => call.path), ['/api/backup/modules', '/api/backup/settings']);
  h.calls[0].resolve(backupModules); h.calls[1].resolve(settings); await flush();
  assert.equal(h.calls[2]?.path, '/api/backup/list', h.host.textContent);
  h.calls[2].resolve(list); await flush();
  h.autoCard = h.host.querySelectorAll('.chart-panel').find((el) => el.querySelector('h3')?.textContent === '自动备份');
  h.manualCard = h.host.querySelectorAll('.chart-panel').find((el) => el.querySelector('h3')?.textContent === '手动备份');
  assert.ok(h.autoCard); assert.ok(h.manualCard); return h;
}
const checkedModules = (card) => card.querySelectorAll('.chk-list input').filter((el) => el.checked).map((el) => el.value);

for (const modules of [[], ['personnel'], ['item-loans']]) test(`automatic backup preserves explicit module selection without expanding it: ${JSON.stringify(modules)}`, async () => {
  const h = await backupPage({ enabled: false, modules });
  assert.deepEqual(checkedModules(h.autoCard), modules);
  assert.deepEqual(h.autoCard.querySelectorAll('.chk-list input').map((el) => el.value), backupModules.map((m) => m.key));
  assert.ok(h.autoCard.textContent.includes('物品借还')); assert.ok(h.autoCard.textContent.includes('动态新增模块'));
  assert.deepEqual(checkedModules(h.manualCard), backupModules.map((m) => m.key), 'manual defaults remain independently all-selected');
  h.button(h.autoCard, '保存设置').click();
  if (!modules.length) {
    assert.equal(h.queue('POST').length, 0); assert.match(h.toasts.at(-1).text, /自动备份至少选择一个模块/);
    assert.deepEqual(checkedModules(h.autoCard), [], 'validation must not turn an empty user choice into all modules');
  } else {
    assert.deepEqual(h.queue('POST')[0].data.modules, modules); assert.equal(h.queue('POST')[0].path, '/api/backup/settings');
    h.queue('POST')[0].resolve({ enabled: false, modules }); await flush(); assert.deepEqual(checkedModules(h.autoCard), modules);
  }
});

for (const modules of [undefined, null, 'personnel']) test(`automatic backup defaults to dynamically provided modules only when settings.modules is not an array: ${String(modules)}`, async () => {
  const h = await backupPage({ enabled: false, modules });
  assert.deepEqual(checkedModules(h.autoCard), backupModules.map((m) => m.key));
});

test('dynamic item-loans backup module supports manual selection, record labels and selective restore', async () => {
  const record = { name: 'loans.json', createdText: '2026-10-17', size: 20, modules: { 'item-loans': { item_loan: 2 } } };
  const h = await backupPage({ enabled: false, modules: ['personnel'] }, [record]);
  assert.match(h.host.querySelector('tbody').textContent, /物品借还/);
  h.button(h.manualCard, '全不选').click(); h.manualCard.querySelector('input[value="item-loans"]').checked = true;
  h.button(h.manualCard, '立即备份').click(); assert.equal(h.queue('POST')[0].path, '/api/backup/run');
  assert.deepEqual(h.queue('POST')[0].data.modules, ['item-loans']);
  h.queue('POST')[0].resolve({ size: 20, modules: record.modules }); await flush();
  assert.match(h.toasts.at(-1).text, /备份完成:.*物品借还/);
  h.queue('GET').at(-1).resolve([record]); await flush();
  h.button(h.host, '恢复').click(); const modal = h.modal();
  assert.deepEqual(checkedModules(modal), ['item-loans']);
  h.button(modal, '开始恢复').click(); assert.equal(h.queue('POST')[1].path, '/api/backup/restore');
  assert.deepEqual(h.queue('POST')[1].data, { name: 'loans.json', modules: ['item-loans'] });
  h.c.refreshAll = () => Promise.resolve(); h.queue('POST')[1].resolve({ restored: [{ module: 'item-loans', tables: { item_loan: 2 } }] }); await flush();
  assert.match(h.toasts.at(-1).text, /恢复完成:物品借还\(2条\)/);
  h.queue('GET').at(-1).resolve([record]); await flush();
  assert.deepEqual(checkedModules(h.autoCard), ['personnel'], 'manual and restore operations do not expand automatic selection');
});

const uploadedBackup = (modules = { personnel: { personnel: 3 } }) => ({
  name: 'import-unique.zip', createdText: '2026-10-18 12:00', size: 128, modules,
});
const backupRequests = (h, action) => h.calls.filter((call) => call.path === '/api/backup/' + action);
function chooseBackup(h, file) {
  const input = h.host.querySelector('input[type="file"][accept=".zip"]');
  assert.ok(input, 'production backup ZIP input');
  input.files = file ? [file] : [];
  input.value = file ? 'C:\\fakepath\\' + file.name : '';
  input.dispatch('change');
  assert.equal(input.value, '', 'reset permits reselecting the identical file');
  return input;
}

test('empty backup list keeps the primary upload entry visible and explains the two-step process', async () => {
  const h = await backupPage({ modules: [] });
  const button = h.button(h.host, '上传备份并恢复');
  assert.equal(button.classList.contains('btn-primary'), true);
  assert.equal(button.disabled, false);
  assert.equal(button.closest('table'), null, 'upload is independent of list rows');
  assert.match(h.host.querySelector('.empty-row').textContent, /暂无备份.*上传备份并恢复.*100MB.*确认才会恢复/);
  const input = h.host.querySelector('input[accept=".zip"]');
  let chooserOpened = 0; input.addEventListener('click', () => chooserOpened++);
  button.click(); assert.equal(chooserOpened, 1);
  chooseBackup(h, null);
  assert.equal(h.queue('POST').length, 0, 'cancelling file selection does nothing');
  assert.equal(h.modal(), undefined);
});

for (const [name, size, error] of [
  ['empty.zip', 0, /空备份文件/],
  ['backup.json', 10, /\.zip/],
  ['backup.zip.exe', 10, /\.zip/],
  ['backup', 10, /\.zip/],
  ['huge.zip', 100 * 1024 * 1024 + 1, /100MB/],
]) test(`backup upload rejects invalid file before any request: ${name}`, async () => {
  const h = await backupPage({ modules: [] });
  chooseBackup(h, { name, size });
  assert.equal(h.queue('POST').length, 0);
  assert.match(h.toasts.at(-1).text, error);
  assert.equal(h.button(h.host, '上传备份并恢复').disabled, false);
  assert.equal(h.modal(), undefined);
});

test('uppercase ZIP at exactly 100MB uses the actual token-aware multipart helper; upload only opens confirmation', async () => {
  const h = await backupPage({ modules: [] });
  const file = { name: 'local.ZIP', size: 100 * 1024 * 1024 };
  const input = chooseBackup(h, file);
  const upload = backupRequests(h, 'upload')[0];
  assert.equal(upload.method, 'POST'); assert.ok(upload.body instanceof Multipart);
  assert.equal(upload.body.get('file'), file, 'multipart contains the original selected file');
  assert.equal(upload.body.fields.length, 1);
  assert.deepEqual(upload.headers, { 'X-Auth-Token': 'token-7' }, 'do not set multipart Content-Type manually');
  assert.equal(input.disabled, true);
  assert.equal(h.button(h.host, '正在上传…').disabled, true);
  const record = uploadedBackup({ personnel: { personnel: 3 }, unsupported: { future_table: 2 } });
  upload.resolve(record); await flush();
  assert.equal(input.disabled, false);
  assert.equal(backupRequests(h, 'list').length, 2);
  assert.equal(backupRequests(h, 'restore').length, 0, 'never restore on upload success');
  const modal = h.modal(); assert.ok(modal);
  assert.match(modal.textContent, /2026-10-18 12:00/);
  assert.deepEqual(modal.querySelectorAll('.chk-list input').map((el) => el.value), ['personnel'], 'only the supported/present intersection is offered');
  assert.deepEqual(checkedModules(modal), ['personnel']);
  assert.match(modal.textContent, /自动创建安全备份.*同一个事务.*v4.*回收站照片.*dict.*启动配置/);
  assert.doesNotMatch(modal.textContent, /每个模块独立执行|不.*删除撤回照片/);
  backupRequests(h, 'list').at(-1).resolve([record]); await flush();
  assert.equal(backupRequests(h, 'restore').length, 0);
  assert.match(h.host.querySelector('tbody').textContent, /import-unique.zip/);
  h.button(modal, '取消').click();
  assert.equal(h.modal(), undefined);
  assert.equal(backupRequests(h, 'restore').length, 0);
  assert.equal(backupRequests(h, 'delete').length, 0);
  assert.match(h.host.querySelector('tbody').textContent, /import-unique.zip/, 'cancel keeps the uploaded file available');
  h.button(modal, '开始恢复').dispatch('click');
  assert.equal(backupRequests(h, 'restore').length, 0, 'detached cancelled confirmation cannot restore');
  h.button(h.host, '恢复').click(); assert.deepEqual(checkedModules(h.modal()), ['personnel']);
  h.button(h.modal(), '取消').click();
});

test('upload disables chooser and guards duplicate events; failure permits retrying the identical file', async () => {
  const h = await backupPage({ modules: [] });
  const file = { name: 'same.zip', size: 20 };
  const input = h.host.querySelector('input[accept=".zip"]');
  let chooserOpened = 0; input.addEventListener('click', () => chooserOpened++);
  const button = h.button(h.host, '上传备份并恢复'); button.click();
  chooseBackup(h, file);
  button.click(); button.dispatch('click'); chooseBackup(h, file);
  assert.equal(chooserOpened, 1, 'synthetic clicks cannot bypass the pending lock');
  assert.equal(backupRequests(h, 'upload').length, 1);
  backupRequests(h, 'upload')[0].reject(new Error('网络断开')); await flush();
  assert.equal(input.disabled, false); assert.equal(button.disabled, false);
  assert.equal(button.textContent, '上传备份并恢复'); assert.match(h.toasts.at(-1).text, /上传失败:网络断开.*重试/);
  assert.equal(h.modal(), undefined); assert.equal(backupRequests(h, 'restore').length, 0);
  button.click(); assert.equal(chooserOpened, 2); chooseBackup(h, file);
  assert.equal(backupRequests(h, 'upload').length, 2);
  const record = uploadedBackup(); backupRequests(h, 'upload')[1].resolve(record); await flush();
  backupRequests(h, 'list').at(-1).resolve([record]); await flush();
  assert.ok(h.modal()); assert.equal(backupRequests(h, 'restore').length, 0);
  h.button(h.modal(), '取消').click();
  chooseBackup(h, file); assert.equal(backupRequests(h, 'upload').length, 3, 'same-file selection works after success too');
  backupRequests(h, 'upload')[2].reject(new Error('ZIP 已损坏')); await flush();
  assert.equal(button.disabled, false); assert.equal(backupRequests(h, 'restore').length, 0);
});

for (const modules of [null, {}, { unknown: {} }]) test(`backup restore refuses absent/unsupported modules: ${JSON.stringify(modules)}`, async () => {
  const h = await backupPage({ modules: [] }, [uploadedBackup(modules)]);
  h.button(h.host, '恢复').click();
  assert.equal(h.modal(), undefined); assert.match(h.toasts.at(-1).text, /没有当前系统支持的模块/);
  assert.equal(h.queue('POST').length, 0);
});

test('restore submits only confirmed modules, guards duplicate/cancel/close, and unlocks after failure', async () => {
  const record = uploadedBackup({ personnel: {}, 'item-loans': {}, unsupported: {} });
  const h = await backupPage({ modules: [] }, [record]);
  h.button(h.host, '恢复').click(); const modal = h.modal();
  const boxes = modal.querySelectorAll('.chk-list input');
  assert.deepEqual(boxes.map((el) => el.value), ['personnel', 'item-loans']);
  boxes.forEach((box) => { box.checked = false; });
  const start = h.button(modal, '开始恢复'), cancel = h.button(modal, '取消');
  start.click(); assert.equal(backupRequests(h, 'restore').length, 0);
  assert.match(h.toasts.at(-1).text, /至少勾选一个模块/);
  boxes[1].checked = true; start.click(); start.dispatch('click');
  assert.equal(start.disabled, true); assert.equal(cancel.disabled, true); assert.ok(boxes.every((box) => box.disabled));
  cancel.dispatch('click'); modal.querySelector('.dialog-close').click(); modal.dispatch('keydown', { key: 'Escape' }); modal.parentNode.dispatch('mousedown');
  assert.ok(h.document.body.contains(modal), 'in-flight recovery cannot lose its confirmation');
  h.button(h.host, '恢复').click(); assert.equal(h.document.body.querySelectorAll('.dialog').length, 1);
  assert.equal(backupRequests(h, 'restore').length, 1);
  assert.deepEqual(backupRequests(h, 'restore')[0].data, { name: record.name, modules: ['item-loans'] });
  assert.deepEqual(backupRequests(h, 'restore')[0].headers, { 'X-Auth-Token': 'token-7', 'Content-Type': 'application/json' });
  backupRequests(h, 'restore')[0].reject(new Error('恢复校验失败')); await flush();
  assert.equal(start.disabled, false); assert.equal(start.textContent, '开始恢复'); assert.equal(cancel.disabled, false);
  assert.ok(boxes.every((box) => !box.disabled)); assert.match(h.toasts.at(-1).text, /恢复校验失败/);
  assert.deepEqual(checkedModules(modal), ['item-loans']);
  boxes[0].checked = true; boxes[1].checked = false; start.click(); start.dispatch('click');
  assert.equal(backupRequests(h, 'restore').length, 2);
  assert.deepEqual(backupRequests(h, 'restore')[1].data, { name: record.name, modules: ['personnel'] });
  backupRequests(h, 'restore')[1].resolve({ restored: [{ module: 'personnel', tables: { personnel: 3 } }],
    photos: 2, configFiles: ['config.json'], requiresRestart: true, warnings: ['兼容提示'] }); await flush();
  assert.equal(h.modal(), undefined); assert.deepEqual(h.renders, ['refreshAll']);
  assert.match(h.toasts.at(-2).text, /恢复完成:人员管理\(3条\).*照片2张.*系统配置1个.*重启后生效/);
  assert.match(h.toasts.at(-1).text, /兼容提示/);
  backupRequests(h, 'list').at(-1).resolve([record]); await flush();
  start.dispatch('click'); assert.equal(backupRequests(h, 'restore').length, 2, 'completed modal cannot resubmit');
});

for (const dismiss of ['close', 'escape', 'backdrop']) test(`restore ${dismiss} cancels without a restore request`, async () => {
  const h = await backupPage({ modules: [] }, [uploadedBackup()]); h.button(h.host, '恢复').click(); const modal = h.modal();
  if (dismiss === 'close') modal.querySelector('.dialog-close').click();
  if (dismiss === 'escape') modal.dispatch('keydown', { key: 'Escape' });
  if (dismiss === 'backdrop') modal.parentNode.dispatch('mousedown');
  assert.equal(h.modal(), undefined); assert.equal(backupRequests(h, 'restore').length, 0);
});

test('requiresRelogin uses real forceLogin, clears both storages and never requests protected refresh data', async () => {
  const h = await backupPage({ modules: [] }, [uploadedBackup()]);
  for (const store of [h.c.localStorage, h.c.sessionStorage]) { store.setItem('tk', 'token-7'); store.setItem('usr', '{"userId":7}'); }
  h.button(h.host, '恢复').click(); h.button(h.modal(), '开始恢复').click();
  const requestsBefore = h.calls.length;
  backupRequests(h, 'restore')[0].resolve({ restored: [], requiresRelogin: true, warnings: ['账号已恢复'] }); await flush();
  assert.equal(h.c.TOKEN, ''); assert.equal(h.c.USER, null);
  for (const store of [h.c.localStorage, h.c.sessionStorage]) { assert.equal(store.getItem('tk'), null); assert.equal(store.getItem('usr'), null); }
  assert.equal(h.modal(), undefined); assert.equal(h.calls.length, requestsBefore, 'no list/me/refresh calls with a revoked token');
  assert.equal(h.renders.length, 1); assert.equal(h.renders[0][0], 'login'); assert.match(h.renders[0][1], /恢复完成.*重新登录/);
});

test('upload 401 follows the existing authentication helper without opening restore', async () => {
  const h = await backupPage({ modules: [] });
  h.c.fetch = () => Promise.resolve({ status: 401 });
  chooseBackup(h, { name: 'backup.zip', size: 20 }); await flush();
  assert.equal(h.c.TOKEN, ''); assert.equal(h.c.USER, null); assert.equal(h.renders[0][0], 'login');
  assert.equal(h.modal(), undefined); assert.equal(backupRequests(h, 'restore').length, 0);
  assert.equal(h.button(h.host, '上传备份并恢复').disabled, false);
});

test('late pre-upload empty list cannot overwrite the refreshed uploaded backup', async () => {
  const h = harness({ realApi: true }); h.c.backupView('backup').render(h.host);
  h.calls[0].resolve(backupModules); h.calls[1].resolve({ modules: [] }); await flush();
  const initial = backupRequests(h, 'list')[0];
  chooseBackup(h, { name: 'backup.zip', size: 20 });
  const record = uploadedBackup(); backupRequests(h, 'upload')[0].resolve(record); await flush();
  backupRequests(h, 'list')[1].resolve([record]); await flush();
  initial.resolve([]); await flush();
  assert.match(h.host.querySelector('tbody').textContent, /import-unique.zip/);
  h.button(h.modal(), '取消').click();
  h.button(h.host, '恢复').click(); assert.ok(h.modal());
  assert.equal(backupRequests(h, 'restore').length, 0); h.button(h.modal(), '取消').click();
});

// Extract and execute the actual nested route function with the real settingsView/backupView.
function settingsRoute(h, savedTab) {
  const c = h.c;
  c.localStorage.setItem('settingsTab', savedTab);
  Object.assign(c, { content: h.host, currentShell: () => true, canAccessView: () => true,
    refreshDicts() {}, menuItems: {}, groupParents: {}, GROUP_OF: {}, lastViewKey: 'user:7:lastView',
    titleEl: c.h('div'), location: { hash: '' } });
  c.history = { replaceState(_a, _b, hash) { c.location.hash = hash; } };
  const route = source.match(/^  function navigate\(view\) \{[\s\S]*?^  \}/m);
  assert.ok(route, 'actual production navigate function');
  vm.runInContext(route[0], c);
}
for (const oldTab of ['basic', 'theme', 'report']) test(`#backup opens the real backup tab regardless of saved ${oldTab} setting`, async () => {
  const h = harness({ realApi: true }); settingsRoute(h, oldTab); h.c.navigate('backup');
  assert.equal(h.c.location.hash, '#backup'); assert.equal(h.c.currentView, 'backup');
  assert.equal(h.c.localStorage.getItem('user:7:lastView'), 'backup');
  assert.deepEqual(h.host.querySelectorAll('.tab.active').map((el) => el.textContent), ['备份恢复']);
  assert.deepEqual(h.calls.map((call) => call.path), ['/api/backup/modules', '/api/backup/settings']);
  h.calls[0].resolve(backupModules); h.calls[1].resolve({ modules: [] }); await flush(); h.calls[2].resolve([]); await flush();
  const button = h.button(h.host, '上传备份并恢复');
  assert.match(button.closest('.chart-panel').parentNode.parentNode.parentNode.getAttribute('style'), /display:block/);
  assert.equal(h.host.textContent.includes('导出上报数据包'), false);
  const n = h.calls.length; h.host.querySelectorAll('.tab')[2].click(); assert.equal(h.calls.length, n, 'backup tab remains lazy-loaded once');
});

test('#settings still honors the remembered tab and does not eagerly load backup', () => {
  const h = harness({ realApi: true }); settingsRoute(h, 'theme'); h.c.navigate('settings');
  assert.equal(h.c.location.hash, '#settings');
  assert.deepEqual(h.host.querySelectorAll('.tab.active').map((el) => el.textContent), ['主题与显示']);
  assert.equal(h.calls.length, 0);
});

function dictPage(items) {
  const h = harness();
  h.c.Ctx.dictTypes = [{ type: 'holiday_type', name: '节假日', items }];
  h.c.refreshDicts = () => Promise.resolve();
  const build = h.c.buildForm;
  h.c.buildForm = (...args) => { h.formInstance = build(...args); return h.formInstance; };
  h.c.dictView().render(h.host);
  return h;
}

const dictItemsSample = () => [
  { id: 1, dictType: 'holiday_type', dictLabel: '甲', sort: 1, enabled: true, parentId: 0, remark: '' },
  { id: 2, dictType: 'holiday_type', dictLabel: '乙', sort: 2, enabled: true, parentId: 0, remark: '' },
  { id: 3, dictType: 'holiday_type', dictLabel: '丙', sort: 4, enabled: true, parentId: 0, remark: '' }
];

test('dictionary new options default to the end and rows reorder within a sibling group', async () => {
  const h = dictPage(dictItemsSample());
  const rows = h.host.querySelectorAll('tbody tr[data-id]');
  assert.deepEqual(rows.map((row) => row.getAttribute('data-id')), ['1', '2', '3']);
  assert.deepEqual(rows.map((row) => row.getAttribute('draggable')), ['true', 'true', 'true']);
  assert.ok(rows[0].children[0].querySelector('.dict-drag-handle'), 'drag handle is the leftmost cell');
  assert.equal(rows[0].children[1].textContent, '甲');
  assert.equal(rows[0].children[2].textContent, '1');
  h.button(h.host, '新增字典选项').click();
  assert.equal(String(h.formInstance.inputs.sort.value), '5');
  h.button(h.modal(), '取消').click();

  const transfer = { setData() {} };
  rows[2].dispatch('dragstart', { dataTransfer: transfer });
  rows[0].dispatch('dragover', { clientY: 105, dataTransfer: transfer });
  rows[0].dispatch('drop', { dataTransfer: transfer });
  const request = h.queue('PUT')[0];
  assert.equal(request.path, '/api/dict/reorder');
  assert.deepEqual(request.data, { dictType: 'holiday_type', ids: [3, 1, 2] });
  request.resolve({}); await flush();
  assert.ok(h.toasts.some((item) => item.text === '排序已保存'));
});

test('dictionary drag sorting ignores a row from another parent group', () => {
  const items = [
    { id: 1, dictType: 'holiday_type', dictLabel: '甲', sort: 1, enabled: true, parentId: 0, remark: '' },
    { id: 4, dictType: 'holiday_type', dictLabel: '甲一', sort: 1, enabled: true, parentId: 1, remark: '' },
    { id: 5, dictType: 'holiday_type', dictLabel: '甲二', sort: 2, enabled: true, parentId: 1, remark: '' },
    { id: 2, dictType: 'holiday_type', dictLabel: '乙', sort: 2, enabled: true, parentId: 0, remark: '' }
  ];
  const h = dictPage(items);
  const rows = h.host.querySelectorAll('tbody tr[data-id]');
  assert.deepEqual(rows.map((row) => row.getAttribute('data-id')), ['1', '4', '5', '2']);
  const transfer = { setData() {} };
  rows[1].dispatch('dragstart', { dataTransfer: transfer });
  rows[3].dispatch('dragover', { clientY: 105, dataTransfer: transfer });
  rows[3].dispatch('drop', { dataTransfer: transfer });
  assert.equal(h.queue('PUT').length, 0);
});

// The five personnel/dashboard requirements run actual production DOM and transports.
function loadPersonnel(h) {
  h.c.personnelPhotoBlock = () => null;
  h.c.dictLabels = () => [];
  h.c.loadRowPhotos = () => {};
  h.c.validateIdCard = () => ({ gender: '男', birthday: '1990-01-01' });
  for (const key of ['POSITION_SUB', 'REWARD_SUB', 'FAMILY_SUB', 'DRIVER_SUB', 'CARD_SUB', 'PVEHICLE_SUB', 'HOUSING_SUB']) h.c[key] = {};
  vm.runInContext(source.match(/^var personnelCfg = \{[\s\S]*?^\};/m)[0], h.c);
  return h.c.personnelCfg;
}
function loadDashboard(h) {
  for (const name of ['DASH_COLORS', 'DASH_DIMS', 'DASH_KINDS', 'DASH_DEFAULT', 'DASH_OLD_WIDGET', 'DRILL_COLS']) {
    const constant = source.match(new RegExp(`^var ${name} = [\\s\\S]*?;`, 'm'));
    assert.ok(constant, name); vm.runInContext(constant[0], h.c);
  }
  h.c.location = { hash: '#stats' };
  h.c.window.addEventListener = () => {};
}
const departmentGroups = [
  { id: 10, parentId: 0, name: '某营', path: '某营', depth: 0, directCount: 1, count: 8 },
  { id: 12, parentId: 10, name: '同名连', path: '某营同名连', depth: 1, directCount: 2, count: 7 },
  { id: 13, parentId: 12, name: '排', path: '某营同名连排', depth: 2, directCount: 0, count: 5 },
  { id: 14, parentId: 13, name: '班', path: '某营同名连排班', depth: 3, directCount: 5, count: 5 },
  { id: 21, parentId: 0, name: '另营', path: '另营', depth: 0, directCount: 0, count: 3 },
  { id: 22, parentId: 21, name: '同名连', path: '另营同名连', depth: 1, directCount: 3, count: 3 },
  { id: 0, parentId: 0, name: '未分配', path: '未分配', depth: 0, directCount: 2, count: 2 },
];

test('department hierarchy preserves every preorder node, true depth, long paths and separate direct/cumulative counts', () => {
  const h = harness(); const list = [...departmentGroups];
  for (let i = 0; i < 20; i++) list.push({ id: 100 + i, parentId: 14, name: '深层' + i, path: '某营' + '长路径'.repeat(i + 1) + '末级', depth: i + 4, directCount: 1, count: 1 });
  const original = JSON.stringify(list); const chart = h.c.drawDepartmentHierarchy(list); h.host.appendChild(chart);
  const bars = chart.querySelectorAll('.department-hierarchy-row');
  assert.deepEqual(bars.map((row) => row.getAttribute('data-department-id')), list.map((row) => String(row.id)));
  assert.equal(bars.length, 27); assert.equal(JSON.stringify(list), original);
  for (let i = 0; i < list.length; i++) {
    assert.equal(bars[i].querySelector('.department-hierarchy-label').textContent, list[i].path);
    assert.ok(bars[i].getAttribute('title').includes(list[i].path));
    assert.equal(bars[i].getAttribute('data-depth'), String(list[i].depth));
    assert.equal(bars[i].querySelector('.department-hierarchy-meta'), null);
    assert.doesNotMatch(bars[i].textContent, /第\d+级|ID \d+|直属/);
    assert.equal(bars[i].querySelector('.department-hierarchy-count').textContent, list[i].count + ' 人');
  }
  assert.ok(bars[0].getAttribute('style') !== bars[1].getAttribute('style'));
  assert.notEqual(bars[0].querySelector('.department-hierarchy-track').firstChild.getAttribute('style'), bars[1].querySelector('.department-hierarchy-track').firstChild.getAttribute('style'));
  assert.equal(chart.querySelector('.department-depth-legend'), null);
  assert.equal(chart.querySelector('.department-hierarchy-note'), null);
  assert.doesNotMatch(chart.textContent, /各行不可相加|仅当前账号可见人员|第1级（根）/);
  assert.equal(bars[6].disabled, true); assert.equal(bars[6].getAttribute('data-api'), null);
  assert.match(bars[6].querySelector('.department-hierarchy-track').firstChild.getAttribute('style'), /#8a97a8/);
  assert.equal(chart.querySelectorAll('svg').length, 0);
});

test('legacy department layouts normalize without deleting/reordering other widgets or changing sizes', () => {
  const h = harness(); loadDashboard(h);
  for (const kind of ['bar', 'donut', 'ring', 'rose', 'treemap', 'hierarchy']) {
    const layout = [{ dim: 'rank', kind: 'bar', size: 's' }, { dim: 'department', kind, size: 'l' }, { dim: 'status', kind: 'rose', size: 'm' }];
    const migrated = plain(h.c.dashMigrate(layout));
    assert.deepEqual(migrated, [layout[0], { dim: 'department', kind: 'hierarchy', size: 'l' }, layout[2]]);
    assert.deepEqual(plain(h.c.dashMigrate(migrated)), migrated);
  }
  assert.deepEqual(plain(h.c.dashMigrate(['deptTree', { id: 'dept', size: 's' }, 'status'])), [
    { dim: 'department', kind: 'hierarchy', size: 'm' }, { dim: 'department', kind: 'hierarchy', size: 's' }, { dim: 'status', kind: 'donut', size: 'm' }]);
  assert.deepEqual(plain(h.c.kindsForDim(h.c.DASH_DIMS.department)), ['hierarchy']);
  assert.deepEqual(plain(h.c.dashMigrate({})), []);
});

test('real dashboard renders cumulative totals independently and drills identical department names by ID without lookup', async () => {
  const h = harness(); loadDashboard(h);
  h.c.Ctx.deptFlat = [{ id: 999, name: '同名连' }]; // Deliberately wrong name lookup source.
  h.c.localStorage.setItem(h.c.preferenceKey('dashWidgets'), JSON.stringify([{ dim: 'department', kind: 'rose', size: 'l' }, { dim: 'locationPoint', kind: 'bar', size: 'm' }]));
  h.c.dashboardView().render(h.host);
  h.calls.find((call) => call.path === '/api/stats/dashboard').resolve({ total: 13, groups: { department: departmentGroups,
    locationPoint: [{ name: '点位 A&B', count: 11 }, { name: '未填写', count: 2 }] } });
  h.calls.find((call) => call.path === '/api/dashboard/events').resolve([]); await flush();
  assert.equal(h.host.querySelector('.stat-card .num').textContent, '13');
  assert.equal(h.host.querySelectorAll('.department-hierarchy-row').length, 7);
  assert.equal(h.host.querySelectorAll('.dash-card')[0].getAttribute('data-wid'), 'department:hierarchy');
  assert.equal(h.host.querySelectorAll('svg').length, 0);
  for (const id of ['12', '22']) {
    h.host.querySelector('[data-department-id="' + id + '"]').click();
    const query = new URLSearchParams(h.c.location.hash.split('?')[1]);
    assert.equal(query.get('departmentId'), id); assert.equal(query.get('includeDescendants'), 'true');
  }
  assert.equal(h.host.querySelector('[data-department-id="0"]').getAttribute('data-api'), null);
  const point = h.host.querySelector('[data-param="locationPoint"]'); point.click();
  assert.equal(h.c.location.hash, 'personnel?locationPoint=' + encodeURIComponent('点位 A&B'));
  const missing = h.host.querySelectorAll('.bar-row').find((row) => row.textContent.includes('未填写'));
  assert.equal(missing.getAttribute('data-api'), null);
  assert.equal(h.calls.filter((call) => call.path.indexOf('/api/personnel?') === 0).length, 0);
});

test('stat cards render as focusable buttons routing to modules with status filters', async () => {
  const h = harness(); loadDashboard(h);
  h.c.location = { hash: '#stats' };
  h.c.dashboardView().render(h.host);
  h.calls.find((call) => call.path === '/api/stats/dashboard').resolve({ total: 13, onDuty: 10, onLeave: 3, groups: {} });
  h.calls.find((call) => call.path === '/api/dashboard/events').resolve([]); await flush();
  const cards = h.host.querySelectorAll('.stat-card-btn');
  assert.ok(cards.length >= 3, 'stat cards render as buttons');
  assert.equal(cards[0].tagName, 'BUTTON');
  const leave = [...cards].find((c) => c.textContent.includes('休假中'));
  assert.ok(leave, 'onLeave card exists');
  leave.click();
  assert.equal(h.c.location.hash, 'personnel?status=' + encodeURIComponent('休假'));
});

test('personnel route accepts only dictionary status values from card links', () => {
  const h = harness();
  assert.ok(h.c.statusOptions().indexOf('休假') >= 0, 'fixture status options contain 休假');
  h.c.location = { hash: '#personnel?status=' + encodeURIComponent('休假') };
  assert.deepEqual(plain(h.c.readPersonnelRouteFilters()), { status: '休假' });
  h.c.location = { hash: '#personnel?status=' + encodeURIComponent('自定义状态') };
  assert.deepEqual(plain(h.c.readPersonnelRouteFilters()), {});
});

test('keyboard Enter on a focused drillable bar opens the same drill dialog as click', async () => {
  const h = harness(); loadDashboard(h);
  h.c.localStorage.setItem(h.c.preferenceKey('dashWidgets'), JSON.stringify([{ dim: 'leaveType', kind: 'bar', size: 'm' }]));
  h.c.dashboardView().render(h.host);
  h.calls.find((call) => call.path === '/api/stats/dashboard').resolve({ total: 8, groups: { leaveType: [{ name: '年假', count: 6 }] } });
  h.calls.find((call) => call.path === '/api/dashboard/events').resolve([]); await flush();
  const bar = h.host.querySelectorAll('.bar-row.drillable')[0];
  assert.ok(bar, 'drillable bar exists');
  assert.equal(bar.getAttribute('tabindex'), '0');
  assert.equal(bar.getAttribute('role'), 'button');
  bar.dispatch('keydown', { key: 'Enter' });
  assert.ok(h.modal(), 'drill dialog opened by keyboard Enter');
});

test('personnel route whitelist accepts only true subtree flags with real IDs and ignores download options/bad encoding', () => {
  const h = harness();
  for (const [hash, expected] of [
    ['#personnel?departmentId=22&includeDescendants=true&marks=party&sort=name&fields=x', { departmentId: '22', includeDescendants: 'true' }],
    ['#personnel?locationPoint=A%26B&includeDescendants=true', { locationPoint: 'A&B' }],
    ['#personnel?departmentId=0&includeDescendants=true', {}], ['#personnel?departmentId=abc&includeDescendants=true', {}],
    ['#personnel?departmentId=22&includeDescendants=false', { departmentId: '22' }],
    ['#personnel?locationPoint=%E0%A4%A&includeDescendants=true', {}], ['#stats?departmentId=22&includeDescendants=true', {}],
  ]) { h.c.location = { hash }; assert.deepEqual(plain(h.c.readPersonnelRouteFilters()), expected); }
});

for (const action of ['change', 'clear', 'reset']) test(`personnel subtree ${action} removes hidden flag before query and prevents refresh leakage`, async () => {
  const h = harness(); h.c.location = { hash: '#personnel?departmentId=22&includeDescendants=true' };
  h.c.history = { replaceState(_a, _b, hash) { h.c.location.hash = hash; } };
  h.page({ key: 'personnel', endpoint: '/api/personnel', filters: [{ key: 'name', label: '姓名' },
    { key: 'departmentId', label: '单位', type: 'select', options: [{ v: '12', l: '某营同名连' }, { v: '22', l: '另营同名连' }] }] });
  assert.match(h.calls[0].path, /departmentId=22&includeDescendants=true/);
  const flag = h.host.querySelector('[data-fkey="includeDescendants"]'); assert.equal(flag.value, 'true');
  const dept = h.host.querySelector('[data-fkey="departmentId"]'); assert.equal(dept.value, '22');
  assert.equal(dept.querySelectorAll('.dd-item').length, 3);
  h.calls[0].resolve(rows(1)); await flush();
  const name = h.host.querySelector('[data-fkey="name"]'); name.value = '保留其他筛选';
  if (action === 'reset') h.button(h.host.querySelector('.filter-actions'), '重置').click();
  else { dept.value = action === 'clear' ? '' : '12'; dept.dispatch('change'); }
  const url = new URL(h.calls.at(-1).path, 'http://unit.test');
  assert.equal(url.searchParams.has('includeDescendants'), false); assert.equal(flag.value, '');
  assert.equal(h.c.location.hash, '#personnel'); assert.equal(h.host.querySelector('.subtree-filter-note').style.display, 'none');
  assert.equal(url.searchParams.get('name'), action === 'reset' ? null : '保留其他筛选');
  assert.equal(url.searchParams.get('departmentId'), action === 'change' ? '12' : null);
});

test('location route prepopulates only locationPoint and ordinary department selection remains direct', () => {
  const h = harness(); h.c.location = { hash: '#personnel?locationPoint=' + encodeURIComponent('某点位') };
  h.page({ key: 'personnel', endpoint: '/api/personnel', filters: [{ key: 'departmentId', label: '单位', type: 'select', options: ['12'] }],
    moreFilters: [{ key: 'locationPoint', label: '所在点位', type: 'select', options: ['某点位'] }] });
  assert.equal(h.host.querySelector('[data-fkey="locationPoint"]').value, '某点位');
  assert.equal(h.host.querySelector('[data-fkey="departmentId"]').value, '');
  assert.equal(new URL(h.calls[0].path, 'http://unit.test').searchParams.has('includeDescendants'), false);
});

test('department flattening builds paths only from server-authorized nodes without fabricating hidden ancestors', async () => {
  const h = harness(); vm.runInContext(definition('refreshDepts'), h.c);
  const pending = h.c.refreshDepts(); h.calls[0].resolve([{ id: 12, name: '可见连', children: [{ id: 14, name: '可见班', children: [] }] }]); await pending;
  assert.deepEqual(plain(h.c.Ctx.deptFlat).map((row) => [row.id, row.path]), [[12, '可见连'], [14, '可见连可见班']]);
  assert.deepEqual(plain(h.c.Ctx.deptTree).map((row) => row.id), [12]);
});

test('personnel picker shows full paths, keeps leaf compatibility, and selects same-name people by ID', async () => {
  const h = harness(); let picked;
  h.c.Ctx.deptFlat = [{ id: 14, name: '班', path: '某营一连二排班', depth: 3 }];
  h.c.openPersonPicker((person) => { picked = plain(person); });
  const full = '某营一连二排班';
  h.calls[0].resolve({ list: [{ id: 31, name: '同名', idCard: 'A', departmentId: 14, departmentName: '班', departmentPath: full },
    { id: 32, name: '同名', idCard: 'B', departmentName: '旧叶子' }], total: 2 }); await flush();
  const modal = h.modal(); const paths = modal.querySelectorAll('.picker-row .personnel-department-path');
  assert.equal(paths[0].textContent, full); assert.equal(paths[0].getAttribute('title'), full); assert.equal(paths[1].textContent, '旧叶子');
  assert.match(modal.querySelector('.dd-item[data-v="14"]').textContent, /某营一连二排班/);
  modal.querySelectorAll('.picker-row')[0].click();
  assert.equal(picked.id, 31); assert.equal(picked.departmentName, '班'); assert.equal(picked.departmentPath, full); assert.equal(picked.departmentId, 14);
});

test('selected-person summary shows full path and clears it with the identity match', async () => {
  const h = harness(); const form = h.form([{ key: 'card', label: '身份证' }, { key: 'name', label: '姓名' }, { key: 'pid', type: 'hidden' }]);
  h.c.setupPersonLookup(form, { idCard: 'card', name: 'name', pid: 'pid' });
  h.pick; h.c.pick({ id: 31, name: '甲', idCard: 'A', departmentName: '班', departmentPath: '营 / 连 / 排 / 班' });
  const summary = form.el.querySelector('.person-lookup-summary');
  assert.equal(summary.textContent, '营 / 连 / 排 / 班'); assert.equal(summary.title, summary.textContent);
  form.inputs.card.value = 'B'; form.inputs.card.dispatch('input'); assert.equal(summary.textContent, ''); assert.equal(summary.title, '');
});

test('personnel list/detail prefer escaped full path and historical military ID displays unfilled', () => {
  const h = harness(); const cfg = loadPersonnel(h);
  const full = '营 / 连 / 排 / <班>&'; const row = { id: 31, name: '甲', departmentId: 14, departmentName: '班', departmentPath: full };
  const col = cfg.columns.find((column) => column.key === 'departmentPath');
  const holder = h.c.h('div', { html: col.render(row) }); const path = holder.querySelector('.personnel-department-path');
  const displayPath = '营连排<班>&';
  assert.equal(path.textContent, displayPath); assert.equal(path.getAttribute('title'), displayPath); assert.equal(path.tagName, 'SPAN'); assert.equal(path.children.length, 0);
  assert.match(col.render({ departmentName: '旧叶子' }), /旧叶子/);
  assert.equal(cfg.columns.find((column) => column.key === 'militaryIdNo').render(row), '未填写');
  h.c.openDetailModal({ ...cfg, form: cfg.form.filter((field) => ['departmentId', 'militaryIdNo'].includes(field.key)) }, row);
  assert.equal(h.modal().querySelector('.personnel-department-path').textContent, displayPath);
  assert.equal(h.modal().querySelector('.personnel-department-path').getAttribute('title'), displayPath);
  assert.match(h.modal().textContent, /军人证件号未填写/);
});

for (const value of ['', ' ', '\t\n']) test(`military ID blank ${JSON.stringify(value)} is a native and generic required error, never copied from citizen ID`, () => {
  const h = harness(); const cfg = loadPersonnel(h);
  const fields = cfg.form.filter((field) => ['idCard', 'militaryIdNo'].includes(field.key));
  const form = h.form(fields, { idCard: '110101199001010011', militaryIdNo: value });
  assert.equal(form.inputs.militaryIdNo.getAttribute('type'), 'text');
  assert.equal(form.inputs.militaryIdNo.getAttribute('required'), 'required');
  assert.equal(form.inputs.militaryIdNo.getAttribute('aria-required'), 'true');
  assert.equal(form.inputs.militaryIdNo.value, value); assert.equal(form.get(), null);
  assert.match(form.el.textContent, /军人证件号/); assert.equal(form.inputs.militaryIdNo.classList.contains('bad'), true);
  assert.ok(form.inputs.militaryIdNo.parentElement.querySelector('.req'));
  assert.doesNotMatch(form.el.textContent, /独立必填文本|历史空值需补齐|不自动使用身份证号/);
  form.inputs.militaryIdNo.value = ' 任意格式 / 000A<&> '; const data = form.get();
  assert.equal(data.militaryIdNo, '任意格式 / 000A<&>'); assert.equal(data.idCard, '110101199001010011');
  assert.match(h.c.importRuleText('personnel'), /重新下载新模板.*必填「军人证件号」/);
});

for (const editing of [false, true]) test(`personnel ${editing ? 'PUT edit' : 'POST create'} submits militaryIdNo independently and blocks blank legacy edit`, async () => {
  const h = harness(); const cfg = loadPersonnel(h);
  h.c.Ctx.deptFlat = [{ id: 14, name: '班', path: '营 / 连 / 排 / 班', depth: 3 }];
  h.page({ ...cfg, form: cfg.form.filter((field) => ['name', 'idCard', 'militaryIdNo', 'departmentId'].includes(field.key)),
    columns: cfg.columns.filter((column) => ['idCard', 'militaryIdNo', 'departmentPath'].includes(column.key)),
    formTop: null, subPanels: null, formSetup: null, toolbarExtra: null, filters: [], moreFilters: [], excel: false });
  h.calls[0].resolve({ list: editing ? [{ id: 31, name: '甲', idCard: 'citizen', departmentId: 14 }] : [], total: editing ? 1 : 0 }); await flush();
  if (editing) h.button(h.host, '编辑').click(); else h.button(h.host, '新增人员信息').click();
  const modal = h.modal(); const inputs = modal.querySelectorAll('input');
  inputs[0].value = '甲'; inputs[1].value = 'citizen';
  const military = inputs[2]; assert.equal(military.value, ''); modal.querySelector('.dd').value = '14';
  h.button(modal, '保存').click(); assert.equal(h.queue(editing ? 'PUT' : 'POST').length, 0);
  assert.match(modal.textContent, /请填写:军人证件号/);
  military.value = ' 独立军人证件 / 000 '; h.button(modal, '保存').click();
  const request = h.queue(editing ? 'PUT' : 'POST')[0]; assert.ok(request);
  assert.equal(request.path, '/api/personnel' + (editing ? '/31' : ''));
  assert.equal(request.data.militaryIdNo, '独立军人证件 / 000'); assert.equal(request.data.idCard, 'citizen'); assert.equal(request.data.departmentId, '14');
  assert.equal(Object.hasOwn(request.data, 'departmentPath'), false); assert.equal(Object.hasOwn(request.data, 'departmentName'), false);
});

test('driver default chart migration is exact, idempotent and remembers deliberate removal', () => {
  const h = harness(); loadDashboard(h);
  const previous = [
    { dim: 'department', kind: 'hierarchy', size: 'm' }, { dim: 'locationPoint', kind: 'bar', size: 'm' },
    { dim: 'status', kind: 'donut', size: 'm' }, { dim: 'entryTrend', kind: 'columns', size: 'l' },
    { dim: 'leaveType', kind: 'bar', size: 'm' },
  ];
  const original = JSON.stringify(previous);
  const migrated = plain(h.c.dashDriverLayout(previous));
  assert.equal(migrated.length, 6); assert.equal(migrated[5].dim, 'driverLicense');
  assert.equal(migrated[0].driverLicenseDefaultVersion, 1); assert.equal(JSON.stringify(previous), original);
  assert.deepEqual(plain(h.c.dashDriverLayout(migrated)), migrated);
  const removed = migrated.filter((widget) => widget.dim !== 'driverLicense');
  assert.equal(h.c.dashDriverLayout(removed).length, 5);
  assert.equal(h.c.dashDriverLayout(removed).some((widget) => widget.dim === 'driverLicense'), false);
  assert.ok(plain(h.c.DASH_DEFAULT).some((widget) => widget.dim === 'driverLicense'));
});

test('driver chart migration preserves custom sizes/orders and cannot sanitize an invalid layout into defaults', () => {
  const h = harness(); loadDashboard(h);
  const previous = [
    { dim: 'department', kind: 'hierarchy', size: 'm' }, { dim: 'locationPoint', kind: 'bar', size: 'm' },
    { dim: 'status', kind: 'donut', size: 'm' }, { dim: 'entryTrend', kind: 'columns', size: 'l' },
    { dim: 'leaveType', kind: 'bar', size: 'm' },
  ];
  for (const custom of [previous.map((widget, index) => ({ ...widget, size: index === 0 ? 'l' : widget.size })),
    [...previous].reverse(), previous.slice(1), [...previous, { dim: 'invalid', kind: 'bar', size: 'm' }]]) {
    const result = plain(h.c.dashDriverLayout(custom));
    assert.equal(result.some((widget) => widget.dim === 'driverLicense'), false);
    assert.deepEqual(result.map(({ dim, kind, size }) => ({ dim, kind, size })), plain(h.c.dashMigrate(custom)));
  }
});

test('driver chart maps both labels to exact yes/no personnel routes without opening a truncated detail query', async () => {
  const h = harness(); loadDashboard(h);
  h.c.localStorage.setItem(h.c.preferenceKey('dashWidgets'), JSON.stringify([{ dim: 'driverLicense', kind: 'bar', size: 'm' }]));
  h.c.dashboardView().render(h.host);
  h.calls.find((call) => call.path === '/api/stats/dashboard').resolve({ total: 7, groups: { driverLicense: [{ name: '有驾驶证', count: 5 }, { name: '无驾驶证', count: 2 }] } });
  h.calls.find((call) => call.path === '/api/dashboard/events').resolve([]); await flush();
  const bars = h.host.querySelectorAll('[data-param="driverLicense"]');
  assert.equal(bars.length, 2); assert.equal(h.host.querySelector('.stat-card .num').textContent, '7');
  for (const value of ['yes', 'no']) {
    bars.find((row) => row.getAttribute('data-val') === value).click();
    assert.equal(new URLSearchParams(h.c.location.hash.split('?')[1]).get('driverLicense'), value);
  }
  const before = h.c.location.hash;
  h.c.openDrillModal('不应导航', '/api/personnel', 'driverLicense', 'unknown');
  assert.equal(h.c.location.hash, before);
  assert.equal(h.calls.filter((call) => call.path.indexOf('/api/personnel?') === 0).length, 0);
});

test('visible license filters are populated from safe routes, sent together and cleared on reset', async () => {
  const h = harness(); const cfg = loadPersonnel(h);
  const fields = cfg.filters.concat(cfg.moreFilters).filter((field) => ['driverLicense', 'driverLicenseType', 'driverLicenseStatus'].includes(field.key));
  assert.equal(fields.length, 3);
  assert.deepEqual(plain(fields[0].options).map((option) => option.v), ['yes', 'no']);
  h.c.dictLabels = (type) => type === 'license_type' ? ['临时证'] : [];
  h.c.location = { hash: '#personnel?driverLicense=yes&driverLicenseType=' + encodeURIComponent('临时证') + '&driverLicenseStatus=' + encodeURIComponent('有效') + '&fields=driverLicenseNo&marks=driver' };
  assert.deepEqual(plain(h.c.readPersonnelRouteFilters()), { driverLicense: 'yes', driverLicenseType: '临时证', driverLicenseStatus: '有效' });
  h.c.history = { replaceState(_a, _b, hash) { h.c.location.hash = hash; } };
  h.page({ key: 'personnel', endpoint: '/api/personnel', filters: fields });
  const first = new URL(h.calls[0].path, 'http://unit.test').searchParams;
  assert.equal(first.get('driverLicense'), 'yes'); assert.equal(first.get('driverLicenseType'), '临时证'); assert.equal(first.get('driverLicenseStatus'), '有效');
  assert.equal(h.host.querySelectorAll('[data-fkey="driverLicense"]').length, 1);
  h.calls[0].resolve(rows(0)); await flush();
  h.button(h.host.querySelector('.filter-actions'), '重置').click();
  const cleared = new URL(h.calls.at(-1).path, 'http://unit.test').searchParams;
  for (const key of ['driverLicense', 'driverLicenseType', 'driverLicenseStatus']) assert.equal(cleared.has(key), false);
  h.c.location.hash = '#personnel?driverLicense=maybe&driverLicenseType=unknown';
  assert.deepEqual(plain(h.c.readPersonnelRouteFilters()), {});
});

test('changed production functions remain ES5: no new syntax, Promise.finally, fetch or padStart', () => {
  const functions = names.map(definition).join('\n');
  assert.doesNotMatch(functions, /\b(?:let|const|class|async|await)\s+\w|=>|\.finally\(|\.padStart\(|\bfetch\(/);
});
