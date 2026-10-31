'use strict';

// Run from the repository root:
// node --test personnel-server/src/test/frontend/leave-plan-selection.test.cjs
// Only the actual page function is evaluated. No app bootstrap, browser, HTTP,
// business database, external dependency, or production-file modification.
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');

const sourcePath = resolve(__dirname, '../../main/resources/static/ext-features.js');
const source = readFileSync(sourcePath, 'utf8');
const appSource = readFileSync(resolve(__dirname, '../../main/resources/static/app.js'), 'utf8');
const importRule = appSource.match(/^function importRuleText\([^\n]*\) \{[\s\S]*?^\}/m);
assert.ok(importRule, 'expected production importRuleText');
const importRuleScript = new vm.Script(importRule[0]);
// The outer function closes at two-space indentation; nested functions do not.
// Fail explicitly if the source layout changes rather than testing a copied body.
const functions = [...source.matchAll(/^  function renderLeavePlanPage\(\) \{[\s\S]*?^  \}/gm)];
assert.equal(functions.length, 1, 'expected exactly one renderLeavePlanPage');
const pageScript = new vm.Script(`(${functions[0][0]})()`, {
  filename: sourcePath,
  lineOffset: source.slice(0, functions[0].index).split('\n').length - 1,
});

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  })[c]);
}

function decodeHtml(value) {
  return value.replace(/&(amp|lt|gt|quot|#39);/g, (_, entity) => ({
    amp: '&', lt: '<', gt: '>', quot: '"', '#39': "'",
  })[entity]);
}

// Deliberately small DOM: only selectors and HTML tags used by this page.
// Unsupported syntax fails loudly instead of silently hiding coverage gaps.
function selectorParts(selector) {
  const match = /^([a-z][\w-]*)?((?:\.[\w-]+)*)(?:\[([\w-]+)(?:="([^"]*)")?\])?$/.exec(selector);
  assert.ok(match && match[0], `unsupported mock selector: ${selector}`);
  return { tag: match[1], classes: match[2].split('.').filter(Boolean), attr: match[3], value: match[4] };
}

class Element {
  constructor(tag) {
    this.tagName = tag;
    this.attributes = Object.create(null);
    this.children = [];
    this.parentNode = null;
    this.style = {};
    this.value = '';
    this.checked = false;
    this.indeterminate = false;
    this.disabled = false;
    this.listeners = Object.create(null);
    this._text = '';
    this._html = '';
    this.classList = {
      contains: (name) => (this.getAttribute('class') || '').split(/\s+/).includes(name),
      add: (name) => {
        if (!this.classList.contains(name)) this.setAttribute('class', `${this.getAttribute('class') || ''} ${name}`.trim());
      },
      remove: (name) => this.setAttribute('class', (this.getAttribute('class') || '').split(/\s+/).filter((c) => c !== name).join(' ')),
      toggle: (name, force) => {
        const add = force === undefined ? !this.classList.contains(name) : force;
        this.classList[add ? 'add' : 'remove'](name);
        return add;
      },
    };
  }

  setAttribute(name, value) {
    this.attributes[name] = String(value);
    if (name === 'disabled' || name === 'checked') this[name] = true;
    if (name === 'value') this.value = String(value);
  }

  getAttribute(name) {
    return Object.hasOwn(this.attributes, name) ? this.attributes[name] : null;
  }

  appendChild(child) {
    assert.ok(child instanceof Element, 'mock only accepts element children');
    child.remove();
    child.parentNode = this;
    this.children.push(child);
    return child;
  }

  remove() {
    if (this.parentNode) {
      const siblings = this.parentNode.children;
      siblings.splice(siblings.indexOf(this), 1);
      this.parentNode = null;
    }
  }

  set textContent(text) {
    this.clearChildren();
    this._html = '';
    this._text = String(text);
  }

  get textContent() {
    return this._text + this.children.map((child) => child.textContent).join('');
  }

  clearChildren() {
    for (const child of this.children) child.parentNode = null;
    this.children = [];
    this._text = '';
  }

  set innerHTML(html) {
    this.clearChildren();
    this._html = String(html);
    const stack = [this];
    const tokens = this._html.match(/<[^>]*>|[^<]+/g) || [];
    for (const token of tokens) {
      if (!token.startsWith('<')) {
        stack.at(-1)._text += decodeHtml(token);
      } else if (token.startsWith('</')) {
        const tag = /^<\/([a-z]+)>$/.exec(token);
        assert.ok(tag && stack.length > 1, `unsupported mock closing tag: ${token}`);
        assert.equal(stack.pop().tagName, tag[1], 'balanced mock HTML');
      } else {
        const tag = /^<([a-z]+)\b([^>]*)>$/.exec(token);
        assert.ok(tag && /^(thead|tbody|tr|th|td|input|b|button)$/.test(tag[1]), `unsupported mock HTML: ${token}`);
        const child = new Element(tag[1]);
        let attrs = tag[2].trim();
        while (attrs) {
          const attr = /^([\w-]+)(?:="([^"]*)")?(?:\s+|$)/.exec(attrs);
          assert.ok(attr, `unsupported mock attributes: ${attrs}`);
          child.setAttribute(attr[1], decodeHtml(attr[2] ?? ''));
          attrs = attrs.slice(attr[0].length);
        }
        stack.at(-1).appendChild(child);
        if (tag[1] !== 'input') stack.push(child);
      }
    }
    assert.equal(stack.length, 1, 'mock HTML must be closed');
  }

  get innerHTML() {
    return this._html;
  }

  matches(selector) {
    const { tag, classes, attr, value } = selectorParts(selector);
    return (!tag || this.tagName === tag)
      && classes.every((name) => this.classList.contains(name))
      && (!attr || (this.getAttribute(attr) !== null && (value === undefined || this.getAttribute(attr) === value)));
  }

  querySelectorAll(selector) {
    selectorParts(selector);
    const result = [];
    for (const child of this.children) {
      if (child.matches(selector)) result.push(child);
      result.push(...child.querySelectorAll(selector));
    }
    return result;
  }

  querySelector(selector) {
    return this.querySelectorAll(selector)[0] || null;
  }

  closest(selector) {
    return this.matches(selector) ? this : this.parentNode?.closest(selector) || null;
  }

  addEventListener(type, handler) {
    (this.listeners[type] ||= []).push(handler);
  }

  dispatch(type, extra = {}) {
    // Intentionally permits synthetic dispatch on disabled controls: lock tests
    // must exercise handler guards, not just browser disabled-button behavior.
    const event = { type, target: this, ...extra };
    let stopped = false;
    event.stopPropagation = () => { stopped = true; };
    for (let current = this; current; current = current.parentNode) {
      event.currentTarget = current;
      for (const handler of current.listeners[type] || []) handler.call(current, event);
      if (stopped) break;
    }
  }

  click() {
    if (!this.disabled) this.dispatch('click');
  }
}

