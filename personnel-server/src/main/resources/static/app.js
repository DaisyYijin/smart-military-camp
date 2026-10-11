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

/* ================= 扁平线性图标 ================= */

var ICON_PATHS = {
  users: '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
  shield: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>',
  box: '<path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/>',
  truck: '<rect x="1" y="3" width="15" height="13" rx="1"/><polygon points="16 8 20 8 23 11 23 16 16 16 16 8"/><circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/>',
  calendar: '<rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/>',
  activity: '<polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>',
  building: '<path d="M3 21h18"/><path d="M5 21V7l7-4 7 4v14"/><line x1="9" y1="10" x2="9" y2="13"/><line x1="15" y1="10" x2="15" y2="13"/><line x1="10" y1="14" x2="14" y2="14"/><line x1="9" y1="17" x2="9" y2="17.01"/><line x1="15" y1="17" x2="15" y2="17.01"/>',
  book: '<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>',
  database: '<ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/>',
  chart: '<line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/>',
  check: '<path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/>',
  fileText: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/>',
  chevronL: '<polyline points="15 18 9 12 15 6"/>',
  chevronR: '<polyline points="9 18 15 12 9 6"/>'
};

/**
 * 自绘日期组件:文本输入 + 页面内日历面板(不用浏览器原生弹窗,杜绝核显卡顿)。
 * 返回容器,.value 为规范的 yyyy-MM-dd(空串表示未选)。
 */
function makeDateField() {
  function p2(n) { return n < 10 ? '0' + n : '' + n; }
  function fmt(d) { return d.getFullYear() + '-' + p2(d.getMonth() + 1) + '-' + p2(d.getDate()); }
  function parse(s) {
    var t = String(s == null ? '' : s).trim();
    var m = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(t) || /^(\d{4})(\d{2})(\d{2})$/.exec(t);
    if (!m) {
      return null;
    }
    var d = new Date(+m[1], +m[2] - 1, +m[3]);
    if (d.getFullYear() !== +m[1] || d.getMonth() !== +m[2] - 1 || d.getDate() !== +m[3]) {
      return null;
    }
    return d;
  }

  var input = h('input', { type: 'text', placeholder: '年/月/日', autocomplete: 'off' });
  var iconBtn = h('button', {
    type: 'button', class: 'cal-icon', html: iconSvg('calendar'), title: '选择日期'
  });
  var wrap = h('div', { class: 'cal' }, input, iconBtn);
  var panel = null;
  var renderPanel = null;
  var view = { y: 0, m: 0 };

  function commit(text) {
    var d = parse(text);
    if (d) {
      input.value = fmt(d);
      input.classList.remove('bad');
    } else if (String(text).trim() === '') {
      input.value = '';
      input.classList.remove('bad');
    } else {
      input.classList.add('bad');
    }
    wrap.dispatchEvent(new CustomEvent('change'));
  }

  Object.defineProperty(wrap, 'value', {
    get: function () {
      var d = parse(input.value);
      return d ? fmt(d) : '';
    },
    set: function (v) {
      var d = parse(v);
      input.value = d ? fmt(d) : '';
      input.classList.remove('bad');
    }
  });

  function ensurePanel() {
    if (panel) {
      return;
    }
    var title = h('div', { class: 'cal-title' });
    var prevBtn = h('button', { type: 'button', class: 'cal-nav', text: '‹' });
    var nextBtn = h('button', { type: 'button', class: 'cal-nav', text: '›' });
    var grid = h('div', { class: 'cal-grid' });
    var todayBtn = h('button', { type: 'button', class: 'cal-foot-btn', text: '今天' });
    var clearBtn = h('button', { type: 'button', class: 'cal-foot-btn cal-clear', text: '清空' });
    panel = h('div', { class: 'cal-panel' },
      h('div', { class: 'cal-head' }, prevBtn, title, nextBtn),
      h('div', { class: 'cal-week' },
        h('span', { text: '一' }), h('span', { text: '二' }), h('span', { text: '三' }),
        h('span', { text: '四' }), h('span', { text: '五' }), h('span', { text: '六' }),
        h('span', { text: '日' })),
      grid,
      h('div', { class: 'cal-foot' }, todayBtn, clearBtn));
    wrap.appendChild(panel);

    renderPanel = function () {
      title.textContent = view.y + ' 年 ' + (view.m + 1) + ' 月';
      grid.innerHTML = '';
      var offset = (new Date(view.y, view.m, 1).getDay() + 6) % 7;
      var days = new Date(view.y, view.m + 1, 0).getDate();
      var today = new Date();
      var sel = wrap.value;
      var i;
      for (i = 0; i < offset; i++) {
        grid.appendChild(h('span', { class: 'cal-day blank' }));
      }
      for (var d = 1; d <= days; d++) {
        (function (dd) {
          var iso = view.y + '-' + p2(view.m + 1) + '-' + p2(dd);
          var cls = 'cal-day';
          if (iso === sel) {
            cls += ' sel';
          }
          if (view.y === today.getFullYear() && view.m === today.getMonth() && dd === today.getDate()) {
            cls += ' today';
          }
          grid.appendChild(h('span', {
            class: cls, text: String(dd),
            onclick: function () {
              input.value = iso;
              input.classList.remove('bad');
              wrap.classList.remove('open');
              wrap.dispatchEvent(new CustomEvent('change'));
            }
          }));
        })(d);
      }
    };
    prevBtn.addEventListener('click', function () {
      view.m--; if (view.m < 0) { view.m = 11; view.y--; } renderPanel();
    });
    nextBtn.addEventListener('click', function () {
      view.m++; if (view.m > 11) { view.m = 0; view.y++; } renderPanel();
    });
    todayBtn.addEventListener('click', function () {
      input.value = fmt(new Date());
      wrap.classList.remove('open');
      wrap.dispatchEvent(new CustomEvent('change'));
    });
    clearBtn.addEventListener('click', function () {
      input.value = '';
      wrap.classList.remove('open');
      wrap.dispatchEvent(new CustomEvent('change'));
    });
    panel.addEventListener('click', function (e) { e.stopPropagation(); });
  }

  function openPanel() {
    ensurePanel();
    var base = parse(input.value);
    var t = base || new Date();
    view.y = t.getFullYear();
    view.m = t.getMonth();
    renderPanel();
    // 视口定位:absolute 在弹窗滚动区域内会被边界裁剪,fixed 不会
    var r = input.getBoundingClientRect();
    wrap.classList.add('open');
    var ph = panel.offsetHeight || 330;
    var below = r.bottom + 6 + ph <= window.innerHeight;
    panel.style.position = 'fixed';
    panel.style.width = '262px';
    panel.style.left = Math.min(r.left, window.innerWidth - 274) + 'px';
    panel.style.top = (below ? r.bottom + 6 : Math.max(6, r.top - ph - 6)) + 'px';
  }

  iconBtn.addEventListener('click', function (e) {
    e.stopPropagation();
    if (wrap.classList.contains('open')) {
      wrap.classList.remove('open');
    } else {
      openPanel();
    }
  });
  input.addEventListener('click', function (e) {
    e.stopPropagation();
    if (!wrap.classList.contains('open')) {
      openPanel();
    }
  });
  input.addEventListener('keydown', function (e) {
    if (e.key === 'Enter') {
      commit(input.value);
      wrap.classList.remove('open');
      e.preventDefault();
    }
  });
  input.addEventListener('blur', function () {
    commit(input.value);
  });

  return wrap;
}

