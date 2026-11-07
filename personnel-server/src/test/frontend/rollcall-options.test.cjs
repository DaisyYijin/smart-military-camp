'use strict';

// Run actual production functions against a small DOM and isolated transports.
// node --test personnel-server/src/test/frontend/rollcall-options.test.cjs
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
  const match = source.match(new RegExp(`^${indent}function ${escaped}\\([^\\n]*\\) \\{[\\s\\S]*?^${indent}\\}`, 'm'));
  assert.ok(match, `production function ${name} must exist`);
  return match[0];
}
const rollcall = definition(ext, 'downloadRollcall', '  ');
const functions = [definition(app, 'h'), ...['readFilterParams', 'toQueryString', 'downloadExcel', 'downloadRollcall', 'openRosterDialog']
  .map((name) => definition(ext, name, '  ')), ext.match(/^  var ROSTER_DEFAULT = .*;$/m)[0]].join('\n');
const flush = () => new Promise((done) => setImmediate(done));

class Element {
  constructor(tag, text = '') {
    this.tagName = tag;
    this.nodeType = tag === '#text' ? 3 : 1;
    this.nodes = [];
    this.attributes = {};
    this.listeners = {};
    this.parentNode = null;
    this.disabled = false;
    this._value = null;
    this._text = text;
    this.classList = {
      contains: (name) => this.className.split(/\s+/).includes(name),
      toggle: (name, on) => {
        const names = this.className.split(/\s+/).filter(Boolean).filter((item) => item !== name);
        if (on) names.push(name);
        this.className = names.join(' ');
      },
    };
  }
  get children() { return this.nodes.filter((node) => node.nodeType === 1); }
  get className() { return this.attributes.class || ''; }
  set className(value) { this.attributes.class = value; }
  get value() {
    if (this.tagName !== 'select') return this._value === null ? (this.getAttribute('value') || '') : this._value;
    if (this._value === null) return this.children.length ? this.children[0].value : '';
    return this.children.some((child) => child.value === this._value) ? this._value : '';
  }
  set value(value) { this._value = String(value); }
  setAttribute(key, value) {
    this.attributes[key] = String(value);
    if (key === 'disabled') this.disabled = true;
    if (key === 'value') this.value = value;
  }
  getAttribute(key) { return Object.hasOwn(this.attributes, key) ? this.attributes[key] : null; }
  appendChild(node) { node.remove(); this.nodes.push(node); node.parentNode = this; return node; }
  remove() {
    if (this.parentNode) this.parentNode.nodes.splice(this.parentNode.nodes.indexOf(this), 1);
    this.parentNode = null;
  }
  get textContent() { return this._text + this.nodes.map((node) => node.textContent).join(''); }
  set textContent(value) { for (const node of this.nodes) node.parentNode = null; this.nodes = []; this._text = String(value); this._value = null; }
  matches(selector) {
    const match = /^(?:([a-z][\w-]*))?((?:\.[\w-]+)*)(?:\[([\w-]+)(?:="([^"]*)")?\])?$/.exec(selector);
    assert.ok(match, `unsupported selector ${selector}`);
    return (!match[1] || this.tagName === match[1])
      && match[2].split('.').filter(Boolean).every((name) => this.classList.contains(name))
      && (!match[3] || (this.getAttribute(match[3]) !== null && (match[4] === undefined || this.getAttribute(match[3]) === match[4])));
  }
  querySelectorAll(selector) {
    const space = selector.indexOf(' ');
    if (space >= 0) return this.querySelectorAll(selector.slice(0, space)).flatMap((node) => node.querySelectorAll(selector.slice(space + 1)));
    return this.children.flatMap((node) => [...(node.matches(selector) ? [node] : []), ...node.querySelectorAll(selector)]);
  }
  querySelector(selector) { return this.querySelectorAll(selector)[0] || null; }
  addEventListener(type, listener) { (this.listeners[type] ||= []).push(listener); }
  dispatch(type) { for (const listener of this.listeners[type] || []) listener.call(this, { target: this }); }
  click() { if (!this.disabled) this.dispatch('click'); }
  focus() { this.focused = true; }
}
function harness(filters = {}, transport = {}) {
  const body = new Element('body');
  const document = { body, createElement: (tag) => new Element(tag), createTextNode: (text) => new Element('#text', text),
    querySelectorAll: (selector) => body.querySelectorAll(selector) };
  for (const [key, value] of Object.entries(filters)) {
    const input = new Element('input'); input.setAttribute('data-fkey', key); input.value = value; body.appendChild(input);
  }
  const calls = [], progress = [], saved = [], timers = [], messages = [], modals = [], gets = [];
  const context = vm.createContext({ document,
    toast: (text, type) => messages.push({ text, type }),
    setTimeout: (callback, delay) => timers.push({ callback, delay }),
    downloadBlob: (blob, filename) => saved.push({ blob, filename }),
    GET: (url) => { gets.push(url); return Promise.resolve([{ key: 'name', label: '姓名' }, { key: 'rank', label: '军衔' }, { key: 'militaryIdNo', label: '军人证件号' }, { key: 'departmentPath', label: '单位全路径' }]); },
    apiBlobWithProgress: (url, notify) => {
      calls.push({ url, notify });
      if (transport.deferred) return new Promise((resolve, reject) => Object.assign(calls.at(-1), { resolve, reject }));
      return Promise.resolve({ blob: 'excel-data', filename: '人员点名册.xlsx' });
    },
    showProgressModal: (title) => {
      const pm = { title, values: [], statuses: [], closed: false,
        setProgress(value) { this.values.push(value); }, setStatus(text) { this.statuses.push(text); }, close() { this.closed = true; } };
      progress.push(pm); return pm;
    },
    openModal: (options) => {
      const dialog = new Element('div'); dialog.appendChild(options.body);
      const footer = new Element('div'); footer.className = 'dialog-foot';
      for (const button of options.footer) footer.appendChild(button);
      dialog.appendChild(footer); body.appendChild(dialog);
      const modal = { dialog, closed: false, close() { this.closed = true; dialog.remove(); } };
      modals.push(modal); return modal;
    },
  });
  vm.runInContext(functions, context, { timeout: 1000 });
  return { context, body, calls, progress, saved, timers, messages, modals, gets,
    open() { context.downloadRollcall(); return modals.at(-1); } };
}
function ui(modal) {
  const dialog = modal.dialog;
  const button = (root, text) => root.querySelectorAll('button').find((item) => item.textContent === text);
  return {
    marks: () => dialog.querySelectorAll('[data-rollcall-mark]'),
    rows: () => dialog.querySelectorAll('.rollcall-sort-row'),
    add: dialog.querySelector('[data-rollcall-add-sort]'),
    all: button(dialog, '全选标志'), none: button(dialog, '全不选标志'),
    download: button(dialog, '下载 Excel'), cancel: button(dialog, '取消'),
    error: dialog.querySelector('.rollcall-error'),
    unit: () => dialog.querySelector('[data-rollcall-actual-unit]'),
    field: (index) => dialog.querySelectorAll('[data-rollcall-field]')[index],
    direction: (index) => dialog.querySelectorAll('[data-rollcall-direction]')[index],
    choose(index, field, direction) {
      this.field(index).value = field; this.field(index).dispatch('change');
      if (direction) this.direction(index).value = direction;
    },
    action(index, text) { button(this.rows()[index], text).click(); },
  };
}
function query(h) { return new URL(h.calls.at(-1).url, 'http://unit.test').searchParams; }
function markStates(controls) { return controls.marks().map((mark) => [mark.classList.contains('on'), mark.getAttribute('aria-pressed')]); }

const fields = [
  ['serviceYears', 'desc', '高到低', '低到高'], ['rank', 'desc', '高到低', '低到高'],
  ['age', 'desc', '大到小', '小到大'], ['name', 'asc', '中文正序', '中文倒序'],
  ['entryDate', 'asc', '早到晚', '晚到早'], ['postDate', 'asc', '早到晚', '晚到早'],
  ['category', 'asc', '中文正序', '中文倒序'], ['education', 'desc', '高到低', '低到高'],
  ['post', 'asc', '中文正序', '中文倒序'],
];

test('rollcall defaults: no checkbox, marks or sort; empty first row permits the original download', () => {
  const h = harness(); const modal = h.open(); const c = ui(modal);
  assert.equal(modal.dialog.querySelectorAll('input').length, 0);
  assert.match(modal.dialog.textContent, /同级单位从左到右排列，姓名行与所属单位表头等宽合并/);
  assert.doesNotMatch(modal.dialog.textContent, /六列网格|每个单位独占一整行标题|上级直属人员单独成列/);
  assert.equal(c.marks().length, 3); assert.deepEqual(markStates(c), [[false, 'false'], [false, 'false'], [false, 'false']]);
  assert.equal(c.rows().length, 1); assert.equal(c.field(0).value, ''); assert.equal(c.direction(0).disabled, true);
  assert.match(c.rows()[0].textContent, /第一排序条件/);
  assert.deepEqual(c.field(0).children.slice(1).map((option) => option.value), fields.map((item) => item[0]));
  c.download.click(); assert.equal(modal.closed, true);
  assert.equal(h.calls[0].url, '/api/personnel/rollcall/excel?'); assert.equal(query(h).has('sort'), false); assert.equal(query(h).has('marks'), false);
});

test('mark buttons toggle independently; all/none synchronize class and aria without changing sort', () => {
  const h = harness(); const c = ui(h.open()); c.choose(0, 'age', 'asc'); c.add.click(); c.choose(1, 'name');
  c.marks()[0].click(); assert.deepEqual(markStates(c), [[true, 'true'], [false, 'false'], [false, 'false']]);
  c.marks()[0].click(); assert.deepEqual(markStates(c), [[false, 'false'], [false, 'false'], [false, 'false']]);
  c.all.click(); assert.deepEqual(markStates(c), [[true, 'true'], [true, 'true'], [true, 'true']]);
  c.none.click(); assert.deepEqual(markStates(c), [[false, 'false'], [false, 'false'], [false, 'false']]);
  assert.equal(c.rows().length, 2); assert.equal(c.field(0).value, 'age'); assert.equal(c.direction(0).value, 'asc'); assert.equal(c.field(1).value, 'name');
  c.marks()[2].click(); c.marks()[0].click(); c.download.click();
  assert.equal(query(h).get('marks'), 'party,driver'); assert.equal(query(h).get('sort'), 'age:asc,name:asc');
});

test('selecting all marks emits exactly the existing party,league,driver protocol', () => {
  const h = harness(); const c = ui(h.open()); c.all.click(); c.download.click();
  assert.equal(query(h).get('marks'), 'party,league,driver'); assert.equal(query(h).has('sort'), false);
});

for (const exit of ['cancel', 'download']) test(`reopening after ${exit} resets marks and sort, without persisting options`, () => {
  const h = harness(); let c = ui(h.open()); c.all.click(); c.choose(0, 'rank'); c.add.click(); c.choose(1, 'name');
  c[exit].click(); c = ui(h.open());
  assert.deepEqual(markStates(c), [[false, 'false'], [false, 'false'], [false, 'false']]);
  assert.equal(c.rows().length, 1); assert.equal(c.field(0).value, ''); assert.equal(c.direction(0).value, '');
});

test('add/remove/reorder preserve field and direction, renumber priorities, and enforce five rows', () => {
  const h = harness(); const c = ui(h.open());
  for (let i = 0; i < 8; i++) c.add.click();
  c.add.dispatch('click'); assert.equal(c.rows().length, 5); assert.equal(c.add.disabled, true);
  for (let i = 0; i < 5; i++) { c.choose(i, fields[i][0]); assert.match(c.rows()[i].textContent, new RegExp(['第一', '第二', '第三', '第四', '第五'][i] + '排序条件')); }
  c.choose(1, 'rank', 'asc');
  const original = c.field(1); c.action(1, '上移');
  assert.equal(c.field(0), original); assert.equal(c.field(0).value, 'rank'); assert.equal(c.direction(0).value, 'asc'); assert.equal(original.focused, true);
  c.action(0, '上移'); assert.equal(c.field(0), original);
  c.action(0, '下移'); assert.equal(c.field(1), original);
  c.action(4, '下移'); assert.equal(c.field(4).value, 'entryDate');
  c.action(2, '移除'); assert.equal(c.rows().length, 4); assert.equal(c.add.disabled, false); assert.equal(c.add.textContent, '添加第五排序条件');
  assert.equal(c.field(2).value, 'name'); assert.equal(c.field(2).getAttribute('aria-label'), '第三排序条件字段');
  c.add.click(); c.choose(4, 'post'); c.action(4, '上移'); c.download.click();
  assert.equal(query(h).get('sort'), 'serviceYears:desc,rank:asc,name:asc,post:asc,entryDate:asc');
});

test('removing every row allows download without sort and can restart at first condition', () => {
  const h = harness(); const c = ui(h.open()); c.choose(0, 'name'); c.action(0, '移除');
  assert.equal(c.rows().length, 0); assert.equal(c.add.textContent, '添加第一排序条件'); assert.equal(c.add.focused, true);
  c.add.click(); assert.equal(c.rows().length, 1); assert.equal(c.field(0).value, '');
  c.action(0, '移除'); c.download.click(); assert.equal(query(h).has('sort'), false);
});

for (const [field, initial, label, reverseLabel] of fields) test(`${field}: default direction/labels, reversible protocol, and field-switch reset`, () => {
  const h = harness(); const c = ui(h.open()); c.choose(0, field);
  assert.equal(c.direction(0).disabled, false); assert.equal(c.direction(0).value, initial);
  assert.equal(c.direction(0).children.find((option) => option.value === initial).textContent, label);
  const reverse = initial === 'asc' ? 'desc' : 'asc';
  assert.equal(c.direction(0).children.find((option) => option.value === reverse).textContent, reverseLabel);
  c.direction(0).value = reverse; c.download.click(); assert.equal(query(h).get('sort'), `${field}:${reverse}`);
  const second = ui(h.open()); second.choose(0, field, reverse); second.choose(0, field);
  assert.equal(second.direction(0).value, initial); second.download.click(); assert.equal(query(h).get('sort'), `${field}:${initial}`);
});

test('multi-condition wire format matches the contract and only selected fields are sent', () => {
  const h = harness(); const c = ui(h.open()); c.choose(0, 'serviceYears'); c.add.click(); c.choose(1, 'rank'); c.add.click(); c.choose(2, 'name');
  c.download.click(); assert.equal(query(h).get('sort'), 'serviceYears:desc,rank:desc,name:asc');
  assert.match(h.calls[0].url, /sort=serviceYears%3Adesc%2Crank%3Adesc%2Cname%3Aasc/);
  assert.equal(query(h).has('marks'), false);
});

test('duplicate fields reject download without closing or starting progress; correction retries successfully', () => {
  const h = harness(); const modal = h.open(); const c = ui(modal);
  c.choose(0, 'rank'); c.add.click(); c.choose(1, 'rank', 'asc'); c.download.click();
  assert.match(c.error.textContent, /不能重复.*军衔/); assert.equal(modal.closed, false); assert.equal(h.calls.length, 0); assert.equal(h.progress.length, 0);
  assert.equal(c.field(1).focused, true); assert.equal(c.error.getAttribute('role'), 'alert');
  c.choose(1, 'name'); assert.equal(c.error.textContent, ''); c.download.click();
  assert.equal(modal.closed, true); assert.equal(query(h).get('sort'), 'rank:desc,name:asc');
});

for (const target of ['field', 'direction']) test(`invalid ${target} option blocks download and leaves the modal editable`, () => {
  const h = harness(); const modal = h.open(); const c = ui(modal); c.choose(0, 'name');
  const select = c[target](0); const invalid = new Element('option'); invalid.value = 'invalid'; select.appendChild(invalid); select.value = 'invalid';
  c.download.click(); assert.match(c.error.textContent, /有效的排序字段和方向/); assert.equal(modal.closed, false); assert.equal(h.calls.length, 0); assert.equal(h.progress.length, 0);
  c.choose(0, 'age'); c.download.click(); assert.equal(query(h).get('sort'), 'age:desc');
});

test('empty rows are ignored; clearing the last selected field removes sort and disables direction', () => {
  const h = harness(); const c = ui(h.open()); c.add.click(); c.choose(1, 'name'); c.add.click();
  c.download.click(); assert.equal(query(h).get('sort'), 'name:asc');
  const second = ui(h.open()); second.choose(0, 'entryDate', 'desc'); second.choose(0, '');
  assert.equal(second.direction(0).disabled, true); assert.equal(second.direction(0).value, '');
  second.add.click(); second.download.click(); assert.equal(query(h).has('sort'), false);
});

test('current readFilterParams values are trimmed, encoded, and read at download time; no option leakage', () => {
  const filters = { name: ' 张 三 ', departmentId: ' 7 ', category: '战士/文职', keyword: 'A&B+测试', entryDateStart: '2020-01-01', status: ' 在位 ', rank: '', education: '   ' };
  const h = harness(filters); const c = ui(h.open()); c.choose(0, 'rank'); c.marks()[1].click();
  h.body.querySelector('[data-fkey="departmentId"]').value = ' 9 ';
  c.download.click(); const q = query(h);
  for (const [key, value] of Object.entries({ name: '张 三', departmentId: '9', category: '战士/文职', keyword: 'A&B+测试', entryDateStart: '2020-01-01', status: '在位', marks: 'league', sort: 'rank:desc' })) assert.equal(q.get(key), value);
  assert.equal(q.has('rank'), false); assert.equal(q.has('education'), false);
  assert.equal(h.body.querySelector('[data-fkey="rank"]').value, '');
  const second = ui(h.open()); second.download.click(); assert.equal(query(h).has('sort'), false); assert.equal(query(h).has('marks'), false);
});

test('download keeps the original Excel progress, saved filename, and delayed progress close', async () => {
  const h = harness({}, { deferred: true }); const modal = h.open(); ui(modal).download.click();
  assert.equal(h.progress.length, 1); const pm = h.progress[0];
  assert.equal(pm.title, '生成点名册'); assert.deepEqual(pm.statuses, ['正在按当前筛选条件生成…']);
  h.calls[0].notify(50); assert.deepEqual(pm.values, [45]); assert.equal(pm.statuses.at(-1), '正在下载… 50%');
  h.calls[0].resolve({ blob: 'real-result', filename: '人员点名册-2026-10-18.xlsx' }); await flush();
  assert.equal(pm.values.at(-1), 100); assert.equal(pm.statuses.at(-1), '下载完成');
  assert.deepEqual(h.saved, [{ blob: 'real-result', filename: '人员点名册-2026-10-18.xlsx' }]);
  assert.equal(pm.closed, false); assert.equal(h.timers[0].delay, 600); h.timers[0].callback(); assert.equal(pm.closed, true);
});

test('download failure uses the existing progress close and error notification', async () => {
  const h = harness({}, { deferred: true }); ui(h.open()).download.click();
  h.calls[0].reject(new Error('生成失败')); await flush();
  assert.equal(h.progress[0].closed, true); assert.deepEqual(h.messages, [{ text: '生成失败', type: 'err' }]); assert.equal(h.saved.length, 0);
});

test('rollcall options do not alter roster selection, defaults, filters or export parameters', async () => {
  const h = harness({ departmentId: '4', keyword: '当前筛选' }); const c = ui(h.open()); c.all.click(); c.choose(0, 'rank'); c.download.click();
  h.context.openRosterDialog(); await flush(); assert.deepEqual(h.gets, ['/api/personnel/roster/fields']);
  const roster = h.modals.at(-1); const download = roster.dialog.querySelector('.btn-primary');
  const chips = roster.dialog.querySelectorAll('.roster-field-check'); assert.ok(chips.every((chip) => !chip.classList.contains('on')));
  const count = h.calls.length; download.click(); assert.equal(h.calls.length, count); assert.equal(roster.closed, false); assert.match(h.messages.at(-1).text, /至少选择一个字段/);
  chips[0].click(); download.click();
  assert.equal(h.calls.at(-1).url.split('?')[0], '/api/personnel/roster/excel');
  assert.equal(query(h).get('fields'), 'name'); assert.equal(query(h).get('departmentId'), '4'); assert.equal(query(h).get('keyword'), '当前筛选');
  assert.equal(query(h).has('sort'), false); assert.equal(query(h).has('marks'), false);
  h.context.openRosterDialog(); await flush(); assert.ok(h.modals.at(-1).dialog.querySelectorAll('.roster-field-check').every((chip) => !chip.classList.contains('on')));
});

test('subtree and point filters flow through both downloads without leaking rollcall options into roster fields', async () => {
  const h = harness({ departmentId: '22', includeDescendants: 'true', locationPoint: '点位 A&B', status: '在位' });
  const c = ui(h.open()); c.marks()[0].click(); c.choose(0, 'rank'); c.download.click();
  assert.equal(query(h).get('departmentId'), '22'); assert.equal(query(h).get('includeDescendants'), 'true');
  assert.equal(query(h).get('locationPoint'), '点位 A&B'); assert.equal(query(h).get('marks'), 'party'); assert.equal(query(h).get('sort'), 'rank:desc');
  h.context.openRosterDialog(); await flush(); const modal = h.modals.at(-1);
  const chips = modal.dialog.querySelectorAll('.roster-field-check');
  assert.equal(chips.length, 4); assert.ok(chips.every((chip) => !chip.classList.contains('on')));
  for (const label of ['军人证件号', '单位全路径']) chips.find((chip) => chip.textContent === label).click();
  modal.dialog.querySelector('.btn-primary').click();
  assert.equal(query(h).get('fields'), 'militaryIdNo,departmentPath'); assert.equal(query(h).get('includeDescendants'), 'true');
  assert.equal(query(h).get('locationPoint'), '点位 A&B'); assert.equal(query(h).has('sort'), false); assert.equal(query(h).has('marks'), false);
  h.body.querySelector('[data-fkey="departmentId"]').value = '';
  assert.equal(Object.hasOwn(h.context.readFilterParams(), 'includeDescendants'), false);
  assert.equal(h.body.querySelector('[data-fkey="includeDescendants"]').value, 'true'); // Reader never mutates the controls.
});

test('roster field area never creates its own scroll container alongside the dialog body', async () => {
  const h = harness();
  h.context.GET = () => Promise.resolve(Array.from({ length: 40 }, (_, index) => ({ key: 'field' + index, label: '字段' + index })));
  h.context.openRosterDialog(); await flush();
  const modal = h.modals.at(-1);
  const descendants = modal.dialog.querySelectorAll('div');
  const scrollables = descendants.filter((el) => /auto|scroll/.test(String((el.style || {}).overflow) + String((el.style || {}).overflowY)));
  assert.equal(scrollables.length, 0, 'only the dialog body may scroll');
  const chips = modal.dialog.querySelectorAll('.roster-field-check');
  assert.equal(chips.length, 40);
  assert.doesNotMatch(chips[0].parentNode.getAttribute('style'), /max-height|overflow/);
});

test('roster dynamically offers license columns without preselection and keeps field keys separate from license filters', async () => {
  const h = harness({ driverLicense: 'yes', driverLicenseType: '军本', driverLicenseStatus: '有效' });
  const keys = ['name', 'driverLicense', 'hasDriverLicense', 'driverLicenseType', 'driverLicenseNo', 'driverVehicleClass', 'driverIssueDate', 'driverExpiryDate', 'driverLicenseStatus', 'driverReviewDate', 'driverIssuingAuth'];
  h.context.GET = () => Promise.resolve(keys.map((key) => ({ key, label: key })));
  h.context.openRosterDialog(); await flush();
  const modal = h.modals.at(-1); const chips = modal.dialog.querySelectorAll('.roster-field-check');
  assert.equal(chips.length, 11); assert.ok(chips.every((chip) => !chip.classList.contains('on')));
  assert.match(modal.dialog.textContent, /多证在同一人员行内分行显示/);
  for (const key of ['name', 'driverLicense', 'driverLicenseNo']) chips.find((chip) => chip.textContent === key).click();
  modal.dialog.querySelector('.btn-primary').click();
  assert.equal(query(h).get('fields'), 'name,driverLicense,driverLicenseNo');
  assert.equal(query(h).get('driverLicense'), 'yes'); assert.equal(query(h).get('driverLicenseType'), '军本'); assert.equal(query(h).get('driverLicenseStatus'), '有效');
});

test('license filters reach both real download functions without changing the old driver-mark option', async () => {
  const h = harness({ driverLicense: 'yes', driverLicenseType: '地方本', driverLicenseStatus: '过期' });
  const c = ui(h.open()); c.marks()[2].click(); c.download.click();
  assert.equal(query(h).get('driverLicense'), 'yes'); assert.equal(query(h).get('driverLicenseType'), '地方本'); assert.equal(query(h).get('driverLicenseStatus'), '过期');
  assert.equal(query(h).get('marks'), 'driver');
  h.context.openRosterDialog(); await flush();
  const modal = h.modals.at(-1); modal.dialog.querySelectorAll('.roster-field-check')[0].click(); modal.dialog.querySelector('.btn-primary').click();
  assert.equal(query(h).get('driverLicense'), 'yes'); assert.equal(query(h).get('driverLicenseType'), '地方本'); assert.equal(query(h).get('driverLicenseStatus'), '过期');
  assert.equal(query(h).has('marks'), false); assert.equal(query(h).has('sort'), false);
});

test('native button/select semantics, accessible labels, ES5 and wrapping do not require CSS or keyboard emulation', () => {
  const h = harness(); const c = ui(h.open());
  for (const button of h.modals[0].dialog.querySelectorAll('button')) {
    assert.equal(button.getAttribute('type'), 'button'); assert.equal(button.getAttribute('tabindex'), null);
    assert.equal(button.listeners.keydown, undefined); assert.equal(button.listeners.keyup, undefined);
  }
  for (const select of [c.field(0), c.direction(0)]) { assert.equal(select.tagName, 'select'); assert.ok(select.getAttribute('aria-label')); }
  assert.match(c.rows()[0].getAttribute('style'), /display:flex;flex-wrap:wrap/);
  assert.match(c.field(0).getAttribute('style'), /min-width:0;max-width:100%/);
  assert.match(c.marks()[0].parentNode.getAttribute('style'), /flex-wrap:wrap/);
  assert.doesNotMatch(rollcall, /\b(?:let|const|class|async|await)\s|=>|\.includes\(|\.finally\(|fetch\(/);
  const text = h.modals[0].dialog.textContent;
  for (const phrase of ['第一条件相同再比下一条件', '相当入伍日期', '精确日期', '未知军衔', '不使用字典序号', '不表示职位高低', '不改变单位层级及不在位原因分组', '同级单位从左到右排列', '姓名行与所属单位表头等宽合并', '本级人员下方再显示子单位及人员']) assert.ok(text.includes(phrase), phrase);
  assert.doesNotMatch(rollcall, /window\.print|localStorage|sessionStorage/);
});

test('actual-unit switch defaults off, toggles class/aria, and sends actualUnit=true only when enabled', () => {
  const h = harness(); const modal = h.open(); const c = ui(modal);
  const chip = c.unit();
  assert.ok(chip, 'single toggle exists');
  assert.equal(modal.dialog.querySelectorAll('[data-rollcall-actual-unit]').length, 1);
  assert.equal(chip.textContent, '按实际单位排列'); assert.equal(chip.getAttribute('type'), 'button');
  assert.equal(chip.classList.contains('on'), false); assert.equal(chip.getAttribute('aria-pressed'), 'false');
  assert.match(modal.dialog.textContent, /单位分组/);
  assert.match(modal.dialog.textContent, /默认按编制单位分组；开启后按实际在岗单位分组/);
  const establishment = modal.dialog.querySelector('[data-rollcall-unit-mode="establishment"]');
  assert.equal(establishment.classList.contains('on'), true); assert.equal(establishment.getAttribute('aria-pressed'), 'true');
  assert.match(chip.parentNode.getAttribute('style'), /flex-wrap:wrap/);
  c.download.click(); assert.equal(query(h).has('actualUnit'), false);
  const second = ui(h.open());
  second.unit().click();
  assert.equal(second.unit().classList.contains('on'), true); assert.equal(second.unit().getAttribute('aria-pressed'), 'true');
  second.unit().click();
  assert.equal(second.unit().classList.contains('on'), false); assert.equal(second.unit().getAttribute('aria-pressed'), 'false');
  second.unit().click(); second.download.click();
  assert.equal(query(h).get('actualUnit'), 'true');
  assert.equal(query(h).has('marks'), false); assert.equal(query(h).has('sort'), false);
});

for (const exit of ['cancel', 'download']) test(`reopening after ${exit} resets the actual-unit switch like marks`, () => {
  const h = harness(); let c = ui(h.open()); c.unit().click(); c.marks()[0].click(); c.choose(0, 'rank');
  c[exit].click(); c = ui(h.open());
  assert.equal(c.unit().classList.contains('on'), false); assert.equal(c.unit().getAttribute('aria-pressed'), 'false');
  assert.deepEqual(markStates(c), [[false, 'false'], [false, 'false'], [false, 'false']]);
  assert.equal(c.field(0).value, '');
  c.download.click(); assert.equal(query(h).has('actualUnit'), false); assert.equal(query(h).has('marks'), false);
});

test('actual-unit option combines with marks and sort without changing their protocol', () => {
  const h = harness(); const c = ui(h.open());
  c.all.click(); c.choose(0, 'serviceYears'); c.unit().click(); c.download.click();
  const q = query(h);
  assert.equal(q.get('actualUnit'), 'true'); assert.equal(q.get('marks'), 'party,league,driver'); assert.equal(q.get('sort'), 'serviceYears:desc');
});

test('actual-unit never leaks into roster downloads and the roster note explains full paths', async () => {
  const h = harness({ departmentId: '4' }); const c = ui(h.open());
  c.unit().click(); c.download.click();
  assert.equal(query(h).get('actualUnit'), 'true');
  h.context.openRosterDialog(); await flush();
  const roster = h.modals.at(-1);
  assert.match(roster.dialog.textContent, /实际单位显示完整路径/);
  roster.dialog.querySelectorAll('.roster-field-check')[0].click(); roster.dialog.querySelector('.btn-primary').click();
  const q = query(h);
  assert.equal(q.get('fields'), 'name'); assert.equal(q.get('departmentId'), '4');
  assert.equal(q.has('actualUnit'), false); assert.equal(q.has('sort'), false); assert.equal(q.has('marks'), false);
});
