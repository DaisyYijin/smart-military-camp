'use strict';

// Actual production functions only: form fields, list tag, filters, route whitelist,
// dashboard actualDuty drill, detail modal — no bootstrap, HTTP or business data.
// node --test personnel-server/src/test/frontend/personnel-actual-duty.test.cjs
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const base = resolve(__dirname, '../../main/resources/static');
const source = readFileSync(resolve(base, 'app.js'), 'utf8');
function definition(name) {
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = source.match(new RegExp(`^function ${escaped}\\([^\\n]*\\) \\{[^\\n]*\\}$`, 'm'))
    || source.match(new RegExp(`^function ${escaped}\\([^\\n]*\\) \\{[\\s\\S]*?^\\}`, 'm'));
  assert.ok(match, `production function ${name}`);
  return match[0];
}
const names = ['$', '$all', 'h', 'esc', 'openModal', 'parseCalendarDay', 'formatCalendarDay',
  'makeDateField', 'buildForm', 'attachPersonnelAvatar', 'attachColumnResize', 'aug1EasterEgg', 'martyrEasterEgg', 'oct1EasterEgg', 'lateNightCare', 'openMessageCenter', 'openMessageCompose', 'setupPersonLookup', 'setupLeaveAutoCalc', 'saveSubRecordBatch',
  'crudView', 'buildTrendChart', 'preferenceKey', 'importRuleText', 'importFileErrorMessage', 'importResultSummary', 'openImportResultModal', 'dashMigrate', 'markDashDriverLayout', 'dashDriverLayout', 'dashboardView', 'openChangePassword', 'subRecordsPanel',
  'makeDropdown', 'itemLoanStatus', 'setupItemLoanForm', 'openItemLoanDetail', 'openItemLoanReturn', 'fmtSize', 'backupView',
  'dictItems', 'dictView', 'settingsView', 'readPersonnelRouteFilters', 'kindsForDim', 'dashCardLabel',
  'departmentDepthColor', 'drawDepartmentHierarchy', 'dashBarList', 'openDrillModal', 'openPersonPicker', 'openDetailModal', 'statusOptions', 'badge', 'driverLicenseOptions',
  'prefersReducedMotion', 'countUp', 'skeletonRows'];
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
    const bare = selector.replace(/\[[^\]]*\]/g, '');
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
    query() { this.button(host.querySelector('.filter-actions'), '查询').click(); } };
}
const rows = (...ids) => ({ list: ids.map((id) => ({ id, name: `人员${id}` })), total: ids.length });

// The personnel/dashboard requirements run actual production DOM and transports.
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
  for (const name of ['DASH_COLORS', 'DASH_DIMS', 'DASH_KINDS', 'DASH_DEFAULT', 'DASH_OLD_WIDGET']) {
    const constant = source.match(new RegExp(`^var ${name} = [\\s\\S]*?;`, 'm'));
    assert.ok(constant, name); vm.runInContext(constant[0], h.c);
  }
  h.c.location = { hash: '#stats' };
  h.c.window.addEventListener = () => {};
}

/* ================= 表单实际单位字段 ================= */

test('personnel form defines the single optional actual-unit field right after the department', () => {
  const h = harness(); const cfg = loadPersonnel(h);
  const keys = cfg.form.filter((field) => !field.section).map((field) => field.key);
  assert.ok(keys.indexOf('actualDepartmentId') === keys.indexOf('departmentId') + 1, 'actual unit follows department');
  const dept = cfg.form.find((field) => field.key === 'departmentId');
  const actual = cfg.form.find((field) => field.key === 'actualDepartmentId');
  assert.equal(dept.required, true); assert.equal(actual.required, undefined); assert.equal(actual.type, 'dept');
  assert.match(actual.hint, /留空表示在编在岗/);
  assert.equal(cfg.form.filter((field) => field.key === 'actualPost' || field.key === 'actualPostDate').length, 0,
    '实际岗位/实际在岗开始时间表单字段已删除');
});