/**
 * 自绘下拉组件:返回一个"按钮即输入控件"(可读写 .value、可监听 change),
 * 列表为页面内 DOM,不创建浏览器弹窗表面(避免核显环境空白卡顿)。
 */
function makeDropdown(opts, placeholder) {
  var items = (opts || []).map(function (o) {
    if (o && o.v !== undefined) {
      return { v: String(o.v), t: String(o.l !== undefined ? o.l : o.v) };
    }
    return { v: String(o), t: String(o) };
  });
  var ph = placeholder || '请选择';
  // 调用方已带空值项(如"全部")时不再重复追加占位
  if (!items.length || items[0].v !== '') {
    items.unshift({ v: '', t: ph });
  }

  var cur = '';
  var txt = h('span', { class: 'txt ph', text: ph });
  var arrow = h('span', { class: 'dd-arrow', text: '▾' });
  var btn = h('button', { type: 'button', class: 'dd-btn' }, txt, arrow);
  var list = h('div', { class: 'dd-list' });

  var wrap = h('div', { class: 'dd' }, btn, list);

  items.forEach(function (it) {
    list.appendChild(h('div', {
      class: 'dd-item', text: it.t, 'data-v': it.v,
      onclick: (function (v) {
        return function () {
          wrap.value = v;
          wrap.classList.remove('open');
          wrap.dispatchEvent(new CustomEvent('change'));
        };
      })(it.v)
    }));
  });

  function refresh() {
    var found = null;
    for (var i = 0; i < items.length; i++) {
      if (items[i].v === cur) { found = items[i]; break; }
    }
    txt.textContent = found ? found.t : ph;
    txt.classList.toggle('ph', cur === '');
    $all('.dd-item', list).forEach(function (el2) {
      el2.classList.toggle('cur', el2.getAttribute('data-v') === cur);
    });
  }

  // value 定义在容器上:容器被插入布局时按钮与列表保持一体
  Object.defineProperty(wrap, 'value', {
    get: function () { return cur; },
    set: function (v) { cur = v == null ? '' : String(v); refresh(); }
  });

  btn.addEventListener('click', function (e) {
    e.stopPropagation();
    var opening = !wrap.classList.contains('open');
    $all('.dd.open, .cal.open').forEach(function (d) { if (d !== wrap) d.classList.remove('open'); });
    wrap.classList.toggle('open', opening);
    if (!opening) {
      return;
    }
    // 视口定位:避免被弹窗滚动区域裁剪
    var r = btn.getBoundingClientRect();
    list.style.position = 'fixed';
    list.style.left = r.left + 'px';
    list.style.width = r.width + 'px';
    var lh = list.offsetHeight || 200;
    var below = r.bottom + 6 + lh <= window.innerHeight;
    list.style.top = (below ? r.bottom + 5 : Math.max(6, r.top - lh - 5)) + 'px';
  });
  list.addEventListener('click', function (e) { e.stopPropagation(); });

  return wrap;
}

// 全局:点空白/按 Esc 关闭所有下拉(一次注册,避免泄漏)
document.addEventListener('click', function () {
  $all('.dd.open, .cal.open').forEach(function (d) { d.classList.remove('open'); });
});
document.addEventListener('scroll', function (e) {
  // 下拉列表/日历面板自身的滚动不关闭;只有页面或弹窗滚动才关闭(避免面板错位)
  var t = e.target;
  if (t && t.closest && t.closest('.dd.open, .cal.open')) {
    return;
  }
  $all('.dd.open, .cal.open').forEach(function (d) { d.classList.remove('open'); });
}, true);
document.addEventListener('keydown', function (e) {
  if (e.key === 'Escape') {
    $all('.dd.open, .cal.open').forEach(function (d) { d.classList.remove('open'); });
  }
});

