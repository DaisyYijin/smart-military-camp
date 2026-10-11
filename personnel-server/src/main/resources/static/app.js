/* 数智军营人员管理系统 - 网页端(零依赖原生 JS,离线可用) */
'use strict';

/* ================= 工具函数 ================= */

function $(sel, root) { return (root || document).querySelector(sel); }
function $all(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }

function esc(s) {
  if (s === null || s === undefined) return '';
  return String(s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function h(tag, attrs) {
  var el = document.createElement(tag);
  attrs = attrs || {};
  Object.keys(attrs).forEach(function (k) {
    var v = attrs[k];
    if (v === null || v === undefined || v === false) return;
    if (k === 'class') el.className = v;
    else if (k === 'html') el.innerHTML = v;
    else if (k === 'text') el.textContent = v;
    else if (k.slice(0, 2) === 'on') el.addEventListener(k.slice(2), v);
    else el.setAttribute(k, v === true ? '' : v);
  });
  for (var i = 2; i < arguments.length; i++) {
    var c = arguments[i];
    if (c === null || c === undefined || c === false) continue;
    if (Array.isArray(c)) c.forEach(function (x) { if (x) el.appendChild(x); });
    else if (typeof c === 'string') el.appendChild(document.createTextNode(c));
    else el.appendChild(c);
  }
  return el;
}

function toast(msg, type) {
  var box = $('#toast-box');
  if (!box) { box = h('div', { id: 'toast-box' }); document.body.appendChild(box); }
  var t = h('div', { class: 'toast ' + (type || 'ok'), text: msg });
  box.appendChild(t);
  setTimeout(function () { t.style.opacity = '0'; t.style.transition = 'opacity .3s'; }, 2400);
  setTimeout(function () { t.remove(); }, 2800);
}

function openModal(opt) {
  var mask = h('div', { class: 'mask' });
  var closeBtn = h('button', { class: 'dialog-close', text: '×', onclick: function () { close(); } });
  var head = h('div', { class: 'dialog-head' }, h('div', { class: 'ttl', text: opt.title || '' }), closeBtn);
  var body = h('div', { class: 'dialog-body' });
  if (opt.body) body.appendChild(opt.body);
  var dlg = h('div', { class: 'dialog' + (opt.wide ? ' wide' : '') }, head, body);
  if (opt.footer) dlg.appendChild(h('div', { class: 'dialog-foot' }, opt.footer));
  mask.appendChild(dlg);
  mask.addEventListener('mousedown', function (e) { if (e.target === mask) close(); });
  document.body.appendChild(mask);
  function close() { mask.remove(); if (opt.onClose) opt.onClose(); }
  return { close: close, body: body, dialog: dlg };
}

function confirmBox(msg, danger) {
  return new Promise(function (resolve) {
    var ok = false;
    var m = openModal({
      title: '操作确认',
      body: h('div', { style: 'line-height:1.7', text: msg }),
      footer: [
        h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m.close(); } }),
        h('button', {
          class: 'btn ' + (danger ? 'btn-danger' : 'btn-primary'), text: '确定',
          onclick: function () { ok = true; m.close(); }
        })
      ],
      onClose: function () { resolve(ok); }
    });
  });
}

function badge(text) {
  var t = text || '';
  var cls = 'badge';
  if (/在职|正常|优秀|已归还|启用/.test(t)) cls += ' green';
  else if (/休假|借调|培训|良好|使用中/.test(t)) cls += ' blue';
  else if (/维修|保养|及格|预警|缺货/.test(t)) cls += ' orange';
  else if (/离职|报废|不及格|损坏|停用/.test(t)) cls += ' red';
  else if (/其他|未填写/.test(t)) cls += ' gray';
  return '<span class="' + cls + '">' + esc(t || '-') + '</span>';
}

/* ================= API 层 ================= */

var TOKEN = localStorage.getItem('tk') || '';
var USER = null;
try { USER = JSON.parse(localStorage.getItem('usr') || 'null'); } catch (e) { USER = null; }

function saveSession(token, user) {
  TOKEN = token; USER = user;
  localStorage.setItem('tk', token);
  localStorage.setItem('usr', JSON.stringify(user || {}));
}
function clearSession() {
  TOKEN = ''; USER = null;
  localStorage.removeItem('tk'); localStorage.removeItem('usr');
}

function forceLogin(msg) {
  if ($('.login-wrap')) { if (msg) toast(msg, 'err'); return; }
  clearSession();
  renderLogin(msg);
}