function h(tag, attrs = {}, ...children) {
  const el = new Element(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (value === false || value == null) continue;
    if (key === 'text') el.textContent = value;
    else if (key === 'html') el.innerHTML = value;
    else if (key.startsWith('on')) el.addEventListener(key.slice(2), value);
    else el.setAttribute(key, value === true ? '' : value);
  }
  for (const child of children.flat()) if (child) el.appendChild(child);
  return el;
}

function deferred(data) {
  let resolvePromise;
  let rejectPromise;
  const call = { ...data, settled: false };
  call.promise = new Promise((resolve, reject) => { resolvePromise = resolve; rejectPromise = reject; });
  call.resolve = (value) => {
    assert.equal(call.settled, false, 'stub request must settle only once');
    call.settled = true;
    resolvePromise(value);
  };
  call.reject = (error) => {
    assert.equal(call.settled, false, 'stub request must settle only once');
    call.settled = true;
    rejectPromise(error);
  };
  return call;
}

// One event-loop turn drains the real Promise chain without timing sleeps.
const flush = () => new Promise((resolve) => setImmediate(resolve));
const rowsFor = (...ids) => ids.map((id) => ({ id, personnel_name: `测试人员${id}`, year: 2026 }));
const baseUrl = '/api/leave-plan?page=1&size=200';