/** 生成扁平线性 SVG 图标(继承文字颜色) */
function iconSvg(key) {
  return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" '
    + 'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'
    + (ICON_PATHS[key] || '') + '</svg>';
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

var redirectingToLogin = false;

function forceLogin(msg) {
  // 并发的多个 401(如重启后页面启动时的字典/部门/选项三个请求)
  // 只弹一次提示、只渲染一次登录页
  if (redirectingToLogin) {
    return;
  }
  redirectingToLogin = true;
  if ($('.login-wrap')) { if (msg) toast(msg, 'err'); return; }
  clearSession();
  renderLogin(msg);
  setTimeout(function () { redirectingToLogin = false; }, 600);
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
  var inputs = {};
  var sectionOf = {};
  var datalistSeq = 0;

  // 按 section 分组;两组及以上渲染为标签页(切页仅隐藏面板,已填数据不丢失)
  var groups = [];
  fields.forEach(function (f) {
    if (f.section) {
      groups.push({ title: f.section, fields: [] });
    } else {
      if (!groups.length) {
        groups.push({ title: '', fields: [] });
      }
      groups[groups.length - 1].fields.push(f);
    }
  });
  var useTabs = groups.length >= 2;

  var tabsBar = h('div', { class: 'tabs' });
  var panels = [];
  var tabEls = [];

  function activateTab(idx) {
    tabEls.forEach(function (t, i) { t.classList.toggle('active', i === idx); });
    panels.forEach(function (p, i) { p.classList.toggle('active', i === idx); });
  }

  function addField(panel, f) {
    var wrap = h('div', { class: 'form-item' + (f.span === 'full' ? ' full' : '') });
    wrap.appendChild(h('label', { html: esc(f.label) + (f.required ? '<span class="req">*</span>' : '') }));

    var val = values[f.key];
    var el;
    if (f.type === 'select' || f.type === 'personnel' || f.type === 'dept') {
      var ddOpts;
      if (f.type === 'select') {
        ddOpts = (typeof f.options === 'function' ? f.options() : f.options) || [];
      } else if (f.type === 'personnel') {
        ddOpts = Ctx.personnelOptions.map(function (p) {
          return { v: String(p.id), l: p.name + (p.departmentName ? ' (' + p.departmentName + ')' : '') };
        });
      } else {
        ddOpts = Ctx.deptFlat.map(function (d) {
          return { v: String(d.id), l: new Array(d.depth + 1).join('　') + d.name };
        });
      }
      el = makeDropdown(ddOpts, f.type === 'dept' && !f.required ? '(不指定)' : '请选择');
      if (val !== undefined && val !== null && String(val) !== '') el.value = String(val);
    } else if (f.type === 'date') {
      el = makeDateField();
      if (val !== undefined && val !== null && String(val) !== '') el.value = String(val);
    } else if (f.type === 'textarea') {
      el = h('textarea', { placeholder: f.placeholder || '' });
      el.value = val || '';
    } else if (f.datalist) {
      var listId = 'dl' + (++datalistSeq);
      var list = h('datalist', { id: listId });
      var labels = (typeof f.datalist === 'function' ? f.datalist() : f.datalist) || [];
      labels.forEach(function (l) { list.appendChild(h('option', { value: l })); });
      wrap.appendChild(list);
      el = h('input', { type: 'text', list: listId, placeholder: f.placeholder || '' });
      el.value = val || '';
    } else {
      var type = f.type === 'number' ? 'number' : 'text';
      el = h('input', { type: type, placeholder: f.placeholder || '' });
      if (f.step) el.setAttribute('step', f.step);
      if (val !== undefined && val !== null) el.value = val;
    }
    inputs[f.key] = el;
    wrap.appendChild(el);
    panel.appendChild(wrap);
  }

  groups.forEach(function (g, gi) {
    var panel = h('div', { class: 'form-grid tab-panel' });
    if (!useTabs) {
      panel.classList.add('active');
    } else {
      var tab = h('div', {
        class: 'tab' + (gi === 0 ? ' active' : ''),
        text: g.title,
        onclick: (function (i) { return function () { activateTab(i); }; })(gi)
      });
      tabsBar.appendChild(tab);
      tabEls.push(tab);
      if (gi === 0) {
        panel.classList.add('active');
      }
    }
    g.fields.forEach(function (f) {
      addField(panel, f);
      sectionOf[f.key] = gi;
    });
    panels.push(panel);
  });

  var err = h('div', { class: 'form-err' });

  /** 校验失败定位:跳到字段所在标签页,红框 + 滚动 + 聚焦 */
  function markBad(el) {
    el.classList.add('bad');
    var focusEl = el;
    if (el.classList.contains('dd')) {
      focusEl = el.querySelector('.dd-btn');
    } else if (el.classList.contains('cal')) {
      focusEl = el.querySelector('input');
    }
    if (focusEl) {
      var clear = function () {
        el.classList.remove('bad');
        focusEl.removeEventListener('focus', clear);
      };
      focusEl.addEventListener('focus', clear);
      try { focusEl.focus({ preventScroll: true }); } catch (e) { focusEl.focus(); }
    }
    el.scrollIntoView({ block: 'center' });
  }

  var container = h('div', {});
  if (useTabs) {
    container.appendChild(tabsBar);
  }
  panels.forEach(function (p) { container.appendChild(p); });
  container.appendChild(err);

  return {
    el: container,
    setError: function (msg) { err.textContent = msg || ''; },
    get: function () {
      var out = {};
      var missing = [];
      var firstBadKey = null;
      fields.forEach(function (f) {
        if (f.section) {
          return;
        }
        var el = inputs[f.key];
        if (f.type === 'number') {
          out[f.key] = el.value === '' ? null : Number(el.value);
          return;
        }
        var v = String(el.value || '').trim();
        out[f.key] = v;
        if (f.required && v === '') {
          missing.push(f.label);
          if (!firstBadKey) {
            firstBadKey = f.key;
          }
        }
      });
      if (missing.length) {
        if (firstBadKey) {
          if (useTabs) {
            activateTab(sectionOf[firstBadKey]);
          }
          markBad(inputs[firstBadKey]);
        }
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
  var state = { page: 1, size: 20, total: 0, rows: [], filters: {}, loading: false, selected: {} };

  function syncHeaderCheck() {
    var head = $('.cb-all', root);
    if (!head) return;
    var boxes = $all('.cb-row', root);
    var allOn = boxes.length > 0;
    for (var i = 0; i < boxes.length; i++) {
      if (!state.selected[boxes[i].getAttribute('data-id')]) { allOn = false; break; }
    }
    head.checked = allOn;
  }
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
    var cols = cfg.columns.length + 2;
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
        var inner;
        if (c.render) {
          inner = c.render(row);
        } else if (cfg.clickKey && c.key === cfg.clickKey) {
          inner = '<span class="view-detail" data-view-id="' + row.id + '" title="点击查看详情"><b>' + esc(raw) + '</b></span>';
        } else {
          inner = esc(raw);
        }
        return '<td title="' + esc(raw) + '">' + inner + '</td>';
      }).join('');
      return '<tr><td class="cb-cell"><input type="checkbox" class="cb-row" data-id="' + row.id + '"'
        + (state.selected[row.id] ? ' checked' : '') + '></td>' + tds +
        '<td class="actions">' +
        '<button class="btn-link" data-act="edit" data-id="' + row.id + '">编辑</button>' +
        '<button class="btn-link danger" data-act="del" data-id="' + row.id + '">删除</button>' +
        '</td></tr>';
    }).join('');
    syncHeaderCheck();
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
        var fopts = (typeof f.options === 'function' ? f.options() : f.options) || [];
        var ddItems = [{ v: '', l: '全部' }].concat(fopts.map(function (o) {
          return { v: String(o), l: String(o) };
        }));
        el = makeDropdown(ddItems, '全部');
        el.setAttribute('data-fkey', f.key);
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
    var batchBtn = h('button', {
      class: 'btn btn-ghost', text: '删除所选', disabled: true,
      onclick: function () { batchDelete(); }
    });
    toolbar.appendChild(batchBtn);
    function syncBatchBtn() {
      var n = Object.keys(state.selected).length;
      batchBtn.disabled = n === 0;
      batchBtn.textContent = n ? '删除所选(' + n + ')' : '删除所选';
    }
    function batchDelete() {
      var ids = Object.keys(state.selected);
      if (!ids.length) return;
      confirmBox('确定删除选中的 ' + ids.length + ' 条记录吗?删除后不可恢复。', true).then(function (yes) {
        if (!yes) return;
        var okCount = 0, failCount = 0;
        var chain = Promise.resolve();
        ids.forEach(function (id) {
          chain = chain.then(function () {
            return DEL(cfg.endpoint + '/' + id)
              .then(function () { okCount++; })
              .catch(function () { failCount++; });
          });
        });
        chain.then(function () {
          state.selected = {};
          toast('批量删除完成:成功 ' + okCount + ' 条' + (failCount ? ',失败 ' + failCount + ' 条' : ''),
            failCount ? 'info' : 'ok');
          afterChange();
        });
      });
    }
    // 导入:上传 xlsx(存在则更新,不存在则新增)
    var importInput = h('input', { type: 'file', accept: '.xlsx', style: 'display:none' });
    importInput.addEventListener('change', function () {
      var f = importInput.files[0];
      importInput.value = '';
      if (!f) return;
      var fd = new FormData();
      fd.append('file', f);
      toast('正在导入…', 'info');
      POST(cfg.endpoint + '/import-excel', fd).then(function (r) {
        var msg = '导入完成:新增 ' + r.added + ' 条,更新 ' + r.updated + ' 条,失败 ' + r.failed + ' 条';
        if (r.errors && r.errors.length) {
          msg += ';失败明细:' + r.errors.join('、');
        }
        toast(msg, r.failed ? 'info' : 'ok');
        load();
      }).catch(function (e) { toast(e.message, 'err'); });
    });
    toolbar.appendChild(importInput);
    toolbar.appendChild(h('button', {
      class: 'btn btn-ghost', text: '导入 Excel',
      title: '导入 Excel(第一行为列头,建议用导出的文件作模板)',
      onclick: function () { importInput.click(); }
    }));
    // 导出:仅导出勾选的记录;未勾选时提示
    toolbar.appendChild(h('button', {
      class: 'btn btn-ghost', text: '导出 Excel',
      onclick: function () {
        var ids = Object.keys(state.selected);
        if (!ids.length) {
          toast('请先勾选要导出的数据(可点表格左上角全选本页)', 'err');
          return;
        }
        apiBlob(cfg.endpoint + '/export?ids=' + ids.join(',')).then(function (r) {
          downloadBlob(r.blob, r.filename);
        }).catch(function (e) { toast(e.message, 'err'); });
      }
    }));
    toolbar.appendChild(h('button', {
      class: 'btn btn-primary',
      text: '＋ ' + (cfg.addLabel || '新增' + cfg.title.replace(/管理$/, '')),
      onclick: function () { openForm(null); }
    }));

    var table = h('table', { class: 'grid' });
    table.innerHTML = '<thead><tr>' +
      '<th class="cb-cell"><input type="checkbox" class="cb-all" title="全选本页"/></th>' +
      cfg.columns.map(function (c) { return '<th>' + esc(c.label) + '</th>'; }).join('') +
      '<th style="width:110px">操作</th></tr></thead><tbody></tbody>';
    table.addEventListener('change', function (e) {
      var t = e.target;
      if (t.classList.contains('cb-row')) {
        var id = t.getAttribute('data-id');
        if (t.checked) state.selected[id] = true; else delete state.selected[id];
        syncBatchBtn();
        syncHeaderCheck();
      } else if (t.classList.contains('cb-all')) {
        $all('.cb-row', table).forEach(function (b) {
          var bid = b.getAttribute('data-id');
          b.checked = t.checked;
          if (t.checked) state.selected[bid] = true; else delete state.selected[bid];
        });
        syncBatchBtn();
      }
    });
    table.addEventListener('click', function (e) {
      var viewEl = e.target.closest('[data-view-id]');
      if (viewEl) {
        var vid = viewEl.getAttribute('data-view-id');
        var vrow = state.rows.filter(function (r) { return String(r.id) === String(vid); })[0];
        if (vrow) {
          var onView = cfg.onView || function (rw, at) { openDetailModal(cfg, rw, at); };
          onView(vrow, { edit: function () { openForm(vrow); } });
        }
        return;
      }
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
    syncBatchBtn();
    load();
  }

  return { render: render };
}

/* ================= 各模块配置 ================= */

/** 通用详情弹窗:按模块表单定义分栏展示全部字段(人员模块附带照片) */
function openDetailModal(cfg, row, act) {
  var grid = h('div', { class: 'detail-grid' });
  (cfg.form || []).forEach(function (f) {
    if (f.section) {
      grid.appendChild(h('div', { class: 'form-section', text: f.section }));
      return;
    }
    var v = row[f.key];
    grid.appendChild(h('div', { class: 'detail-item' },
      h('div', { class: 'dl', text: f.label }),
      h('div', {
        class: 'dv',
        text: (v === null || v === undefined || v === '') ? '—' : String(v)
      })));
  });

  var body;
  if (cfg.key === 'personnel') {
    var photo = h('div', { class: 'detail-photo', text: '无照片' });
    if (row.photo) {
      apiBlob('/api/personnel/' + row.id + '/photo').then(function (r) {
        photo.style.backgroundImage = 'url(' + URL.createObjectURL(r.blob) + ')';
        photo.textContent = '';
      }).catch(function () { /* 无照片保持占位 */ });
    }
    body = h('div', { class: 'detail-wrap' }, photo, grid);
  } else {
    body = grid;
  }

  var m = openModal({
    title: (cfg.title.replace(/管理$/, '').replace(/考核$/, '考核')) + '详情 - ' + (cfg.rowTitle ? cfg.rowTitle(row) : row.id),
    body: body,
    wide: true,
    footer: [
      h('button', { class: 'btn btn-ghost', text: '关闭', onclick: function () { m.close(); } }),
      h('button', {
        class: 'btn btn-primary', text: '编辑',
        onclick: function () { m.close(); if (act && act.edit) act.edit(); }
      })
    ]
  });
}

function statusOptions() {
  var l = dictLabels('personnel_status');
  return l.length ? l : ['在职', '休假', '培训', '借调', '离职', '其他'];
}

var personnelCfg = {
  key: 'personnel', title: '人员管理', icon: 'users',
  endpoint: '/api/personnel', wide: true,
  filters: [
    { key: 'keyword', label: '检索' },
    { key: 'status', label: '状态', type: 'select', options: statusOptions }
  ],
  columns: [
    {
      label: '姓名', render: function (r) {
        if (r.photo) {
          return '<div class="cell-person view-detail" data-view-id="' + r.id + '" title="点击查看详情"><img class="avatar" data-pid="' + r.id + '" alt=""/><b>' + esc(r.name) + '</b></div>';
        }
        return '<div class="cell-person view-detail" data-view-id="' + r.id + '" title="点击查看详情"><span class="avatar missing">' + esc((r.name || '?').charAt(0)) + '</span><b>' + esc(r.name) + '</b></div>';
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
  onView: function (row, act) { openDetailModal(personnelCfg, row, act); },
  delTip: function (r) { return '确定删除人员「' + r.name + '」吗?相关休假、体能、装备保管记录将一并解除关联,删除后不可恢复。'; },
  afterTable: function (rootEl) { loadRowPhotos(rootEl); },
  formTop: personnelPhotoBlock,
  addLabel: '新增人员',
  form: [
    { section: '基本信息' },
    { key: 'name', label: '姓名', required: true },
    { key: 'gender', label: '性别', type: 'select', options: ['男', '女'] },
    { key: 'idCard', label: '身份证号' },
    { key: 'birthday', label: '出生日期', type: 'date' },
    { key: 'phone', label: '手机号' },
    { key: 'departmentId', label: '部门', type: 'dept' },
    { key: 'rank', label: '军衔/职级', type: 'select', options: function () { return dictLabels('rank'); } },
    { key: 'post', label: '职务/岗位' },
    { key: 'status', label: '状态', type: 'select', required: true, options: statusOptions },
    { key: 'entryDate', label: '入伍/入职日期', type: 'date' },
    { section: '更多资料' },
    { key: 'nation', label: '民族', type: 'select', options: function () { return dictLabels('nation'); } },
    { key: 'nativePlace', label: '籍贯' },
    { key: 'bloodType', label: '血型', type: 'select', options: function () { return dictLabels('blood_type'); } },
    { key: 'politicalStatus', label: '政治面貌', type: 'select', options: function () { return dictLabels('political_status'); } },
    { key: 'partyJoinDate', label: '入党/入团日期', type: 'date' },
    { key: 'maritalStatus', label: '婚姻状况', type: 'select', options: function () { return dictLabels('marital_status'); } },
    { key: 'education', label: '学历', type: 'select', options: function () { return dictLabels('education'); } },
    { key: 'onlyChild', label: '独生子女', type: 'select', options: function () { return dictLabels('only_child'); } },
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
  key: 'equipment', title: '装备管理', icon: 'shield', endpoint: '/api/equipment',
  clickKey: 'name', addLabel: '新增装备',
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
    { key: 'category', label: '类别', type: 'select', options: function () { return dictLabels('equipment_category'); } },
    { key: 'model', label: '型号' },
    { key: 'status', label: '状态', type: 'select', required: true, options: function () { return dictLabels('equipment_status'); } },
    { key: 'keeperId', label: '保管人', type: 'personnel' },
    { key: 'location', label: '存放位置' },
    { key: 'buyDate', label: '购置日期', type: 'date' },
    { key: 'remark', label: '备注', type: 'textarea', span: 'full' }
  ]
};

var materialCfg = {
  key: 'material', title: '物资管理', icon: 'box', endpoint: '/api/material',
  clickKey: 'name', addLabel: '新增物资',
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
    { key: 'category', label: '分类', type: 'select', options: function () { return dictLabels('material_category'); } },
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
  key: 'vehicle', title: '车辆管理', icon: 'truck', endpoint: '/api/vehicle',
  clickKey: 'plateNo', addLabel: '新增车辆',
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
    { key: 'type', label: '车型', type: 'select', options: function () { return dictLabels('vehicle_type'); } },
    { key: 'status', label: '状态', type: 'select', required: true, options: function () { return dictLabels('vehicle_status'); } },
    { key: 'driverId', label: '驾驶员', type: 'personnel' },
    { key: 'seats', label: '核定人数', type: 'number', step: '1' },
    { key: 'mileage', label: '里程(km)', type: 'number', step: '0.1' },
    { key: 'remark', label: '备注', type: 'textarea', span: 'full' }
  ]
};

var leaveCfg = {
  key: 'leave', title: '休假管理', icon: 'calendar', endpoint: '/api/leave',
  clickKey: 'personnelName', addLabel: '新增休假',
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
  key: 'fitness', title: '体能考核', icon: 'activity', endpoint: '/api/fitness',
  clickKey: 'personnelName', addLabel: '新增考核记录',
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
    { key: 'item', label: '考核项目', type: 'select', options: function () { return dictLabels('fitness_item'); }, required: true },
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
        '<div class="stat-card"><span class="ic">' + iconSvg('users') + '</span><div class="num">' + (s.total || 0) + '</div><div class="lbl">人员总数</div></div>' +
        '<div class="stat-card"><span class="ic">' + iconSvg('building') + '</span><div class="num">' + ((s.department || []).length) + '</div><div class="lbl">有人员的部门数</div></div>' +
        '<div class="stat-card"><span class="ic">' + iconSvg('check') + '</span><div class="num">' + onDuty + '</div><div class="lbl">在职人数</div></div>' +
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

/* ================= 操作日志视图 ================= */

function logView() {
  var state = { page: 1, size: 20, total: 0, keyword: '' };
  var root = h('div');
  var pagerBox = h('div');
  var table = h('table', { class: 'grid' });

  function load() {
    var q = { page: state.page, size: state.size };
    if (state.keyword) {
      q.keyword = state.keyword;
    }
    var qs = Object.keys(q).map(function (k) { return k + '=' + encodeURIComponent(q[k]); }).join('&');
    GET('/api/log?' + qs).then(function (pageData) {
      var rows = (pageData && pageData.list) || [];
      state.total = (pageData && pageData.total) || 0;
      table.innerHTML = '<thead><tr><th style="width:150px">时间</th><th style="width:110px">用户</th>'
        + '<th style="width:130px">IP</th><th style="width:120px">操作</th><th>详情</th></tr></thead><tbody>'
        + (rows.length ? rows.map(function (r) {
            return '<tr><td>' + esc(r.created_at) + '</td>'
              + '<td>' + esc(r.username || '-') + '</td>'
              + '<td>' + esc(r.ip || '-') + '</td>'
              + '<td>' + esc(r.action) + '</td>'
              + '<td>' + esc(r.detail || '') + '</td></tr>';
          }).join('') : '<tr><td colspan="5" class="empty-row">暂无日志</td></tr>')
        + '</tbody>';
      pagerBox.innerHTML = '';
      pagerBox.appendChild(renderPager(state, function (p) { state.page = p; load(); }));
    }).catch(function (e) { toast(e.message, 'err'); });
  }

  function render(container) {
    var kw = h('input', { type: 'text', 'data-fkey': 'kw', placeholder: '用户/操作/IP 关键字,回车搜索' });
    kw.addEventListener('keydown', function (e) {
      if (e.key === 'Enter') { state.keyword = kw.value.trim(); state.page = 1; load(); }
    });
    root.appendChild(h('div', { class: 'panel' },
      h('div', { class: 'toolbar' },
        h('div', { class: 'field-inline' }, h('label', { text: '检索' }), kw),
        h('button', {
          class: 'btn btn-ghost', text: '查询',
          onclick: function () { state.keyword = kw.value.trim(); state.page = 1; load(); }
        }),
        h('div', { class: 'spacer' }),
        h('span', { style: 'color:#8a97a8;font-size:13px', text: '自动记录:登录/登出/增删改/导入导出/备份' })),
      h('div', { class: 'table-wrap' }, table),
      pagerBox));
    container.appendChild(root);
    load();
  }
  return { render: render };
}

/* ================= 备份恢复视图 ================= */

function fmtSize(bytes) {
  if (bytes == null) return '';
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / 1024 / 1024).toFixed(1) + ' MB';
}

function backupView() {
  var root = h('div', { class: 'loading-inline', text: '加载中…' });
  var MODULES = [];   // [{key,label}]

  function moduleLabel(key) {
    for (var i = 0; i < MODULES.length; i++) {
      if (MODULES[i].key === key) return MODULES[i].label;
    }
    return key;
  }

  /** 模块勾选组;返回 {el, get, setAll} */
  function checkGroup(checkedKeys) {
    var boxes = [];
    var wrap = h('div', { class: 'chk-list' });
    MODULES.forEach(function (m) {
      var box = h('input', { type: 'checkbox', value: m.key });
      box.checked = checkedKeys.indexOf(m.key) >= 0;
      boxes.push(box);
      wrap.appendChild(h('label', {}, box, m.label));
    });
    return {
      el: wrap,
      get: function () {
        return boxes.filter(function (b) { return b.checked; })
          .map(function (b) { return b.value; });
      },
      setAll: function (on) { boxes.forEach(function (b) { b.checked = !!on; }); }
    };
  }

  function moduleBadges(mods) {
    if (!mods) return '<span class="badge gray">未知</span>';
    return Object.keys(mods).map(function (k) {
      var rows = 0;
      var tables = mods[k] || {};
      Object.keys(tables).forEach(function (t) { rows += Number(tables[t]) || 0; });
      return '<span class="badge blue" title="' + rows + ' 条">' + esc(moduleLabel(k)) + '</span>';
    }).join(' ');
  }

  function openRestore(bk) {
    var avail = bk.modules ? Object.keys(bk.modules) : [];
    if (!avail.length) {
      toast('该备份没有模块信息,无法恢复', 'err');
      return;
    }
    var group = checkGroup(avail);
    var body = h('div');
    body.appendChild(h('div', { class: 'backup-tip', text: '恢复会先清空所选模块的现有数据,再写入备份内容(每个模块独立执行)。例如只勾选“人员管理”则只覆盖人员,部门、装备等其他模块不受影响。管理员账号与密码不受影响。' }));
    body.appendChild(h('div', { style: 'font-size:13px;color:#66788c;margin-bottom:6px', text: '选择要恢复的模块:' }));
    body.appendChild(group.el);
    var m = openModal({
      title: '恢复备份 - ' + bk.createdText,
      body: body,
      footer: [
        h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m.close(); } }),
        h('button', {
          class: 'btn btn-danger', text: '开始恢复',
          onclick: function () {
            var mods = group.get();
            if (!mods.length) { toast('请至少勾选一个模块', 'err'); return; }
            POST('/api/backup/restore', { name: bk.name, modules: mods }).then(function (r) {
              m.close();
              var parts = (r.restored || []).map(function (x) {
                var rows = 0;
                var tables = x.tables || {};
                Object.keys(tables).forEach(function (t) { rows += Number(tables[t]) || 0; });
                return moduleLabel(x.module) + '(' + rows + '条)';
              });
              toast('恢复完成:' + parts.join('、') + (r.photos ? ',照片' + r.photos + '张' : ''), 'ok');
              refreshAll();
              loadList();
            }).catch(function (e) { toast(e.message, 'err'); });
          }
        })
      ]
    });
  }

  var listTable = h('table', { class: 'grid' });
  listTable.addEventListener('click', function (e) {
    var b = e.target.closest('button[data-act]');
    if (!b) return;
    var name = b.getAttribute('data-name');
    var act = b.getAttribute('data-act');
    if (act === 'restore') {
      var bk = lastList.filter(function (x) { return x.name === name; })[0];
      if (bk) openRestore(bk);
    } else if (act === 'download') {
      apiBlob('/api/backup/download?name=' + encodeURIComponent(name))
        .then(function (r) { downloadBlob(r.blob, r.filename); })
        .catch(function (e) { toast(e.message, 'err'); });
    } else if (act === 'delete') {
      confirmBox('确定删除备份文件 ' + name + ' 吗?删除后不可恢复。', true).then(function (yes) {
        if (!yes) return;
        POST('/api/backup/delete', { name: name }).then(function () {
          toast('已删除'); loadList();
        }).catch(function (e) { toast(e.message, 'err'); });
      });
    }
  });

  var lastList = [];
  function loadList() {
    GET('/api/backup/list').then(function (list) {
      lastList = list || [];
      listTable.innerHTML = '<thead><tr><th>创建时间</th><th>类型</th><th>包含模块</th><th>大小</th><th>文件名</th><th style="width:170px">操作</th></tr></thead><tbody>' +
        (lastList.length ? lastList.map(function (bk) {
          return '<tr><td>' + esc(bk.createdText) + '</td>' +
            '<td>' + (bk.auto ? '<span class="badge green">自动</span>' : '<span class="badge blue">手动</span>') + '</td>' +
            '<td>' + moduleBadges(bk.modules) + '</td>' +
            '<td>' + fmtSize(bk.size) + '</td>' +
            '<td title="' + esc(bk.name) + '">' + esc(bk.name) + '</td>' +
            '<td class="actions">' +
            '<button class="btn-link" data-act="restore" data-name="' + esc(bk.name) + '">恢复</button>' +
            '<button class="btn-link" data-act="download" data-name="' + esc(bk.name) + '">下载</button>' +
            '<button class="btn-link danger" data-act="delete" data-name="' + esc(bk.name) + '">删除</button>' +
            '</td></tr>';
        }).join('') : '<tr><td colspan="6" class="empty-row">暂无备份</td></tr>') + '</tbody>';
    }).catch(function (e) {
      toast(e.message, 'err');
    });
  }

  function render(container) {
    container.appendChild(root);
    Promise.all([GET('/api/backup/modules'), GET('/api/backup/settings')])
      .then(function (res) {
        MODULES = res[0] || [];
        var settings = res[1] || {};
        root.className = '';
        root.innerHTML = '';

        /* ---- 自动备份设置 ---- */
        var enabledBox = h('input', { type: 'checkbox' });
        enabledBox.checked = !!settings.enabled;
        var intervalInput = h('input', { type: 'number', min: '1', max: '720', step: '1' });
        intervalInput.value = settings.intervalHours || 24;
        var keepInput = h('input', { type: 'number', min: '1', max: '200', step: '1' });
        keepInput.value = settings.keepCount || 10;
        var autoGroup = checkGroup(settings.modules && settings.modules.length
          ? settings.modules : MODULES.map(function (m) { return m.key; }));
        var statusLine = h('div', { style: 'color:#66788c;font-size:13px;margin-top:10px' });
        function syncStatusLine() {
          statusLine.textContent = settings.enabled
            ? (settings.autoLastRun
              ? '上次自动备份:' + new Date(settings.autoLastRun).toLocaleString() + '(到点后自动执行)'
              : '已开启,尚未执行过自动备份(到点后自动执行)')
            : '自动备份当前:未开启';
        }
        syncStatusLine();

        var autoCard = h('div', { class: 'chart-panel' },
          h('h3', { text: '自动备份' }),
          h('div', { class: 'form-inline' },
            h('label', {}, enabledBox, ' 启用自动备份'),
            h('label', {}, '每 '), intervalInput, h('span', { text: ' 小时备份一次' }),
            h('label', {}, '自动备份保留最近 '), keepInput, h('span', { text: ' 份(超出自动删除)' })),
          h('div', { style: 'font-size:13px;color:#66788c;margin:8px 0 2px', text: '自动备份包含的模块:' }),
          autoGroup.el,
          statusLine,
          h('div', { style: 'margin-top:12px' },
            h('button', {
              class: 'btn btn-primary', text: '保存设置',
              onclick: function () {
                if (!autoGroup.get().length) { toast('自动备份至少选择一个模块', 'err'); return; }
                POST('/api/backup/settings', {
                  enabled: enabledBox.checked,
                  intervalHours: Number(intervalInput.value) || 24,
                  keepCount: Number(keepInput.value) || 10,
                  modules: autoGroup.get()
                }).then(function (s) {
                  settings = s; syncStatusLine();
                  toast('自动备份设置已保存');
                }).catch(function (e) { toast(e.message, 'err'); });
              }
            })));

        /* ---- 手动备份 ---- */
        var manualGroup = checkGroup(MODULES.map(function (m) { return m.key; }));
        var manualCard = h('div', { class: 'chart-panel' },
          h('h3', { text: '手动备份' }),
          h('div', { class: 'form-inline' },
            h('button', { class: 'btn btn-ghost', text: '全选', onclick: function () { manualGroup.setAll(true); } }),
            h('button', { class: 'btn btn-ghost', text: '全不选', onclick: function () { manualGroup.setAll(false); } }),
            h('button', {
              class: 'btn btn-primary', text: '立即备份',
              onclick: function () {
                var mods = manualGroup.get();
                if (!mods.length) { toast('请至少选择一个要备份的模块', 'err'); return; }
                toast('正在备份…', 'info');
                POST('/api/backup/run', { modules: mods }).then(function (r) {
                  toast('备份完成:' + fmtSize(r.size) + '(' + Object.keys(r.modules).map(moduleLabel).join('、') + ')');
                  loadList();
                }).catch(function (e) { toast(e.message, 'err'); });
              }
            })),
          manualGroup.el,
          h('div', { class: 'backup-tip', text: '备份文件保存在本机程序的 data/backup/ 文件夹;可下载后拷到 U 盘保存。恢复时可选只恢复其中部分模块。' }));

        /* ---- 备份列表 ---- */
        var listCard = h('div', { class: 'chart-panel', style: 'padding:0' },
          h('div', { class: 'table-wrap' }, listTable));

        root.appendChild(autoCard);
        root.appendChild(manualCard);
        listCard.insertBefore(h('h3', { style: 'padding:18px 20px 0;margin-bottom:10px', text: '备份文件' }), listCard.firstChild);
        root.appendChild(listCard);
        loadList();
      })
      .catch(function (e) {
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
  if (!TOKEN) {
    return; // 已被踢回登录页(令牌失效/退出),不再渲染主界面
  }
  var app = $('#app');
  app.innerHTML = '';

  var content = h('div', { class: 'content' });
  var titleEl = h('div', { class: 'topbar-title' });
  var menuItems = {};
  var currentView = 'personnel';

  function navigate(view) {
    currentView = view;
    // 记住当前页面:URL 锚点(刷新/分享/前进后退)+ localStorage 兜底
    if (('#' + view) !== location.hash) {
      try { history.replaceState(null, '', '#' + view); } catch (e) { location.hash = view; }
    }
    localStorage.setItem('lastView', view);
    Object.keys(menuItems).forEach(function (k) { menuItems[k].classList.remove('active'); });
    if (menuItems[view]) menuItems[view].classList.add('active');

    content.innerHTML = '';
    var comp;
    if (view === 'department') { titleEl.textContent = '部门管理'; comp = departmentView(); }
    else if (view === 'dict') { titleEl.textContent = '数据字典'; comp = dictView(); }
    else if (view === 'stats') { titleEl.textContent = '统计报表'; comp = statsView(); }
    else if (view === 'backup') { titleEl.textContent = '备份恢复'; comp = backupView(); }
    else if (view === 'log') { titleEl.textContent = '操作日志'; comp = logView(); }
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
    var item = h('div', {
      class: 'menu-item', 'data-tip': label,
      onclick: function () { navigate(key); }
    },
      h('span', { class: 'ic', html: iconSvg(icon) }), h('span', { class: 'txt', text: label }));
    menu.appendChild(item);
    menuItems[key] = item;
  }
  MODULES.forEach(function (m) { addMenu(m.key, m.icon, m.title); });
  addMenu('department', 'building', '部门管理');
  addMenu('dict', 'book', '数据字典');
  addMenu('backup', 'database', '备份恢复');
  addMenu('log', 'fileText', '操作日志');
  addMenu('stats', 'chart', '统计报表');

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

  var toggleIcon = h('span', { class: 'ic' });
  var toggleTxt = h('span', { class: 'txt', text: '收起菜单' });
  var toggleBtn = h('div', {
    class: 'menu-item toggle', 'data-tip': '展开菜单',
    onclick: function () { setCollapsed(!sideEl.classList.contains('collapsed')); }
  }, toggleIcon, toggleTxt);

  var sideEl = h('div', { class: 'sidebar' },
    h('div', { class: 'sidebar-logo' },
      h('span', { class: 'star', text: '★' }),
      h('div', {},
        h('div', { class: 't1', text: '数智军营' }),
        h('div', { class: 't2', text: '人员管理系统' }))),
    menu,
    h('div', { class: 'sidebar-toggle' }, toggleBtn));

  function setCollapsed(collapsed) {
    sideEl.classList.toggle('collapsed', collapsed);
    localStorage.setItem('sidebarCollapsed', collapsed ? '1' : '0');
    toggleIcon.innerHTML = iconSvg(collapsed ? 'chevronR' : 'chevronL');
    toggleTxt.textContent = collapsed ? '展开菜单' : '收起菜单';
    toggleBtn.setAttribute('data-tip', collapsed ? '展开菜单' : '收起菜单');
  }
  setCollapsed(localStorage.getItem('sidebarCollapsed') === '1');

  app.appendChild(h('div', { class: 'layout' },
    sideEl,
    h('div', { class: 'main' }, topbar, content)));

  // 浏览器前进/后退键切换模块
  window.addEventListener('hashchange', function () {
    var v = (location.hash || '').replace('#', '');
    if (menuItems[v] && v !== currentView) navigate(v);
  });

  // 恢复上次所在页面:优先 URL 锚点,其次 localStorage
  var initialView = (location.hash || '').replace('#', '');
  var validViews = Object.keys(menuItems);
  if (validViews.indexOf(initialView) < 0) {
    initialView = localStorage.getItem('lastView') || 'personnel';
  }
  if (validViews.indexOf(initialView) < 0) {
    initialView = 'personnel';
  }
  navigate(initialView);
}

/* ================= 启动 ================= */

(function boot() {
  if (!TOKEN) { renderLogin(); return; }
  refreshAll().then(function () {
    if (TOKEN) {
      renderShell();             // 缓存刷新期间令牌失效则不进主界面(已在登录页)
    }
  }).catch(function (e) {
    if (!TOKEN) return;          // 401 已跳转登录
    toast(e.message, 'err');
    renderShell();
  });
})();