function api(method, path, body) {
  var headers = {};
  if (TOKEN) headers['X-Auth-Token'] = TOKEN;
  var payload;
  if (body instanceof FormData) {
    payload = body;
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  return fetch(path, { method: method, headers: headers, body: payload }).then(function (res) {
    if (res.status === 401) {
      forceLogin('登录已过期,请重新登录');
      throw new Error('未登录');
    }
    return res.json().catch(function () { return null; }).then(function (data) {
      if (!res.ok || (data && data.code !== 0)) {
        throw new Error((data && data.message) || ('请求失败(' + res.status + ')'));
      }
      return data ? data.data : null;
    });
  });
}
function GET(p) { return api('GET', p); }
function POST(p, b) { return api('POST', p, b); }
function PUT(p, b) { return api('PUT', p, b); }
function DEL(p) { return api('DELETE', p); }

function apiBlob(path) {
  var headers = {};
  if (TOKEN) headers['X-Auth-Token'] = TOKEN;
  return fetch(path, { headers: headers }).then(function (res) {
    if (res.status === 401) { forceLogin('登录已过期,请重新登录'); throw new Error('未登录'); }
    if (!res.ok) throw new Error('下载失败(' + res.status + ')');
    var cd = res.headers.get('Content-Disposition') || '';
    var m = /filename\*=UTF-8''([^;]+)/.exec(cd);
    var filename = m ? decodeURIComponent(m[1]) : 'download.bin';
    return res.blob().then(function (b) { return { blob: b, filename: filename }; });
  });
}

function downloadBlob(blob, filename) {
  var url = URL.createObjectURL(blob);
  var a = h('a', { href: url, download: filename || '' });
  document.body.appendChild(a);
  a.click();
  setTimeout(function () { URL.revokeObjectURL(url); a.remove(); }, 3000);
}

/* ================= 全局缓存 ================= */

var Ctx = {
  dictTypes: [],        // [{type,name,items:[DictItem]}]
  deptTree: [],         // 嵌套部门树
  deptFlat: [],         // [{id,name,depth}]
  personnelOptions: [], // [{id,name,departmentName,rank,post}]
  photoUrls: {}
};

function refreshDicts() {
  return GET('/api/dict/types').then(function (list) {
    Ctx.dictTypes = list || [];
  }).catch(function () { Ctx.dictTypes = []; });
}

function dictItems(type) {
  for (var i = 0; i < Ctx.dictTypes.length; i++) {
    if (Ctx.dictTypes[i].type === type) return Ctx.dictTypes[i].items || [];
  }
  return [];
}
function dictLabels(type) {
  return dictItems(type)
    .filter(function (d) { return d.enabled !== false; })
    .map(function (d) { return d.dictLabel; });
}

function refreshDepts() {
  return GET('/api/departments').then(function (tree) {
    Ctx.deptTree = tree || [];
    Ctx.deptFlat = [];
    (function walk(nodes, depth) {
      (nodes || []).forEach(function (n) {
        Ctx.deptFlat.push({ id: n.id, name: n.name, depth: depth });
        walk(n.children, depth + 1);
      });
    })(Ctx.deptTree, 0);
  }).catch(function () { Ctx.deptTree = []; Ctx.deptFlat = []; });
}

function refreshPersonnelOptions() {
  return GET('/api/personnel/options').then(function (list) {
    Ctx.personnelOptions = list || [];
  }).catch(function () { Ctx.personnelOptions = []; });
}

function refreshAll() {
  return Promise.all([refreshDicts(), refreshDepts(), refreshPersonnelOptions()]);
}

function loadRowPhotos(root) {
  $all('img[data-pid]', root).forEach(function (img) {
    var id = img.getAttribute('data-pid');
    var key = 'p' + id;
    if (Ctx.photoUrls[key]) { img.src = Ctx.photoUrls[key]; return; }
    apiBlob('/api/personnel/' + id + '/photo').then(function (r) {
      var url = URL.createObjectURL(r.blob);
      Ctx.photoUrls[key] = url;
      img.src = url;
    }).catch(function () { /* 无照片,保持占底色 */ });
  });
}

/* ================= 表单构建 ================= */

function buildForm(fields, values) {
  values = values || {};
  var grid = h('div', { class: 'form-grid' });
  var inputs = {};
  var datalistSeq = 0;

  fields.forEach(function (f) {
    if (f.section) {
      grid.appendChild(h('div', { class: 'form-section', text: f.section }));
      return;
    }
    var wrap = h('div', { class: 'form-item' + (f.span === 'full' ? ' full' : '') });
    wrap.appendChild(h('label', { html: esc(f.label) + (f.required ? '<span class="req">*</span>' : '') }));

    var val = values[f.key];
    var el;
    if (f.type === 'select' || f.type === 'personnel' || f.type === 'dept') {
      el = h('select', {});
      if (f.type === 'select') {
        var options = (typeof f.options === 'function' ? f.options() : f.options) || [];
        if (!f.required) el.appendChild(h('option', { value: '', text: '请选择' }));
        options.forEach(function (o) {
          var v = (o && o.v !== undefined) ? o.v : o;
          var l = (o && o.l !== undefined) ? o.l : o;
          el.appendChild(h('option', { value: String(v), text: String(l) }));
        });
      } else if (f.type === 'personnel') {
        el.appendChild(h('option', { value: '', text: '请选择人员' }));
        Ctx.personnelOptions.forEach(function (p) {
          var label = p.name + (p.departmentName ? ' (' + p.departmentName + ')' : '');
          el.appendChild(h('option', { value: String(p.id), text: label }));
        });
      } else {
        el.appendChild(h('option', { value: '', text: f.required ? '请选择部门' : '(不指定)' }));
        Ctx.deptFlat.forEach(function (d) {
          var prefix = new Array(d.depth + 1).join('　');
          el.appendChild(h('option', { value: String(d.id), text: prefix + d.name }));
        });
      }
      if (val !== undefined && val !== null) el.value = String(val);
    } else if (f.type === 'textarea') {
      el = h('textarea', { placeholder: f.placeholder || '' });
      el.value = val || '';
    } else if (f.datalist) {
      var listId = 'dl' + (++datalistSeq);
      var list = h('datalist', { id: listId });
      var labels = (typeof f.datalist === 'function' ? f.datalist() : f.datalist) || [];
      labels.forEach(function (l) { list.appendChild(h('option', { value: l })); });
      wrap.appendChild(list);
      el = h('input', { type: 'text', list: listId, placeholder: f.placeholder || '可输入或从列表选择' });
      el.value = val || '';
    } else {
      var type = f.type === 'number' ? 'number' : (f.type === 'date' ? 'date' : 'text');
      el = h('input', { type: type, placeholder: f.placeholder || '' });
      if (f.step) el.setAttribute('step', f.step);
      if (val !== undefined && val !== null) el.value = val;
    }
    inputs[f.key] = el;
    wrap.appendChild(el);
    grid.appendChild(wrap);
  });

  var err = h('div', { class: 'form-err' });

  return {
    el: h('div', {}, grid, err),
    setError: function (msg) { err.textContent = msg || ''; },
    get: function () {
      var out = {};
      var missing = [];
      fields.forEach(function (f) {
        if (f.section) return;
        var el = inputs[f.key];
        if (f.type === 'number') {
          out[f.key] = el.value === '' ? null : Number(el.value);
          return;
        }
        var v = String(el.value || '').trim();
        out[f.key] = v;
        if (f.required && v === '') missing.push(f.label);
      });
      if (missing.length) {
        this.setError('请填写:' + missing.join('、'));
        return null;
      }
      return out;
    }
  };
}

/* ================= 分页 ================= */

function renderPager(state, onChange) {
  var totalPages = Math.max(1, Math.ceil(state.total / state.size));
  var box = h('div', { class: 'pager' });
  box.appendChild(h('span', {
    class: 'info',
    text: '共 ' + state.total + ' 条 / 第 ' + state.page + '/' + totalPages + ' 页'
  }));
  function btn(text, page, extraDisabled, isNum) {
    var disabled = !!extraDisabled || (isNum && page === state.page);
    return h('button', {
      text: text, disabled: disabled,
      class: isNum && page === state.page ? 'cur' : '',
      onclick: function () {
        if (!disabled && page >= 1 && page <= totalPages && page !== state.page) onChange(page);
      }
    });
  }
  box.appendChild(btn('‹ 上一页', state.page - 1, state.page <= 1));
  var start = Math.max(1, state.page - 3);
  var end = Math.min(totalPages, start + 6);
  start = Math.max(1, end - 6);
  if (start > 1) box.appendChild(h('span', { text: '…' }));
  for (var p = start; p <= end; p++) box.appendChild(btn(String(p), p, false, true));
  if (end < totalPages) box.appendChild(h('span', { text: '…' }));
  box.appendChild(btn('下一页 ›', state.page + 1, state.page >= totalPages));
  return box;
}

/* ================= 通用 CRUD 视图 ================= */

function crudView(cfg) {
  var state = { page: 1, size: 20, total: 0, rows: [], filters: {}, loading: false };
  var root = h('div');
  var pagerBox = h('div');

  function collectFilters() {
    var out = {};
    $all('[data-fkey]', root).forEach(function (el) {
      out[el.getAttribute('data-fkey')] = el.value.trim();
    });
    return out;
  }

  function load() {
    state.filters = collectFilters();
    state.loading = true;
    renderTable();
    var q = {};
    Object.keys(state.filters).forEach(function (k) {
      if (state.filters[k] !== '') q[k] = state.filters[k];
    });
    q.page = state.page; q.size = state.size;
    var qs = Object.keys(q).map(function (k) { return k + '=' + encodeURIComponent(q[k]); }).join('&');
    GET(cfg.endpoint + (qs ? '?' + qs : '')).then(function (pageData) {
      state.rows = (pageData && pageData.list) || [];
      state.total = (pageData && pageData.total) || 0;
    }).catch(function (e) {
      state.rows = []; state.total = 0;
      toast(e.message, 'err');
    }).then(function () {
      state.loading = false;
      renderTable();
      renderPagerBox();
      if (cfg.afterTable) cfg.afterTable(root);
    });
  }

  function renderPagerBox() {
    pagerBox.innerHTML = '';
    pagerBox.appendChild(renderPager(state, function (p) { state.page = p; load(); }));
  }

  function renderTable() {
    var tb = $('.grid tbody', root);
    if (!tb) return;
    var cols = cfg.columns.length + 1;
    if (state.loading) {
      tb.innerHTML = '<tr><td colspan="' + cols + '" class="empty-row">加载中…</td></tr>';
      return;
    }
    if (!state.rows.length) {
      tb.innerHTML = '<tr><td colspan="' + cols + '" class="empty-row">暂无数据</td></tr>';
      return;
    }
    tb.innerHTML = state.rows.map(function (row) {
      var tds = cfg.columns.map(function (c) {
        var raw = row[c.key] === null || row[c.key] === undefined ? '' : row[c.key];
        var inner = c.render ? c.render(row) : esc(raw);
        return '<td title="' + esc(raw) + '">' + inner + '</td>';
      }).join('');
      return '<tr>' + tds +
        '<td class="actions">' +
        '<button class="btn-link" data-act="edit" data-id="' + row.id + '">编辑</button>' +
        '<button class="btn-link danger" data-act="del" data-id="' + row.id + '">删除</button>' +
        '</td></tr>';
    }).join('');
  }

  function openForm(row) {
    var form = buildForm(cfg.form, row || {});
    var extra = cfg.formTop ? cfg.formTop(row) : null;
    var body = h('div');
    if (extra) body.appendChild(extra.el);
    body.appendChild(form.el);

    var m = openModal({
      title: (row ? '编辑 · ' : '新增 · ') + cfg.title + (row && cfg.rowTitle ? ' - ' + cfg.rowTitle(row) : ''),
      body: body,
      wide: cfg.wide,
      footer: [
        h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m.close(); } }),
        h('button', {
          class: 'btn btn-primary', text: '保存',
          onclick: function () {
            var data = form.get();
            if (!data) return;
            if (extra && extra.get) {
              var ex = extra.get();
              if (!ex) return;
              Object.keys(ex).forEach(function (k) { if (ex[k] !== undefined) data[k] = ex[k]; });
            }
            var done = row ? PUT(cfg.endpoint + '/' + row.id, data) : POST(cfg.endpoint, data);
            done.then(function () {
              m.close();
              toast('保存成功');
              afterChange();
            }).catch(function (e) { form.setError(e.message); });
          }
        })
      ]
    });
  }

  function afterChange() {
    load();
    refreshDepts();
    refreshPersonnelOptions();
  }

  function render(container) {
    var toolbar = h('div', { class: 'toolbar' });
    (cfg.filters || []).forEach(function (f) {
      var inline = h('div', { class: 'field-inline' });
      inline.appendChild(h('label', { text: f.label }));
      var el;
      if (f.type === 'select') {
        el = h('select', { 'data-fkey': f.key });
        el.appendChild(h('option', { value: '', text: '全部' }));
        var opts = (typeof f.options === 'function' ? f.options() : f.options) || [];
        opts.forEach(function (o) { el.appendChild(h('option', { value: String(o), text: String(o) })); });
        el.addEventListener('change', function () { state.page = 1; load(); });
      } else {
        el = h('input', { type: 'text', 'data-fkey': f.key, placeholder: f.placeholder || '关键字,回车搜索' });
        el.addEventListener('keydown', function (e) {
          if (e.key === 'Enter') { state.page = 1; load(); }
        });
      }
      inline.appendChild(el);
      toolbar.appendChild(inline);
    });
    if (cfg.filters && cfg.filters.length) {
      toolbar.appendChild(h('button', {
        class: 'btn btn-ghost', text: '查询',
        onclick: function () { state.page = 1; load(); }
      }));
    }
    toolbar.appendChild(h('div', { class: 'spacer' }));
    if (cfg.toolbar) {
      cfg.toolbar(toolbar, {
        load: load,
        openNew: function () { openForm(null); },
        getFilters: function () {
          var f = collectFilters();
          Object.keys(f).forEach(function (k) { if (f[k] === '') delete f[k]; });
          return f;
        }
      });
    }

    var table = h('table', { class: 'grid' });
    table.innerHTML = '<thead><tr>' +
      cfg.columns.map(function (c) { return '<th>' + esc(c.label) + '</th>'; }).join('') +
      '<th style="width:110px">操作</th></tr></thead><tbody></tbody>';
    table.addEventListener('click', function (e) {
      var btnEl = e.target.closest('button[data-act]');
      if (!btnEl) return;
      var id = btnEl.getAttribute('data-id');
      var row = state.rows.filter(function (r) { return String(r.id) === String(id); })[0];
      if (!row) return;
      if (btnEl.getAttribute('data-act') === 'edit') {
        openForm(row);
      } else {
        confirmBox(cfg.delTip ? cfg.delTip(row) : '确定删除这条记录吗?删除后不可恢复。', true)
          .then(function (yes) {
            if (!yes) return;
            DEL(cfg.endpoint + '/' + id).then(function () {
              toast('已删除');
              if (state.rows.length === 1 && state.page > 1) state.page--;
              afterChange();
            }).catch(function (err) { toast(err.message, 'err'); });
          });
      }
    });

    var panel = h('div', { class: 'panel' }, h('div', { class: 'table-wrap' }, table), pagerBox);
    root.appendChild(h('div', { class: 'panel' }, toolbar));
    root.appendChild(h('div', { style: 'height:12px' }));
    root.appendChild(panel);
    container.appendChild(root);
    load();
  }

  return { render: render };
}