function harness(t) {
  const body = h('body');
  const content = h('main', { class: 'content' });
  body.appendChild(h('div', { class: 'topbar-title' }));
  body.appendChild(content);
  const calls = { get: [], del: [], confirm: [], toast: [], form: [] };
  const queue = (kind, data) => {
    const request = deferred(data);
    calls[kind].push(request);
    return request.promise;
  };
  const document = {
    body,
    querySelector: (selector) => body.querySelector(selector),
    querySelectorAll: (selector) => body.querySelectorAll(selector),
  };
  const context = vm.createContext({
    document, h,
    history: { replaceState() {} },
    localStorage: { setItem() {} },
    LEAVE_CUSTOM_ITEMS: [],
    $all: (selector, root = document) => root.querySelectorAll(selector),
    esc: escapeHtml,
    dictLabels: () => ['年假', '探亲假'],
    makeDropdown: () => h('select'),
    makeDateField: () => h('input', { type: 'date' }),
    GET: (url) => {
      assert.ok(url.startsWith(baseUrl), `unexpected GET: ${url}`);
      return queue('get', { url });
    },
    DEL: (url) => {
      assert.match(url, /^\/api\/leave-plan\/[^/]+$/, 'DEL must stay inside the stub endpoint');
      return queue('del', { url });
    },
    confirmBox: (message, danger) => queue('confirm', { message, danger }),
    toast: (message, type) => calls.toast.push({ message, type }),
    openPlanForm: (row) => calls.form.push(row),
    // No require/process/fetch/XMLHttpRequest or real transport is exposed.
  }, { codeGeneration: { strings: false, wasm: false } });
  importRuleScript.runInContext(context, { timeout: 1000 });
  pageScript.runInContext(context, { timeout: 1000 });
  const table = content.querySelector('table');
  const api = {
    calls, content, table,
    all: () => table.querySelector('.lp-cb-all'),
    checkboxes: () => table.querySelectorAll('.lp-cb-row'),
    batch: content.querySelector('.toolbar').querySelectorAll('button')[0],
    toolbar: content.querySelector('.toolbar'),
    query: content.querySelector('.filter-actions').querySelectorAll('button')[0],
    reset: content.querySelector('.filter-actions').querySelectorAll('button')[1],
    filter: (name) => content.querySelector(`[data-lp-filter="${name}"]`),
    type: content.querySelector('select'),
    rowIds: () => table.querySelectorAll('.lp-cb-row').map((cb) => cb.getAttribute('data-id')),
    choose(id, checked = true) {
      const cb = api.checkboxes().find((item) => item.getAttribute('data-id') === String(id));
      assert.ok(cb, `missing row ${id}`);
      cb.checked = checked;
      cb.dispatch('change');
    },
    chooseAll(checked = true) {
      api.all().checked = checked;
      api.all().dispatch('change');
    },
    action(id, act) {
      const button = table.querySelectorAll('button[data-act]').find((el) => el.getAttribute('data-id') === String(id) && el.getAttribute('data-act') === act);
      assert.ok(button, `missing ${act} button for ${id}`);
      return button;
    },
    async loaded(rows) {
      assert.equal(calls.get.length, 1);
      assert.equal(calls.get[0].url, baseUrl);
      calls.get[0].resolve({ list: rows });
      await flush();
    },
  };
  t.after(() => {
    for (const kind of ['get', 'del', 'confirm']) {
      assert.ok(calls[kind].every((request) => request.settled), `unsettled ${kind} stub request`);
    }
  });
  return api;
}

function assertSelection(page, selected) {
  assert.deepEqual(page.checkboxes().filter((cb) => cb.checked).map((cb) => cb.getAttribute('data-id')).sort(), selected.map(String).sort());
  assert.equal(page.all().checked, page.checkboxes().length > 0 && selected.length === page.checkboxes().length);
  assert.equal(page.all().indeterminate, selected.length > 0 && selected.length < page.checkboxes().length);
  assert.equal(page.batch.textContent, selected.length ? `删除所选(${selected.length})` : '删除所选');
  assert.equal(page.batch.disabled, selected.length === 0);
}

function assertDeletionLocked(page) {
  assert.equal(page.batch.disabled, true);
  assert.equal(page.batch.textContent, '删除处理中…');
  assert.equal(page.all().disabled, true);
  for (const control of [...page.checkboxes(), ...page.table.querySelectorAll('button[data-act]'), ...page.toolbar.querySelectorAll('button')]) {
    assert.equal(control.disabled, true, 'selection, row actions and toolbar must lock');
  }
}

function assertNoDeletes(page) {
  assert.equal(page.calls.del.length, 0);
}

