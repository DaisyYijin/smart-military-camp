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
  var dlg = h('div', { class: 'dialog' + (opt.wide ? ' wide' : '') + (opt.xwide ? ' xwide' : '') }, head, body);
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
  if (/在职|正常|优秀|已归还|启用|奖励|已执行/.test(t)) cls += ' green';
  else if (/休假|借调|培训|良好|使用中|计划中/.test(t)) cls += ' blue';
  else if (/离职|报废|不及格|损坏|停用|处分/.test(t)) cls += ' red';
  else if (/维修|保养|及格|预警|缺货/.test(t)) cls += ' orange';
  else if (/其他|未填写|已取消/.test(t)) cls += ' gray';
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
  chevronR: '<polyline points="9 18 15 12 9 6"/>',
  schedule: '<circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>'
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

  /** 月份输入(2026-09):合法返回规范化 YYYY-MM,否则 null */
  function monthText(t) {
    var m = /^(\d{4})-(\d{1,2})$/.exec(String(t == null ? '' : t).trim());
    return m && +m[2] >= 1 && +m[2] <= 12 ? m[1] + '-' + p2(+m[2]) : null;
  }

  function commit(text) {
    var mo = monthText(text);
    if (mo) {
      input.value = mo;
      input.classList.remove('bad');
      wrap.dispatchEvent(new CustomEvent('change'));
      return;
    }
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
      if (d) { return fmt(d); }
      return monthText(input.value) || '';
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
    panel.addEventListener('mousedown', function (e) { e.preventDefault(); });
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
    $all('.dd.open, .cal.open').forEach(function (d) { d.classList.remove('open'); });
    if (wrap.classList.contains('open')) {
      wrap.classList.remove('open');
    } else {
      openPanel();
    }
  });
  input.addEventListener('click', function (e) {
    e.stopPropagation();
    $all('.dd.open, .cal.open').forEach(function (d) { d.classList.remove('open'); });
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

/** 进度弹窗:返回 {close, setProgress(pct), setStatus(text)} */
function showProgressModal(title) {
  var fill = h('div', { class: 'pg-fill', style: 'width:0%' });
  var bar = h('div', { class: 'pg-bar' }, fill);
  var status = h('div', { style: 'margin:10px 0 6px;font-size:13px;color:#66788c', text: '准备中…' });
  var pctText = h('div', { style: 'font-size:12px;color:#97a4b5;text-align:right', text: '0%' });
  var m = openModal({
    title: title,
    body: h('div', {}, status, bar, pctText)
  });
  return {
    close: function () { m.close(); },
    setProgress: function (pct) {
      fill.style.width = Math.min(100, Math.max(0, pct)) + '%';
      pctText.textContent = Math.round(pct) + '%';
    },
    setStatus: function (text) { status.textContent = text; }
  };
}

/** XHR 下载 blob(带进度回调) */
function apiBlobWithProgress(url, onProgress) {
  return new Promise(function (resolve, reject) {
    var xhr = new XMLHttpRequest();
    xhr.open('GET', url);
    if (TOKEN) { xhr.setRequestHeader('X-Auth-Token', TOKEN); }
    xhr.responseType = 'blob';
    xhr.onprogress = function (e) {
      if (e.lengthComputable && onProgress) {
        onProgress(Math.round(e.loaded / e.total * 100));
      }
    };
    xhr.onload = function () {
      if (xhr.status === 401) { reject(new Error('登录已过期')); return; }
      if (xhr.status !== 200) { reject(new Error('下载失败(' + xhr.status + ')')); return; }
      var cd = xhr.getResponseHeader('Content-Disposition') || '';
      var m = /filename\*=UTF-8''([^;]+)/.exec(cd);
      var filename = m ? decodeURIComponent(m[1]) : 'export.bin';
      resolve({ blob: xhr.response, filename: filename });
    };
    xhr.onerror = function () { reject(new Error('网络错误')); };
    xhr.send();
  });
}

/** XHR 上传(带进度回调) */
function postFormWithProgress(url, formData, onProgress) {
  return new Promise(function (resolve, reject) {
    var xhr = new XMLHttpRequest();
    xhr.open('POST', url);
    if (TOKEN) { xhr.setRequestHeader('X-Auth-Token', TOKEN); }
    xhr.upload.onprogress = function (e) {
      if (e.lengthComputable && onProgress) {
        onProgress(Math.round(e.loaded / e.total * 100));
      }
    };
    xhr.onload = function () {
      if (xhr.status === 401) { reject(new Error('登录已过期')); return; }
      try {
        var data = JSON.parse(xhr.responseText);
        if (data.code !== 0) { reject(new Error(data.message || '导入失败')); return; }
        resolve(data.data);
      } catch (e) {
        reject(new Error('响应解析失败'));
      }
    };
    xhr.onerror = function () { reject(new Error('网络错误')); };
    xhr.send(formData);
  });
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

  // 动态更新选项(级联下拉用):当前值不在新选项中时自动清空
  wrap.setOptions = function (newOpts) {
    items = (newOpts || []).map(function (o) {
      if (o && o.v !== undefined) {
        return { v: String(o.v), t: String(o.l !== undefined ? o.l : o.v) };
      }
      return { v: String(o), t: String(o) };
    });
    if (!items.length || items[0].v !== '') {
      items.unshift({ v: '', t: ph });
    }
    list.innerHTML = '';
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
    var stillThere = cur === '';
    for (var i = 0; i < items.length; i++) {
      if (items[i].v === cur) { stillThere = true; break; }
    }
    if (!stillThere) cur = '';
    refresh();
  };

  btn.addEventListener('click', function (e) {
    e.stopPropagation();
    var opening = !wrap.classList.contains('open');
    $all('.dd.open, .cal.open').forEach(function (d) { d.classList.remove('open'); });
    wrap.classList.toggle('open', opening);
    if (!opening) {
      return;
    }
    // 视口定位:避免被弹窗滚动区域裁剪
    var r = btn.getBoundingClientRect();
    list.style.position = 'fixed';
    list.style.left = r.left + 'px';
    list.style.minWidth = r.width + 'px';
    list.style.maxWidth = (window.innerWidth - r.left - 10) + 'px';
    list.style.width = 'auto';
    var lh = list.offsetHeight || 200;
    var below = r.bottom + 6 + lh <= window.innerHeight;
    list.style.top = (below ? r.bottom + 5 : Math.max(6, r.top - lh - 5)) + 'px';
  });
  list.addEventListener('click', function (e) { e.stopPropagation(); });
  list.addEventListener('mousedown', function (e) { e.preventDefault(); });

  return wrap;
}

// 标签栏鼠标拖动横向滚动
(function tabsDragInit() {
  document.addEventListener('mousedown', function (e) {
    var tabs = e.target.closest('.tabs');
    if (!tabs) { return; }
    var startX = e.clientX;
    var startScroll = tabs.scrollLeft;
    var dragging = false;
    function onMove(ev) {
      var dx = ev.clientX - startX;
      if (Math.abs(dx) > 3) { dragging = true; }
      tabs.scrollLeft = startScroll - dx;
      ev.preventDefault();
    }
    function onUp() {
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
      if (dragging) {
        tabs.__suppressClick = true;
        setTimeout(function () { tabs.__suppressClick = false; }, 100);
      }
    }
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  });
  document.addEventListener('click', function (e) {
    var tabs = e.target.closest('.tabs');
    if (tabs && tabs.__suppressClick) {
      e.stopPropagation();
      e.preventDefault();
    }
  }, true);
})();

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

/* ================= 站点外观/主题(系统设置) ================= */
var SITE = { name: '数智军营', title: '数智军营人员管理系统', subtitle: '人员管理系统', logo: '', favicon: '', theme: '#2f6fd6', pageSize: 20 };

/** 当前主题色(读 CSS 变量,图表绘制用) */
function PRI() {
  var v = getComputedStyle(document.documentElement).getPropertyValue('--pri').trim();
  return v || '#2f6fd6';
}
/** 由主色派生深色(标题/选中态) */
function shade(c, f) {
  var m = /^#([0-9a-f]{6})$/i.exec((c || '').trim());
  if (!m) { return '#16437e'; }
  var n = parseInt(m[1], 16);
  var c = function (x) { return Math.max(0, Math.min(255, Math.round(x))); };
  var r = c(((n >> 16) & 255) * f), g = c(((n >> 8) & 255) * f), b = c((n & 255) * f);
  return '#' + ((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1);
}
/** 应用外观: 主题色/标签标题/favicon */
/** 一次性应用全部主题派生色(启动/选色共用) */
function applyThemeVars(c) {
  var t = /^#[0-9a-f]{6}$/i.test(c) ? c : '#2f6fd6';
  document.documentElement.style.setProperty('--pri', t);
  document.documentElement.style.setProperty('--pri-dark', shade(t, 0.62));
  document.documentElement.style.setProperty('--pri-deep', shade(t, 0.40));   // 侧栏底色
  document.documentElement.style.setProperty('--pri-light', shade(t, 1.45));  // 亮描边
  document.documentElement.style.setProperty('--pri-a28', t + '47');          // 28%透明
  document.documentElement.style.setProperty('--pri-a35', t + '59');          // 35%透明
}

function applyBranding() {
  applyThemeVars(SITE.theme);
  document.title = SITE.title || SITE.name || '人员管理系统';
  var fav = document.querySelector('link[rel="icon"]') || document.createElement('link');
  fav.setAttribute('rel', 'icon');
  fav.href = SITE.favicon || 'data:image/svg+xml,' + encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><rect width="24" height="24" rx="5" fill="' + SITE.theme + '"/><text x="12" y="17" font-size="13" text-anchor="middle" fill="#fff">⭐</text></svg>');
  if (!fav.parentNode) { document.head.appendChild(fav); }
}
/** 启动时拉取站点设置(免登录,失败用默认) */
function loadSiteSettings() {
  return fetch('/api/setting/public').then(function (r) { return r.json(); }).then(function (j) {
    var d = (j && j.data) || {};
    if (d.siteName) { SITE.name = d.siteName; }
    if (d.siteTitle) { SITE.title = d.siteTitle; }
    if (d.siteSubtitle !== undefined && d.siteSubtitle !== '') { SITE.subtitle = d.siteSubtitle; }
    if (d.siteLogo) { SITE.logo = d.siteLogo; }
    if (d.siteFavicon) { SITE.favicon = d.siteFavicon; }
    if (d.themeColor && /^#[0-9a-f]{6}$/i.test(d.themeColor)) { SITE.theme = d.themeColor; }
    applyBranding();
    return d;
  }).catch(function () { applyBranding(); });
}

/* ================= API 层 ================= */

var TOKEN = sessionStorage.getItem('tk') || localStorage.getItem('tk') || '';
var USER = null;
try { USER = JSON.parse(sessionStorage.getItem('usr') || localStorage.getItem('usr') || 'null'); } catch (e) { USER = null; }

function saveSession(token, user, remember) {
  TOKEN = token; USER = user;
  try {
    sessionStorage.setItem('tk', token);
    sessionStorage.setItem('usr', JSON.stringify(user || {}));
  } catch (e) { /* 隐私模式忽略 */ }
  if (remember) {
    localStorage.setItem('tk', token);
    localStorage.setItem('usr', JSON.stringify(user || {}));
  } else {
    localStorage.removeItem('tk');
    localStorage.removeItem('usr');
  }
}
function clearSession() {
  TOKEN = ''; USER = null;
  try { sessionStorage.removeItem('tk'); sessionStorage.removeItem('usr'); } catch (e) { }
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

/**
 * FLIP 滑动:布局瞬时切换到终态,再施加反向 transform 从旧视觉位置平滑滑入。
 * 只动画 transform(合成层,GPU 加速),避免逐帧 width/margin 重排大表格造成卡顿。
 */
function slideFrom(el, deltaX, duration) {
  if (!el || !deltaX) return;
  var ms = duration || 300;
  el.style.transition = 'none';
  el.style.transform = 'translateX(' + deltaX + 'px)';
  void el.offsetWidth; // 强制回流,让起始位置先生效
  el.style.transition = 'transform ' + ms + 'ms cubic-bezier(.4,0,.2,1)';
  el.style.transform = 'translateX(0)';
  var done = false;
  function clear() {
    if (done) return;
    done = true;
    el.style.transition = '';
    el.style.transform = '';
    el.removeEventListener('transitionend', clear);
  }
  el.addEventListener('transitionend', clear);
  setTimeout(clear, ms + 100);
}

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
    (function walk(nodes, depth, parentId) {
      (nodes || []).forEach(function (n) {
        Ctx.deptFlat.push({ id: n.id, name: n.name, depth: depth, parentId: parentId });
        walk(n.children, depth + 1, n.id);
      });
    })(Ctx.deptTree, 0, null);
  }).catch(function () { Ctx.deptTree = []; Ctx.deptFlat = []; });
}

function refreshPersonnelOptions() {
  return GET('/api/personnel/options').then(function (list) {
    Ctx.personnelOptions = list || [];
  }).catch(function () { Ctx.personnelOptions = []; });
}

function refreshAll() {
  return Promise.all([refreshDicts(), refreshDepts(), refreshPersonnelOptions(), loadGradeRule(), loadHolidayMap()]);
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

function buildForm(fields, values, extraPanels) {
  values = values || {};
  extraPanels = extraPanels || {};
  var inputs = {};
  var sectionOf = {};
  var datalistSeq = 0;
  var pendingDepends = []; // 级联下拉字段,全部字段创建完毕后统一绑定

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
    // 隐藏字段:只存值不显示(如身份证反查出的 personnelId)
    if (f.type === 'hidden') {
      var hi = h('input', { type: 'hidden' });
      hi.value = values[f.key] === undefined || values[f.key] === null ? '' : String(values[f.key]);
      inputs[f.key] = hi;
      panel.appendChild(hi);
      return;
    }
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
      el = makeDropdown(ddOpts, f.placeholder || (f.type === 'dept' && !f.required ? '(不指定)' : '请选择'));
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
      if (f.min !== undefined) el.setAttribute('min', f.min);
      if (f.max !== undefined) el.setAttribute('max', f.max);
      if (val !== undefined && val !== null) el.value = val;
    }
    // 只读字段:自动计算/自动带出的值不允许手改
    if (f.readonly) {
      if (el.classList && el.classList.contains('cal')) {
        var calInp = el.querySelector('input');
        if (calInp) calInp.readOnly = true;
        var calBtn = el.querySelector('.cal-icon');
        if (calBtn) calBtn.style.display = 'none';
        el.classList.add('ro');
      } else {
        try { el.readOnly = true; } catch (e) { /* ignore */ }
        el.classList.add('ro');
      }
    }
    inputs[f.key] = el;
    wrap.appendChild(el);
    panel.appendChild(wrap);

    // 人员下拉旁加"选择"按钮:弹窗按姓名/身份证/部门搜索选人
    if (f.type === 'personnel') {
      attachPersonPick(el, function (p) { el.value = String(p.id); });
    }

    // 级联下拉:父字段选定后,本字段选项过滤为该父项的下级(如 品牌→型号)
    if (f.depends && f.type === 'select') {
      pendingDepends.push(f);
    }
  }

  groups.forEach(function (g, gi) {
    if (!g.fields.length && !extraPanels[g.title]) {
      return;
    }
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
    if (extraPanels[g.title]) {
      panel.appendChild(extraPanels[g.title]);
    }
    panels.push(panel);
  });

  // 绑定级联下拉(此时全部 inputs 已就绪)
  pendingDepends.forEach(function (f) {
    var childEl = inputs[f.key];
    var parentEl = inputs[f.depends.field];
    if (!childEl || !parentEl || !childEl.setOptions) return;
    var dep = f.depends;
    var saved = values[f.key];
    function childOptions() {
      var pv = String(parentEl.value || '');
      if (!pv) return [];
      var parentDict = dep.parentDict || dep.dict;
      var parent = null;
      dictItems(parentDict).forEach(function (d) {
        if (d.dictLabel === pv) parent = d;
      });
      if (!parent) return [];
      return dictItems(dep.dict).filter(function (d) {
        return d.parentId && String(d.parentId) === String(parent.id) && d.enabled !== false;
      }).map(function (d) { return d.dictLabel; });
    }
    function refreshChild(keepSaved) {
      childEl.setOptions(childOptions());
      if (keepSaved && saved !== undefined && saved !== null && String(saved) !== '') {
        childEl.value = String(saved);
      }
    }
    parentEl.addEventListener('change', function () { refreshChild(false); });
    refreshChild(true);
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
    inputs: inputs,
    setError: function (msg) { err.textContent = msg || ''; },
    get: function () {
      var out = {};
      var missing = [];
      var firstBadKey = null;
      // 先收集全部值(条件必填需要读取其他字段的值)
      fields.forEach(function (f) {
        if (f.section) { return; }
        var el = inputs[f.key];
        if (f.type === 'number') {
          out[f.key] = el.value === '' ? null : Number(el.value);
        } else {
          out[f.key] = String(el.value || '').trim();
        }
      });
      // 再校验(含条件必填)
      fields.forEach(function (f) {
        if (f.section) { return; }
        var v = out[f.key];
        var need = f.required || (f.requiredIf && f.requiredIf(out));
        if (need && !v) {
          missing.push(f.label);
          if (!firstBadKey) { firstBadKey = f.key; }
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

/** 人员选择弹窗:按姓名/身份证/部门搜索后点选,onPick({id,name,idCard,departmentName}) */
function openPersonPicker(onPick) {
  var selName = h('input', { type: 'text', placeholder: '姓名' });
  var selIdCard = h('input', { type: 'text', placeholder: '身份证号' });
  var selDept = makeDropdown(Ctx.deptFlat.map(function (d) {
    return { v: String(d.id), l: new Array(d.depth + 1).join('　') + d.name };
  }), '全部部门');
  var info = h('div', { class: 'picker-info', text: '' });
  var resultBox = h('div', { class: 'picker-result' });

  function search() {
    var q = '/api/personnel?page=1&size=15';
    if (selName.value.trim()) q += '&name=' + encodeURIComponent(selName.value.trim());
    if (selIdCard.value.trim()) q += '&idCard=' + encodeURIComponent(selIdCard.value.trim());
    if (selDept.value) q += '&departmentId=' + encodeURIComponent(selDept.value);
    resultBox.innerHTML = '<div class="empty-row">搜索中…</div>';
    info.textContent = '';
    GET(q).then(function (d) {
      var list = (d && d.list) || [];
      info.textContent = d && d.total ? ('共 ' + d.total + ' 人,显示前 ' + list.length + ' 人') : '';
      if (!list.length) {
        resultBox.innerHTML = '<div class="empty-row">未找到匹配人员</div>';
        return;
      }
      resultBox.innerHTML = '';
      list.forEach(function (p) {
        resultBox.appendChild(h('div', {
          class: 'picker-row',
          onclick: function () {
            m.close();
            onPick({ id: p.id, name: p.name || '', idCard: p.idCard || '', departmentName: p.departmentName || '' });
          }
        },
          h('b', { text: p.name || '' }),
          h('span', { text: p.idCard || '' }),
          h('span', { text: ((p.departmentName || '') + (p.rank ? ' · ' + p.rank : '')).trim() })));
      });
    }).catch(function (e) {
      resultBox.innerHTML = '<div class="empty-row">' + esc(e.message) + '</div>';
    });
  }

  [selName, selIdCard].forEach(function (el) {
    el.addEventListener('keydown', function (e) { if (e.key === 'Enter') search(); });
  });
  var bar = h('div', { class: 'picker-bar' },
    selName, selIdCard, selDept,
    h('button', { class: 'btn btn-primary', text: '搜索', onclick: search }));

  var m = openModal({
    title: '选择人员',
    body: h('div', { class: 'person-picker' }, bar, info, resultBox),
    footer: [h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m.close(); } })]
  });
  search(); // 打开即列出前15人
}

/** 在姓名只读框旁加"选择"按钮,弹窗选人后回调回填 */
function attachPersonPick(nameEl, onPick) {
  if (!nameEl || !nameEl.parentElement) return;
  var btn = h('button', { type: 'button', class: 'btn btn-ghost pick-btn', text: '选择' });
  btn.addEventListener('click', function () { openPersonPicker(onPick); });
  var row = h('div', { class: 'field-row' });
  nameEl.parentElement.replaceChild(row, nameEl);
  row.appendChild(nameEl);
  row.appendChild(btn);
}

/** 通用:身份证号反查人员,自动带出姓名(隐藏 pid 字段);姓名旁提供弹窗选人 */
function setupPersonLookup(form, map) {
  var idCardEl = form.inputs[map.idCard];
  var nameEl = form.inputs[map.name];
  var pidEl = form.inputs[map.pid];
  if (!idCardEl) return;
  function lookup() {
    var v = String(idCardEl.value || '').trim();
    if (!v) {
      if (nameEl) nameEl.value = '';
      if (pidEl) pidEl.value = '';
      idCardEl.classList.remove('bad');
      idCardEl.title = '';
      return;
    }
    GET('/api/personnel/by-idcard?idCard=' + encodeURIComponent(v)).then(function (p) {
      if (p) {
        if (nameEl) nameEl.value = p.name || '';
        if (pidEl) pidEl.value = String(p.id);
        idCardEl.classList.remove('bad');
        idCardEl.title = p.departmentName || '';
      } else {
        if (nameEl) nameEl.value = '';
        if (pidEl) pidEl.value = '';
        idCardEl.classList.add('bad');
        idCardEl.title = '未找到对应人员';
        if (form.setError) form.setError('未找到身份证号「' + v + '」对应的人员,请核对后重新输入');
      }
    }).catch(function () { /* 网络异常保持现状 */ });
  }
  idCardEl.addEventListener('change', lookup);
  attachPersonPick(nameEl, function (p) {
    idCardEl.value = p.idCard;
    nameEl.value = p.name;
    pidEl.value = String(p.id);
    idCardEl.classList.remove('bad');
    idCardEl.title = p.departmentName || '';
  });
  // 编辑时预填了身份证号 → 自动带出校验
  if (String(idCardEl.value || '').trim()) setTimeout(lookup, 60);
}

/** 休假模块联动:身份证带出人员 + 路途年度限一次 + 结束日期/总天数自动计算 */
function setupLeaveAutoCalc(form, row) {
  var startDateEl = form.inputs['startDate'];
  var daysEl = form.inputs['days'];
  var travelEl = form.inputs['travelDays'];
  var holidayEl = form.inputs['holidayDays'];
  var endDateEl = form.inputs['endDate'];
  var yearEl = form.inputs['year'];
  var personEl = form.inputs['personnelId'];   // 隐藏字段,由身份证反查填充
  var idCardEl = form.inputs['idCard'];
  var nameEl = form.inputs['personnelName'];
  var totalEl = form.inputs['totalDays'];
  if (!startDateEl || !daysEl) return;

  var editId = row ? row.id : null;

  function fmtNum(n) {
    return Math.round(n * 10) / 10;
  }

  /* —— 身份证号 → 自动带出人员 —— */
  function lookupIdCard() {
    var v = idCardEl ? String(idCardEl.value || '').trim() : '';
    if (!v) {
      if (nameEl) nameEl.value = '';
      if (personEl) personEl.value = '';
      if (idCardEl) idCardEl.classList.remove('bad');
      form.setError('');
      refreshYear();
      return;
    }
    GET('/api/leave/find-person?idCard=' + encodeURIComponent(v)).then(function (list) {
      if (list && list.length) {
        var p = list[0];
        if (nameEl) nameEl.value = p.name || '';
        if (personEl) personEl.value = String(p.id);
        if (idCardEl) idCardEl.classList.remove('bad');
        form.setError('');
      } else {
        if (nameEl) nameEl.value = '';
        if (personEl) personEl.value = '';
        if (idCardEl) {
          idCardEl.classList.add('bad');
          idCardEl.title = '未找到对应人员';
        }
        form.setError('未找到身份证号「' + v + '」对应的人员,请核对后重新输入');
      }
      refreshYear();
    }).catch(function () { refreshYear(); });
  }
  if (idCardEl) {
    idCardEl.addEventListener('change', lookupIdCard);
    // 姓名旁提供弹窗选人
    attachPersonPick(nameEl, function (p) {
      idCardEl.value = p.idCard;
      if (nameEl) nameEl.value = p.name;
      if (personEl) personEl.value = String(p.id);
      idCardEl.classList.remove('bad');
      idCardEl.title = p.departmentName || '';
      form.setError('');
      refreshYear();
    });
    // 编辑时预填了身份证号 → 自动带出校验
    if (String(idCardEl.value || '').trim()) setTimeout(lookupIdCard, 60);
  }

  /* —— 路途天数每年度限一次 —— */
  function checkTravel() {
    if (!personEl || !yearEl || !travelEl) return;
    var pid = personEl.value;
    var yr = String(yearEl.value || '').trim();
    if (!pid || !yr) { unlockTravel(); return; }
    GET('/api/leave/check-travel?personnelId=' + pid + '&year=' + encodeURIComponent(yr)).then(function (used) {
      // 编辑时排除自己
      var others = (used || []).filter(function (u) { return String(u.id) !== String(editId); });
      if (others.length > 0) {
        var when = others[0].start_date || '';
        travelEl.value = '';
        travelEl.readOnly = true;
        travelEl.classList.add('ro');
        travelEl.title = when + ' 已使用路途天数';
        var lbl = travelEl.parentElement.querySelector('label');
        if (lbl) {
          var old = lbl.querySelector('.travel-warn');
          if (old) old.remove();
          var w = document.createElement('span');
          w.className = 'travel-warn';
          w.style.cssText = 'color:#d9463e;font-size:12px;margin-left:6px';
          w.textContent = '(' + yr + '年度路途已在 ' + when + ' 使用)';
          lbl.appendChild(w);
        }
      } else {
        unlockTravel();
      }
    }).catch(function () { unlockTravel(); });
  }
  function unlockTravel() {
    if (!travelEl) return;
    travelEl.readOnly = false;
    travelEl.classList.remove('ro');
    travelEl.title = '';
    var lbl = travelEl.parentElement ? travelEl.parentElement.querySelector('label') : null;
    var w = lbl ? lbl.querySelector('.travel-warn') : null;
    if (w) w.remove();
  }

  /* —— 本条总天数 = 基本 + 路途 + 节假日 —— */
  function updateTotal() {
    if (!totalEl) return;
    var bd = parseFloat(daysEl.value) || 0;
    var td = travelEl ? parseFloat(travelEl.value) || 0 : 0;
    var hd = holidayEl ? parseFloat(holidayEl.value) || 0 : 0;
    if (bd + td + hd <= 0) { totalEl.value = ''; return; }
    totalEl.value = fmtNum(bd + td + hd) + ' 天(基本 ' + fmtNum(bd) + ' + 路途 ' + fmtNum(td) + ' + 节假日 ' + fmtNum(hd) + ')';
  }

  function refreshYear() { checkTravel(); updateTotal(); }

  if (personEl) personEl.addEventListener('change', refreshYear);
  if (yearEl) {
    yearEl.addEventListener('change', refreshYear);
    yearEl.addEventListener('input', updateTotal);
  }
  // 编辑打开时:预填的年度/人员需要触发一次检查与统计
  setTimeout(refreshYear, 150);

  /* —— 结束日期自动计算:开始日期 + 基本天数 + 手填节假日 + 路途 —— */
  function calc() {
    var sd = startDateEl.value;
    var bd = parseFloat(daysEl.value) || 0;
    var td = travelEl ? parseFloat(travelEl.value) || 0 : 0;
    var hd = holidayEl ? parseFloat(holidayEl.value) || 0 : 0;
    if (sd && bd > 0 && endDateEl) {
      var total = bd + hd + td;
      var m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(sd);
      if (m) {
        var d = new Date(+m[1], +m[2] - 1, +m[3]);
        d.setDate(d.getDate() + Math.round(total) - 1);
        endDateEl.value = d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
      }
    }
    updateTotal();
  }

  startDateEl.addEventListener('change', calc);
  daysEl.addEventListener('input', calc);
  if (travelEl) travelEl.addEventListener('input', calc);
  if (holidayEl) holidayEl.addEventListener('input', calc);
  if (row) setTimeout(calc, 200);
}

function crudView(cfg) {
  var state = { page: 1, size: (SITE.pageSize || 20), total: 0, rows: [], filters: {}, loading: false, selected: {} };

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
  var loadingMore = false;
  var allLoaded = false;
  var sentinel = h('div', { class: 'scroll-sentinel' },
    h('div', { class: 'scroll-indicator' }));

  function collectFilters() {
    var out = {};
    $all('[data-fkey]', root).forEach(function (el) {
      out[el.getAttribute('data-fkey')] = el.value.trim();
    });
    return out;
  }

  function buildQuery(append) {
    var q = {};
    Object.keys(state.filters).forEach(function (k) {
      if (state.filters[k] !== '') q[k] = state.filters[k];
    });
    q.page = append ? state.page + 1 : 1;
    q.size = state.size;
    return Object.keys(q).map(function (k) { return k + '=' + encodeURIComponent(q[k]); }).join('&');
  }

  function load() {
    state.filters = collectFilters();
    state.page = 1;
    allLoaded = false;
    state.loading = true;
    renderTable();
    GET(cfg.endpoint + '?' + buildQuery(false)).then(function (pageData) {
      state.rows = (pageData && pageData.list) || [];
      state.total = (pageData && pageData.total) || 0;
      allLoaded = state.rows.length >= state.total;
      updateIndicator();
    }).catch(function (e) {
      state.rows = []; state.total = 0; allLoaded = true;
      toast(e.message, 'err');
    }).then(function () {
      state.loading = false;
      renderTable();
      if (cfg.afterTable) cfg.afterTable(root);
    });
  }

  function loadMore() {
    if (loadingMore || allLoaded || state.loading) return;
    loadingMore = true;
    updateIndicator('加载中…');
    GET(cfg.endpoint + '?' + buildQuery(true)).then(function (pageData) {
      var list = (pageData && pageData.list) || [];
      state.page++;
      state.rows = state.rows.concat(list);
      state.total = (pageData && pageData.total) || state.total;
      if (state.rows.length >= state.total || list.length === 0) {
        allLoaded = true;
      }
      renderTable();
      updateIndicator();
    }).catch(function () {
      allLoaded = true;
    }).then(function () {
      loadingMore = false;
    });
  }

  function updateIndicator(text) {
    var el = sentinel.firstChild;
    if (text) {
      el.textContent = text;
      el.className = 'scroll-indicator loading';
      return;
    }
    if (allLoaded) {
      el.textContent = state.rows.length ? '已加载全部 ' + state.rows.length + ' 条' : '';
      el.className = 'scroll-indicator done';
    } else {
      el.textContent = '下拉加载更多';
      el.className = 'scroll-indicator';
    }
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
    var subExtra = cfg.subPanels ? cfg.subPanels(row) : null;
    var form = buildForm(cfg.form, row || {}, subExtra ? subExtra.panels : null);
    var extra = cfg.formTop ? cfg.formTop(row) : null;
    var body = h('div');
    if (extra) body.appendChild(extra.el);
    body.appendChild(form.el);

    // 休假模块特殊逻辑:自动计算 + 路途检查
    if (cfg.key === 'leave') {
      setupLeaveAutoCalc(form, row);
    }
    // 模块自定义表单联动(如休假计划自动算天数)
    if (cfg.formSetup) {
      cfg.formSetup(form, row);
    }
    // 体能考核:填分数自动按评定标准评级(可手动改)
    if (cfg.key === 'fitness') {
      var ptsEl = form.inputs['points'];
      var gradeEl = form.inputs['grade'];
      if (ptsEl && gradeEl) {
        if (!GRADE_RULE) { loadGradeRule(); }
        ptsEl.addEventListener('input', function () {
          var g = gradeForPoints(ptsEl.value);
          if (g) { gradeEl.value = g; }
        });
      }
    }
    // 身份证反查人员(如车辆的驾驶员)
    if (cfg.personLookup) {
      setupPersonLookup(form, cfg.personLookup);
    }

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
            // 保存前转换钩子(剔除仅用于展示的虚拟字段等)
            if (cfg.beforeSave) {
              var transformed = cfg.beforeSave(data, row);
              if (transformed) data = transformed;
            }
            if (extra && extra.get) {
              var ex = extra.get();
              if (!ex) return;
              Object.keys(ex).forEach(function (k) { if (ex[k] !== undefined) data[k] = ex[k]; });
            }
            var done = row ? PUT(cfg.endpoint + '/' + row.id, data) : POST(cfg.endpoint, data);
            done.then(function (saved) {
              // 新建时:暂存的子记录(任职经历/奖惩)随人员一并入库
              var chain = Promise.resolve();
              var subCount = 0;
              if (!row && subExtra && subExtra.staged && saved && saved.id) {
                Object.keys(subExtra.staged).forEach(function (kind) {
                  (subExtra.staged[kind]() || []).forEach(function (rec) {
                    chain = chain.then(function () {
                      var body = {};
                      for (var k in rec) {
                        body[k] = rec[k];
                      }
                      body.personnelId = saved.id;
                      return POST('/api/sub/' + kind, body).then(function () {
                        subCount++;
                      }).catch(function () { /* 单条失败不阻断 */ });
                    });
                  });
                });
              }
              chain.then(function () {
                m.close();
                toast(subCount ? '保存成功(含 ' + subCount + ' 条子记录)' : '保存成功');
                afterChange();
              });
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
    var filterBtn = null;
    // 工具栏(只放操作按钮,不放筛选)
    var toolbar = h('div', { class: 'toolbar' });

    // 筛选面板构建函数
    function buildFilterField(f) {
      var wrap = h('div', { class: 'filter-field' });
      wrap.appendChild(h('label', { text: f.label }));
      var el;
      if (f.type === 'select') {
        var fopts = (typeof f.options === 'function' ? f.options() : f.options) || [];
        var ddItems = [{ v: '', l: '全部' }].concat(fopts.map(function (o) {
          if (o && o.v !== undefined) {
            return { v: String(o.v), l: String(o.l !== undefined ? o.l : o.v) };
          }
          return { v: String(o), l: String(o) };
        }));
        el = makeDropdown(ddItems, '全部');
        el.setAttribute('data-fkey', f.key);
        el.addEventListener('change', function () { state.page = 1; load(); });
      } else if (f.type === 'date') {
        el = makeDateField();
        el.setAttribute('data-fkey', f.key);
        el.addEventListener('change', function () { state.page = 1; load(); });
      } else {
        el = h('input', { type: 'text', 'data-fkey': f.key, placeholder: f.placeholder || '回车搜索' });
        el.addEventListener('keydown', function (e) {
          if (e.key === 'Enter') { state.page = 1; load(); }
        });
      }
      wrap.appendChild(el);
      return wrap;
    }

    // 右侧筛选面板
    var hasFilters = (cfg.filters && cfg.filters.length) || (cfg.moreFilters && cfg.moreFilters.length);
    var filterPanel = h('div', { class: 'filter-panel' });
    var filterCount = h('span', { class: 'badge blue', text: '0' });

    if (hasFilters) {
      var inner = h('div', { class: 'filter-panel-inner' });
      inner.appendChild(h('h4', { text: '筛选条件' }));
      (cfg.filters || []).forEach(function (f) {
        inner.appendChild(buildFilterField(f));
      });
      (cfg.moreFilters || []).forEach(function (f) {
        inner.appendChild(buildFilterField(f));
      });
      // 操作按钮
      inner.appendChild(h('div', { class: 'filter-actions' },
        h('button', {
          class: 'btn btn-primary', text: '查询',
          onclick: function () { state.page = 1; load(); }
        }),
        h('button', {
          class: 'btn btn-ghost', text: '重置',
          onclick: function () {
            $all('[data-fkey]', filterPanel).forEach(function (el) {
              el.value = '';
            });
            state.page = 1; load();
          }
        })));
      filterPanel.appendChild(inner);

      // 级联筛选(如 人员类别→军衔):父项选定后,子项选项过滤为其下级
      (cfg.filters || []).concat(cfg.moreFilters || []).forEach(function (f) {
        if (!f.depends) return;
        var childEl = filterPanel.querySelector('[data-fkey="' + f.key + '"]');
        var parentEl = filterPanel.querySelector('[data-fkey="' + f.depends.field + '"]');
        if (!childEl || !parentEl || !childEl.setOptions) return;
        var fullOpts = function () { return (typeof f.options === 'function' ? f.options() : f.options) || []; };
        parentEl.addEventListener('change', function () {
          var pv = String(parentEl.value || '');
          if (!pv) { childEl.setOptions(fullOpts()); return; }
          var parentItem = null;
          dictItems(f.depends.parentDict || f.depends.dict).forEach(function (d) {
            if (d.dictLabel === pv) parentItem = d;
          });
          childEl.setOptions(parentItem
            ? dictItems(f.depends.dict).filter(function (d) {
                return d.parentId && String(d.parentId) === String(parentItem.id) && d.enabled !== false;
              }).map(function (d) { return d.dictLabel; })
            : []);
        });
      });

      // 垂直标签按钮(固定在右侧边缘居中);先清理旧按钮避免累积
      $all('.filter-tab-btn', document).forEach(function (b) { b.remove(); });
      filterBtn = h('div', {
        class: 'filter-tab-btn', text: '筛 选',
        onclick: function () {
          var opening = !filterPanel.classList.contains('open');
          var main = root.querySelector('.content-main');
          if (opening) {
            // 布局瞬时切换,transform 滑入(合成层,不逐帧重排表格)
            filterPanel.classList.add('open');
            slideFrom(filterPanel, 270);
            if (main) { main.classList.add('filter-padding'); slideFrom(main, 270); }
          } else {
            filterPanel.style.transition = 'transform .3s cubic-bezier(.4,0,.2,1)';
            filterPanel.style.transform = 'translateX(100%)';
            if (main) { main.classList.remove('filter-padding'); slideFrom(main, -270); }
            setTimeout(function () {
              filterPanel.classList.remove('open');
              filterPanel.style.transition = '';
              filterPanel.style.transform = '';
            }, 310);
          }
          this.classList.toggle('panel-open', opening);
          this.textContent = opening ? '收 起' : '筛 选';
        }
      });
      document.body.appendChild(filterBtn);
    }

    function getActiveCount() {
      var n = 0;
      $all('[data-fkey]', filterPanel).forEach(function (el) {
        if (String(el.value || '').trim()) { n++; }
      });
      return n;
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
      var pm = showProgressModal('导入 Excel');
      pm.setStatus('正在上传 ' + f.name + '(' + (f.size / 1024).toFixed(1) + 'KB)…');
      postFormWithProgress(cfg.endpoint + '/import-excel', fd,
        function (pct) {
          pm.setProgress(pct * 0.5); // 上传占前半
          pm.setStatus('正在上传… ' + pct + '%');
        }
      ).then(function (r) {
        pm.setProgress(80);
        pm.setStatus('正在处理数据…');
        var msg;
        if (r.subTotal !== undefined) {
          msg = '导入完成:人员 ' + r.mainCount + ' 条,子记录 ' + r.subTotal + ' 条';
          var parts = [];
          for (var k in r) {
            if (k !== 'mainCount' && k !== 'subTotal' && r[k] > 0) {
              parts.push(k + ' ' + r[k] + '条');
            }
          }
          if (parts.length) { msg += '(' + parts.join(',') + ')'; }
          if (r.cardWarn > 0) { msg += ';注意:' + r.cardWarn + ' 条身份证号不是18位,请核对'; }
        } else {
          msg = '导入完成:新增 ' + r.added + ' 条,更新 ' + r.updated + ' 条,失败 ' + r.failed + ' 条';
          if (r.dictAdded > 0) {
            msg += ',自动新增字典项 ' + r.dictAdded + ' 个';
          }
          if (r.errors && r.errors.length) {
            msg += ';失败明细:' + r.errors.join('、');
          }
        }
        pm.setProgress(100);
        pm.setStatus('导入完成');
        pm.close();
        toast(msg, r.failed ? 'info' : 'ok');
        load();
      }).catch(function (e) {
        pm.close();
        toast(e.message, 'err');
      });
    });
    toolbar.appendChild(importInput);
    {
      toolbar.appendChild(h('button', {
        class: 'btn btn-ghost', text: '下载模板',
        title: cfg.key === 'personnel' ? '下载多 Sheet 导入模板(含人员/任职经历/奖惩/家庭成员等)' : '下载本模块导入模板(第一行为列头,含示例行)',
        onclick: function () {
          var pm = showProgressModal('下载模板');
          pm.setStatus('正在生成模板…');
          apiBlobWithProgress(cfg.endpoint + '/template', function (pct) {
            pm.setProgress(pct);
          }).then(function (r) {
            pm.setProgress(100);
            pm.setStatus('下载完成');
            downloadBlob(r.blob, r.filename);
            setTimeout(function () { pm.close(); }, 500);
          }).catch(function (e) {
            pm.close();
            toast(e.message, 'err');
          });
        }
      }));
    }
    toolbar.appendChild(h('button', {
      class: 'btn btn-ghost', text: '导入 Excel',
      title: '导入 Excel(第一行为列头,建议用导出的文件作模板)',
      onclick: function () { importInput.click(); }
    }));
    // 导出:仅导出勾选的记录;未勾选时提示;带进度条
    toolbar.appendChild(h('button', {
      class: 'btn btn-ghost', text: '导出 Excel(含子表)',
      onclick: function () {
        var ids = Object.keys(state.selected);
        if (!ids.length) {
          toast('请先勾选要导出的数据(可点表格左上角全选本页)', 'err');
          return;
        }
        var pm = showProgressModal('导出 Excel');
        pm.setStatus('正在导出 ' + ids.length + ' 条数据…');
        apiBlobWithProgress(cfg.endpoint + '/export?ids=' + ids.join(','),
          function (pct) {
            pm.setProgress(pct * 0.9); // 留 10% 给处理
            pm.setStatus('正在下载… ' + pct + '%');
          }
        ).then(function (r) {
          pm.setProgress(100);
          pm.setStatus('导出完成');
          downloadBlob(r.blob, r.filename);
          setTimeout(function () { pm.close(); }, 600);
        }).catch(function (e) {
          pm.close();
          toast(e.message, 'err');
        });
      }
    }));
    var actions = {
      load: load,
      openNew: function () { openForm(null); },
      getFilters: function () {
        var f = collectFilters();
        Object.keys(f).forEach(function (k) { if (f[k] === '') delete f[k]; });
        return f;
      }
    };
    if (cfg.toolbarExtra) {
      cfg.toolbarExtra(toolbar, actions);
    }
    toolbar.appendChild(h('button', {
      class: 'btn btn-primary',
      text: '＋ ' + (cfg.addLabel || '新增' + cfg.title.replace(/管理$/, '')),
      onclick: function () { openForm(null); }
    }));
    // 筛选按钮已改为固定在右侧边缘的垂直标签(document.body 上),不在工具栏

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

    var panel = h('div', { class: 'panel' }, h('div', { class: 'table-wrap' }, table), sentinel);
    // 布局:左侧主内容 + 右侧筛选面板
    var mainArea = h('div', { class: 'content-main' },
      h('div', { class: 'panel', style: 'margin-bottom:12px' }, toolbar),
      panel);
    var layout = h('div', { class: 'content-layout' }, mainArea);
    if (hasFilters) {
      layout.appendChild(filterPanel);
    }
    root.appendChild(layout);
    container.appendChild(root);

    // 无限滚动:观察 sentinel 进入视口时加载更多
    var io = new IntersectionObserver(function (entries) {
      if (entries[0].isIntersecting) {
        loadMore();
      }
    }, { rootMargin: '200px' });
    io.observe(sentinel);

    // 回到顶部/底部按钮组(平滑滚动)
    $all('.scroll-btns', document).forEach(function (b) { b.remove(); });
    var scrollEl = root.closest('.content') || root.parentElement;
    function smoothScroll(top) {
      if (!scrollEl) return;
      scrollEl.scrollTo({ top: top, behavior: 'smooth' });
    }
    var btns = h('div', { class: 'scroll-btns' },
      h('div', {
        class: 'scroll-btn', title: '回到顶部',
        onclick: function () { smoothScroll(0); }
      }, h('span', { text: '⬆' })),
      h('div', {
        class: 'scroll-btn', title: '跳到底部',
        onclick: function () { if (scrollEl) smoothScroll(scrollEl.scrollHeight); }
      }, h('span', { text: '⬇' })));
    document.body.appendChild(btns);

    syncBatchBtn();
    load();
  }

  return { render: render };
}

/* ================= 各模块配置 ================= */

/** 通用详情弹窗:按模块表单定义分栏展示全部字段(人员模块附带照片) */
function openDetailModal(cfg, row, act) {
  var grid = h('div', { class: 'detail-grid' });
  var SUB_KINDS = { '任职经历': 'position-history', '奖惩情况': 'reward-punish', '家庭信息': 'family' };
  (cfg.form || []).forEach(function (f) {
    if (f.section) {
      grid.appendChild(h('div', { class: 'form-section', text: f.section }));
      if (SUB_KINDS[f.section] && row.id) {
        var holder = h('div', { style: 'grid-column:1/-1' });
        grid.appendChild(holder);
        (function (title, kind) {
          GET('/api/sub/' + kind + '?personnelId=' + row.id).then(function (list) {
            (list || []).forEach(function (r) {
              var line;
              if (title === '任职经历') {
                line = esc((r.start_date || '—') + ' 至 ' + (r.end_date || '今') + '　'
                  + r.place + '　任 ' + r.post);
              } else if (title === '家庭信息') {
                var age = calcAge(r.birth_date);
                line = esc((r.relationship || '') + '　' + r.name
                  + (r.gender ? '(' + r.gender + (age ? ',' + age + '岁' : '') + ')' : '')
                  + (r.work_info ? '　' + r.work_info : '')
                  + (r.phone ? '　电话 ' + r.phone : ''));
              } else {
                line = esc((r.occur_date || '—') + '　') + badge(r.kind)
                  + esc('　' + r.reason + '　' + r.content);
              }
              holder.appendChild(h('div', { class: 'detail-sub-line', html: line }));
            });
            if (!list || !list.length) {
              holder.appendChild(h('div', { class: 'detail-sub-line', text: '暂无记录' }));
            }
          }).catch(function () { });
        })(f.section, SUB_KINDS[f.section]);
      }
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

/** 人员子记录面板:表单标签页内的可编辑列表(任职经历/奖惩情况) */
/** 从身份证号解析出生日期与性别 */
function parseIdCard(idCard) {
  var id = String(idCard || '').trim().toUpperCase();
  var m18 = /^\d{17}[0-9X]$/.test(id);
  var m15 = /^\d{15}$/.test(id);
  if (!m18 && !m15) {
    return null;
  }
  var y, mo, d, genderDigit;
  if (m18) {
    y = +id.slice(6, 10); mo = +id.slice(10, 12); d = +id.slice(12, 14);
    genderDigit = +id.charAt(16);
  } else {
    y = 1900 + +id.slice(6, 8); mo = +id.slice(8, 10); d = +id.slice(10, 12);
    genderDigit = +id.charAt(14);
  }
  var dt = new Date(y, mo - 1, d);
  if (dt.getFullYear() !== y || dt.getMonth() !== mo - 1 || dt.getDate() !== d) {
    return null;
  }
  function p2(n) { return n < 10 ? '0' + n : '' + n; }
  return {
    birthDate: y + '-' + p2(mo) + '-' + p2(d),
    gender: genderDigit % 2 === 1 ? '男' : '女'
  };
}

/** 根据出生日期计算年龄 */
function calcAgeAt(birthDate, atDate) {
  if (!birthDate) { return ''; }
  var m = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(birthDate);
  if (!m) { return ''; }
  var b = new Date(+m[1], +m[2] - 1, +m[3]);
  var now;
  if (atDate) {
    var m2 = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(atDate);
    now = m2 ? new Date(+m2[1], +m2[2] - 1, +m2[3]) : new Date();
  } else {
    now = new Date();
  }
  var age = now.getFullYear() - b.getFullYear();
  var mo = now.getMonth() - b.getMonth();
  if (mo < 0 || (mo === 0 && now.getDate() < b.getDate())) { age--; }
  return age >= 0 && age < 150 ? String(age) : '';
}

/** 军龄计算:自入伍日期至今的年数 */
function calcServiceYears(entryDate) {
  if (!entryDate) { return ''; }
  var m = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(entryDate);
  if (!m) { return ''; }
  var start = new Date(+m[1], +m[2] - 1, +m[3]);
  var now = new Date();
  var years = now.getFullYear() - start.getFullYear();
  var mo = now.getMonth() - start.getMonth();
  if (mo < 0 || (mo === 0 && now.getDate() < start.getDate())) { years--; }
  return years >= 0 && years < 100 ? String(years) : '';
}

function calcAge(birthDate) {
  if (!birthDate) { return ''; }
  var d = parseIdCard('000000' + birthDate.replace(/-/g, '') + '0');
  if (!d) {
    var m = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(birthDate);
    if (!m) { return ''; }
    d = { birthDate: birthDate };
  }
  var now = new Date();
  var b = m ? new Date(+m[1], +m[2] - 1, +m[3]) : null;
  if (!b || b.getFullYear() < 1900) { return ''; }
  var age = now.getFullYear() - b.getFullYear();
  var mo = now.getMonth() - b.getMonth();
  if (mo < 0 || (mo === 0 && now.getDate() < b.getDate())) { age--; }
  return age >= 0 && age < 150 ? String(age) : '';
}

var subUid = 0;

/**
 * 人员子记录面板:电子表格式行内编辑。
 * 双击单元格原地编辑(文本/日期/下拉),失焦即保存;
 * "添加"直接插入黄色空行,必填填完后自动入库(服务端模式)。
 */
function subRecordsPanel(kind, personnelId, opt) {
  var box = h('div', { style: 'grid-column: 1 / -1; padding: 2px 0 6px;' });
  var table = h('table', { class: 'grid sub-grid' });
  var localMode = !personnelId; // 新建人员:全部暂存,随人员一并入库
  var serverMode = !localMode;
  var rows = [];
  var renderTimer = null;

  function colByKey(k) {
    for (var i = 0; i < opt.columns.length; i++) {
      if (opt.columns[i].key === k) {
        return opt.columns[i];
      }
    }
    return null;
  }

  function rowValid(r) {
    for (var i = 0; i < opt.columns.length; i++) {
      var c = opt.columns[i];
      if (!c.virtual && !c.auto && c.required && !String(r[c.key] || '').trim()) {
        return false;
      }
    }
    return true;
  }

  function snapshot(r) {
    var o = {};
    for (var k in r) {
      if (k.indexOf('__') !== 0) {
        o[k] = r[k];
      }
    }
    return o;
  }

  function load() {
    if (localMode) {
      render();
      return;
    }
    GET('/api/sub/' + kind + '?personnelId=' + personnelId).then(function (list) {
      rows = (list || []).map(function (r) {
        r.__id = 's' + r.id;
        r.__pending = false;
        return r;
      });
      render();
    }).catch(function (e) { toast(e.message, 'err'); });
  }

  function fmtCell(r, c) {
    if (c.virtual) {
      if (c.key === '__age') {
        var age = calcAge(r.birth_date);
        return age ? esc(age) : '<span style="color:#c6cfda">—</span>';
      }
      return '';
    }
    if (c.auto) {
      // 身份证自动填充列:值来自身份证解析,背景淡蓝标识
      var v = r[c.key] || '';
      return v ? '<span style="color:#1a5d3a">' + esc(v) + '</span>' : '<span style="color:#c6cfda">—</span>';
    }
    var v2 = r[c.key] || '';
    if (!String(v2).trim()) {
      return c.required ? '<span class="cell-empty-hint">*</span>' : '<span style="color:#c6cfda">—</span>';
    }
    if (c.render) {
      return c.render(r);
    }
    return esc(v2);
  }

  function render() {
    var head = '<thead><tr>'
      + opt.columns.map(function (c) {
        return '<th' + (c.w ? ' style="width:' + c.w + 'px"' : '') + '>' + esc(c.label) + '</th>';
      }).join('')
      + '<th style="width:56px">操作</th></tr></thead>';
    var body = rows.map(function (r) {
      var cls = r.__pending ? ' class="pending-row"' : '';
      var tds = opt.columns.map(function (c) {
        var tip = String(r[c.key] || '').trim();
        return '<td data-row="' + r.__id + '" data-col="' + c.key + '" title="' + esc(tip) + '">'
          + fmtCell(r, c) + '</td>';
      }).join('');
      return '<tr' + cls + '>' + tds
        + '<td class="actions"><button class="btn-link danger" data-del="' + r.__id + '">删除</button></td></tr>';
    }).join('');
    if (!rows.length) {
      body = '<tr><td colspan="' + (opt.columns.length + 1) + '" class="empty-row">暂无记录,点上方按钮添加</td></tr>';
    }
    table.innerHTML = head + '<tbody>' + body + '</tbody>';
  }

  function deferredRender() {
    clearTimeout(renderTimer);
    renderTimer = setTimeout(render, 0);
  }

  function openEditor(r, c) {
    if (c.virtual || c.auto) {
      return; // 虚拟列(年龄)和自动填充列(性别/出生日期)不可手动编辑
    }
    var td = table.querySelector('td[data-row="' + r.__id + '"][data-col="' + c.key + '"]');
    if (!td) {
      return;
    }
    td.innerHTML = '';
    td.classList.add('editing');
    var editor;
    if (c.type === 'date') {
      editor = makeDateField();
      if (r[c.key]) {
        editor.value = r[c.key];
      }
      editor.addEventListener('change', function () { commit(r, c, editor.value); });
    } else if (c.type === 'select') {
      var ddOpts = typeof c.options === 'function' ? c.options() : (c.options || []);
      editor = makeDropdown(ddOpts, '请选择');
      if (r[c.key]) {
        editor.value = r[c.key];
      }
      editor.addEventListener('change', function () { commit(r, c, editor.value); });
      var btn = editor.querySelector('.dd-btn');
      if (btn) {
        btn.addEventListener('blur', function () {
          setTimeout(function () { commit(r, c, r[c.key]); }, 150);
        });
      }
    } else {
      editor = h('input', { type: 'text', style: 'width:100%' });
      editor.value = r[c.key] || '';
      editor.addEventListener('blur', function () { commit(r, c, editor.value); });
      editor.addEventListener('keydown', function (e) {
        if (e.key === 'Enter') { editor.blur(); }
        if (e.key === 'Tab') {
          e.preventDefault();
          editor.blur();
          var cols = opt.columns.filter(function (x) { return !x.virtual && !x.auto; });
          var ci = cols.indexOf(c);
          var next = cols[ci + 1] || cols[0];
          if (next) { setTimeout(function () { openEditor(r, next); }, 50); }
        }
      });
    }
    td.appendChild(editor);
    var focusEl = editor.tagName === 'INPUT' ? editor : editor.querySelector('input,.dd-btn');
    if (focusEl) {
      setTimeout(function () { focusEl.focus(); if (focusEl.select) { focusEl.select(); } }, 0);
    }
  }

  /** 全部字段为空的行自动移除(点了添加又没填任何内容) */
  function autoRemoveIfEmpty(r) {
    var allEmpty = true;
    opt.columns.forEach(function (c) {
      if (!c.virtual && String(r[c.key] || '').trim()) {
        allEmpty = false;
      }
    });
    if (allEmpty) {
      rows = rows.filter(function (x) { return x !== r; });
      return true;
    }
    return false;
  }

  function commit(r, c, value) {
    value = String(value == null ? '' : value).trim();
    if (String(r[c.key] || '') === value) {
      autoRemoveIfEmpty(r);
      deferredRender();
      return;
    }
    r[c.key] = value;
    // 体能成绩失焦:按矩阵自动计算等级分数(需要知道本人年龄)
    if (c.key === 'score' && value && opt.age !== undefined) {
      var age = calcAgeAt(opt.birthday, r.test_date) || opt.age;
      var g = calcFitnessGrade(r.item || '', value, age);
      if (g) { r.points = g; r.grade = gradeForPoints(g); }
    }
    // 考核日期变更后也重算(年龄可能跨档)
    if (c.key === 'test_date' && r.score) {
      var age2 = calcAgeAt(opt.birthday, value) || opt.age;
      var g2 = calcFitnessGrade(r.item || '', r.score, age2);
      if (g2) { r.points = g2; r.grade = gradeForPoints(g2); }
    }
    // 身份证号失焦:自动填充性别和出生日期
    if (c.key === 'id_card' && value) {
      var parsed = parseIdCard(value);
      if (parsed) {
        r.gender = parsed.gender;
        r.birth_date = parsed.birthDate;
      }
    }
    saveRow(r);
  }

  function saveRow(r) {
    if (localMode) {
      deferredRender();
      return;
    }
    var body = snapshot(r);
    if (r.__pending) {
      if (!rowValid(r)) {
        if (autoRemoveIfEmpty(r)) {
          deferredRender();
          return;
        }
        deferredRender(); // 有部分内容但必填未齐,黄色行挂着
        return;
      }
      body.personnelId = personnelId;
      POST('/api/sub/' + kind, body).then(function (saved) {
        if (saved && saved.id) {
          r.id = saved.id;
          r.__id = 's' + saved.id;
        }
        r.__pending = false;
        deferredRender();
      }).catch(function (e) {
        toast(e.message, 'err');
        deferredRender();
      });
    } else {
      var backup = r.__backup || body;
      r.__backup = snapshot(r);
      PUT('/api/sub/' + kind + '/' + r.id, body).then(function () {
        r.__backup = null;
        deferredRender();
      }).catch(function (e) {
        toast(e.message, 'err');
        for (var k in backup) {
          r[k] = backup[k];
        }
        deferredRender();
      });
    }
  }

  table.addEventListener('dblclick', function (e) {
    var td = e.target.closest('td[data-row][data-col]');
    if (!td) {
      return;
    }
    var r = rows.filter(function (x) { return x.__id === td.getAttribute('data-row'); })[0];
    var c = colByKey(td.getAttribute('data-col'));
    if (r && c) {
      openEditor(r, c);
    }
  });

  table.addEventListener('click', function (e) {
    var b = e.target.closest('button[data-del]');
    if (!b) {
      return;
    }
    var rid = b.getAttribute('data-del');
    var r = rows.filter(function (x) { return x.__id === rid; })[0];
    if (!r) {
      return;
    }
    if (r.__pending || localMode) {
      rows = rows.filter(function (x) { return x !== r; });
      render();
      return;
    }
    confirmBox('确定删除这条' + opt.name + '吗?', true).then(function (yes) {
      if (!yes) {
        return;
      }
      DEL('/api/sub/' + kind + '/' + r.id).then(function () {
        rows = rows.filter(function (x) { return x !== r; });
        render();
        toast('已删除');
      }).catch(function (err) { toast(err.message, 'err'); });
    });
  });

  box.appendChild(h('div', { style: 'margin-bottom:10px;display:flex;align-items:center;gap:12px' },
    h('button', {
      class: 'btn btn-ghost btn-sm', text: '＋ 添加' + opt.name,
      onclick: function () {
        var r = { __id: 'l' + (--subUid), __pending: serverMode };
        opt.columns.forEach(function (c) { r[c.key] = ''; });
        rows.push(r);
        render();
        var firstReq = opt.columns.filter(function (c) { return c.required; })[0] || opt.columns[0];
        setTimeout(function () { openEditor(r, firstReq); }, 30);
      }
    }),
    h('span', { style: 'color:#a6b3c4;font-size:12px', text: '黄色行=必填未填完' })));
  box.appendChild(h('div', { class: 'table-wrap' }, table));

  box.__staged = function () {
    return rows.map(function (r) { return snapshot(r); });
  };
  load();
  return box;
}

var POSITION_SUB = {
  name: '任职经历', kind: 'position-history',
  columns: [
    { key: 'start_date', label: '开始日期', type: 'date', required: true, w: 105 },
    { key: 'end_date', label: '结束日期', type: 'date', w: 105 },
    { key: 'place', label: '任职地点', required: true },
    { key: 'post', label: '担任职务', required: true },
    { key: 'remark', label: '备注', w: 130 }
  ]
};

var FAMILY_RELATIONS = ['配偶', '子女', '父亲', '母亲', '岳父', '岳母', '兄弟', '姐妹', '其他'];

var FAMILY_SUB = {
  name: '家庭成员', kind: 'family',
  columns: [
    { key: 'relationship', label: '关系', type: 'select', options: FAMILY_RELATIONS, required: true, w: 62 },
    { key: 'name', label: '姓名', required: true, w: 70 },
    { key: 'id_card', label: '身份证号', w: 140 },
    { key: 'gender', label: '性别', w: 42, auto: true },
    { key: '__age', label: '年龄', w: 38, virtual: true },
    { key: 'political_status', label: '政治面貌', type: 'select', options: function () { return dictLabels('political_status'); }, w: 68 },
    { key: 'work_info', label: '工作单位及职务' },
    { key: 'phone', label: '联系电话', w: 100 },
    { key: 'remark', label: '备注', w: 90 }
  ]
};

var DRIVER_SUB = {
  name: '驾驶证', kind: 'driver',
  columns: [
    { key: 'license_type', label: '类型', type: 'select', options: ['军本', '地方本'], required: true, w: 60 },
    { key: 'license_no', label: '证号', required: true, w: 150 },
    { key: 'vehicle_class', label: '准驾车型', w: 80, placeholder: '如:B2、C1' },
    { key: 'issue_date', label: '初领日期', type: 'date', w: 95 },
    { key: 'expiry_date', label: '有效期至', type: 'date', w: 95 },
    { key: 'issuing_auth', label: '发证机关' },
    { key: 'license_status', label: '状态', type: 'select', options: function () { var l = dictLabels('license_status'); return l.length ? l : ['有效', '过期', '吊销']; }, w: 55 },
    { key: 'review_date', label: '年审日期', type: 'date', w: 90 },
    { key: 'remark', label: '备注', w: 90 }
  ]
};

var CARD_SUB = {
  name: '卡号', kind: 'card',
  columns: [
    { key: 'card_type', label: '卡类型', type: 'select', required: true, w: 110, options: function () { var l = dictLabels('card_type'); return l.length ? l : ['保障卡号', '保障卡银行卡号', '银行卡号', '其他']; } },
    { key: 'card_no', label: '卡号', required: true, w: 170 },
    { key: 'bank_name', label: '开户行', w: 110, placeholder: '如:工商银行' },
    { key: 'branch_name', label: '开户支行' },
    { key: 'open_date', label: '开户日期', type: 'date', w: 90 },
    { key: 'card_status', label: '卡状态', type: 'select', options: function () { var l = dictLabels('card_status'); return l.length ? l : ['正常', '挂失', '注销']; }, w: 55 },
    { key: 'remark', label: '备注', w: 90 }
  ]
};

var PVEHICLE_SUB = {
  name: '私家车', kind: 'pvehicle',
  columns: [
    { key: 'plate_no', label: '车牌号', required: true, w: 95 },
    { key: 'brand', label: '品牌型号', w: 100 },
    { key: 'color', label: '颜色', w: 55 },
    { key: 'purchase_date', label: '购置日期', type: 'date', w: 90 },
    { key: 'price', label: '价格(万)', w: 70 },
    { key: 'insurance_date', label: '保险到期', type: 'date', w: 90 },
    { key: 'vehicle_status', label: '状态', type: 'select', options: function () { var l = dictLabels('vehicle_status_sub'); return l.length ? l : ['正常', '出售', '报废']; }, w: 55 },
    { key: 'owner_name', label: '登记在谁名下', w: 100 },
    { key: 'remark', label: '备注' }
  ]
};

var HOUSING_SUB = {
  name: '住房', kind: 'housing',
  columns: [
    { key: 'housing_type', label: '类型', type: 'select', options: ['自有住房', '按揭住房', '部队公寓', '租房', '其他'], w: 75 },
    { key: 'address', label: '地址', required: true, w: 180 },
    { key: 'area', label: '面积', w: 55 },
    { key: 'house_nature', label: '性质', type: 'select', options: function () { var l = dictLabels('house_nature'); return l.length ? l : ['商品房', '房改房', '自建房', '其他']; }, w: 65 },
    { key: 'has_cert', label: '房产证', type: 'select', options: function () { var l = dictLabels('has_cert'); return l.length ? l : ['有', '无']; }, w: 50 },
    { key: 'loan_status', label: '贷款', type: 'select', options: function () { var l = dictLabels('loan_status'); return l.length ? l : ['全款', '按揭中', '已还清']; }, w: 60 },
    { key: 'purchase_date', label: '购置时间', type: 'date', w: 90 },
    { key: 'owner_name', label: '产权人', w: 80 },
    { key: 'remark', label: '备注' }
  ]
};

var FITNESS_SUB = {
  name: '体能考核', kind: 'fitness',
  columns: [
    { key: 'item', label: '考核项目', required: true, w: 110 },
    { key: 'score', label: '成绩', required: true, w: 90 },
    { key: 'points', label: '分数', w: 60, auto: true },
    { key: 'grade', label: '评定', w: 70, auto: true, render: function (r) {
        return r.grade ? '<span class="badge green">' + esc(r.grade) + '</span>' : '<span style="color:#c6cfda">—</span>';
      } },
    { key: 'test_date', label: '考核日期', type: 'date', required: true, w: 100 }
  ]
};

var REWARD_SUB = {
  name: '奖惩记录', kind: 'reward-punish',
  columns: [
    { key: 'kind', label: '类型', type: 'select', options: ['奖励', '处分'], required: true, w: 85, render: function (r) { return badge(r.kind); } },
    { key: 'occur_date', label: '日期', type: 'date', required: true, w: 105 },
    { key: 'reason', label: '事由', required: true },
    { key: 'content', label: '内容', required: true },
    { key: 'remark', label: '备注', w: 130 }
  ]
};

/** 批量导入照片:选目录(含子目录),按文件名中的身份证号匹配人员并设置照片 */
function batchImportPhotos(fileList) {
  var files = [];
  for (var i = 0; i < fileList.length; i++) {
    var f = fileList[i];
    var ext = (f.name.split('.').pop() || '').toLowerCase();
    if (ext === 'jpg' || ext === 'jpeg' || ext === 'png') {
      files.push(f);
    }
  }
  if (!files.length) {
    toast('所选目录中没有 jpg/png 图片', 'err');
    return;
  }

  var bar = h('div', { class: 'pg-bar' }, h('div', { class: 'pg-fill', style: 'width:0%' }));
  var status = h('div', { style: 'margin:10px 0 4px;font-size:13px;color:#66788c', text: '正在读取人员列表…' });
  var summary = h('div', { style: 'font-size:13px;line-height:1.9;white-space:pre-wrap' });
  var m = openModal({
    title: '批量导入照片',
    body: h('div', {}, status, bar, summary),
    footer: [h('button', { class: 'btn btn-ghost', text: '关闭', onclick: function () { m.close(); } })]
  });

  function extractId(name) {
    var m18 = /(^|\D)(\d{17}[0-9Xx])(?=\D|$)/.exec(name);
    if (m18) {
      return m18[2].toUpperCase();
    }
    var m15 = /(^|\D)(\d{15})(?=\D|$)/.exec(name);
    if (m15) {
      return m15[2];
    }
    return null;
  }

  function readAsDataURL(file) {
    return new Promise(function (resolve, reject) {
      var reader = new FileReader();
      reader.onload = function () { resolve(reader.result); };
      reader.onerror = function () { reject(new Error('读取失败')); };
      reader.readAsDataURL(file);
    });
  }

  GET('/api/personnel?page=1&size=100000').then(function (pageData) {
    var byCard = {};
    (pageData && pageData.list ? pageData.list : []).forEach(function (p) {
      var card = (p.idCard || '').trim().toUpperCase();
      if (card) {
        byCard[card] = p;
      }
    });

    var okCount = 0, overwriteCount = 0, missCount = 0, failCount = 0;
    var missed = [];
    var failed = [];

    var chain = Promise.resolve();
    var idx = 0;
    files.forEach(function (f) {
      chain = chain.then(function () {
        var fid = extractId(f.name);
        var person = fid ? byCard[fid] : null;
        if (!person) {
          missCount++;
          if (missed.length < 10) {
            missed.push(f.name);
          }
          return null;
        }
        if (f.size > 5 * 1024 * 1024) {
          failCount++;
          failed.push(f.name + '(超过5MB)');
          return null;
        }
        return readAsDataURL(f).then(function (dataURL) {
          return PUT('/api/personnel/' + person.id, { photoBase64: dataURL }).then(function () {
            if (person.photo) {
              overwriteCount++;
            }
            okCount++;
          });
        }).catch(function () {
          failCount++;
          failed.push(f.name);
        });
      }).then(function () {
        idx++;
        status.textContent = '进度:' + idx + ' / ' + files.length;
        bar.firstChild.style.width = Math.round(idx / files.length * 100) + '%';
      });
    });

    chain.then(function () {
      var text = '完成:成功设置 ' + okCount + ' 张照片'
        + (overwriteCount ? '(其中覆盖旧照 ' + overwriteCount + ' 张)' : '');
      if (missCount) {
        text += '\n未匹配到人员:' + missCount + ' 个文件(文件名需含身份证号)';
        missed.forEach(function (n) { text += '\n  · ' + n; });
      }
      if (failCount) {
        text += '\n失败:' + failCount + ' 个';
        failed.slice(0, 5).forEach(function (n) { text += '\n  · ' + n; });
      }
      summary.textContent = text;
      status.textContent = '全部处理完毕';
      toast('照片导入完成:成功 ' + okCount + ',未匹配 ' + missCount + ',失败 ' + failCount,
        failCount || missCount ? 'info' : 'ok');
    });
  }).catch(function (e) {
    status.textContent = '读取人员列表失败:' + e.message;
  });
}

/** 体能评定标准:[{label,op,min}] 按min降序;不及格为兜底 */

var TREND_COLORS = ['#2f6fd6', '#e8743b', '#2ea44f', '#9333ea', '#d9463e', '#0d9488', '#b26a00'];

/** 本次考核弹窗:某人某天的全部项目(合计/平均) */
function openFitnessSession(rw, act) {
  var body = h('div');
  var m = openModal({
    title: '本次考核 · ' + (rw.personnelName || '') + ' ' + (rw.testDate || ''),
    body: body,
    footer: [
      h('button', { class: 'btn btn-ghost', text: '编辑本条', onclick: function () { m.close(); act.edit(); } }),
      h('button', { class: 'btn btn-primary', text: '体能档案', onclick: function () { m.close(); openPersonProfile(rw, act); } }),
      h('button', { class: 'btn btn-ghost', text: '关闭', onclick: function () { m.close(); } })
    ]
  });
  body.appendChild(h('div', { class: 'loading-inline', text: '加载中…' }));
  var q = '/api/fitness?size=100';
  if (rw.idCard) { q += '&idCard=' + encodeURIComponent(rw.idCard); }
  else { q += '&personnelId=' + rw.personnelId; }
  if (rw.testDate) { q += '&startDate=' + rw.testDate + '&endDate=' + rw.testDate; }
  GET(q).then(function (d) {
    var rows = (d && d.list) || [];
    var total = 0;
    body.innerHTML = '';
    var table = h('table', { class: 'grid', style: 'width:100%' });
    var thead = h('thead', {}, h('tr', {},
      h('th', { text: '考核项目' }), h('th', { text: '成绩' }), h('th', { text: '分数' }),
      h('th', { text: '评定' }), h('th', { text: '备注' })));
    var tbody = h('tbody');
    rows.forEach(function (x) {
      total += Number(x.points) || 0;
      tbody.appendChild(h('tr', {},
        h('td', { text: x.item || '' }),
        h('td', { text: x.score || '' }),
        h('td', { text: (x.points !== null && x.points !== undefined && x.points !== '') ? String(x.points) : '—' }),
        h('td', { html: badge(x.grade) }),
        h('td', { text: x.remark || '' })));
    });
    table.appendChild(thead);
    table.appendChild(tbody);
    body.appendChild(h('div', { class: 'table-wrap', style: 'max-height:48vh;overflow:auto' }, table));
    body.appendChild(h('div', { style: 'margin-top:10px;display:flex;gap:18px;font-size:13px;color:#445262' },
      h('span', { text: '共 ' + rows.length + ' 个项目' }),
      h('b', { text: '合计分数:' + (Math.round(total * 10) / 10) + ' 分' }),
      h('span', { text: '平均:' + (rows.length ? (Math.round(total / rows.length * 10) / 10) : 0) + ' 分' })));
  }).catch(function (e) {
    body.innerHTML = '';
    body.appendChild(h('div', { class: 'empty-row', text: e.message }));
  });
}

/** 体能档案大窗口:左侧3D人体(最新身高体重) + 右侧基本信息 + 下方各项目成绩走势(时间范围可快捷/自定义) */
function openPersonProfile(rw, act) {
  var body = h('div');
  var m = openModal({
    title: '体能档案 · ' + (rw.personnelName || ''),
    body: body, wide: true, xwide: true,
    footer: [
      h('button', { class: 'btn btn-ghost', text: '编辑本条', onclick: function () { m.close(); act.edit(); } }),
      h('button', { class: 'btn btn-ghost', text: '本次考核', onclick: function () { m.close(); openFitnessSession(rw, act); } }),
      h('button', { class: 'btn btn-primary', text: '关闭', onclick: function () { m.close(); } })
    ]
  });
  body.appendChild(h('div', { class: 'loading-inline', text: '加载中…' }));
  var fq = '/api/fitness?size=500';
  if (rw.idCard) { fq += '&idCard=' + encodeURIComponent(rw.idCard); }
  else { fq += '&personnelId=' + rw.personnelId; }
  var pq = rw.idCard
    ? GET('/api/personnel?idCard=' + encodeURIComponent(rw.idCard)).then(function (d) { return (d && d.list && d.list.length) ? d.list[0] : null; })
    : GET('/api/personnel/' + rw.personnelId);
  Promise.all([pq, GET(fq), GRADE_RULE ? Promise.resolve(null) : loadGradeRule(), BMI_STD ? Promise.resolve(null) : loadBmiStd()]).then(function (res) {
    var p = res[0] || {};
    var rows = (res[1] && res[1].list) || [];
    body.innerHTML = '';
    build(p, rows);
  }).catch(function (e) {
    body.innerHTML = '';
    body.appendChild(h('div', { class: 'empty-row', text: e.message }));
  });

  var allSeries = [];   // [{name,data:[{date,pts,raw,grade}]}] 每项目按日期升序
  var chartsBox = h('div');
  var chips = [];
  var range = { months: 0 };   // 0=全部

  function rangeStart(n) {     // 最近n个自然月(含本月)的第一天
    var t = new Date();
    var d = new Date(t.getFullYear(), t.getMonth() - (n - 1), 1);
    return d.getFullYear() + '-' + ('0' + (d.getMonth() + 1)).slice(-2) + '-01';
  }

  function setRange(n, btn) {
    range.months = n;
    chips.forEach(function (c) { c.style.background = ''; c.style.color = ''; c.style.borderColor = ''; });
    if (btn) { btn.style.background = 'var(--pri)'; btn.style.color = '#fff'; btn.style.borderColor = 'var(--pri)'; }
    renderCharts();
  }

  function build(p, rows) {
    var idc = p.idCard || rw.idCard || '';
    var birthday = p.birthday || (idc.length >= 14 ? idc.slice(6, 10) + '-' + idc.slice(10, 12) + '-' + idc.slice(12, 14) : '');
    function cell(label, value) {
      return h('div', {},
        h('div', { style: 'font-size:12px;color:#8a97a8', text: label }),
        h('div', { style: 'font-size:14px;font-weight:600;color:#1d2c40;word-break:break-all', text: (value === null || value === undefined || value === '') ? '—' : String(value) }));
    }
    var ageTxt = '';
    var am = /^(\d{4})-(\d{2})-(\d{2})/.exec(birthday || '');
    if (am) {
      var t = new Date();
      var age = t.getFullYear() - (+am[1]) - ((t.getMonth() + 1 < +am[2] || (t.getMonth() + 1 === +am[2] && t.getDate() < +am[3])) ? 1 : 0);
      if (age >= 0 && age < 130) { ageTxt = age + ' 岁'; }
    }
    var info = h('div', { style: 'flex:1;display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px 18px;align-content:start' },
      cell('姓名', p.name || rw.personnelName), cell('身份证号码', idc), cell('出生日期', birthday),
      cell('年龄', ageTxt), cell('入伍时间', p.entryDate), cell('人员类别', p.category),
      cell('军衔', p.rank), cell('职务', p.post), cell('所属单位', p.departmentName));
    body.appendChild(h('div', { style: 'display:flex;gap:18px;align-items:stretch' },
      h('div', { style: 'flex:none;width:300px;border:1px solid #edf1f6;border-radius:10px;padding:12px 10px;display:flex;justify-content:center' },
        buildBodyFigure(p.height, p.weight, p.gender)),
      info));

    // 按项目分组(带分数的参与走势)
    var byItem = {};
    rows.forEach(function (r) {
      if (!r.testDate || !r.item) { return; }
      var pts = (r.points === null || r.points === undefined || r.points === '') ? null : Number(r.points);
      if (pts === null || isNaN(pts)) { return; }
      (byItem[r.item] = byItem[r.item] || []).push({ date: r.testDate, pts: pts, raw: r.score, grade: r.grade });
    });
    allSeries = Object.keys(byItem).map(function (k) {
      byItem[k].sort(function (a, b) { return a.date < b.date ? -1 : 1; });
      return { name: k, data: byItem[k] };
    });

    // 时间范围:快捷(本月/近N月/全部) + 自定义最近N个月
    var chipDefs = [['本月', 1], ['近2月', 2], ['近3月', 3], ['近4月', 4], ['近5月', 5], ['近6月', 6], ['近12月', 12], ['全部', 0]];
    var custom = h('input', { type: 'number', min: '1', max: '120', placeholder: 'N', style: 'width:54px;height:26px;padding:0 6px;border:1px solid #c5cdd8;border-radius:4px;font-size:13px' });
    var bar = h('div', { style: 'display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin:6px 0 4px' },
      h('span', { style: 'font-size:13px;color:#66788c', text: '时间范围:' }));
    chipDefs.forEach(function (cd) {
      var b = h('button', {
        type: 'button', text: cd[0],
        style: 'height:26px;padding:0 12px;border:1px solid #c5cdd8;border-radius:13px;background:#fff;color:#445262;font-size:12px;cursor:pointer',
        onclick: function () { setRange(cd[1], b); }
      });
      chips.push(b);
      bar.appendChild(b);
    });
    bar.appendChild(h('span', { style: 'font-size:12px;color:#8a97a8;margin-left:6px', text: '自定义:最近' }));
    bar.appendChild(custom);
    bar.appendChild(h('span', { style: 'font-size:12px;color:#8a97a8', text: '个月' }));
    bar.appendChild(h('button', {
      type: 'button', text: '查看',
      style: 'height:26px;padding:0 12px;border:1px solid var(--pri);border-radius:4px;background:var(--pri);color:#fff;font-size:12px;cursor:pointer',
      onclick: function () {
        var n = parseInt(custom.value, 10);
        if (!n || n < 1) { n = 1; }
        if (n > 120) { n = 120; }
        setRange(n, null);
      }
    }));
    body.appendChild(h('div', { style: 'margin-top:16px;font-size:15px;font-weight:600;color:#1d2c40', text: '考核成绩与走势' }));
    body.appendChild(bar);
    body.appendChild(chartsBox);
    chips[chips.length - 1].click();   // 默认选中"全部"
  }

  function chartCard(titleEl, rightEl, chartEl) {
    return h('div', { style: 'border:1px solid #edf1f6;border-radius:10px;padding:12px;margin-top:12px' },
      h('div', { style: 'display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:8px' }, titleEl, rightEl),
      chartEl);
  }

  function renderCharts() {
    chartsBox.innerHTML = '';
    var start = range.months > 0 ? rangeStart(range.months) : '';
    var ser = allSeries.map(function (s) {
      return { name: s.name, data: start ? s.data.filter(function (x) { return x.date >= start; }) : s.data.slice() };
    }).filter(function (s) { return s.data.length > 0; });
    if (!ser.length) {
      chartsBox.appendChild(h('div', { class: 'empty-row', text: allSeries.length ? '该时间范围内没有带分数的考核记录' : '暂无带分数的考核记录' }));
      return;
    }
    ser.sort(function (a, b) {   // 最近有成绩的项目排前面
      var la = a.data[a.data.length - 1].date, lb = b.data[b.data.length - 1].date;
      return la < lb ? 1 : la > lb ? -1 : b.data.length - a.data.length;
    });
    function chartData(s) { return { name: s.name, data: s.data.map(function (x) { return { date: x.date, score: x.pts }; }) }; }
    function r1(v) { return Math.round(v * 10) / 10; }
    // 总览:全部项目一张图对比
    chartsBox.appendChild(chartCard(
      h('div', { style: 'font-size:15px;font-weight:600;color:#1d2c40', text: '全部项目总览' }),
      h('div', { style: 'font-size:12px;color:#8a97a8', text: '悬停圆点可查看明细' }),
      buildTrendChart(ser.map(chartData), { H: 300 })));
    // 每个项目一张小图 + 统计
    ser.forEach(function (s) {
      var pts = s.data.map(function (x) { return x.pts; });
      var sum = 0; pts.forEach(function (v) { sum += v; });
      var last = s.data[s.data.length - 1];
      chartsBox.appendChild(chartCard(
        h('div', { style: 'font-size:14px;font-weight:600;color:#1d2c40', text: s.name }),
        h('div', { style: 'display:flex;gap:14px;align-items:center;font-size:12.5px;color:#445262;flex-wrap:wrap' },
          h('span', { text: s.data.length + ' 次' }),
          h('span', { text: '最新 ' + last.date + ':' }),
          h('b', { style: 'color:#1d2c40', text: last.pts + ' 分' }),
          h('span', { html: badge(last.grade || gradeForPoints(last.pts)) }),
          h('span', { text: '成绩 ' + (last.raw || '—') }),
          h('span', { text: '平均 ' + r1(sum / pts.length) + ' 分' }),
          h('span', { text: '最好 ' + r1(Math.max.apply(null, pts)) + ' 分' })),
        buildTrendChart([chartData(s)], { H: 190, legend: false })));
    });
  }
}

/** 伪3D人体模型:身高定高度、BMI定体宽,渐变模拟立体感;无数据给占位提示 */
function buildBodyFigure(hcm, wkg, gender) {
  var hc = Number(hcm), wc = Number(wkg);
  var has = hc > 80 && hc < 230 && wc > 25 && wc < 200;
  var box = h('div', { style: 'display:flex;flex-direction:column;align-items:center;width:100%' });
  if (!has) {
    box.appendChild(h('div', { style: 'width:230px;height:296px;border:2px dashed #d5dce6;border-radius:12px;display:flex;align-items:center;justify-content:center;text-align:center;padding:24px;color:#8a97a8;font-size:13px;line-height:1.8', text: '暂无身高体重数据,请在人员管理中填写' }));
    return box;
  }
  var bmi = wc / Math.pow(hc / 100, 2);
  var wf = Math.max(0.78, Math.min(1.45, bmi / 21.5));   // 体宽系数
  var k = 1.45;                                          // 1cm = 1.45px
  var figH = hc * k;
  var topY = 28, groundY = topY + figH;
  var cx = 142;
  var headR = Math.max(9, figH / 15);
  var headCy = topY + headR;
  var neckW = headR * 1.05, neckH = headR * 0.5;
  var shoulderY = headCy + headR + neckH - 2;
  var torsoH = figH * 0.335;
  var torsoW = Math.max(26, figH * 0.21 * wf);
  var hipY = shoulderY + torsoH;
  var armW = Math.max(8, figH * 0.06 * wf), armL = figH * 0.305;
  var legW = Math.max(10, figH * 0.082 * wf);
  var legTop = hipY - 4;
  var legH = groundY - legTop - 8;
  var s = [];
  s.push('<svg viewBox="0 0 250 366" style="width:100%;max-width:280px;display:block">');
  s.push('<defs>' +
    '<linearGradient id="pfUni" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#8fb07c"/><stop offset="0.45" stop-color="#5d7d4c"/><stop offset="1" stop-color="#39522d"/></linearGradient>' +
    '<linearGradient id="pfUniD" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#7d9a68"/><stop offset="0.45" stop-color="#4d6a3e"/><stop offset="1" stop-color="#2f4525"/></linearGradient>' +
    '<linearGradient id="pfSkin" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#f6d5b0"/><stop offset="0.5" stop-color="#e2b184"/><stop offset="1" stop-color="#c08a5a"/></linearGradient>' +
    '</defs>');
  // 左侧身高标尺 + 本人身高标记线
  s.push('<line x1="40" y1="' + topY + '" x2="40" y2="' + groundY + '" stroke="#c9d3de" stroke-width="1"/>');
  for (var cm = 140; cm <= 200; cm += 10) {
    var y = groundY - cm * k;
    if (y < topY - 4 || y > groundY) { continue; }
    s.push('<line x1="35" y1="' + y.toFixed(1) + '" x2="45" y2="' + y.toFixed(1) + '" stroke="#c9d3de" stroke-width="1"/>');
    if (cm % 20 === 0) {
      s.push('<text x="32" y="' + (y + 3).toFixed(1) + '" text-anchor="end" font-size="9" fill="#8a97a8">' + cm + '</text>');
    }
  }
  s.push('<line x1="40" y1="' + topY + '" x2="' + (cx - headR).toFixed(1) + '" y2="' + topY + '" stroke="'+PRI()+'" stroke-dasharray="4 3" stroke-width="1"/>');
  // 地面影子
  s.push('<ellipse cx="' + cx + '" cy="' + (groundY + 5) + '" rx="' + (figH * 0.155).toFixed(1) + '" ry="7" fill="#1d2c40" opacity="0.10"/>');
  // 腿 + 靴
  [-1, 1].forEach(function (side) {
    var lx = cx + (side < 0 ? -legW - 2 : 2);
    s.push('<rect x="' + lx.toFixed(1) + '" y="' + legTop.toFixed(1) + '" width="' + legW.toFixed(1) + '" height="' + legH.toFixed(1) + '" rx="' + (legW / 2).toFixed(1) + '" fill="url(#pfUniD)"/>');
    s.push('<rect x="' + (lx - 1.5).toFixed(1) + '" y="' + (groundY - 8).toFixed(1) + '" width="' + (legW + 3).toFixed(1) + '" height="9" rx="3" fill="#2b3138"/>');
  });
  // 手臂(微外张)+ 手
  [-1, 1].forEach(function (side) {
    var ax = cx + side * (torsoW / 2 + armW * 0.35);
    s.push('<rect x="' + (ax - armW / 2).toFixed(1) + '" y="' + (shoulderY + 2).toFixed(1) + '" width="' + armW.toFixed(1) + '" height="' + armL.toFixed(1) + '" rx="' + (armW / 2).toFixed(1) + '" fill="url(#pfUniD)" transform="rotate(' + (side * 5) + ' ' + ax.toFixed(1) + ' ' + (shoulderY + 4).toFixed(1) + ')"/>');
    s.push('<circle cx="' + (ax + side * armL * 0.09).toFixed(1) + '" cy="' + (shoulderY + armL + 2).toFixed(1) + '" r="' + (armW * 0.52).toFixed(1) + '" fill="url(#pfSkin)"/>');
  });
  // 躯干 + 腰带 + 高光
  s.push('<rect x="' + (cx - torsoW / 2).toFixed(1) + '" y="' + shoulderY.toFixed(1) + '" width="' + torsoW.toFixed(1) + '" height="' + (torsoH + 6).toFixed(1) + '" rx="' + (torsoW * 0.3).toFixed(1) + '" fill="url(#pfUni)"/>');
  s.push('<rect x="' + (cx - torsoW / 2).toFixed(1) + '" y="' + (hipY - 2).toFixed(1) + '" width="' + torsoW.toFixed(1) + '" height="7" rx="2" fill="#2f3d28" opacity="0.85"/>');
  s.push('<ellipse cx="' + (cx - torsoW * 0.22).toFixed(1) + '" cy="' + (shoulderY + torsoH * 0.32).toFixed(1) + '" rx="' + (torsoW * 0.12).toFixed(1) + '" ry="' + (torsoH * 0.3).toFixed(1) + '" fill="#ffffff" opacity="0.13"/>');
  // 脖子 + 头 + 头发
  s.push('<rect x="' + (cx - neckW / 2).toFixed(1) + '" y="' + (headCy + headR * 0.4).toFixed(1) + '" width="' + neckW.toFixed(1) + '" height="' + (neckH + 8).toFixed(1) + '" rx="3" fill="url(#pfSkin)"/>');
  s.push('<circle cx="' + cx + '" cy="' + headCy.toFixed(1) + '" r="' + headR.toFixed(1) + '" fill="url(#pfSkin)"/>');
  s.push('<path d="M ' + (cx - headR).toFixed(1) + ' ' + headCy.toFixed(1) + ' A ' + headR.toFixed(1) + ' ' + headR.toFixed(1) + ' 0 0 1 ' + (cx + headR).toFixed(1) + ' ' + headCy.toFixed(1) + ' Z" fill="#3a332b"/>');
  s.push('</svg>');
  var bmiV = Math.round(bmi * 10) / 10;
  // 军事标准判定(按性别,可在体能标准-BMI标准中配置区间)
  var v = bmiVerdict(bmi, gender);
  var std = (BMI_STD || defaultBmiStd());
  var r = gender === '女' ? std.female : std.male;
  box.appendChild(h('div', { style: 'width:100%', html: s.join('') }));
  box.appendChild(h('div', { style: 'margin-top:8px;font-size:13px;color:#445262;text-align:center' },
    h('span', { text: '身高 ' + (+hc) + ' cm · 体重 ' + (+wc) + ' kg · BMI ' + bmiV + ' ' }),
    h('b', { style: 'color:' + v[2], text: v[1] })));
  box.appendChild(h('div', { style: 'margin-top:3px;font-size:11.5px;color:#8a97a8;text-align:center',
    text: '军标区间(' + (gender === '女' ? '女' : '男') + '):' + r[0] + ' ~ ' + r[1] }));
  return box;
}

/** 折线图:series=[{name,data:[{date,score}]}],X=考核日期,Y=分数0-100;opts:{W,H,legend:false} */
function buildTrendChart(series, opts) {
  opts = opts || {};
  var W = opts.W || 880, H = opts.H || 430, padL = 46, padR = 18, padT = 18, padB = 62;
  var dates = [];
  series.forEach(function (s) {
    s.data.forEach(function (p) { if (dates.indexOf(p.date) < 0) { dates.push(p.date); } });
  });
  dates.sort();
  var xi = {};
  dates.forEach(function (d, i) { xi[d] = i; });
  var n = dates.length;
  function xAt(i) { return n === 1 ? (padL + (W - padL - padR) / 2) : padL + (W - padL - padR) * i / (n - 1); }
  function yAt(v) {
    var yMin = 0, yMax = 100, vv = Math.max(yMin, Math.min(yMax, v));
    return padT + (H - padT - padB) * (1 - vv / (yMax - yMin));
  }
  var svg = ['<svg viewBox="0 0 ' + W + ' ' + H + '" style="width:100%;height:auto;background:#fff;display:block">'];
  [0, 20, 40, 60, 80, 100].forEach(function (v) {
    var y = yAt(v).toFixed(1);
    svg.push('<line x1="' + padL + '" y1="' + y + '" x2="' + (W - padR) + '" y2="' + y + '" stroke="#eef1f5" stroke-width="1"/>');
    svg.push('<text x="' + (padL - 8) + '" y="' + (+y + 4) + '" text-anchor="end" font-size="11" fill="#8a97a8">' + v + '</text>');
  });
  var step = Math.max(1, Math.ceil(n / 12));
  dates.forEach(function (d, i) {
    if (i % step !== 0 && i !== n - 1) { return; }
    var x = xAt(i).toFixed(1);
    svg.push('<line x1="' + x + '" y1="' + padT + '" x2="' + x + '" y2="' + (H - padB) + '" stroke="#f5f7fa" stroke-width="1"/>');
    svg.push('<text x="' + x + '" y="' + (H - padB + 18) + '" text-anchor="middle" font-size="10" fill="#8a97a8">' + esc(d.slice(2)) + '</text>');
  });
  svg.push('<text x="' + (padL - 30) + '" y="' + (padT + 4) + '" font-size="11" fill="#8a97a8">分</text>');
  series.forEach(function (s, si) {
    var color = TREND_COLORS[si % TREND_COLORS.length];
    var pts = s.data.map(function (p) { return xAt(xi[p.date]).toFixed(1) + ',' + yAt(p.score).toFixed(1); });
    svg.push('<polyline points="' + pts.join(' ') + '" fill="none" stroke="' + color + '" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>');
    s.data.forEach(function (p) {
      svg.push('<circle cx="' + xAt(xi[p.date]).toFixed(1) + '" cy="' + yAt(p.score).toFixed(1) + '" r="3.5" fill="' + color + '"><title>' + esc(s.name) + ' ' + p.date + ' : ' + p.score + '分</title></circle>');
    });
  });
  svg.push('</svg>');
  var chartBox = h('div', { style: 'max-height:54vh;overflow:auto;border:1px solid #edf1f6;border-radius:8px;padding:10px', html: svg.join('') });
  if (opts.legend === false) { return chartBox; }
  var legend = h('div', { style: 'display:flex;gap:16px;flex-wrap:wrap;margin-top:10px;font-size:13px;color:#445262' });
  series.forEach(function (s, si) {
    var color = TREND_COLORS[si % TREND_COLORS.length];
    legend.appendChild(h('span', { style: 'display:flex;align-items:center;gap:5px' },
      h('span', { style: 'width:16px;height:3px;background:' + color + ';display:inline-block;border-radius:2px' }),
      h('span', { text: s.name + '(' + s.data.length + '次)' })));
  });
  return h('div', {}, chartBox, legend);
}

var BMI_STD = null;   // {male:[min,max], female:[min,max]}
function defaultBmiStd() {
  return { male: [17.5, 27.5], female: [17, 24.5] };
}
function loadBmiStd() {
  return GET('/api/setting/bmi_standard').then(function (r) {
    BMI_STD = (r && r.male && r.female) ? r : defaultBmiStd();
    return BMI_STD;
  }).catch(function () { BMI_STD = defaultBmiStd(); return BMI_STD; });
}
/** BMI 按性别军标判定: [合格?, 标签, 颜色] */
function bmiVerdict(bmi, gender) {
  var std = BMI_STD || defaultBmiStd();
  var r = gender === '女' ? std.female : std.male;
  var inRange = bmi >= r[0] && bmi <= r[1];
  if (inRange) { return [true, '合格', '#2ea44f']; }
  if (bmi < r[0]) { return [false, '偏瘦不合格', '#2f6fd6']; }
  return [false, '超重不合格', '#d9463e'];
}

var GRADE_RULE = null;
function defaultGradeRule() {
  return [{ label: '优秀', op: '>=', min: 90 }, { label: '良好', op: '>=', min: 80 }, { label: '及格', op: '>=', min: 60 }];
}
function loadGradeRule() {
  return GET('/api/setting/fitness_grade_rule').then(function (r) {
    GRADE_RULE = (r && r.length) ? r : defaultGradeRule();
    GRADE_RULE.sort(function (a, b) { return (+b.min) - (+a.min); });
    return GRADE_RULE;
  }).catch(function () { GRADE_RULE = defaultGradeRule(); return GRADE_RULE; });
}
/** 按分数得评定(op 支持 >= 与 >),低于所有阈值 → 不及格 */
function gradeForPoints(pts) {
  if (pts === '' || pts === null || pts === undefined || isNaN(pts)) { return ''; }
  pts = +pts;
  var rule = GRADE_RULE || defaultGradeRule();
  for (var i = 0; i < rule.length; i++) {
    if (rule[i].op === '>' ? pts > +rule[i].min : pts >= +rule[i].min) { return rule[i].label; }
  }
  return '不及格';
}

/** 体能评分标准缓存(从服务端加载) */
/** 体能评分矩阵缓存:item → [{age_group, score, value}] */
var FITNESS_MATRIX_CACHE = {};

function reloadFitnessMatrix() {
  GET('/api/fitness-matrix/items').then(function (items) {
    var jobs = (items || []).map(function (item) {
      return GET('/api/fitness-matrix?item=' + encodeURIComponent(item)).then(function (rows) {
        FITNESS_MATRIX_CACHE[item] = rows || [];
      });
    });
    return Promise.all(jobs);
  }).catch(function () { FITNESS_MATRIX_CACHE = {}; });
}

/** 年龄 → 年龄段标签(从矩阵列中匹配) */
function matchAgeGroup(ageGroups, age) {
  if (age === '' || age === null || age === undefined) { return null; }
  age = +age;
  for (var i = 0; i < ageGroups.length; i++) {
    var label = ageGroups[i];
    var nums = label.match(/\d+/g);
    if (!nums) { continue; }
    var a = +nums[0];
    var b = nums[1] ? +nums[1] : a;
    var hit;
    if (label.indexOf('以下') >= 0) { hit = age <= a; }
    else if (label.indexOf('以上') >= 0) { hit = age >= a; }
    else { hit = age >= a && age <= b; }
    if (hit) { return label; }
  }
  return null;
}

/** 根据项目+年龄+成绩文本 → 分数(100/95/.../55),无法计算返回 '' */
function calcFitnessGrade(item, scoreText, age) {
  var matrix = FITNESS_MATRIX_CACHE[item];
  if (!matrix || !matrix.length) { return ''; }
  var maxV = 0;
  matrix.forEach(function (r) { if (r.value > maxV) { maxV = r.value; } });
  var isTime = maxV > 300;
  var text = String(scoreText || '').trim();
  if (!text) { return ''; }
  var perf = null;
  if (isTime) {
    var m = /(\d+)['\u2032:](\d+)/.exec(text);
    if (m) { perf = +m[1] * 60 + +m[2]; }
    if (perf === null) {
      var m2 = /(\d+)\s*秒/.exec(text);
      if (m2) { perf = +m2[1]; }
    }
    if (perf === null) {
      var m3 = /(\d+(?:\.\d+)?)/.exec(text);
      if (m3) { perf = +m3[1]; }
    }
  } else {
    var m4 = /(\d+)/.exec(text);
    if (m4) { perf = +m4[1]; }
  }
  if (perf === null) { return ''; }
  var groups = [];
  matrix.forEach(function (r) {
    if (groups.indexOf(r.age_group) < 0) { groups.push(r.age_group); }
  });
  var myGroup = matchAgeGroup(groups, age);
  if (!myGroup) { return ''; }
  var scored = matrix.filter(function (r) { return r.age_group === myGroup; });
  scored.sort(function (a, b) { return b.score - a.score; });
  for (var i = 0; i < scored.length; i++) {
    var need = scored[i].value;
    if (isTime ? perf <= need : perf >= need) {
      return String(scored[i].score);
    }
  }
  return '55';
}

/** 体能标准管理器:矩阵式编辑(项目选择 + 分数行 x 年龄段列) */
/** 体能评分矩阵管理:左侧项目列表 + 右侧可直接输入的矩阵表格 */
/** 体能标准管理(全屏页面):左侧固定项目列表 + 右侧矩阵表格直接输入 */
/** 评定标准管理:用户自定义多少分为及格/良好/优秀(可选 ≥ 或 >) */
function openGradeRuleManager() {
  var rulesBox = h('div');
  var rules = [];
  function renderRules() {
    rulesBox.innerHTML = '';
    rules.forEach(function (r, i) {
      var lbl = makeDropdown((dictLabels('fitness_grade').length ? dictLabels('fitness_grade') : ['优秀', '良好', '及格']).map(function (x) { return { v: x, l: x }; }), '评定');
      lbl.value = r.label;
      var op = makeDropdown([{ v: '>=', l: '≥ (大于等于)' }, { v: '>', l: '> (大于)' }], '比较');
      op.value = r.op;
      var min = h('input', { type: 'number', step: '0.5', value: r.min, style: 'width:90px' });
      var row = h('div', { style: 'display:flex;gap:8px;align-items:center;margin-bottom:8px' },
        h('span', { text: '分数' }), op, min,
        h('span', { text: '时评为' }), lbl,
        h('button', { class: 'btn-link danger', text: '删除', onclick: function () { rules.splice(i, 1); renderRules(); } }));
      row._bind = function () { r.op = op.value; r.min = min.value; r.label = lbl.value; };
      rulesBox.appendChild(row);
      // 保存前收集
      row.addEventListener('change', function () { r.op = op.value; r.min = min.value; r.label = lbl.value; });
    });
  }
  rulesBox.addEventListener('change', function () {
    Array.prototype.forEach.call(rulesBox.children, function (row, i) {
      if (rules[i]) { rules[i]._saved = true; }
    });
  });
  var m = openModal({
    title: '体能评定标准(自上而下依次匹配,都不满足为不及格)',
    body: h('div', {},
      h('div', { text: '规则按分数从高到低依次判断;例如 "分数 ≥ 90 时评为 优秀"。', style: 'color:#66788c;font-size:13px;margin-bottom:12px' }),
      rulesBox,
      h('button', { class: 'btn btn-ghost', text: '＋ 添加规则', onclick: function () {
        rules.push({ label: '及格', op: '>=', min: 60 });
        renderRules();
      } })),
    footer: [
      h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m.close(); } }),
      h('button', { class: 'btn btn-primary', text: '保存', onclick: function () {
        // 收集行内最新值
        Array.prototype.forEach.call(rulesBox.children, function (row, i) {
          var op = row.querySelectorAll('.dd')[0];
          var lbl = row.querySelectorAll('.dd')[1];
          var min = row.querySelector('input[type=number]');
          if (rules[i] && op && lbl && min) {
            rules[i].op = op.value; rules[i].min = Number(min.value); rules[i].label = lbl.value;
          }
        });
        if (!rules.length) { toast('至少保留一条规则', 'err'); return; }
        var sorted = rules.slice().sort(function (a, b) { return (+b.min) - (+a.min); });
        PUT('/api/setting/fitness_grade_rule', sorted).then(function () {
          m.close();
          toast('评定标准已保存');
          loadGradeRule();
        }).catch(function (e) { toast(e.message, 'err'); });
      } })
    ]
  });
  (GRADE_RULE ? Promise.resolve(GRADE_RULE) : loadGradeRule()).then(function (r) {
    rules = (r || defaultGradeRule()).map(function (x) { return { label: x.label, op: x.op, min: x.min }; });
    renderRules();
  });
}

/** BMI 标准:男女合格区间配置(身高体重指数,档案判定与BMI考核项目共用) */
function openBmiStdManager() {
  var ready = BMI_STD ? Promise.resolve(BMI_STD) : loadBmiStd();
  ready.then(function (std) {
    function num(v) {
      var n = Number(v);
      return isNaN(n) ? '' : n;
    }
    var mMin = h('input', { type: 'number', step: '0.1', style: 'width:110px;height:32px;padding:0 8px;border:1px solid #c5cdd8;border-radius:4px' });
    var mMax = h('input', { type: 'number', step: '0.1', style: 'width:110px;height:32px;padding:0 8px;border:1px solid #c5cdd8;border-radius:4px' });
    var fMin = h('input', { type: 'number', step: '0.1', style: 'width:110px;height:32px;padding:0 8px;border:1px solid #c5cdd8;border-radius:4px' });
    var fMax = h('input', { type: 'number', step: '0.1', style: 'width:110px;height:32px;padding:0 8px;border:1px solid #c5cdd8;border-radius:4px' });
    mMin.value = num(std.male[0]); mMax.value = num(std.male[1]);
    fMin.value = num(std.female[0]); fMax.value = num(std.female[1]);
    function row(label, a, b) {
      return h('div', { style: 'display:flex;align-items:center;gap:10px;margin-bottom:14px' },
        h('span', { style: 'width:60px;font-size:14px;color:#1d2c40;font-weight:600', text: label }),
        a, h('span', { text: '~' }), b, h('span', { style: 'color:#8a97a8;font-size:12px', text: '(含边界)' }));
    }
    var m = openModal({
      title: 'BMI 标准(身高体重指数合格区间)',
      body: h('div', {},
        h('div', { style: 'font-size:13px;color:#66788c;margin-bottom:14px;line-height:1.7',
          text: 'BMI = 体重(kg) ÷ 身高(m)²。用于:体能档案人体模型判定、BMI考核项目导入评分(合格100分/不合格0分)。' }),
        row('男军人', mMin, mMax),
        row('女军人', fMin, fMax),
        h('button', {
          class: 'btn btn-ghost btn-sm', text: '恢复默认(男17.5~27.5 / 女17~24.5)',
          onclick: function () { mMin.value = 17.5; mMax.value = 27.5; fMin.value = 17; fMax.value = 24.5; }
        })),
      footer: [
        h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m.close(); } }),
        h('button', {
          class: 'btn btn-primary', text: '保存',
          onclick: function () {
            var val = function (el) { return Number(el.value); };
            var mm = [val(mMin), val(mMax)], ff = [val(fMin), val(fMax)];
            if ([mm[0], mm[1], ff[0], ff[1]].some(function (x) { return isNaN(x) || x <= 0; }) || mm[0] >= mm[1] || ff[0] >= ff[1]) {
              toast('请填写正确的区间(最小值需小于最大值)', 'err');
              return;
            }
            PUT('/api/setting/bmi_standard', { male: mm, female: ff }).then(function () {
              BMI_STD = { male: mm, female: ff };
              m.close();
              toast('BMI标准已保存');
            }).catch(function (e) { toast(e.message, 'err'); });
          }
        })
      ]
    });
  });
}

function openFitnessStdManager() {
  var items = [];
  var currentItem = null;
  var ageGroups = [];
  var scores = [];
  var saveTimers = {};

  // 全屏容器
  var overlay = h('div', { style: 'position:fixed;inset:0;background:#eef2f7;z-index:90;display:flex;flex-direction:column' });
  var topbar = h('div', { style: 'height:48px;flex:none;background:#fff;border-bottom:1px solid #e2e8f0;display:flex;align-items:center;justify-content:space-between;padding:0 20px' },
    h('div', { style: 'display:flex;align-items:center;gap:14px' },
      h('div', { style: 'font-size:17px;font-weight:600;color:#1d2c40', text: '体能评分标准管理' }),
      h('button', { class: 'btn btn-ghost btn-sm', text: 'BMI标准', title: '设置男女身高体重指数合格区间', onclick: openBmiStdManager })),
    h('div', { style: 'font-size:17px;font-weight:600;color:#1d2c40', text: ' ' }),
    h('button', { class: 'btn btn-primary', text: '✕ 关闭', onclick: function () { overlay.remove(); reloadFitnessMatrix(); } }));
  var mainArea = h('div', { style: 'flex:1;display:flex;overflow:hidden' });

  // 左侧:项目列表(固定)
  var left = h('div', { style: 'width:190px;flex:none;background:#fff;border-right:1px solid #edf1f6;display:flex;flex-direction:column;overflow:hidden' });
  var listBox = h('div', { style: 'flex:1;overflow-y:auto;padding:8px 0' });
  var addBox = h('div', { style: 'padding:10px;border-top:1px solid #edf1f6' });

  // 右侧:矩阵
  var right = h('div', { style: 'flex:1;overflow:auto;padding:16px' });
  var rightHead = h('div');
  var rightTable = h('div');

  overlay.appendChild(topbar);
  overlay.appendChild(mainArea);
  mainArea.appendChild(left);
  mainArea.appendChild(right);
  document.body.appendChild(overlay);

  /* ===== 左侧项目列表 ===== */
  function renderLeft() {
    listBox.innerHTML = '';
    items.forEach(function (it, idx) {
      var row = h('div', {
        style: 'display:flex;align-items:center;justify-content:space-between;padding:9px 12px;cursor:pointer;' +
          (it === currentItem ? 'background:#e4edfb;color:#16437e;font-weight:600' : 'color:#445262') +
          ';border-left:3px solid ' + (it === currentItem ? '#2f6fd6' : 'transparent')
      });
      row.appendChild(h('span', { text: it, style: 'flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap' }));
      var pencilSpan = h('span', {
        text: '✏', title: '点击重命名',
        style: 'color:#b0bccb;font-size:11px;flex:none;width:20px;height:20px;display:flex;align-items:center;justify-content:center;cursor:pointer;border-radius:3px'
      });
      row.appendChild(pencilSpan);
      row.appendChild(h('span', {
        text: '×', style: 'color:#d0d8e2;font-size:16px;flex:none;width:24px;height:24px;display:flex;align-items:center;justify-content:center;border-radius:3px;cursor:pointer',
        onclick: function (e) {
          e.stopPropagation();
          confirmBox('确定删除项目「' + it + '」及其全部评分标准?\n删除后不可恢复。', true).then(function (yes) {
            if (!yes) { return; }
            DEL('/api/fitness-matrix/item?item=' + encodeURIComponent(it)).then(function () {
              items = items.filter(function (x) { return x !== it; });
              if (currentItem === it) { currentItem = items[0] || null; ageGroups = []; scores = []; }
              renderLeft();
              renderRight();
              toast('已删除');
            }).catch(function (err) { toast(err.message, 'err'); });
          });
        }
      }));
      // hover 效果
      row.addEventListener('mouseenter', function () { row.style.background = it === currentItem ? '#e4edfb' : '#f4f7fb'; });
      row.addEventListener('mouseleave', function () { row.style.background = it === currentItem ? '#e4edfb' : '#fff'; });
      // 点击选中(不重建左侧,只切高亮)
      row.addEventListener('click', function () {
        if (currentItem !== it) {
          currentItem = it;
          // 只切高亮,不重建DOM
          $all('.dict-type-item', listBox).forEach(function (el) { el.style.background = ''; el.style.borderLeft = ''; el.style.fontWeight = ''; el.style.color = ''; });
          row.style.background = '#e4edfb';
          row.style.borderLeft = '3px solid #2f6fd6';
          row.style.fontWeight = '600';
          row.style.color = '#16437e';
          loadMatrix(it);
        }
      });
      // 铅笔按钮:点击重命名(替代双击,避免与单击冲突)
      pencilSpan.addEventListener('click', function (e) {
        e.stopPropagation();
        var nameSpan = row.firstChild;
        var oldName = nameSpan.textContent;
        nameSpan.innerHTML = '';
        var inp = h('input', { type: 'text', style: 'width:100%;font-size:13px;padding:2px 6px;border:1px solid #2f6fd6;border-radius:3px;background:#fff' });
        inp.value = oldName;
        nameSpan.appendChild(inp);
        inp.focus();
        inp.select();
        function finishRename() {
          var newName = inp.value.trim();
          nameSpan.textContent = newName || oldName;
          if (!newName || newName === oldName) { return; }
          POST('/api/fitness-matrix/rename', { old_item: oldName, new_item: newName }).then(function () {
            items[idx] = newName;
            if (currentItem === oldName) { currentItem = newName; }
            renderLeft();
            renderRight();
            reloadFitnessMatrix();
            toast('已重命名为「' + newName + '」');
          }).catch(function (err) {
            nameSpan.textContent = oldName;
            toast(err.message, 'err');
          });
        }
        inp.addEventListener('blur', finishRename);
        inp.addEventListener('keydown', function (ev) {
          if (ev.key === 'Enter') { inp.blur(); }
          if (ev.key === 'Escape') { nameSpan.textContent = oldName; }
        });
      });
      listBox.appendChild(row);
    });
  }

  // 新增项目(带保存按钮)
  addBox.appendChild(h('div', { style: 'font-size:12px;color:#8a97a8;margin-bottom:6px', text: '新增考核项目:' }));
  addBox.appendChild(h('button', {
    class: 'btn btn-primary btn-sm', text: '＋ 新建项目',
    style: 'width:100%',
    onclick: function () { openNewProjectDialog(); }
  }));

  function openNewProjectDialog() {
    var nameInput = h('input', { type: 'text', placeholder: '如:3000米跑', style: 'width:100%;height:34px;padding:0 10px;border:1px solid #c5cdd8;border-radius:4px' });
    var unitDD = makeDropdown([{v:'count',l:'次数型(越多越好)'},{v:'time',l:'时间型(越少越好)'}], '必选');

    var m2 = openModal({
      title: '新建考核项目',
      body: h('div', { style: 'display:flex;flex-direction:column;gap:12px' },
        h('div', {}, h('div', { style: 'font-size:13px;color:#66788c;margin-bottom:5px', text: '项目名称(必填):' }), nameInput),
        h('div', {}, h('div', { style: 'font-size:13px;color:#66788c;margin-bottom:5px', text: '计分方式(必选):' }), unitDD)),
      footer: [
        h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m2.close(); } }),
        h('button', {
          class: 'btn btn-primary', text: '创建',
          onclick: function () {
            var name = nameInput.value.trim();
            if (!name) { toast('请输入项目名称', 'err'); return; }
            if (items.indexOf(name) >= 0) { toast('该项目已存在', 'err'); return; }
            var unit = unitDD.value;
            if (!unit) { toast('请选择计分方式(次数型或时间型)', 'err'); return; }
            m2.close();
            var defaultAges = ['24岁以下', '25-27', '28-30', '31-33', '34-36', '37-39'];
            var defaultScores = [100, 95, 90, 85, 80, 75, 70, 65, 60, 55];
            POST('/api/fitness-matrix/init', {
              item: name, unit: unit,
              ageGroups: defaultAges, scores: defaultScores
            }).then(function () {
              items.push(name);
              currentItem = name;
              loadMatrix(name);
              reloadFitnessMatrix();
              toast('项目「' + name + '」已创建(已保存,关闭不丢失)');
            }).catch(function (e) { toast(e.message, 'err'); });
          }
        })
      ]
    });
    nameInput.focus();
  }
  left.appendChild(listBox);
  left.appendChild(addBox);

  /* ===== 右侧矩阵 ===== */
  function loadMatrix(item) {
    currentItem = item;
    GET('/api/fitness-matrix?item=' + encodeURIComponent(item)).then(function (rows) {
      rows = rows || [];
      ageGroups = [];
      scores = [];
      rows.forEach(function (r) {
        if (ageGroups.indexOf(r.age_group) < 0) { ageGroups.push(r.age_group); }
        if (scores.indexOf(r.score) < 0) { scores.push(r.score); }
      });
      scores.sort(function (a, b) { return b - a; });
      renderRight(); // 只渲染右侧,不重建左侧(避免销毁重命名输入框)
    }).catch(function (e) { toast(e.message, 'err'); });
  }

  function renderRight() {
    right.innerHTML = '';
    if (!currentItem) {
      right.appendChild(h('div', { style: 'color:#97a4b5;padding:60px;text-align:center;font-size:15px', text: '请在左侧选择或新建考核项目' }));
      return;
    }

    // 工具栏
    rightHead = h('div', { style: 'margin-bottom:12px;display:flex;gap:8px;align-items:center;flex-wrap:wrap' },
      h('strong', { style: 'font-size:15px;color:#16437e', text: currentItem }),
      h('span', { style: 'color:#a6b3c4;font-size:12px', text: '双击项目名重命名;单元格直接输入,失焦保存;双击表头删除' }),
      h('div', { style: 'flex:1' }),
      h('button', { class: 'btn btn-ghost btn-sm', text: '＋ 年龄段', onclick: promptAgeGroup }),
      h('button', { class: 'btn btn-ghost btn-sm', text: '＋ 分数行', onclick: promptScoreRow }));
    right.appendChild(rightHead);

    // 单位提示条:自动检测数据类型并显示
    var isTimeType = false;
    GET('/api/fitness-matrix?item=' + encodeURIComponent(currentItem)).then(function (rows) {
      var maxV = 0;
      (rows || []).forEach(function (r) { if (r.value > maxV) { maxV = r.value; } });
      isTimeType = maxV > 300;
      var unitLabel = isTimeType ? '时间型(秒)' : '次数型(次)';
      var unitColor = isTimeType ? '#e8960e' : '#2f9e5b';
      var hintText = isTimeType
        ? "达标值填秒数,如750=12分30秒。成绩可填12分30秒或750。值越小越好。"
        : "达标值填次数,如15=做15个。成绩填数字即可。值越多越好。";
      right.appendChild(h('div', {
        style: 'background:#fff;border:1px solid #dbe4ee;border-left:3px solid ' + unitColor +
          ';border-radius:6px;padding:10px 14px;margin-bottom:12px;font-size:13px;color:#445262;display:flex;align-items:center;gap:10px'
      },
        h('span', {
          style: 'flex:none;padding:3px 10px;border-radius:10px;font-size:12px;font-weight:600;color:#fff;background:' + unitColor,
          text: unitLabel
        }),
        h('span', { text: hintText })));

      // 在表头加单位后缀
      var ageHeads = $all('.mx-age-head', right);
      ageHeads.forEach(function (th) {
        var orig = th.textContent.trim();
        var suffix = isTimeType ? '(秒)' : '(次)';
        if (orig.indexOf('(') < 0) {
          th.textContent = orig + suffix;
        }
      });

      // 空单元格加 placeholder
      $all('.mx-input', right).forEach(function (inp) {
        inp.placeholder = isTimeType ? '秒' : '次';
      });
    });

    if (!ageGroups.length || !scores.length) {
      right.appendChild(h('div', { style: 'background:#fff;border-radius:8px;padding:40px;text-align:center;color:#97a4b5' },
        h('span', { text: '暂无标准,点击上方按钮添加年龄段和分数行' })));
      return;
    }

    // 矩阵表格
    var table = h('table', { class: 'grid sub-grid', style: 'background:#fff;border-radius:8px;overflow:hidden' });
    var head = '<thead><tr><th style="width:80px;text-align:center">分数\\年龄</th>';
    ageGroups.forEach(function (g) {
      head += '<th class="mx-age-head" data-col="' + esc(g) + '" title="双击删除该列" style="text-align:center;cursor:pointer">' + esc(g) + '</th>';
    });
    head += '</tr></thead>';
    var bodyHtml = '';
    scores.forEach(function (sc) {
      bodyHtml += '<tr><th style="text-align:center;cursor:pointer" class="mx-score-head" data-score="' + sc + '" title="双击删除该行">' + sc + '</th>';
      ageGroups.forEach(function (g) {
        bodyHtml += '<td><input type="text" class="mx-input" data-col="' + esc(g) + '" data-score="' + sc +
          '" style="width:100%;height:28px;font-size:13px;padding:0 4px;text-align:center;border:1px solid transparent;border-radius:3px;background:transparent" /></td>';
      });
      bodyHtml += '</tr>';
    });
    table.innerHTML = head + '<tbody>' + bodyHtml + '</tbody>';

    // 加载已有值
    GET('/api/fitness-matrix?item=' + encodeURIComponent(currentItem)).then(function (rows) {
      var map = {};
      (rows || []).forEach(function (r) { map[r.age_group + '|' + r.score] = r.value; });
      $all('.mx-input', table).forEach(function (inp) {
        var g = inp.getAttribute('data-col');
        var sc = inp.getAttribute('data-score');
        var v = map[g + '|' + sc];
        inp.value = (v === undefined || v === null || v === 0) ? '' : String(v);
      });
    });

    // 输入框:焦点高亮,失焦保存
    table.addEventListener('focusin', function (e) {
      var inp = e.target.closest('.mx-input');
      if (inp) { inp.style.border = '1px solid #2f6fd6'; inp.style.background = '#fff'; }
    });
    table.addEventListener('focusout', function (e) {
      var inp = e.target.closest('.mx-input');
      if (!inp) { return; }
      inp.style.border = '1px solid transparent';
      var g = inp.getAttribute('data-col');
      var sc = inp.getAttribute('data-score');
      var val = inp.value.trim();
      clearTimeout(saveTimers[g + '|' + sc]);
      saveTimers[g + '|' + sc] = setTimeout(function () {
        POST('/api/fitness-matrix', { item: currentItem, age_group: g, score: +sc,
          value: val === '' ? 0 : +val }).then(function () { reloadFitnessMatrix(); })
          .catch(function (err) { toast(err.message, 'err'); });
      }, 300);
    });
    table.addEventListener('keydown', function (e) { if (e.key === 'Enter') { e.target.blur(); } });

    // 双击表头删除
    table.addEventListener('dblclick', function (e) {
      var ageHead = e.target.closest('.mx-age-head');
      if (ageHead) {
        var g = ageHead.getAttribute('data-col');
        confirmBox('确定删除年龄段「' + g + '」整列数据?', true).then(function (yes) {
          if (!yes) { return; }
          DEL('/api/fitness-matrix/column?item=' + encodeURIComponent(currentItem)
            + '&age_group=' + encodeURIComponent(g)).then(function () {
            ageGroups = ageGroups.filter(function (x) { return x !== g; });
            renderRight();
            reloadFitnessMatrix();
          }).catch(function (err) { toast(err.message, 'err'); });
        });
        return;
      }
      var scoreHead = e.target.closest('.mx-score-head');
      if (scoreHead) {
        var sc = +scoreHead.getAttribute('data-score');
        confirmBox('确定删除分数「' + sc + '」整行数据?', true).then(function (yes) {
          if (!yes) { return; }
          DEL('/api/fitness-matrix?item=' + encodeURIComponent(currentItem) + '&score=' + sc)
            .then(function () {
              scores = scores.filter(function (x) { return x !== sc; });
              renderRight();
              reloadFitnessMatrix();
            }).catch(function (err) { toast(err.message, 'err'); });
        });
      }
    });

    rightTable = h('div', { style: 'background:#fff;border-radius:8px;box-shadow:0 1px 4px rgba(23,43,77,.08);overflow-x:auto' }, table);
    right.appendChild(rightTable);
  }

  /* ===== 弹窗式添加 ===== */
  function promptAgeGroup() {
    if (!currentItem) { toast('请先选择项目', 'err'); return; }
    var input = h('input', { type: 'text', placeholder: '如:40-42、40以上、24岁以下', style: 'width:100%;height:34px;padding:0 10px;border:1px solid #c5cdd8;border-radius:4px' });
    var m2 = openModal({
      title: '添加年龄段',
      body: h('div', {}, h('div', { style: 'color:#66788c;font-size:13px;margin-bottom:8px', text: '输入年龄段名称,将作为矩阵的新列:' }), input),
      footer: [
        h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m2.close(); } }),
        h('button', { class: 'btn btn-primary', text: '添加', onclick: function () {
          var g = input.value.trim();
          if (!g) { toast('请输入年龄段', 'err'); return; }
          if (ageGroups.indexOf(g) < 0) { ageGroups.push(g); }
          m2.close();
          renderRight();
        } })
      ]
    });
    input.focus();
  }

  function promptScoreRow() {
    if (!currentItem) { toast('请先选择项目', 'err'); return; }
    var input = h('input', { type: 'number', placeholder: '如:50、45', style: 'width:100%;height:34px;padding:0 10px;border:1px solid #c5cdd8;border-radius:4px' });
    var m2 = openModal({
      title: '添加分数行',
      body: h('div', {}, h('div', { style: 'color:#66788c;font-size:13px;margin-bottom:8px', text: '输入分数(0-100),将作为矩阵的新行:' }), input),
      footer: [
        h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m2.close(); } }),
        h('button', { class: 'btn btn-primary', text: '添加', onclick: function () {
          var s = parseInt(input.value.trim(), 10);
          if (isNaN(s) || s < 0 || s > 100) { toast('请输入0-100的数字', 'err'); return; }
          if (scores.indexOf(s) < 0) { scores.push(s); scores.sort(function (a, b) { return b - a; }); }
          m2.close();
          renderRight();
        } })
      ]
    });
    input.focus();
  }

  // CSS
  var style = document.createElement('style');
  style.textContent = '.mx-input:focus{outline:none}.mx-input:hover{border-color:#c5cdd8!important}';
  document.head.appendChild(style);

  // 初始化
  GET('/api/fitness-matrix/items').then(function (list) {
    items = list || [];
    renderLeft(); // 先渲染左侧列表
    if (items.length) {
      currentItem = items[0];
      loadMatrix(currentItem);
    } else {
      renderRight();
    }
  }).catch(function (e) {
    renderLeft();
    renderRight();
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
    { key: 'name', label: '姓名' },
    { key: 'idCard', label: '身份证号' },
    { key: 'status', label: '状态', type: 'select', options: statusOptions },
    { key: 'departmentId', label: '部门', type: 'select', options: function () {
        return Ctx.deptFlat.map(function (d) {
          var prefix = '';
          for (var i = 0; i < d.depth; i++) { prefix += '　'; }
          return { v: String(d.id), l: prefix + d.name };
        });
      } }
  ],
  moreFilters: [
    { key: 'gender', label: '性别', type: 'select', options: ['男', '女'] },
    { key: 'politicalStatus', label: '政治面貌', type: 'select', options: function () { return dictLabels('political_status'); } },
    { key: 'category', label: '人员类别', type: 'select', options: function () { return dictLabels('personnel_category'); } },
    { key: 'rank', label: '军衔', type: 'select', options: function () { return dictLabels('rank'); },
      depends: { field: 'category', dict: 'rank', parentDict: 'personnel_category' } },
    { key: 'education', label: '学历', type: 'select', options: function () { return dictLabels('education'); } },
    { key: 'maritalStatus', label: '婚姻状况', type: 'select', options: function () { return dictLabels('marital_status'); } },
    { key: 'nation', label: '民族', type: 'select', options: function () { return dictLabels('nation'); } },
    { key: 'bloodType', label: '血型', type: 'select', options: function () { return dictLabels('blood_type'); } },
    { key: 'onlyChild', label: '独生子女', type: 'select', options: ['是', '否'] },
    { key: 'post', label: '职务/岗位' },
    { key: 'nativePlace', label: '籍贯' },
    { key: 'entryDateStart', label: '入伍起', type: 'date' },
    { key: 'entryDateEnd', label: '入伍止', type: 'date' }
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
    { key: 'category', label: '人员类别', render: function (r) { return r.category || '—'; } },
    { key: 'rank', label: '军衔/职级' },
    { key: 'post', label: '职务' },
    { key: 'status', label: '状态', render: function (r) { return badge(r.status); } },
    { key: 'phone', label: '手机号' },
    { key: 'entryDate', label: '入伍日期' },
    { key: '__service', label: '军龄', render: function (r) { var y = calcServiceYears(r.entryDate); return y ? y + '年' : '—'; } }
  ],
  rowTitle: function (r) { return r.name; },
  onView: function (row, act) { openDetailModal(personnelCfg, row, act); },
  delTip: function (r) { return '确定删除人员「' + r.name + '」吗?相关休假、体能、装备保管记录将一并解除关联,删除后不可恢复。'; },
  afterTable: function (rootEl) { loadRowPhotos(rootEl); },
  formTop: personnelPhotoBlock,
  addLabel: '新增人员',
  toolbarExtra: function (bar, act) {
    var dirInput = h('input', { type: 'file', accept: 'image/*', multiple: true, style: 'display:none' });
    dirInput.setAttribute('webkitdirectory', '');
    dirInput.addEventListener('change', function () {
      var files = dirInput.files;
      dirInput.value = '';
      if (files && files.length) {
        batchImportPhotos(files);
      }
    });
    bar.appendChild(dirInput);
    bar.appendChild(h('button', {
      class: 'btn btn-ghost', text: '导入照片',
      title: '选择文件夹(含子目录),按文件名中的身份证号自动匹配人员照片',
      onclick: function () { dirInput.click(); }
    }));
  },
  form: [
    { section: '基本信息' },
    { key: 'name', label: '姓名', required: true },
    { key: 'gender', label: '性别', type: 'select', options: ['男', '女'] },
    { key: 'idCard', label: '身份证号' },
    { key: 'birthday', label: '出生日期', type: 'date' },
    { key: 'phone', label: '手机号' },
    { key: 'departmentId', label: '部门', type: 'dept' },
    { key: 'category', label: '人员类别', type: 'select', options: function () { return dictLabels('personnel_category'); } },
    { key: 'rank', label: '军衔/职级', type: 'select', placeholder: '先选类别',
      depends: { field: 'category', dict: 'rank', parentDict: 'personnel_category' } },
    { key: 'post', label: '职务/岗位' },
    { key: 'status', label: '状态', type: 'select', required: true, options: statusOptions },
    { key: 'entryDate', label: '入伍/入职日期', type: 'date' },
    { section: '更多资料' },
    { key: 'nation', label: '民族', type: 'select', options: function () { return dictLabels('nation'); } },
    { key: 'nativePlace', label: '籍贯' },
    { key: 'bloodType', label: '血型', type: 'select', options: function () { return dictLabels('blood_type'); } },
    
    
    
    { key: 'education', label: '学历', type: 'select', options: function () { return dictLabels('education'); } },
    { key: 'onlyChild', label: '独生子女', type: 'select', options: function () { return dictLabels('only_child'); } },
    { key: 'height', label: '身高(cm)', type: 'number', step: '0.1' },
    { key: 'weight', label: '体重(kg)', type: 'number', step: '0.1' },
    { key: 'address', label: '家庭住址', span: 'full' },
    { section: '党团籍信息' },
    { key: 'politicalStatus', label: '政治面貌', type: 'select', options: function () { return dictLabels('political_status'); } },
    { key: 'partyApplyDate', label: '提交申请书时间', type: 'date' },
    { key: 'partyActivistDate', label: '积极分子时间', type: 'date' },
    { key: 'partyCandidateDate', label: '预备党员时间', type: 'date' },
    { key: 'partyFullDate', label: '转正时间', type: 'date' },
    { key: 'partyJoinDate', label: '入党/入团日期', type: 'date', requiredIf: function (v) { return v.politicalStatus === '党员' || v.politicalStatus === '团员'; } },
    { key: 'partyOrgLocation', label: '组织关系所在地', placeholder: '如:XX部队XX党委', requiredIf: function (v) { return v.politicalStatus === '党员'; } },
    { key: 'partyPost', label: '党内职务', placeholder: '如:支部委员、党委书记' },
    { key: 'partyRemark', label: '党团备注', type: 'textarea', span: 'full' },
    { section: '婚姻状况' },
    { key: 'maritalStatus', label: '婚姻状况', type: 'select', options: function () { return dictLabels('marital_status'); } },
    { key: 'marriageDate', label: '结婚日期', type: 'date', requiredIf: function (v) { return v.maritalStatus === '已婚' || v.maritalStatus === '离异'; } },
    { key: 'divorceDate', label: '离婚日期', type: 'date', requiredIf: function (v) { return v.maritalStatus === '离异'; } },
    { key: 'followArmyDate', label: '随军时间', type: 'date' },
    { key: 'followArmyStatus', label: '随军条件', type: 'select', options: function () { var l = dictLabels('follow_army_status'); return l.length ? l : ['符合', '不符合', '未申请']; } },
    { key: 'marriageRemark', label: '婚姻备注', type: 'textarea', span: 'full' },
    { section: '家庭信息' },
{ section: '任职经历' },
    { section: '奖惩情况' },
    { section: '驾驶证' },
    { section: '个人卡号' },
    { section: '私家车' },
    { section: '住房情况' }
  ],
  subPanels: function (row) {
    var fitnessOpt = {};
    var fk;
    for (fk in FITNESS_SUB) { fitnessOpt[fk] = FITNESS_SUB[fk]; }
    fitnessOpt.age = row ? calcAge(row.birthday) : '';
    fitnessOpt.birthday = row ? row.birthday : '';
    if (row && row.id) {
      return {
        panels: {
          '任职经历': subRecordsPanel(POSITION_SUB.kind, row.id, POSITION_SUB),
          '奖惩情况': subRecordsPanel(REWARD_SUB.kind, row.id, REWARD_SUB),
          '家庭信息': subRecordsPanel(FAMILY_SUB.kind, row.id, FAMILY_SUB),
          '驾驶证': subRecordsPanel(DRIVER_SUB.kind, row.id, DRIVER_SUB),
          '个人卡号': subRecordsPanel(CARD_SUB.kind, row.id, CARD_SUB),
          '私家车': subRecordsPanel(PVEHICLE_SUB.kind, row.id, PVEHICLE_SUB),
          '住房情况': subRecordsPanel(HOUSING_SUB.kind, row.id, HOUSING_SUB),
          '体能考核': subRecordsPanel(FITNESS_SUB.kind, row.id, fitnessOpt)
        }
      };
    }
    var pos = subRecordsPanel(POSITION_SUB.kind, null, POSITION_SUB);
    var rew = subRecordsPanel(REWARD_SUB.kind, null, REWARD_SUB);
    var fam = subRecordsPanel(FAMILY_SUB.kind, null, FAMILY_SUB);
    var drv = subRecordsPanel(DRIVER_SUB.kind, null, DRIVER_SUB);
    var card = subRecordsPanel(CARD_SUB.kind, null, CARD_SUB);
    var pveh = subRecordsPanel(PVEHICLE_SUB.kind, null, PVEHICLE_SUB);
    var hous = subRecordsPanel(HOUSING_SUB.kind, null, HOUSING_SUB);
    var fit = subRecordsPanel(FITNESS_SUB.kind, null, fitnessOpt);
    return {
      panels: { '任职经历': pos, '奖惩情况': rew, '家庭信息': fam,
        '驾驶证': drv, '个人卡号': card, '私家车': pveh, '住房情况': hous, '体能考核': fit },
      staged: { 'position-history': pos.__staged, 'reward-punish': rew.__staged,
        'family': fam.__staged, 'driver': drv.__staged, 'card': card.__staged,
        'pvehicle': pveh.__staged, 'housing': hous.__staged }
    };
  }
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
    { key: 'name', label: '名称' },
    { key: 'status', label: '状态', type: 'select', options: function () { return dictLabels('equipment_status'); } }
  ],
  moreFilters: [
    { key: 'category', label: '类别', type: 'select', options: function () { return dictLabels('equipment_category'); } },
    { key: 'code', label: '编号' },
    { key: 'keeperName', label: '保管人' },
    { key: 'location', label: '存放位置' },
    { key: 'buyDateStart', label: '购置起', type: 'date' },
    { key: 'buyDateEnd', label: '购置止', type: 'date' }
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
  personLookup: { idCard: 'keeperIdCard', name: 'keeperName', pid: 'keeperId' },
  filters: [
    { key: 'name', label: '名称' },
    { key: 'category', label: '分类', type: 'select', options: function () { return dictLabels('material_category'); } }
  ],
  moreFilters: [
    { key: 'spec', label: '规格' },
    { key: 'keeperName', label: '责任人' },
    { key: 'location', label: '存放位置', type: 'select', options: function () { return dictLabels('material_location'); } }
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
    { key: 'location', label: '存放位置', render: function (r) {
        return [r.location, r.detailLocation].filter(Boolean).join(' · ') || '—';
      } }
  ],
  rowTitle: function (r) { return r.name; },
  form: [
    { key: 'name', label: '物资名称', required: true },
    { key: 'category', label: '分类', type: 'select', options: function () { return dictLabels('material_category'); } },
    { key: 'spec', label: '规格' },
    { key: 'unit', label: '单位', placeholder: '个/箱/件…' },
    { key: 'quantity', label: '数量', type: 'number', step: '0.01' },
    { key: 'minQuantity', label: '库存预警下限', type: 'number', step: '0.01' },
    { key: 'keeperIdCard', label: '责任人身份证', placeholder: '输入后自动带出姓名' },
    { key: 'keeperName', label: '责任人姓名', readonly: true, placeholder: '按身份证号自动获取' },
    { key: 'keeperId', type: 'hidden' },
    { key: 'location', label: '存放位置', type: 'select', options: function () { return dictLabels('material_location'); } },
    { key: 'detailLocation', label: '详细存放点', placeholder: '如 A区3排2层' },
    { key: 'remark', label: '备注', type: 'textarea', span: 'full' }
  ],
  beforeSave: function (data) {
    // keeperIdCard/keeperName 仅用于表单展示,不提交
    delete data.keeperIdCard;
    delete data.keeperName;
    return data;
  }
};