/* ================= 各模块配置 ================= */

function statusOptions() {
  var l = dictLabels('personnel_status');
  return l.length ? l : ['在职', '休假', '培训', '借调', '离职', '其他'];
}

var personnelCfg = {
  key: 'personnel', title: '人员管理', icon: '👥',
  endpoint: '/api/personnel', wide: true,
  filters: [
    { key: 'keyword', label: '检索' },
    { key: 'status', label: '状态', type: 'select', options: statusOptions }
  ],
  columns: [
    {
      label: '姓名', render: function (r) {
        if (r.photo) {
          return '<div class="cell-person"><img class="avatar" data-pid="' + r.id + '" alt=""/><b>' + esc(r.name) + '</b></div>';
        }
        return '<div class="cell-person"><span class="avatar missing">' + esc((r.name || '?').charAt(0)) + '</span><b>' + esc(r.name) + '</b></div>';
      }
    },
    { key: 'gender', label: '性别' },
    { key: 'idCard', label: '身份证号' },
    { key: 'departmentName', label: '部门' },
    { key: 'rank', label: '军衔/职级' },
    { key: 'post', label: '职务' },
    { key: 'status', label: '状态', render: function (r) { return badge(r.status); } },
    { key: 'phone', label: '手机号' },
    { key: 'entryDate', label: '入伍日期' }
  ],
  rowTitle: function (r) { return r.name; },
  delTip: function (r) { return '确定删除人员「' + r.name + '」吗?相关休假、体能、装备保管记录将一并解除关联,删除后不可恢复。'; },
  afterTable: function (rootEl) { loadRowPhotos(rootEl); },
  formTop: personnelPhotoBlock,
  toolbar: function (bar, act) {
    var fileInput = h('input', { type: 'file', accept: '.xlsx', style: 'display:none' });
    fileInput.addEventListener('change', function () {
      var f = fileInput.files[0];
      fileInput.value = '';
      if (!f) return;
      var fd = new FormData();
      fd.append('file', f);
      toast('正在导入…', 'info');
      POST('/api/personnel/import-excel', fd).then(function (r) {
        toast('导入完成:新增 ' + r.added + ' 条,更新 ' + r.updated + ' 条,失败 ' + r.failed + ' 条', r.failed ? 'info' : 'ok');
        act.load();
      }).catch(function (e) { toast(e.message, 'err'); });
    });
    bar.appendChild(fileInput);
    bar.appendChild(h('button', { class: 'btn btn-ghost', text: '📥 导入Excel', onclick: function () { fileInput.click(); } }));
    bar.appendChild(h('button', {
      class: 'btn btn-ghost', text: '📤 导出Excel',
      onclick: function () {
        var q = act.getFilters();
        var qs = Object.keys(q).map(function (k) { return k + '=' + encodeURIComponent(q[k]); }).join('&');
        apiBlob('/api/personnel/export' + (qs ? '?' + qs : '')).then(function (r) {
          downloadBlob(r.blob, r.filename);
        }).catch(function (e) { toast(e.message, 'err'); });
      }
    }));
    bar.appendChild(h('button', { class: 'btn btn-primary', text: '＋ 新增人员', onclick: function () { act.openNew(); } }));
  },
  form: [
    { section: '基本信息' },
    { key: 'name', label: '姓名', required: true },
    { key: 'gender', label: '性别', type: 'select', options: ['男', '女'] },
    { key: 'idCard', label: '身份证号' },
    { key: 'birthday', label: '出生日期', type: 'date' },
    { key: 'phone', label: '手机号' },
    { key: 'departmentId', label: '部门', type: 'dept' },
    { key: 'rank', label: '军衔/职级', datalist: function () { return dictLabels('rank'); } },
    { key: 'post', label: '职务/岗位' },
    { key: 'status', label: '状态', type: 'select', required: true, options: statusOptions },
    { key: 'entryDate', label: '入伍/入职日期', type: 'date' },
    { section: '更多资料' },
    { key: 'nation', label: '民族' },
    { key: 'nativePlace', label: '籍贯' },
    { key: 'bloodType', label: '血型' },
    { key: 'politicalStatus', label: '政治面貌', datalist: ['党员', '团员', '群众'] },
    { key: 'partyJoinDate', label: '入党/入团日期', type: 'date' },
    { key: 'maritalStatus', label: '婚姻状况', type: 'select', options: ['未婚', '已婚', '离异', '丧偶'] },
    { key: 'education', label: '学历', type: 'select', options: ['高中', '中专', '大专', '本科', '硕士', '博士'] },
    { key: 'onlyChild', label: '独生子女', type: 'select', options: ['是', '否'] },
    { key: 'height', label: '身高(cm)', type: 'number', step: '0.1' },
    { key: 'weight', label: '体重(kg)', type: 'number', step: '0.1' },
    { key: 'address', label: '家庭住址', span: 'full' },
    { section: '家庭信息(配偶)' },
    { key: 'spouseName', label: '配偶姓名' },
    { key: 'spousePhone', label: '配偶电话' },
    { key: 'spouseJob', label: '配偶职业' },
    { key: 'spouseWork', label: '配偶工作单位' },
    { key: 'spouseIdCard', label: '配偶身份证号' },
    { key: 'marriageDate', label: '结婚日期', type: 'date' },
    { key: 'remark', label: '备注', type: 'textarea', span: 'full' }
  ]
};