for (const editing of [false, true]) test(`personnel ${editing ? 'edit' : 'create'} submits the actual unit only and clearing is allowed`, async () => {
  const h = harness(); const cfg = loadPersonnel(h);
  h.c.Ctx.deptFlat = [{ id: 12, name: '一连', path: '某营一连', depth: 1 }, { id: 14, name: '外援连', path: '友邻营外援连', depth: 1 }];
  const keys = ['name', 'departmentId', 'actualDepartmentId'];
  h.page({ ...cfg, key: 'personnel', endpoint: '/api/personnel', form: cfg.form.filter((field) => keys.includes(field.key)),
    columns: cfg.columns.filter((column) => column.key === 'departmentPath'),
    formTop: null, subPanels: null, toolbarExtra: null, filters: [], moreFilters: [], excel: false });
  h.calls[0].resolve({ list: editing ? [{ id: 31, name: '甲', departmentId: 12, actualDepartmentId: 14 }] : [], total: editing ? 1 : 0 }); await flush();
  if (editing) h.button(h.host, '编辑').click(); else h.button(h.host, '新增人员信息').click();
  let modal = h.modal();
  let dropdowns = modal.querySelectorAll('.dd');
  assert.equal(dropdowns.length, 2); // 编制单位 + 实际单位
  const name = modal.querySelector('input'); if (!editing) name.value = '甲';
  if (!editing) { dropdowns[0].value = '12'; dropdowns[1].value = '14'; }
  h.button(modal, '保存').click();
  const request = h.queue(editing ? 'PUT' : 'POST')[0]; assert.ok(request);
  if (editing) {
    assert.equal(request.data.actualDepartmentId, '14');
  } else {
    assert.equal(request.data.actualDepartmentId, '14');
    assert.equal(request.data.departmentId, '12');
  }
  assert.equal(request.data.actualPost, undefined, 'payload no longer carries actualPost');
  assert.equal(request.data.actualPostDate, undefined, 'payload no longer carries actualPostDate');
  request.resolve({ id: 31 }); await flush();
  // 清空:不选实际单位,照常提交空串
  h.button(h.host, '新增人员信息').click();
  modal = h.modal();
  modal.querySelector('input').value = '乙';
  dropdowns = modal.querySelectorAll('.dd');
  dropdowns[0].value = '12'; dropdowns[1].value = '';
  h.button(modal, '保存').click();
  const cleared = h.queue('POST').at(-1);
  assert.equal(cleared.data.actualDepartmentId, '');
  assert.equal(cleared.data.actualPost, undefined);
  assert.equal(cleared.data.actualPostDate, undefined);
  cleared.resolve({ id: 32 }); await flush();
});

test('edit form normalizes stored actualDepartmentId 0 to an empty optional submit', async () => {
  const h = harness(); const cfg = loadPersonnel(h);
  const adField = cfg.form.find((field) => field.key === 'actualDepartmentId');
  const form = h.form([adField], { actualDepartmentId: 0 });
  assert.equal(form.inputs.actualDepartmentId.value, '0');
  cfg.formSetup(form, { id: 31, actualDepartmentId: 0 });
  assert.equal(form.inputs.actualDepartmentId.value, '');
  const data = form.get();
  assert.equal(data.actualDepartmentId, '');
  assert.equal('actualPost' in data, false); assert.equal('actualPostDate' in data, false);
});

/* ================= 列表小标签 ================= */