test('empty list: eleven headers, colspan=11, no enabled selection or deletion', async (t) => {
  const page = harness(t);
  assert.equal(page.all().disabled, true, 'initial load locks selection');
  assert.equal(page.batch.disabled, true);
  await page.loaded([]);
  assert.equal(page.table.querySelectorAll('th').length, 11);
  const cell = page.table.querySelector('tbody').querySelector('td');
  assert.equal(cell.getAttribute('colspan'), '11');
  assert.equal(cell.textContent, '暂无数据');
  assertSelection(page, []);
  assert.equal(page.all().disabled, true);
  page.batch.dispatch('click');
  page.chooseAll();
  assert.equal(page.all().checked, false);
  assert.equal(page.calls.confirm.length, 0);
  assertNoDeletes(page);
});

test('toolbar: spacer leads, delete-selected first among the right-hand actions, no hint text', async (t) => {
  const page = harness(t);
  await page.loaded([]);
  const children = page.toolbar.children;
  const spacerIndex = children.indexOf(page.toolbar.querySelector('.spacer'));
  assert.equal(spacerIndex, 0);
  assert.equal(children[spacerIndex + 1], page.batch);
  assert.doesNotMatch(page.toolbar.textContent, /年度休假安排/);
  assert.deepEqual(page.toolbar.querySelectorAll('button').map((button) => button.textContent),
    ['删除所选', '下载模板', '导入 Excel', '导出 Excel', '新增休假计划']);
});

test('leave-plan import title and confirmation include the shared date hint and keep xlsx-only input', async (t) => {
  const page = harness(t);
  await page.loaded([]);
  const button = page.toolbar.querySelectorAll('button').find((el) => el.textContent === '导入 Excel');
  const hint = '日期列支持20160901、2016-09-01、2016.09.01、2016/9/1、2016年9月1日及Excel日期单元格，统一识别真实日期，非法日期会报错。';
  assert.ok(button.getAttribute('title').endsWith('\n' + hint));
  assert.match(button.getAttribute('title'), /增量导入/);
  assert.equal(page.toolbar.querySelector('input[type="file"]').getAttribute('accept'), '.xlsx');
  button.click();
  assert.equal(page.calls.confirm.length, 1);
  assert.equal(page.calls.confirm[0].message, button.getAttribute('title') + '\n\n确定要继续选择文件导入吗？');
  page.calls.confirm[0].resolve(false);
  await flush();
  assert.equal(page.calls.get.length, 1, 'cancel keeps the page without reloading');
  assertNoDeletes(page);
});

test('single selection, indeterminate, full selection and deselection remain synchronized', async (t) => {
  const page = harness(t);
  await page.loaded(rowsFor(11, 22, 33));
  assertSelection(page, []);
  page.choose(22);
  assertSelection(page, [22]);
  page.choose(11);
  assertSelection(page, [11, 22]);
  page.choose(33);
  assertSelection(page, [11, 22, 33]);
  page.choose(22, false);
  assertSelection(page, [11, 33]);
  page.chooseAll(false);
  assertSelection(page, []);
  page.chooseAll();
  assertSelection(page, [11, 22, 33]);
  page.chooseAll(false);
  assertSelection(page, []);
  assertNoDeletes(page);
});

test('one-row list: selecting the row also checks the header without indeterminate', async (t) => {
  const page = harness(t);
  await page.loaded(rowsFor(5));
  page.choose(5);
  assertSelection(page, [5]);
  page.choose(5, false);
  assertSelection(page, []);
});

test('cancel batch confirmation: no DEL, no reload, preserve selection and unlock', async (t) => {
  const page = harness(t);
  await page.loaded(rowsFor(1, 2, 3));
  page.choose(1);
  page.choose(3);
  page.batch.click();
  assert.equal(page.calls.confirm.length, 1);
  assert.equal(page.calls.confirm[0].message, '确定删除选中的 2 条休假计划吗?删除后不可恢复。');
  assert.equal(page.calls.confirm[0].danger, true);
  assertDeletionLocked(page);
  assertNoDeletes(page);
  page.calls.confirm[0].resolve(false);
  await flush();
  assertNoDeletes(page);
  assert.equal(page.calls.get.length, 1);
  assertSelection(page, [1, 3]);
  assert.equal(page.all().disabled, false);
  assert.ok(page.toolbar.querySelectorAll('button').every((button) => !button.disabled));
  assert.deepEqual(page.calls.toast, []);
});