/* 人员表单顶部的照片选择区 */
function personnelPhotoBlock(row) {
  var st = {
    base64: null,
    removed: false,
    existing: row && row.photo ? ('/api/personnel/' + row.id + '/photo') : null
  };
  var preview = h('div', { class: 'photo-preview', text: st.existing ? '' : '无照片' });
  if (st.existing) {
    apiBlob(st.existing).then(function (r) {
      preview.style.backgroundImage = 'url(' + URL.createObjectURL(r.blob) + ')';
      preview.textContent = '';
    }).catch(function () { preview.textContent = '无照片'; });
  }

  var fileInput = h('input', { type: 'file', accept: 'image/*', style: 'display:none' });
  fileInput.addEventListener('change', function () {
    var f = fileInput.files[0];
    fileInput.value = '';
    if (!f) return;
    if (f.size > 5 * 1024 * 1024) { toast('照片不能超过 5MB', 'err'); return; }
    var reader = new FileReader();
    reader.onload = function () {
      st.base64 = reader.result;
      st.removed = false;
      preview.style.backgroundImage = 'url(' + st.base64 + ')';
      preview.textContent = '';
    };
    reader.readAsDataURL(f);
  });

  var box = h('div', { class: 'form-item full' });
  box.appendChild(h('label', { text: '人员照片' }));
  box.appendChild(h('div', { class: 'photo-box' }, preview,
    h('div', {}, fileInput,
      h('div', { style: 'display:flex;flex-direction:column;gap:8px' },
        h('button', { class: 'btn btn-ghost btn-sm', text: '选择照片', onclick: function () { fileInput.click(); } }),
        h('button', {
          class: 'btn btn-ghost btn-sm', text: '移除照片',
          onclick: function () {
            st.base64 = null; st.removed = true; st.existing = null;
            preview.style.backgroundImage = '';
            preview.textContent = '无照片';
          }
        }),
        h('div', { style: 'color:#a6b3c4;font-size:12px', text: '支持 jpg/png,不超过 5MB' })))));

  return {
    el: box,
    get: function () {
      if (st.base64) return { photoBase64: st.base64 };
      if (st.removed) return { photo: '' };
      return {};
    }
  };
}