var vehicleCfg = {
  key: 'vehicle', title: '车辆管理', icon: 'truck', endpoint: '/api/vehicle',
  clickKey: 'plateNo', addLabel: '新增车辆',
  personLookup: { idCard: 'driverIdCard', name: 'driverName', pid: 'driverId' },
  filters: [
    { key: 'plateNo', label: '车牌号' },
    { key: 'status', label: '状态', type: 'select', options: function () { return dictLabels('vehicle_status'); } }
  ],
  moreFilters: [
    { key: 'type', label: '车型', type: 'select', options: function () { return dictLabels('vehicle_type'); } },
    { key: 'brand', label: '品牌', type: 'select', options: function () { return dictLabels('vehicle_brand'); } },
    { key: 'model', label: '型号' },
    { key: 'vin', label: '车架号' },
    { key: 'driverName', label: '驾驶员' }
  ],
  columns: [
    { key: 'plateNo', label: '车牌号' },
    { key: 'vin', label: '车架号', render: function (r) { return r.vin || '—'; } },
    { key: 'brand', label: '品牌', render: function (r) { return r.brand || '—'; } },
    { key: 'model', label: '型号', render: function (r) { return r.model || '—'; } },
    { key: 'type', label: '车型' },
    { key: 'status', label: '状态', render: function (r) { return badge(r.status); } },
    { key: 'driverName', label: '驾驶员', render: function (r) { return r.driverName || '—'; } },
    { key: 'insuranceExpiry', label: '保险到期', render: function (r) { return r.insuranceExpiry || '—'; } },
    { key: 'inspectionExpiry', label: '年检到期', render: function (r) { return r.inspectionExpiry || '—'; } },
    { key: 'mileage', label: '里程(km)' }
  ],
  rowTitle: function (r) { return r.plateNo; },
  form: [
    { key: 'plateNo', label: '车牌号', required: true },
    { key: 'vin', label: '车架号', placeholder: '17位车辆识别代码' },
    { key: 'engineNo', label: '发动机号' },
    { key: 'brand', label: '品牌', type: 'select', options: function () { return dictLabels('vehicle_brand'); } },
    { key: 'model', label: '型号', type: 'select', placeholder: '先选品牌',
      depends: { field: 'brand', dict: 'vehicle_model', parentDict: 'vehicle_brand' } },
    { key: 'type', label: '车型', type: 'select', options: function () { return dictLabels('vehicle_type'); } },
    { key: 'color', label: '车身颜色' },
    { key: 'fuelType', label: '燃料类型', type: 'select', options: function () { return dictLabels('vehicle_fuel'); } },
    { key: 'status', label: '状态', type: 'select', required: true, options: function () { return dictLabels('vehicle_status'); } },
    { key: 'driverIdCard', label: '驾驶员身份证', placeholder: '输入后自动带出姓名' },
    { key: 'driverName', label: '驾驶员姓名', readonly: true, placeholder: '按身份证号自动获取' },
    { key: 'driverId', type: 'hidden' },
    { key: 'seats', label: '核定人数', type: 'number', step: '1' },
    { key: 'mileage', label: '里程(km)', type: 'number', step: '0.1' },
    { key: 'purchaseDate', label: '购置日期', type: 'date' },
    { key: 'insuranceExpiry', label: '保险到期日', type: 'date' },
    { key: 'inspectionExpiry', label: '年检到期日', type: 'date' },
    { key: 'remark', label: '备注', type: 'textarea', span: 'full' }
  ],
  beforeSave: function (data) {
    // driverIdCard/driverName 仅用于表单展示,不提交
    delete data.driverIdCard;
    delete data.driverName;
    return data;
  }
};