test('successful batch: fixed selected IDs, encoded URLs, strictly serial DEL and one final refresh', async (t) => {
  const page = harness(t);
  await page.loaded(rowsFor('id/7', '未选择', '甲 乙', 42));
  page.choose('id/7');
  page.choose('甲 乙');
  page.choose(42);
  page.batch.click();
  page.calls.confirm[0].resolve(true);
  await flush();
  // Object.keys puts integer keys first; selection order is not a contract.
  const expectedIds = ['42', 'id/7', '甲 乙'];
  for (let index = 0; index < expectedIds.length; index++) {
    assert.equal(page.calls.del.length, index + 1, 'next DEL must wait for the previous one');
    assert.equal(page.calls.del[index].url, `/api/leave-plan/${encodeURIComponent(expectedIds[index])}`);
    assert.equal(page.calls.get.length, 1, 'no refresh before the entire batch settles');
    assertDeletionLocked(page);
    page.choose('未选择');
    assert.equal(page.checkboxes().find((cb) => cb.getAttribute('data-id') === '未选择').checked, false, 'locked selection cannot enlarge the ID snapshot');
    await flush();
    assert.equal(page.calls.del.length, index + 1);
    page.calls.del[index].resolve({});
    await flush();
  }
  assert.deepEqual(page.calls.toast, [{ message: '批量删除完成:成功 3 条', type: 'ok' }]);
  assert.equal(page.calls.get.length, 2);
  assert.equal(page.calls.get[1].url, baseUrl);
  assert.equal(page.batch.disabled, true, 'refresh must clear selection and keep controls locked');
  assert.ok(page.checkboxes().every((cb) => !cb.checked && cb.disabled));
  page.calls.get[1].resolve({ list: rowsFor('未选择') });
  await flush();
  assert.deepEqual(page.rowIds(), ['未选择']);
  assertSelection(page, []);
  assert.equal(page.all().disabled, false);
});

test('partial failures: continue serially, count successes/failures, show first error and refresh', async (t) => {
  const page = harness(t);
  await page.loaded(rowsFor(1, 2, 3));
  page.chooseAll();
  page.batch.click();
  page.calls.confirm[0].resolve(true);
  await flush();
  page.calls.del[0].reject(new Error('第一条失败'));
  await flush();
  assert.equal(page.calls.del.length, 2);
  page.calls.del[1].resolve({});
  await flush();
  assert.equal(page.calls.del.length, 3);
  page.calls.del[2].reject(new Error('后续错误不应替换首个错误'));
  await flush();
  assert.deepEqual(page.calls.del.map((request) => request.url), ['/api/leave-plan/1', '/api/leave-plan/2', '/api/leave-plan/3']);
  assert.deepEqual(page.calls.toast, [{ message: '批量删除完成:成功 1 条,失败 2 条。第一条失败', type: 'info' }]);
  assert.equal(page.calls.get.length, 2);
  page.calls.get[1].resolve({ list: rowsFor(1, 3) });
  await flush();
  assertSelection(page, []);
  page.choose(1);
  assertSelection(page, [1]);
});

test('all failed: report zero successes, fallback error, still reload and release lock', async (t) => {
  const page = harness(t);
  await page.loaded(rowsFor(9));
  page.chooseAll();
  page.batch.click();
  page.calls.confirm[0].resolve(true);
  await flush();
  page.calls.del[0].reject({});
  await flush();
  assert.deepEqual(page.calls.toast, [{ message: '批量删除完成:成功 0 条,失败 1 条。请求失败', type: 'info' }]);
  page.calls.get[1].resolve({ list: rowsFor(9) });
  await flush();
  assertSelection(page, []);
  assert.equal(page.all().disabled, false);
});