var equipmentCfg = {
  key: 'equipment', title: '装备管理', icon: '🎖', endpoint: '/api/equipment',
  filters: [
    { key: 'keyword', label: '检索' },
    { key: 'status', label: '状态', type: 'select', options: function () { return dictLabels('equipment_status'); } }
  ],
  columns: [
    { key: 'name', label: '装备名称' },
    { key: 'code', label: '编号' },
    { key: 'category', label: '类别' },
    { key: 'model', label: '型号' },
    { key: 'status', label: '状态', render: function (r) { return badge(r.status); } },
    { key: 'keeperName', label: '保管人' },
    { key: 'location', label: '存放位置' },
    { key: 'buyDate', label: '购置日期' }
  ],
  rowTitle: function (r) { return r.name; },
  form: [
    { key: 'name', label: '装备名称', required: true },
    { key: 'code', label: '装备编号' },
    { key: 'category', label: '类别', datalist: function () { return dictLabels('equipment_category'); } },
    { key: 'model', label: '型号' },
    { key: 'status', label: '状态', type: 'select', required: true, options: function () { return dictLabels('equipment_status'); } },
    { key: 'keeperId', label: '保管人', type: 'personnel' },
    { key: 'location', label: '存放位置' },
    { key: 'buyDate', label: '购置日期', type: 'date' },
    { key: 'remark', label: '备注', type: 'textarea', span: 'full' }
  ]
};

var materialCfg = {
  key: 'material', title: '物资管理', icon: '📦', endpoint: '/api/material',
  filters: [
    { key: 'keyword', label: '检索' },
    { key: 'category', label: '分类', type: 'select', options: function () { return dictLabels('material_category'); } }
  ],
  columns: [
    { key: 'name', label: '物资名称' },
    { key: 'category', label: '分类' },
    { key: 'spec', label: '规格' },
    { key: 'unit', label: '单位' },
    {
      label: '数量', render: function (r) {
        var low = r.minQuantity != null && r.quantity != null && r.quantity <= r.minQuantity;
        return low ? '<span class="warn-num">' + esc(r.quantity) + ' ⚠</span>' : esc(r.quantity);
      }
    },
    { key: 'minQuantity', label: '预警下限' },
    { key: 'keeperName', label: '责任人' },
    { key: 'location', label: '存放位置' }
  ],
  rowTitle: function (r) { return r.name; },
  form: [
    { key: 'name', label: '物资名称', required: true },
    { key: 'category', label: '分类', datalist: function () { return dictLabels('material_category'); } },
    { key: 'spec', label: '规格' },
    { key: 'unit', label: '单位', placeholder: '个/箱/件…' },
    { key: 'quantity', label: '数量', type: 'number', step: '0.01' },
    { key: 'minQuantity', label: '库存预警下限', type: 'number', step: '0.01' },
    { key: 'keeperId', label: '责任人', type: 'personnel' },
    { key: 'location', label: '存放位置' },
    { key: 'remark', label: '备注', type: 'textarea', span: 'full' }
  ]
};

var vehicleCfg = {
  key: 'vehicle', title: '车辆管理', icon: '🚙', endpoint: '/api/vehicle',
  filters: [
    { key: 'keyword', label: '检索' },
    { key: 'status', label: '状态', type: 'select', options: function () { return dictLabels('vehicle_status'); } }
  ],
  columns: [
    { key: 'plateNo', label: '车牌号' },
    { key: 'brand', label: '品牌型号' },
    { key: 'type', label: '车型' },
    { key: 'status', label: '状态', render: function (r) { return badge(r.status); } },
    { key: 'driverName', label: '驾驶员' },
    { key: 'seats', label: '核定人数' },
    { key: 'mileage', label: '里程(km)' }
  ],
  rowTitle: function (r) { return r.plateNo; },
  form: [
    { key: 'plateNo', label: '车牌号', required: true },
    { key: 'brand', label: '品牌型号' },
    { key: 'type', label: '车型', datalist: function () { return dictLabels('vehicle_type'); } },
    { key: 'status', label: '状态', type: 'select', required: true, options: function () { return dictLabels('vehicle_status'); } },
    { key: 'driverId', label: '驾驶员', type: 'personnel' },
    { key: 'seats', label: '核定人数', type: 'number', step: '1' },
    { key: 'mileage', label: '里程(km)', type: 'number', step: '0.1' },
    { key: 'remark', label: '备注', type: 'textarea', span: 'full' }
  ]
};

