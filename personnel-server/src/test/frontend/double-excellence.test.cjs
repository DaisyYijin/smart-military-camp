'use strict';

// Production view/form/identity functions with a lightweight DOM and isolated transports.
// The main page is a year-switched all-people matrix: 单位/姓名/身份证号码 + four quarter cells.
// No HTTP, Java, production database, or browser dependency is used.
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const source = readFileSync(resolve(__dirname, '../../main/resources/static/app.js'), 'utf8');
function definition(name) {
  name = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = source.match(new RegExp(`^function ${name}\\([^\\n]*\\) \\{[^\\n]*\\}$`, 'm'))
    || source.match(new RegExp(`^function ${name}\\([^\\n]*\\) \\{[\\s\\S]*?^\\}`, 'm'));
  assert.ok(match, `production function ${name} exists`); return match[0];
}
const flush = () => new Promise((done) => setImmediate(done));
const plain = (value) => JSON.parse(JSON.stringify(value));
class Element {
  constructor(tag, text = '') {
    this.tagName = tag.toUpperCase(); this.nodeType = tag === '#text' ? 3 : 1;
    this.nodes = []; this.parentNode = null; this.attributes = {}; this.style = {}; this.listeners = {};
    this._text = text; this.value = ''; this.disabled = false; this.checked = false; this.readOnly = false;
    this.classList = {
      contains: (name) => this.className.split(/\s+/).includes(name),
      add: (name) => { if (!this.classList.contains(name)) this.className = `${this.className} ${name}`.trim(); },
      remove: (name) => { this.className = this.className.split(/\s+/).filter((x) => x !== name).join(' '); },
      toggle: (name, force) => { const on = force === undefined ? !this.classList.contains(name) : !!force; this.classList[on ? 'add' : 'remove'](name); },
    };
  }
  get children() { return this.nodes.filter((node) => node.nodeType === 1); }
  get parentElement() { return this.parentNode; }
  get className() { return this.attributes.class || ''; }
  set className(value) { this.attributes.class = String(value); }
  get firstChild() { return this.nodes[0] || null; }
  setAttribute(key, value) { this.attributes[key] = String(value); if (key === 'disabled') this.disabled = true; if (key === 'value') this.value = String(value); }
  getAttribute(key) { return Object.hasOwn(this.attributes, key) ? this.attributes[key] : null; }
  appendChild(node) { node.remove(); this.nodes.push(node); node.parentNode = this; return node; }
  replaceChild(next, old) { const at = this.nodes.indexOf(old); next.remove(); this.nodes[at] = next; next.parentNode = this; old.parentNode = null; }
  remove() { if (this.parentNode) { this.parentNode.nodes.splice(this.parentNode.nodes.indexOf(this), 1); } this.parentNode = null; }
  contains(node) { return node === this || this.nodes.some((child) => child.contains(node)); }
  get textContent() { return this._text + this.nodes.map((node) => node.textContent).join(''); }
  set textContent(value) { this.clear(); this._text = String(value); }
  clear() { for (const node of this.nodes) node.parentNode = null; this.nodes = []; this._text = ''; }
  set innerHTML(value) { assert.ok(this.tagName === 'LABEL' || !value.includes('<'), 'business data never uses unescaped HTML'); this.clear(); this._text = String(value).replace(/<[^>]*>/g, ''); }
  get innerHTML() { return this._text; }
  matches(selector) {
    const match = /^(?:([a-z][\w-]*)|#([\w-]+))?((?:\.[\w-]+)*)(?:\[([\w-]+)(?:="([^"]*)")?\])?$/.exec(selector);
    assert.ok(match, `unsupported DOM selector ${selector}`);
    return (!match[1] || match[1].toUpperCase() === this.tagName) && (!match[2] || this.getAttribute('id') === match[2])
      && match[3].split('.').filter(Boolean).every((name) => this.classList.contains(name))
      && (!match[4] || this.getAttribute(match[4]) !== null && (match[5] === undefined || this.getAttribute(match[4]) === match[5]));
  }
  querySelectorAll(selector) {
    const selectors = selector.split(',').map((part) => part.trim()), found = [];
    for (const node of this.children) {
      if (selectors.some((part) => node.matches(part))) found.push(node);
      found.push(...node.querySelectorAll(selector));
    }
    return found;
  }
  querySelector(selector) { return this.querySelectorAll(selector)[0] || null; }
  addEventListener(type, callback) { (this.listeners[type] ||= []).push(callback); }
  removeEventListener(type, callback) { this.listeners[type] = (this.listeners[type] || []).filter((fn) => fn !== callback); }
  dispatch(type, extra = {}) { const event = { target: this, key: '', preventDefault() {}, stopPropagation() {}, ...extra }; for (const callback of this.listeners[type] || []) callback(event); return event; }
  dispatchEvent(event) { this.dispatch(event.type, event); }
  click() { if (!this.disabled) this.dispatch('click'); }
  focus() {}
  scrollIntoView() {}
  getBoundingClientRect() { return { left: 10, top: 10, bottom: 40, width: 150 }; }
}
function harness(t) {
  const body = new Element('body'), host = new Element('div'); body.appendChild(host);
  const document = { body, createElement: (tag) => new Element(tag), createTextNode: (text) => new Element('#text', text),
    querySelector: (s) => body.querySelector(s), querySelectorAll: (s) => body.querySelectorAll(s) };
  const calls = [], messages = [], confirms = [], modals = [], forms = [], progresses = [], results = [], downloads = [];
  const timers = new Map(), observers = []; let clock = 0, timerId = 0;
  function tick(ms) {
    const until = clock + ms;
    while (true) {
      const next = [...timers].filter(([, timer]) => timer.at <= until).sort((a, b) => a[1].at - b[1].at)[0];
      if (!next) break;
      clock = next[1].at; timers.delete(next[0]); next[1].fn();
    }
    clock = until;
  }
  const c = vm.createContext({ document, TOKEN: 'child-token', USER: { userId: 20, isAdmin: false }, SITE: { pageSize: 2 },
    setTimeout(fn, ms) { const id = ++timerId; timers.set(id, { fn, at: clock + ms }); return id; }, clearTimeout(id) { timers.delete(id); },
    MutationObserver: class { constructor(callback) { this.callback = callback; observers.push(this); } observe() { this.active = true; } disconnect() { this.active = false; } },
    Ctx: { deptFlat: [{ id: 2, name: '本单位', path: '上级 / 本单位' }, { id: 3, name: '下级', path: '上级 / 本单位 / 下级' }] },
    dictLabels: () => ['好', '中', '一般'], // deRatingOptions 默认三档,与生产空字典回退一致
    window: { innerWidth: 1200, innerHeight: 900 }, CustomEvent: function (type) { this.type = type; },
    FormData: class { constructor() { this.entries = []; } append(key, value) { this.entries.push([key, value]); } },
    toast: (message, type) => messages.push({ message, type }), showFieldBubble() {},
    confirmBox: (message) => { confirms.push(message); return Promise.resolve(true); },
    showProgressModal(title) { const progress = { title, closed: false, setStatus() {}, setProgress() {}, close() { this.closed = true; } }; progresses.push(progress); return progress; },
    importResultSummary: (r) => `新增 ${r.added || 0}，更新 ${r.updated || 0}，失败 ${r.failed || 0}`,
    openImportResultModal: (...args) => results.push(plain(args)), downloadBlob: (...args) => downloads.push(args),
  });
  function request(method, path, data) {
    const call = { method, path, data, settled: false };
    call.promise = new Promise((resolve, reject) => { call.resolve = (value) => { call.settled = true; resolve(value); }; call.reject = (e) => { call.settled = true; reject(e); }; });
    calls.push(call); return call.promise;
  }
  c.GET = (path) => request('GET', path); c.POST = (path, data) => request('POST', path, plain(data));
  c.PUT = (path, data) => request('PUT', path, plain(data)); c.DEL = (path) => request('DELETE', path);
  c.apiBlobWithProgress = (path, progress) => { progress(20); return request('DOWNLOAD', path); };
  c.postFormWithProgress = (path, data, progress) => { progress(100); return request('UPLOAD', path, data); };
  c.openModal = (options) => {
    const dialog = new Element('div'), footer = new Element('footer'); dialog.appendChild(options.body);
    for (const button of options.footer || []) footer.appendChild(button);
    dialog.appendChild(footer); body.appendChild(dialog);
    const modal = { dialog, options, close(force) { if (!force && options.beforeClose && !options.beforeClose()) return; dialog.remove(); if (options.onClose) options.onClose(); } };
    modals.push(modal); return modal;
  };
  for (const name of ['$', '$all', 'h', 'esc', 'parseCalendarDay', 'formatCalendarDay', 'makeDropdown', 'buildForm', 'attachPersonnelAvatar', 'attachColumnResize', 'aug1EasterEgg', 'martyrEasterEgg', 'oct1EasterEgg', 'lateNightCare', 'openMessageCenter', 'openMessageCompose', 'attachPersonPick', 'setupPersonLookup',
    'renderPager', 'slideFrom', 'loadAllRows', 'deRatingOptions', 'doubleExcellenceFields', 'doubleExcellencePayload', 'doubleExcellenceQuery', 'doubleExcellenceView',
    'yearMatrixView', 'cellFormValues', 'prefersReducedMotion']) vm.runInContext(definition(name), c);
  // 评级→样式类映射是单行 var 声明,definition() 只抽取函数,单独注入生产定义
  const ratingCls = source.match(/^var DE_RATING_CLS = \{.*\};$/m);
  assert.ok(ratingCls, 'production DE_RATING_CLS exists');
  vm.runInContext(ratingCls[0], c);
  // 动效令牌与矩阵视图配置(多行 var 对象,definition() 只抽取函数,同样单独注入生产定义)
  const motion = source.match(/^var MOTION = \{[\s\S]*?^\};$/m);
  assert.ok(motion, 'production MOTION exists');
  vm.runInContext(motion[0], c);
  const matrixCfg = source.match(/^var doubleExcellenceMatrixCfg = \{[\s\S]*?^\};$/m);
  assert.ok(matrixCfg, 'production doubleExcellenceMatrixCfg exists');
  vm.runInContext(matrixCfg[0], c);
  c.makeDateField = () => {
    const input = c.h('input', { type: 'text' }), wrap = c.h('div', { class: 'cal' }, input);
    Object.defineProperties(wrap, { value: { get: () => { const parsed = c.parseCalendarDay(input.value); return parsed ? c.formatCalendarDay(parsed) : ''; }, set: (v) => { input.value = v; } }, rawValue: { get: () => input.value } });
    return wrap;
  };
  const realBuild = c.buildForm;
  c.buildForm = (...args) => { const form = realBuild(...args); form.fields = plain(args[0]); forms.push(form); return form; };
  let picker;
  c.openPersonPicker = (accept) => { picker = accept; };
  const view = c.doubleExcellenceView(); view.render(host);
  t.after(() => { view.destroy(); assert.deepEqual(calls.filter((call) => !call.settled).map((call) => call.path), [], 'all mocked requests settled'); });
  return { c, host, body, calls, messages, confirms, modals, forms, progresses, results, downloads, view, tick, timers,
    observe() { for (const observer of observers) if (observer.active) observer.callback(); },
    pick: (person) => picker(person),
    button(text, root = host) { const button = root.querySelectorAll('button').find((el) => el.textContent === text); assert.ok(button, `button ${text} exists`); return button; },
    missingButton(text, root = host) { assert.equal(root.querySelectorAll('button').find((el) => el.textContent === text), undefined, `button ${text} must not exist`); },
    field: (key) => host.querySelector(`[data-fkey="${key}"]`),
    last: () => calls.at(-1),
  };
}
const person = { personnelId: 44, personnelName: '现有人员', idCard: '110101199001010010', departmentPath: '上级 / 本单位', year: 2026,
  q1: { id: 710, rating: '好', ratingDate: '2026-04-18' }, q2: null, q3: null, q4: { id: 711, rating: '一般', ratingDate: '2026-12-01' } };
const record = { id: 710, personnelId: person.personnelId, personnelName: person.personnelName, idCard: person.idCard, year: 2026, quarter: 2, rating: '好', ratingDate: '2026-10-18' };
function stats(extra = {}) {
  const base = { year: 2026, total: 1, list: [person], quarters: [1, 2, 3, 4].map((quarter) => ({ quarter, good: 1, middle: 0, general: 0, unrated: 3, rated: 1 })), ...extra };
  // loadAllRows 全量分页:total 与 list 数量必须一致,否则 mock 会触发多余的翻页请求
  while (base.list.length < Number(base.total)) base.list.push({ ...person, personnelId: 900 + base.list.length, personnelName: '填充人员' + base.list.length });
  return base;
}
function query(call) { return new URLSearchParams(call.path.split('?')[1]); }
function choose(h, p = person) { h.button('选择', h.modals.at(-1).dialog).click(); h.pick({ id: p.personnelId, name: p.personnelName, idCard: p.idCard, departmentPath: p.departmentPath }); }
function upload(h, file = { name: '评比.xlsx', size: 100 }) { const input = h.host.querySelector('input[type="file"]'); input.files = [file]; input.dispatch('change'); return input; }
function cellButtons(h) { return h.host.querySelectorAll('.de-cell-btn'); }
const currentYear = new Date().getFullYear();

test('initial load hits the all-people stats matrix; removed controls are absent', async (t) => {
  const h = harness(t);
  assert.equal(h.calls.length, 1);
  assert.equal(h.last().method, 'GET');
  assert.match(h.last().path, /\/api\/double-excellence\/stats\?/);
  const q = query(h.last());
  assert.equal(q.get('year'), String(currentYear)); assert.equal(q.get('page'), '1'); assert.equal(q.get('size'), '200');
  assert.equal(q.get('personnelIds'), null); assert.equal(q.get('personnelName'), null);
  for (const text of ['选择人员', '清空人员', '统计', '刷新']) h.missingButton(text);
  for (const text of ['‹', '›', '导入导出 ▾', '下载模板', '新增评比记录']) assert.ok(h.button(text));
  assert.equal(h.host.querySelector('.de-year-text').textContent, currentYear + ' 年');
  assert.equal(h.host.querySelectorAll('.de-person-chip').length, 0);
  assert.equal(h.host.querySelectorAll('.de-summary').length, 0);
  assert.equal(h.host.querySelectorAll('[data-fkey]').length, 3);
  h.last().resolve(stats({ list: [] }));
  await flush();
  assert.equal(h.host.querySelector('.de-count').textContent, '共 1 人');
});

test('matrix rows show unit/name/card columns and clickable quarter cells', async (t) => {
  const h = harness(t);
  h.last().resolve(stats({ total: 2, list: [person, { ...person, personnelId: 45, personnelName: '<img>', idCard: '<script>', departmentPath: '上级 / 下级', q1: null, q4: null }] }));
  await flush();
  const headers = h.host.querySelectorAll('th').map((th) => th.textContent);
  assert.deepEqual(headers, ['单位', '姓名', '身份证号码', '第一季度', '第二季度', '第三季度', '第四季度']);
  const rows = h.host.querySelectorAll('tr').slice(1);   // skip the header row
  assert.equal(rows.length, 2);
  const first = rows[0].children;
  assert.equal(first[0].textContent, '上级本单位');
  assert.equal(first[1].textContent, '现有人员');
  assert.equal(first[2].textContent, person.idCard);
  const buttons = cellButtons(h);
  assert.equal(buttons.length, 8);
  assert.equal(buttons[0].textContent, '04/18好'); // 日期(月/日)在前、评定在后,完整日期见悬停提示
  assert.equal(buttons[0].classList.contains('is-unrated'), false);
  assert.equal(buttons[1].textContent, '未评定');
  assert.equal(buttons[1].classList.contains('is-unrated'), true);
  assert.match(buttons[1].getAttribute('title'), /点击填写第2季度评定/);
  assert.equal(rows[1].children[1].textContent, '<img>');   // text only, never HTML
  assert.equal(h.host.querySelector('.de-count').textContent, '共 2 人');
});

test('year arrows switch year, guard stale responses and stay within 1900..9999', async (t) => {
  const h = harness(t);
  h.last().resolve(stats({ total: 5 }));
  await flush();
  h.button('›').click(); const next = h.last();
  assert.equal(query(next).get('year'), String(currentYear + 1));
  h.button('›').click(); const later = h.last();
  assert.equal(query(later).get('year'), String(currentYear + 2));
  later.resolve(stats({ total: 2 })); next.resolve(stats({ total: 99 })); await flush();
  assert.equal(h.host.querySelector('.de-year-text').textContent, (currentYear + 2) + ' 年');
  assert.equal(h.host.querySelector('.de-count').textContent, '共 2 人');   // stale year response discarded
  h.button('‹').click(); const back = h.last();
  assert.equal(query(back).get('year'), String(currentYear + 1));
  back.resolve(stats()); await flush();
  assert.equal(h.host.querySelector('.de-year-text').textContent, (currentYear + 1) + ' 年');
  // Bounds are enforced in render: arrows disable at the limits.(矩阵骨架在 yearMatrixView 工厂内)
  const region = source.slice(source.indexOf('function yearMatrixView'), source.indexOf('function pendingModuleView'));
  assert.match(region, /yearPrev\.disabled = state\.year <= 1900/);
  assert.match(region, /yearNext\.disabled = state\.year >= 9999/);
});

test('person filters carry to the matrix query and reset clears them', async (t) => {
  const h = harness(t);
  h.last().resolve(stats()); await flush();
  h.field('personnelName').value = '甲&乙';
  h.field('personnelName').dispatch('keydown', { key: 'Enter' });
  const byName = h.last();
  assert.equal(query(byName).get('personnelName'), '甲&乙'); assert.equal(query(byName).get('page'), '1');
  byName.resolve(stats()); await flush();
  h.field('idCard').value = '110101'; h.field('departmentId').value = '3';
  h.button('查询').click();
  const filtered = h.last();
  assert.equal(query(filtered).get('idCard'), '110101');
  assert.equal(query(filtered).get('departmentId'), '3');
  assert.equal(query(filtered).get('personnelName'), '甲&乙');
  filtered.resolve(stats()); await flush();
  h.button('重置').click();
  const reset = h.last();
  assert.equal(query(reset).get('personnelName'), null); assert.equal(query(reset).get('idCard'), null); assert.equal(query(reset).get('departmentId'), null);
  assert.equal(query(reset).get('year'), String(currentYear));
  reset.resolve(stats()); await flush();
  assert.equal(h.field('personnelName').value, '');
});

test('clicking an unrated quarter cell opens a locked create dialog and POSTs the record', async (t) => {
  const h = harness(t);
  h.last().resolve(stats()); await flush();
  cellButtons(h)[1].click();   // q2 unrated
  const modal = h.modals.at(-1), form = h.forms.at(-1);
  assert.equal(modal.options.title, '填写评定：现有人员 ' + currentYear + '年第2季度'); // 单元格进入点明人员与季度
  assert.equal(String(form.inputs.year.value), String(currentYear));
  assert.equal(form.inputs.quarter.value, '2');
  assert.equal(form.inputs.personnelName.value, '现有人员');
  assert.equal(form.inputs.idCard.value, person.idCard);
  assert.equal(form.inputs.personnelId.value, String(person.personnelId));
  for (const key of ['year', 'personnelName', 'idCard']) assert.equal(form.inputs[key].readOnly, true, key + ' locked');
  assert.equal(form.inputs.quarter.disabled, true, 'quarter locked (dropdown disables instead of readOnly)');
  assert.equal(form.inputs.rating.value, '');
  h.missingButton('删除', modal.dialog);
  form.inputs.rating.value = '好'; form.inputs.ratingDate.value = '2026-07-01';
  h.button('保存', modal.dialog).click();
  assert.equal(h.calls.length, 2);
  assert.equal(h.last().method, 'POST'); assert.equal(h.last().path, '/api/double-excellence');
  assert.deepEqual(h.last().data, { year: currentYear, quarter: 2, personnelId: 44, rating: '好', ratingDate: '2026-07-01' });
  h.last().resolve({});
  await flush();
  assert.equal(h.body.contains(modal.dialog), false);
  assert.ok(h.messages.some((m) => m.message === '保存成功'));
  assert.match(h.last().path, /\/stats\?/);   // matrix reloaded
  h.last().resolve(stats()); await flush();
});

test('clicking a rated cell edits by record id with a delete action; both reload the matrix', async (t) => {
  const h = harness(t);
  h.last().resolve(stats()); await flush();
  cellButtons(h)[0].click();   // q1 rated: record 710
  const modal = h.modals.at(-1), form = h.forms.at(-1);
  assert.equal(modal.options.title, '编辑评定：现有人员 ' + currentYear + '年第1季度');
  assert.equal(form.inputs.rating.value, '好');
  assert.equal(form.inputs.ratingDate.value, '2026-04-18');
  form.inputs.rating.value = '中';
  h.button('保存', modal.dialog).click();
  assert.equal(h.last().method, 'PUT'); assert.equal(h.last().path, '/api/double-excellence/710');
  assert.deepEqual(h.last().data, { year: currentYear, quarter: 1, personnelId: 44, rating: '中', ratingDate: '2026-04-18' });
  h.last().resolve({});
  await flush();
  h.last().resolve(stats()); await flush();

  cellButtons(h)[3].click();   // q4 rated: record 711
  const editing = h.modals.at(-1);
  assert.equal(h.confirms.length, 0);
  h.button('删除', editing.dialog).dispatch('click'); h.button('删除', editing.dialog).dispatch('click'); await flush();
  assert.equal(h.confirms.length, 1);
  assert.match(h.confirms[0], /删除后不可恢复/);
  assert.equal(h.calls.filter((call) => call.method === 'DELETE').length, 1);
  h.last().reject(new Error('无权删除')); await flush();
  assert.ok(h.messages.some((m) => /无权删除/.test(m.message)));
  h.button('删除', editing.dialog).click(); await flush();
  h.last().resolve({}); await flush();
  assert.equal(h.body.contains(editing.dialog), false);
  assert.ok(h.messages.some((m) => m.message === '已删除'));
  assert.match(h.last().path, /\/stats\?/);
  h.last().resolve(stats()); await flush();
});

test('新增评比记录 keeps the free six-field dialog with person lookup; save is retryable', async (t) => {
  const h = harness(t);
  h.last().resolve(stats()); await flush();
  h.button('新增评比记录').click();
  const modal = h.modals.at(-1), form = h.forms.at(-1);
  assert.deepEqual(form.fields.filter((field) => field.type !== 'hidden').map((field) => field.label), ['年份', '季度', '姓名', '身份证号码', '评定', '评比日期']);
  assert.equal(form.inputs.personnelName.readOnly, true);
  assert.equal(String(form.inputs.year.value), String(currentYear));
  assert.equal(form.inputs.quarter.value, String(Math.floor(new Date().getMonth() / 3) + 1));
  choose(h);
  form.inputs.year.value = '2026'; form.inputs.quarter.value = '1'; form.inputs.rating.value = '好'; form.inputs.ratingDate.value = '2026-10-18';
  h.button('保存', modal.dialog).dispatch('click'); h.button('保存', modal.dialog).dispatch('click');
  assert.equal(h.calls.filter((call) => call.method === 'POST').length, 1);
  assert.deepEqual(h.last().data, { year: 2026, quarter: 1, personnelId: 44, rating: '好', ratingDate: '2026-10-18' });
  h.last().reject(new Error('已有记录，请编辑')); await flush();
  assert.match(form.el.textContent, /已有记录，请编辑/);
  assert.equal(h.body.contains(modal.dialog), true);
  h.button('保存', modal.dialog).click();
  h.last().resolve({ id: 712 }); await flush();
  assert.equal(h.body.contains(modal.dialog), false);
  assert.match(h.last().path, /\/stats\?/);
  h.last().resolve(stats()); await flush();
});

test('identity clears ID immediately, rejects forged/pending matches and ignores stale card lookup', async (t) => {
  const h = harness(t);
  h.last().resolve(stats()); await flush();
  h.button('新增评比记录').click(); const form = h.forms.at(-1); choose(h);
  form.inputs.idCard.value = 'old'; form.inputs.idCard.dispatch('input'); form.inputs.idCard.dispatch('change'); const old = h.last();
  assert.equal(form.inputs.personnelId.value, ''); assert.equal(form.validatePerson(), false);
  form.inputs.idCard.value = person.idCard; form.inputs.idCard.dispatch('input'); form.inputs.idCard.dispatch('change'); const latest = h.last();
  latest.resolve({ id: person.personnelId, name: person.personnelName, idCard: person.idCard });
  old.resolve({ id: 99, name: '过期人员', idCard: 'old' }); await flush(); assert.equal(form.inputs.personnelId.value, '44');
  form.inputs.idCard.value = 'tampered'; form.inputs.personnelName.value = '伪造'; assert.equal(form.validatePerson(), false);
});

test('export ships year plus applied filters with no ids/personnelIds; template unchanged', async (t) => {
  const h = harness(t);
  h.last().resolve(stats()); await flush();
  h.field('personnelName').value = '<甲&乙>';
  h.button('查询').click();
  h.last().resolve(stats()); await flush();
  h.button('导出 Excel').click();
  assert.equal(h.last().method, 'DOWNLOAD');
  const q = query(h.last());
  assert.equal(q.get('year'), String(currentYear));
  assert.equal(q.get('personnelName'), '<甲&乙>');
  assert.equal(q.get('ids'), null); assert.equal(q.get('personnelIds'), null); assert.equal(q.get('page'), null);
  h.last().resolve({ blob: 'year', filename: '评比.xlsx' }); await flush();
  h.button('下载模板').click();
  assert.equal(h.last().path, '/api/double-excellence/template');
  h.last().resolve({ blob: 'template', filename: '模板.xlsx' }); await flush();
  assert.equal(h.downloads.length, 2);
});

test('import confirms upsert, blocks duplicate, reports failures and reloads the matrix', async (t) => {
  const h = harness(t);
  h.last().resolve(stats()); await flush();
  h.button('导入 Excel').dispatch('click'); h.button('导入 Excel').dispatch('click'); await flush();
  assert.equal(h.confirms.length, 1); assert.match(h.confirms[0], /身份证号码＋年份＋季度/);
  const input = upload(h), request = h.last();
  assert.equal(request.method, 'UPLOAD');
  input.files = [{ name: 'again.xlsx', size: 1 }]; input.dispatch('change');
  assert.equal(h.calls.filter((call) => call.method === 'UPLOAD').length, 1);
  request.resolve({ added: 2, updated: 1, failed: 1, errors: ['第5行：人员不存在'] }); await flush();
  assert.match(h.last().path, /\/stats\?/);
  assert.deepEqual(h.results[0][1], ['第5行：人员不存在']);
  h.last().resolve(stats()); await flush();
  assert.equal(input.disabled, false);
});

test('import invalid type/10MiB/network failure permit retry; detached late response ignored', async (t) => {
  const h = harness(t);
  h.last().resolve(stats()); await flush();
  upload(h, { name: 'wrong.csv', size: 1 }); upload(h, { name: 'huge.xlsx', size: 10 * 1024 * 1024 + 1 });
  assert.equal(h.calls.filter((call) => call.method === 'UPLOAD').length, 0);
  const input = upload(h); assert.equal(input.value, '');
  h.last().reject(new Error('网络失败')); await flush();
  assert.equal(input.disabled, false);
  upload(h); const late = h.last(), count = h.calls.length;
  h.host.innerHTML = ''; h.observe();
  late.resolve({ added: 1 }); await flush();
  assert.equal(h.calls.length, count); assert.equal(h.results.length, 0);
  assert.ok(h.progresses.every((pm) => pm.closed));
});

test('filter uses actual slideFrom directions, 310ms close, quick reversal, retained DOM and scroll', async (t) => {
  const h = harness(t);
  h.last().resolve(stats()); await flush();
  const panel = h.host.querySelector('.filter-panel'), inner = h.host.querySelector('.filter-panel-inner'), main = h.host.querySelector('.content-main'), tab = h.body.querySelector('.filter-tab-btn');
  const directions = [], real = h.c.slideFrom; h.c.slideFrom = (el, dx) => { directions.push([el, dx]); return real(el, dx); };
  h.host.scrollTop = 222; inner.scrollTop = 63;
  tab.click(); assert.deepEqual(directions.map(([, dx]) => dx), [270, 270]); assert.equal(panel.classList.contains('open'), true);
  assert.equal(tab.getAttribute('aria-expanded'), 'true');
  tab.click(); assert.equal(panel.style.transform, 'translateX(100%)'); assert.equal(directions.at(-1)[1], -270);
  h.tick(309); assert.equal(panel.classList.contains('open'), true);
  tab.click(); h.tick(2); assert.equal(panel.classList.contains('open'), true); assert.equal(tab.getAttribute('aria-expanded'), 'true');
  h.tick(500); assert.equal(main.classList.contains('filter-padding'), true);
  tab.click(); h.tick(310);
  assert.equal(panel.classList.contains('open'), false); assert.equal(panel.style.transform, ''); assert.equal(panel.style.transition, '');
  assert.equal(h.host.querySelector('.filter-panel-inner'), inner);
  assert.equal(h.host.scrollTop, 222); assert.equal(inner.scrollTop, 63);
});

for (const reason of ['navigation', 'forceLogin']) test(`disposal on ${reason} clears body tag/timers and stale response`, async (t) => {
  const h = harness(t), tab = h.body.querySelector('.filter-tab-btn');
  h.last().resolve(stats()); await flush();
  tab.click(); tab.click();
  h.button('›').click(); const late = h.last();
  if (reason === 'navigation') h.host.innerHTML = ''; else { h.c.TOKEN = ''; h.c.USER = null; }
  h.observe();
  assert.equal(h.body.querySelectorAll('.filter-tab-btn').length, 0);
  assert.equal(h.timers.size, 0);
  late.resolve(stats({ total: 999 })); await flush(); h.tick(1000);
  assert.equal(h.body.querySelectorAll('.filter-tab-btn').length, 0);
});

test('load failure surfaces the error and keeps the empty matrix; payload and source stay ES5/safe', async (t) => {
  const h = harness(t);
  h.last().reject(new Error('矩阵失败')); await flush();
  assert.match(h.host.querySelector('.de-error').textContent, /矩阵失败/);
  assert.match(h.host.querySelector('.empty-row').textContent, /加载失败，请重试/);
  const valid = { year: '2026', quarter: '1', personnelId: '44', rating: '一般', ratingDate: '2026-10-18', personnelName: 'ignored', idCard: 'ignored' };
  for (const extra of [{ year: '1899' }, { year: 'Infinity' }, { quarter: 'NaN' }, { quarter: '5' }, { personnelId: '' }, { rating: '优秀' }, { rating: '未评定' }, { ratingDate: '2026-02-30' }]) assert.throws(() => h.c.doubleExcellencePayload({ ...valid, ...extra }));
  assert.deepEqual(Object.keys(plain(h.c.doubleExcellencePayload(valid))), ['year', 'quarter', 'personnelId', 'rating', 'ratingDate']);
  const addition = source.slice(source.indexOf('/* ================= 双争评比'), source.indexOf('function pendingModuleView'));
  assert.doesNotMatch(addition, /\b(?:let|const|class|async|await)\s|=>|\.includes\(|\.finally\(|fetch\(/);
  assert.ok(!addition.includes('doubleExcellenceStatsModal'), 'stats modal removed');
  for (const label of ['选择人员', '清空人员', '刷新统计', 'de-person-chip', 'de-select-empty']) {
    assert.ok(!addition.includes(label), label + ' removed from the section');
  }
});

test('dropdown keyboard navigation highlights options and selects with Enter', async (t) => {
  const h = harness(t);
  h.last().resolve(stats()); await flush(); // settle harness 的初始矩阵加载,与其它用例一致
  const dd = h.c.makeDropdown([{ v: 'good', l: '好' }, { v: 'mid', l: '中' }, { v: 'fair', l: '一般' }]);
  const btn = dd.children[0], list = dd.children[1];
  btn.click();
  const items = h.c.$all('.dd-item', list);
  assert.equal(btn.getAttribute('aria-expanded'), 'true');
  assert.equal(items.filter((el) => el.classList.contains('dd-active')).length, 1, 'opening highlights the current value');
  list.dispatch('keydown', { key: 'ArrowDown' });
  list.dispatch('keydown', { key: 'ArrowDown' });
  assert.equal(items[2].classList.contains('dd-active'), true, 'ArrowDown moves the highlight');
  list.dispatch('keydown', { key: 'Enter' });
  assert.equal(dd.value, 'mid', 'Enter selects the highlighted option');
  assert.equal(btn.getAttribute('aria-expanded'), 'false');
  assert.equal(btn.children[0].textContent, '中');
  btn.click();
  list.dispatch('keydown', { key: 'End' });
  assert.equal(items[3].classList.contains('dd-active'), true, 'End jumps to the last option');
  list.dispatch('keydown', { key: 'Home' });
  assert.equal(items[0].classList.contains('dd-active'), true, 'Home jumps to the first option');
  list.dispatch('keydown', { key: 'Escape' });
  assert.equal(btn.getAttribute('aria-expanded'), 'false');
  assert.equal(dd.value, 'mid', 'Escape closes without changing the value');
});