test('deletion lock guards duplicate batch/row delete/edit and filter loads during confirmation and DEL', async (t) => {
  const page = harness(t);
  await page.loaded(rowsFor(1, 2));
  page.choose(1);
  page.batch.click();
  function attemptLockedActions() {
    assertDeletionLocked(page);
    page.batch.dispatch('click');
    page.action(2, 'del').dispatch('click');
    page.action(2, 'edit').dispatch('click');
    page.query.dispatch('click');
    page.filter('personnelName').dispatch('keydown', { key: 'Enter' });
    page.type.dispatch('change');
    page.choose(2);
    page.chooseAll(false);
    assert.equal(page.checkboxes()[0].checked, true);
    assert.equal(page.checkboxes()[1].checked, false);
    assert.equal(page.calls.confirm.length, 1);
    assert.equal(page.calls.get.length, 1);
    assert.equal(page.calls.form.length, 0);
  }
  attemptLockedActions();
  assertNoDeletes(page);
  page.calls.confirm[0].resolve(true);
  await flush();
  attemptLockedActions();
  assert.equal(page.calls.del.length, 1);
  page.calls.del[0].resolve({});
  await flush();
  page.calls.get[1].resolve({ list: rowsFor(2) });
  await flush();
  assertSelection(page, []);
  page.choose(2);
  page.batch.click();
  assert.equal(page.calls.confirm.length, 2, 'a later operation is allowed after unlock');
  page.calls.confirm[1].resolve(false);
  await flush();
});

test('confirmation rejection: show error, preserve selection, release lock without deleting or refreshing', async (t) => {
  const page = harness(t);
  await page.loaded(rowsFor(6));
  page.choose(6);
  page.batch.click();
  page.calls.confirm[0].reject(new Error('确认框失败'));
  await flush();
  assertSelection(page, [6]);
  assert.equal(page.all().disabled, false);
  assertNoDeletes(page);
  assert.equal(page.calls.get.length, 1);
  assert.deepEqual(page.calls.toast, [{ message: '确认框失败', type: 'err' }]);
});

test('single-row deletion uses only that row ID, not the batch selection', async (t) => {
  const page = harness(t);
  await page.loaded(rowsFor(1, 2));
  page.choose(1);
  page.action(2, 'del').click();
  assert.equal(page.calls.confirm[0].message, '确定删除「测试人员2」的这条休假计划吗?');
  assertDeletionLocked(page);
  page.calls.confirm[0].resolve(true);
  await flush();
  assert.equal(page.calls.del.length, 1);
  assert.equal(page.calls.del[0].url, '/api/leave-plan/2');
  page.calls.del[0].resolve({});
  await flush();
  assert.deepEqual(page.calls.toast, [{ message: '已删除', type: 'ok' }]);
  page.calls.get[1].resolve({ list: rowsFor(1) });
  await flush();
  assertSelection(page, []);
});

test('filter load: encode filters, clear selection immediately, reject actions until rows arrive; reset clears filters', async (t) => {
  const page = harness(t);
  await page.loaded(rowsFor(1, 2));
  page.chooseAll();
  const filters = {
    personnelName: '  张 & 三  ', idCard: '  123456 X  ', year: ' 2026 ',
    startDate: '2026-01-02', endDate: '2026-12-31',
  };
  for (const [key, value] of Object.entries(filters)) page.filter(key).value = value;
  page.type.value = '探亲假';
  page.filter('personnelName').dispatch('keydown', { key: 'Escape' });
  assert.equal(page.calls.get.length, 1, 'non-Enter key must not load');
  page.query.click();
  const expected = new URLSearchParams({
    personnelName: '张 & 三', idCard: '123456 X', leaveType: '探亲假', year: '2026',
    startDate: '2026-01-02', endDate: '2026-12-31',
  });
  const actual = new URL(page.calls.get[1].url, 'https://stub.invalid').searchParams;
  assert.equal(actual.get('page'), '1');
  assert.equal(actual.get('size'), '200');
  for (const [key, value] of expected) assert.equal(actual.get(key), value);
  assert.equal(page.batch.textContent, '删除所选');
  assert.equal(page.batch.disabled, true);
  assert.ok(page.checkboxes().every((cb) => !cb.checked && cb.disabled));
  page.chooseAll();
  page.choose(1);
  assert.ok(page.checkboxes().every((cb) => !cb.checked));
  page.batch.dispatch('click');
  page.action(1, 'del').dispatch('click');
  assert.equal(page.calls.confirm.length, 0);
  assertNoDeletes(page);
  page.calls.get[1].resolve({ list: rowsFor(3) });
  await flush();
  assert.deepEqual(page.rowIds(), ['3']);
  assertSelection(page, []);
  page.choose(3);
  page.reset.click();
  assert.equal(page.calls.get[2].url, baseUrl);
  for (const key of Object.keys(filters)) assert.equal(page.filter(key).value, '');
  assert.equal(page.type.value, '');
  assert.equal(page.batch.textContent, '删除所选');
  page.calls.get[2].resolve({ list: rowsFor(1, 2) });
  await flush();
  assertSelection(page, []);
});