var leaveCfg = {
  key: 'leave', title: '休假管理', icon: '🗓', endpoint: '/api/leave',
  filters: [{ key: 'keyword', label: '检索' }],
  columns: [
    { key: 'personnelName', label: '姓名' },
    { key: 'leaveType', label: '休假类型', render: function (r) { return badge(r.leaveType); } },
    { key: 'startDate', label: '开始日期' },
    { key: 'endDate', label: '结束日期' },
    { key: 'days', label: '天数' },
    { key: 'reason', label: '事由' }
  ],
  rowTitle: function (r) { return r.personnelName; },
  delTip: function (r) { return '确定删除「' + r.personnelName + '」的这条休假记录吗?'; },
  form: [
    { key: 'personnelId', label: '休假人员', type: 'personnel', required: true },
    { key: 'leaveType', label: '休假类型', type: 'select', required: true, options: function () { return dictLabels('leave_type'); } },
    { key: 'startDate', label: '开始日期', type: 'date', required: true },
    { key: 'endDate', label: '结束日期', type: 'date', required: true },
    { key: 'days', label: '天数', type: 'number', step: '0.5' },
    { key: 'reason', label: '事由', span: 'full' },
    { key: 'remark', label: '备注', type: 'textarea', span: 'full' }
  ]
};

var fitnessCfg = {
  key: 'fitness', title: '体能考核', icon: '💪', endpoint: '/api/fitness',
  filters: [
    { key: 'keyword', label: '检索' },
    { key: 'item', label: '项目', type: 'select', options: function () { return dictLabels('fitness_item'); } }
  ],
  columns: [
    { key: 'personnelName', label: '姓名' },
    { key: 'item', label: '考核项目' },
    { key: 'score', label: '成绩' },
    { key: 'grade', label: '等级', render: function (r) { return badge(r.grade); } },
    { key: 'testDate', label: '考核日期' },
    { key: 'tester', label: '考核人' }
  ],
  rowTitle: function (r) { return r.personnelName + ' · ' + r.item; },
  form: [
    { key: 'personnelId', label: '考核人员', type: 'personnel', required: true },
    { key: 'item', label: '考核项目', datalist: function () { return dictLabels('fitness_item'); }, required: true },
    { key: 'score', label: '成绩', placeholder: '如 12\'30 或 45 次' },
    {
      key: 'grade', label: '评定等级', type: 'select', required: true,
      options: function () { var l = dictLabels('fitness_grade'); return l.length ? l : ['优秀', '良好', '及格', '不及格']; }
    },
    { key: 'testDate', label: '考核日期', type: 'date', required: true },
    { key: 'tester', label: '考核人' },
    { key: 'remark', label: '备注', type: 'textarea', span: 'full' }
  ]
};

/* ================= 部门管理视图(树) ================= */

function departmentView() {
  var root = h('div');

  function nodeRow(node, depth) {
    var hasKids = node.children && node.children.length;
    var expand = hasKids
      ? h('span', { class: 'tw', text: node._open === false ? '▸' : '▾', onclick: function () { node._open = node._open === false; renderTree(); } })
      : h('span', { class: 'tw', text: '·' });
    var row = h('div', { class: 'dept-row' }, expand,
      h('span', { class: 'name', text: node.name }),
      hasKids ? h('span', { class: 'cnt', text: hasKids + ' 个子部门' }) : null,
      h('span', { class: 'acts' },
        h('button', { class: 'btn-link', text: '加子级', onclick: function () { openForm(null, node.id); } }),
        h('button', { class: 'btn-link', text: '编辑', onclick: function () { openForm(node); } }),
        h('button', {
          class: 'btn-link danger', text: '删除',
          onclick: function () {
            confirmBox('确定删除部门「' + node.name + '」吗?若其下有子部门或人员将无法删除。', true).then(function (yes) {
              if (!yes) return;
              DEL('/api/departments/' + node.id).then(function () {
                toast('已删除');
                refreshDepts().then(renderTree);
              }).catch(function (e) { toast(e.message, 'err'); });
            });
          }
        })));
    row.style.paddingLeft = (10 + depth * 22) + 'px';
    return row;
  }

  function renderTree() {
    var box = $('.dept-tree', root);
    box.innerHTML = '';
    if (!Ctx.deptTree.length) {
      box.appendChild(h('div', { class: 'empty-row', text: '暂无部门,点击上方按钮创建' }));
      return;
    }
    (function emit(nodes, depth) {
      nodes.forEach(function (n) {
        box.appendChild(nodeRow(n, depth));
        if (n._open !== false && n.children && n.children.length) emit(n.children, depth + 1);
      });
    })(Ctx.deptTree, 0);
  }

  function openForm(node, parentId) {
    var form = buildForm([
      { key: 'name', label: '部门名称', required: true },
      { key: 'parentId', label: '上级部门', type: 'dept' },
      { key: 'sort', label: '排序号', type: 'number', step: '1', placeholder: '小的排前面' },
      { key: 'remark', label: '备注', span: 'full' }
    ], node || { parentId: parentId || null });
    var m = openModal({
      title: node ? '编辑部门 - ' + node.name : '新增部门',
      body: form.el,
      footer: [
        h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m.close(); } }),
        h('button', {
          class: 'btn btn-primary', text: '保存',
          onclick: function () {
            var data = form.get();
            if (!data) return;
            if (data.parentId === '') data.parentId = null;
            if (data.sort === null) data.sort = 0;
            var done = node ? PUT('/api/departments/' + node.id, data) : POST('/api/departments', data);
            done.then(function () {
              m.close();
              toast('保存成功');
              refreshDepts().then(renderTree);
            }).catch(function (e) { form.setError(e.message); });
          }
        })
      ]
    });
  }

  function render(container) {
    root.appendChild(h('div', { class: 'panel' },
      h('div', { class: 'dept-toolbar' },
        h('button', { class: 'btn btn-primary', text: '＋ 新增部门', onclick: function () { openForm(null, null); } }),
        h('button', {
          class: 'btn btn-ghost', text: '刷新', style: 'margin-left:8px',
          onclick: function () { refreshDepts().then(renderTree); }
        })),
      h('div', { class: 'dept-tree' })));
    renderTree();
    container.appendChild(root);
  }
  return { render: render };
}

/* ================= 数据字典视图 ================= */