/** 节假日配色:同名节日永远同色(稳定哈希),日期命中节日也用该色 */
var HOLIDAY_COLOR_CACHE = {};
function holidayColor(name) {
  if (!name) { return ''; }
  if (HOLIDAY_COLOR_CACHE[name]) { return HOLIDAY_COLOR_CACHE[name]; }
  var palette = ['#d9463e', '#e8960e', '#2ea44f', '#7048b7', '#0d8484', '#b26a00', '#2f6fd6', '#c2410c'];
  var h = 0;
  for (var i = 0; i < name.length; i++) { h = (h * 31 + name.charCodeAt(i)) & 0xffff; }
  HOLIDAY_COLOR_CACHE[name] = palette[h % palette.length];
  return HOLIDAY_COLOR_CACHE[name];
}
var HOLIDAY_MAP = {};   // '2026-10-01' → '国庆节'
function loadHolidayMap() {
  return GET('/api/leave/holidays').then(function (list) {
    HOLIDAY_MAP = {};
    (list || []).forEach(function (x) {
      var d = x.holiday_date || x.holidayDate;
      var n = x.holiday_name || x.holidayName || '';
      if (d) { HOLIDAY_MAP[d] = n; }
    });
  }).catch(function () { });
}
/** 日期区间内命中某节日的天数(加假天数为空时自动推算) */
function holidayDaysInRange(start, end, name) {
  if (!start || !end) { return 0; }
  var s = new Date(start + 'T00:00:00'), e = new Date(end + 'T00:00:00');
  if (isNaN(s) || isNaN(e) || e < s) { return 0; }
  var n = 0, guard = 0;
  while (s <= e && guard < 120) {
    var key = s.getFullYear() + '-' + ('0' + (s.getMonth() + 1)).slice(-2) + '-' + ('0' + s.getDate()).slice(-2);
    var fest = HOLIDAY_MAP[key];
    if (fest && (!name || fest === name || name.indexOf(fest) >= 0 || fest.indexOf(name) >= 0)) { n++; }
    s.setDate(s.getDate() + 1);
    guard++;
  }
  return n;
}