test('list tag renders only when the actual unit differs and escapes hostile text', () => {
  const h = harness(); const cfg = loadPersonnel(h);
  const col = cfg.columns.find((column) => column.key === 'departmentPath');
  const render = (row) => h.c.h('div', { html: col.render(row) });
  // 未设置(0/null)与实际=编制 都不显示
  for (const row of [{ departmentId: 12 }, { departmentId: 12, actualDepartmentId: 0 },
    { departmentId: 12, actualDepartmentId: null }, { departmentId: 12, actualDepartmentId: '12' }]) {
    const holder = render(row);
    assert.equal(holder.children.length, 1, 'only the establishment path span');
    assert.ok(holder.firstChild.classList.contains('personnel-department-path'));
  }
  // 差异时追加内联小标签:只显示实际单位路径末段,不再拼岗位
  const hostile = { departmentId: 12, departmentPath: '某营 / 一连', actualDepartmentId: 14,
    actualDepartmentPath: '友邻营 / 外援<连>' };
  const holder = render(hostile);
  assert.equal(holder.children.length, 2);
  const tag = holder.children[1];
  assert.equal(tag.tagName, 'SPAN');
  assert.match(tag.getAttribute('style'), /font-size:12px/);
  assert.match(tag.getAttribute('style'), /#8a97a8/);
  assert.equal(tag.textContent, '实际：外援<连>');
  assert.equal(tag.getAttribute('title'), '友邻营 / 外援<连>');
  assert.ok(holder.innerHTML.indexOf('<span') >= 0 && holder.innerHTML.indexOf('&lt;') > 0, 'escaped');
  // 无完整路径时退到实际单位名
  const leafOnly = render({ departmentId: 12, actualDepartmentId: 14, actualDepartmentName: '外援连' });
  assert.equal(leafOnly.children[1].textContent, '实际：外援连');
  assert.equal(leafOnly.children[1].getAttribute('title'), '外援连');
});

/* ================= 筛选与路由 ================= */

test('moreFilters add on-duty and actual-unit selects right after the three license filters', () => {
  const h = harness(); const cfg = loadPersonnel(h);
  const keys = cfg.moreFilters.map((field) => field.key);
  assert.equal(keys.indexOf('onDuty'), keys.indexOf('driverLicenseStatus') + 1);
  assert.equal(keys.indexOf('actualDepartmentId'), keys.indexOf('onDuty') + 1);
  const onDuty = cfg.moreFilters.find((field) => field.key === 'onDuty');
  assert.equal(onDuty.type, 'select');
  assert.deepEqual(plain(onDuty.options), [{ v: 'yes', l: '在编在岗' }, { v: 'no', l: '借岗在外' }]);
  const actual = cfg.moreFilters.find((field) => field.key === 'actualDepartmentId');
  h.c.Ctx.deptFlat = [{ id: 12, name: '一连', path: '某营一连', depth: 1 }, { id: 3, name: '营部', depth: 0 }];
  assert.deepEqual(plain(actual.options()), [{ v: '12', l: '某营一连' }, { v: '3', l: '营部' }]);
});

test('on-duty and actual-unit filters reach the query together and reset clears them', async () => {
  const h = harness(); const cfg = loadPersonnel(h);
  h.c.Ctx.deptFlat = [{ id: 12, name: '一连', path: '某营一连', depth: 1 }];
  const fields = cfg.moreFilters.filter((field) => ['onDuty', 'actualDepartmentId'].includes(field.key));
  h.page({ key: 'personnel', endpoint: '/api/personnel', filters: fields });
  const onDutyEl = h.host.querySelector('[data-fkey="onDuty"]');
  const actualEl = h.host.querySelector('[data-fkey="actualDepartmentId"]');
  assert.ok(onDutyEl && actualEl);
  onDutyEl.value = 'no'; actualEl.value = '12';
  h.query();
  const q = new URL(h.calls.at(-1).path, 'http://unit.test').searchParams;
  assert.equal(q.get('onDuty'), 'no'); assert.equal(q.get('actualDepartmentId'), '12');
  assert.equal(q.has('includeDescendants'), false);
  h.calls.at(-1).resolve(rows()); await flush();
  h.button(h.host.querySelector('.filter-actions'), '重置').click();
  const cleared = new URL(h.calls.at(-1).path, 'http://unit.test').searchParams;
  assert.equal(cleared.has('onDuty'), false); assert.equal(cleared.has('actualDepartmentId'), false);
  assert.equal(onDutyEl.value, ''); assert.equal(actualEl.value, '');
});

test('route whitelist accepts onDuty yes/no and positive actualDepartmentId only on #personnel', () => {
  const h = harness();
  for (const [hash, expected] of [
    ['#personnel?onDuty=yes', { onDuty: 'yes' }],
    ['#personnel?onDuty=no', { onDuty: 'no' }],
    ['#personnel?actualDepartmentId=12', { actualDepartmentId: '12' }],
    ['#personnel?onDuty=no&actualDepartmentId=12', { onDuty: 'no', actualDepartmentId: '12' }],
    ['#personnel?onDuty=maybe', {}], ['#personnel?actualDepartmentId=0', {}], ['#personnel?actualDepartmentId=abc', {}],
    ['#personnel?actualDepartmentId=12.5', {}], ['#personnel?actualDepartmentId=-12', {}],
    ['#personnel?actualDepartmentId=%E0%A4%A', {}],
    ['#stats?onDuty=yes&actualDepartmentId=12', {}],
    ['#personnel?marks=party&sort=name&fields=x&actualUnit=true', {}],
  ]) { h.c.location = { hash }; assert.deepEqual(plain(h.c.readPersonnelRouteFilters()), expected, hash); }
  // includeDescendants 仍只挂在 departmentId 上
  h.c.location = { hash: '#personnel?actualDepartmentId=12&includeDescendants=true' };
  assert.deepEqual(plain(h.c.readPersonnelRouteFilters()), { actualDepartmentId: '12' });
});

test('actual-duty route prepopulates both new controls and the first query carries them', async () => {
  const h = harness(); const cfg = loadPersonnel(h);
  h.c.Ctx.deptFlat = [{ id: 12, name: '一连', path: '某营一连', depth: 1 }];
  const fields = cfg.moreFilters.filter((field) => ['onDuty', 'actualDepartmentId', 'driverLicense'].includes(field.key));
  h.c.location = { hash: '#personnel?onDuty=no&actualDepartmentId=12&actualUnit=true&marks=party' };
  h.c.history = { replaceState(_a, _b, hash) { h.c.location.hash = hash; } };
  h.page({ key: 'personnel', endpoint: '/api/personnel', filters: fields });
  assert.equal(h.host.querySelector('[data-fkey="onDuty"]').value, 'no');
  assert.equal(h.host.querySelector('[data-fkey="actualDepartmentId"]').value, '12');
  assert.equal(h.host.querySelector('[data-fkey="driverLicense"]').value, '');
  const q = new URL(h.calls[0].path, 'http://unit.test').searchParams;
  assert.equal(q.get('onDuty'), 'no'); assert.equal(q.get('actualDepartmentId'), '12');
  assert.equal(q.has('actualUnit'), false); assert.equal(q.has('marks'), false);
  assert.equal(q.has('includeDescendants'), false);
  h.calls[0].resolve(rows()); await flush();
});

/* ================= 仪表盘 actualDuty ================= */

test('actualDuty dimension is optional, unpicked by default and offers regular chart kinds', () => {
  const h = harness(); loadDashboard(h);
  const dim = h.c.DASH_DIMS.actualDuty;
  assert.equal(dim.cat, '人员管理'); assert.equal(dim.label, '实际在岗分布');
  assert.equal(dim.group, 'actualDuty'); assert.equal(dim.unit, '人');
  assert.deepEqual(plain(dim.drill), { api: '/api/personnel', param: 'actualDepartmentId' });
  assert.ok(plain(h.c.DASH_DEFAULT).every((widget) => widget.dim !== 'actualDuty'), 'not in defaults');
  assert.deepEqual(plain(h.c.kindsForDim(dim)), ['bar', 'donut', 'rose', 'treemap']);
  // 迁移不注入新卡
  const saved = [{ dim: 'status', kind: 'donut', size: 'm' }];
  assert.ok(plain(h.c.dashDriverLayout(saved)).every((widget) => widget.dim !== 'actualDuty'));
});

test('actualDuty bars resolve full paths to department IDs and unmatched paths stay unclickable', async () => {
  const h = harness(); loadDashboard(h);
  h.c.Ctx.deptFlat = [{ id: 12, name: '一连', path: '某营一连', depth: 1 }, { id: 14, name: '二连', path: '某营二连', depth: 1 }];
  h.c.localStorage.setItem(h.c.preferenceKey('dashWidgets'), JSON.stringify([{ dim: 'actualDuty', kind: 'bar', size: 'm' }]));
  h.c.dashboardView().render(h.host);
  h.calls.find((call) => call.path === '/api/stats/dashboard').resolve({ total: 4, groups: { actualDuty: [
    { name: '某营一连', count: 2 }, { name: '某营 / 二连', count: 1 }, { name: '营外临时单位', count: 1 }] } });
  h.calls.find((call) => call.path === '/api/dashboard/events').resolve([]); await flush();
  const bars = h.host.querySelectorAll('[data-param="actualDepartmentId"]');
  assert.equal(bars.length, 2); // 未匹配路径无 data-api → 不可点
  assert.equal(h.host.querySelectorAll('.bar-row').length, 3);
  for (const [id, name] of [['12', '某营一连'], ['14', '某营 / 二连']]) {
    const bar = bars.find((row) => row.getAttribute('data-val') === id);
    assert.ok(bar, id); assert.equal(bar.getAttribute('data-api'), '/api/personnel');
    assert.ok(/实际在岗分布/.test(bar.getAttribute('data-title')) && bar.getAttribute('data-title').indexOf(name) >= 0, bar.getAttribute('data-title'));
  }
  const unmatchable = h.host.querySelectorAll('.bar-row').find((row) => row.textContent.includes('营外临时单位'));
  assert.equal(unmatchable.getAttribute('data-api'), null); assert.equal(unmatchable.classList.contains('drillable'), false);
  // 点击下钻:按实际单位精确过滤,不带编制的 includeDescendants
  bars.find((row) => row.getAttribute('data-val') === '12').click();
  const q = new URLSearchParams(h.c.location.hash.split('?')[1]);
  assert.equal(q.get('actualDepartmentId'), '12'); assert.equal(q.has('includeDescendants'), false);
  h.c.openDrillModal('不应导航', '/api/personnel', 'actualDepartmentId', '0');
  assert.equal(h.c.location.hash.split('?')[1], 'actualDepartmentId=12');
  h.c.openDrillModal('不应导航', '/api/personnel', 'actualDepartmentId', '');
  assert.equal(h.c.location.hash.split('?')[1], 'actualDepartmentId=12');
  assert.equal(h.calls.filter((call) => call.path.indexOf('/api/personnel?') === 0).length, 0);
});

/* ================= 详情弹窗 ================= */

test('detail modal shows the full actual path beside the establishment and 0 renders empty', () => {
  const h = harness(); const cfg = loadPersonnel(h);
  const fields = cfg.form.filter((field) => ['departmentId', 'actualDepartmentId'].includes(field.key));
  const full = '友邻营 / 外援<连>';
  h.c.openDetailModal({ ...cfg, form: fields, subPanels: null }, { id: 31, name: '甲', departmentId: 12, departmentPath: '某营 / 一连',
    actualDepartmentId: 14, actualDepartmentPath: full });
  const modal = h.modal();
  const values = modal.querySelectorAll('.dv');
  assert.equal(values[0].textContent, '某营一连');
  assert.equal(values[1].textContent, '友邻营外援<连>'); assert.equal(values[1].getAttribute('title'), '友邻营外援<连>');
  assert.ok(modal.textContent.includes('实际单位'));
  assert.equal(modal.textContent.includes('实际岗位'), false, '实际岗位字段已删除');
  assert.equal(modal.textContent.includes('实际在岗开始时间'), false, '实际在岗开始时间字段已删除');
  h.c.openDetailModal({ ...cfg, form: fields, subPanels: null }, { id: 31, name: '甲', departmentId: 12, departmentPath: '某营 / 一连', actualDepartmentId: 0 });
  const onDutyModal = h.modal();
  assert.equal(onDutyModal.querySelectorAll('.dv')[1].textContent, '—', '0 means on-duty in the establishment unit');
});

/* ================= 版本与 ES5 ================= */

test('resource versions stay at least 316/82/259 and additions remain ES5', () => {
  const html = readFileSync(resolve(base, 'index.html'), 'utf8');
  const version = (pattern) => Number(html.match(pattern)[1]);
  assert.ok(version(/app\.js\?v=(\d+)/) >= 316, 'app.js version never regresses');
  assert.ok(version(/ext-features\.js\?v=(\d+)/) >= 82, 'ext-features.js version never regresses');
  assert.ok(version(/app\.css\?v=(\d+)/) >= 259, 'app.css version never regresses');
  const changed = [source.match(/^var personnelCfg = \{[\s\S]*?^\};/m)[0],
    source.match(/^var DASH_DIMS = \{[\s\S]*?^\};/m)[0],
    definition('openDetailModal'), definition('openDrillModal'), definition('readPersonnelRouteFilters'), definition('dashboardView')].join('\n');
  assert.doesNotMatch(changed, /\b(?:let|const|class|async|await)\s+\w|=>|\.finally\(|\.padStart\(|\bfetch\(/);
});