function dictView() {
  var root = h('div');
  var currentType = null;

  function typeGroup(t) {
    for (var i = 0; i < Ctx.dictTypes.length; i++) {
      if (Ctx.dictTypes[i].type === t) return Ctx.dictTypes[i];
    }
    return null;
  }

  function renderTypeList() {
    var box = $('.dict-types', root);
    box.innerHTML = '';
    Ctx.dictTypes.forEach(function (g) {
      box.appendChild(h('div', {
        class: 'dict-type-item' + (g.type === currentType ? ' active' : ''),
        onclick: function () { currentType = g.type; renderTypeList(); renderItems(); }
      }, h('span', { text: g.name || g.type }), h('span', { class: 'n', text: String((g.items || []).length) })));
    });
  }

  function renderItems() {
    var main = $('.dict-main', root);
    main.innerHTML = '';
    if (!currentType) {
      main.appendChild(h('div', { class: 'panel' }, h('div', { class: 'empty-row', text: '暂无字典数据' })));
      return;
    }
    var g = typeGroup(currentType) || { items: [] };
    var items = g.items || [];

    var table = h('table', { class: 'grid' });
    table.innerHTML = '<thead><tr><th>选项内容</th><th>排序</th><th>状态</th><th>备注</th><th style="width:110px">操作</th></tr></thead><tbody>' +
      (items.length ? items.map(function (d) {
        return '<tr><td>' + esc(d.dictLabel) + '</td><td>' + esc(d.sort) + '</td>' +
          '<td>' + (d.enabled === false ? '<span class="badge gray">停用</span>' : '<span class="badge green">启用</span>') + '</td>' +
          '<td>' + esc(d.remark) + '</td>' +
          '<td class="actions"><button class="btn-link" data-edit="' + d.id + '">编辑</button>' +
          '<button class="btn-link danger" data-del="' + d.id + '">删除</button></td></tr>';
      }).join('') : '<tr><td colspan="5" class="empty-row">该类型暂无选项</td></tr>') + '</tbody>';
    table.addEventListener('click', function (e) {
      var b = e.target.closest('button');
      if (!b) return;
      var id = b.getAttribute('data-edit') || b.getAttribute('data-del');
      var item = items.filter(function (x) { return String(x.id) === String(id); })[0];
      if (!item) return;
      if (b.hasAttribute('data-del')) {
        confirmBox('确定删除选项「' + item.dictLabel + '」吗?已有数据中的该值将保留,但不再出现在下拉选项中。', true)
          .then(function (yes) {
            if (!yes) return;
            DEL('/api/dict/' + id).then(function () {
              toast('已删除');
              refreshDicts().then(renderAll);
            }).catch(function (err) { toast(err.message, 'err'); });
          });
      } else {
        openForm(item);
      }
    });

    main.appendChild(h('div', { class: 'panel' },
      h('div', { class: 'toolbar' },
        h('strong', { text: (g.name || currentType) + '(' + currentType + ')' }),
        h('div', { class: 'spacer' }),
        h('button', { class: 'btn btn-primary', text: '＋ 新增选项', onclick: function () { openForm(null); } })),
      h('div', { class: 'table-wrap' }, table)));
  }

  function openForm(item) {
    var form = buildForm([
      { key: 'dictLabel', label: '选项内容', required: true },
      { key: 'sort', label: '排序号', type: 'number', step: '1' },
      { key: 'enabled', label: '状态', type: 'select', required: true, options: [{ v: '1', l: '启用' }, { v: '0', l: '停用' }] },
      { key: 'remark', label: '备注', span: 'full' }
    ], item
      ? { dictLabel: item.dictLabel, sort: item.sort, enabled: item.enabled === false ? '0' : '1', remark: item.remark }
      : { enabled: '1', sort: 0 });
    var m = openModal({
      title: item ? '编辑选项 - ' + item.dictLabel : '新增选项 · ' + (typeGroup(currentType) || {}).name,
      body: form.el,
      footer: [
        h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m.close(); } }),
        h('button', {
          class: 'btn btn-primary', text: '保存',
          onclick: function () {
            var data = form.get();
            if (!data) return;
            data.dictType = currentType;
            data.enabled = data.enabled !== '0';
            if (data.sort === null) data.sort = 0;
            var done = item ? PUT('/api/dict/' + item.id, data) : POST('/api/dict', data);
            done.then(function () {
              m.close();
              toast('保存成功');
              refreshDicts().then(renderAll);
            }).catch(function (e) { form.setError(e.message); });
          }
        })
      ]
    });
  }

  function renderAll() {
    if (!currentType && Ctx.dictTypes.length) currentType = Ctx.dictTypes[0].type;
    renderTypeList();
    renderItems();
  }

  function render(container) {
    root.appendChild(h('div', { class: 'dict-layout' },
      h('div', { class: 'panel dict-types' }),
      h('div', { class: 'dict-main' })));
    renderAll();
    container.appendChild(root);
  }
  return { render: render };
}

/* ================= 统计视图 ================= */

function statsView() {
  var root = h('div', { class: 'loading-inline', text: '加载中…' });

  function barList(list, colorClass) {
    if (!list.length) return '<div class="empty-row">暂无数据</div>';
    var max = 1;
    list.forEach(function (x) { if (x.count > max) max = x.count; });
    return list.map(function (x) {
      var pct = Math.max(2, Math.round(x.count / max * 100));
      return '<div class="bar-row ' + colorClass + '"><span class="bn" title="' + esc(x.name) + '">' + esc(x.name) +
        '</span><span class="bt"><span class="bf" style="width:' + pct + '%"></span></span>' +
        '<span class="bv">' + x.count + ' 人</span></div>';
    }).join('');
  }

  function render(container) {
    container.appendChild(root);
    GET('/api/stats/summary').then(function (s) {
      var onDuty = 0;
      (s.status || []).forEach(function (x) { if (x.name === '在职') onDuty = x.count; });
      root.className = '';
      root.innerHTML =
        '<div class="stats-cards">' +
        '<div class="stat-card"><span class="ic">👥</span><div class="num">' + (s.total || 0) + '</div><div class="lbl">人员总数</div></div>' +
        '<div class="stat-card"><span class="ic">🏢</span><div class="num">' + ((s.department || []).length) + '</div><div class="lbl">有人员的部门数</div></div>' +
        '<div class="stat-card"><span class="ic">✅</span><div class="num">' + onDuty + '</div><div class="lbl">在职人数</div></div>' +
        '</div>' +
        '<div class="chart-panel"><h3>各部门人数</h3>' + barList(s.department || [], '') + '</div>' +
        '<div class="chart-panel"><h3>性别分布</h3>' + barList(s.gender || [], 'green') + '</div>' +
        '<div class="chart-panel"><h3>人员状态分布</h3>' + barList(s.status || [], 'orange') + '</div>';
    }).catch(function (e) {
      root.className = '';
      root.innerHTML = '<div class="empty-row">' + esc(e.message) + '</div>';
    });
  }
  return { render: render };
}