/** 日期单元格渲染:命中节日 → 同色小方块+悬停节日名 */
function holiDateCell(date) {
  if (!date) { return ''; }
  var fest = HOLIDAY_MAP[date];
  if (fest) {
    return '<span title="' + esc(fest) + '"><span class="date-dot" style="background:' + holidayColor(fest) + '"></span>' + esc(date) + '</span>';
  }
  return esc(date);
}

/** 休假档案:某人全部历史休假(按年度分组+汇总) */
function openLeaveHistory(rw, act) {
  var body = h('div');
  var m = openModal({
    title: '休假档案 · ' + (rw.personnelName || ''),
    body: body, wide: true,
    footer: [
      h('button', { class: 'btn btn-ghost', text: '编辑本条', onclick: function () { m.close(); act.edit(); } }),
      h('button', { class: 'btn btn-primary', text: '关闭', onclick: function () { m.close(); } })
    ]
  });
  body.appendChild(h('div', { class: 'loading-inline', text: '加载中…' }));
  var q = '/api/leave?size=500';
  if (rw.idCard) { q += '&idCard=' + encodeURIComponent(rw.idCard); }
  else { q += '&personnelName=' + encodeURIComponent(rw.personnelName || ''); }
  var ready = Object.keys(HOLIDAY_MAP).length ? Promise.resolve() : loadHolidayMap();
  ready.then(function () {
  GET(q).then(function (d) {
    var rows = (d && d.list) || [];
    body.innerHTML = '';
    if (!rows.length) {
      body.appendChild(h('div', { class: 'empty-row', text: '暂无休假记录' }));
      return;
    }
    // 汇总
    var totalDays = 0, yearNow = String(new Date().getFullYear()), nowDays = 0;
    rows.forEach(function (x) {
      var t = (Number(x.days) || 0) + (Number(x.travelDays) || 0) + (Number(x.holidayDays) || 0);
      totalDays += t;
      if (String(x.year) === yearNow) { nowDays += t; }
    });
    body.appendChild(h('div', { style: 'display:flex;gap:16px;margin-bottom:14px;flex-wrap:wrap' },
      h('span', { style: 'background:#eef4ff;color:#16437e;border-radius:8px;padding:6px 14px;font-size:13px', text: '累计休假 ' + rows.length + ' 次' }),
      h('span', { style: 'background:#e9f7ef;color:#22633c;border-radius:8px;padding:6px 14px;font-size:13px', text: '累计 ' + (Math.round(totalDays * 10) / 10) + ' 天(含路途/节假日)' }),
      h('span', { style: 'background:#fdf1e7;color:#8a5a1a;border-radius:8px;padding:6px 14px;font-size:13px', text: yearNow + ' 年已休 ' + (Math.round(nowDays * 10) / 10) + ' 天' })));
    // 按年度分组(新→旧)
    var byYear = {};
    rows.forEach(function (x) { (byYear[x.year || '未填年度'] = byYear[x.year || '未填年度'] || []).push(x); });
    var years = Object.keys(byYear).sort(function (a, b) { return a < b ? 1 : -1; });
    years.forEach(function (y) {
      var list = byYear[y];
      var yDays = 0;
      list.forEach(function (x) { yDays += (Number(x.days) || 0) + (Number(x.travelDays) || 0) + (Number(x.holidayDays) || 0); });
      body.appendChild(h('div', { style: 'font-weight:600;color:#1d2c40;margin:12px 0 6px;font-size:14px', text: y + ' 年(' + list.length + ' 次 · 共 ' + (Math.round(yDays * 10) / 10) + ' 天)' }));
      var table = h('table', { class: 'grid', style: 'width:100%' });
      table.appendChild(h('thead', {}, h('tr', {},
        h('th', { text: '类型' }), h('th', { text: '开始' }), h('th', { text: '结束' }),
        h('th', { text: '基本' }), h('th', { text: '路途' }), h('th', { text: '节假日' }),
        h('th', { text: '合计' }), h('th', { text: '事由' }))));
      var tb = h('tbody');
      list.forEach(function (x) {
        var sum = (Number(x.days) || 0) + (Number(x.travelDays) || 0) + (Number(x.holidayDays) || 0);
        tb.appendChild(h('tr', {},
          h('td', { html: badge(x.leaveType) }),
          h('td', { html: holiDateCell(x.startDate) }),
          h('td', { html: holiDateCell(x.endDate) }),
          h('td', { text: String(Number(x.days) || 0) }),
          h('td', { text: Number(x.travelDays) > 0 ? String(x.travelDays) : '—' }),
          h('td', { html: (function () {
              var d = Number(x.holidayDays);
              if (x.holidayName) {
                if (!(d > 0)) { d = holidayDaysInRange(x.startDate, x.endDate, x.holidayName); }
                return '<span class="holi-chip" style="background:' + holidayColor(x.holidayName) + '">' + esc(x.holidayName) + (d > 0 ? ' ' + d + '天' : '') + '</span>';
              }
              return d > 0 ? d + '天' : '—';
            })() }),
          h('td', { html: '<b>' + (Math.round(sum * 10) / 10) + '</b>' }),
          h('td', { text: x.reason || '', style: 'max-width:160px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap' })));
      });
      table.appendChild(tb);
      body.appendChild(h('div', { class: 'table-wrap' }, table));
    });
  }).catch(function (e) {
    body.innerHTML = '';
    body.appendChild(h('div', { class: 'empty-row', text: e.message }));
  });
  });
}