for (const trigger of ['personnelName', 'idCard', 'year', 'leaveType', 'startDate', 'endDate']) {
  test(`filter ${trigger}: Enter/change starts load and clears previous selection`, async (t) => {
    const page = harness(t);
    await page.loaded(rowsFor(1));
    page.choose(1);
    if (trigger === 'leaveType') {
      page.type.value = '年假';
      page.type.dispatch('change');
    } else {
      const input = page.filter(trigger);
      input.value = ({ personnelName: '姓名', idCard: '123', year: '2026' })[trigger] || '2026-02-01';
      input.dispatch(['startDate', 'endDate'].includes(trigger) ? 'change' : 'keydown', { key: 'Enter' });
    }
    assert.equal(page.calls.get.length, 2);
    assert.ok(new URL(page.calls.get[1].url, 'https://stub.invalid').searchParams.has(trigger));
    assert.equal(page.batch.textContent, '删除所选');
    assert.equal(page.batch.disabled, true);
    assert.equal(page.checkboxes()[0].checked, false);
    page.calls.get[1].resolve({ list: rowsFor(2) });
    await flush();
    assertSelection(page, []);
    assertNoDeletes(page);
  });
}

for (const staleOutcome of ['success', 'failure']) {
  for (const order of ['old-first', 'new-first']) {
    test(`concurrent load: stale ${staleOutcome}, ${order}, cannot replace rows/selection or release latest lock`, async (t) => {
      const page = harness(t);
      await page.loaded(rowsFor('original'));
      page.choose('original');
      page.query.click();
      page.filter('personnelName').value = 'latest';
      page.filter('personnelName').dispatch('keydown', { key: 'Enter' });
      assert.equal(page.calls.get.length, 3);
      const old = page.calls.get[1];
      const latest = page.calls.get[2];
      const settleOld = () => staleOutcome === 'success'
        ? old.resolve({ list: rowsFor('stale') }) : old.reject(new Error('过期请求错误'));
      if (order === 'old-first') {
        settleOld();
        await flush();
        assert.deepEqual(page.rowIds(), ['original']);
        assert.equal(page.all().disabled, true, 'stale result must not release newer load lock');
        assert.equal(page.batch.disabled, true);
        assert.ok(page.checkboxes().every((cb) => !cb.checked && cb.disabled));
      }
      latest.resolve({ list: rowsFor('latest-a', 'latest-b') });
      await flush();
      page.choose('latest-b');
      assertSelection(page, ['latest-b']);
      if (order === 'new-first') {
        settleOld();
        await flush();
      }
      assert.deepEqual(page.rowIds(), ['latest-a', 'latest-b']);
      assertSelection(page, ['latest-b']);
      assert.equal(page.all().disabled, false);
      assert.deepEqual(page.calls.toast, [], 'stale errors must not show a toast');
      assertNoDeletes(page);
    });
  }
}

test('current load failure: clear rows/selection, ten-column error, allow query retry', async (t) => {
  const page = harness(t);
  await page.loaded(rowsFor(1));
  page.choose(1);
  page.query.click();
  page.calls.get[1].reject(new Error('模拟加载失败'));
  await flush();
  assert.deepEqual(page.rowIds(), []);
  const cell = page.table.querySelector('tbody').querySelector('td');
  assert.equal(cell.getAttribute('colspan'), '11');
  assert.equal(cell.textContent, '加载失败，请重试');
  assertSelection(page, []);
  assert.equal(page.all().disabled, true);
  assert.deepEqual(page.calls.toast, [{ message: '模拟加载失败', type: 'err' }]);
  page.query.click();
  assert.equal(page.calls.get.length, 3);
  page.calls.get[2].resolve({ list: rowsFor(2) });
  await flush();
  assertSelection(page, []);
  assert.equal(page.all().disabled, false);
  assertNoDeletes(page);
});