/* ================= 登录页 ================= */

function renderLogin(msg) {
  var app = $('#app');
  app.innerHTML = '';
  if (msg) toast(msg, 'err');

  var errBox = h('div', { class: 'login-error' });
  var userInput = h('input', { type: 'text', placeholder: '请输入管理员账号', autocomplete: 'username' });
  var pwInput = h('input', { type: 'password', placeholder: '请输入密码', autocomplete: 'current-password' });
  var btn = h('button', { class: 'btn btn-primary login-btn', text: '登  录' });

  function submit() {
    var u = userInput.value.trim();
    var p = pwInput.value;
    if (!u || !p) { errBox.textContent = '请输入账号和密码'; return; }
    btn.disabled = true; btn.textContent = '登录中…';
    errBox.textContent = '';
    api('POST', '/api/auth/login', { username: u, password: p }).then(function (data) {
      saveSession(data.token, { username: data.username, displayName: data.displayName });
      return refreshAll().then(renderShell);
    }).catch(function (e) {
      btn.disabled = false; btn.textContent = '登  录';
      errBox.textContent = e.message;
    });
  }
  btn.addEventListener('click', submit);
  pwInput.addEventListener('keydown', function (e) { if (e.key === 'Enter') submit(); });
  userInput.addEventListener('keydown', function (e) { if (e.key === 'Enter') pwInput.focus(); });

  app.appendChild(h('div', { class: 'login-wrap' },
    h('div', { class: 'login-card' },
      h('div', { class: 'login-emblem', text: '★' }),
      h('div', { class: 'login-title', text: '数智军营人员管理系统' }),
      h('div', { class: 'login-sub', text: '内网离线运行 · 数据本机存储' }),
      h('div', { class: 'field' }, h('label', { text: '账号' }), userInput),
      h('div', { class: 'field' }, h('label', { text: '密码' }), pwInput),
      errBox,
      btn,
      h('div', { class: 'login-tip', text: '忘记密码请联系本机管理员(托盘菜单可重置)' }))));
  userInput.focus();
}

/* ================= 修改密码 ================= */

function openChangePassword() {
  var form = buildForm([
    { key: 'oldPassword', label: '旧密码', required: true },
    { key: 'newPassword', label: '新密码(至少6位)', required: true },
    { key: 'confirm', label: '确认新密码', required: true }
  ], {});
  var m = openModal({
    title: '修改密码',
    body: form.el,
    footer: [
      h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m.close(); } }),
      h('button', {
        class: 'btn btn-primary', text: '确定修改',
        onclick: function () {
          var data = form.get();
          if (!data) return;
          if (data.newPassword !== data.confirm) { form.setError('两次输入的新密码不一致'); return; }
          POST('/api/auth/change-password', { oldPassword: data.oldPassword, newPassword: data.newPassword })
            .then(function () { m.close(); toast('密码修改成功,下次登录请使用新密码'); })
            .catch(function (e) { form.setError(e.message); });
        }
      })
    ]
  });
}

/* ================= 主框架 ================= */

var MODULES = [personnelCfg, equipmentCfg, materialCfg, vehicleCfg, leaveCfg, fitnessCfg];

function renderShell() {
  var app = $('#app');
  app.innerHTML = '';

  var content = h('div', { class: 'content' });
  var titleEl = h('div', { class: 'topbar-title' });
  var menuItems = {};

  function navigate(view) {
    Object.keys(menuItems).forEach(function (k) { menuItems[k].classList.remove('active'); });
    if (menuItems[view]) menuItems[view].classList.add('active');

    content.innerHTML = '';
    var comp;
    if (view === 'department') { titleEl.textContent = '部门管理'; comp = departmentView(); }
    else if (view === 'dict') { titleEl.textContent = '数据字典'; comp = dictView(); }
    else if (view === 'stats') { titleEl.textContent = '统计报表'; comp = statsView(); }
    else {
      var cfg = null;
      MODULES.forEach(function (m) { if (m.key === view) cfg = m; });
      if (!cfg) { cfg = personnelCfg; view = 'personnel'; }
      titleEl.textContent = cfg.title;
      comp = crudView(cfg);
    }
    comp.render(content);
  }

  var menu = h('div', { class: 'menu' });
  function addMenu(key, icon, label) {
    var item = h('div', { class: 'menu-item', onclick: function () { navigate(key); } },
      h('span', { class: 'ic', text: icon }), h('span', { class: 'txt', text: label }));
    menu.appendChild(item);
    menuItems[key] = item;
  }
  MODULES.forEach(function (m) { addMenu(m.key, m.icon, m.title); });
  addMenu('department', '🏢', '部门管理');
  addMenu('dict', '📚', '数据字典');
  addMenu('stats', '📊', '统计报表');

  var who = (USER && (USER.displayName || USER.username)) || '管理员';
  var topbar = h('div', { class: 'topbar' },
    titleEl,
    h('div', { class: 'topbar-user' },
      h('span', { class: 'who', text: who }),
      h('button', { class: 'btn btn-ghost btn-sm', text: '修改密码', onclick: openChangePassword }),
      h('button', {
        class: 'btn btn-ghost btn-sm', text: '退出',
        onclick: function () {
          POST('/api/auth/logout').catch(function () { }).then(function () {
            forceLogin('已退出登录');
          });
        }
      })));

  app.appendChild(h('div', { class: 'layout' },
    h('div', { class: 'sidebar' },
      h('div', { class: 'sidebar-logo' },
        h('span', { class: 'star', text: '★' }),
        h('div', {},
          h('div', { class: 't1', text: '数智军营' }),
          h('div', { class: 't2', text: '人员管理系统' }))),
      menu,
      h('div', { class: 'sidebar-foot', text: '内网离线运行' })),
    h('div', { class: 'main' }, topbar, content)));

  navigate('personnel');
}

/* ================= 启动 ================= */

(function boot() {
  if (!TOKEN) { renderLogin(); return; }
  refreshAll().then(renderShell).catch(function (e) {
    if (!TOKEN) return;          // 401 已跳转登录
    toast(e.message, 'err');
    renderShell();
  });
})();