var leaveCfg = {
  key: 'leave', title: '休假管理', icon: 'calendar', endpoint: '/api/leave',
  clickKey: 'personnelName', addLabel: '新增休假',
  filters: [
    { key: 'personnelName', label: '姓名' },
    { key: 'leaveType', label: '休假类型', type: 'select', options: function () { return dictLabels('leave_type'); } }
  ],
  moreFilters: [
    { key: 'year', label: '年度', placeholder: '如 2026' },
    { key: 'startDate', label: '开始日期', type: 'date' },
    { key: 'endDate', label: '结束日期', type: 'date' }
  ],
  columns: [
    { key: 'personnelName', label: '姓名' },
    { key: 'idCard', label: '身份证号', render: function (r) {
        return r.idCard ? '<span class="view-detail" data-view-id="' + r.id + '" title="点击查看休假档案"><b>' + esc(r.idCard) + '</b></span>' : '—';
      } },
    { key: 'year', label: '年度', render: function (r) { return r.year || '—'; } },
    { key: 'leaveType', label: '休假类型', render: function (r) { return badge(r.leaveType); } },
    { key: 'startDate', label: '开始日期', render: function (r) { return holiDateCell(r.startDate); } },
    { key: 'endDate', label: '结束日期', render: function (r) { return holiDateCell(r.endDate); } },
    { key: 'days', label: '基本天数' },
    { key: 'travelDays', label: '路途', render: function (r) { return Number(r.travelDays) > 0 ? Number(r.travelDays) + '天' : '—'; } },
    { key: 'holidayDays', label: '节假日', render: function (r) {
        var d = Number(r.holidayDays);
        if (r.holidayName) {
          if (!(d > 0)) { d = holidayDaysInRange(r.startDate, r.endDate, r.holidayName); }
          return '<span class="holi-chip" style="background:' + holidayColor(r.holidayName) + '">' + esc(r.holidayName) + (d > 0 ? ' ' + d + '天' : '') + '</span>';
        }
        return d > 0 ? d + '天' : '—';
      } },
    { key: 'reason', label: '事由' }
  ],
  rowTitle: function (r) { return r.personnelName; },
  delTip: function (r) { return '确定删除「' + r.personnelName + '」的这条休假记录吗?'; },
  // 点击姓名 → 该人历史休假档案
  onView: function (rw, act) { openLeaveHistory(rw, act); },
  form: [
    { key: 'idCard', label: '身份证号', required: true, placeholder: '输入后自动带出姓名' },
    { key: 'personnelName', label: '姓名', readonly: true, placeholder: '按身份证号自动获取' },
    { key: 'personnelId', type: 'hidden', required: true },
    { key: 'year', label: '休假年度', type: 'number', required: true, min: '1990', max: '2100', step: '1', placeholder: '如 2026' },
    { key: 'leaveType', label: '休假类型', type: 'select', required: true, options: function () { return dictLabels('leave_type'); } },
    { key: 'startDate', label: '开始日期', type: 'date', required: true },
    { key: 'days', label: '基本天数', type: 'number', step: '0.5', required: true, placeholder: '不含路途和节假日' },
    { key: 'travelDays', label: '路途天数', type: 'number', step: '0.5', placeholder: '每年度限一次' },
    { key: 'holidayName', label: '节假日', type: 'select', options: function () { return dictLabels('holiday_type'); } },
    { key: 'holidayDays', label: '节假日加假', type: 'number', step: '0.5', placeholder: '手动填写' },
    { key: 'endDate', label: '结束日期', type: 'date', placeholder: '自动计算', readonly: true },
    { key: 'totalDays', label: '总天数', readonly: true, placeholder: '自动计算' },
    { key: 'reason', label: '事由', span: 'full' },
    { key: 'remark', label: '备注', type: 'textarea', span: 'full' }
  ],
  beforeSave: function (data) {
    // idCard/personnelName/totalDays 仅用于表单展示,不提交
    delete data.idCard;
    delete data.personnelName;
    delete data.totalDays;
    return data;
  }
};

var fitnessCfg = {
  key: 'fitness', title: '体能考核', icon: 'activity', endpoint: '/api/fitness',
  clickKey: 'personnelName', addLabel: '新增考核记录',
  toolbarExtra: function (bar, act) {
    bar.appendChild(h('button', {
      class: 'btn btn-ghost', text: '体能标准',
      title: '查看体能考核评分标准(成绩→分数)',
      onclick: function () { openFitnessStdManager(); }
    }));
    bar.appendChild(h('button', {
      class: 'btn btn-ghost', text: '评定标准',
      title: '设置多少分为及格/良好/优秀',
      onclick: function () { openGradeRuleManager(); }
    }));
  },
  filters: [
    { key: 'personnelName', label: '姓名' },
    { key: 'idCard', label: '身份证号' },
    { key: 'item', label: '项目', type: 'select', options: function () { return Object.keys(FITNESS_MATRIX_CACHE); } }
  ],
  moreFilters: [
    { key: 'grade', label: '评定', type: 'select', options: function () {
        var l = dictLabels('fitness_grade');
        return l.length ? l : ['优秀', '良好', '及格', '不及格'];
      } },
    { key: 'startDate', label: '起始日期', type: 'date' },
    { key: 'endDate', label: '终止日期', type: 'date' }
  ],
  columns: [
    { key: 'personnelName', label: '姓名' },
    { key: 'idCard', label: '身份证号', render: function (r) {
        return r.idCard ? '<span class="view-detail" data-view-id="' + r.id + '" title="点击查看体能档案"><b>' + esc(r.idCard) + '</b></span>' : '—';
      } },
    { key: 'item', label: '考核项目' },
    { key: 'score', label: '成绩' },
    { key: 'points', label: '分数', render: function (r) { return r.points != null && r.points !== '' ? r.points : '—'; } },
    { key: 'grade', label: '评定', render: function (r) { return badge(r.grade); } },
    { key: 'testDate', label: '考核日期' }
  ],
  rowTitle: function (r) { return r.personnelName + ' · ' + r.item; },
  // 点击姓名或身份证号 → 体能档案大窗口(3D人体+基本信息+走势)
  onView: function (rw, act) { openPersonProfile(rw, act); },
  form: [
    { key: 'personnelId', label: '考核人员', type: 'personnel', required: true },
    { key: 'item', label: '考核项目', type: 'select', options: function () { return dictLabels('fitness_item'); }, required: true },
    { key: 'score', label: '成绩', placeholder: '如 12\'30 或 45 次' },
    { key: 'points', label: '分数', type: 'number', step: '0.5', placeholder: '按体能标准评分' },
    {
      key: 'grade', label: '评定', type: 'select', required: true,
      options: function () { var l = dictLabels('fitness_grade'); return l.length ? l : ['优秀', '良好', '及格', '不及格']; }
    },
    { key: 'testDate', label: '考核日期', type: 'date', required: true },
    { key: 'remark', label: '备注', type: 'textarea', span: 'full' }
  ]
};

/* ================= 部门管理视图(树) ================= */

function departmentView() {
  var root = h('div');
  var kw = '';          // 搜索关键字
  var statsEl = h('div', { class: 'dept-stats' });
  var stats = { depts: 0, assigned: 0, personTotal: 0 };

  function walk(nodes, fn) {
    nodes.forEach(function (n) {
      fn(n);
      if (n.children && n.children.length) { walk(n.children, fn); }
    });
  }

  /** 标记命中节点:自身或任一下级名称含关键字则保留;命中路径上的父级强制展开 */
  function filterTree(nodes) {
    var any = false;
    nodes.forEach(function (n) {
      n._hit = !!kw && (n.name || '').indexOf(kw) >= 0;
      var kidAny = (n.children && n.children.length) ? filterTree(n.children) : false;
      n._keep = n._hit || kidAny;
      if (kw && kidAny) { n._open = true; }
      if (n._keep) { any = true; }
    });
    return any;
  }

  function chip(text, color) {
    return h('span', { class: 'dept-chip', style: color ? 'color:' + color : '', text: text });
  }

  function openDeptPersonnel(node) {
    var body = h('div');
    var m = openModal({
      title: node.name + ' · 直属人员(' + (node.count || 0) + ' 人)',
      body: body, wide: true,
      footer: [
        h('button', {
          class: 'btn btn-ghost', text: '去人员管理筛选此部门',
          onclick: function () {
            m.close();
            if (location.hash !== '#personnel') { location.hash = 'personnel'; }
            var tries = 0;
            (function apply() {
              var el = document.querySelector('[data-fkey="departmentId"]');
              if (el) {
                el.value = String(node.id);
                el.dispatchEvent(new Event('change', { bubbles: true }));
              } else if (++tries < 10) {
                setTimeout(apply, 300);
              }
            })();
          }
        }),
        h('button', { class: 'btn btn-primary', text: '关闭', onclick: function () { m.close(); } })
      ]
    });
    body.appendChild(h('div', { class: 'loading-inline', text: '加载中…' }));
    GET('/api/personnel?departmentId=' + node.id + '&size=200').then(function (d) {
      var rows = (d && d.list) || [];
      body.innerHTML = '';
      var table = h('table', { class: 'grid', style: 'width:100%' });
      table.appendChild(h('thead', {}, h('tr', {},
        h('th', { text: '姓名' }), h('th', { text: '性别' }), h('th', { text: '人员类别' }),
        h('th', { text: '军衔' }), h('th', { text: '职务' }), h('th', { text: '状态' }))));
      var tbody = h('tbody');
      rows.forEach(function (p) {
        tbody.appendChild(h('tr', {},
          h('td', { text: p.name || '' }),
          h('td', { text: p.gender || '—' }),
          h('td', { text: p.category || '—' }),
          h('td', { text: p.rank || '—' }),
          h('td', { text: p.post || '—' }),
          h('td', { html: badge(p.status) })));
      });
      table.appendChild(tbody);
      body.appendChild(h('div', { class: 'table-wrap', style: 'max-height:52vh;overflow:auto' }, table));
      if (!rows.length) {
        body.appendChild(h('div', { class: 'empty-row', text: '该部门暂无直属人员' }));
      }
      if ((node.total || 0) > (node.count || 0)) {
        body.appendChild(h('div', {
          style: 'margin-top:10px;font-size:13px;color:#66788c',
          text: '含下级部门共 ' + node.total + ' 人,此处仅列出直属人员;点击"去人员管理筛选此部门"可查看并维护。'
        }));
      }
    }).catch(function (e) {
      body.innerHTML = '';
      body.appendChild(h('div', { class: 'empty-row', text: e.message }));
    });
  }

  function nodeRow(node, depth) {
    var hasKids = node.children && node.children.length;
    var expand = hasKids
      ? h('span', { class: 'tw', text: node._open === false ? '▸' : '▾', onclick: function () { node._open = node._open === false; renderTree(); } })
      : h('span', { class: 'tw', text: '·' });
    var nameHtml = kw && node._hit
      ? esc(node.name).split(esc(kw)).join('<b>' + esc(kw) + '</b>')
      : esc(node.name);
    var row = h('div', { class: 'dept-row' }, expand,
      h('span', { class: 'dept-ic', html: iconSvg('building') }),
      h('span', {
        class: 'name clickable', html: nameHtml, title: '点击查看该部门人员',
        onclick: function () { openDeptPersonnel(node); }
      }),
      node.leaderName ? chip('负责人 ' + node.leaderName, '#66788c') : null,
      chip('直属 ' + (node.count || 0) + ' 人', (node.count || 0) > 0 ? '#2f6fd6' : ''),
      hasKids && (node.total || 0) !== (node.count || 0) ? chip('含下级 ' + (node.total || 0) + ' 人', '#2ea44f') : null,
      hasKids ? chip(hasKids + ' 个子部门', '') : null,
      h('span', { class: 'acts' },
        (node.count || 0) > 0 ? h('button', { class: 'btn-link', text: '查看人员', onclick: function () { openDeptPersonnel(node); } }) : null,
        h('button', { class: 'btn-link', text: '加子级', onclick: function () { openForm(null, node.id); } }),
        h('button', { class: 'btn-link', text: '编辑', onclick: function () { openForm(node); } }),
        h('button', {
          class: 'btn-link danger', text: '删除',
          onclick: function () {
            confirmBox('确定删除部门「' + node.name + '」吗?若其下有子部门或人员将无法删除。', true).then(function (yes) {
              if (!yes) return;
              DEL('/api/departments/' + node.id).then(function () {
                toast('已删除');
                refreshDepts().then(function () { loadStats(); renderTree(); });
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
    if (kw) { filterTree(Ctx.deptTree); }
    var found = 0;
    (function emit(nodes, depth) {
      nodes.forEach(function (n) {
        if (kw && !n._keep) { return; }
        found++;
        box.appendChild(nodeRow(n, depth));
        if (n._open !== false && n.children && n.children.length) { emit(n.children, depth + 1); }
      });
    })(Ctx.deptTree, 0);
    if (kw && !found) {
      box.appendChild(h('div', { class: 'empty-row', text: '没有名称包含「' + kw + '」的部门' }));
    }
  }

  function renderStats() {
    statsEl.innerHTML = '';
    var unassigned = Math.max(0, stats.personTotal - stats.assigned);
    [
      { num: stats.depts, lbl: '部门总数', ic: 'building', bg: '#e8f0fd', fg: '#2f6fd6' },
      { num: stats.personTotal, lbl: '人员总数', ic: 'users', bg: '#e9f7ef', fg: '#2ea44f' },
      { num: stats.assigned, lbl: '已分配部门', ic: 'check', bg: '#eef4ff', fg: '#16437e' },
      { num: unassigned, lbl: '未分配部门', ic: 'shield', bg: '#fdf1e7', fg: '#e8743b' }
    ].forEach(function (c) {
      statsEl.appendChild(h('div', { class: 'dept-stat-card' },
        h('span', { class: 'dept-stat-ic', style: 'background:' + c.bg + ';color:' + c.fg, html: iconSvg(c.ic) }),
        h('div', {},
          h('div', { class: 'num', text: String(c.num) }),
          h('div', { class: 'lbl', text: c.lbl }))));
    });
  }

  function loadStats() {
    stats.depts = 0;
    stats.assigned = 0;
    walk(Ctx.deptTree, function (n) {
      stats.depts++;
      stats.assigned += n.count || 0;
    });
    GET('/api/personnel?size=1').then(function (d) {
      stats.personTotal = (d && d.total) || 0;
    }).catch(function () { stats.personTotal = 0; }).then(renderStats);
  }

  function openForm(node, parentId) {
    var form = buildForm([
      { key: 'name', label: '部门名称', required: true },
      { key: 'parentId', label: '上级部门', type: 'dept' },
      { key: 'leaderId', label: '负责人', type: 'personnel' },
      { key: 'sort', label: '排序号', type: 'number', step: '1', placeholder: '小的排前面' },
      { key: 'remark', label: '备注', span: 'full' }
    ], node
      ? { name: node.name, parentId: node.parentId, leaderId: node.leaderId > 0 ? node.leaderId : null, sort: node.sort, remark: node.remark }
      : { parentId: parentId || null });
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
            if (data.leaderId === '' || data.leaderId == null) data.leaderId = 0;
            if (data.sort === null) data.sort = 0;
            var done = node ? PUT('/api/departments/' + node.id, data) : POST('/api/departments', data);
            done.then(function () {
              m.close();
              toast('保存成功');
              refreshDepts().then(function () { loadStats(); renderTree(); });
            }).catch(function (e) { form.setError(e.message); });
          }
        })
      ]
    });
  }

  var searchTimer = null;
  var searchInput = h('input', {
    type: 'text', class: 'dept-search', placeholder: '搜索部门名称…',
    style: 'margin-left:8px'
  });
  searchInput.addEventListener('input', function () {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(function () {
      kw = searchInput.value.trim();
      renderTree();
    }, 200);
  });

  function render(container) {
    root.appendChild(statsEl);
    root.appendChild(h('div', { class: 'panel', style: 'margin-top:14px' },
      h('div', { class: 'dept-toolbar' },
        h('button', { class: 'btn btn-primary', text: '＋ 新增部门', onclick: function () { openForm(null, null); } }),
        h('button', {
          class: 'btn btn-ghost', text: '全部展开', style: 'margin-left:8px',
          onclick: function () { walk(Ctx.deptTree, function (n) { n._open = true; }); renderTree(); }
        }),
        h('button', {
          class: 'btn btn-ghost', text: '全部收起',
          onclick: function () { walk(Ctx.deptTree, function (n) { n._open = false; }); renderTree(); }
        }),
        searchInput,
        h('button', {
          class: 'btn btn-ghost', text: '刷新', style: 'margin-left:auto',
          onclick: function () { refreshDepts().then(function () { loadStats(); renderTree(); }); }
        })),
      h('div', { class: 'dept-tree' })));
    renderTree();
    loadStats();
    container.appendChild(root);
  }
  return { render: render };
}

/* ================= 数据字典视图 ================= */

function dictView() {
  var root = h('div');
  var currentType = null;
  var pendingHighlightPid = null; // 点击"N个下级"跳转后,高亮该父项的下级行

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

  /** 记住最后浏览的字典类型,刷新后停留在原处 */
  function saveCurrentType() {
    if (currentType) {
      try { localStorage.setItem('lastDictType', currentType); } catch (e) { /* 忽略 */ }
    }
  }

  function renderItems() {
    var main = $('.dict-main', root);
    main.innerHTML = '';
    if (!currentType) {
      main.appendChild(h('div', { class: 'panel' }, h('div', { class: 'empty-row', text: '暂无字典数据' })));
      return;
    }
    saveCurrentType();
    var g = typeGroup(currentType) || { items: [] };
    var items = g.items || [];
    var byId = {};
    items.forEach(function (d) { byId[String(d.id)] = d; });

    // 层级排序:同类型下级紧跟父项(缩进);跨类型下级(如 型号→品牌)按上级分组追加
    var ordered = [];
    var seen = {};
    items.forEach(function (d) {
      if (d.parentId) return;
      ordered.push(d);
      seen[String(d.id)] = true;
      items.forEach(function (c) {
        if (String(c.parentId) === String(d.id)) {
          ordered.push(c);
          seen[String(c.id)] = true;
        }
      });
    });
    var cross = items.filter(function (d) { return d.parentId && !seen[String(d.id)]; });
    if (cross.length) {
      var groups = {};
      cross.forEach(function (c) {
        var pl = '';
        Ctx.dictTypes.forEach(function (g2) {
          (g2.items || []).forEach(function (x) {
            if (String(x.id) === String(c.parentId)) pl = x.dictLabel;
          });
        });
        c._parentLabel = pl || '未指定上级';
        (groups[c._parentLabel] = groups[c._parentLabel] || []).push(c);
      });
      Object.keys(groups).sort().forEach(function (pl) {
        groups[pl].forEach(function (c) { ordered.push(c); });
      });
    }
    function globalChildCount(d) {
      var n = 0;
      Ctx.dictTypes.forEach(function (g2) {
        (g2.items || []).forEach(function (x) {
          if (String(x.parentId) === String(d.id)) n++;
        });
      });
      return n;
    }
    /** 下级所在的字典类型(取数量最多的) */
    function childMainType(d) {
      var counts = {};
      Ctx.dictTypes.forEach(function (g2) {
        (g2.items || []).forEach(function (x) {
          if (String(x.parentId) === String(d.id)) counts[g2.type] = (counts[g2.type] || 0) + 1;
        });
      });
      var best = null, bestN = -1;
      Object.keys(counts).forEach(function (t) { if (counts[t] > bestN) { bestN = counts[t]; best = t; } });
      return best;
    }

    var table = h('table', { class: 'grid' });
    table.innerHTML = '<thead><tr><th>选项内容</th><th>排序</th><th>状态</th><th>备注</th><th style="width:150px">操作</th></tr></thead><tbody>' +
      (ordered.length ? ordered.map(function (d, ri) {
        var isChild = !!d.parentId;
        var childCount = globalChildCount(d);
        var rowFlash = (pendingHighlightPid && String(d.parentId) === String(pendingHighlightPid)) ? ' class="row-flash"' : '';
        var label;
        if (d._parentLabel) {
          label = '<span class="dict-child">↳ ' + esc(d._parentLabel) + ' · ' + esc(d.dictLabel) + '</span>';
        } else if (isChild) {
          label = '<span class="dict-child">└ ' + esc(d.dictLabel) + '</span>';
        } else {
          var ct = childMainType(d);
          label = esc(d.dictLabel) + (childCount
            ? ' <span class="badge blue dict-child-link" data-ctype="' + esc(ct || '') + '" data-pid="' + d.id + '" title="点击查看下级">' + childCount + '个下级 ›</span>'
            : '');
        }
        return '<tr' + rowFlash + '><td>' + label + '</td><td>' + esc(d.sort) + '</td>' +
          '<td>' + (d.enabled === false ? '<span class="badge gray">停用</span>' : '<span class="badge green">启用</span>') + '</td>' +
          '<td>' + esc(d.remark) + '</td>' +
          '<td class="actions">' +
          (isChild ? '' : '<button class="btn-link" data-sub="' + d.id + '">＋下级</button>') +
          '<button class="btn-link" data-edit="' + d.id + '">编辑</button>' +
          '<button class="btn-link danger" data-del="' + d.id + '">删除</button></td></tr>';
      }).join('') : '<tr><td colspan="5" class="empty-row">该类型暂无选项</td></tr>') + '</tbody>';
    pendingHighlightPid = null;
    table.addEventListener('click', function (e) {
      // 点击"N个下级"徽标 → 跳到下级所在类型并高亮
      var link = e.target.closest('.dict-child-link');
      if (link) {
        var ctype = link.getAttribute('data-ctype');
        if (ctype && typeGroup(ctype)) {
          currentType = ctype;
          pendingHighlightPid = link.getAttribute('data-pid');
          renderTypeList();
          renderItems();
        }
        return;
      }
      var b = e.target.closest('button');
      if (!b) return;
      var subId = b.getAttribute('data-sub');
      var id = b.getAttribute('data-edit') || b.getAttribute('data-del') || subId;
      var item = byId[String(id)];
      if (!item) return;
      if (subId) {
        openForm(null, item);
      } else if (b.hasAttribute('data-del')) {
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

  /** item=编辑对象;parentItem=新增下级时的父项 */
  function openForm(item, parentItem) {
    var items = (typeGroup(currentType) || {}).items || [];
    // 检测上级引用类型:本类型下级的父项若属于其他类型(如 车辆型号→车辆品牌),表单提供上级下拉
    var refType = null;
    items.forEach(function (d) {
      if (!d.parentId) return;
      Ctx.dictTypes.forEach(function (g2) {
        (g2.items || []).forEach(function (x) {
          if (String(x.id) === String(d.parentId) && g2.type !== currentType) refType = g2.type;
        });
      });
    });
    var hasHierarchy = items.some(function (d) { return d.parentId; }) || !!parentItem;
    var parentTypeForForm = refType || currentType;
    function labelForId(pid) {
      var found = '';
      dictItems(parentTypeForForm).forEach(function (d) { if (String(d.id) === String(pid)) found = d.dictLabel; });
      return found;
    }
    function idForLabel(lb) {
      var found = 0;
      dictItems(parentTypeForForm).forEach(function (d) { if (d.dictLabel === lb) found = d.id; });
      return found;
    }
    var parentOptions = dictItems(parentTypeForForm).filter(function (d) {
      if (d.enabled === false) return false;
      // 同类型时只能选顶级项,且不能选自己
      if (parentTypeForForm === currentType) return !d.parentId && String(d.id) !== String(item ? item.id : '');
      return true;
    }).map(function (d) { return d.dictLabel; });

    var fields = [
      { key: 'dictLabel', label: '选项内容', required: true },
      { key: '_parent', label: '上级', type: 'select', options: function () { return parentOptions; },
        placeholder: parentTypeForForm === currentType ? '无(顶级)' : ('请选择' + ((typeGroup(parentTypeForForm) || {}).name || '')) },
      { key: 'sort', label: '排序号', type: 'number', step: '1' },
      { key: 'enabled', label: '状态', type: 'select', required: true, options: [{ v: '1', l: '启用' }, { v: '0', l: '停用' }] },
      { key: 'remark', label: '备注', span: 'full' }
    ];
    if (!hasHierarchy) fields.splice(1, 1); // 该类型尚无下级时不显示上级选择

    var preset = item
      ? { dictLabel: item.dictLabel, sort: item.sort, enabled: item.enabled === false ? '0' : '1', remark: item.remark }
      : { enabled: '1', sort: 0 };
    if (hasHierarchy) {
      preset._parent = item && item.parentId ? labelForId(item.parentId)
        : (parentItem ? (parentItem.dictType === parentTypeForForm ? parentItem.dictLabel : labelForId(parentItem.id)) : '');
    }
    var form = buildForm(fields, preset);
    var m = openModal({
      title: item
        ? '编辑选项 - ' + item.dictLabel
        : (parentItem ? '新增下级 · ' + parentItem.dictLabel + ' 之下' : '新增选项 · ' + (typeGroup(currentType) || {}).name),
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
            if (hasHierarchy) {
              data.parentId = data._parent ? idForLabel(data._parent) : 0;
            } else {
              data.parentId = item ? (item.parentId || 0) : (parentItem ? parentItem.id : 0);
            }
            delete data._parent;
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
    if (!currentType && Ctx.dictTypes.length) {
      // 恢复上次浏览的类型;已不存在则回退到第一个
      var saved = null;
      try { saved = localStorage.getItem('lastDictType'); } catch (e) { /* 忽略 */ }
      currentType = (saved && typeGroup(saved)) ? saved : Ctx.dictTypes[0].type;
    }
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

/* ================= 仪表盘 ================= */

var DASH_COLORS = ['#2f6fd6', '#2ea44f', '#e8960e', '#9333ea', '#d9463e', '#0d9488', '#b26a00', '#66788c'];

/** 仪表盘数据维度:cat=所属模块 group=接口分组 trend=按年趋势 gauge=仪表 drill=点击下钻 */
var DASH_DIMS = {
  department:       { cat: '人员管理', label: '各部门人数',  group: 'department', unit: '人', drill: { api: '/api/personnel', param: 'departmentId' } },
  status:           { cat: '人员管理', label: '人员状态',    group: 'status', drill: { api: '/api/personnel', param: 'status' } },
  gender:           { cat: '人员管理', label: '性别分布',    group: 'gender', drill: { api: '/api/personnel', param: 'gender' } },
  category:         { cat: '人员管理', label: '人员类别',    group: 'category', drill: { api: '/api/personnel', param: 'category' } },
  rank:             { cat: '人员管理', label: '军衔分布',    group: 'rank', unit: '人', drill: { api: '/api/personnel', param: 'rank' } },
  education:        { cat: '人员管理', label: '学历分布',    group: 'education', drill: { api: '/api/personnel', param: 'education' } },
  politicalStatus:  { cat: '人员管理', label: '政治面貌',    group: 'politicalStatus', drill: { api: '/api/personnel', param: 'politicalStatus' } },
  entryTrend:       { cat: '人员管理', label: '入伍趋势',    trend: true, unit: '人', drill: { api: '/api/personnel', param: 'entryYear' } },
  onDuty:           { cat: '人员管理', label: '人员在位率',  gauge: true, drill: { api: '/api/personnel', param: 'status', fixedVal: '在职' } },
  equipmentCategory:{ cat: '装备管理', label: '装备类别',    group: 'equipmentCategory', unit: '件', drill: { api: '/api/equipment', param: 'category' } },
  equipmentStatus:  { cat: '装备管理', label: '装备状态',    group: 'equipmentStatus', drill: { api: '/api/equipment', param: 'status' } },
  materialCategory: { cat: '物资管理', label: '物资分类',    group: 'materialCategory', drill: { api: '/api/material', param: 'category' } },
  vehicleStatus:    { cat: '车辆管理', label: '车辆状态',    group: 'vehicleStatus', drill: { api: '/api/vehicle', param: 'status' } },
  vehicleBrand:     { cat: '车辆管理', label: '车辆品牌',    group: 'vehicleBrand', unit: '辆', drill: { api: '/api/vehicle', param: 'brand' } },
  leaveType:        { cat: '休假管理', label: '今年休假类型', group: 'leaveType', unit: '次', drill: { api: '/api/leave', param: 'leaveType' } },
  fitnessGrade:     { cat: '体能考核', label: '今年体能评定', group: 'fitnessGrade', drill: { api: '/api/fitness', param: 'grade' } }
};

/** 图表类型中文名 */
var DASH_KINDS = { bar: '条形图', donut: '环形图', rose: '玫瑰图', treemap: '树状图', columns: '柱状图', line: '折线图', gauge: '仪表图' };

/** 每个维度可选的图表类型 */
function kindsForDim(dim) {
  if (dim.gauge) { return ['gauge']; }
  if (dim.trend) { return ['columns', 'line']; }
  return ['bar', 'donut', 'rose', 'treemap'];
}

/** 维度+图型 → 卡片标题 */
function dashCardLabel(dimKey, kind) {
  var dim = DASH_DIMS[dimKey];
  var kinds = kindsForDim(dim);
  return kinds.length === 1 ? dim.label : dim.label + ' · ' + DASH_KINDS[kind];
}

/** 默认布局:[{dim,kind,size}] */
var DASH_DEFAULT = [
  { dim: 'department', kind: 'bar', size: 'm' },
  { dim: 'status', kind: 'donut', size: 'm' },
  { dim: 'entryTrend', kind: 'columns', size: 'l' },
  { dim: 'leaveType', kind: 'bar', size: 'm' }
];

/** 旧版目录 id → {dim,kind} 映射(配置迁移用) */
var DASH_OLD_WIDGET = {
  dept: ['department', 'bar'], deptTree: ['department', 'treemap'],
  status: ['status', 'donut'], statusRose: ['status', 'rose'],
  gender: ['gender', 'donut'], category: ['category', 'donut'],
  rank: ['rank', 'bar'], rankTree: ['rank', 'treemap'],
  education: ['education', 'donut'], political: ['politicalStatus', 'donut'],
  onDutyGauge: ['onDuty', 'gauge'],
  entryTrend: ['entryTrend', 'columns'], entryLine: ['entryTrend', 'line'],
  equipmentCat: ['equipmentCategory', 'bar'], equipmentStatus: ['equipmentStatus', 'donut'],
  materialCat: ['materialCategory', 'donut'],
  vehicleStatus: ['vehicleStatus', 'donut'], vehicleBrand: ['vehicleBrand', 'bar'],
  leaveType: ['leaveType', 'bar'], fitnessGrade: ['fitnessGrade', 'donut']
};

/** 旧版(纯id数组 / {id,size}) → 新版({dim,kind,size}) 兼容迁移 */
function dashMigrate(saved) {
  var out = [];
  (saved || []).forEach(function (w) {
    if (typeof w === 'string' && DASH_OLD_WIDGET[w]) {
      out.push({ dim: DASH_OLD_WIDGET[w][0], kind: DASH_OLD_WIDGET[w][1], size: 'm' });
    } else if (w && w.dim && DASH_DIMS[w.dim] && kindsForDim(DASH_DIMS[w.dim]).indexOf(w.kind) >= 0) {
      out.push({ dim: w.dim, kind: w.kind, size: w.size === 's' || w.size === 'l' ? w.size : 'm' });
    } else if (w && w.id && DASH_OLD_WIDGET[w.id]) {
      out.push({ dim: DASH_OLD_WIDGET[w.id][0], kind: DASH_OLD_WIDGET[w.id][1], size: w.size === 's' || w.size === 'l' ? w.size : 'm' });
    }
  });
  return out;
}

function dashBarList(list, unit, colorClass, drill) {
  if (!list.length) { return h('div', { class: 'empty-row', text: '暂无数据' }); }
  var max = 1;
  list.forEach(function (x) { if (x.count > max) { max = x.count; } });
  var box = h('div');
  list.forEach(function (x) {
    var pct = Math.max(2, Math.round(x.count / max * 100));
    var val = drill ? drill.valOf(x.name) : null;
    var attrs = { class: 'bar-row ' + (colorClass || '') + (val != null ? ' drillable' : '') };
    if (val != null) {
      attrs['data-api'] = drill.api;
      attrs['data-param'] = drill.param;
      attrs['data-val'] = String(val);
      attrs['data-title'] = drill.title + ' - ' + x.name;
    }
    box.appendChild(h('div', attrs,
      h('span', { class: 'bn', title: x.name, text: x.name }),
      h('span', { class: 'bt' }, h('span', { class: 'bf', style: 'width:' + pct + '%' })),
      h('span', { class: 'bv', text: x.count + ' ' + (unit || '') })));
  });
  return box;
}

/** 环形图:SVG 分段圆环 + 中心总数 + 右侧图例;分段可点击下钻 */
function drawDonut(items, drill) {
  var data = (items || []).filter(function (x) { return x.count > 0; });
  var total = 0;
  data.forEach(function (x) { total += x.count; });
  if (!total) { return h('div', { class: 'empty-row', text: '暂无数据' }); }
  var R = 56, SW = 26, C = 2 * Math.PI * R, deg = 0;
  var svg = ['<svg viewBox="0 0 150 150" style="width:150px;height:150px;flex:none">'];
  data.forEach(function (x, i) {
    var len = x.count / total * C;
    var color = DASH_COLORS[i % DASH_COLORS.length];
    var val = drill ? drill.valOf(x.name) : null;
    svg.push('<circle cx="75" cy="75" r="' + R + '" fill="none" stroke="' + color + '" stroke-width="' + SW +
      '" stroke-dasharray="' + len.toFixed(2) + ' ' + (C - len).toFixed(2) +
      '" transform="rotate(' + deg.toFixed(2) + ' 75 75)"' +
      (val != null ? ' class="seg" data-api="' + drill.api + '" data-param="' + drill.param + '" data-val="' + esc(String(val)) + '" data-title="' + esc(drill.title + ' - ' + x.name) + '"' : '') +
      '><title>' + esc(x.name) + ': ' + x.count + (val != null ? '(点击查看)' : '') + '</title></circle>');
    deg += x.count / total * 360;
  });
  svg.push('<text x="75" y="71" text-anchor="middle" font-size="21" font-weight="700" fill="#1d2c40">' + total + '</text>');
  svg.push('<text x="75" y="90" text-anchor="middle" font-size="11" fill="#8a97a8">总计</text>');
  svg.push('</svg>');
  var legend = h('div', { class: 'donut-legend' });
  data.forEach(function (x, i) {
    var pct = Math.round(x.count / total * 100);
    legend.appendChild(h('div', { class: 'row' },
      h('span', { style: 'width:10px;height:10px;border-radius:2px;background:' + DASH_COLORS[i % DASH_COLORS.length] + ';flex:none' }),
      h('span', { style: 'flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap', title: x.name, text: x.name }),
      h('b', { style: 'color:#1d2c40', text: String(x.count) }),
      h('span', { style: 'color:#8a97a8;width:40px;text-align:right;flex:none', text: pct + '%' })));
  });
  return h('div', { class: 'donut-box' }, h('div', { html: svg.join('') }), legend);
}

/** 年度柱状图:近15年入伍趋势等;柱体可点击下钻该年份 */
function drawColumns(items, unit, drill) {
  if (!items || !items.length) { return h('div', { class: 'empty-row', text: '暂无数据' }); }
  var W = 480, H = 220, padL = 34, padR = 10, padT = 20, padB = 28;
  var max = 1;
  items.forEach(function (x) { if (x.count > max) { max = x.count; } });
  var n = items.length;
  var step = (W - padL - padR) / n;
  var bw = Math.min(34, step * 0.62);
  function yAt(v) { return padT + (H - padT - padB) * (1 - v / max); }
  var svg = ['<svg viewBox="0 0 ' + W + ' ' + H + '" style="width:100%;height:auto;display:block">'];
  [0, 0.5, 1].forEach(function (f) {
    var y = yAt(max * f).toFixed(1);
    svg.push('<line x1="' + padL + '" y1="' + y + '" x2="' + (W - padR) + '" y2="' + y + '" stroke="#eef1f5"/>');
    svg.push('<text x="' + (padL - 6) + '" y="' + (+y + 4) + '" text-anchor="end" font-size="10" fill="#8a97a8">' + Math.round(max * f) + '</text>');
  });
  items.forEach(function (x, i) {
    var cx = padL + step * (i + 0.5);
    var y = yAt(x.count);
    var hgt = (H - padB) - y;
    var val = drill ? drill.valOf(String(x.name)) : null;
    svg.push('<rect x="' + (cx - bw / 2).toFixed(1) + '" y="' + y.toFixed(1) + '" width="' + bw.toFixed(1) + '" height="' + Math.max(hgt, 1).toFixed(1) + '" rx="2" fill="' + PRI() + '" opacity="0.88"' +
      (val != null ? ' class="seg" data-api="' + drill.api + '" data-param="' + drill.param + '" data-val="' + esc(String(val)) + '" data-title="' + esc(drill.title + ' - ' + x.name) + '"' : '') +
      '><title>' + esc(x.name) + '年: ' + x.count + ' ' + (unit || '') + (val != null ? '(点击查看)' : '') + '</title></rect>');
    svg.push('<text x="' + cx.toFixed(1) + '" y="' + (y - 4).toFixed(1) + '" text-anchor="middle" font-size="10" fill="#445262" pointer-events="none">' + x.count + '</text>');
    svg.push('<text x="' + cx.toFixed(1) + '" y="' + (H - padB + 16) + '" text-anchor="middle" font-size="9.5" fill="#8a97a8" pointer-events="none">' + esc(String(x.name).slice(2)) + '</text>');
  });
  svg.push('</svg>');
  return h('div', { html: svg.join('') });
}

/** 折线图:趋势;圆点可点击下钻该年份 */
function drawLine(items, unit, drill) {
  if (!items || !items.length) { return h('div', { class: 'empty-row', text: '暂无数据' }); }
  var W = 480, H = 210, padL = 34, padR = 14, padT = 16, padB = 26;
  var max = 1;
  items.forEach(function (x) { if (x.count > max) { max = x.count; } });
  var n = items.length;
  function xAt(i) { return n === 1 ? (padL + (W - padL - padR) / 2) : padL + (W - padL - padR) * i / (n - 1); }
  function yAt(v) { return padT + (H - padT - padB) * (1 - v / max); }
  var pts = [];
  items.forEach(function (x, i) { pts.push(xAt(i).toFixed(1) + ',' + yAt(x.count).toFixed(1)); });
  var svg = ['<svg viewBox="0 0 ' + W + ' ' + H + '" style="width:100%;height:auto;display:block">'];
  [0, 0.5, 1].forEach(function (f) {
    var y = yAt(max * f).toFixed(1);
    svg.push('<line x1="' + padL + '" y1="' + y + '" x2="' + (W - padR) + '" y2="' + y + '" stroke="#eef1f5"/>');
    svg.push('<text x="' + (padL - 6) + '" y="' + (+y + 4) + '" text-anchor="end" font-size="10" fill="#8a97a8">' + Math.round(max * f) + '</text>');
  });
  svg.push('<polygon points="' + padL + ',' + (H - padB) + ' ' + pts.join(' ') + ' ' + (W - padR) + ',' + (H - padB) + '" fill="' + PRI() + '" opacity="0.10"/>');
  svg.push('<polyline points="' + pts.join(' ') + '" fill="none" stroke="' + PRI() + '" stroke-width="2.2" stroke-linejoin="round" stroke-linecap="round"/>');
  items.forEach(function (x, i) {
    var val = drill ? drill.valOf(String(x.name)) : null;
    svg.push('<circle cx="' + xAt(i).toFixed(1) + '" cy="' + yAt(x.count).toFixed(1) + '" r="4.5" fill="' + PRI() + '" stroke="#fff" stroke-width="1.5"' +
      (val != null ? ' class="seg" data-api="' + drill.api + '" data-param="' + drill.param + '" data-val="' + esc(String(val)) + '" data-title="' + esc(drill.title + ' - ' + x.name) + '"' : '') +
      '><title>' + esc(x.name) + '年: ' + x.count + ' ' + (unit || '') + (val != null ? '(点击查看)' : '') + '</title></circle>');
    svg.push('<text x="' + xAt(i).toFixed(1) + '" y="' + (H - padB + 15) + '" text-anchor="middle" font-size="9.5" fill="#8a97a8" pointer-events="none">' + esc(String(x.name).slice(2)) + '</text>');
  });
  svg.push('</svg>');
  return h('div', { html: svg.join('') });
}

/** 树状图(矩形树图):面积∝数量,色块可点击下钻 */
function treemapLayout(items, W, H) {
  var arr = items.filter(function (x) { return x.count > 0; })
    .sort(function (a, b) { return b.count - a.count; })
    .map(function (x, i) { return { name: x.name, count: x.count, i: i }; });
  var out = [];
  function place(list, x, y, w, h) {
    if (!list.length) { return; }
    if (list.length === 1) { out.push({ c: list[0], x: x, y: y, w: w, h: h }); return; }
    var total = 0;
    list.forEach(function (c) { total += c.count; });
    var acc = list[0].count, k = 1;
    while (k < list.length - 1 && acc + list[k].count <= total / 2) { acc += list[k].count; k++; }
    var fa = acc / total;
    var a = list.slice(0, k), b = list.slice(k);
    if (w >= h) { place(a, x, y, w * fa, h); place(b, x + w * fa, y, w * (1 - fa), h); }
    else { place(a, x, y, w, h * fa); place(b, x, y + h * fa, w, h * (1 - fa)); }
  }
  place(arr, 0, 0, W, H);
  return out;
}

function drawTreemap(items, unit, drill) {
  var data = (items || []).filter(function (x) { return x.count > 0; });
  if (!data.length) { return h('div', { class: 'empty-row', text: '暂无数据' }); }
  var W = 520, H = 270;
  var cells = treemapLayout(data, W, H);
  var svg = ['<svg viewBox="0 0 ' + W + ' ' + H + '" style="width:100%;height:auto;display:block">'];
  cells.forEach(function (cell) {
    var color = DASH_COLORS[cell.c.i % DASH_COLORS.length];
    var x = cell.x + 1, y = cell.y + 1, w = Math.max(cell.w - 2, 2), hgt = Math.max(cell.h - 2, 2);
    var val = drill ? drill.valOf(cell.c.name) : null;
    svg.push('<g class="tm-cell' + (val != null ? ' seg' : '') + '"' +
      (val != null ? ' data-api="' + drill.api + '" data-param="' + drill.param + '" data-val="' + esc(String(val)) + '" data-title="' + esc(drill.title + ' - ' + cell.c.name) + '"' : '') + '>');
    svg.push('<rect x="' + x.toFixed(1) + '" y="' + y.toFixed(1) + '" width="' + w.toFixed(1) + '" height="' + hgt.toFixed(1) + '" rx="4" fill="' + color + '"><title>' + esc(cell.c.name) + ': ' + cell.c.count + (unit || '') + (val != null ? '(点击查看)' : '') + '</title></rect>');
    var nameMax = Math.floor(w / 13);
    if (w >= 60 && hgt >= 36) {
      svg.push('<text x="' + (x + 8) + '" y="' + (y + 18) + '" font-size="11.5" fill="#fff" font-weight="600">' + esc(cell.c.name.length > nameMax ? cell.c.name.slice(0, nameMax) + '…' : cell.c.name) + '</text>');
      svg.push('<text x="' + (x + 8) + '" y="' + (y + 38) + '" font-size="14" fill="#fff" font-weight="700">' + cell.c.count + '</text>');
    } else if (w >= 36 && hgt >= 20) {
      svg.push('<text x="' + (x + 5) + '" y="' + (y + hgt / 2 + 5).toFixed(1) + '" font-size="12.5" fill="#fff" font-weight="700">' + cell.c.count + '</text>');
    }
    svg.push('</g>');
  });
  svg.push('</svg>');
  return h('div', { html: svg.join('') });
}

/** 玫瑰图(南丁格尔):角度均分、半径∝数量;扇叶可点击下钻 */
function drawRose(items, drill) {
  var data = (items || []).filter(function (x) { return x.count > 0; });
  var total = 0, max = 1;
  data.forEach(function (x) { total += x.count; if (x.count > max) { max = x.count; } });
  if (!total) { return h('div', { class: 'empty-row', text: '暂无数据' }); }
  var R = 80, cx = 95, cy = 95;
  var step = 360 / data.length;
  var svg = ['<svg viewBox="0 0 190 190" style="width:190px;height:190px;flex:none">'];
  data.forEach(function (x, i) {
    var a1 = (-90 + i * step + 1.5) * Math.PI / 180;
    var a2 = (-90 + (i + 1) * step - 1.5) * Math.PI / 180;
    var r = R * (0.3 + 0.7 * x.count / max);
    var x1 = (cx + r * Math.cos(a1)).toFixed(1), y1 = (cy + r * Math.sin(a1)).toFixed(1);
    var x2 = (cx + r * Math.cos(a2)).toFixed(1), y2 = (cy + r * Math.sin(a2)).toFixed(1);
    var color = DASH_COLORS[i % DASH_COLORS.length];
    var val = drill ? drill.valOf(x.name) : null;
    svg.push('<path d="M ' + cx + ' ' + cy + ' L ' + x1 + ' ' + y1 + ' A ' + r.toFixed(1) + ' ' + r.toFixed(1) + ' 0 0 1 ' + x2 + ' ' + y2 + ' Z" fill="' + color + '" stroke="#fff" stroke-width="1.5"' +
      (val != null ? ' class="seg" data-api="' + drill.api + '" data-param="' + drill.param + '" data-val="' + esc(String(val)) + '" data-title="' + esc(drill.title + ' - ' + x.name) + '"' : '') +
      '><title>' + esc(x.name) + ': ' + x.count + '(' + Math.round(x.count / total * 100) + '%)' + (val != null ? '(点击查看)' : '') + '</title></path>');
  });
  svg.push('<circle cx="' + cx + '" cy="' + cy + '" r="22" fill="#fff"/>');
  svg.push('<text x="' + cx + '" y="' + (cy - 1) + '" text-anchor="middle" font-size="17" font-weight="700" fill="#1d2c40">' + total + '</text>');
  svg.push('<text x="' + cx + '" y="' + (cy + 14) + '" text-anchor="middle" font-size="10" fill="#8a97a8">总计</text>');
  svg.push('</svg>');
  var legend = h('div', { class: 'donut-legend' });
  data.forEach(function (x, i) {
    legend.appendChild(h('div', { class: 'row' },
      h('span', { style: 'width:10px;height:10px;border-radius:2px;background:' + DASH_COLORS[i % DASH_COLORS.length] + ';flex:none' }),
      h('span', { style: 'flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap', title: x.name, text: x.name }),
      h('b', { style: 'color:#1d2c40', text: String(x.count) })));
  });
  return h('div', { class: 'donut-box' }, h('div', { html: svg.join('') }), legend);
}

/** 仪表图:人员在位率等;圆环可点击下钻 */
function drawGauge(d, drill) {
  var total = d.total || 0;
  var onDuty = d.onDuty || 0;
  var pct = total ? Math.round(onDuty / total * 100) : 0;
  var cx = 120, cy = 118, R = 86, SW = 20;
  function polar(aDeg, r) {
    var a = aDeg * Math.PI / 180;
    return (cx + r * Math.cos(a)).toFixed(1) + ' ' + (cy + r * Math.sin(a)).toFixed(1);
  }
  var start = 150, sweep = 240;
  function arc(a1, a2) {
    var large = (a2 - a1) > 180 ? 1 : 0;
    return 'M ' + polar(a1, R) + ' A ' + R + ' ' + R + ' 0 ' + large + ' 1 ' + polar(a2, R);
  }
  var color = pct >= 95 ? '#2ea44f' : pct >= 80 ? '#2f6fd6' : '#e8960e';
  var val = drill ? drill.valOf('在职') : null;
  var svg = ['<svg viewBox="0 0 240 160" style="width:100%;max-width:280px;display:block;margin:0 auto">'];
  svg.push('<path d="' + arc(start, start + sweep) + '" fill="none" stroke="#edf1f6" stroke-width="' + SW + '" stroke-linecap="round"/>');
  if (pct > 0) {
    svg.push('<path d="' + arc(start, start + sweep * pct / 100) + '" fill="none" stroke="' + color + '" stroke-width="' + SW + '" stroke-linecap="round"' +
      (val != null ? ' class="seg" data-api="' + drill.api + '" data-param="' + drill.param + '" data-val="' + esc(String(val)) + '" data-title="' + esc(drill.title) + '"' : '') +
      '><title>在职 ' + onDuty + ' / 共 ' + total + '(' + pct + '%)' + (val != null ? '(点击查看在职名单)' : '') + '</title></path>');
  }
  svg.push('<text x="' + cx + '" y="' + (cy - 6) + '" text-anchor="middle" font-size="30" font-weight="700" fill="#1d2c40">' + pct + '%</text>');
  svg.push('<text x="' + cx + '" y="' + (cy + 16) + '" text-anchor="middle" font-size="11.5" fill="#8a97a8">在职 ' + onDuty + ' / 共 ' + total + '</text>');
  svg.push('</svg>');
  return h('div', { html: svg.join('') });
}

/** 下钻明细弹窗:按 data-api/param/val 调对应模块接口列明细 */
var DRILL_COLS = {
  '/api/personnel': [['name', '姓名'], ['gender', '性别'], ['rank', '军衔'], ['post', '职务'], ['status', '状态'], ['departmentName', '单位']],
  '/api/vehicle': [['plateNo', '车牌号'], ['brand', '品牌'], ['model', '型号'], ['status', '状态']],
  '/api/material': [['name', '名称'], ['category', '分类'], ['quantity', '数量'], ['minQuantity', '预警下限'], ['location', '存放位置']],
  '/api/equipment': [['name', '名称'], ['category', '类别'], ['status', '状态'], ['keeperName', '保管人']],
  '/api/leave': [['personnelName', '姓名'], ['leaveType', '类型'], ['startDate', '开始日期'], ['endDate', '结束日期'], ['days', '天数']],
  '/api/fitness': [['personnelName', '姓名'], ['item', '项目'], ['score', '成绩'], ['points', '分数'], ['grade', '评定'], ['testDate', '考核日期']]
};

function openDrillModal(title, api, param, val) {
  var params = {};
  if (param === 'entryYear') {
    params.entryDateStart = val + '-01-01';
    params.entryDateEnd = val + '-12-31';
  } else {
    params[param] = val;
  }
  var qs = Object.keys(params).map(function (k) { return k + '=' + encodeURIComponent(params[k]); }).join('&');
  var cols = DRILL_COLS[api] || [['name', '名称']];
  var body = h('div');
  var m = openModal({
    title: (title || '明细') + ' · 详情',
    body: body, wide: true,
    footer: [h('button', { class: 'btn btn-primary', text: '关闭', onclick: function () { m.close(); } })]
  });
  body.appendChild(h('div', { class: 'loading-inline', text: '加载中…' }));
  GET(api + '?' + qs + '&size=500').then(function (d) {
    var rows = (d && d.list) || [];
    body.innerHTML = '';
    var table = h('table', { class: 'grid', style: 'width:100%' });
    var thead = h('tr', {});
    cols.forEach(function (c) { thead.appendChild(h('th', { text: c[1] })); });
    table.appendChild(h('thead', {}, thead));
    var tbody = h('tbody');
    rows.forEach(function (r) {
      var tr = h('tr', {});
      cols.forEach(function (c) {
        var v = r[c[0]];
        if (c[0] === 'status' || c[0] === 'grade') {
          tr.appendChild(h('td', { html: badge(v) }));
        } else {
          tr.appendChild(h('td', { text: (v === null || v === undefined || v === '') ? '—' : String(v) }));
        }
      });
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    body.appendChild(h('div', { class: 'table-wrap', style: 'max-height:56vh;overflow:auto' }, table));
    body.appendChild(h('div', { style: 'margin-top:10px;font-size:13px;color:#66788c', text: '共 ' + rows.length + ' 条' }));
    if (!rows.length) {
      body.appendChild(h('div', { class: 'empty-row', text: '没有符合条件的记录' }));
    }
  }).catch(function (e) {
    body.innerHTML = '';
    body.appendChild(h('div', { class: 'empty-row', text: e.message }));
  });
}

function dashboardView() {
  var root = h('div', { class: 'loading-inline', text: '加载中…' });
  var widgets;
  try { widgets = JSON.parse(localStorage.getItem('dashWidgets') || 'null'); } catch (e) { widgets = null; }
  widgets = dashMigrate(widgets);
  if (!widgets.length) {
    widgets = DASH_DEFAULT.map(function (w) { return { dim: w.dim, kind: w.kind, size: w.size }; });
  }
  var lastData = null;
  var lastEvents = [];
  var evFilter = '全部';
  var chartsGrid = h('div', { class: 'dash-grid' });
  var eventsBox = h('div');
  var dragEl = null;

  function saveWidgets() {
    try { localStorage.setItem('dashWidgets', JSON.stringify(widgets)); } catch (e) { /* 忽略 */ }
  }

  /* ---------- 近期事件(带快捷筛选) ---------- */
  function renderEvents() {
    eventsBox.innerHTML = '';
    var counts = {};
    lastEvents.forEach(function (ev) { var t = ev.tag || '动态'; counts[t] = (counts[t] || 0) + 1; });
    var chipDefs = ['全部', '物资', '休假', '生日', '党务', '装备', '车辆'].filter(function (t) { return t === '全部' || counts[t]; });
    var chipBar = h('div', { class: 'ev-chips' });
    chipDefs.forEach(function (t) {
      chipBar.appendChild(h('button', {
        type: 'button', class: 'ev-chip' + (evFilter === t ? ' on' : ''),
        text: t + '(' + (t === '全部' ? lastEvents.length : counts[t]) + ')',
        onclick: function () { evFilter = t; renderEvents(); }
      }));
    });
    eventsBox.appendChild(chipBar);
    // 固定高度内部滚动:切换筛选时面板尺寸不变,不闪动
    var listBox = h('div', { class: 'ev-list' });
    eventsBox.appendChild(listBox);
    var list = evFilter === '全部' ? lastEvents : lastEvents.filter(function (ev) { return (ev.tag || '') === evFilter; });
    if (!list.length) {
      listBox.appendChild(h('div', { class: 'empty-row', text: lastEvents.length ? '该类别暂无事件' : '近期没有需要关注的事件' }));
      return;
    }
    list.forEach(function (ev) {
      listBox.appendChild(h('div', { class: 'dash-event ' + (ev.type || 'info'), title: ev.text },
        h('span', { class: 'tag', text: ev.tag || '动态' }),
        h('span', { class: 'txt', text: ev.text || '' }),
        h('span', { class: 'dt', text: (ev.date || '').slice(5) })));
    });
  }

  /* ---------- 图表卡片(尺寸可切 + 可拖动排序) ---------- */
  function makeCard(w) {
    var dim = DASH_DIMS[w.dim];
    var kind = w.kind;
    var label = dashCardLabel(w.dim, kind);
    // 下钻配置:valOf 把图表项名转成接口参数值(部门按名称查id,其余原样)
    var drill = null;
    if (dim.drill) {
      var fixed = dim.drill.fixedVal;
      drill = {
        api: dim.drill.api,
        param: dim.drill.param,
        title: label,
        valOf: fixed != null
          ? function () { return fixed; }
          : (dim.drill.param === 'departmentId'
              ? function (name) {
                  if (name === '未分配') { return null; }
                  for (var i = 0; i < Ctx.deptFlat.length; i++) {
                    if (Ctx.deptFlat[i].name === name) { return String(Ctx.deptFlat[i].id); }
                  }
                  return null;
                }
              : function (name) { return name === '未填写' || name === '未分配' ? null : name; })
      };
    }
    var body;
    if (kind === 'columns') {
      body = drawColumns(lastData.entryTrend, dim.unit, drill);
    } else if (kind === 'line') {
      body = drawLine(lastData.entryTrend, dim.unit, drill);
    } else if (kind === 'gauge') {
      body = drawGauge(lastData, drill);
    } else {
      var items = (lastData.groups && lastData.groups[dim.group]) || [];
      if (kind === 'treemap') {
        body = drawTreemap(items, dim.unit, drill);
      } else if (kind === 'rose') {
        body = drawRose(items, drill);
      } else {
        body = kind === 'donut' ? drawDonut(items, drill) : dashBarList(items, dim.unit, '', drill);
      }
    }
    var sizeBtns = h('span', { class: 'size-opts', title: '切换显示尺寸' });
    ['s', 'm', 'l'].forEach(function (sz) {
      sizeBtns.appendChild(h('button', {
        type: 'button', class: 'sz-b' + (w.size === sz ? ' on' : ''), text: ({ s: '小', m: '中', l: '大' })[sz],
        onclick: function (e) {
          e.stopPropagation();
          w.size = sz;
          saveWidgets();
          renderCharts();
        }
      }));
    });
    var card = h('div', { class: 'chart-panel dash-card size-' + (w.size || 'm'), 'data-wid': w.dim + ':' + w.kind },
      h('div', { class: 'dash-card-head' },
        h('h3', { style: 'margin:0', text: label }),
        h('span', { style: 'display:flex;align-items:center;gap:6px' },
          sizeBtns,
          h('button', {
            class: 'dash-card-x', text: '×', title: '移除此图表',
            onclick: function () {
              widgets = widgets.filter(function (x) { return !(x.dim === w.dim && x.kind === w.kind); });
              saveWidgets();
              renderCharts();
            }
          }))),
      body);
    card.draggable = true;
    card.addEventListener('dragstart', function (e) {
      dragEl = card;
      card.classList.add('dragging');
      try { e.dataTransfer.setData('text/plain', ''); e.dataTransfer.effectAllowed = 'move'; } catch (_) { /* IE */ }
    });
    card.addEventListener('dragend', function () {
      card.classList.remove('dragging');
      dragEl = null;
      // 按 DOM 顺序回写 widgets(保持各自的尺寸)
      var order = [];
      Array.prototype.forEach.call(chartsGrid.querySelectorAll('.dash-card'), function (el) {
        var key = el.getAttribute('data-wid');
        for (var i = 0; i < widgets.length; i++) {
          if (widgets[i].dim + ':' + widgets[i].kind === key) { order.push(widgets[i]); break; }
        }
      });
      if (order.length === widgets.length) {
        widgets = order;
        saveWidgets();
      }
      renderCharts();
    });
    card.addEventListener('dragenter', function (e) {
      if (!dragEl || dragEl === card) { return; }
      var r = card.getBoundingClientRect();
      var after = (e.clientX || 0) > r.left + r.width / 2;
      if (after) { chartsGrid.insertBefore(dragEl, card.nextSibling); }
      else { chartsGrid.insertBefore(dragEl, card); }
    });
    return card;
  }

  function renderCharts() {
    chartsGrid.innerHTML = '';
    if (!widgets.length) {
      chartsGrid.appendChild(h('div', { class: 'empty-row', style: 'grid-column:1/-1', text: '还没有图表,点击上方"添加图表"选择要展示的内容' }));
      return;
    }
    widgets.forEach(function (w) { chartsGrid.appendChild(makeCard(w)); });
  }
  chartsGrid.addEventListener('dragover', function (e) { e.preventDefault(); });
  // 点击图表区域(条形/环段/树块/扇叶/柱体/圆点/仪表弧) → 下钻明细
  chartsGrid.addEventListener('click', function (e) {
    var el = e.target.closest ? e.target.closest('[data-api]') : null;
    if (!el) { return; }
    openDrillModal(el.getAttribute('data-title'), el.getAttribute('data-api'),
      el.getAttribute('data-param'), el.getAttribute('data-val'));
  });

  /* ---------- 添加图表:先选指标,再选图表类型 ---------- */
  function openPicker() {
    var wrap = h('div', { style: 'max-height:62vh;overflow:auto' });
    var cats = [];
    Object.keys(DASH_DIMS).forEach(function (k) {
      var c = DASH_DIMS[k].cat;
      if (cats.indexOf(c) < 0) { cats.push(c); }
    });
    function isAdded(dimKey, kind) {
      return widgets.some(function (w) { return w.dim === dimKey && w.kind === kind; });
    }
    cats.forEach(function (cat) {
      var dims = Object.keys(DASH_DIMS).filter(function (k) { return DASH_DIMS[k].cat === cat; });
      var allAdded = dims.every(function (k) {
        return kindsForDim(DASH_DIMS[k]).every(function (kd) { return isAdded(k, kd); });
      });
      if (allAdded) { return; }   // 该模块所有组合都已添加
      wrap.appendChild(h('div', { class: 'dash-pick-cat', text: cat }));
      var grid = h('div', { class: 'dash-picker' });
      dims.forEach(function (k) {
        var dim = DASH_DIMS[k];
        var kinds = kindsForDim(dim);
        var card = h('div', { class: 'dash-pick-btn' },
          h('b', { text: dim.label }),
          h('span', { class: 'kind', text: kinds.length === 1 ? '专用图表' : '选择图表类型:' }));
        var btns = h('div', { class: 'pick-kinds' });
        kinds.forEach(function (kd) {
          var added = isAdded(k, kd);
          btns.appendChild(h('button', {
            type: 'button', class: 'pick-kind-btn' + (added ? ' on' : ''),
            text: (added ? '✓ ' : '＋ ') + DASH_KINDS[kd], title: added ? '已添加' : '以' + DASH_KINDS[kd] + '展示' + dim.label,
            onclick: function () {
              widgets.push({ dim: k, kind: kd, size: 'm' });
              saveWidgets();
              renderCharts();
              m.close();
            }
          }));
        });
        card.appendChild(btns);
        grid.appendChild(card);
      });
      wrap.appendChild(grid);
    });
    var m = openModal({
      title: '添加图表(选指标 → 选类型)',
      body: wrap,
      footer: [h('button', { class: 'btn btn-primary', text: '关闭', onclick: function () { m.close(); } })]
    });
  }

  function build(d, events) {
    root.className = '';
    root.innerHTML = '';
    // 顶部概况卡
    var chips = h('div', { class: 'stats-cards' });
    function card(num, lbl, ic, danger) {
      return h('div', { class: 'stat-card' + (danger && +num > 0 ? ' danger' : '') },
        h('span', { class: 'ic', html: iconSvg(ic) }),
        h('div', {}, h('div', { class: 'num', text: String(num) }), h('div', { class: 'lbl', text: lbl })));
    }
    chips.appendChild(card(d.total || 0, '人员总数', 'users'));
    chips.appendChild(card(d.onDuty || 0, '在职人数', 'check'));
    chips.appendChild(card(d.onLeave || 0, '休假中', 'calendar'));
    chips.appendChild(card(d.deptCount || 0, '部门总数', 'building'));
    chips.appendChild(card(d.vehicleCount || 0, '车辆总数', 'truck'));
    chips.appendChild(card(d.materialLow || 0, '库存预警', 'box', true));
    root.appendChild(chips);

    root.appendChild(h('div', { class: 'dash-main' },
      h('div', { class: 'dash-left' },
        h('div', { class: 'dash-toolbar' },
          h('button', { class: 'btn btn-primary', text: '＋ 添加图表', onclick: openPicker }),
          h('button', {
            class: 'btn btn-ghost', text: '恢复默认',
            onclick: function () {
              widgets = DASH_DEFAULT.map(function (w) { return { dim: w.dim, kind: w.kind, size: w.size }; });
              saveWidgets();
              renderCharts();
            }
          }),
          h('span', { style: 'color:#8a97a8;font-size:12px', text: '卡片可拖动排序,小/中/大可切,布局保存在本机' })),
        chartsGrid),
      h('div', { class: 'dash-right' },
        h('div', { class: 'dash-events' },
          h('div', { class: 'dash-events-head' },
            h('h3', { style: 'margin:0', text: '近期事件' }),
            h('button', {
              class: 'btn-link', text: '刷新',
              onclick: function () {
                eventsBox.innerHTML = '';
                eventsBox.appendChild(h('div', { class: 'loading-inline', text: '加载中…' }));
                GET('/api/dashboard/events').then(function (evs) {
                  lastEvents = evs || [];
                  renderEvents();
                }).catch(function (e) {
                  eventsBox.innerHTML = '';
                  eventsBox.appendChild(h('div', { class: 'empty-row', text: e.message }));
                });
              }
            }),
            ),
          eventsBox))));
    lastEvents = events || [];
    renderEvents();
    renderCharts();
  }

  function render(container) {
    container.appendChild(root);
    Promise.all([GET('/api/stats/dashboard'), GET('/api/dashboard/events')])
      .then(function (res) {
        lastData = res[0] || { groups: {} };
        build(lastData, res[1] || []);
      })
      .catch(function (e) {
        root.className = '';
        root.innerHTML = '';
        root.appendChild(h('div', { class: 'empty-row', text: e.message }));
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

/* ================= 系统设置视图 ================= */

function settingsView() {
  var root = h('div');
  var vals = {
    siteName: SITE.name === '数智军营' ? '' : SITE.name,
    siteTitle: SITE.title === '数智军营人员管理系统' ? '' : SITE.title,
    siteSubtitle: (SITE.subtitle === '人员管理系统' || !SITE.subtitle) ? '' : SITE.subtitle,
    siteLogo: SITE.logo || '', siteFavicon: SITE.favicon || '',
    themeColor: SITE.theme || '#2f6fd6', pageSize: String(SITE.pageSize || 20), backupKeep: '14'
  };

  function section(title) {
    var box = h('div', { class: 'panel', style: 'margin-bottom:16px' });
    box.appendChild(h('div', { style: 'padding:14px 20px;border-bottom:1px solid #edf1f6;font-weight:600;color:#1d2c40', text: title }));
    var body = h('div', { style: 'padding:18px 20px;display:grid;grid-template-columns:1fr 1fr;gap:16px 24px' });
    box.appendChild(body);
    root.appendChild(box);
    return body;
  }
  function field(label, el, hint) {
    return h('div', { class: 'form-item', style: 'margin:0' },
      h('label', { text: label, style: 'display:block;font-size:13px;color:#66788c;margin-bottom:6px' }),
      el,
      hint ? h('div', { style: 'font-size:12px;color:#8a97a8;margin-top:4px', text: hint }) : null);
  }
  function txt(key, ph) {
    var i = h('input', { type: 'text', placeholder: ph || '', style: 'width:100%;height:34px;padding:0 10px;border:1px solid #c5cdd8;border-radius:4px' });
    i.value = vals[key];
    i.addEventListener('input', function () { vals[key] = i.value; });
    return i;
  }

  /* ---- 基础信息 ---- */
  var basic = section('基础信息');
  basic.appendChild(field('网站名称(侧栏主标题)', txt('siteName', '默认:数智军营')));
  basic.appendChild(field('系统标题(浏览器标签页)', txt('siteTitle', '默认:数智军营人员管理系统')));
  basic.appendChild(field('网站副标题(侧栏副标题)', txt('siteSubtitle', '默认:人员管理系统')));
  basic.appendChild(h('div'));

  var logoPrev = h('div', { style: 'display:flex;align-items:center;gap:12px' });
  var logoImg = h('img', { src: vals.siteLogo, style: 'max-height:44px;max-width:150px;display:' + (vals.siteLogo ? 'block' : 'none') + ';border-radius:6px;border:1px solid #edf1f6' });
  var logoFile = h('input', { type: 'file', accept: 'image/*', style: 'display:none' });
  logoFile.addEventListener('change', function () {
    readImage(logoFile.files[0], function (b64) {
      vals.siteLogo = b64;
      logoImg.src = b64;
      logoImg.style.display = 'block';
    });
    logoFile.value = '';
  });
  logoPrev.appendChild(logoImg);
  logoPrev.appendChild(h('button', { class: 'btn btn-ghost btn-sm', text: '上传LOGO', onclick: function () { logoFile.click(); } }));
  logoPrev.appendChild(h('button', { class: 'btn-link danger', text: '清除', onclick: function () {
    vals.siteLogo = '';
    logoImg.style.display = 'none';
  } }));
  logoPrev.appendChild(logoFile);
  basic.appendChild(field('LOGO(侧栏/登录页)', logoPrev, '建议 PNG 透明底,不超过 300KB'));

  var favPrev = h('div', { style: 'display:flex;align-items:center;gap:12px' });
  var favImg = h('img', { src: vals.siteFavicon, style: 'width:32px;height:32px;border-radius:4px;display:' + (vals.siteFavicon ? 'block' : 'none') });
  var favFile = h('input', { type: 'file', accept: 'image/*', style: 'display:none' });
  favFile.addEventListener('change', function () {
    readImage(favFile.files[0], function (b64) {
      vals.siteFavicon = b64;
      favImg.src = b64;
      favImg.style.display = 'block';
    });
    favFile.value = '';
  });
  favPrev.appendChild(favImg);
  favPrev.appendChild(h('button', { class: 'btn btn-ghost btn-sm', text: '上传图标', onclick: function () { favFile.click(); } }));
  favPrev.appendChild(h('button', { class: 'btn-link danger', text: '清除', onclick: function () {
    vals.siteFavicon = '';
    favImg.style.display = 'none';
  } }));
  favPrev.appendChild(favFile);
  basic.appendChild(field('网站图标(favicon)', favPrev, '建议 32x32 或 64x64,浏览器标签页小图标'));

  /* ---- 主题色 ---- */
  var themeSec = section('主题色');
  var presets = ['#2f6fd6', '#4a7c59', '#1f3a5f', '#8a4a2f', '#7048b7', '#b03a3a', '#0d8484', '#5a5f6b'];
  /** 选色即时保存到服务器(刷新/其他人打开都保持) */
  function saveThemeNow(c) {
    PUT('/api/setting/themeColor', c).then(function () {
      SITE.theme = c;
      toast('主题色已应用并保存', 'ok');
    }).catch(function (e) { toast(e.message, 'err'); });
  }
  var swatches = h('div', { style: 'display:flex;gap:10px;flex-wrap:wrap;align-items:center' });
  presets.forEach(function (c) {
    var sw = h('span', { class: 'theme-sw', style: 'width:34px;height:34px;border-radius:8px;background:' + c + ';cursor:pointer;border:2px solid ' + (vals.themeColor === c ? '#1d2c40' : 'transparent'), title: c });
    sw.addEventListener('click', function () {
      vals.themeColor = c;
      applyThemeVars(c);
      Array.prototype.forEach.call(swatches.querySelectorAll('.theme-sw'), function (x) { x.style.borderColor = 'transparent'; });
      sw.style.borderColor = '#1d2c40';
      saveThemeNow(c);
    });
    swatches.appendChild(sw);
  });
  var customColor = h('input', { type: 'color', value: vals.themeColor, style: 'width:44px;height:34px;border:none;background:none;cursor:pointer' });
  var colorSaveTimer = null;
  customColor.addEventListener('input', function () {
    vals.themeColor = customColor.value;
    applyThemeVars(customColor.value);
    Array.prototype.forEach.call(swatches.querySelectorAll('.theme-sw'), function (x) { x.style.borderColor = 'transparent'; });
    clearTimeout(colorSaveTimer);
    colorSaveTimer = setTimeout(function () { saveThemeNow(customColor.value); }, 600);
  });
  swatches.appendChild(h('span', { style: 'color:#8a97a8;font-size:12px', text: '自定义:' }));
  swatches.appendChild(customColor);
  themeSec.appendChild(h('div', { style: 'grid-column:1/-1' }, swatches,
    h('div', { style: 'font-size:12px;color:#8a97a8;margin-top:8px', text: '点击即时预览;保存后对所有人生效,图表主色随主题变化' })));

  /* ---- 显示与维护 ---- */
  var disp = section('显示与维护');
  var pageSizeSel = h('select', { style: 'width:100%;height:34px;padding:0 8px;border:1px solid #c5cdd8;border-radius:4px' });
  ['10', '20', '50', '100'].forEach(function (n) {
    pageSizeSel.appendChild(h('option', { value: n, text: n + ' 条/页' }));
  });
  pageSizeSel.value = vals.pageSize;
  pageSizeSel.addEventListener('change', function () { vals.pageSize = pageSizeSel.value; });
  disp.appendChild(field('列表每页条数', pageSizeSel, '保存后刷新页面生效'));
  var keepInput = h('input', { type: 'number', min: '7', max: '60', style: 'width:100%;height:34px;padding:0 10px;border:1px solid #c5cdd8;border-radius:4px' });
  keepInput.value = vals.backupKeep;
  keepInput.addEventListener('input', function () { vals.backupKeep = keepInput.value; });
  disp.appendChild(field('自动备份保留份数', keepInput, '每天 03:00 自动备份,超出自动轮转删除(7~60)'));
  GET('/api/setting/backupKeep').then(function (v) {
    if (v) { keepInput.value = v; vals.backupKeep = v; }
  }).catch(function () { });

  /* ---- 操作条 ---- */
  var bar = h('div', { class: 'panel', style: 'padding:14px 20px;display:flex;gap:12px;align-items:center;flex-wrap:wrap' });
  bar.appendChild(h('button', {
    class: 'btn btn-primary', text: '保存全部设置',
    onclick: function () {
      var keys = ['siteName', 'siteTitle', 'siteSubtitle', 'siteLogo', 'siteFavicon', 'themeColor', 'pageSize', 'backupKeep'];
      var chain = Promise.resolve();
      keys.forEach(function (k) {
        chain = chain.then(function () { return PUT('/api/setting/' + k, vals[k] || ''); });
      });
      chain.then(function () {
        SITE.name = vals.siteName || '数智军营';
        SITE.title = vals.siteTitle || '数智军营人员管理系统';
        SITE.subtitle = vals.siteSubtitle || '人员管理系统';
        SITE.logo = vals.siteLogo;
        SITE.favicon = vals.siteFavicon;
        SITE.theme = vals.themeColor;
        SITE.pageSize = parseInt(vals.pageSize, 10) || 20;
        applyBranding();
        var t1 = document.querySelector('.sidebar-logo .t1');
        var t2 = document.querySelector('.sidebar-logo .t2');
        if (t1) { t1.textContent = SITE.name; }
        if (t2) { t2.textContent = SITE.subtitle; }
        var hasImg = !!document.querySelector('.sidebar-logo img.logo-img');
        if ((SITE.logo && !hasImg) || (!SITE.logo && hasImg)) { location.reload(); return; }
        if (SITE.logo) { document.querySelector('.sidebar-logo img.logo-img').src = SITE.logo; }
        var star = document.querySelector('.sidebar-logo .star');
        if (SITE.logo && star) { location.reload(); }
        toast('设置已保存');
      }).catch(function (e) { toast(e.message, 'err'); });
    }
  }));
  bar.appendChild(h('button', {
    class: 'btn btn-ghost', text: '恢复默认外观',
    onclick: function () {
      confirmBox('确定恢复默认外观?标题/LOGO/图标/主题色将清空为默认值。', true).then(function (yes) {
        if (!yes) { return; }
        var keys = ['siteName', 'siteTitle', 'siteSubtitle', 'siteLogo', 'siteFavicon', 'themeColor'];
        var chain = Promise.resolve();
        keys.forEach(function (k) { chain = chain.then(function () { return PUT('/api/setting/' + k, ''); }); });
        chain.then(function () { location.reload(); });
      });
    }
  }));
  bar.appendChild(h('span', { style: 'color:#8a97a8;font-size:12px', text: '设置保存在服务器,对所有使用本系统的人生效' }));
  root.appendChild(bar);

  function render(container) {
    container.appendChild(root);
  }
  return { render: render };
}

/** 图片文件 → base64(限 300KB) */
function readImage(file, cb) {
  if (!file) { return; }
  if (file.size > 300 * 1024) {
    toast('图片过大(超过300KB),请压缩后再上传', 'err');
    return;
  }
  var fr = new FileReader();
  fr.onload = function () { cb(fr.result); };
  fr.readAsDataURL(file);
}



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

        /* ---- 数据上报(下级→上级) ---- */
        root.appendChild(h('div', { class: 'panel', style: 'margin-bottom:16px' },
          h('div', { style: 'padding:14px 20px;border-bottom:1px solid #edf1f6;font-weight:600;color:#1d2c40', text: '数据上报(下级单位 ↔ 上级单位)' }),
          h('div', { style: 'padding:16px 20px;display:flex;gap:14px;flex-wrap:wrap;align-items:center' },
            h('button', {
              class: 'btn btn-primary', text: '⬆ 导出上报数据包',
              title: '选择模块导出上报包,发送给上级单位',
              onclick: openReportExport
            }),
            h('button', {
              class: 'btn btn-ghost', text: '⬇ 接收下级数据包',
              title: '导入下级单位的上报包:自动检索/新建对应部门,按身份证合并人员',
              onclick: openReportReceive
            }),
            h('span', { style: 'color:#8a97a8;font-size:12px', text: '下级单位把"系统设置→网站名称"设为本单位名(如:XX班);上级接收时按该名称匹配/新建部门' }))));

        var reportFile = null;
        function openReportReceive() {
          var input = h('input', { type: 'file', accept: '.json', style: 'display:none' });
          input.addEventListener('change', function () {
            var f = input.files[0];
            input.value = '';
            if (!f) { return; }
            reportCheck(f);
          });
          document.body.appendChild(input);
          input.click();
          setTimeout(function () { input.remove(); }, 60000);
        }

        function openReportExport() {
          var mods = { personnel: true, equipment: true, material: true, vehicle: true, leave: true, fitness: true };
          var deptSel = {};
          var allDept = true;
          var modBtns = {};
          var deptBtns = {};

          // ① 单位选择(树形分组:大单位一个框,子单位缩进在框内)
          var kidsOf = {};
          (Ctx.deptFlat || []).forEach(function (x) {
            (kidsOf[x.parentId] = kidsOf[x.parentId] || []).push(x.id);
          });
          function descendants(id) {
            var out = [id];
            (kidsOf[id] || []).forEach(function (k) {
              descendants(k).forEach(function (kk) { out.push(kk); });
            });
            return out;
          }
          var deptBox = h('div', { style: 'max-height:260px;overflow:auto;padding:1px' });
          function makeDeptBtn(d, childCount) {
            var b = h('button', {
              type: 'button', class: 'pick-kind-btn',
              style: (childCount ? 'font-weight:600;padding:0 14px;height:30px;' : 'font-size:12px;padding:0 10px;height:26px;'),
              text: d.name + (childCount ? '(' + childCount + '个下级)' : '')
            });
            b.title = childCount ? '点击选择「' + d.name + '」及其全部下级' : '';
            b.addEventListener('click', function () {
              if (allDept) {
                deptSel = {};
                allDept = false;
                descendants(d.id).forEach(function (k) { deptSel[k] = true; });
              } else if (deptSel[d.id]) {
                descendants(d.id).forEach(function (k) { delete deptSel[k]; });
              } else {
                descendants(d.id).forEach(function (k) { deptSel[k] = true; });
              }
              paint();
            });
            b.setAttribute('data-big', childCount ? '1' : '0');
            deptBtns[d.id] = b;
            return b;
          }
          (function buildDeptTree(nodes, container, depth) {
            (nodes || []).forEach(function (d) {
              var kids = d.children || [];
              if (kids.length) {
                var g = h('div', { class: depth === 0 ? 'dept-group' : 'dept-group nested' },
                  makeDeptBtn(d, kids.length));
                var kidBox = h('div', { class: 'dept-children' });
                buildDeptTree(kids, kidBox, depth + 1);
                g.appendChild(kidBox);
                container.appendChild(g);
              } else {
                container.appendChild(makeDeptBtn(d, 0));
              }
            });
          })(Ctx.deptTree, deptBox, 0);
          if (!(Ctx.deptFlat || []).length) {
            deptBox.appendChild(h('span', { style: 'color:#8a97a8;font-size:12px', text: '(暂无部门)' }));
          }
          var countLbl = h('span', { style: 'font-size:12px;color:var(--pri-dark,#16437e);font-weight:600' });

          // ② 数据模块
          var modBox = h('div', { style: 'display:flex;gap:8px;flex-wrap:wrap' });
          [['personnel', '人员管理'], ['equipment', '装备管理'], ['material', '物资管理'], ['vehicle', '车辆管理'], ['leave', '休假管理'], ['fitness', '体能考核']].forEach(function (m) {
            var b = h('button', { type: 'button', class: 'pick-kind-btn', text: m[1] });
            b.addEventListener('click', function () {
              mods[m[0]] = !mods[m[0]];
              paint();
            });
            modBtns[m[0]] = b;
            modBox.appendChild(b);
          });

          function paint() {
            Object.keys(modBtns).forEach(function (k) {
              modBtns[k].className = 'pick-kind-btn' + (mods[k] ? ' mod-on' : '');
              modBtns[k].style.cssText = '';
            });
            Object.keys(deptBtns).forEach(function (id) {
              var on = allDept || deptSel[id];
              var b = deptBtns[id];
              b.className = 'pick-kind-btn' + (on ? ' mod-on' : '');
              var big = b.getAttribute('data-big') === '1';
              b.style.cssText = big ? 'font-weight:600;padding:0 14px;height:30px;' : 'font-size:12px;padding:0 10px;height:26px;';
            });
            countLbl.textContent = allDept ? '已选:全部单位' : ('已选:' + Object.keys(deptSel).length + ' 个单位');
          }

          var body = h('div', {},
            h('div', { style: 'font-weight:600;margin-bottom:8px;display:flex;align-items:center;gap:10px' },
              h('span', { text: '① 选择单位' }), countLbl),
            deptBox,
            h('div', { style: 'font-weight:600;margin:14px 0 8px;display:flex;align-items:center;gap:10px' },
              h('span', { text: '② 数据模块' }),
              h('span', { style: 'flex:1' }),
              h('button', { type: 'button', class: 'btn btn-ghost btn-sm', text: '全选', onclick: function () {
                Object.keys(mods).forEach(function (k) { mods[k] = true; });
                paint();
              } }),
              h('button', { type: 'button', class: 'btn btn-ghost btn-sm', text: '全不选', onclick: function () {
                Object.keys(mods).forEach(function (k) { mods[k] = false; });
                paint();
              } })),
            modBox,
            h('div', { style: 'color:#8a97a8;font-size:12px;margin-top:10px;line-height:1.7',
              text: '人员/休假/体能按所选单位导出;装备/物资/车辆为全单位台账。' }));
          paint();

          var m = openModal({
            title: '导出上报数据包', body: body,
            footer: [
              h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m.close(); } }),
              h('button', {
                class: 'btn btn-primary', text: '导出',
                onclick: function () {
                  var sel = [];
                  ['personnel', 'equipment', 'material', 'vehicle', 'leave', 'fitness'].forEach(function (k) { if (mods[k]) { sel.push(k); } });
                  if (!sel.length) { toast('至少选择一个数据模块', 'err'); return; }
                  var needPeople = sel.indexOf('personnel') >= 0 || sel.indexOf('leave') >= 0 || sel.indexOf('fitness') >= 0;
                  var deptIds = Object.keys(deptSel);
                  if (needPeople && !allDept && !deptIds.length) { toast('请至少选择一个单位', 'err'); return; }
                  var url = '/api/report/export?scope=' + sel.join(',');
                  if (needPeople && !allDept) { url += '&depts=' + deptIds.join(','); }
                  m.close();
                  var pm = showProgressModal('导出上报包');
                  pm.setStatus('正在打包…');
                  apiBlobWithProgress(url, function (pct) { pm.setProgress(pct); }).then(function (r) {
                    pm.setProgress(100);
                    pm.setStatus('下载完成');
                    downloadBlob(r.blob, r.filename);
                    setTimeout(function () { pm.close(); }, 500);
                  }).catch(function (e) { pm.close(); toast(e.message, 'err'); });
                }
              })
            ]
          });
        }

        function reportCheck(f) {
          var pm = showProgressModal('解析上报包');
          pm.setStatus('正在解析 ' + f.name + '…');
          var fd = new FormData();
          fd.append('file', f);
          postFormWithProgress('/api/report/check', fd, function () { }).then(function (d) {
            pm.close();
            reportFile = f;
            openReportConfirm(d, f);
          }).catch(function (e) { pm.close(); toast(e.message, 'err'); });
        }

        function openReportConfirm(d, f) {
          var body = h('div');
          var matched = d.matchedDepts || [];
          var useDeptId = matched.length ? String(matched[0].id) : '';
          var createNew = !matched.length;

          body.appendChild(h('div', { style: 'background:#eef4ff;border-radius:8px;padding:12px 16px;margin-bottom:14px;line-height:1.9;font-size:13.5px' },
            h('div', { html: '上报单位:<b>' + esc(d.unit) + '</b>(' + esc(d.exportedAt || '') + ')' }),
            h('div', { html: '人员 <b>' + d.personnelCount + '</b> 人(新 ' + d.newCount + ' / 本单位已存在 ' + d.existCount + '),子表 ' + d.subCount + ' 条,休假 ' + d.leaveCount + ' 条,体能 ' + d.fitnessCount + ' 条' }),
            h('div', { html: '装备 ' + d.equipmentCount + ' 项,物资 ' + d.materialCount + ' 项,车辆 ' + d.vehicleCount + ' 辆' })));

          var deptArea = h('div');
          function renderDeptChoice() {
            deptArea.innerHTML = '';
            if (matched.length) {
              deptArea.appendChild(h('div', { style: 'font-weight:600;margin-bottom:8px', text: '已检索到同名单位,数据并入:' }));
              matched.forEach(function (m) {
                var id = String(m.id);
                deptArea.appendChild(h('label', { style: 'display:flex;align-items:center;gap:8px;margin-bottom:6px;cursor:pointer' },
                  (function () {
                    var rb = h('input', { type: 'radio', name: 'rpt-dept' });
                    rb.checked = useDeptId === id;
                    rb.addEventListener('change', function () { useDeptId = id; createNew = false; });
                    return rb;
                  })(),
                  h('span', { text: m.name + '(ID ' + m.id + ')' })));
              });
              deptArea.appendChild(h('label', { style: 'display:flex;align-items:center;gap:8px;margin-top:8px;cursor:pointer' },
                (function () {
                  var rb = h('input', { type: 'radio', name: 'rpt-dept' });
                  rb.checked = createNew;
                  rb.addEventListener('change', function () { createNew = true; useDeptId = ''; renderDeptChoice(); });
                  return rb;
                })(),
                h('span', { text: '都不对,新建部门「' + d.unit + '」' })));
              if (createNew) { deptArea.appendChild(parentPicker()); }
            } else {
              deptArea.appendChild(h('div', { style: 'font-weight:600;margin-bottom:8px', html: '本单位没有名为「<b>' + esc(d.unit) + '</b>」的部门,将<b>新建</b>该部门:' }));
              deptArea.appendChild(parentPicker());
            }
          }
          var parentSel;
          function parentPicker() {
            var wrap = h('div', { style: 'margin:8px 0 0 26px' });
            parentSel = h('select', { style: 'height:32px;border:1px solid #c5cdd8;border-radius:4px;padding:0 8px;max-width:260px' });
            (Ctx.deptFlat || []).forEach(function (x) {
              var o = h('option', { value: String(x.id), text: new Array(x.depth + 1).join('　') + x.name });
              parentSel.appendChild(o);
            });
            var top = h('option', { value: '0', text: '(作为顶级部门)' });
            parentSel.insertBefore(top, parentSel.firstChild);
            parentSel.value = '0';
            wrap.appendChild(h('span', { style: 'font-size:13px;color:#66788c;margin-right:6px', text: '上级:' }));
            wrap.appendChild(parentSel);
            return wrap;
          }
          renderDeptChoice();

          body.appendChild(deptArea);
          body.appendChild(h('div', { style: 'color:#8a97a8;font-size:12px;margin-top:12px;line-height:1.8',
            text: '合并规则:人员按身份证去重(新人入所选部门,老人仅更新不改单位);子表以下级为准替换;休假/体能跳过重复;装备按编号、物资按名称、车辆按车牌去重(有则更新,无则新增)。' }));

          var m = openModal({
            title: '接收下级数据 - ' + d.unit, body: body, wide: true,
            footer: [
              h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m.close(); } }),
              h('button', {
                class: 'btn btn-primary', text: '确认导入',
                onclick: function () {
                  var fd = new FormData();
                  fd.append('file', f);
                  if (createNew) {
                    fd.append('createNew', 'true');
                    fd.append('parentId', parentSel ? parentSel.value : '0');
                  } else {
                    if (!useDeptId) { toast('请选择接收部门', 'err'); return; }
                    fd.append('deptId', useDeptId);
                  }
                  var pid = 'imp' + Date.now() + Math.floor(Math.random() * 1000);
                  fd.append('progressId', pid);
                  var pm = showProgressModal('导入上报包');
                  pm.setStatus('上传中…');
                  // 轮询服务端真实进度(阶段+明细+百分比)
                  var poll = setInterval(function () {
                    GET('/api/report/progress?id=' + pid).then(function (pg) {
                      if (pg && pg.pct !== undefined) {
                        pm.setProgress(Math.max(2, Math.round(pg.pct)));
                        pm.setStatus((pg.stage || '处理中') + (pg.detail ? ' · ' + pg.detail : ''));
                      }
                    }).catch(function () { });
                  }, 400);
                  postFormWithProgress('/api/report/import', fd, function (pct) {
                    pm.setProgress(Math.max(1, Math.round(pct * 8)));
                    pm.setStatus('上传中… ' + Math.round(pct) + '%');
                  }).then(function (r) {
                    clearInterval(poll);
                    pm.setProgress(100);
                    pm.setStatus('导入完成');
                    setTimeout(function () { pm.close(); }, 300);
                    m.close();
                    openReportResult(r);
                    refreshDepts();
                  }).catch(function (e) {
                    clearInterval(poll);
                    pm.close();
                    toast(e.message, 'err');
                  });
                }
              })
            ]
          });
        }

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
  var rememberCb = h('input', { type: 'checkbox', id: 'remember-me' });
  rememberCb.checked = true;
  var btn = h('button', { class: 'btn btn-primary login-btn', text: '登  录' });

  function submit() {
    var u = userInput.value.trim();
    var p = pwInput.value;
    if (!u || !p) { errBox.textContent = '请输入账号和密码'; return; }
    btn.disabled = true; btn.textContent = '登录中…';
    errBox.textContent = '';
    api('POST', '/api/auth/login', { username: u, password: p }).then(function (data) {
      saveSession(data.token, { username: data.username, displayName: data.displayName }, rememberCb.checked);
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
      SITE.logo
        ? h('img', { class: 'login-logo-img', src: SITE.logo, alt: 'LOGO' })
        : h('div', { class: 'login-emblem', text: '★' }),
      h('div', { class: 'login-title', text: SITE.title || '数智军营人员管理系统' }),
      SITE.subtitle && SITE.subtitle !== '人员管理系统' ? h('div', { class: 'login-subtitle', text: SITE.subtitle }) : null,
      h('form', {
        class: 'login-form', autocomplete: 'on',
        onsubmit: function (e) { e.preventDefault(); submit(); }
      },
        h('div', { class: 'field' }, h('label', { text: '账号' }), userInput),
        h('div', { class: 'field' }, h('label', { text: '密码' }), pwInput),
        h('label', { class: 'login-remember', for: 'remember-me' },
          rememberCb,
          h('span', { text: '保持登录(记住我)' }),
          h('span', { class: 'hint', text: '不勾选则关闭浏览器后需重新登录' })),
        errBox,
        btn),
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
  var currentView = 'stats';

  function navigate(view) {
    currentView = view;
    refreshDicts(); // 字典可能在别处被修改,切页时刷新缓存保证筛选/表单下拉最新
    // 记住当前页面:URL 锚点(刷新/分享/前进后退)+ localStorage 兜底
    if (('#' + view) !== location.hash) {
      try { history.replaceState(null, '', '#' + view); } catch (e) { location.hash = view; }
    }
    localStorage.setItem('lastView', view);
    Object.keys(menuItems).forEach(function (k) { menuItems[k].classList.remove('active'); });
    if (menuItems[view]) menuItems[view].classList.add('active');

    // 切换页面时清理筛选按钮和滚动按钮(非 CRUD 页面不显示)
    $all('.filter-tab-btn', document).forEach(function (b) { b.remove(); });
    $all('.scroll-btns', document).forEach(function (b) { b.remove(); });

    content.innerHTML = '';
    var comp;
    if (view === 'department') { titleEl.textContent = '部门管理'; comp = departmentView(); }
    else if (view === 'dict') { titleEl.textContent = '数据字典'; comp = dictView(); }
    else if (view === 'settings') { titleEl.textContent = '系统设置'; comp = settingsView(); }
    else if (view === 'stats') { titleEl.textContent = '仪表盘'; comp = dashboardView(); }
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
  addMenu('stats', 'chart', '仪表盘');   // 仪表盘置顶
  MODULES.forEach(function (m) { addMenu(m.key, m.icon, m.title); });
  addMenu('department', 'building', '部门管理');
  addMenu('dict', 'book', '数据字典');
  addMenu('settings', 'shield', '系统设置');
  addMenu('backup', 'database', '备份恢复');
  addMenu('log', 'fileText', '操作日志');

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
  var manualExpand = false;   // 窄窗口下用户手动展开后,自动收起让位
  var toggleBtn = h('div', {
    class: 'menu-item toggle', 'data-tip': '展开菜单',
    onclick: function () {
      if (!sideEl.classList.contains('collapsed') === false) { /* noop */ }
      setCollapsed(!sideEl.classList.contains('collapsed'));
      if (!sideEl.classList.contains('collapsed')) { manualExpand = true; }
    }
  }, toggleIcon, toggleTxt);

  var sideEl = h('div', { class: 'sidebar' },
    h('div', { class: 'sidebar-logo' },
      SITE.logo
        ? h('img', { class: 'logo-img', src: SITE.logo, alt: 'LOGO' })
        : h('span', { class: 'star', text: '★' }),
      h('div', {},
        h('div', { class: 't1', text: SITE.name || '数智军营' }),
        h('div', { class: 't2', text: SITE.subtitle || '人员管理系统' }))),
    menu,
    h('div', { class: 'sidebar-toggle' }, toggleBtn));

  var mainEl = h('div', { class: 'main' }, topbar, content);
  var SIDEBAR_W = 208, SIDEBAR_MINI = 64;

  // 窗口太窄自动收起菜单(与手动收起同一动画),变宽自动展开;900/960滞回避免临界抖动
  window.addEventListener('resize', function () {
    var w = window.innerWidth;
    var collapsed = sideEl.classList.contains('collapsed');
    if (w < 900 && !collapsed && !manualExpand) { setCollapsed(true); }
    else if (w >= 960) {
      if (collapsed) { setCollapsed(false); }
      manualExpand = false;   // 回到宽屏,恢复自动接管
    }
  });

  function setCollapsed(collapsed) {
    var was = sideEl.classList.contains('collapsed');
    sideEl.classList.toggle('collapsed', collapsed);
    document.body.classList.toggle('side-mini', collapsed);
    localStorage.setItem('sidebarCollapsed', collapsed ? '1' : '0');
    toggleIcon.innerHTML = iconSvg(collapsed ? 'chevronR' : 'chevronL');
    toggleTxt.textContent = collapsed ? '展开菜单' : '收起菜单';
    toggleBtn.setAttribute('data-tip', collapsed ? '展开菜单' : '收起菜单');
    // 侧边栏由 CSS transform 过渡平移;主区 margin 瞬切 + FLIP transform 滑动,两者同步
    if (was !== collapsed) {
      slideFrom(mainEl, collapsed ? (SIDEBAR_W - SIDEBAR_MINI) : -(SIDEBAR_W - SIDEBAR_MINI));
    }
  }
  setCollapsed(localStorage.getItem('sidebarCollapsed') === '1');

  app.appendChild(h('div', { class: 'layout' }, sideEl, mainEl));

  // 浏览器前进/后退键切换模块
  window.addEventListener('hashchange', function () {
    var v = (location.hash || '').replace('#', '');
    if (menuItems[v] && v !== currentView) navigate(v);
  });

  // 恢复上次所在页面:优先 URL 锚点,其次 localStorage
  var initialView = (location.hash || '').replace('#', '');
  var validViews = Object.keys(menuItems);
  if (validViews.indexOf(initialView) < 0) {
    initialView = localStorage.getItem('lastView') || 'stats';
  }
  if (validViews.indexOf(initialView) < 0) {
    initialView = 'personnel';
  }
  navigate(initialView);
}

/* ================= 启动 ================= */

(function boot() {
  // 站点外观(标题/LOGO/主题色)免登录可用,登录页也要显示
  var siteReady = loadSiteSettings();
  if (!TOKEN) { siteReady.then(function () { renderLogin(); }); return; }
  Promise.all([siteReady]).then(function () {
  refreshAll().then(function () {
    if (TOKEN) {
      renderShell();             // 缓存刷新期间令牌失效则不进主界面(已在登录页)
    }
  }).catch(function (e) {
    if (!TOKEN) return;          // 401 已跳转登录
    toast(e.message, 'err');
    renderShell();
  });
  });
})();
