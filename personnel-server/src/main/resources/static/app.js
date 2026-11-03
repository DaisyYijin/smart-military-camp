/* 数智军营人员管理系统 - 网页端(零依赖原生 JS,离线可用) */
'use strict';

/* ================= 动效令牌 =================
   与 app.css 的 --dur- 与 --ease- 系列同源(此处为毫秒,css 为秒),改动须两边同步。
   减动效:系统开启「减少动画」时 JS 侧动效直接终态(css 侧由文件末尾全局块兜底)。 */
var MOTION = {
  glide: 'cubic-bezier(.4,0,.2,1)',  /* 位移滑行 = --ease-glide */
  panel: 300,                        /* 面板滑行 ms = --dur-panel */
  fade: 300,                         /* 淡出 ms = --dur-fade */
  toastStay: 2400,                   /* toast 停留 ms(淡出开始) */
  toastGone: 2800                    /* toast 移除 ms */
};
function prefersReducedMotion() {
  // 自包含判定(便于按名提取做单测);无 matchMedia 的环境一律视为不减动效
  return Boolean(typeof window !== 'undefined' && window.matchMedia
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
}

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
        else if (k === 'draggable') el.draggable = !!v;
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
  setTimeout(function () { t.style.opacity = '0'; t.style.transition = 'opacity ' + MOTION.fade + 'ms'; }, MOTION.toastStay);
  setTimeout(function () { t.remove(); }, MOTION.toastGone);
}

/**
 * 字段校验气泡:在字段上方浮出红色提示(fixed 定位,不受弹窗滚动裁剪),
 * 重新输入或 4 秒后自动消失。配套 hideFieldBubble 清除。
 */
function hideFieldBubble(el) {
  if (el && el._fieldBubble) {
    if (el._fieldBubble.parentNode) { el._fieldBubble.parentNode.removeChild(el._fieldBubble); }
    el._fieldBubble = null;
  }
}

function showFieldBubble(el, text) {
  if (!el || !text) { return; }
  hideFieldBubble(el);
  var b = h('div', { class: 'field-bubble', text: text });
  document.body.appendChild(b);
  var r = el.getBoundingClientRect();
  b.style.left = Math.max(8, Math.min(r.left, window.innerWidth - b.offsetWidth - 8)) + 'px';
  var top = r.top - b.offsetHeight - 8;
  b.style.top = (top < 6 ? r.bottom + 6 : top) + 'px';
  b.classList.add(top < 6 ? 'below' : 'above');
  el._fieldBubble = b;
  function clear() {
    hideFieldBubble(el);
    el.removeEventListener('input', clear);
    el.removeEventListener('change', clear);
  }
  el.addEventListener('input', clear);
  el.addEventListener('change', clear);
  setTimeout(clear, 4000);
}

function openModal(opt) {
  var previousFocus = document.activeElement;
  var closed = false;
  var mask = h('div', { class: 'mask' });
  var closeBtn = h('button', { type: 'button', class: 'dialog-close', 'aria-label': '关闭对话框', text: '×', onclick: function () { close(); } });
  var titleId = 'dialog-title-' + (openModal.sequence = (openModal.sequence || 0) + 1);
  var head = h('div', { class: 'dialog-head' }, h('div', { id: titleId, class: 'ttl', text: opt.title || '' }), closeBtn);
  var body = h('div', { class: 'dialog-body' });
  if (opt.body) body.appendChild(opt.body);
  var dlg = h('div', { class: 'dialog' + (opt.wide ? ' wide' : '') + (opt.xwide ? ' xwide' : '') + (opt.xxwide ? ' xxwide' : '') }, head, body);
  if (opt.footer) dlg.appendChild(h('div', { class: 'dialog-foot' }, opt.footer));
  dlg.setAttribute('role', 'dialog');
  dlg.setAttribute('aria-modal', 'true');
  dlg.setAttribute('aria-labelledby', titleId);
  dlg.setAttribute('tabindex', '-1');
  mask.appendChild(dlg);
  document.querySelectorAll('.scroll-btns').forEach(function (b) { b.style.display = 'none'; });
  mask.addEventListener('mousedown', function (e) { if (e.target === mask) close(); });
  mask.addEventListener('keydown', function (e) {
    if ($all('.mask').slice(-1)[0] !== mask) return;
    if (e.key === 'Escape' || e.keyCode === 27) { e.preventDefault(); e.stopPropagation(); close(); }
    if (e.key !== 'Tab' && e.keyCode !== 9) return;
    var focusable = $all('button, input, select, textarea, a[href], [tabindex]', dlg).filter(function (el) {
      return !el.disabled && el.type !== 'hidden' && el.getAttribute('tabindex') !== '-1' && el.getClientRects().length > 0;
    });
    var first = focusable[0], last = focusable[focusable.length - 1];
    if (!first) { e.preventDefault(); dlg.focus(); }
    else if (e.shiftKey && (document.activeElement === first || document.activeElement === dlg)) { e.preventDefault(); last.focus(); }
    else if (!e.shiftKey && (document.activeElement === last || document.activeElement === dlg)) { e.preventDefault(); first.focus(); }
  });
  document.body.appendChild(mask);
  dlg.focus();
  function close(force) {
    if (closed) return;
    if (force !== true && opt.beforeClose && opt.beforeClose() === false) return;
    closed = true;
    mask.remove();
    if (!$('.mask')) document.querySelectorAll('.scroll-btns').forEach(function (b) { b.style.display = ''; });
    if (previousFocus && document.body.contains(previousFocus) && previousFocus.focus) previousFocus.focus();
    if (opt.onClose) opt.onClose();
  }
  return { close: close, body: body, dialog: dlg };
}

/** 彩蛋:2.2秒内连点元素5次触发回调(绑定在侧栏品牌区,不影响任何业务;独立测试环境无定时器时降级为不自动重置) */
function attachEasterEgg(el, fire) {
  var scheduleReset = typeof setTimeout === 'function' ? setTimeout : null;
  var cancelReset = typeof clearTimeout === 'function' ? clearTimeout : null;
  var hits = 0, resetTimer = null;
  el.style.cursor = 'pointer';
  el.addEventListener('click', function () {
    hits++;
    if (resetTimer && cancelReset) { cancelReset(resetTimer); }
    resetTimer = scheduleReset ? scheduleReset(function () { hits = 0; resetTimer = null; }, 2200) : null;
    if (hits >= 5) {
      hits = 0;
      if (resetTimer && cancelReset) { cancelReset(resetTimer); resetTimer = null; }
      fire();
    }
  });
}

/** 彩蛋弹窗:严密防范网络泄密"十二条禁令"警示教育(连点品牌区5次触发) */
var EGG_RULES = [
  '严禁涉密计算机联接国际互联网；',
  '严禁涉密计算机安装、使用无线上网卡；',
  '严禁涉密计算机开通红外、蓝牙等无线联接、传递功能；',
  '严禁将使用无线上网卡的私人计算机和具有无线联网功能的可穿戴设备带入涉密场所；',
  '严禁在联接国际互联网的计算机上使用涉密或者曾经涉密的移动存储载体；',
  '严禁在联接国际互联网的计算机上存储、处理或者传递涉密信息；',
  '严禁在联接国际互联网的计算机上存储显示军人身份的信息；',
  '严禁在国际互联网上谈论、发布、传播、泄露涉密和敏感信息；',
  '严禁违反规定在国际互联网上开设网站、网页以及博客、论坛、微博、微信公众号、贴吧、QQ群、头条号、网络直播、知乎、分答等新媒体账号；',
  '严禁以部队番号、代号建立QQ、微信、微博等网络社群；',
  '严禁计算机在军队内部网络和国际互联网之间交叉联接；',
  '严禁存储载体在涉密计算机和联接国际互联网的计算机之间交叉使用。'
];

function openEasterEgg() {
  var now = new Date();
  var pad = function (n) { return n < 10 ? '0' + n : '' + n; };
  var dateStr = now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate());
  var user = (typeof USER !== 'undefined' && USER && USER.username) ? USER.username : '—';
  var list = h('ol', { class: 'egg-rules' });
  for (var i = 0; i < EGG_RULES.length; i++) {
    list.appendChild(h('li', { text: EGG_RULES[i] }));
  }
  var body = h('div', { class: 'egg' },
    h('div', { class: 'egg-star', 'aria-hidden': 'true', text: '★' }),
    h('div', { class: 'egg-title', text: '严密防范网络泄密「十二条禁令」' }),
    h('div', { class: 'egg-text', text: '严守纪律 · 保守秘密，人人有责、时时牢记。' }),
    list,
    h('div', { class: 'egg-info' }, h('div', { text: '当前账号：' + user + ' · ' + dateStr })),
    h('div', { class: 'egg-hint', text: '隐藏警示卡，仅此一枚，请牢记于心。' }));
  var m = openModal({
    title: '★ 严密防范网络泄密十二条禁令',
    body: body,
    footer: [h('button', {
      class: 'btn btn-primary', text: '牢记',
      onclick: function () { if (m) { m.close(true); } }
    })]
  });
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

/** 各模块 Excel 导入的匹配规则及日期列提示：不含日历日期的模板不显示日期说明。 */
function importRuleText(key) {
  if (key === 'equipment-large' || key === 'equipment-small') key = 'equipment';
  var rules = {
    personnel: '人员导入按「身份证号」匹配：身份证号已存在的人员会被本次数据更新，新的身份证号新增；任职经历、家庭成员等子表随人员归档，不会重复插入。请重新下载新模板并补齐必填「军人证件号」列（独立文本，不用身份证号代填）；历史空值原样保留，编辑时须补齐，旧备份仍可恢复。',
    'equipment-gun': '枪支导入按「装备编号」匹配：装备编号已存在的枪支会被本次数据更新，新的装备号新增。',
    equipment: '装备导入按「识别码」匹配（类别为枪支的按「装备编号」）：识别码已存在的装备会被本次数据更新，新的识别码新增。',
    vehicle: '车辆导入按「车架号」匹配：车架号已存在的车辆会被本次数据更新，新的车架号新增。',
    phone: '手机设备导入按「人员身份证号」匹配：该人员已有设备记录时会被本次数据更新，否则新增。',
    fitness: '体能考核导入模板为宽行格式：一人一行，各考核项目为列，单元格填写该项目成绩，空白表示未参加该项目；旧的一项一行格式仍可导入。同一人员同一天同一项目重复导入时按覆盖更新处理原成绩（匹配：人员+项目+考核日期）。',
    military: '军事考核为增量导入：每个有效行都新增一条成绩，不匹配旧记录、不覆盖已有成绩。',
    leave: '休假登记为增量导入：每个有效行都新增一条登记，不匹配旧记录、不覆盖已有登记。',
    'leave-plan': '休假计划为增量导入：每个有效行都新增一条计划，不匹配旧记录、不覆盖已有计划。',
    material: '物资为增量导入：每个有效行都新增一条记录，不匹配旧记录、不修改已有库存数量。'
  };
  var text = rules[key] || '请先下载模板，按模板列头填写后导入。';
  if (['personnel', 'equipment-gun', 'equipment', 'vehicle', 'phone', 'fitness', 'military', 'leave', 'leave-plan'].indexOf(key) >= 0) {
    text += '\n日期列支持20160901、2016-09-01、2016.09.01、2016/9/1、2016年9月1日及Excel日期单元格，统一识别真实日期，非法日期会报错。';
  }
  return text;
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
  flag: '<path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/><line x1="4" y1="22" x2="4" y2="15"/>',
  chevronL: '<polyline points="15 18 9 12 15 6"/>',
  chevronR: '<polyline points="9 18 15 12 9 6"/>',
  schedule: '<circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>',
  smartphone: '<rect x="5" y="2" width="14" height="20" rx="2"/><line x1="12" y1="18" x2="12" y2="18.01"/>'
};

/**
 * 自绘日期组件:文本输入 + 页面内日历面板(不用浏览器原生弹窗,杜绝核显卡顿)。
 * 返回容器,.value 为规范的 yyyy-MM-dd(空串表示未选)。
 */
function parseCalendarDay(s) {
  var t = String(s == null ? '' : s).trim();
  var m = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(t) || /^(\d{4})(\d{2})(\d{2})$/.exec(t);
  if (!m || +m[1] < 1) return null;
  var d = new Date(2000, +m[2] - 1, +m[3]);
  d.setFullYear(+m[1]);
  return d.getFullYear() === +m[1] && d.getMonth() === +m[2] - 1 && d.getDate() === +m[3] ? d : null;
}

function formatCalendarDay(d) {
  return ('0000' + d.getFullYear()).slice(-4) + '-' + ('0' + (d.getMonth() + 1)).slice(-2) + '-' + ('0' + d.getDate()).slice(-2);
}

function makeDateField() {
  function p2(n) { return n < 10 ? '0' + n : '' + n; }
  function fmt(d) { return formatCalendarDay(d); }
  function parseDayText(s) { return parseCalendarDay(s); }

  var input = h('input', { type: 'text', placeholder: '年/月/日', autocomplete: 'off' });
  var iconBtn = h('button', {
    type: 'button', class: 'cal-icon', html: iconSvg('calendar'), title: '选择日期'
  });
  var wrap = h('div', { class: 'cal' }, input, iconBtn);
  var panel = null;
  var renderPanel = null;
  var view = { y: 0, m: 0 };

  /** 月份输入(2026-09):合法返回规范化 YYYY-MM,否则 null */
  function normMonthText(t) {
    var m = /^(\d{4})-(\d{1,2})$/.exec(String(t == null ? '' : t).trim());
    return m && +m[2] >= 1 && +m[2] <= 12 ? m[1] + '-' + p2(+m[2]) : null;
  }

  function commitCalInput(text) {
    var mo = normMonthText(text);
    if (mo) {
      input.value = mo;
      input.classList.remove('bad');
      wrap.dispatchEvent(new CustomEvent('change'));
      return;
    }
    var d = parseDayText(text);
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
      var d = parseDayText(input.value);
      if (d) { return fmt(d); }
      return normMonthText(input.value) || '';
    },
    set: function (v) {
      var d = parseDayText(v);
      input.value = d ? fmt(d) : String(v == null ? '' : v);
      input.classList.toggle('bad', !!input.value && !d && !normMonthText(input.value));
    }
  });
  Object.defineProperty(wrap, 'rawValue', { get: function () { return input.value; } });

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

    // 视图模式: 'day'(日历) | 'year'(年份选取) | 'month'(月份选取)
    var viewMode = 'day';

    function renderYearPicker() {
      var today = new Date();
      var base = view._yearPage != null ? view._yearPage : view.y - 4;
      title.textContent = base + ' - ' + (base + 15) + ' 年';
      title.title = '点击选择年份';
      grid.innerHTML = '';
      for (var yy = base; yy < base + 16; yy++) {
        (function (year) {
          var cls = 'cal-year-cell' + (year === view.y ? ' sel' : '') + (year === today.getFullYear() ? ' today' : '');
          grid.appendChild(h('span', {
            class: cls, text: String(year),
            onclick: function () {
              view.y = year;
              view._yearPage = null;
              viewMode = 'month';
              renderPanel();
            }
          }));
        })(yy);
      }
    }

    function renderMonthPicker() {
      title.textContent = view.y + ' 年';
      title.title = '点击回到年份选择';
      grid.innerHTML = '';
      var monthNames = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月'];
      var today = new Date();
      monthNames.forEach(function (mn, idx) {
        var cls = 'cal-year-cell' + (idx === view.m ? ' sel' : '') + (view.y === today.getFullYear() && idx === today.getMonth() ? ' today' : '');
        grid.appendChild(h('span', {
          class: cls, text: mn,
          onclick: function () {
            view.m = idx;
            viewMode = 'day';
            renderPanel();
          }
        }));
      });
    }

    renderPanel = function () {
      if (viewMode === 'year') { renderYearPicker(); return; }
      if (viewMode === 'month') { renderMonthPicker(); return; }
      title.textContent = '';
      title.title = '';
      var yLink = h('span', { class: 'cal-title-y', text: view.y + '年' });
      yLink.title = '点击选年份';
      yLink.addEventListener('click', function (e) {
        e.stopPropagation();
        viewMode = 'year';
        renderPanel();
      });
      var mLink = h('span', { class: 'cal-title-m', text: (view.m + 1) + '月' });
      mLink.title = '点击选月份';
      mLink.addEventListener('click', function (e) {
        e.stopPropagation();
        viewMode = 'month';
        renderPanel();
      });
      title.appendChild(yLink);
      title.appendChild(document.createTextNode(' '));
      title.appendChild(mLink);
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
      if (viewMode === 'year') {
        var base = view._yearPage != null ? view._yearPage : view.y - 4;
        view._yearPage = base - 16;
        renderYearPicker();
      } else if (viewMode === 'month') {
        view.y--; renderMonthPicker();
      } else {
        view.m--; if (view.m < 0) { view.m = 11; view.y--; } renderPanel();
      }
    });
    nextBtn.addEventListener('click', function () {
      if (viewMode === 'year') {
        var base2 = view._yearPage != null ? view._yearPage : view.y - 4;
        view._yearPage = base2 + 16;
        renderYearPicker();
      } else if (viewMode === 'month') {
        view.y++; renderMonthPicker();
      } else {
        view.m++; if (view.m > 11) { view.m = 0; view.y++; } renderPanel();
      }
    });
    // 整标题点击 → 年份选择(日视图时; 年/月视图由子链接处理)
    title.addEventListener('click', function () {
      if (viewMode === 'day') { viewMode = 'year'; renderPanel(); }
      else if (viewMode === 'year') { viewMode = 'month'; renderPanel(); }
      else { viewMode = 'day'; renderPanel(); }
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
    var base = parseDayText(input.value);
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
      commitCalInput(input.value);
      wrap.classList.remove('open');
      e.preventDefault();
    }
  });
  input.addEventListener('blur', function () {
    commitCalInput(input.value);
  });

  return wrap;
}

/** 进度弹窗:返回 {close, setProgress(pct), setStatus(text)} */
function showProgressModal(title) {
  var fill = h('div', { class: 'pg-fill', style: 'width:0%' });
  var bar = h('div', { class: 'pg-bar' }, fill);
  var status = h('div', { style: 'margin:12px 0 6px;font-size:13px;color:var(--c-sub, #66788c)', text: '准备中…' });
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
  var btn = h('button', { type: 'button', class: 'dd-btn', 'aria-haspopup': 'listbox', 'aria-expanded': 'false' }, txt, arrow);
  var list = h('div', { class: 'dd-list', role: 'listbox', tabindex: '-1', 'aria-label': ph });

  var wrap = h('div', { class: 'dd' }, btn, list);

  function closeList() {
    wrap.classList.remove('open');
    btn.setAttribute('aria-expanded', 'false');
  }
  function choose(v) {
    wrap.value = v;
    closeList();
    wrap.dispatchEvent(new CustomEvent('change'));
  }

  items.forEach(function (it) {
    list.appendChild(h('div', {
      class: 'dd-item', text: it.t, 'data-v': it.v, role: 'option', tabindex: '-1',
      onclick: (function (v) {
        return function () { choose(v); };
      })(it.v)
    }));
  });

  function refresh() {
    var found = null;
    for (var i = 0; i < items.length; i++) {
      if (items[i].v === cur) { found = items[i]; break; }
    }
    txt.textContent = found ? found.t : ph;
    txt.title = txt.textContent;
    btn.title = txt.textContent;
    txt.classList.toggle('ph', cur === '');
    $all('.dd-item', list).forEach(function (el2) {
      var isCur = el2.getAttribute('data-v') === cur;
      el2.classList.toggle('cur', isCur);
      el2.setAttribute('aria-selected', isCur ? 'true' : 'false');
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
        class: 'dd-item', text: it.t, 'data-v': it.v, role: 'option', tabindex: '-1',
        onclick: (function (v) {
          return function () { choose(v); };
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

  // 键盘操作状态:打开时高亮当前值,方向键移动、Enter/空格选择、Esc 关闭并回焦按钮
  var activeIndex = -1;
  function highlight(scroll) {
    var els = $all('.dd-item', list);
    els.forEach(function (el2, i2) { el2.classList.toggle('dd-active', i2 === activeIndex); });
    var act = els[activeIndex];
    // 仅键盘导航需要滚动定位;打开时的初始高亮不滚动,避免 scrollIntoView 引发祖先容器滚动而触发全局滚动关闭
    if (scroll && act && act.scrollIntoView) { try { act.scrollIntoView({ block: 'nearest' }); } catch (err) { /* 独立环境无滚动 */ } }
  }

  btn.addEventListener('click', function (e) {
    e.stopPropagation();
    if (btn.disabled) return; // 禁用态(锁定字段)不展开
    var opening = !wrap.classList.contains('open');
    $all('.dd.open, .cal.open').forEach(function (d) {
      d.classList.remove('open');
      if (d !== wrap && typeof d.querySelector === 'function') {
        var otherBtn = d.querySelector('button');
        if (otherBtn && otherBtn.setAttribute) otherBtn.setAttribute('aria-expanded', 'false');
      }
    });
    wrap.classList.toggle('open', opening);
    if (!opening) {
      btn.setAttribute('aria-expanded', 'false');
      return;
    }
    btn.setAttribute('aria-expanded', 'true');
    activeIndex = 0;
    for (var i = 0; i < items.length; i++) { if (items[i].v === cur) { activeIndex = i; break; } }
    ddLastOpenedAt = Date.now();
    highlight(false);
    try { list.focus(); } catch (err) { /* 独立环境 */ }
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
  list.addEventListener('keydown', function (e) {
    var els = $all('.dd-item', list);
    if (!els.length) return;
    var k = e.key;
    if (k === 'ArrowDown' || k === 'ArrowUp' || k === 'Home' || k === 'End') {
      if (e.preventDefault) e.preventDefault();
      if (k === 'ArrowDown') activeIndex = Math.min(els.length - 1, activeIndex + 1);
      else if (k === 'ArrowUp') activeIndex = Math.max(0, activeIndex - 1);
      else if (k === 'Home') activeIndex = 0;
      else activeIndex = els.length - 1;
      highlight(true);
    } else if (k === 'Enter' || k === ' ') {
      if (e.preventDefault) e.preventDefault();
      var it = items[activeIndex];
      if (it) { choose(it.v); try { btn.focus(); } catch (err) { /* 独立环境 */ } }
    } else if (k === 'Escape') {
      if (e.preventDefault) e.preventDefault();
      closeList();
      try { btn.focus(); } catch (err) { /* 独立环境 */ }
    }
  });
  // 焦点离开列表(含页面其它位置点击被全局关闭)后纠正展开标记,读屏不会读到滞留的展开状态
  list.addEventListener('blur', function () { btn.setAttribute('aria-expanded', 'false'); });

  // 禁用态:锁定字段用(如双争矩阵单元格固定年度/季度),容器没有 readOnly,统一走 disabled
  Object.defineProperty(wrap, 'disabled', {
    get: function () { return btn.disabled; },
    set: function (v) {
      btn.disabled = !!v;
      if (btn.disabled) wrap.classList.remove('open');
    }
  });

  return wrap;
}

/**
 * 省市区三级联动(字典驱动):region_province → region_city → region_district。
 * 值存为 "省/市/区县" 组合串;旧自由文本无 "/" 时原样保留,重新选择后覆盖。
 */
function makeRegionField() {
  var parts = ['', '', ''];
  var provDd = makeDropdown([], '省份');
  var cityDd = makeDropdown([], '城市');
  var distDd = makeDropdown([], '区/县');
  var wrap = h('div', { class: 'region-field' }, provDd, cityDd, distDd);

  function findItem(type, label, parentItem) {
    var hit = null;
    dictItems(type).forEach(function (d) {
      if (d.dictLabel === label
        && (!parentItem || String(d.parentId) === String(parentItem.id))) { hit = d; }
    });
    return hit;
  }
  function childrenOf(type, parentItem) {
    if (!parentItem) return [];
    return dictItems(type).filter(function (d) {
      return d.parentId && String(d.parentId) === String(parentItem.id) && d.enabled !== false;
    }).map(function (d) { return d.dictLabel; });
  }
  function sync() { wrap.dispatchEvent(new CustomEvent('change')); }
  function refreshCities(keep) {
    var provItem = parts[0] ? findItem('region_province', parts[0]) : null;
    cityDd.setOptions(childrenOf('region_city', provItem));
    if (keep && parts[1] && cityDd.value !== parts[1]) { cityDd.value = parts[1]; }
    if (!cityDd.value) { parts[1] = ''; }
    refreshDistricts(keep);
  }
  function refreshDistricts(keep) {
    // 城市级按"已选省"消歧:四个直辖市的城市项同名"直辖市"
    var provItem = parts[0] ? findItem('region_province', parts[0]) : null;
    var cityItem = parts[1] ? findItem('region_city', parts[1], provItem) : null;
    distDd.setOptions(childrenOf('region_district', cityItem));
    if (keep && parts[2] && distDd.value !== parts[2]) { distDd.value = parts[2]; }
    if (!distDd.value) { parts[2] = ''; }
  }

  provDd.addEventListener('change', function () {
    parts = [provDd.value || '', '', ''];
    cityDd.value = '';
    distDd.value = '';
    refreshCities(false);
    sync();
  });
  cityDd.addEventListener('change', function () {
    parts[1] = cityDd.value || '';
    parts[2] = '';
    distDd.value = '';
    refreshDistricts(false);
    sync();
  });
  distDd.addEventListener('change', function () { parts[2] = distDd.value || ''; sync(); });

  function joined() {
    var seg = [parts[0], parts[1], parts[2]];
    for (var i = 2; i >= 0; i--) { if (seg[i]) break; seg.pop(); }
    return seg.join('/');
  }
  Object.defineProperty(wrap, 'value', {
    get: function () { return joined(); },
    set: function (v) {
      var seg = String(v == null ? '' : v).split('/');
      parts = [seg[0] || '', seg[1] || '', seg[2] || ''];
      provDd.setOptions(dictLabels('region_province'));
      provDd.value = parts[0];
      refreshCities(true);
    }
  });
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

// 全局:点空白/按 Esc 关闭所有下拉(一次注册,避免泄漏);ddLastOpenedAt 记录最近一次打开时间供滚动豁免
var ddLastOpenedAt = 0;
document.addEventListener('click', function () {
  $all('.dd.open, .cal.open').forEach(function (d) { d.classList.remove('open'); });
});
document.addEventListener('scroll', function (e) {
  // 下拉列表/日历面板自身的滚动不关闭;只有页面或弹窗滚动才关闭(避免面板错位)
  var t = e.target;
  if (t && t.closest && t.closest('.dd.open, .cal.open')) {
    return;
  }
  // 刚打开的下拉短时间豁免:打开时的聚焦/初始定位可能引发祖先容器连锁滚动,不应立即关闭刚展开的列表
  if (Date.now() - ddLastOpenedAt < 200) {
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
  return v || 'var(--pri, #2f6fd6)';
}
/** 由主色派生深色(标题/选中态) */
function shadeColor(c, f) {
  var m = /^#([0-9a-f]{6})$/i.exec((c || '').trim());
  if (!m) { return 'var(--pri-dark, #16437e)'; }
  var n = parseInt(m[1], 16);
  var c = function (x) { return Math.max(0, Math.min(255, Math.round(x))); };
  var r = c(((n >> 16) & 255) * f), g = c(((n >> 8) & 255) * f), b = c((n & 255) * f);
  return '#' + ((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1);
}
/** 应用外观: 主题色/标签标题/favicon */
/** 一次性应用全部主题派生色(启动/选色共用) */
function applyThemeVars(c) {
  var t = /^#[0-9a-f]{6}$/i.test(c) ? c : 'var(--pri, #2f6fd6)';
  document.documentElement.style.setProperty('--pri', t);
  document.documentElement.style.setProperty('--pri-dark', shadeColor(t, 0.62));
  document.documentElement.style.setProperty('--pri-deep', shadeColor(t, 0.40));   // 侧栏底色
  document.documentElement.style.setProperty('--pri-light', shadeColor(t, 1.45));  // 亮描边
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
    var pageSize = parseInt(d.pageSize, 10);
    if (pageSize >= 1 && pageSize <= 200) SITE.pageSize = pageSize;
    applyBranding();
    return d;
  }).catch(function () { applyBranding(); });
}

/** 18位身份证号校验(GB11643): 校验位加权算法,返回生日/性别或null */
function validateIdCard(id) {
  var s = String(id || '').trim().toUpperCase();
  if (!/^\d{17}[\dX]$/.test(s)) { return null; }
  var W = [7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2];
  var CODES = '10X98765432';
  var sum = 0;
  for (var i = 0; i < 17; i++) { sum += (+s[i]) * W[i]; }
  if (CODES[sum % 11] !== s[17]) { return null; }
  return {
    birthday: s.slice(6, 10) + '-' + s.slice(10, 12) + '-' + s.slice(12, 14),
    gender: (+s[16]) % 2 === 0 ? '女' : '男'
  };
}

/** 按出生日期算年龄(以今天为基准) */
function calcAgeFrom(bd) {
  var m = /^(\d{4})-(\d{2})-(\d{2})/.exec(bd || '');
  if (!m) { return ''; }
  var t = new Date();
  var age = t.getFullYear() - (+m[1]) - ((t.getMonth() + 1 < +m[2] || (t.getMonth() + 1 === +m[2] && t.getDate() < +m[3])) ? 1 : 0);
  return (age >= 0 && age < 130) ? age : '';
}

/* ================= API 层 ================= */

var TOKEN = sessionStorage.getItem('tk') || localStorage.getItem('tk') || '';
// 本地 profile 只用于持久化,绝不作为启动权限依据;先经 /me 校验再渲染。
var USER = null;

function normalizeProfile(data) {
  data = data || {};
  return {
    userId: data.userId, username: data.username || '', displayName: data.displayName || '',
    isAdmin: data.isAdmin === true, parentId: data.parentId == null ? null : data.parentId,
    departmentIds: Array.isArray(data.departmentIds) ? data.departmentIds.slice() : [],
    departmentNames: Array.isArray(data.departmentNames) ? data.departmentNames.slice() : []
  };
}

function preferenceKey(key) {
  return 'user:' + (USER && USER.userId != null ? String(USER.userId) : 'anonymous') + ':' + key;
}

function hasGlobalAccess() { return !!(USER && USER.isAdmin === true); }

function canAccessView(view) {
  return hasGlobalAccess() || ['settings', 'backup', 'log', 'report', 'dict',
    'fitness-std', 'fitness-standards', 'military-schemes'].indexOf(view) < 0;
}

function refreshSessionProfile() {
  var token = TOKEN;
  USER = null;
  return GET('/api/auth/me').then(function (profile) {
    if (!token || TOKEN !== token) throw new Error('登录状态已变更,请重新登录');
    if (!profile || profile.userId == null || !profile.username) throw new Error('无法确认当前账号,请重新登录');
    saveSession(token, normalizeProfile(profile), localStorage.getItem('tk') === token);
    return USER;
  });
}

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
  try { sessionStorage.removeItem('tk'); sessionStorage.removeItem('usr'); } catch (e) { /* 忽略 */ }
  localStorage.removeItem('tk'); localStorage.removeItem('usr');
}

var redirectingToLogin = false;

function forceLogin(msg) {
  // 并发的多个 401(如重启后页面启动时的字典/单位/选项三个请求)
  // 只弹一次提示、只渲染一次登录页
  if (redirectingToLogin) {
    return;
  }
  redirectingToLogin = true;
  clearSession();
  $all('.mask').forEach(function (mask) { mask.remove(); });
  if ($('.login-wrap')) { if (msg) toast(msg, 'err'); }
  else renderLogin(msg);
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

function loadAllRows(path) {
  var rows = [], page = 1;
  function next() {
    return GET(path + (path.indexOf('?') >= 0 ? '&' : '?') + 'page=' + page + '&size=200').then(function (data) {
      var list = (data && data.list) || [];
      rows = rows.concat(list);
      if (rows.length >= Number(data && data.total || 0)) return { list: rows, total: rows.length };
      if (!list.length) throw new Error('数据分页未完整返回,请刷新后重试');
      page++;
      return next();
    });
  }
  return next();
}

/**
 * FLIP 滑动:布局瞬时切换到终态,再施加反向 transform 从旧视觉位置平滑滑入。
 * 只动画 transform(合成层,GPU 加速),避免逐帧 width/margin 重排大表格造成卡顿。
 */
function slideFrom(el, deltaX, duration) {
  if (!el || !deltaX) return;
  if (prefersReducedMotion()) return; // 减动效:不做位移动画,元素保持终态
  var ms = duration || MOTION.panel;
  el.style.transition = 'none';
  el.style.transform = 'translateX(' + deltaX + 'px)';
  void el.offsetWidth; // 强制回流,让起始位置先生效
  el.style.transition = 'transform ' + ms + 'ms ' + MOTION.glide;
  el.style.transform = 'translateX(0)';  var done = false;
  function clear() {
    if (done) return;
    done = true;
    el.style.transition = '';
    el.style.transform = '';
    el.removeEventListener('transitionend', clear);
  }
  el.addEventListener('transitionend', clear);
  var timer = setTimeout(clear, ms + 100);
  // 可选取消句柄供需要销毁/快速反向切换的视图使用，现有调用方可继续忽略返回值。
  return function () { clearTimeout(timer); clear(); };
}

/* 统计数字入场滚动:从 0 数到目标(仅"纯数字"或"数字+单位后缀"),rAF easeOut ~600ms;
   减动效/非正数/解析失败时直接终值,不阻塞渲染;重入时取消上一轮帧。 */
function countUp(el, text) {
  var m = /^(\d+)(.*)$/.exec(String(text == null ? '' : text).trim());
  if (!el || !m) return;
  var target = Number(m[1]), suffix = m[2];
  if (!isFinite(target) || target <= 0 || prefersReducedMotion()) return;
  if (typeof requestAnimationFrame !== 'function') return; // 无 rAF 环境(单测沙箱)直接终值
  if (el._countFrame) cancelAnimationFrame(el._countFrame);
  var start = null, dur = 600;
  function frame(ts) {
    if (start === null) start = ts;
    var p = Math.min(1, (ts - start) / dur);
    var eased = 1 - Math.pow(1 - p, 3); // easeOutCubic
    el.textContent = Math.round(target * eased) + suffix;
    if (p < 1) el._countFrame = requestAnimationFrame(frame);
    else el._countFrame = null;
  }
  el._countFrame = requestAnimationFrame(frame);
}

/* 表格加载骨架屏:替代"加载中…"文字行,宽度错落的扫光占位条;
   减动效时 shimmer 被全局块置为静止(仍保留占位高度)。 */
function skeletonRows(cols) {
  var out = '';
  for (var i = 0; i < 8; i++) {
    out += '<tr><td colspan="' + cols + '"><div class="sk-bar" style="width:' + (42 + (i * 13) % 48) + '%"></div></td></tr>';
  }
  return out;
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

/**
 * 照片 Blob URL 生命周期绑定:元素复用替换旧 URL、元素移出文档(弹窗关闭/表格重渲染)后自动释放。
 * 避免长期使用中弹窗照片与列表缩略图的 ObjectURL 无限累积占内存。
 * 轮询用短间隔定时器,在可 unref 的环境(测试)不阻塞进程退出;最长观察约5分钟后放弃回收。
 */
function bindBlobUrl(el, blob, asBackground) {
  var old = el._blobUrl;
  var url = URL.createObjectURL(blob);
  el._blobUrl = url;
  if (asBackground) { el.style.backgroundImage = 'url(' + url + ')'; }
  else { el.src = url; }
  if (old) { URL.revokeObjectURL(old); }
  var checks = 0;
  (function poll() {
    var timer = setTimeout(function () {
      if (el._blobUrl !== url) { return; }
      if (!document.body.contains(el)) { el._blobUrl = null; URL.revokeObjectURL(url); return; }
      if (++checks < 120) { poll(); }
    }, 2500);
    if (timer && typeof timer.unref === 'function') { timer.unref(); }
  })();
}

/* ================= 全局缓存 ================= */

var Ctx = {
  dictTypes: [],        // [{type,name,items:[DictItem]}]
  deptTree: [],         // 嵌套单位树
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
    (function walk(nodes, depth, parentId, parentPath) {
      (nodes || []).forEach(function (n) {
        var path = String(n.path || (parentPath || '') + n.name).replace(/\s*\/\s*/g, '');
        Ctx.deptFlat.push({ id: n.id, name: n.name, path: path, depth: depth, parentId: parentId });
        walk(n.children, depth + 1, n.id, path);
      });
    })(Ctx.deptTree, 0, null, '');
  }).catch(function () { Ctx.deptTree = []; Ctx.deptFlat = []; });
}

function refreshPersonnelOptions() {
  return GET('/api/personnel/options').then(function (list) {
    Ctx.personnelOptions = list || [];
  }).catch(function () { Ctx.personnelOptions = []; });
}

function refreshAll() {
  return Promise.all([refreshDicts(), refreshDepts(), refreshPersonnelOptions(), loadGradeRule(), loadHolidayMap(), reloadFitnessMatrix()]);
}

function loadRowPhotos(root) {
  $all('img[data-pid]', root).forEach(function (img) {
    var id = img.getAttribute('data-pid');
    var key = 'p' + id;
    if (Ctx.photoUrls[key]) { img.src = Ctx.photoUrls[key]; return; }
    apiBlob('/api/personnel/' + id + '/photo').then(function (r) {
      var url = URL.createObjectURL(r.blob);
      if (Ctx.photoUrls[key]) { URL.revokeObjectURL(Ctx.photoUrls[key]); }
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
  var fieldSeq = 0; // 字段ID序列:label 关联输入控件(读屏朗读/点击聚焦)用
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
    var fieldId = 'fitem' + (++fieldSeq) + '_' + f.key;
    wrap.appendChild(h('label', { html: esc(f.label) + (f.required ? '<span class="req">*</span>' : ''), 'for': fieldId }));

    var val = values[f.key];
    var el;
    if (f.type === 'select' || f.type === 'personnel' || f.type === 'dept') {
      var ddOpts;
      if (f.type === 'select') {
        ddOpts = (typeof f.options === 'function' ? f.options() : f.options) || [];
      } else if (f.type === 'personnel') {
        ddOpts = Ctx.personnelOptions.map(function (p) {
          return { v: String(p.id), l: p.name + ((p.departmentPath || p.departmentName) ? ' (' + (p.departmentPath || p.departmentName) + ')' : '') };
        });
      } else {
        ddOpts = Ctx.deptFlat.map(function (d) {
          return { v: String(d.id), l: d.path || new Array(d.depth + 1).join('　') + d.name };
        });
      }
      el = makeDropdown(ddOpts, f.placeholder || (f.type === 'dept' && !f.required ? '(不指定)' : '请选择'));
      if (val !== undefined && val !== null && String(val) !== '') el.value = String(val);
    } else if (f.type === 'date') {
      el = makeDateField();
      if (val !== undefined && val !== null && String(val) !== '') el.value = String(val);
    } else if (f.type === 'region') {
      el = makeRegionField();
      el.value = val === undefined || val === null ? '' : String(val);
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
      var type = f.type === 'number' ? 'number' : (f.type === 'password' ? 'password' : 'text');
      el = h('input', { type: type, placeholder: f.placeholder || '' });
      if (f.step) el.setAttribute('step', f.step);
      if (f.min !== undefined) el.setAttribute('min', f.min);
      if (f.max !== undefined) el.setAttribute('max', f.max);
      if (val !== undefined && val !== null) el.value = val;
    }
    if (f.required && el.tagName && /^(INPUT|TEXTAREA|SELECT)$/i.test(el.tagName)) {
      el.setAttribute('required', 'required');
      el.setAttribute('aria-required', 'true');
    }
    if (f.type === 'personnel' || f.type === 'dept') el.classList.add('personnel-path-dropdown');
    // 只读字段:自动计算/自动带出的值不允许手改
    if (f.readonly) {
      if (el.classList && el.classList.contains('dd')) {
        el.disabled = true; // 下拉容器无 readOnly,禁用其按钮
      } else if (el.classList && el.classList.contains('cal')) {
        var calInp = el.querySelector('input');
        if (calInp) calInp.readOnly = true;
        var calBtn = el.querySelector('.cal-icon');
        if (calBtn) calBtn.style.display = 'none';
      } else {
        try { el.readOnly = true; } catch (e) { /* ignore */ }
      }
      el.classList.add('ro');
    }
    // 关联 label:复合控件(dd/cal/region)把 id 落到内部第一个可聚焦元素,点击 label 可聚焦,读屏朗读字段名
    var labelTarget = el;
    if (el.classList && el.classList.contains('dd')) {
      labelTarget = el.querySelector('button') || el;
    } else if (typeof el.querySelector === 'function') {
      var innerFocus = el.querySelector('input, button');
      if (innerFocus) labelTarget = innerFocus;
    }
    if (labelTarget && labelTarget.setAttribute) labelTarget.setAttribute('id', fieldId);
    inputs[f.key] = el;
    wrap.appendChild(el);
    if (f.hint) {
      wrap.appendChild(h('div', { style: 'font-size:12px;color:var(--c-muted,#8a97a8);margin-top:4px;line-height:1.4', text: f.hint }));
    }
    panel.appendChild(wrap);

    // 人员下拉旁加"选择"按钮:弹窗按姓名/身份证/单位搜索选人
    if (f.type === 'personnel') {
      attachPersonPick(el, function (p) {
        el.value = String(p.id);
        el.dispatchEvent(new CustomEvent('change'));
      });
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

  /** 校验失败定位:跳到字段所在标签页,红框 + 气泡提示 + 滚动 + 聚焦 */
  function markBad(el, reason) {
    el.classList.add('bad');
    showFieldBubble(el, reason || el.title || '该项为必填,请填写');
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
          out[f.key] = f.type === 'password' ? String(el.value || '') : String(el.value || '').trim();
        }
      });
      // 先检查原始日期文本。可选字段的非法输入不能被组件的空值归一化吞掉。
      for (var di = 0; di < fields.length; di++) {
        var df = fields[di];
        if (df.section || df.type !== 'date') continue;
        var dateEl = inputs[df.key];
        var rawDate = String(dateEl.rawValue === undefined ? dateEl.value : dateEl.rawValue).trim();
        var parsedDate = parseCalendarDay(rawDate);
        if (rawDate && !parsedDate) {
          if (useTabs) activateTab(sectionOf[df.key]);
          markBad(dateEl, '「' + df.label + '」不是有效日期');
          this.setError('请检查日期「' + df.label + '」');
          return null;
        }
        out[df.key] = parsedDate ? formatCalendarDay(parsedDate) : '';
      }
      if (this.validatePerson && !this.validatePerson()) return null;
      // 再校验(含条件必填)
      var firstBadLabel = '';
      fields.forEach(function (f) {
        if (f.section) { return; }
        var v = out[f.key];
        var need = f.required || (f.requiredIf && f.requiredIf(out));
        // 编辑模式:历史数据本就为空的必填项(含条件必填)不阻塞保存(旧数据渐进完善;清空原有值仍会被拦截;军人证件号为关键标识必须补全,不豁免)
        // 仅对携带 id 的编辑行生效,新增表单(无 id)必填保持强制
        var legacyBlank = values && values.id != null && f.key !== 'militaryIdNo' && !String(values[f.key] == null ? '' : values[f.key]).trim();
        if (need && !v && !legacyBlank) {
          missing.push(f.label);
          if (!firstBadKey) { firstBadKey = f.key; firstBadLabel = f.label; }
        }
      });
      if (missing.length) {
        if (firstBadKey) {
          if (useTabs) {
            activateTab(sectionOf[firstBadKey]);
          }
          markBad(inputs[firstBadKey], '「' + firstBadLabel + '」为必填项');
        }
        this.setError('请填写:' + missing.join('、'));
        return null;
      }
      return out;
    }
  };
}

/**
 * 导入结果摘要(人员多Sheet / 普通模块 两种返回结构)
 */
function importFileErrorMessage(error, isPersonnel) {
  var raw = String(error || '');
  var subject = isPersonnel ? '人员' : '数据';
  if (/Your InputStream was neither an OLE2 stream, nor an OOXML stream|文件解析失败/i.test(raw)) {
    return '无法读取导入文件，未导入任何' + subject + '。请重新下载系统提供的导入模板，确认文件为 .xlsx 格式；不要使用 CSV、旧版 .xls，也不要只修改文件后缀。若仍失败，请重新下载模板后再试。';
  }
  return '导入未完成，未导入任何' + subject + '。请检查文件后重试；如果问题仍然存在，请联系系统管理员。';
}

function importResultSummary(r, isPersonnel) {
  r = r || {};
  if (r.error) return importFileErrorMessage(r.error, isPersonnel);
  var msg;
  if (r.subTotal !== undefined) {
    msg = '导入完成:人员 ' + ((r.added || 0) + (r.updated || 0)) + ' 条(新增 ' + (r.added || 0) + ',更新 ' + (r.updated || 0) + '),子记录 ' + r.subTotal + ' 条';
    var parts = [];
    for (var k in r) {
      if (k !== 'mainCount' && k !== 'subTotal' && k !== 'errors' && k !== 'error' && k !== 'failed' && k !== 'subFailed' && k !== 'cardWarn'
          && k !== 'added' && k !== 'updated' && r[k] > 0) {
        parts.push(k + ' ' + r[k] + '条');
      }
    }
    if (parts.length) { msg += '(' + parts.join(',') + ')'; }
    if ((r.failed || 0) > 0) { msg += ';人员失败 ' + r.failed + ' 条'; }
    if (r.subFailed > 0) { msg += ';子记录失败 ' + r.subFailed + ' 条'; }
    if (r.cardWarn > 0) { msg += ';注意:' + r.cardWarn + ' 条身份证号不是18位,请核对'; }
  } else {
    msg = '导入完成:新增 ' + (r.added || 0) + ' 条,更新 ' + (r.updated || 0) + ' 条,失败 ' + (r.failed || 0) + ' 条';
  }
  return msg;
}

/**
 * 导入失败明细弹窗:汇总一行 + 逐条失败原因(行号+原因),滚动由弹窗体承担
 */
function openImportResultModal(summary, errors, fileError) {
  var list = h('div', { class: 'import-err-list' });
  errors.forEach(function (e) {
    list.appendChild(h('div', { class: 'import-err-row', text: String(e) }));
  });
  var body = h('div', {}, h('div', { style: 'margin-bottom:12px;font-size:14px;color:#26313d', text: summary }));
  if (fileError) {
    body.appendChild(h('details', { style: 'margin-top:12px;color:#64748b' },
      h('summary', { style: 'cursor:pointer' }, '技术详情'),
      h('div', { style: 'margin-top:8px;white-space:pre-wrap;word-break:break-word', text: String(fileError) })));
  } else if (errors.length) {
    body.appendChild(h('div', { style: 'font-size:13px;color:#d9463e;font-weight:600;margin-bottom:8px', text: '以下 ' + errors.length + ' 条记录未导入:' }));
    body.appendChild(list);
  }
  var m = openModal({
    title: '导入结果',
    body: body,
    footer: [h('button', { class: 'btn btn-ghost', text: '下载失败明细', onclick: function () {
      var report = errors.map(function (e) { return String(e); });
      if (fileError) report.unshift('技术详情: ' + String(fileError));
      downloadBlob(new Blob(['\uFEFF' + summary + '\r\n\r\n' + report.join('\r\n')], { type: 'text/plain;charset=utf-8' }), '导入失败明细.txt');
    } }), h('button', { class: 'btn btn-primary', text: '知道了', onclick: function () { m.close(); } })]
  });
}

/* ================= 分页 ================= */

/** 体能汇总(宽行)单元格文本:成绩（分数/等级）,无该项目显示— */
function summaryScoreText(rec) {
  if (!rec) return '—';
  var score = (rec.score === null || rec.score === undefined) ? '' : String(rec.score);
  var sub = [];
  if (rec.points !== null && rec.points !== undefined && rec.points !== '') { sub.push(rec.points + '分'); }
  if (rec.grade) { sub.push(String(rec.grade)); }
  var text = score;
  if (sub.length) { text += '（' + sub.join('/') + '）'; }
  return text || '—';
}

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

/** 人员选择弹窗:按姓名/身份证/单位搜索后点选,onPick({id,name,idCard,departmentName}) */
function openPersonPicker(onPick) {
  var selName = h('input', { type: 'text', placeholder: '姓名' });
  var selIdCard = h('input', { type: 'text', placeholder: '身份证号' });
  var selDept = makeDropdown(Ctx.deptFlat.map(function (d) {
    return { v: String(d.id), l: d.path || new Array(d.depth + 1).join('　') + d.name };
  }), '全部单位');
  selDept.classList.add('personnel-path-dropdown');
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
            onPick({ id: p.id, name: p.name || '', idCard: p.idCard || '', departmentId: p.departmentId,
              departmentName: p.departmentName || '', departmentPath: p.departmentPath || p.departmentName || '' });
          }
        },
          h('b', { text: p.name || '' }),
          h('span', { text: p.idCard || '' }),
          h('span', { class: 'personnel-department-path', title: p.departmentPath || p.departmentName || '',
            text: (String(p.departmentPath || p.departmentName || '').replace(/\s*\/\s*/g, '') + (p.rank ? ' · ' + p.rank : '')).trim() })));
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
function setupPersonLookup(form, map, options) {
  options = options || {};
  var idCardEl = form.inputs[map.idCard];
  var nameEl = form.inputs[map.name];
  var pidEl = form.inputs[map.pid];
  if (!idCardEl) return;
  var departmentHint = nameEl && nameEl.parentElement ? h('div', { class: 'personnel-department-path person-lookup-summary' }) : null;
  if (departmentHint) nameEl.parentElement.appendChild(departmentHint);
  var loadId = 0, matchedCard = '', matchedId = '';
  var previousValidator = form.validatePerson;
  function card() { return String(idCardEl.value || '').trim(); }
  function changed() { if (options.onChanged) options.onChanged(); }
  function clear() {
    loadId++;
    matchedCard = ''; matchedId = '';
    if (departmentHint) { departmentHint.textContent = ''; departmentHint.title = ''; }
    if (nameEl) nameEl.value = '';
    if (pidEl) pidEl.value = '';
    idCardEl.classList.remove('bad');
    idCardEl.title = '';
    changed();
  }
  function accept(p, v) {
    if (!p || p.id === undefined || p.id === null || (p.idCard && String(p.idCard).trim() !== v)) return false;
    matchedCard = v; matchedId = String(p.id);
    if (nameEl) nameEl.value = p.name || '';
    if (pidEl) pidEl.value = matchedId;
    idCardEl.classList.remove('bad');
    idCardEl.title = p.departmentPath || p.departmentName || '';
    if (departmentHint) { departmentHint.textContent = idCardEl.title; departmentHint.title = idCardEl.title; }
    form.setError('');
    changed();
    return true;
  }
  function lookup() {
    clear();
    var requestId = loadId, v = card();
    if (!v) return;
    GET((options.endpoint || '/api/personnel/by-idcard') + '?idCard=' + encodeURIComponent(v)).then(function (result) {
      if (requestId !== loadId || card() !== v) return;
      var p = options.list ? (result || [])[0] : result;
      if (accept(p, v)) return;
      idCardEl.classList.add('bad');
      idCardEl.title = '未找到对应人员';
      form.setError('未找到身份证号「' + v + '」对应的人员,请核对后重新输入');
    }).catch(function (e) {
      if (requestId !== loadId || card() !== v) return;
      form.setError('人员查询失败，请重试：' + (e.message || '请求失败'));
    });
  }
  form.validatePerson = function () {
    if (previousValidator && !previousValidator()) return false;
    var v = card();
    if (!v && (!pidEl || !pidEl.value)) return true;
    if (v && v === matchedCard && matchedId && pidEl && String(pidEl.value) === matchedId) return true;
    form.setError('身份证号与关联人员尚未匹配，请完成查询或重新选择人员后保存');
    return false;
  };
  idCardEl.addEventListener('input', clear);
  idCardEl.addEventListener('change', lookup);
  attachPersonPick(nameEl, function (p) {
    clear();
    idCardEl.value = p.idCard || '';
    if (card()) accept(p, card());
  });
  // 初始化立即清除未经反查确认的旧关联，不留可直接保存的时间窗口。
  if (card()) lookup();
  else clear();
}

/** 上报导入结果弹窗:展示人员/子表/休假(含计划)/体能/军事/手机/资产合并统计与告警 */
function openReportResult(r) {
  function stat(label, added, total) {
    return h('div', { style: 'display:flex;justify-content:space-between;padding:5px 0;border-bottom:1px dashed var(--c-line,#e3e9f2)' },
      h('span', { text: label }),
      h('b', { text: '新增 ' + (added || 0) + ' / 共 ' + (total || 0) + ' 条' }));
  }
  var body = h('div');
  body.appendChild(h('div', { style: 'background:#eef4ff;border-radius:8px;padding:10px 14px;margin-bottom:12px;line-height:1.8;font-size:13.5px',
    html: '数据已并入:<b>' + esc(r.deptName || '') + '</b>' + (r.created ? '(新建单位)' : '') +
      ' · 人员 新增 ' + (r.added || 0) + ' / 更新 ' + (r.updated || 0) + (r.skipped ? ' / 跳过 ' + r.skipped : '') }));
  body.appendChild(stat('档案子表', r.subRows, r.subRows));
  body.appendChild(stat('休假记录', r.leaveAdded, r.leaveTotal));
  body.appendChild(stat('休假计划', r.leavePlanAdded, r.leavePlanTotal));
  body.appendChild(stat('体能考核', r.fitnessAdded, r.fitnessTotal));
  body.appendChild(stat('军事考核', r.militaryAdded, r.militaryTotal));
  body.appendChild(stat('手机设备', r.phoneAdded, r.phoneTotal));
  body.appendChild(stat('装备', r.equipmentAdded, r.equipmentTotal));
  body.appendChild(stat('物资', r.materialAdded, r.materialTotal));
  body.appendChild(stat('车辆', r.vehicleAdded, r.vehicleTotal));
  var warnList = r.warnings || [];
  if (warnList.length) {
    var wBox = h('div', { style: 'margin-top:12px;max-height:180px;overflow:auto;border:1px solid var(--c-warn,#e8960e);border-radius:8px;padding:8px 12px;font-size:12.5px;color:#8a5a00' });
    wBox.appendChild(h('div', { style: 'font-weight:600;margin-bottom:4px', text: '告警(' + warnList.length + ' 条,均未静默覆盖):' }));
    warnList.forEach(function (w) { wBox.appendChild(h('div', { style: 'margin:2px 0', text: '· ' + w })); });
    body.appendChild(wBox);
  }
  var m = openModal({
    title: '接收下级数据 · 结果', body: body,
    footer: [h('button', { class: 'btn btn-primary', text: '关闭', onclick: function () { m.close(); } })]
  });
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

  /* 身份证反查复用统一的请求序号、输入快照及保存前匹配校验。 */
  var travelLoadId = 0, refLoadId = 0;

  /* —— 路途天数每年度限一次 —— */
  function checkTravel() {
    if (!personEl || !yearEl || !travelEl) return;
    var requestId = ++travelLoadId;
    var pid = personEl.value;
    var yr = String(yearEl.value || '').trim();
    if (!pid || !yr) { unlockTravel(); return; }
    function current() { return requestId === travelLoadId && personEl.value === pid && String(yearEl.value || '').trim() === yr; }
    GET('/api/leave/check-travel?personnelId=' + encodeURIComponent(pid) + '&year=' + encodeURIComponent(yr)).then(function (used) {
      if (!current()) return;
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
          w.style.cssText = 'color:var(--c-danger, #d9463e);font-size:12px;margin-left:8px';
          w.textContent = '(' + yr + '年度路途已在 ' + when + ' 使用)';
          lbl.appendChild(w);
        }
        calc();
      } else {
        unlockTravel();
      }
    }).catch(function () { if (current()) unlockTravel(); });
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

  function refreshYear() { checkTravel(); updateTotal(); loadLeaveRef(); }

  /* —— 休假参考:上次休假情况 + 本年度休假计划(新增/编辑登记时辅助判断) —— */
  var leaveRefBox = h('div');
  leaveRefBox.style.cssText = 'margin-top:14px;border:1px solid var(--c-line,#e3e9f2);border-radius:8px;background:var(--c-bg,#f6f8fb);padding:10px 12px;font-size:12.5px;line-height:1.8;color:var(--c-dark,#1d2c40)';
  function refTitle(txt) {
    return h('div', { style: 'font-weight:600;color:var(--pri-dark,#16437e);margin-bottom:2px', text: txt });
  }
  function loadLeaveRef() {
    var requestId = ++refLoadId;
    if (!form.el || !document.contains(form.el)) { return; }  // 弹窗已关闭则不再加载
    var pid = personEl ? String(personEl.value || '').trim() : '';
    if (!pid) { leaveRefBox.innerHTML = ''; return; }
    if (!leaveRefBox.parentElement) { form.el.parentElement.appendChild(leaveRefBox); }
    var yr = yearEl && String(yearEl.value || '').trim() ? String(yearEl.value).trim() : String(new Date().getFullYear());
    Promise.all([
      GET('/api/leave?personnelId=' + pid + '&page=1&size=1'),
      GET('/api/leave-plan?personnelId=' + pid + '&year=' + encodeURIComponent(yr) + '&page=1&size=50')
    ]).then(function (rs) {
      if (requestId !== refLoadId || String(personEl.value || '').trim() !== pid || !document.contains(form.el)) { return; }
      leaveRefBox.innerHTML = '';
      var last = (rs[0] && rs[0].list && rs[0].list[0]) || null;
      var lastSec = h('div');
      lastSec.appendChild(refTitle('上次休假'));
      if (last) {
        var total = (Number(last.days) || 0) + (Number(last.travelDays) || 0) + (Number(last.holidayDays) || 0);
        lastSec.appendChild(h('div', { text: (last.startDate || '—') + ' ~ ' + (last.endDate || '—') + ' · ' + (last.leaveType || '—') +
          (total > 0 ? ' · 共' + (Math.round(total * 10) / 10) + '天' : '') }));
      } else {
        lastSec.appendChild(h('div', { style: 'color:var(--c-muted,#8a97a8)', text: '暂无休假记录' }));
      }
      leaveRefBox.appendChild(lastSec);
      var plans = (rs[1] && rs[1].list) || [];
      var planSec = h('div', { style: 'margin-top:6px' });
      planSec.appendChild(refTitle(yr + '年度休假计划'));
      if (plans.length) {
        plans.forEach(function (p) {
          planSec.appendChild(h('div', {
            text: (p.start_date || '—') + ' ~ ' + (p.end_date || '—') + ' · ' + (p.leave_type || '—') +
              (p.days ? ' · ' + p.days + '天' : '') + (p.remark ? ' · ' + p.remark : '')
          }));
        });
      } else {
        planSec.appendChild(h('div', { style: 'color:var(--c-muted,#8a97a8)', text: '暂无休假计划' }));
      }
      leaveRefBox.appendChild(planSec);
    }).catch(function () { if (requestId === refLoadId) leaveRefBox.innerHTML = ''; });
  }

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
    if (endDateEl) {
      endDateEl.value = '';
      var d = parseCalendarDay(sd);
      var total = bd + hd + td;
      if (d && bd > 0 && td >= 0 && hd >= 0 && isFinite(total)) {
        d.setDate(d.getDate() + Math.ceil(total) - 1);
        endDateEl.value = formatCalendarDay(d);
      }
    }
    updateTotal();
  }

  startDateEl.addEventListener('change', calc);
  daysEl.addEventListener('input', calc);
  if (travelEl) travelEl.addEventListener('input', calc);
  if (holidayEl) holidayEl.addEventListener('input', calc);
  if (row) setTimeout(calc, 200);
  setupPersonLookup(form, { idCard: 'idCard', name: 'personnelName', pid: 'personnelId' }, {
    endpoint: '/api/leave/find-person', list: true, onChanged: refreshYear
  });
}

/** 人员删除撤回:列出最近删除的人员(含级联数据快照),可一键恢复或彻底删除 */
function openPersonnelUndo() {
  var listBox = h('div');
  var m = openModal({
    title: '删除撤回',
    body: h('div', {},
      h('div', { style: 'font-size:13px;color:var(--c-sub, #66788c);margin-bottom:12px;line-height:1.7',
        text: '可恢复最近删除的人员及其全部关联记录(手机、休假、体能、档案子表、装备/物资/车辆保管、单位负责人、照片)。快照保留 30 天,总量最多 1000 条,批量删除逐人保留。' }),
      listBox),
    wide: true,
    footer: [
      h('button', {
        class: 'btn btn-ghost', text: '全部撤回',
        onclick: function () {
          var rows = $all('[data-undo-id]', listBox);
          if (!rows.length) { toast('没有可撤回的记录', 'err'); return; }
          confirmBox('撤回全部 ' + rows.length + ' 名人员及其关联记录?', true).then(function (yes) {
            if (!yes) { return; }
            var ids = [];
            rows.forEach(function (b) { ids.push(b.getAttribute('data-undo-id')); });
            var ok = 0, fail = 0, firstErr = '';
            var chain = Promise.resolve();
            ids.forEach(function (uid) {
              chain = chain.then(function () {
                return POST('/api/personnel/undo/' + uid).then(function () { ok++; })
                  .catch(function (e) { fail++; if (!firstErr) { firstErr = e.message; } });
              });
            });
            chain.then(function () {
              toast('撤回完成:成功 ' + ok + ' 人' + (fail ? ',失败 ' + fail + ' 人' + (firstErr ? '(' + firstErr + ')' : '') : ''), fail ? 'info' : 'ok');
              load();
              refreshDepts();
              refreshPersonnelOptions();
            });
          });
        }
      }),
      h('button', { class: 'btn btn-primary', text: '关闭', onclick: function () { m.close(); } })]
  });

  function fmtTime(t) {
    return t ? String(t).replace('T', ' ').replace(/\.\d+$/, '') : '';
  }

  function load() {
    listBox.innerHTML = '<div class="empty-row">加载中…</div>';
    GET('/api/personnel/undo-list').then(function (list) {
      listBox.innerHTML = '';
      if (!list || !list.length) {
        listBox.appendChild(h('div', { class: 'empty-row', text: '暂无可撤回的删除记录' }));
        return;
      }
      var table = h('table', { class: 'grid', style: 'width:100%' });
      table.appendChild(h('thead', {}, h('tr', {},
        h('th', { text: '姓名' }), h('th', { text: '删除时间' }),
        h('th', { text: '将恢复的关联记录' }), h('th', { text: '操作', style: 'width:150px' }))));
      var tb = h('tbody');
      list.forEach(function (r) {
        tb.appendChild(h('tr', { 'data-undo-id': r.id },
          h('td', { text: r.name || ('ID ' + r.personnel_id) }),
          h('td', { text: fmtTime(r.created_at || r.createdAt) }),
          h('td', { text: r.summary || '—', style: 'max-width:340px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap' }),
          h('td', { class: 'actions', style: 'white-space:nowrap' },
            h('button', {
              class: 'btn-link', text: '撤回',
              onclick: function () {
                POST('/api/personnel/undo/' + r.id).then(function (res) {
                  var skipped = (res && res.skipped) || [];
                  var msg = '已恢复「' + ((res && res.person && res.person.name) || r.name) + '」' + (r.summary && r.summary !== '无关联记录' ? '及 ' + r.summary : '');
                  if (skipped.length) { msg += ';注意:' + skipped.join(';'); }
                  toast(msg, skipped.length ? 'info' : 'ok');
                  load();
                  refreshDepts();
                  refreshPersonnelOptions();
                }).catch(function (e) { toast(e.message, 'err'); });
              }
            }),
            h('button', {
              class: 'btn-link danger', text: '彻底删除',
              onclick: function () {
                confirmBox('彻底删除「' + (r.name || ('ID ' + r.personnel_id)) + '」的撤回快照?关联记录和照片将无法恢复。', true).then(function (yes) {
                  if (!yes) { return; }
                  POST('/api/personnel/undo-discard/' + r.id).then(function () {
                    toast('已彻底删除');
                    load();
                  }).catch(function (e) { toast(e.message, 'err'); });
                });
              }
            }))));
      });
      table.appendChild(tb);
      listBox.appendChild(h('div', { class: 'table-wrap' }, table));
    }).catch(function (e) {
      listBox.innerHTML = '';
      listBox.appendChild(h('div', { class: 'empty-row', text: e.message }));
    });
  }
  load();
}

function saveSubRecordBatch(items, parentId) {
  var failed = [], success = 0, chain = Promise.resolve();
  (items || []).forEach(function (item) {
    chain = chain.then(function () {
      var body = {};
      Object.keys(item.record).forEach(function (key) { body[key] = item.record[key]; });
      var endpoint = item.endpoint;
      if (endpoint) {
        body[item.parentKey || 'personnelId'] = parentId;
        if (typeof endpoint === 'function') { endpoint = endpoint(parentId); }
        return POST(endpoint, body).then(function () { success++; })
          .catch(function (e) { failed.push({ item: item, reason: e.message || '保存失败' }); });
      }
      body.personnelId = parentId;
      return POST('/api/sub/' + encodeURIComponent(item.kind), body).then(function () { success++; })
        .catch(function (e) { failed.push({ item: item, reason: e.message || '保存失败' }); });
    });
  });
  return chain.then(function () { return { failed: failed, success: success }; });
}

/** 驾驶证筛选使用登记字典标签；基础选项与自定义项并存。 */
function driverLicenseOptions(type) {
  var options = type === 'license_type' ? ['军本', '地方本'] : ['有效', '过期', '吊销'];
  dictLabels(type).forEach(function (label) {
    if (options.indexOf(label) < 0) options.push(label);
  });
  return options;
}

/** 只接受仪表盘人员下钻的真实筛选参数，不把下载选项混入查询。 */
function readPersonnelRouteFilters() {
  var out = {}, hash = typeof location === 'undefined' ? '' : String(location.hash || '');
  if (hash.indexOf('#personnel?') !== 0) return out;
  hash.slice(hash.indexOf('?') + 1).split('&').forEach(function (part) {
    var at = part.indexOf('='), key, value;
    if (at < 0) return;
    try { key = decodeURIComponent(part.slice(0, at)); value = decodeURIComponent(part.slice(at + 1)).trim(); } catch (e) { return; }
    if (key === 'departmentId' && /^[1-9]\d*$/.test(value)) out.departmentId = value;
    if (key === 'locationPoint' && value && value !== '未填写') out.locationPoint = value;
    if (key === 'driverLicense' && (value === 'yes' || value === 'no')) out.driverLicense = value;
    if (key === 'driverLicenseType' && driverLicenseOptions('license_type').indexOf(value) >= 0) out.driverLicenseType = value;
    if (key === 'driverLicenseStatus' && driverLicenseOptions('license_status').indexOf(value) >= 0) out.driverLicenseStatus = value;
    if (key === 'onDuty' && (value === 'yes' || value === 'no')) out.onDuty = value;
    // 概况卡"在职/休假中"跳转:仅接受人员状态字典里真实存在的值
    if (key === 'status' && value && typeof statusOptions === 'function' && statusOptions().indexOf(value) >= 0) out.status = value;
    if (key === 'actualDepartmentId' && /^[1-9]\d*$/.test(value)) out.actualDepartmentId = value;
    if (key === 'includeDescendants' && value === 'true') out.includeDescendants = value;
  });
  if (!out.departmentId) delete out.includeDescendants;
  return out;
}

function crudView(cfg) {
  var state = { page: 1, size: (SITE.pageSize || 20), total: 0, rows: [], filters: {}, loading: false, deleting: false, selected: {}, requestGeneration: 0 };
  // 汇总视图(宽行:一人一行,项目为列)独立分页与请求代际,旧响应不回填
  var summaryMode = false;
  var summary = { page: 1, size: (SITE.pageSize || 20), total: 0, rows: [], items: [], loading: false, error: '', generation: 0 };
  var summaryToggleBtn = null, summaryPanel = null, summaryTable = null, summaryPagerBox = null;
  var recordTableEl = null, recordAddBtn = null, recordBatchBtn = null;
  var selectionChanged = function () {};
  function selectedIds() {
    return state.rows.filter(function (r) { return state.selected[r.id]; }).map(function (r) { return String(r.id); });
  }
  function locked() { return state.loading || state.deleting; }
  // 未配置时保留原编辑/删除行为；配置后仅允许当前行可见的动作。
  function rowActions(row) {
    return (cfg.rowActions || [{ key: 'edit', label: '编辑' }, { key: 'del', label: '删除', danger: true }])
      .filter(function (action) { return !action.visible || action.visible(row); });
  }
  function canEdit(row) {
    return rowActions(row).some(function (action) { return action.key === 'edit'; });
  }
  function viewRow(row) {
    var onView = cfg.onView || function (rw, at) { openDetailModal(cfg, rw, at); };
    onView(row, { edit: canEdit(row) ? function () { openForm(row); } : null, refresh: afterChange });
  }

  function syncHeaderCheck() {
    var head = $('.cb-all', root);
    if (!head) return;
    var boxes = $all('.cb-row', root);
    var allOn = boxes.length > 0;
    for (var i = 0; i < boxes.length; i++) {
      if (!state.selected[boxes[i].getAttribute('data-id')]) { allOn = false; break; }
    }
    head.checked = allOn;
    head.indeterminate = !allOn && selectedIds().length > 0;
    head.disabled = locked() || !boxes.length;
    boxes.forEach(function (b) { b.disabled = locked(); });
  }
  var root = h('div', { class: 'view-in' });
  var loadingMore = false;
  var allLoaded = false;
  var sentinel = h('div', { class: 'scroll-sentinel' },
    h('div', { class: 'scroll-indicator' }));

  function collectFilters() {
    var out = {};
    $all('[data-fkey]', root).forEach(function (el) {
      out[el.getAttribute('data-fkey')] = el.value.trim();
    });
    if (!out.departmentId) delete out.includeDescendants;
    return out;
  }

  function clearSubtreeFilter() {
    if (cfg.key !== 'personnel') return;
    var flag = root.querySelector('[data-fkey="includeDescendants"]');
    if (flag) flag.value = '';
    var note = root.querySelector('.subtree-filter-note');
    if (note) note.style.display = 'none';
    // 清除下钻书签，刷新后也不能重新扩大为子树；当前其他筛选控件不变。
    if (typeof location !== 'undefined' && String(location.hash || '').indexOf('#personnel?') === 0) {
      try { history.replaceState(null, '', '#personnel'); } catch (e) { /* 仅清除页面内标志 */ }
    }
  }

  function buildQueryFrom(filters, page, size) {
    var q = {};
    // 分组视图固定筛选(如枪支管理固定类别=枪支),不受筛选面板影响
    if (cfg.fixedFilter) {
      Object.keys(cfg.fixedFilter).forEach(function (k) {
        if (cfg.fixedFilter[k]) { q[k] = cfg.fixedFilter[k]; }
      });
    }
    Object.keys(filters).forEach(function (k) {
      if (filters[k] !== '') q[k] = filters[k];
    });
    q.page = page;
    q.size = size;
    return Object.keys(q).map(function (k) { return k + '=' + encodeURIComponent(q[k]); }).join('&');
  }

  function buildQuery(append) {
    return buildQueryFrom(state.filters, append ? state.page + 1 : 1, state.size);
  }

  function load() {
    if (state.deleting) return;
    var generation = ++state.requestGeneration;
    state.filters = collectFilters();
    state.page = 1;
    state.selected = {};
    loadingMore = false;
    allLoaded = false;
    state.loading = true;
    renderTable();
    GET(cfg.endpoint + '?' + buildQuery(false)).then(function (pageData) {
      if (generation !== state.requestGeneration) return;
      state.rows = (pageData && pageData.list) || [];
      state.total = (pageData && pageData.total) || 0;
      allLoaded = state.rows.length >= state.total;
      updateIndicator();
    }).catch(function (e) {
      if (generation !== state.requestGeneration) return;
      state.rows = []; state.total = 0; allLoaded = true;
      toast(e.message, 'err');
    }).then(function () {
      if (generation !== state.requestGeneration) return;
      state.loading = false;
      renderTable();
      if (cfg.afterTable) cfg.afterTable(root);
    });
  }

  function loadMore() {
    if (summaryMode || loadingMore || allLoaded || locked()) return;
    var generation = state.requestGeneration;
    var nextPage = state.page + 1;
    loadingMore = true;
    updateIndicator('加载中…');
    GET(cfg.endpoint + '?' + buildQuery(true)).then(function (pageData) {
      if (generation !== state.requestGeneration) return;
      var list = (pageData && pageData.list) || [];
      state.page = nextPage;
      state.rows = state.rows.concat(list);
      state.total = (pageData && pageData.total) || state.total;
      if (state.rows.length >= state.total || list.length === 0) {
        allLoaded = true;
      }
      renderTable();
      updateIndicator();
    }).catch(function (e) {
      if (generation !== state.requestGeneration) return;
      toast(e.message || '加载失败，请重试', 'err');
      updateIndicator('加载失败，下拉重试');
    }).then(function () {
      if (generation === state.requestGeneration) loadingMore = false;
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

  /* ---------- 汇总视图(宽行):独立请求代际与分页,只读 ---------- */

  function reloadCurrent() {
    if (summaryMode) { loadSummary(true); return; }
    state.page = 1;
    load();
  }

  function loadSummary(reset) {
    if (!cfg.summaryView) return;
    var generation = ++summary.generation;
    if (reset) { summary.page = 1; }
    summary.loading = true;
    summary.error = '';
    renderSummary();
    GET((cfg.summaryEndpoint || (cfg.endpoint + '/summary')) + '?' + buildQueryFrom(collectFilters(), summary.page, summary.size)).then(function (data) {
      if (generation !== summary.generation) return;
      summary.rows = (data && data.list) || [];
      summary.total = (data && data.total) || 0;
      summary.items = (data && data.items) || [];
      summary.loading = false;
      renderSummary();
    }).catch(function (e) {
      if (generation !== summary.generation) return;
      summary.loading = false;
      summary.rows = [];
      summary.total = 0;
      summary.error = e.message || '加载失败，请重试';
      renderSummary();
    });
  }

  function renderSummary() {
    if (!summaryTable) return;
    var cols = summary.items.length + 3;
    var html = '<thead><tr><th>姓名</th><th>身份证号码</th><th>考核日期</th>' +
      summary.items.map(function (name) { return '<th>' + esc(name) + '</th>'; }).join('') +
      '</tr></thead><tbody>';
    if (summary.loading) {
      html += skeletonRows(cols);
    } else if (summary.error) {
      html += '<tr><td colspan="' + cols + '" class="empty-row">加载失败：' + esc(summary.error) + '</td></tr>';
    } else if (!summary.rows.length) {
      html += '<tr><td colspan="' + cols + '" class="empty-row">暂无数据</td></tr>';
    } else {
      summary.rows.forEach(function (row) {
        html += '<tr data-summary-row="1"><td>' + esc(row.personnelName || '') + '</td>' +
          '<td>' + esc(row.idCard || '—') + '</td><td>' + esc(row.testDate || '—') + '</td>';
        summary.items.forEach(function (name) {
          var cellText = summaryScoreText(row.items ? row.items[name] : null);
          html += '<td title="' + esc(cellText) + '">' + esc(cellText) + '</td>';
        });
        html += '</tr>';
      });
    }
    html += '</tbody>';
    summaryTable.innerHTML = html;
    if (summaryPagerBox) {
      summaryPagerBox.innerHTML = '';
      if (!summary.loading && !summary.error) {
        summaryPagerBox.appendChild(renderPager({ total: summary.total, page: summary.page, size: summary.size }, function (page) {
          if (summary.loading) return;
          summary.page = page;
          loadSummary(false);
        }));
      }
    }
  }

  function applySummaryMode() {
    if (!cfg.summaryView) return;
    if (summaryToggleBtn) {
      summaryToggleBtn.textContent = summaryMode ? '记录视图' : '汇总视图';
      summaryToggleBtn.setAttribute('title', summaryMode ? '返回记录视图(逐条记录,可编辑)' : '切换到汇总视图:一人一行,各考核项目为列(只读)');
    }
    if (recordTableEl) { recordTableEl.style.display = summaryMode ? 'none' : ''; }
    sentinel.style.display = summaryMode ? 'none' : '';
    if (summaryPanel) { summaryPanel.style.display = summaryMode ? '' : 'none'; }
    if (recordBatchBtn) { recordBatchBtn.style.display = summaryMode ? 'none' : ''; }
    if (recordAddBtn) { recordAddBtn.style.display = summaryMode ? 'none' : ''; }
  }

  function enterSummary() {
    if (summaryMode) return;
    summaryMode = true;
    // 废弃仍在途的记录请求,切回前不让旧响应回填记录列表
    state.requestGeneration++;
    loadingMore = false;
    applySummaryMode();
    loadSummary(true);
  }

  function exitSummary() {
    if (!summaryMode) return;
    summaryMode = false;
    summary.generation++;
    // 进入汇总时废弃的记录请求不会再回落,这里复位加载锁
    state.loading = false;
    loadingMore = false;
    applySummaryMode();
    if (JSON.stringify(collectFilters()) !== JSON.stringify(state.filters)) {
      // 汇总视图期间筛选已变化,记录列表按当前条件重查
      load();
    } else {
      renderTable();
      updateIndicator();
    }
  }

  function renderTable() {
    if (summaryMode) return;
    var tb = $('.grid tbody', root);
    if (!tb) return;
    var cols = cfg.columns.length + 2;
    selectionChanged();
    syncHeaderCheck();
    if (state.loading) {
      tb.innerHTML = skeletonRows(cols);
      return;
    }
    if (!state.rows.length) {
      tb.innerHTML = '<tr><td colspan="' + cols + '" class="empty-row">暂无数据，点击右上角「新增」开始录入</td></tr>';
      syncHeaderCheck();
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
      return '<tr data-vrow="1"><td class="cb-cell"><input type="checkbox" class="cb-row" data-id="' + row.id + '"'
        + (state.selected[row.id] ? ' checked' : '') + '></td>' + tds +
        '<td class="actions" style="width:140px;white-space:nowrap">' +
        rowActions(row).map(function (action) {
          return '<button class="btn-link' + (action.danger ? ' danger' : '') + '" data-act="' + esc(action.key) + '" data-id="' + esc(row.id) + '">' + esc(action.label) + '</button>';
        }).join('') +
        '</td></tr>';
    }).join('');
    syncHeaderCheck();
  }

  function openForm(row) {
    if (row && !canEdit(row)) return;
    var subExtra = cfg.subPanels ? cfg.subPanels(row) : null;
    var form = buildForm(cfg.form, row || {}, subExtra ? subExtra.panels : null);
    // 分组视图新增时预填固定值(如枪支管理默认类别=枪支)
    if (!row && cfg.presetNew) {
      Object.keys(cfg.presetNew).forEach(function (k) {
        if (form.inputs[k] && !form.inputs[k].value) {
          form.inputs[k].value = cfg.presetNew[k];
        }
      });
    }
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
    // 所有身份证号字段自动校验(18位+加权校验码)
    Object.keys(form.inputs).forEach(function (fk) {
      var el = form.inputs[fk];
      if (!el || !el.addEventListener) { return; }
      if (fk.toLowerCase().indexOf('idcard') < 0 && fk.toLowerCase().indexOf('id_card') < 0) { return; }
      // 借用人证件为自定义文本，不套用系统人员的身份证格式校验。
      if (cfg.key === 'item-loans' && fk === 'borrowerIdCard') { return; }
      if (el._idcardHooked) { return; }
      el._idcardHooked = true;
      function check() {
        var v = String(el.value || '').trim();
        if (!v || v.length < 18) { return; }
        if (!validateIdCard(v)) {
          el.classList.add('bad');
          el.title = '身份证校验码不正确,请核对';
          showFieldBubble(el, '身份证校验码不正确,请核对');
        } else {
          el.classList.remove('bad');
          el.title = '校验通过';
          hideFieldBubble(el);
        }
      }
      el.addEventListener('change', check);
      el.addEventListener('input', function () {
        clearTimeout(el._ict);
        el._ict = setTimeout(function () {
          if (String(el.value || '').trim().length >= 18) { check(); }
        }, 400);
      });
    });

    // 身份证反查人员(如车辆的驾驶员)
    if (cfg.personLookup) {
      setupPersonLookup(form, cfg.personLookup);
    }

    var formSnapshot = {};
    function inputSnapshot(el) { return String(el.rawValue === undefined ? el.value || '' : el.rawValue); }
    Object.keys(form.inputs).forEach(function (key) { formSnapshot[key] = inputSnapshot(form.inputs[key]); });
    var extraDirty = false;
    if (extra && extra.el) {
      extra.el.addEventListener('input', function () { extraDirty = true; });
      extra.el.addEventListener('change', function () { extraDirty = true; });
    }
    var formSaved = false;
    var createdPersonId = null;
    var parentSaved = false;
    var retrySubRecords = null;
    var savingForm = false;
    var m = openModal({
      title: (row ? '编辑 · ' : '新增 · ') + cfg.title + (row && cfg.rowTitle ? ' - ' + cfg.rowTitle(row) : ''),
      body: body,
      wide: cfg.wide,
      beforeClose: function () {
        if (savingForm) { form.setError('正在保存，请稍候'); return false; }
        if (formSaved) return true;
        if (parentSaved && retrySubRecords && retrySubRecords.length) return confirm('主档已保存，仍有子记录未保存。关闭将放弃这些失败项，确定关闭吗？');
        var changed = extraDirty || Object.keys(form.inputs).some(function (key) {
          return inputSnapshot(form.inputs[key]) !== formSnapshot[key];
        });
        if (!changed && subExtra && subExtra.staged) {
          changed = Object.keys(subExtra.staged).some(function (kind) { return (subExtra.staged[kind]() || []).length > 0; });
        }
        return !changed || confirm('表单有未保存的修改，确定放弃并关闭吗？');
      },
      footer: [
        h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m.close(); } }),
        h('button', {
          class: 'btn btn-primary', text: '保存',
          onclick: function () {
            if (savingForm) return;
            if (parentSaved && !createdPersonId) { form.setError('主档已保存但未返回ID，请关闭后刷新核实，不要重复创建'); return; }
            var data = parentSaved ? {} : form.get();
            if (!data || (!parentSaved && form.validatePerson && !form.validatePerson())) return;
            if (!parentSaved && subExtra && subExtra.panels) {
              var invalidPanel = Object.keys(subExtra.panels).some(function (key) {
                var panel = subExtra.panels[key];
                if (panel.__validate && !panel.__validate()) { form.setError('请完善或修正「' + key + '」中的记录后保存'); return true; }
                return false;
              });
              if (invalidPanel) return;
            }
            // 保存前转换钩子(剔除仅用于展示的虚拟字段等)
            if (!parentSaved && cfg.beforeSave) {
              var transformed = cfg.beforeSave(data, row);
              if (transformed === null || transformed === false) return;
              if (transformed !== undefined) data = transformed;
            }
            if (!parentSaved && extra && extra.get) {
              var ex = extra.get();
              if (!ex) return;
              Object.keys(ex).forEach(function (k) { if (ex[k] !== undefined) data[k] = ex[k]; });
            }
            savingForm = true;
            var lockedInputs = $all('input, button, select, textarea', body).map(function (el) {
              var entry = { el: el, disabled: el.disabled };
              el.disabled = true;
              return entry;
            });
            body.style.pointerEvents = 'none';
            var saveButton = m.dialog.querySelector('.dialog-foot .btn-primary');
            if (saveButton) saveButton.disabled = true;
            var parentRequest = createdPersonId ? Promise.resolve({ id: createdPersonId })
              : (row ? PUT(cfg.endpoint + '/' + row.id, data) : POST(cfg.endpoint, data));
            parentRequest.then(function (saved) {
              if (saved && saved.id) createdPersonId = saved.id;
              parentSaved = true;
              // 主档已落库后禁止再编辑本次快照，重试只能发送失败子项。
              body.style.pointerEvents = 'none';
              $all('input, button, select, textarea', body).forEach(function (el) { el.disabled = true; });
              if (!row && subExtra && subExtra.staged && !createdPersonId) throw new Error('主档已保存但未返回ID，请关闭后刷新核实，不要重复创建');
              if (!row && subExtra && subExtra.staged && createdPersonId) {
                if (!retrySubRecords) {
                  retrySubRecords = [];
                  Object.keys(subExtra.staged).forEach(function (kind) {
                    (subExtra.staged[kind]() || []).forEach(function (rec) {
                      var meta = subExtra.stagedMeta && subExtra.stagedMeta[kind];
                      retrySubRecords.push({ kind: kind, endpoint: meta && meta.endpoint, parentKey: meta && meta.parentKey, record: rec });
                    });
                  });
                }
              }
              return saveSubRecordBatch(retrySubRecords, createdPersonId).then(function (result) {
                var failed = result.failed, subCount = result.success;
                if (failed.length) {
                  retrySubRecords = failed.map(function (entry) { return entry.item; });
                  form.setError(cfg.title + '主档已保存，本次子记录成功 ' + subCount + ' 条、失败 ' + failed.length + ' 条；表单已锁定，保留失败项，点击重试仅重发失败项。' +
                    failed.map(function (entry) { return entry.item.kind + ': ' + entry.reason; }).join('；'));
                  if (saveButton) saveButton.textContent = '重试子记录';
                  afterChange();
                  return;
                }
                formSaved = true;
                m.close(true);
                toast(subCount ? '保存成功(含 ' + subCount + ' 条子记录)' : '保存成功');
                afterChange();
              });
            }).catch(function (e) { form.setError(e.message); }).then(function () {
              savingForm = false;
              if (!parentSaved) {
                body.style.pointerEvents = '';
                lockedInputs.forEach(function (entry) { entry.el.disabled = entry.disabled; });
              }
              if (saveButton) saveButton.disabled = false;
            });
          }
        })
      ]
    });
  }

  function afterChange() {
    if (summaryMode) { loadSummary(true); } else { load(); }
    refreshDepts();
    refreshPersonnelOptions();
  }

  function render(container) {
    var filterBtn = null;
    // 工具栏(只放操作按钮,不放筛选)
    var toolbar = h('div', { class: 'toolbar' });
    if (cfg.summaryView) {
      // 记录/汇总视图切换:汇总为宽行只读表,记录视图恢复原列表
      summaryToggleBtn = h('button', {
        class: 'btn btn-ghost', text: '汇总视图',
        title: '切换到汇总视图:一人一行,各考核项目为列(只读)',
        onclick: function () { if (summaryMode) { exitSummary(); } else { enterSummary(); } }
      });
      toolbar.appendChild(summaryToggleBtn);
    }

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
        el.addEventListener('change', function () {
          if (f.key === 'departmentId') clearSubtreeFilter();
          reloadCurrent();
        });
      } else if (f.type === 'date') {
        el = makeDateField();
        el.setAttribute('data-fkey', f.key);
        el.addEventListener('change', function () { reloadCurrent(); });
      } else {
        el = h('input', { type: 'text', 'data-fkey': f.key, placeholder: f.placeholder || '回车搜索' });
        el.addEventListener('keydown', function (e) {
          if (e.key === 'Enter') { reloadCurrent(); }
        });
      }
      if (cfg.key === 'item-loans') {
        var filterInput = f.type === 'select' ? el.querySelector('.dd-btn') : f.type === 'date' ? el.querySelector('input') : el;
        if (filterInput) filterInput.setAttribute('aria-label', f.label);
      }
      if (f.key === 'departmentId') el.classList.add('personnel-path-dropdown');
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
          onclick: function () { reloadCurrent(); }
        }),
        h('button', {
          class: 'btn btn-ghost', text: '重置',
          onclick: function () {
            $all('[data-fkey]', filterPanel).forEach(function (el) {
              el.value = '';
            });
            clearSubtreeFilter();
            reloadCurrent();
          }
        })));
      if (cfg.key === 'personnel') {
        var routeFilters = readPersonnelRouteFilters();
        Object.keys(routeFilters).forEach(function (key) {
          if (key === 'includeDescendants') return;
          var input = inner.querySelector('[data-fkey="' + key + '"]');
          // 权限树外单位不添加为可选节点；只在当前人员视图传递后端校验的筛选ID。
          if (input) input.value = routeFilters[key];
        });
        var subtree = h('input', { type: 'hidden', 'data-fkey': 'includeDescendants' });
        subtree.value = routeFilters.includeDescendants || '';
        inner.appendChild(subtree);
        inner.appendChild(h('div', { class: 'subtree-filter-note', style: subtree.value ? '' : 'display:none',
          text: '当前单位筛选包含全部可见下级；手动更换单位后恢复仅直属。' }));
      }
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
            filterPanel.style.transition = 'transform ' + MOTION.panel + 'ms ' + MOTION.glide;
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
    recordBatchBtn = batchBtn;
    function syncBatchBtn() {
      var n = selectedIds().length;
      batchBtn.disabled = locked() || n === 0;
      batchBtn.textContent = state.deleting ? '删除处理中…' : (n ? '删除所选(' + n + ')' : '删除所选');
    }
    selectionChanged = syncBatchBtn;
    function batchDelete() {
      if (locked()) return;
      var ids = selectedIds();
      if (!ids.length) return;
      state.deleting = true;
      syncBatchBtn(); syncHeaderCheck();
      confirmBox('确定删除选中的 ' + ids.length + ' 条记录吗?删除后不可恢复。', true).then(function (yes) {
        if (!yes) return false;
        // 删除开始即废弃仍在途的追加分页结果。
        state.requestGeneration++;
        var okCount = 0, failCount = 0, firstError = '';
        var chain = Promise.resolve();
        ids.forEach(function (id) {
          chain = chain.then(function () {
            return DEL(cfg.endpoint + '/' + encodeURIComponent(id))
              .then(function () { okCount++; })
              .catch(function (e) { failCount++; if (!firstError) firstError = e.message || '请求失败'; });
          });
        });
        return chain.then(function () {
          state.selected = {};
          toast('批量删除完成:成功 ' + okCount + ' 条' + (failCount ? ',失败 ' + failCount + ' 条。' + firstError : ''), failCount ? 'info' : 'ok');
          return true;
        });
      }).then(function (reload) {
        state.deleting = false;
        if (reload) afterChange();
        syncBatchBtn(); syncHeaderCheck();
      }).catch(function (e) {
        state.deleting = false;
        syncBatchBtn(); syncHeaderCheck();
        toast(e.message || '请求失败', 'err');
      });
    }
    // 不支持 Excel 的模块不构建模板、导入、导出入口。
    if (cfg.excel !== false) {
    // 导入:按模块规则追加记录或匹配更新
    var importInput = h('input', { type: 'file', accept: '.xlsx', style: 'display:none' });
    importInput.addEventListener('change', function () {
      var f = importInput.files[0];
      importInput.value = '';
      if (!f) return;
      var fd = new FormData();
      fd.append('file', f);
      var pm = showProgressModal('导入 Excel');
      pm.setStatus('正在上传 ' + f.name + '(' + (f.size / 1024).toFixed(1) + 'KB)…');
      postFormWithProgress((cfg.excelEndpoint || cfg.endpoint) + '/import-excel', fd,
        function (pct) {
          pm.setProgress(pct * 0.5); // 上传占前半
          pm.setStatus('正在上传… ' + pct + '%');
        }
      ).then(function (r) {
        pm.setProgress(80);
        pm.setStatus('正在处理数据…');
        r = r || {};
        var isPersonnelImport = cfg.key === 'personnel';
        var msg = importResultSummary(r, isPersonnelImport);
        pm.setProgress(100);
        pm.setStatus(r.error ? '导入失败' : '处理完成');
        pm.close();
        var fileError = r.error || '';
        var errors = (r.errors || []).slice();
        if (fileError) {
          openImportResultModal(msg, [], fileError);
        } else if (errors.length) {
          openImportResultModal(msg, errors);
        } else {
          toast(msg, r.error ? 'err' : ((r.failed || r.subFailed) ? 'info' : 'ok'));
        }
        afterChange();
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
          apiBlobWithProgress((cfg.excelEndpoint || cfg.endpoint) + '/template', function (pct) {
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
    // 导入导出合并按钮:下拉菜单选择操作
    var doExport = function () {
      if (locked()) return;
      if (summaryMode) {
        toast('汇总视图为只读视图，请切回记录视图勾选记录后导出', 'info');
        return;
      }
      var ids = selectedIds();
      if (!ids.length) {
        toast('请先勾选要导出的数据(可点表格左上角全选本页)', 'err');
        return;
      }
      var exportUrl = (cfg.excelEndpoint || cfg.endpoint) + '/export?ids=' + encodeURIComponent(ids.join(','));
      // 如体能考核宽行导出等模块级附加参数
      if (cfg.exportParams) { exportUrl += '&' + cfg.exportParams; }
      var pm = showProgressModal('导出 Excel');
      pm.setStatus('正在导出 ' + ids.length + ' 条数据…');
      apiBlobWithProgress(exportUrl,
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
    };
    var ioMenu = h('div', { class: 'dd-list io-menu' },
      h('div', {
        class: 'dd-item', text: '导入 Excel',
        title: importRuleText(cfg.key),
        onclick: function () {
          ioWrap.classList.remove('open');
          confirmBox(importRuleText(cfg.key) + '\n\n确定要继续选择文件导入吗？').then(function (yes) {
            if (yes) importInput.click();
          });
        }
      }),
      h('div', {
        class: 'dd-item', text: '导出 Excel',
        title: '导出勾选的记录' + (cfg.key === 'personnel' ? '(含任职经历/奖惩等子表)' : ''),
        onclick: function () { ioWrap.classList.remove('open'); doExport(); }
      }));
    var ioBtn = h('button', {
      type: 'button', class: 'btn btn-ghost dd-btn io-btn',
      title: '导入 / 导出 Excel'
    }, h('span', { class: 'txt', text: '导入导出' }), h('span', { class: 'dd-arrow', text: '▾' }));
    var ioWrap = h('div', { class: 'dd io-dd' }, ioBtn, ioMenu);
    ioBtn.addEventListener('click', function (e) {
      e.stopPropagation();
      var opening = !ioWrap.classList.contains('open');
      $all('.dd.open, .cal.open').forEach(function (d) { d.classList.remove('open'); });
      ioWrap.classList.toggle('open', opening);
    });
    ioMenu.addEventListener('click', function (e) { e.stopPropagation(); });
    ioMenu.addEventListener('mousedown', function (e) { e.preventDefault(); });
    toolbar.appendChild(ioWrap);
    }
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
    var addBtn = h('button', {
      class: 'btn btn-primary',
      text: cfg.addLabel || '新增' + cfg.title.replace(/管理$/, ''),
      onclick: function () { openForm(null); }
    });
    toolbar.appendChild(addBtn);
    recordAddBtn = addBtn;
    // 筛选按钮已改为固定在右侧边缘的垂直标签(document.body 上),不在工具栏

    var table = h('table', { class: 'grid' });
    table.innerHTML = '<thead><tr>' +
      '<th class="cb-cell"><input type="checkbox" class="cb-all" title="全选本页"/></th>' +
      cfg.columns.map(function (c) { return '<th>' + esc(c.label) + '</th>'; }).join('') +
      '<th class="action-col" style="width:110px">操作</th></tr></thead><tbody></tbody>';
    table.addEventListener('change', function (e) {
      var t = e.target;
      if (locked()) {
        $all('.cb-row', table).forEach(function (b) { b.checked = !!state.selected[b.getAttribute('data-id')]; });
        syncHeaderCheck();
        return;
      }
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
        syncHeaderCheck();
      }
    });
    table.addEventListener('click', function (e) {
      if (locked()) return;
      var viewEl = e.target.closest('[data-view-id]');
      if (viewEl) {
        var vid = viewEl.getAttribute('data-view-id');
        var vrow = state.rows.filter(function (r) { return String(r.id) === String(vid); })[0];
        if (vrow) viewRow(vrow);
        return;
      }
      var btnEl = e.target.closest('button[data-act]');
      if (!btnEl) return;
      var id = btnEl.getAttribute('data-id');
      var row = state.rows.filter(function (r) { return String(r.id) === String(id); })[0];
      if (!row) return;
      var actionKey = btnEl.getAttribute('data-act');
      var action = rowActions(row).filter(function (entry) { return entry.key === actionKey; })[0];
      if (!action) return; // 未知/已隐藏的动作绝不能回落到删除。
      if (action.onClick) {
        action.onClick(row, { refresh: afterChange, view: function () { viewRow(row); } });
      } else if (actionKey === 'view') {
        viewRow(row);
      } else if (actionKey === 'edit') {
        openForm(row);
      } else if (actionKey === 'del') {
        var trEl = btnEl.closest('tr'); // 行删除塌陷动画目标(滑出淡出后再刷新整表)
        state.deleting = true;
        syncBatchBtn(); syncHeaderCheck();
        confirmBox(cfg.delTip ? cfg.delTip(row) : '确定删除这条记录吗?删除后不可恢复。', true)
          .then(function (yes) {
            if (!yes) return false;
            state.requestGeneration++;
            return DEL(cfg.endpoint + '/' + encodeURIComponent(id)).then(function () { toast('已删除'); return true; });
          }).then(function (reload) {
            state.deleting = false;
            if (reload) {
              // 行塌陷:被删行右滑淡出后整表刷新(其余行自然上移补位);
              // 减动效或无 rAF 的单测沙箱直接刷新(与 mock 定时器时序契约一致)
              if (trEl && document.body.contains(trEl) && !prefersReducedMotion()
                  && typeof requestAnimationFrame === 'function') {
                trEl.classList.add('row-removing');
                setTimeout(afterChange, 240);
              } else {
                afterChange();
              }
            }
            syncBatchBtn(); syncHeaderCheck();
          }).catch(function (err) {
            state.deleting = false;
            syncBatchBtn(); syncHeaderCheck();
            toast(err.message || '请求失败', 'err');
          });
      }
    });

    recordTableEl = table;
    var tableWrap = h('div', { class: 'table-wrap' }, table);
    var panel = h('div', { class: 'panel' }, tableWrap, sentinel);
    if (cfg.summaryView) {
      // 汇总视图(宽行只读):项目列来自 summary.items,单元格为 成绩（分数/等级）
      summaryTable = h('table', { class: 'grid fitness-summary-grid' });
      summaryTable.addEventListener('click', function (e) {
        var rowEl = e.target && e.target.closest ? e.target.closest('tr[data-summary-row]') : null;
        if (rowEl) { toast('汇总视图为只读视图，编辑记录请切回记录视图', 'info'); }
      });
      summaryPagerBox = h('div');
      summaryPanel = h('div', { class: 'fitness-summary-panel', style: 'display:none' },
        h('div', {
          style: 'padding:8px 12px 0;font-size:13px;color:#64748b',
          text: '汇总视图为只读视图：一人一行、各考核项目为列，空白表示未参加；编辑或导出勾选记录请切回记录视图。'
        }),
        h('div', { class: 'table-wrap' }, summaryTable),
        h('div', { style: 'padding:8px 12px' }, summaryPagerBox));
      panel.appendChild(summaryPanel);
    }
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
  var SUB_KINDS = {
    '任职经历': POSITION_SUB, '奖惩情况': REWARD_SUB, '家庭信息': FAMILY_SUB,
    '驾驶证': DRIVER_SUB, '个人卡号': CARD_SUB, '私家车': PVEHICLE_SUB, '住房情况': HOUSING_SUB
  };
  (cfg.form || []).forEach(function (f) {
    if (f.section) {
      grid.appendChild(h('div', { class: 'form-section', text: f.section }));
      if (SUB_KINDS[f.section] && row.id) {
        var holder = h('div', { style: 'grid-column:1/-1' });
        grid.appendChild(holder);
        (function (def) {
          GET('/api/sub/' + def.kind + '?personnelId=' + row.id).then(function (list) {
            (list || []).forEach(function (r) {
              var values = def.columns.filter(function (c) { return !c.virtual; }).map(function (c) {
                var v = r[c.key];
                return c.label + ':' + (v === null || v === undefined || v === '' ? '—' : String(v));
              });
              holder.appendChild(h('div', { class: 'detail-sub-line', text: values.join('　') }));
            });
            if (!list || !list.length) {
              holder.appendChild(h('div', { class: 'detail-sub-line', text: '暂无记录' }));
            }
          }).catch(function (err) {
            holder.appendChild(h('div', { class: 'detail-sub-line', text: '加载失败:' + err.message }));
          });
        })(SUB_KINDS[f.section]);
      }
      return;
    }
    if (f.type === 'hidden' || !f.label) return;
    var v = row[f.key];
    if (f.key === 'militaryIdNo') v = String(v || '').trim();
    if (f.type === 'dept') {
      if (f.key === 'actualDepartmentId') {
        // 实际单位:后端另带 actualDepartmentPath(完整路径);0/空 表示在编在岗,显示为空
        v = Number(v) > 0
          ? (row.actualDepartmentPath || row.actualDepartmentName ||
            (Ctx.deptFlat.filter(function (d) { return String(d.id) === String(v); })[0] || {}).path ||
            (Ctx.deptFlat.filter(function (d) { return String(d.id) === String(v); })[0] || {}).name || v)
          : '';
      } else {
        v = row.departmentPath || row.departmentName || (Ctx.deptFlat.filter(function (d) { return String(d.id) === String(v); })[0] || {}).path || (Ctx.deptFlat.filter(function (d) { return String(d.id) === String(v); })[0] || {}).name || v;
      }
      v = v == null ? v : String(v).replace(/\s*\/\s*/g, '');
    }
    grid.appendChild(h('div', { class: 'detail-item' },
      h('div', { class: 'dl', text: f.label }),
      h('div', {
        class: 'dv' + (f.type === 'dept' ? ' personnel-department-path' : ''), title: v == null ? '' : String(v),
        text: (v === null || v === undefined || v === '') ? (f.key === 'militaryIdNo' ? '未填写' : '—') : String(v)
      })));
    // 人员详情:入伍日期旁自动展示军龄(优先按相当入伍时间折算,只读计算不进编辑表单)
    if (cfg.key === 'personnel' && f.key === 'entryDate') {
      var svc = calcServiceYears(row.equivEntryDate || row.entryDate || '');
      grid.appendChild(h('div', { class: 'detail-item' },
        h('div', { class: 'dl', text: '军龄' }),
        h('div', { class: 'dv', text: svc ? svc + ' 年' : '—' })));
    }
  });

  var body;
  if (cfg.key === 'personnel') {
    var photo = h('div', { class: 'detail-photo', text: '无照片' });
    if (row.photo) {
      apiBlob('/api/personnel/' + row.id + '/photo').then(function (r) {
        bindBlobUrl(photo, r.blob, true);
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
  var parentId = opt.parentId !== undefined ? opt.parentId : personnelId;
  var localMode = !parentId; // 新建主记录:全部暂存,随主记录一并入库
  var serverMode = !localMode;
  function endpoint(id) {
    return opt.endpoint ? opt.endpoint(id) : '/api/sub/' + kind;
  }
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
    var url = opt.endpoint ? endpoint(parentId) : '/api/sub/' + kind + '?personnelId=' + parentId;
    GET(url).then(function (list) {
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
      editor.addEventListener('change', function () { commitCellEdit(r, c, editor.rawValue === undefined ? editor.value : editor.rawValue); });
    } else if (c.type === 'select') {
      var ddOpts = typeof c.options === 'function' ? c.options() : (c.options || []);
      editor = makeDropdown(ddOpts, '请选择');
      if (r[c.key]) {
        editor.value = r[c.key];
      }
      editor.addEventListener('change', function () { commitCellEdit(r, c, editor.value); });
      var btn = editor.querySelector('.dd-btn');
      if (btn) {
        btn.addEventListener('blur', function () {
          setTimeout(function () {
            // 下拉展开期间的失焦是打开动作的一部分(焦点移入列表),不是离开单元格,不应提交;提交会因值未变触发空行自动移除,表现为"卡类型刚点开就消失"
            var holder = btn.closest && btn.closest('.dd');
            if (holder && holder.classList.contains('open')) { return; }
            commitCellEdit(r, c, r[c.key]);
          }, 150);
        });
      }
    } else {
      editor = h('input', { type: 'text', style: 'width:100%' });
      editor.value = r[c.key] || '';
      editor.addEventListener('blur', function () { commitCellEdit(r, c, editor.value); });
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

  function commitCellEdit(r, c, value) {
    value = String(value == null ? '' : value).trim();
    if (c.type === 'date' && value) {
      var day = parseCalendarDay(value);
      if (!day) { toast('「' + c.label + '」不是有效日期，请修正后保存', 'err'); return; }
      value = formatCalendarDay(day);
    }
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
      if (opt.endpoint) { body[opt.parentKey || 'personnelId'] = parentId; }
      else { body.personnelId = parentId; }
      var createUrl = endpoint(parentId);
      POST(createUrl, body).then(function (saved) {
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
      PUT(endpoint(parentId) + '/' + r.id, body).then(function () {
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
      DEL(endpoint(parentId) + '/' + r.id).then(function () {
        rows = rows.filter(function (x) { return x !== r; });
        render();
        toast('已删除');
      }).catch(function (err) { toast(err.message, 'err'); });
    });
  });

  box.appendChild(h('div', { style: 'margin-bottom:12px;display:flex;align-items:center;gap:12px' },
    h('button', {
      class: 'btn btn-ghost btn-sm', text: '添加' + opt.name,
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

  box.__validate = function () {
    var badEditor = $all('.cal', box).some(function (el) {
      var raw = String(el.rawValue === undefined ? el.value || '' : el.rawValue).trim();
      return raw && !parseCalendarDay(raw);
    });
    return !badEditor && rows.every(function (r) {
      return rowValid(r) && opt.columns.every(function (c) {
        return c.type !== 'date' || !r[c.key] || !!parseCalendarDay(r[c.key]);
      });
    });
  };
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
  var status = h('div', { style: 'margin:12px 0 4px;font-size:13px;color:var(--c-sub, #66788c)', text: '正在读取人员列表…' });
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

  loadAllRows('/api/personnel').then(function (pageData) {
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
        if (!/\.(jpe?g|png)$/i.test(f.name) || (f.type && !/^image\/(jpeg|png)$/i.test(f.type))) {
          failCount++;
          failed.push(f.name + '(仅支持 JPG 或 PNG)');
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

var TREND_COLORS = ['var(--pri, #2f6fd6)', '#e8743b', 'var(--c-ok, #2ea44f)', '#9333ea', 'var(--c-danger, #d9463e)', '#0d9488', '#b26a00'];

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
  var q = '/api/fitness?personnelId=' + encodeURIComponent(rw.personnelId);
  if (rw.testDate) { q += '&startDate=' + encodeURIComponent(rw.testDate) + '&endDate=' + encodeURIComponent(rw.testDate); }
  loadAllRows(q).then(function (d) {
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
    body.appendChild(h('div', { style: 'margin-top:12px;display:flex;gap:16px;font-size:13px;color:var(--c-body, #445262)' },
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
  var fq = '/api/fitness?personnelId=' + encodeURIComponent(rw.personnelId);
  var pq = GET('/api/personnel/' + encodeURIComponent(rw.personnelId));
  Promise.all([pq, loadAllRows(fq), GRADE_RULE ? Promise.resolve(null) : loadGradeRule(), BMI_STD ? Promise.resolve(null) : loadBmiStd()]).then(function (res) {
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
        h('div', { style: 'font-size:12px;color:var(--c-muted, #8a97a8)', text: label }),
        h('div', { style: 'font-size:14px;font-weight:600;color:var(--c-dark, #1d2c40);word-break:break-all', text: (value === null || value === undefined || value === '') ? '—' : String(value) }));
    }
    var ageTxt = '';
    var profileAge = null;
    var am = /^(\d{4})-(\d{2})-(\d{2})/.exec(birthday || '');
    if (am) {
      var t = new Date();
      var age = t.getFullYear() - (+am[1]) - ((t.getMonth() + 1 < +am[2] || (t.getMonth() + 1 === +am[2] && t.getDate() < +am[3])) ? 1 : 0);
      if (age >= 0 && age < 130) { ageTxt = age + ' 岁'; profileAge = age; }
    }
    var svcYears = calcServiceYears((p.equivEntryDate || p.entryDate) || '');
    var svcTxt = svcYears ? svcYears + ' 年' : '';
    var info = h('div', { style: 'flex:1;display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px 18px;align-content:start' },
      cell('姓名', p.name || rw.personnelName), cell('身份证号码', idc), cell('出生日期', birthday),
      cell('年龄', ageTxt), cell('入伍时间', p.entryDate), cell('军龄', svcTxt),
      cell('人员类别', p.category),
      cell('军衔', p.rank), cell('职务', p.post), cell('所属单位', p.departmentPath || p.departmentName));
    body.appendChild(h('div', { style: 'display:flex;gap:16px;align-items:stretch' },
      h('div', { style: 'flex:none;width:300px;border:1px solid #edf1f6;border-radius:10px;padding:12px 10px;display:flex;justify-content:center' },
        buildBodyFigure(p.height, p.weight, p.gender, profileAge)),
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
    var bar = h('div', { style: 'display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin:8px 0 4px' },
      h('span', { style: 'font-size:13px;color:var(--c-sub, #66788c)', text: '时间范围:' }));
    chipDefs.forEach(function (cd) {
      var b = h('button', {
        type: 'button', text: cd[0],
        style: 'height:26px;padding:0 12px;border:1px solid #c5cdd8;border-radius:13px;background:#fff;color:var(--c-body, #445262);font-size:12px;cursor:pointer',
        onclick: function () { setRange(cd[1], b); }
      });
      chips.push(b);
      bar.appendChild(b);
    });
    bar.appendChild(h('span', { style: 'font-size:12px;color:var(--c-muted, #8a97a8);margin-left:8px', text: '自定义:最近' }));
    bar.appendChild(custom);
    bar.appendChild(h('span', { style: 'font-size:12px;color:var(--c-muted, #8a97a8)', text: '个月' }));
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
    body.appendChild(h('div', { style: 'margin-top:16px;font-size:15px;font-weight:600;color:var(--c-dark, #1d2c40)', text: '考核成绩与走势' }));
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
      h('div', { style: 'font-size:15px;font-weight:600;color:var(--c-dark, #1d2c40)', text: '全部项目总览' }),
      h('div', { style: 'font-size:12px;color:var(--c-muted, #8a97a8)', text: '悬停圆点可查看明细' }),
      buildTrendChart(ser.map(chartData), { H: 300 })));
    // 每个项目一张小图 + 统计
    ser.forEach(function (s) {
      var pts = s.data.map(function (x) { return x.pts; });
      var sum = 0; pts.forEach(function (v) { sum += v; });
      var last = s.data[s.data.length - 1];
      chartsBox.appendChild(chartCard(
        h('div', { style: 'font-size:14px;font-weight:600;color:var(--c-dark, #1d2c40)', text: s.name }),
        h('div', { style: 'display:flex;gap:16px;align-items:center;font-size:12.5px;color:var(--c-body, #445262);flex-wrap:wrap' },
          h('span', { text: s.data.length + ' 次' }),
          h('span', { text: '最新 ' + last.date + ':' }),
          h('b', { style: 'color:var(--c-dark, #1d2c40)', text: last.pts + ' 分' }),
          h('span', { html: badge(last.grade || gradeForPoints(last.pts)) }),
          h('span', { text: '成绩 ' + (last.raw || '—') }),
          h('span', { text: '平均 ' + r1(sum / pts.length) + ' 分' }),
          h('span', { text: '最好 ' + r1(Math.max.apply(null, pts)) + ' 分' })),
        buildTrendChart([chartData(s)], { H: 190, legend: false })));
    });
  }
}

/** 伪3D人体模型:身高定高度、BMI定体宽,渐变模拟立体感;无数据给占位提示 */
function buildBodyFigure(hcm, wkg, gender, age) {
  var hc = Number(hcm), wc = Number(wkg);
  var has = hc > 80 && hc < 230 && wc > 25 && wc < 200;
  var box = h('div', { style: 'display:flex;flex-direction:column;align-items:center;width:100%' });
  if (!has) {
    box.appendChild(h('div', { style: 'width:230px;height:296px;border:2px dashed #d5dce6;border-radius:12px;display:flex;align-items:center;justify-content:center;text-align:center;padding:24px;color:var(--c-muted, #8a97a8);font-size:13px;line-height:1.8', text: '暂无身高体重数据,请在人员管理中填写' }));
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
      s.push('<text x="32" y="' + (y + 3).toFixed(1) + '" text-anchor="end" font-size="9" fill="var(--c-muted, #8a97a8)">' + cm + '</text>');
    }
  }
  s.push('<line x1="40" y1="' + topY + '" x2="' + (cx - headR).toFixed(1) + '" y2="' + topY + '" stroke="'+PRI()+'" stroke-dasharray="4 3" stroke-width="1"/>');
  // 地面影子
  s.push('<ellipse cx="' + cx + '" cy="' + (groundY + 5) + '" rx="' + (figH * 0.155).toFixed(1) + '" ry="7" fill="var(--c-dark, #1d2c40)" opacity="0.10"/>');
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
  // 体重标准判定(按性别+身高行+年龄列,可在体能标准-体重标准中配置)
  var v = weightVerdict(wc, hc, gender, age);
  box.appendChild(h('div', { style: 'width:100%', html: s.join('') }));
  box.appendChild(h('div', { style: 'margin-top:8px;font-size:13px;color:var(--c-body, #445262);text-align:center' },
    h('span', { text: '身高 ' + (+hc) + ' cm · 体重 ' + (+wc) + ' kg · BMI ' + bmiV + ' ' }),
    h('b', { style: 'color:' + v[2], text: v[1] })));
  box.appendChild(h('div', { style: 'margin-top:4px;font-size:11.5px;color:var(--c-muted, #8a97a8);text-align:center',
    text: v[3] ? ('体重合格区间(' + v[3].split(' ').slice(1).join(' ') + ')') : (gender === '女' ? '女' : '男') + '性暂无该身高标准' }));
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
    svg.push('<text x="' + (padL - 8) + '" y="' + (+y + 4) + '" text-anchor="end" font-size="11" fill="var(--c-muted, #8a97a8)">' + v + '</text>');
  });
  var step = Math.max(1, Math.ceil(n / 12));
  dates.forEach(function (d, i) {
    if (i % step !== 0 && i !== n - 1) { return; }
    var x = xAt(i).toFixed(1);
    svg.push('<line x1="' + x + '" y1="' + padT + '" x2="' + x + '" y2="' + (H - padB) + '" stroke="#f5f7fa" stroke-width="1"/>');
    svg.push('<text x="' + x + '" y="' + (H - padB + 18) + '" text-anchor="middle" font-size="10" fill="var(--c-muted, #8a97a8)">' + esc(d.slice(2)) + '</text>');
  });
  svg.push('<text x="' + (padL - 30) + '" y="' + (padT + 4) + '" font-size="11" fill="var(--c-muted, #8a97a8)">分</text>');
  series.forEach(function (s, si) {
    var color = TREND_COLORS[si % TREND_COLORS.length];
    var pts = s.data.map(function (p) { return xAt(xi[p.date]).toFixed(1) + ',' + yAt(p.score).toFixed(1); });
    svg.push('<polyline points="' + pts.join(' ') + '" fill="none" stroke="' + color + '" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>');
    s.data.forEach(function (p) {
      svg.push('<circle cx="' + xAt(xi[p.date]).toFixed(1) + '" cy="' + yAt(p.score).toFixed(1) + '" r="3.5" fill="' + color + '"><title>' + esc(s.name) + ' ' + esc(p.date) + ' : ' + esc(p.score) + '分</title></circle>');
    });
  });
  svg.push('</svg>');
  var chartBox = h('div', { style: 'max-height:54vh;overflow:auto;border:1px solid #edf1f6;border-radius:8px;padding:12px', html: svg.join('') });
  if (opts.legend === false) { return chartBox; }
  var legend = h('div', { style: 'display:flex;gap:16px;flex-wrap:wrap;margin-top:12px;font-size:13px;color:var(--c-body, #445262)' });
  series.forEach(function (s, si) {
    var color = TREND_COLORS[si % TREND_COLORS.length];
    legend.appendChild(h('span', { style: 'display:flex;align-items:center;gap:4px' },
      h('span', { style: 'width:16px;height:3px;background:' + color + ';display:inline-block;border-radius:2px' }),
      h('span', { text: s.name + '(' + s.data.length + '次)' })));
  });
  return h('div', {}, chartBox, legend);
}

var BMI_STD = null;   // {ranges:[{gender,ageMin,ageMax,min,max},...]}
function defaultBmiStd() {
  return normWeightStd({ male: [17.5, 27.5], female: [17, 24.5] });
}
/** 归一化:兼容旧 {male:[..],female:[..]} → ranges */
function normWeightStd(v) {
  if (v && v.ages && v.male && v.male.rows) {
    return { ages: v.ages, male: v.male, female: v.female || { rows: [] } };
  }
  // 旧BMI区间 → 生成 1.50~2.00m 等值体重区间表
  var mk = function (lo, hi) {
    var rows = [];
    for (var h = 150; h <= 200; h++) {
      var m2 = h / 100;
      rows.push({ h: m2, w: ['0-24', '25-29', '30-39', '40-49', '50-59', '60-99'].map(function () {
        return [Math.round(m2 * m2 * lo * 10) / 10, Math.round(m2 * m2 * hi * 10) / 10];
      }) });
    }
    return { rows: rows };
  };
  var m = (v && v.male) ? v.male : [17.5, 27.5];
  var f = (v && v.female) ? v.female : [17, 24.5];
  return { ages: ['0-24', '25-29', '30-39', '40-49', '50-59', '60-99'], male: mk(m[0], m[1]), female: mk(f[0], f[1]) };
}
function loadBmiStd() {
  return GET('/api/setting/bmi_standard').then(function (r) {
    BMI_STD = normWeightStd(r);
    return BMI_STD;
  }).catch(function () { BMI_STD = defaultBmiStd(); return BMI_STD; });
}
/** 身高(米)匹配标准中最接近的行 */
function weightRowFor(heightCm, gender) {
  var std = BMI_STD || defaultBmiStd();
  var side = (gender === '女' ? std.female : std.male) || { rows: [] };
  var best = null, bestDiff = 1e9;
  (side.rows || []).forEach(function (r) {
    var d = Math.abs(r.h * 100 - heightCm);
    if (d < bestDiff) { bestDiff = d; best = r; }
  });
  return (best && bestDiff <= 0.51) ? best : null;
}
/** 体重区间判定: [合格?, 标签, 颜色, 区间描述] */
function weightVerdict(weightKg, heightCm, gender, age) {
  var row = weightRowFor(heightCm, gender);
  if (!row) { return [false, '无身高标准', 'var(--c-muted, #8a97a8)', null]; }
  var side = (gender === '女') ? '女' : '男';
  var col = -1;
  var std = BMI_STD || defaultBmiStd();
  (std.ages || []).forEach(function (a, i) {
    if (col >= 0) { return; }
    var nums = (a.match(/\d+/g) || []).map(Number);
    if (a.indexOf('以下') >= 0 || a.indexOf('≤') >= 0 || a.indexOf('<=') >= 0) {
      if (age == null || age <= (nums.length ? nums[0] : 24)) { col = i; }
    } else if (a.indexOf('以上') >= 0 || a.indexOf('≥') >= 0 || a.indexOf('>=') >= 0) {
      if (age == null || age >= (nums.length ? nums[0] : 60)) { col = i; }
    } else if (nums.length >= 2) {
      if (age == null || (age >= nums[0] && age <= nums[1])) { col = i; }
    }
  });
  var pair = (row.w && col >= 0) ? row.w[col] : null;
  if (!pair) { return [false, '无标准', 'var(--c-muted, #8a97a8)', null]; }
  var inRange = weightKg >= pair[0] && weightKg <= pair[1];
  var desc = side + ' ' + (std.ages[col] || '') + ' ' + row.h.toFixed(2) + 'm:' + pair[0] + '-' + pair[1] + 'kg';
  if (inRange) { return [true, '合格', 'var(--c-ok, #2ea44f)', desc]; }
  if (weightKg < pair[0]) { return [false, '体重偏轻不合格', 'var(--pri, #2f6fd6)', desc]; }
  return [false, '体重超标不合格', 'var(--c-danger, #d9463e)', desc];
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
  return GET('/api/fitness-matrix/items').then(function (items) {
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

function fitnessMatrixUnit(rows) {
  var units = [];
  (rows || []).forEach(function (r) {
    if (r.unit && units.indexOf(r.unit) < 0) units.push(r.unit);
  });
  if (units.length) return units.length === 1 && (units[0] === 'time' || units[0] === 'count') ? units[0] : '';
  var groups = {}, direction = '';
  (rows || []).forEach(function (r) {
    (groups[r.age_group] = groups[r.age_group] || []).push(r);
  });
  var invalid = false;
  Object.keys(groups).forEach(function (key) {
    var group = groups[key].slice().sort(function (a, b) { return Number(b.score) - Number(a.score); });
    var groupDirection = '';
    for (var i = 1; i < group.length; i++) {
      var diff = Number(group[i].value) - Number(group[i - 1].value);
      var current = diff > 0 ? 'time' : diff < 0 ? 'count' : '';
      if (!current || (groupDirection && groupDirection !== current)) { invalid = true; break; }
      groupDirection = current;
    }
    if (!groupDirection || (direction && direction !== groupDirection)) invalid = true;
    direction = groupDirection;
  });
  return invalid ? '' : direction;
}

/** 根据项目+年龄+成绩文本 → 分数,无法计算返回 '' */
function calcFitnessGrade(item, scoreText, age) {
  var matrix = FITNESS_MATRIX_CACHE[item];
  if (!matrix || !matrix.length) return '';
  var groups = [];
  matrix.forEach(function (r) {
    if (groups.indexOf(r.age_group) < 0) groups.push(r.age_group);
  });
  var myGroup = matchAgeGroup(groups, age);
  if (!myGroup) return '';
  var scored = matrix.filter(function (r) { return r.age_group === myGroup; });
  var unit = fitnessMatrixUnit(scored);
  if (!unit) return '';
  var text = String(scoreText == null ? '' : scoreText).trim(), perf = null;
  if (!text) return '';
  if (unit === 'time') {
    var clock = /^(\d+)\s*(?:分|['\u2032:])\s*(\d+(?:\.\d+)?)\s*(?:秒|["\u2033])?$/.exec(text);
    if (clock) {
      if (+clock[2] >= 60) return '';
      perf = +clock[1] * 60 + +clock[2];
    } else {
      var minutes = /^(\d+(?:\.\d+)?)\s*分$/.exec(text);
      var seconds = /^(\d+(?:\.\d+)?)\s*(?:秒|s)?$/i.exec(text);
      if (minutes) perf = +minutes[1] * 60;
      else if (seconds) perf = +seconds[1];
    }
  } else {
    var number = /^(\d+(?:\.\d+)?)\s*(?:次|个|米|m|厘米|cm)?$/i.exec(text);
    if (number) perf = +number[1];
  }
  if (perf === null || !isFinite(perf) || perf < 0) return '';
  scored.sort(function (a, b) { return Number(b.score) - Number(a.score); });
  for (var i = 0; i < scored.length; i++) {
    var need = Number(scored[i].value);
    if (unit === 'time' ? perf <= need : perf >= need) return String(scored[i].score);
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
      h('div', { text: '规则按分数从高到低依次判断;例如 "分数 ≥ 90 时评为 优秀"。', style: 'color:var(--c-sub, #66788c);font-size:13px;margin-bottom:12px' }),
      rulesBox,
        h('button', { class: 'btn btn-ghost', text: '添加规则', onclick: function () {
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

/** 标准Excel导入(评分矩阵/体重标准),成功后回调刷新 */
function importStdExcel(kind, done) {
  var input = h('input', { type: 'file', accept: '.xlsx', style: 'display:none' });
  input.addEventListener('change', function () {
    var f = input.files[0];
    input.value = '';
    if (!f) { return; }
    var fd = new FormData();
    fd.append('file', f);
    postFormWithProgress('/api/fitness-std/' + kind + '/import', fd, function () { }).then(function (r) {
      var msg = '导入完成:' + (r.added !== undefined ? '新增 ' + r.added + ' 格,更新 ' + r.updated + ' 格' : '共 ' + r.rows + ' 行') + (r.failed ? ',失败 ' + r.failed : '');
      toast(msg, r.failed ? 'info' : 'ok');
      if (r.errors && r.errors.length) { toast(r.errors.join('；'), 'err'); }
      done(r);
    }).catch(function (e) { toast(e.message, 'err'); });
  });
  document.body.appendChild(input);
  input.click();
  setTimeout(function () { input.remove(); }, 60000);
}

/** 体型标准:按性别 × 身高行 × 年龄列的合格体重区间矩阵 */
function openBodyStdManager() {
  var ready = BMI_STD ? Promise.resolve(BMI_STD) : loadBmiStd();
  ready.then(function (std) {
    var AGE_DEFAULTS = ['0-24', '25-29', '30-39', '40-49', '50-59', '60-99'];
    var data = normWeightStd(std);

    function normWeightStd(v) {
      if (v && v.ages && v.male && v.male.rows) {
        return { ages: v.ages, male: v.male, female: v.female || { rows: [] } };
      }
      // 旧BMI区间格式 → 生成 1.50~2.00m 等值区间表
      var mk = function (lo, hi) {
        var rows = [];
        for (var h = 150; h <= 200; h++) {
          var m2 = h / 100;
          rows.push({ h: m2, w: [Math.round(m2 * m2 * lo * 10) / 10, Math.round(m2 * m2 * hi * 10) / 10] });
        }
        return { rows: rows };
      };
      var m = (v && v.male) ? v.male : [17.5, 27.5];
      var f = (v && v.female) ? v.female : [17, 24.5];
      return { ages: AGE_DEFAULTS.slice(), male: mk(m[0], m[1]), female: mk(f[0], f[1]) };
    }

    var cur = { gender: '男' };
    var gBtns = {};
    var gridBox = h('div');

    function renderGrid() {
      try {
      var side = data[cur.gender === '女' ? 'female' : 'male'];
      gridBox.innerHTML = '';
      var table = h('table', { class: 'grid', style: 'width:100%;white-space:nowrap' });
      var hr = h('tr', {}, h('th', { style: 'min-width:74px', html: '体重(kg)<br>身高(m)' }));
      data.ages.forEach(function (a) {
        var cell = h('th');
        var i = h('input', { value: a, title: '年龄区间(如 0-24 或 25-29)', style: 'width:78px;border:1px solid #b9c6d6;background:#e8eef7;color:var(--pri-dark, #16437e);font-weight:700;text-align:center;border-radius:4px;padding:4px 2px' });
        i.addEventListener('input', function () {
          var idx = data.ages.indexOf(a);
          if (idx >= 0) { data.ages[idx] = i.value; }
        });
        cell.appendChild(i);
        cell.appendChild(h('button', { class: 'btn-link danger', style: 'margin-left:4px', text: '×', title: '删除该年龄组', onclick: function () {
          if (data.ages.length <= 1) { toast('至少保留一个年龄组', 'err'); return; }
          var idx = data.ages.indexOf(a);
          data.ages.splice(idx, 1);
          side.rows.forEach(function (r) { if (r.w && r.w.length > data.ages.length) { r.w.splice(idx, 1); } });
          renderGrid();
        } }));
        hr.appendChild(cell);
      });
      table.appendChild(h('thead', {}, hr));
      var tb = h('tbody');
      side.rows.forEach(function (r) {
        var tr = h('tr', {});
        var th = h('td', { style: 'font-weight:600;text-align:center;background:#f5f8fc;white-space:nowrap' });
        var hi = h('input', { value: r.h.toFixed(2), style: 'width:52px;border:1px solid transparent;background:transparent;text-align:center;font-weight:600' });
        hi.addEventListener('input', function () {
          var v = Number(hi.value);
          if (!isNaN(v) && v >= 1.2 && v <= 2.3) { r.h = v; }
        });
        th.appendChild(hi);
        th.appendChild(h('button', { class: 'btn-link danger', style: 'margin-left:2px', text: '×', title: '删除该身高行', onclick: function () {
          side.rows = side.rows.filter(function (x) { return x !== r; });
          renderGrid();
        } }));
        tr.appendChild(th);
        data.ages.forEach(function (a, ai) {
          var lo = (r.w && r.w[ai]) ? r.w[ai][0] : '';
          var hv = (r.w && r.w[ai]) ? r.w[ai][1] : '';
          var cellIn = h('input', { value: lo === '' ? '' : (lo + '-' + hv), placeholder: '下限-上限', style: 'width:92px;border:1px solid transparent;border-radius:3px;text-align:center;font-size:12.5px' });
          cellIn.addEventListener('input', function () {
            if (!r.w) { r.w = []; }
            var m = /^\s*([0-9.]+)\s*[~～-]\s*([0-9.]+)\s*$/.exec(cellIn.value);
            if (m) {
              r.w[ai] = [Number(m[1]), Number(m[2])];
              cellIn.style.background = '#eaf6ec';
            } else {
              cellIn.style.background = '#fdf0ef';
            }
          });
          tr.appendChild(h('td', { style: 'text-align:center;padding:2px' }, cellIn));
        });
        tb.appendChild(tr);
      });
      table.appendChild(tb);
      gridBox.appendChild(h('div', { class: 'table-wrap', style: 'max-height:62vh' }, table));
      gridBox.appendChild(h('div', { style: 'display:flex;gap:12px;margin-top:12px;flex-wrap:wrap' },
        h('button', { class: 'btn btn-ghost btn-sm', text: '添加身高行', onclick: function () {
          var last = side.rows.length ? side.rows[side.rows.length - 1].h : 1.4;
          side.rows.push({ h: Math.round((last + 0.01) * 100) / 100, w: new Array(data.ages.length).fill(null) });
          renderGrid();
        } }),
        h('button', { class: 'btn btn-ghost btn-sm', text: '添加年龄组', onclick: function () {
          var n = prompt('年龄组名称(如 31-35岁):', '');
          if (n && n.trim()) {
            data.ages.push(n.trim());
            side.rows.forEach(function (r) { if (!r.w) { r.w = []; } r.w.push(null); });
            renderGrid();
          }
        } }),
        h('button', { class: 'btn btn-ghost btn-sm', text: '相邻行填充空白', onclick: function () {
          side.rows.forEach(function (r) {
            if (!r.w) { r.w = []; }
            for (var i = 0; i < data.ages.length; i++) {
              if (!r.w[i]) {
                var src2 = null;
                for (var j = i - 1; j >= 0 && !src2; j--) { if (r.w[j]) { src2 = r.w[j]; } }
                for (var j2 = i + 1; j2 < data.ages.length && !src2; j2++) { if (r.w[j2]) { src2 = r.w[j2]; } }
                r.w[i] = src2 ? src2.slice() : null;
              }
            }
          });
          renderGrid();
        } })));
      } catch (e) {
        gridBox.innerHTML = '<div style="color:var(--c-danger, #d9463e);padding:12px">渲染失败:' + e.message + '</div>';
      }
    }

    var body = h('div', {},
      h('div', { style: 'display:flex;gap:8px;align-items:center;margin-bottom:12px;flex-wrap:wrap' },
        h('span', { style: 'font-size:13px;color:var(--c-sub, #66788c);flex:1;min-width:260px', text: '体重合格标准:行=身高(米),列=年龄段,单元格=合格体重区间(填 47.4~66.3)。判定:身高匹配行、年龄匹配列。' }),
        h('button', { class: 'btn btn-ghost btn-sm', text: '下载模板', onclick: function () {
          var pm = showProgressModal('下载模板');
          apiBlobWithProgress('/api/fitness-std/weight/template', function (p) { pm.setProgress(p); }).then(function (r) {
            pm.setProgress(100); downloadBlob(r.blob, r.filename); setTimeout(function () { pm.close(); }, 400);
          }).catch(function (e) { pm.close(); toast(e.message, 'err'); });
        } }),
        h('button', { class: 'btn btn-ghost btn-sm', text: '导入Excel', onclick: function () {
          importStdExcel('weight', function (r) {
            loadBmiStd().then(function () { m.close(); openBodyStdManager(); });
          });
        } })),
      h('div', { style: 'display:flex;gap:8px;margin-bottom:12px;align-items:center' },
        (function () {
          var box = h('div', { style: 'display:flex;gap:8px' });
          [['男', 'male'], ['女', 'female']].forEach(function (g) {
            var b = h('button', { type: 'button', class: 'btn btn-sm', text: g[0] + '标准', style: 'border-radius:14px' });
            b.addEventListener('click', function () { cur.gender = g[0]; renderGrid(); paintG(); });
            gBtns[g[1]] = b;
            box.appendChild(b);
          });
          function paintG() {
            Object.keys(gBtns).forEach(function (k) {
              gBtns[k].className = 'btn btn-sm' + (cur.gender === (k === 'male' ? '男' : '女') ? ' btn-primary' : ' btn-ghost');
            });
          }
          paintG();
          return box;
        })(),
        h('span', { style: 'flex:1' }),
        h('button', { class: 'btn btn-ghost btn-sm', text: '生成完整身高表(1.50~2.00m)', onclick: function () {
          if (!confirm('将生成 1.50~2.00m 共51行(保留已填数据),确定?')) { return; }
          var side = data[cur.gender];
          var map = {};
          side.rows.forEach(function (r) { map[Math.round(r.h * 100)] = r; });
          side.rows = [];
          for (var hcm = 150; hcm <= 200; hcm++) {
            if (map[hcm]) { side.rows.push(map[hcm]); }
            else { side.rows.push({ h: hcm / 100, w: new Array(data.ages.length).fill(null) }); }
          }
          renderGrid();
        } })),
      gridBox);

    var m;
    var m = openModal({
      title: '体型标准(身高 × 年龄段 体重合格区间矩阵)', body: body, wide: true, xxwide: true,
      footer: [
        h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m.close(); } }),
        h('button', {
          class: 'btn btn-primary', text: '保存',
          onclick: function () {
            var missing = 0;
            data.male.rows.forEach(function (r) { (r.w || []).forEach(function (x) { if (!x) { missing++; } }); });
            data.female.rows.forEach(function (r) { (r.w || []).forEach(function (x) { if (!x) { missing++; } }); });
            if (missing > 0 && !confirm('还有 ' + missing + ' 个空单元格,空白将视为无标准(该人员不判分)。继续保存?')) { return; }
            PUT('/api/setting/bmi_standard', { ages: data.ages, male: data.male, female: data.female }).then(function () {
              BMI_STD = data;
              m.close();
              toast('体重标准已保存');
            }).catch(function (e) { toast(e.message, 'err'); });
          }
        })
      ]
    });
    renderGrid();
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
    h('div', { style: 'display:flex;align-items:center;gap:16px' },
      h('div', { style: 'font-size:17px;font-weight:600;color:var(--c-dark, #1d2c40)', text: '体能评分标准管理' }),
      h('button', { class: 'btn btn-ghost btn-sm', text: '体型标准', title: '设置身高体重体型合格标准', onclick: openBodyStdManager }),
      h('button', { class: 'btn btn-ghost btn-sm', text: '下载模板', title: '下载评分标准Excel模板(每项目一个Sheet)', onclick: function () {
        var pm = showProgressModal('下载模板');
        apiBlobWithProgress('/api/fitness-std/matrix/template', function (p) { pm.setProgress(p); }).then(function (r) {
          pm.setProgress(100); downloadBlob(r.blob, r.filename); setTimeout(function () { pm.close(); }, 400);
        }).catch(function (e) { pm.close(); toast(e.message, 'err'); });
      } }),
      h('button', { class: 'btn btn-ghost btn-sm', text: '导入Excel', title: '导入评分标准(每Sheet一个项目:分数列+各年龄段列)', onclick: function () {
        importStdExcel('matrix', function () { refreshAll(); });
      } })),
    h('div', { style: 'font-size:17px;font-weight:600;color:var(--c-dark, #1d2c40)', text: ' ' }),
    h('button', { class: 'btn btn-primary', text: '✕ 关闭', onclick: function () { overlay.remove(); reloadFitnessMatrix(); } }));
  var mainArea = h('div', { style: 'flex:1;display:flex;overflow:hidden' });

  // 左侧:项目列表(固定)
  var left = h('div', { style: 'width:190px;flex:none;background:#fff;border-right:1px solid #edf1f6;display:flex;flex-direction:column;overflow:hidden' });
  var listBox = h('div', { style: 'flex:1;overflow-y:auto;padding:8px 0' });
  var addBox = h('div', { style: 'padding:12px;border-top:1px solid #edf1f6' });

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
        style: 'display:flex;align-items:center;justify-content:space-between;padding:8px 12px;cursor:pointer;' +
          (it === currentItem ? 'background:#e4edfb;color:var(--pri-dark, #16437e);font-weight:600' : 'color:var(--c-body, #445262)') +
          ';border-left:3px solid ' + (it === currentItem ? 'var(--pri, #2f6fd6)' : 'transparent')
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
          row.style.color = 'var(--pri-dark, #16437e)';
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
  addBox.appendChild(h('div', { style: 'font-size:12px;color:var(--c-muted, #8a97a8);margin-bottom:8px', text: '新增考核项目:' }));
  addBox.appendChild(h('button', {
    class: 'btn btn-primary btn-sm', text: '新建项目',
    style: 'width:100%',
    onclick: function () { openNewProjectDialog(); }
  }));

  function openNewProjectDialog() {
    var nameInput = h('input', { type: 'text', placeholder: '如:3000米跑', style: 'width:100%;height:32px;padding:0 10px;border:1px solid #c5cdd8;border-radius:4px' });
    var unitDD = makeDropdown([{v:'count',l:'次数型(越多越好)'},{v:'time',l:'时间型(越少越好)'}], '必选');

    var m2 = openModal({
      title: '新建考核项目',
      body: h('div', { style: 'display:flex;flex-direction:column;gap:12px' },
        h('div', {}, h('div', { style: 'font-size:13px;color:var(--c-sub, #66788c);margin-bottom:4px', text: '项目名称(必填):' }), nameInput),
        h('div', {}, h('div', { style: 'font-size:13px;color:var(--c-sub, #66788c);margin-bottom:4px', text: '计分方式(必选):' }), unitDD)),
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
            var defaultAges = ['0-24', '25-27', '28-30', '31-33', '34-36', '37-39'];
            var defaultScores = [100, 95, 90, 85, 80, 75, 70, 65, 60, 55];
            POST('/api/fitness-matrix/init', {
              item: name, unit: unit,
              ageGroups: defaultAges, scores: defaultScores
            }).then(function () {
              if (items.indexOf(name) < 0) { items.push(name); }
              currentItem = name;
              renderLeft();
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
    function openUnitDialog() {
      GET('/api/fitness-matrix?item=' + encodeURIComponent(currentItem)).then(function (rows) {
        var cur = fitnessMatrixUnit(rows || []);
        var dd = makeDropdown([{ v: 'count', l: '次数型(越多越好)' }, { v: 'time', l: '时间型(越少越好)' }], '必选');
        if (cur) { dd.value = cur; }
        var curText = cur === 'time' ? '时间型(越少越好)' : cur === 'count' ? '次数型(越多越好)' : '待确认';
        var m2 = openModal({
          title: '修改计分方式 · ' + currentItem,
          body: h('div', { style: 'display:flex;flex-direction:column;gap:12px' },
            h('div', { style: 'font-size:13px;color:var(--c-sub, #66788c)', text: '当前计分方式:' + curText }),
            h('div', {}, h('div', { style: 'font-size:13px;color:var(--c-sub, #66788c);margin-bottom:4px', text: '改为:' }), dd),
            h('div', { style: 'font-size:12px;color:var(--c-muted, #8a97a8);line-height:1.7',
              text: '保存后立即对评分生效。达标值数值不会自动换算,请同时核对各分数档的达标值(次数型越大越好,时间型越小越好)。' })),
          footer: [
            h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m2.close(); } }),
            h('button', {
              class: 'btn btn-primary', text: '保存',
              onclick: function () {
                var unit = dd.value;
                if (!unit) { toast('请选择计分方式', 'err'); return; }
                POST('/api/fitness-matrix/unit', { item: currentItem, unit: unit }).then(function () {
                  m2.close();
                  renderRight();
                  reloadFitnessMatrix();
                  toast('计分方式已修改');
                }).catch(function (e) { toast(e.message, 'err'); });
              }
            })
          ]
        });
      });
    }
    rightHead = h('div', { style: 'margin-bottom:12px;display:flex;gap:8px;align-items:center;flex-wrap:wrap' },
      h('strong', { style: 'font-size:15px;color:var(--pri-dark, #16437e)', text: currentItem }),
      h('span', { style: 'color:#a6b3c4;font-size:12px', text: '单元格直接输入,失焦保存;表头点击编辑年龄/分数;× 删除列' }),
      h('div', { style: 'flex:1' }),
      h('button', { class: 'btn btn-ghost btn-sm', text: '计分方式', title: '修改本项目的计分方式(次数型/时间型)', onclick: openUnitDialog }),
      h('button', { class: 'btn btn-ghost btn-sm', text: '添加年龄段', onclick: promptAgeGroup }),
      h('button', { class: 'btn btn-ghost btn-sm', text: '添加分数行', onclick: promptScoreRow }));
    right.appendChild(rightHead);

    // 单位提示条:自动检测数据类型并显示
    var isTimeType = false;
    GET('/api/fitness-matrix?item=' + encodeURIComponent(currentItem)).then(function (rows) {
      var unit = fitnessMatrixUnit(rows || []);
      isTimeType = unit === 'time';
      var unitLabel = unit ? (isTimeType ? '时间型(越少越好)' : '次数型(越多越好)') : '计分方式待确认';
      var unitColor = isTimeType ? 'var(--c-warn, #e8960e)' : (unit ? '#2f9e5b' : 'var(--c-muted, #8a97a8)');
      var hintText = !unit ? '该项目各分数档的达标值方向不一致,无法判断计分方式。请修改达标值:分数越高,时间型应越短、次数型应越多;或点上方「计分方式」按钮直接指定。'
        : isTimeType
          ? '按用时长短计分:分数越高,要求的用时越短。达标值填秒数,如 12分30秒 填 750。录入成绩时可填「12分30秒」或直接填秒数。'
          : '按个数/距离计分:分数越高,要求的个数(或米数)越多。达标值直接填数字,支持小数,如俯卧撑填 40、立定跳远填 2.35。录入成绩时填同单位数字即可。';
      right.appendChild(h('div', {
        style: 'background:#fff;border:1px solid #dbe4ee;border-left:3px solid ' + unitColor +
          ';border-radius:6px;padding:12px 14px;margin-bottom:12px;font-size:13px;color:var(--c-body, #445262);display:flex;align-items:center;gap:12px'
      },
        h('span', {
          style: 'flex:none;padding:4px 10px;border-radius:10px;font-size:12px;font-weight:600;color:#fff;background:' + unitColor,
          text: unitLabel
        }),
        h('span', { text: hintText })));

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
      head += '<th class="mx-age-head" data-col="' + esc(g) + '" style="text-align:center;white-space:nowrap">' +
        '<input type="text" value="' + esc(g) + '" title="点击编辑年龄区间" style="width:64px;height:24px;text-align:center;font-weight:700;font-size:12px;color:var(--pri-dark, #16437e);background:#e8eef7;border:1px solid #b9c6d6;border-radius:3px;padding:0 2px;line-height:24px;box-sizing:border-box" />' +
        '<button type="button" class="btn-link danger mx-age-del" data-col="' + esc(g) + '" title="删除该列" style="margin-left:4px;font-size:11px;vertical-align:middle">&times;</button></th>';
    });
    head += '</tr></thead>';
    var bodyHtml = '';
    scores.forEach(function (sc) {
      bodyHtml += '<tr><th style="text-align:center" class="mx-score-head" data-score="' + sc + '">' +
        '<input type="text" value="' + sc + '" title="点击编辑分数" style="width:46px;height:24px;text-align:center;font-weight:700;font-size:12px;color:var(--pri-dark, #16437e);background:#e8eef7;border:1px solid #b9c6d6;border-radius:3px;padding:0 2px;line-height:24px;box-sizing:border-box" /></th>';
      ageGroups.forEach(function (g) {
        bodyHtml += '<td><input type="text" class="mx-input" data-col="' + esc(g) + '" data-score="' + sc +
          '" style="width:100%;height:26px;font-size:13px;padding:0 4px;text-align:center;border:1px solid transparent;border-radius:3px;background:transparent" /></td>';
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

    // 年龄列删除按钮(×)
    table.addEventListener('click', function (e) {
      var del = e.target.closest('.mx-age-del');
      if (!del) { return; }
      var g = del.getAttribute('data-col');
      confirmBox('确定删除年龄段「' + g + '」整列数据?', true).then(function (yes) {
        if (!yes) { return; }
        DEL('/api/fitness-matrix/column?item=' + encodeURIComponent(currentItem)
          + '&age_group=' + encodeURIComponent(g)).then(function () {
          ageGroups = ageGroups.filter(function (x) { return x !== g; });
          renderRight();
          reloadFitnessMatrix();
        }).catch(function (err) { toast(err.message, 'err'); });
      });
    });

    // 表头输入框:失焦/回车 → 重命名(年龄区间/分数,数据批量迁移到新名称)
    table.addEventListener('change', function (e) {
      if (e.target.tagName !== 'INPUT') { return; }
      var ageHead = e.target.closest('.mx-age-head');
      if (ageHead) {
        var oldG = ageHead.getAttribute('data-col');
        var newG = e.target.value.trim();
        if (!newG || newG === oldG) { e.target.value = oldG; return; }
        GET('/api/fitness-matrix?item=' + encodeURIComponent(currentItem)).then(function (rows) {
          var chain = Promise.resolve();
          (rows || []).filter(function (r) { return r.age_group === oldG; }).forEach(function (r) {
            chain = chain.then(function () {
              return POST('/api/fitness-matrix', { item: currentItem, age_group: newG, score: r.score, value: r.value });
            });
          });
          return chain;
        }).then(function () {
          return DEL('/api/fitness-matrix/column?item=' + encodeURIComponent(currentItem)
            + '&age_group=' + encodeURIComponent(oldG));
        }).then(function () {
          ageGroups = ageGroups.map(function (x) { return x === oldG ? newG : x; });
          renderRight();
          reloadFitnessMatrix();
          toast('年龄区间已改为「' + newG + '」');
        }).catch(function (err) { toast(err.message, 'err'); renderRight(); });
        return;
      }
      var scoreHead = e.target.closest('.mx-score-head');
      if (scoreHead) {
        var oldS = +scoreHead.getAttribute('data-score');
        var newS = parseInt(e.target.value, 10);
        if (isNaN(newS) || newS === oldS) { e.target.value = oldS; return; }
        GET('/api/fitness-matrix?item=' + encodeURIComponent(currentItem)).then(function (rows) {
          var chain = Promise.resolve();
          (rows || []).filter(function (r) { return +r.score === oldS; }).forEach(function (r) {
            chain = chain.then(function () {
              return POST('/api/fitness-matrix', { item: currentItem, age_group: r.age_group, score: newS, value: r.value });
            });
          });
          return chain;
        }).then(function () {
          return DEL('/api/fitness-matrix?item=' + encodeURIComponent(currentItem) + '&score=' + oldS);
        }).then(function () {
          scores = scores.map(function (x) { return +x === oldS ? newS : x; });
          renderRight();
          reloadFitnessMatrix();
          toast('分数已改为 ' + newS);
        }).catch(function (err) { toast(err.message, 'err'); renderRight(); });
      }
    });

    rightTable = h('div', { style: 'background:#fff;border-radius:8px;box-shadow:0 1px 4px rgba(23,43,77,.08);overflow-x:auto' }, table);
    right.appendChild(rightTable);
  }

  /* ===== 弹窗式添加 ===== */
  function promptAgeGroup() {
    if (!currentItem) { toast('请先选择项目', 'err'); return; }
    var input = h('input', { type: 'text', placeholder: '如:0-24、25-29、40-42', style: 'width:100%;height:32px;padding:0 10px;border:1px solid #c5cdd8;border-radius:4px' });
    var m2 = openModal({
      title: '添加年龄段',
      body: h('div', {}, h('div', { style: 'color:var(--c-sub, #66788c);font-size:13px;margin-bottom:8px', text: '输入年龄段名称,将作为矩阵的新列:' }), input),
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
    var input = h('input', { type: 'number', placeholder: '如:50、45', style: 'width:100%;height:32px;padding:0 10px;border:1px solid #c5cdd8;border-radius:4px' });
    var m2 = openModal({
      title: '添加分数行',
      body: h('div', {}, h('div', { style: 'color:var(--c-sub, #66788c);font-size:13px;margin-bottom:8px', text: '输入分数(0-100),将作为矩阵的新行:' }), input),
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
  style.textContent = '.mx-input:focus{outline:none}.mx-input:hover{border-color:var(--c-border, #c5cdd8)!important}';
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
    { key: 'departmentId', label: '编制单位', type: 'select', options: function () {
        return Ctx.deptFlat.map(function (d) {
          var prefix = '';
          for (var i = 0; i < d.depth; i++) { prefix += '　'; }
          return { v: String(d.id), l: d.path || prefix + d.name };
        });
      } }
  ],
  moreFilters: [
    { key: 'driverLicense', label: '驾驶证', type: 'select', options: [{ v: 'yes', l: '有驾驶证' }, { v: 'no', l: '无驾驶证' }] },
    { key: 'driverLicenseType', label: '驾驶证类型', type: 'select', options: function () { return driverLicenseOptions('license_type'); } },
    { key: 'driverLicenseStatus', label: '驾驶证状态', type: 'select', options: function () { return driverLicenseOptions('license_status'); } },
    { key: 'onDuty', label: '在岗情况', type: 'select', options: [{ v: 'yes', l: '在编在岗' }, { v: 'no', l: '借岗在外' }] },
    { key: 'actualDepartmentId', label: '实际单位', type: 'select', options: function () {
        return Ctx.deptFlat.map(function (d) {
          var prefix = '';
          for (var i = 0; i < d.depth; i++) { prefix += '　'; }
          return { v: String(d.id), l: d.path || prefix + d.name };
        });
      } },
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
    { key: 'locationPoint', label: '所在点位', type: 'select', options: function () { return dictLabels('personnel_location'); } },
    { key: 'post', label: '职务/岗位' },
    { key: 'nativePlace', label: '籍贯所在地' },
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
    { key: 'militaryIdNo', label: '军人证件号', render: function (r) { return esc(String(r.militaryIdNo || '').trim() || '未填写'); } },
    { key: 'departmentPath', label: '编制单位', render: function (r) {
        var path = String(r.departmentPath || r.departmentName || '未分配').replace(/\s*\/\s*/g, '');
        var html = '<span class="personnel-department-path" title="' + esc(path) + '">' + esc(path) + '</span>';
        // 编制之外另有实际在岗单位时,追加灰色小标签(与编制相同或未设置则不显示)
        if (Number(r.actualDepartmentId) > 0 && String(r.actualDepartmentId) !== String(r.departmentId)) {
          var actualPath = r.actualDepartmentPath || '';
          // 后端已直接提供末级名;旧响应无该字段时回退按'/'拆分
          var leaf = r.actualDepartmentName || (actualPath ? actualPath.split('/').pop().trim() : '') || ('单位' + r.actualDepartmentId);
          html += '<span style="font-size:12px;color:#8a97a8;margin-left:4px;white-space:nowrap" title="' + esc(actualPath || leaf) + '">实际：' + esc(leaf) + '</span>';
        }
        return html;
      } },
    { key: 'locationPoint', label: '所在点位', render: function (r) { return r.locationPoint || '—'; } },
    { key: 'category', label: '人员类别', render: function (r) { return r.category || '—'; } },
    { key: 'rank', label: '军衔/职级' },
    { key: 'post', label: '职务' },
    { key: 'status', label: '状态', render: function (r) { return badge(r.status); } },
    { key: 'phone', label: '手机号' },
    { key: 'entryDate', label: '入伍日期' },
    { key: '__service', label: '军龄', render: function (r) { var y = calcServiceYears(r.equivEntryDate || r.entryDate); return y ? y + '年' : '—'; } }
  ],
  rowTitle: function (r) { return r.name; },
  onView: function (row, act) { openDetailModal(personnelCfg, row, act); },
  delTip: function (r) { return '确定删除人员「' + r.name + '」吗?其手机设备、休假、体能、档案子表记录将一并删除,装备/物资/车辆保管和单位负责人关联将解除。\n删除后可通过工具栏「删除撤回」完整恢复(快照保留 30 天)。'; },
  afterTable: function (rootEl) { loadRowPhotos(rootEl); },
  formTop: personnelPhotoBlock,
  addLabel: '新增人员信息',
  toolbarExtra: function (bar, act) {
    bar.appendChild(h('button', {
      class: 'btn btn-ghost', text: '删除撤回',
      title: '恢复最近删除的人员及其全部关联记录(手机、休假、体能、子表、保管关联、照片)',
      onclick: function () { openPersonnelUndo(); }
    }));
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
    { key: 'idCard', label: '身份证号', required: true, hint: '输入后自动校验并带出性别/出生日期/年龄' },
    { key: 'militaryIdNo', label: '军人证件号', required: true },
    { key: 'gender', label: '性别', type: 'select', required: true, options: ['男', '女'] },
    { key: 'birthday', label: '出生日期', type: 'date', required: true },
    { key: 'age', label: '年龄', required: true, readonly: true, placeholder: '自动计算' },
    { key: 'phone', label: '手机号', readonly: true, placeholder: '在「手机管理」中添加设备后自动带出' },
    { key: 'departmentId', label: '编制单位', type: 'dept', required: true },
    { key: 'actualDepartmentId', label: '实际单位', type: 'dept', hint: '留空表示在编在岗；与编制单位并列对照' },
    { key: 'locationPoint', label: '所在点位', type: 'select', required: true, options: function () { return dictLabels('personnel_location'); } },
    { key: 'status', label: '状态', type: 'select', required: true, options: statusOptions },
    { section: '军衔职务' },
    { key: 'category', label: '人员类别', type: 'select', required: true, options: function () { return dictLabels('personnel_category'); } },
    { key: 'rank', label: '军衔/职级', type: 'select', required: true, placeholder: '先选类别',
      depends: { field: 'category', dict: 'rank', parentDict: 'personnel_category' } },
    { key: 'post', label: '职务/岗位', required: true },
    { key: 'postDate', label: '任岗位时间', type: 'date' },
    { key: 'entryDate', label: '入伍/入职日期', type: 'date', required: true },
    { key: 'equivEntryDate', label: '相当入伍时间', type: 'date', required: true },
    { section: '更多资料' },
    { key: 'nation', label: '民族', type: 'select', required: true, options: function () { return dictLabels('nation'); } },
    { key: 'bloodType', label: '血型', type: 'select', required: true, options: function () { return dictLabels('blood_type'); } },
    { key: 'enlistPlace', label: '入伍所在地', type: 'region', required: true },
    { key: 'nativePlace', label: '籍贯所在地', type: 'region', required: true },



    { key: 'onlyChild', label: '独生子女', type: 'select', required: true, options: function () { return dictLabels('only_child'); } },
    { key: 'address', label: '详细地址', span: 'full', required: true, placeholder: '街道/门牌号等具体住址' },
    { section: '党团籍信息' },
    { key: 'politicalStatus', label: '政治面貌', type: 'select', required: true, options: function () { return dictLabels('political_status'); } },
    { key: 'partyApplyDate', label: '提交申请书时间', type: 'date' },
    { key: 'partyActivistDate', label: '积极分子时间', type: 'date' },
    { key: 'partyCandidateDate', label: '预备党员时间', type: 'date' },
    { key: 'partyFullDate', label: '转正时间', type: 'date' },
    { key: 'partyJoinDate', label: '入党/入团日期', type: 'date', requiredIf: function (v) { return ['党员', '团员', '中共党员', '共青团员'].indexOf(v.politicalStatus) >= 0; } },
    { key: 'partyOrgLocation', label: '组织关系所在地', placeholder: '如:XX部队XX党委', requiredIf: function (v) { return v.politicalStatus === '党员' || v.politicalStatus === '中共党员'; } },
    { key: 'partyPost', label: '党内职务', placeholder: '如:支部委员、党委书记' },
    { key: 'partyRemark', label: '党团备注', type: 'textarea', span: 'full' },
    { section: '婚姻状况' },
    { key: 'maritalStatus', label: '婚姻状况', type: 'select', required: true, options: function () { return dictLabels('marital_status'); } },
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
  formSetup: function (form, row) {
    // 实际单位:后端 0=无(在编在岗);下拉无 0 选项,归一为空串再展示/提交
    var adEl = form.inputs['actualDepartmentId'];
    if (adEl && String(adEl.value) === '0') { adEl.value = ''; }
    var idEl = form.inputs['idCard'];
    var gEl = form.inputs['gender'];
    var bEl = form.inputs['birthday'];
    var aEl = form.inputs['age'];
    // 手机号只读:编辑时实时从手机管理取本人最新设备号码
    var phEl = form.inputs['phone'];
    if (phEl && row && row.id) {
      phEl.value = String(row.phone || '');
      api('GET', '/api/phone/by-person?personnelId=' + encodeURIComponent(row.id)).then(function (data) {
        var list = data && data.list ? data.list : (Array.isArray(data) ? data : []);
        for (var i = 0; i < list.length; i++) {
          var pn = String(list[i].phone_number || '').trim();
          if (pn) { phEl.value = pn; return; }
        }
        phEl.value = '';
      }).catch(function () { /* 取不到设备时保留原值 */ });
    }
    if (!idEl) { return; }
    function autoFill() {
      var v = String(idEl.value || '').trim().toUpperCase();
      if (!v) { if (aEl) { aEl.value = ''; } idEl.classList.remove('bad'); idEl.title = ''; hideFieldBubble(idEl); return; }
      var info = validateIdCard(v);
      if (!info) {
        var msg = v.length === 18 ? '身份证校验码不正确,请核对' : '身份证号应为18位';
        idEl.classList.add('bad');
        idEl.title = msg;
        showFieldBubble(idEl, msg);
        if (aEl) { aEl.value = ''; }
        return;
      }
      idEl.classList.remove('bad');
      idEl.title = '校验通过';
      hideFieldBubble(idEl);
      if (gEl) { gEl.value = info.gender; }
      if (bEl) { bEl.value = info.birthday; }
      if (aEl) { aEl.value = String(calcAgeFrom(info.birthday)); }
    }
    idEl.addEventListener('change', autoFill);
    idEl.addEventListener('input', function () {
      clearTimeout(idEl._t);
      idEl._t = setTimeout(function () {
        if (String(idEl.value || '').trim().length === 18) { autoFill(); }
      }, 400);
    });
    if (String(idEl.value || '').trim().length === 18) { setTimeout(autoFill, 60); }
    if (bEl && aEl) {
      bEl.addEventListener('change', function () { aEl.value = String(calcAgeFrom(bEl.value)); });
    }
  },
  beforeSave: function (data) {
    delete data.age;
    delete data.phone; // 手机号由手机管理模块维护,人员表单不修改
    var v = String(data.idCard || '').trim();
    if (v && !validateIdCard(v)) {
      toast('身份证号校验不通过,请核对后重试', 'err');
      return null;
    }
    return data;
  },
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
        'pvehicle': pveh.__staged, 'housing': hous.__staged, 'fitness': fit.__staged }
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
      bindBlobUrl(preview, r.blob, true);
      preview.textContent = '';
    }).catch(function () { preview.textContent = '无照片'; });
  }

  var fileInput = h('input', { type: 'file', accept: 'image/jpeg,image/png', style: 'display:none' });
  var removeBtn; // 移除照片按钮:选择新照片后需要在 reader.onload 里重新启用
  fileInput.addEventListener('change', function () {
    var f = fileInput.files[0];
    fileInput.value = '';
    if (!f) return;
    var mime = String(f.type || '').toLowerCase();
    if (!/\.(jpe?g|png)$/i.test(f.name) || (mime && !/^image\/(jpeg|png)$/.test(mime))) {
      toast('仅支持 JPG 或 PNG 图片', 'err');
      return;
    }

    var reader = new FileReader();
    reader.onload = function () {
      st.base64 = reader.result;
      st.removed = false;
      preview.style.backgroundImage = 'url(' + st.base64 + ')';
      preview.textContent = '';
      removeBtn.disabled = false; // 选定新照片后允许移除
    };
    reader.onerror = function () { toast('读取图片失败', 'err'); };
    reader.readAsDataURL(f);
  });

  var box = h('div', { class: 'form-item full' });
  box.appendChild(h('label', { text: '人员照片' }));
  box.appendChild(h('div', { class: 'photo-box' }, preview,
    h('div', {}, fileInput,
      h('div', { class: 'photo-actions' },
        h('button', { class: 'btn btn-ghost btn-sm photo-action', text: '选择照片', onclick: function () { fileInput.click(); } }),
        removeBtn = h('button', {
          class: 'btn btn-ghost btn-sm photo-action', text: '移除照片',
          disabled: !st.existing, // 无照片时禁用,避免对空状态误操作
          onclick: function () {
            st.base64 = null; st.removed = true; st.existing = null;
            preview.style.backgroundImage = '';
            preview.textContent = '无照片';
            this.disabled = true;
          }
        })),
      h('div', { class: 'photo-hint', text: '支持 JPG、PNG 格式' }))));

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
  clickKey: 'name', addLabel: '新增装备信息',
  personLookup: { idCard: 'keeperIdCard', name: 'keeperName', pid: 'keeperId' },
  filters: [
    { key: 'name', label: '名称' },
    { key: 'status', label: '状态', type: 'select', options: function () { return dictLabels('equipment_status'); } }
  ],
  moreFilters: [
    { key: 'category', label: '类别', type: 'select', options: function () { return dictLabels('equipment_category'); } },
    { key: 'code', label: '编号' },
    { key: 'identCode', label: '识别码' },
    { key: 'keeperName', label: '保管人' },
    { key: 'location', label: '存放位置', type: 'select', options: function () { return dictLabels('equipment_location'); } }
  ],
  columns: [
    { key: 'name', label: '装备名称' },
    { key: 'code', label: '编号' },
    { key: 'identCode', label: '识别码' },
    { key: 'category', label: '类别' },
    { key: 'model', label: '型号' },
    { key: 'status', label: '状态', render: function (r) { return badge(r.status); } },
    { key: 'keeperName', label: '保管人', render: function (r) { return r.keeperName || '—'; } },
    { key: 'location', label: '存放位置' },
    { key: 'equipDate', label: '装备时间' }
  ],
  rowTitle: function (r) { return r.name; },
  form: [
    { key: 'name', label: '装备名称', required: true },
    { key: 'code', label: '装备编号' },
    { key: 'identCode', label: '识别码' },
    { key: 'category', label: '类别', type: 'select', options: function () { return dictLabels('equipment_category'); } },
    { key: 'model', label: '型号' },
    { key: 'status', label: '状态', type: 'select', required: true, options: function () { return dictLabels('equipment_status'); } },
    { key: 'keeperIdCard', label: '保管人身份证', placeholder: '输入后自动带出姓名' },
    { key: 'keeperName', label: '保管人姓名', readonly: true, placeholder: '按身份证号自动获取' },
    { key: 'keeperId', type: 'hidden' },
    { key: 'location', label: '存放位置', type: 'select', options: function () { return dictLabels('equipment_location'); } },
    { key: 'equipDate', label: '装备时间', type: 'date' },
    { key: 'remark', label: '备注', type: 'textarea', span: 'full' }
  ],
  beforeSave: function (data) {
    // keeperIdCard/keeperName 仅用于表单带出,不提交
    delete data.keeperIdCard;
    delete data.keeperName;
    return data;
  }
};

/** 装备分组子视图:同一装备台账,固定筛选某类别,新增时预填该类别 */
function equipmentVariantCfg(key, title, category, icon) {
  var isGun = category === '枪支';
  var variantForm = equipmentCfg.form.filter(function (field) { return !isGun || field.key !== 'identCode'; });
  var variantColumns = [
    { key: 'name', label: '装备名称' },
    { key: 'code', label: '编号' },
    { key: 'category', label: '类别' },
    { key: 'model', label: '型号' },
    { key: 'status', label: '状态', render: function (r) { return badge(r.status); } },
    { key: 'keeperName', label: '保管人', render: function (r) { return r.keeperName || '—'; } },
    { key: 'location', label: '存放位置' },
    { key: 'equipDate', label: '装备时间' }
  ];
  if (isGun) {
    variantForm = [];
    for (var i = 0; i < equipmentCfg.form.length; i++) {
      var field = equipmentCfg.form[i];
      if (field.key === 'identCode') continue;
      if (field.key === 'code') {
        var requiredCode = {};
        for (var property in field) {
          if (Object.prototype.hasOwnProperty.call(field, property)) requiredCode[property] = field[property];
        }
        requiredCode.required = true;
        variantForm.push(requiredCode);
      } else {
        variantForm.push(field);
      }
    }
  }
  if (category === '大型装备' || category === '小型装备') {
    variantColumns.splice(2, 0, { key: 'identCode', label: '识别码' });
    for (var formIndex = 0; formIndex < variantForm.length; formIndex++) {
      if (variantForm[formIndex].key === 'model') {
        variantForm.splice(formIndex, 0, {
          key: 'brand', label: '品牌', type: 'select',
          options: function () { return dictLabels('equipment_brand'); }
        });
        break;
      }
    }
    for (var columnIndex = 0; columnIndex < variantColumns.length; columnIndex++) {
      if (variantColumns[columnIndex].key === 'model') {
        variantColumns.splice(columnIndex, 0, { key: 'brand', label: '品牌' });
        break;
      }
    }
  }
  return {
    key: key, title: title, icon: icon || 'shield', endpoint: '/api/equipment',
    excelEndpoint: isGun ? '/api/equipment-gun' : '/api/equipment',
    clickKey: 'name', addLabel: '新增' + title,
    personLookup: { idCard: 'keeperIdCard', name: 'keeperName', pid: 'keeperId' },
    fixedFilter: { category: category },
    presetNew: { category: category },
    filters: [
      { key: 'name', label: '名称' },
      { key: 'status', label: '状态', type: 'select', options: function () { return dictLabels('equipment_status'); } }
    ],
    moreFilters: [
      { key: 'code', label: '编号' },
      { key: 'keeperName', label: '保管人' },
      { key: 'location', label: '存放位置', type: 'select', options: function () { return dictLabels('equipment_location'); } }
    ],
    columns: variantColumns,
    rowTitle: function (r) { return r.name; },
    form: variantForm,
    beforeSave: equipmentCfg.beforeSave
  };
}
var equipmentGunCfg = equipmentVariantCfg('equipment-gun', '枪支管理', '枪支');
var equipmentLargeCfg = equipmentVariantCfg('equipment-large', '大型装备', '大型装备', 'box');
var equipmentSmallCfg = equipmentVariantCfg('equipment-small', '小型装备', '小型装备', 'box');

var materialCfg = {
  key: 'material', title: '物资管理', icon: 'box', endpoint: '/api/material',
  clickKey: 'name', addLabel: '新增物资信息',
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

var VEHICLE_FAULT_SUB = {
  name: '历史故障',
  parentKey: 'vehicleId',
  endpoint: function (vehicleId) { return '/api/vehicle/' + vehicleId + '/fault-history'; },
  columns: [
    { key: 'faultDate', label: '故障时间', type: 'date', required: true, w: 130 },
    { key: 'description', label: '故障情况', required: true }
  ]
};

var vehicleCfg = {
  key: 'vehicle', title: '车辆管理', icon: 'truck', endpoint: '/api/vehicle', wide: true,
  clickKey: 'plateNo', addLabel: '新增车辆信息',
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
    { key: 'inspectionExpiry', label: '年检到期', render: function (r) { return r.inspectionExpiry || '—'; } },
    { key: 'equipDate', label: '装备时间', render: function (r) { return r.equipDate || '—'; } }
  ],
  rowTitle: function (r) { return r.plateNo; },
  form: [
    { section: '基本信息' },
    { key: 'plateNo', label: '车牌号', required: true },
    { key: 'vin', label: '车架号', placeholder: '17位车辆识别代码' },
    { key: 'engineNo', label: '发动机号' },
    { key: 'brand', label: '品牌', type: 'select', options: function () { return dictLabels('vehicle_brand'); } },
    { key: 'model', label: '型号', type: 'select', placeholder: '先选品牌',
      depends: { field: 'brand', dict: 'vehicle_model', parentDict: 'vehicle_brand' } },
    { key: 'type', label: '车型', type: 'select', options: function () { return dictLabels('vehicle_type'); } },
    { key: 'color', label: '车身颜色' },
    { key: 'status', label: '状态', type: 'select', required: true, options: function () { return dictLabels('vehicle_status'); } },
    { key: 'driverIdCard', label: '驾驶员身份证', placeholder: '输入后自动带出姓名' },
    { key: 'driverName', label: '驾驶员姓名', readonly: true, placeholder: '按身份证号自动获取' },
    { key: 'driverId', type: 'hidden' },
    { key: 'seats', label: '核定人数', type: 'number', step: '1' },
    { key: 'equipDate', label: '装备时间', type: 'date' },
    { key: 'inspectionExpiry', label: '年检到期日', type: 'date' },
    { key: 'remark', label: '备注', type: 'textarea', span: 'full' },
    { section: '历史故障' }
  ],
  beforeSave: function (data) {
    // driverIdCard/driverName 仅用于表单展示,不提交
    delete data.driverIdCard;
    delete data.driverName;
    return data;
  },
  subPanels: function (row) {
    var panel = subRecordsPanel('vehicle-fault', row && row.id, VEHICLE_FAULT_SUB);
    return {
      panels: { '历史故障': panel },
      staged: row ? null : { 'vehicle-fault': panel.__staged },
      stagedMeta: { 'vehicle-fault': { endpoint: VEHICLE_FAULT_SUB.endpoint, parentKey: VEHICLE_FAULT_SUB.parentKey } }
    };
  }
};

/** 节假日配色:同名节日永远同色(稳定哈希),日期命中节日也用该色 */
var HOLIDAY_COLOR_CACHE = {};
function holidayColor(name) {
  if (!name) { return ''; }
  if (HOLIDAY_COLOR_CACHE[name]) { return HOLIDAY_COLOR_CACHE[name]; }
  var palette = ['var(--c-danger, #d9463e)', 'var(--c-warn, #e8960e)', 'var(--c-ok, #2ea44f)', '#7048b7', '#0d8484', '#b26a00', 'var(--pri, #2f6fd6)', '#c2410c'];
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
  }).catch(function (err) { /* 忽略 */ });
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
  var q = '/api/leave?personnelId=' + encodeURIComponent(rw.personnelId);
  var ready = Object.keys(HOLIDAY_MAP).length ? Promise.resolve() : loadHolidayMap();
  ready.then(function () {
  loadAllRows(q).then(function (d) {
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
    body.appendChild(h('div', { style: 'display:flex;gap:16px;margin-bottom:16px;flex-wrap:wrap' },
      h('span', { style: 'background:#eef4ff;color:var(--pri-dark, #16437e);border-radius:8px;padding:8px 14px;font-size:13px', text: '累计休假 ' + rows.length + ' 次' }),
      h('span', { style: 'background:#e9f7ef;color:#22633c;border-radius:8px;padding:8px 14px;font-size:13px', text: '累计 ' + (Math.round(totalDays * 10) / 10) + ' 天(含路途/节假日)' }),
      h('span', { style: 'background:#fdf1e7;color:#8a5a1a;border-radius:8px;padding:8px 14px;font-size:13px', text: yearNow + ' 年已休 ' + (Math.round(nowDays * 10) / 10) + ' 天' })));
    // 按年度分组(新→旧)
    var byYear = {};
    rows.forEach(function (x) { (byYear[x.year || '未填年度'] = byYear[x.year || '未填年度'] || []).push(x); });
    var years = Object.keys(byYear).sort(function (a, b) { return a < b ? 1 : -1; });
    years.forEach(function (y) {
      var list = byYear[y];
      var yDays = 0;
      list.forEach(function (x) { yDays += (Number(x.days) || 0) + (Number(x.travelDays) || 0) + (Number(x.holidayDays) || 0); });
      body.appendChild(h('div', { style: 'font-weight:600;color:var(--c-dark, #1d2c40);margin:12px 0 6px;font-size:14px', text: y + ' 年(' + list.length + ' 次 · 共 ' + (Math.round(yDays * 10) / 10) + ' 天)' }));
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
  clickKey: 'personnelName', addLabel: '新增休假记录',
  filters: [
    { key: 'personnelName', label: '姓名' },
    { key: 'idCard', label: '身份证号' },
    { key: 'leaveType', label: '休假类型', type: 'select', options: function () { return dictLabels('leave_type'); } }
  ],
  moreFilters: [
    { key: 'year', label: '年度', placeholder: '如 2026' },
    { key: 'startDate', label: '开始日期', type: 'date' },
    { key: 'endDate', label: '结束日期', type: 'date' }
  ],
  columns: [
    { key: 'departmentPath', label: '单位' },
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
    { key: 'reason', label: '事由' },
    { key: 'leaveLocation', label: '休假地点', render: function (r) { return esc(r.leaveLocation || '—'); } }
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
    { key: 'reason', label: '事由' },
    { key: 'leaveLocation', label: '休假地点', placeholder: '如：北京市', span: 'full' },
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

var phoneCfg = {
  key: 'phone', title: '手机管理', icon: 'smartphone', endpoint: '/api/phone',
  clickKey: 'personnelName', addLabel: '新增手机设备',
  personLookup: { idCard: 'idCard', name: 'personnelName', pid: 'personnelId' },
  filters: [
    { key: 'personnelName', label: '姓名' },
    { key: 'idCard', label: '身份证号' },
    { key: 'deviceType', label: '设备类型', type: 'select', options: function () { return dictLabels('device_type'); } },
    { key: 'brand', label: '品牌' }
  ],
  moreFilters: [
    { key: 'status', label: '状态', type: 'select', options: function () { return dictLabels('device_status'); } }
  ],
  columns: [
    { key: 'departmentPath', label: '单位' },
    { key: 'personnelName', label: '姓名' },
    { key: 'idCard', label: '身份证号', render: function (r) { return r.idCard || '—'; } },
    { key: 'deviceType', label: '设备类型' },
    { key: 'brand', label: '品牌' },
    { key: 'model', label: '型号' },
    { key: 'phoneNumber', label: '手机号', render: function (r) { return r.phoneNumber || '—'; } },
    { key: 'imei1', label: 'IMEI1', render: function (r) { return r.imei1 || '—'; } },
    { key: 'status', label: '状态', render: function (r) { return badge(r.status); } },
    { key: 'purchaseDate', label: '购置日期', render: function (r) { return r.purchaseDate || '—'; } }
  ],
  rowTitle: function (r) { return r.personnelName + ' · ' + (r.brand || '') + ' ' + (r.model || ''); },
  delTip: function (r) { return '确定删除「' + r.personnelName + '」的「' + (r.brand || '') + (r.model || '') + '」吗?'; },
  form: [
    { key: 'idCard', label: '身份证号', required: true, placeholder: '输入后自动带出姓名' },
    { key: 'personnelName', label: '姓名', readonly: true, placeholder: '按身份证号自动获取' },
    { key: 'personnelId', type: 'hidden', required: true },
    { key: 'deviceType', label: '设备类型', type: 'select', required: true, options: function () { return dictLabels('device_type'); } },
    { key: 'brand', label: '品牌', type: 'select', required: true, options: function () { return dictLabels('phone_brand'); } },
    { key: 'model', label: '型号', placeholder: '如 小米17 Pro' },
    { key: 'serialNo', label: '序列号', placeholder: '设备SN码' },
    { key: 'imei1', label: 'IMEI1', placeholder: '15位国际移动设备识别码' },
    { key: 'imei2', label: 'IMEI2', placeholder: '双卡设备的第二码' },
    { key: 'phoneNumber', label: '手机号', placeholder: '该设备使用的号码' },
    { key: 'purchaseDate', label: '购置日期', type: 'date' },
    { key: 'status', label: '状态', type: 'select', required: true, options: function () { return dictLabels('device_status'); } },
    { key: 'remark', label: '备注', type: 'textarea', span: 'full' }
  ],
  beforeSave: function (data) {
    delete data.idCard;
    delete data.personnelName;
    return data;
  }
};

var fitnessCfg = {
  key: 'fitness', title: '体能考核', icon: 'activity', endpoint: '/api/fitness',
  summaryView: true, summaryEndpoint: '/api/fitness/summary', exportParams: 'wide=true',
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
    { key: 'testDate', label: '考核日期', type: 'date', required: true },
    { key: 'idCard', label: '身份证号', required: true, placeholder: '输入后自动带出姓名/年龄' },
    { key: 'personnelName', label: '姓名', readonly: true, placeholder: '按身份证号自动获取' },
    { key: 'age', label: '年龄', readonly: true, placeholder: '按考核日期计算' },
    { key: 'personnelId', type: 'hidden', required: true },
    { key: 'item', label: '考核项目', type: 'select', required: true, options: function () { return Object.keys(FITNESS_MATRIX_CACHE); } },
    { key: 'score', label: '成绩', placeholder: "如 12'30 或 45 次", hint: '输入后自动算分数和评定' },
    { key: 'points', label: '分数', readonly: true, placeholder: '自动计算' },
    { key: 'grade', label: '评定', readonly: true, placeholder: '自动计算' },
    { key: 'remark', label: '备注', type: 'textarea', span: 'full' }
  ],
  personLookup: { idCard: 'idCard', name: 'personnelName', pid: 'personnelId' },
  formSetup: function (form, row) {
    var dateEl = form.inputs['testDate'];
    var idEl = form.inputs['idCard'];
    var ageEl = form.inputs['age'];
    function calcAge(bd) {
      var m = /^(\d{4})-(\d{2})-(\d{2})/.exec(bd || '');
      if (!m) { return ''; }
      var ref = dateEl && dateEl.value ? dateEl.value : '';
      var ty, tm, td;
      if (ref.length >= 10) {
        ty = +ref.slice(0, 4); tm = +ref.slice(5, 7); td = +ref.slice(8, 10);
      } else {
        var now = new Date();
        ty = now.getFullYear(); tm = now.getMonth() + 1; td = now.getDate();
      }
      var age = ty - (+m[1]) - ((tm < +m[2] || (tm === +m[2] && td < +m[3])) ? 1 : 0);
      return (age >= 0 && age < 130) ? age : '';
    }
    var lookupGeneration = 0;
    function lookup() {
      var generation = ++lookupGeneration;
      var v = String(idEl.value || '').trim();
      form.inputs['personnelId'].value = '';
      form.inputs['personnelName'].value = '';
      if (ageEl) ageEl.value = '';
      if (!v) return;
      GET('/api/personnel/by-idcard?idCard=' + encodeURIComponent(v)).then(function (p) {
        if (generation !== lookupGeneration || String(idEl.value || '').trim() !== v) return;
        if (p) {
          form.inputs['personnelId'].value = String(p.id);
          form.inputs['personnelName'].value = p.name || '';
          idEl.classList.remove('bad');
          idEl.title = p.departmentPath || p.departmentName || '';
          GET('/api/personnel/' + p.id).then(function (full) {
            if (generation !== lookupGeneration || String(idEl.value || '').trim() !== v) return;
            if (ageEl && full && full.birthday) {
              ageEl.value = calcAge(full.birthday);
              ageEl.dispatchEvent(new Event('change', { bubbles: true }));
            }
          }).catch(function (err) { /* 忽略 */ });
        } else {
          form.inputs['personnelId'].value = '';
          form.inputs['personnelName'].value = '';
          if (ageEl) { ageEl.value = ''; }
          idEl.classList.add('bad');
          idEl.title = '未找到对应人员';
        }
      }).catch(function (err) { /* 忽略 */ });
    }
    idEl.addEventListener('input', function () {
      lookupGeneration++;
      form.inputs['personnelId'].value = '';
      form.inputs['personnelName'].value = '';
      if (ageEl) ageEl.value = '';
    });
    idEl.addEventListener('change', lookup);
    if (String(idEl.value || '').trim()) { setTimeout(lookup, 60); }

    var scoreEl = form.inputs['score'];
    var ptsEl = form.inputs['points'];
    var gradeEl = form.inputs['grade'];
    var itemEl = form.inputs['item'];
    function calc() {
      var item = itemEl ? itemEl.value : '';
      var text = String(scoreEl.value || '').trim();
      if (!item || !text) {
        if (ptsEl) { ptsEl.value = ''; }
        if (gradeEl) { gradeEl.value = ''; }
        return;
      }
      var age = ageEl ? (parseInt(ageEl.value, 10) || null) : null;
      if (!GRADE_RULE) { loadGradeRule(); }
      var pts = calcFitnessGrade(item, text, age);
      if (pts) {
        if (ptsEl) { ptsEl.value = pts; }
        if (gradeEl) { gradeEl.value = gradeForPoints(pts); }
        scoreEl.classList.remove('bad');
      } else {
        if (ptsEl) { ptsEl.value = ''; }
        if (gradeEl) { gradeEl.value = ''; }
      }
    }
    scoreEl.addEventListener('change', calc);
    scoreEl.addEventListener('input', function () {
      clearTimeout(scoreEl._t);
      scoreEl._t = setTimeout(calc, 500);
    });
    if (itemEl && itemEl.addEventListener) { itemEl.addEventListener('change', calc); }
    if (ageEl && ageEl.addEventListener) { ageEl.addEventListener('change', function () { clearTimeout(ageEl._ct); ageEl._ct = setTimeout(calc, 200); }); }
    // 考核日期变化 → 重算年龄 → 重算分数
    if (dateEl && dateEl.addEventListener) { dateEl.addEventListener('change', function () {
      clearTimeout(dateEl._lt);
      dateEl._lt = setTimeout(function () {
        var card = String(idEl.value || '').trim();
        if (card) { lookup(); }
      }, 200);
    }); }
    if (row && row.score) { setTimeout(calc, 100); }
  },
  beforeSave: function (data) {
    delete data.idCard;
    delete data.personnelName;
    delete data.age;
    return data;
  }
};

/* ================= 军事考核(理论/射击/战术等,按分数评定) ================= */

var militaryCfg = {
  key: 'military', title: '军事考核', icon: 'flag', endpoint: '/api/military',
  clickKey: 'personnelName', addLabel: '新增考核记录',
  personLookup: { idCard: 'idCard', name: 'personnelName', pid: 'personnelId' },
  filters: [
    { key: 'personnelName', label: '姓名' },
    { key: 'category', label: '类别', type: 'select', options: function () { return dictLabels('military_type'); } }
  ],
  moreFilters: [
    { key: 'item', label: '考核项目' },
    { key: 'grade', label: '评定', type: 'select', options: function () { return dictLabels('fitness_grade').length ? dictLabels('fitness_grade') : ['优秀', '良好', '及格', '不及格']; } },
    { key: 'startDate', label: '考核起', type: 'date' },
    { key: 'endDate', label: '考核止', type: 'date' }
  ],
  columns: [
    { key: 'personnelName', label: '姓名' },
    { key: 'idCard', label: '身份证号', render: function (r) { return r.idCard || '—'; } },
    { key: 'category', label: '考核类别' },
    { key: 'item', label: '考核项目' },
    { key: 'score', label: '成绩', render: function (r) { return r.score || '—'; } },
    { key: 'points', label: '分数' },
    { key: 'grade', label: '评定', render: function (r) { return badge(r.grade); } },
    { key: 'testDate', label: '考核日期' }
  ],
  rowTitle: function (r) { return r.personnelName + ' · ' + (r.item || ''); },
  form: [
    { key: 'idCard', label: '身份证号', required: true, placeholder: '输入后自动带出姓名' },
    { key: 'personnelName', label: '姓名', readonly: true, placeholder: '按身份证号自动获取' },
    { key: 'personnelId', type: 'hidden', required: true },
    { key: 'category', label: '考核类别', type: 'select', options: function () { return dictLabels('military_type'); } },
    { key: 'item', label: '考核项目', required: true, placeholder: '如:军事理论、射击、战术基础动作' },
    { key: 'score', label: '成绩', placeholder: '如:92分、良好、48环' },
    { key: 'points', label: '分数(0-100)', type: 'number', step: '0.5', hint: '填写后自动评定等级,可手动修改' },
    { key: 'grade', label: '评定', type: 'select', options: function () { return dictLabels('fitness_grade').length ? dictLabels('fitness_grade') : ['优秀', '良好', '及格', '不及格']; } },
    { key: 'testDate', label: '考核日期', type: 'date', required: true },
    { key: 'remark', label: '备注', type: 'textarea', span: 'full' }
  ],
  formSetup: function (form, row) {
    var ptsEl = form.inputs['points'];
    var gradeEl = form.inputs['grade'];
    if (ptsEl && gradeEl) {
      if (!GRADE_RULE) { loadGradeRule(); }
      function autoGrade() {
        var g = gradeForPoints(ptsEl.value);
        if (g) { gradeEl.value = g; }
      }
      ptsEl.addEventListener('input', function () { clearTimeout(ptsEl._gt); ptsEl._gt = setTimeout(autoGrade, 300); });
      ptsEl.addEventListener('change', autoGrade);
    }
  },
  beforeSave: function (data) {
    delete data.idCard;
    delete data.personnelName;
    if (data.points === '') { delete data.points; }
    return data;
  }
};

/* ================= 单位管理视图(树) ================= */

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
          class: 'btn btn-ghost', text: '去人员管理筛选此单位',
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
    // 全量分页加载:单位直属人员不再截断在前200条,人数多的单位也能完整查看
    loadAllRows('/api/personnel?departmentId=' + node.id).then(function (d) {
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
        body.appendChild(h('div', { class: 'empty-row', text: '该单位暂无直属人员' }));
      }
      if ((node.total || 0) > (node.count || 0)) {
        body.appendChild(h('div', {
          style: 'margin-top:12px;font-size:13px;color:var(--c-sub, #66788c)',
          text: '含下级单位共 ' + node.total + ' 人,此处仅列出直属人员;点击"去人员管理筛选此单位"可查看并维护。'
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
        class: 'name clickable', html: nameHtml, title: '点击查看该单位人员',
        onclick: function () { openDeptPersonnel(node); }
      }),
      node.leaderName ? chip('负责人 ' + node.leaderName, 'var(--c-sub, #66788c)') : null,
      chip('直属 ' + (node.count || 0) + ' 人', (node.count || 0) > 0 ? 'var(--pri, #2f6fd6)' : ''),
      hasKids && (node.total || 0) !== (node.count || 0) ? chip('含下级 ' + (node.total || 0) + ' 人', 'var(--c-ok, #2ea44f)') : null,
      hasKids ? chip(hasKids + ' 个子单位', '') : null,
      h('span', { class: 'acts' },
        (node.count || 0) > 0 ? h('button', { class: 'btn-link', text: '查看人员', onclick: function () { openDeptPersonnel(node); } }) : null,
        h('button', { class: 'btn-link', text: '加子级', onclick: function () { openForm(null, node.id); } }),
        h('button', { class: 'btn-link', text: '编辑', onclick: function () { openForm(node); } }),
        h('button', {
          class: 'btn-link danger', text: '删除',
          onclick: function () {
            confirmBox('确定删除单位「' + node.name + '」吗?若其下有子单位或人员将无法删除。', true).then(function (yes) {
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
      box.appendChild(h('div', { class: 'empty-row', text: '暂无单位,点击上方按钮创建' }));
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
      box.appendChild(h('div', { class: 'empty-row', text: '没有名称包含「' + kw + '」的单位' }));
    }
  }

  function renderStats() {
    statsEl.innerHTML = '';
    var unassigned = Math.max(0, stats.personTotal - stats.assigned);
    [
      { num: stats.depts, lbl: '单位总数', ic: 'building', bg: 'var(--c-pribg, #e8f0fd)', fg: 'var(--pri, #2f6fd6)' },
      { num: stats.personTotal, lbl: '人员总数', ic: 'users', bg: '#e9f7ef', fg: 'var(--c-ok, #2ea44f)' },
      { num: stats.assigned, lbl: '已分配单位', ic: 'check', bg: '#eef4ff', fg: 'var(--pri-dark, #16437e)' },
      { num: unassigned, lbl: '未分配单位', ic: 'shield', bg: '#fdf1e7', fg: '#e8743b' }
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
      { key: 'name', label: '单位名称', required: true },
      { key: 'parentId', label: '上级单位', type: node && !hasGlobalAccess() ? 'hidden' : 'dept', required: !node && !hasGlobalAccess() },
      { key: 'leaderId', label: '负责人', type: 'personnel' },
      { key: 'sort', label: '排序号', type: 'number', step: '1', placeholder: '小的排前面' },
      { key: 'remark', label: '备注', span: 'full' }
    ], node
      ? { name: node.name, parentId: node.parentId, leaderId: node.leaderId > 0 ? node.leaderId : null, sort: node.sort, remark: node.remark }
      : { parentId: parentId || null });
    if (!hasGlobalAccess()) form.el.appendChild(h('p', { class: 'account-hint',
      text: node ? '子账号不可变更单位的上级关系。' : '只能在本人授权范围内的单位下新建子单位。' }));
    var m = openModal({
      title: node ? '编辑单位 · ' + node.name : '新增单位',
      body: form.el,
      footer: [
        h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m.close(); } }),
        h('button', {
          class: 'btn btn-primary', text: '保存',
          onclick: function () {
            var data = form.get();
            if (!data) return;
            if (data.parentId === '') data.parentId = null;
            if (!hasGlobalAccess()) {
              if (node) data.parentId = node.parentId || 0; // 剪裁树授权根的 parent=0 由服务端保留原父级
              else if (!Ctx.deptFlat.some(function (dept) { return String(dept.id) === String(data.parentId); })) {
                form.setError('请选择本人授权范围内的上级单位'); return;
              }
            }
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
    type: 'text', class: 'dept-search', placeholder: '搜索单位名称…',
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
    root.appendChild(h('div', { class: 'panel', style: 'margin-top:16px' },
      h('div', { class: 'dept-toolbar' },
        h('button', { class: 'btn btn-primary', text: '新增单位信息', onclick: function () { openForm(null, null); } }),
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

    var groupCounts = {};
    ordered.forEach(function (d) {
      var key = String(d.parentId || 0);
      groupCounts[key] = (groupCounts[key] || 0) + 1;
    });
    var table = h('table', { class: 'grid' });
    table.innerHTML = '<thead><tr><th class="dict-drag-col" style="width:32px"></th><th>选项内容</th><th>排序</th><th>状态</th><th>备注</th><th class="action-col" style="width:150px">操作</th></tr></thead><tbody>' +
      (ordered.length ? ordered.map(function (d, ri) {
        var isChild = !!d.parentId;
        var childCount = globalChildCount(d);
        var groupKey = String(d.parentId || 0);
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
        var dragCell = groupCounts[groupKey] > 1
          ? '<td class="dict-sort-cell" title="按住拖动排序"><span class="dict-drag-handle" aria-hidden="true">⠿</span></td>'
          : '<td class="dict-drag-col"></td>';
        return '<tr' + rowFlash + ' data-id="' + d.id + '" data-parent="' + groupKey + '">' + dragCell +
          '<td>' + label + '</td><td>' + esc(d.sort) + '</td>' +
          '<td>' + (d.enabled === false ? '<span class="badge gray">停用</span>' : '<span class="badge green">启用</span>') + '</td>' +
          '<td>' + esc(d.remark) + '</td>' +
          '<td class="actions" style="width:140px;white-space:nowrap">' +
          (isChild ? '' : '<button class="btn-link" data-sub="' + d.id + '">添加下级</button>') +
          '<button class="btn-link" data-edit="' + d.id + '">编辑</button>' +
          '<button class="btn-link danger" data-del="' + d.id + '">删除</button></td></tr>';
      }).join('') : '<tr><td colspan="6" class="empty-row">该类型暂无选项</td></tr>') + '</tbody>';
    pendingHighlightPid = null;
    var dictDragRow = null, dictDropRow = null, dictDropAfter = false, dictDragMoved = false, dictOrderSaving = false;
    function clearDictDropMarks() {
      $all('tr.dict-drop-before, tr.dict-drop-after', table).forEach(function (row) {
        row.classList.remove('dict-drop-before', 'dict-drop-after');
      });
    }
    function endDictDrag(commit) {
      var moved = dictDragRow;
      var parentKey = moved ? moved.getAttribute('data-parent') : '';
      if (moved) moved.classList.remove('dict-dragging');
      clearDictDropMarks();
      dictDragRow = null;
      dictDropRow = null;
      if (!commit || !moved || !dictDragMoved) { dictDragMoved = false; return; }
      dictDragMoved = false;
      var ids = [];
      $all('tbody tr[data-id]', table).forEach(function (row) {
        if (row.getAttribute('data-parent') === parentKey) ids.push(Number(row.getAttribute('data-id')));
      });
      if (ids.length < 2 || dictOrderSaving) return;
      dictOrderSaving = true;
      PUT('/api/dict/reorder', { dictType: currentType, ids: ids }).then(function () {
        toast('排序已保存');
        return refreshDicts();
      }).then(renderAll).catch(function (e) {
        toast(e.message || '排序保存失败', 'err');
        refreshDicts().then(renderAll);
      }).then(function () { dictOrderSaving = false; });
    }
    $all('tbody tr[data-id]', table).forEach(function (tr) {
      var groupKey = tr.getAttribute('data-parent');
      if ((groupCounts[groupKey] || 0) < 2) return;
      tr.setAttribute('draggable', 'true');
      tr.addEventListener('dragstart', function (e) {
        if (dictOrderSaving) { if (e.preventDefault) e.preventDefault(); return; }
        dictDragRow = tr;
        dictDragMoved = false;
        dictDropRow = null;
        tr.classList.add('dict-dragging');
        if (e.dataTransfer) {
          try {
            e.dataTransfer.effectAllowed = 'move';
            e.dataTransfer.setData('text/plain', tr.getAttribute('data-id'));
          } catch (err) { /* 忽略不支持数据传输的浏览器 */ }
        }
      });
      tr.addEventListener('dragover', function (e) {
        if (!dictDragRow || dictDragRow === tr) return;
        if (dictDragRow.getAttribute('data-parent') !== groupKey) {
          clearDictDropMarks();
          dictDropRow = null;
          return;
        }
        if (e.preventDefault) e.preventDefault();
        if (e.dataTransfer) { try { e.dataTransfer.dropEffect = 'move'; } catch (err) { /* 忽略 */ } }
        var rect = tr.getBoundingClientRect();
        var middle = rect.top + (rect.bottom - rect.top) / 2;
        dictDropAfter = typeof e.clientY === 'number' && e.clientY > middle;
        clearDictDropMarks();
        tr.classList.add(dictDropAfter ? 'dict-drop-after' : 'dict-drop-before');
        dictDropRow = tr;
      });
      tr.addEventListener('drop', function (e) {
        if (!dictDragRow || !dictDropRow || dictDragRow === dictDropRow) return;
        if (e.preventDefault) e.preventDefault();
        if (e.stopPropagation) e.stopPropagation();
        var reference = dictDropAfter ? dictDropRow.nextSibling : dictDropRow;
        while (reference && reference.nodeType !== 1) reference = reference.nextSibling;
        dictDropRow.parentNode.insertBefore(dictDragRow, reference);
        dictDragMoved = true;
        endDictDrag(true);
      });
      tr.addEventListener('dragend', function () { endDictDrag(false); });
    });
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
        h('button', { class: 'btn btn-primary', text: '新增字典选项', onclick: function () { openForm(null); } })),
      h('div', { class: 'table-wrap' }, table)));
  }

  /** item=编辑对象;parentItem=新增下级时的父项 */
  function openForm(item, parentItem) {
    var items = (typeGroup(currentType) || {}).items || [];
    var nextSort = 1;
    items.forEach(function (d) {
      var sort = Number(d.sort);
      if (isFinite(sort) && sort >= nextSort) nextSort = sort + 1;
    });
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
      { key: 'sort', label: '排序号', type: 'number', step: '1', hint: '数字越小越靠前；也可在列表中拖动调整' },
      { key: 'enabled', label: '状态', type: 'select', required: true, options: [{ v: '1', l: '启用' }, { v: '0', l: '停用' }] },
      { key: 'remark', label: '备注', span: 'full' }
    ];
    if (!hasHierarchy) fields.splice(1, 1); // 该类型尚无下级时不显示上级选择

    var preset = item
      ? { dictLabel: item.dictLabel, sort: item.sort, enabled: item.enabled === false ? '0' : '1', remark: item.remark }
      : { enabled: '1', sort: nextSort };
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
            if (data.sort === null) data.sort = item ? item.sort : nextSort;
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

var DASH_COLORS = ['var(--pri, #2f6fd6)', 'var(--c-ok, #2ea44f)', 'var(--c-warn, #e8960e)', '#9333ea', 'var(--c-danger, #d9463e)', '#0d9488', '#b26a00', 'var(--c-sub, #66788c)'];

/** 仪表盘数据维度:cat=所属模块 group=接口分组 trend=按年趋势 gauge=仪表 drill=点击下钻 */
var DASH_DIMS = {
  department:       { cat: '人员管理', label: '各单位人数（含下级）', hierarchy: true, group: 'department', unit: '人', drill: { api: '/api/personnel', param: 'departmentId' } },
  locationPoint:    { cat: '人员管理', label: '所在点位', group: 'locationPoint', unit: '人', drill: { api: '/api/personnel', param: 'locationPoint' } },
  status:           { cat: '人员管理', label: '人员状态',    group: 'status', drill: { api: '/api/personnel', param: 'status' } },
  driverLicense:    { cat: '人员管理', label: '驾驶证持有情况', group: 'driverLicense', unit: '人', drill: { api: '/api/personnel', param: 'driverLicense' } },
  gender:           { cat: '人员管理', label: '性别分布',    group: 'gender', drill: { api: '/api/personnel', param: 'gender' } },
  category:         { cat: '人员管理', label: '人员类别',    group: 'category', drill: { api: '/api/personnel', param: 'category' } },
  rank:             { cat: '人员管理', label: '军衔分布',    group: 'rank', unit: '人', drill: { api: '/api/personnel', param: 'rank' } },
  education:        { cat: '人员管理', label: '学历分布',    group: 'education', drill: { api: '/api/personnel', param: 'education' } },
  politicalStatus:  { cat: '人员管理', label: '政治面貌',    group: 'politicalStatus', drill: { api: '/api/personnel', param: 'politicalStatus' } },
  nativePlace:      { cat: '人员管理', label: '籍贯省份分布', group: 'nativePlace', unit: '人', drill: { api: '/api/personnel', param: 'nativePlace' } },
  enlistPlace:      { cat: '人员管理', label: '入伍地省份分布', group: 'enlistPlace', unit: '人', drill: { api: '/api/personnel', param: 'enlistPlace' } },
  entryTrend:       { cat: '人员管理', label: '入伍趋势',    trend: true, unit: '人', drill: { api: '/api/personnel', param: 'entryYear' } },
  onDuty:           { cat: '人员管理', label: '人员在位率',  gauge: true },
  actualDuty:       { cat: '人员管理', label: '实际在岗分布', group: 'actualDuty', unit: '人', drill: { api: '/api/personnel', param: 'actualDepartmentId' } },
  equipmentCategory:{ cat: '装备管理', label: '装备类别',    group: 'equipmentCategory', unit: '件', drill: { api: '/api/equipment', param: 'category' } },
  equipmentStatus:  { cat: '装备管理', label: '装备状态',    group: 'equipmentStatus', drill: { api: '/api/equipment', param: 'status' } },
  materialCategory: { cat: '物资管理', label: '物资分类',    group: 'materialCategory', drill: { api: '/api/material', param: 'category' } },
  vehicleStatus:    { cat: '车辆管理', label: '车辆状态',    group: 'vehicleStatus', drill: { api: '/api/vehicle', param: 'status' } },
  vehicleType:      { cat: '车辆管理', label: '车辆类型',    group: 'vehicleType', unit: '辆', drill: { api: '/api/vehicle', param: 'type' } },
  vehicleBrand:     { cat: '车辆管理', label: '车辆品牌',    group: 'vehicleBrand', unit: '辆', drill: { api: '/api/vehicle', param: 'brand' } },
  leaveType:        { cat: '休假管理', label: '今年休假类型', group: 'leaveType', unit: '次', drill: { api: '/api/leave', param: 'leaveType' } },
  fitnessGrade:     { cat: '体能考核', label: '今年体能评定', group: 'fitnessGrade', drill: { api: '/api/fitness', param: 'grade' } }
};

/** 图表类型中文名 */
var DASH_KINDS = { hierarchy: '单位层级条形图', bar: '条形图', donut: '环形图', rose: '玫瑰图', treemap: '树状图', columns: '柱状图', line: '折线图', gauge: '仪表图' };

/** 每个维度可选的图表类型 */
function kindsForDim(dim) {
  if (dim.hierarchy) { return ['hierarchy']; }
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
  { dim: 'department', kind: 'hierarchy', size: 'm' },
  { dim: 'locationPoint', kind: 'bar', size: 'm' },
  { dim: 'status', kind: 'donut', size: 'm' },
  { dim: 'entryTrend', kind: 'columns', size: 'l' },
  { dim: 'leaveType', kind: 'bar', size: 'm' },
  { dim: 'driverLicense', kind: 'donut', size: 'm' }
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
  (Array.isArray(saved) ? saved : []).forEach(function (w) {
    if ((w && w.dim === 'department') || (typeof w === 'string' && DASH_OLD_WIDGET[w] && DASH_OLD_WIDGET[w][0] === 'department') ||
        (w && w.id && DASH_OLD_WIDGET[w.id] && DASH_OLD_WIDGET[w.id][0] === 'department')) {
      // 累计单位数不可用面积/环形相加；旧布局仅迁移此卡片，保留顺序、尺寸及其余指标。
      out.push({ dim: 'department', kind: 'hierarchy', size: w.size === 's' || w.size === 'l' ? w.size : 'm' });
    } else if (typeof w === 'string' && DASH_OLD_WIDGET[w]) {
      out.push({ dim: DASH_OLD_WIDGET[w][0], kind: DASH_OLD_WIDGET[w][1], size: 'm' });
    } else if (w && w.dim && DASH_DIMS[w.dim] && kindsForDim(DASH_DIMS[w.dim]).indexOf(w.kind) >= 0) {
      out.push({ dim: w.dim, kind: w.kind, size: w.size === 's' || w.size === 'l' ? w.size : 'm' });
    } else if (w && w.id && DASH_OLD_WIDGET[w.id]) {
      out.push({ dim: DASH_OLD_WIDGET[w.id][0], kind: DASH_OLD_WIDGET[w.id][1], size: w.size === 's' || w.size === 'l' ? w.size : 'm' });
    }
  });
  return out;
}

/** 在现有数组偏好中保存一次迁移标记；删除驾驶证卡后也保留，不新增服务端偏好键。 */
function markDashDriverLayout(widgets) {
  widgets.forEach(function (w) { delete w.driverLicenseDefaultVersion; });
  if (widgets.length) widgets[0].driverLicenseDefaultVersion = 1;
  return widgets;
}

/** 本地和远端使用同一规则：仅未处理的精确旧默认补一张卡，自定义布局原样保留。 */
function dashDriverLayout(saved) {
  var widgets = dashMigrate(saved);
  var previous = [
    { dim: 'department', kind: 'hierarchy', size: 'm' },
    { dim: 'locationPoint', kind: 'bar', size: 'm' },
    { dim: 'status', kind: 'donut', size: 'm' },
    { dim: 'entryTrend', kind: 'columns', size: 'l' },
    { dim: 'leaveType', kind: 'bar', size: 'm' }
  ];
  var handled = Array.isArray(saved) && saved.some(function (w) { return w && w.driverLicenseDefaultVersion === 1; });
  var exact = Array.isArray(saved) && saved.length === previous.length && widgets.length === previous.length;
  if (exact) {
    exact = saved.every(function (w, i) {
      // 先验证原条目，避免无效条目经规范化后伪装成旧默认。
      var legacy = typeof w === 'string' ? DASH_OLD_WIDGET[w] : w && w.id ? DASH_OLD_WIDGET[w.id] : null;
      var valid = !!legacy || (w && w.dim && DASH_DIMS[w.dim] &&
        (w.dim === 'department' ? ['hierarchy', 'bar', 'donut', 'rose', 'treemap'].indexOf(w.kind) >= 0 : kindsForDim(DASH_DIMS[w.dim]).indexOf(w.kind) >= 0));
      if (w && typeof w === 'object' && w.size != null && ['s', 'm', 'l'].indexOf(w.size) < 0) valid = false;
      return valid && widgets[i].dim === previous[i].dim && widgets[i].kind === previous[i].kind && widgets[i].size === previous[i].size;
    });
  }
  if (!handled && exact) widgets.push({ dim: 'driverLicense', kind: 'donut', size: 'm' });
  return markDashDriverLayout(widgets);
}

/** 每个真实层级使用独立色相；未分配不属于树，固定灰色。 */
function departmentDepthColor(depth) {
  return 'hsl(' + ((210 + Math.max(0, depth) * 137.508) % 360).toFixed(2) + ',58%,43%)';
}

/** 单位接口已经是稳定树前序，不能按累计人数排序、截前15项或把各行相加。 */
function drawDepartmentHierarchy(items) {
  if (!items || !items.length) { return h('div', { class: 'empty-row', text: '暂无数据' }); }
  var box = h('div', { class: 'department-hierarchy' });
  var max = 1;
  items.forEach(function (item) {
    if (+item.count > max) max = +item.count;
  });
  items.forEach(function (item) {
    var isUnassigned = String(item.id) === '0';
    var depth = Math.max(0, +item.depth || 0);
    var path = item.path || item.name || '未分配';
    var detail = path + (isUnassigned ? '' : ' [ID ' + item.id + ']') + '；含下级 ' + item.count + ' 人，直属 ' + item.directCount + ' 人';
    var attrs = { type: 'button', class: 'department-hierarchy-row' + (isUnassigned ? ' unassigned' : ' drillable'),
      title: detail, 'data-department-id': String(item.id), 'data-depth': String(depth),
      style: 'padding-left:' + (8 + Math.min(depth, 8) * 12) + 'px' };
    if (!isUnassigned && +item.id > 0) {
      attrs['data-api'] = '/api/personnel';
      attrs['data-param'] = 'departmentId';
      attrs['data-val'] = String(item.id);
      attrs['data-title'] = path;
    } else { attrs.disabled = true; }
    box.appendChild(h('button', attrs,
      h('span', { class: 'department-hierarchy-label', text: path }),
      h('span', { class: 'department-hierarchy-track' }, h('span', { style: 'display:block;height:100%;width:' + Math.max(0, +item.count / max * 100) + '%;background:' + (isUnassigned ? '#8a97a8' : departmentDepthColor(depth)) })),
      h('b', { class: 'department-hierarchy-count', text: item.count + ' 人' })));
  });
  return box;
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
      // 键盘可达:条形可聚焦,Enter/Space 触发与点击相同的下钻(属性名用小写,与其余 data-* 一致)
      attrs['tabindex'] = '0';
      attrs['role'] = 'button';
      attrs['aria-label'] = drill.title + ' - ' + x.name;
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
      (val != null ? ' class="seg" tabindex="0" role="button" aria-label="' + esc(drill.title + ' - ' + x.name) + '" data-api="' + drill.api + '" data-param="' + drill.param + '" data-val="' + esc(String(val)) + '" data-title="' + esc(drill.title + ' - ' + x.name) + '"' : '') +
      '><title>' + esc(x.name) + ': ' + x.count + (val != null ? '(点击查看)' : '') + '</title></circle>');
    deg += x.count / total * 360;
  });
  svg.push('<text x="75" y="71" text-anchor="middle" font-size="21" font-weight="700" fill="var(--c-dark, #1d2c40)">' + total + '</text>');
  svg.push('<text x="75" y="90" text-anchor="middle" font-size="11" fill="var(--c-muted, #8a97a8)">总计</text>');
  svg.push('</svg>');
  var legend = h('div', { class: 'donut-legend' });
  data.forEach(function (x, i) {
    var pct = Math.round(x.count / total * 100);
    legend.appendChild(h('div', { class: 'row' },
      h('span', { style: 'width:10px;height:10px;border-radius:2px;background:' + DASH_COLORS[i % DASH_COLORS.length] + ';flex:none' }),
      h('span', { style: 'flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap', title: x.name, text: x.name }),
      h('b', { style: 'color:var(--c-dark, #1d2c40)', text: String(x.count) }),
      h('span', { style: 'color:var(--c-muted, #8a97a8);width:40px;text-align:right;flex:none', text: pct + '%' })));
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
    svg.push('<text x="' + (padL - 6) + '" y="' + (+y + 4) + '" text-anchor="end" font-size="10" fill="var(--c-muted, #8a97a8)">' + Math.round(max * f) + '</text>');
  });
  items.forEach(function (x, i) {
    var cx = padL + step * (i + 0.5);
    var y = yAt(x.count);
    var hgt = (H - padB) - y;
    var val = drill ? drill.valOf(String(x.name)) : null;
    svg.push('<rect x="' + (cx - bw / 2).toFixed(1) + '" y="' + y.toFixed(1) + '" width="' + bw.toFixed(1) + '" height="' + Math.max(hgt, 1).toFixed(1) + '" rx="2" fill="' + PRI() + '" opacity="0.88"' +
      (val != null ? ' class="seg" tabindex="0" role="button" aria-label="' + esc(drill.title + ' - ' + x.name) + '" data-api="' + drill.api + '" data-param="' + drill.param + '" data-val="' + esc(String(val)) + '" data-title="' + esc(drill.title + ' - ' + x.name) + '"' : '') +
      '><title>' + esc(x.name) + '年: ' + x.count + ' ' + (unit || '') + (val != null ? '(点击查看)' : '') + '</title></rect>');
    svg.push('<text x="' + cx.toFixed(1) + '" y="' + (y - 4).toFixed(1) + '" text-anchor="middle" font-size="10" fill="var(--c-body, #445262)" pointer-events="none">' + x.count + '</text>');
    svg.push('<text x="' + cx.toFixed(1) + '" y="' + (H - padB + 16) + '" text-anchor="middle" font-size="9.5" fill="var(--c-muted, #8a97a8)" pointer-events="none">' + esc(String(x.name).slice(2)) + '</text>');
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
    svg.push('<text x="' + (padL - 6) + '" y="' + (+y + 4) + '" text-anchor="end" font-size="10" fill="var(--c-muted, #8a97a8)">' + Math.round(max * f) + '</text>');
  });
  svg.push('<polygon points="' + padL + ',' + (H - padB) + ' ' + pts.join(' ') + ' ' + (W - padR) + ',' + (H - padB) + '" fill="' + PRI() + '" opacity="0.10"/>');
  svg.push('<polyline points="' + pts.join(' ') + '" fill="none" stroke="' + PRI() + '" stroke-width="2.2" stroke-linejoin="round" stroke-linecap="round"/>');
  items.forEach(function (x, i) {
    var val = drill ? drill.valOf(String(x.name)) : null;
    svg.push('<circle cx="' + xAt(i).toFixed(1) + '" cy="' + yAt(x.count).toFixed(1) + '" r="4.5" fill="' + PRI() + '" stroke="#fff" stroke-width="1.5"' +
      (val != null ? ' class="seg" tabindex="0" role="button" aria-label="' + esc(drill.title + ' - ' + x.name) + '" data-api="' + drill.api + '" data-param="' + drill.param + '" data-val="' + esc(String(val)) + '" data-title="' + esc(drill.title + ' - ' + x.name) + '"' : '') +
      '><title>' + esc(x.name) + '年: ' + x.count + ' ' + (unit || '') + (val != null ? '(点击查看)' : '') + '</title></circle>');
    svg.push('<text x="' + xAt(i).toFixed(1) + '" y="' + (H - padB + 15) + '" text-anchor="middle" font-size="9.5" fill="var(--c-muted, #8a97a8)" pointer-events="none">' + esc(String(x.name).slice(2)) + '</text>');
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
      (val != null ? ' tabindex="0" role="button" aria-label="' + esc(drill.title + ' - ' + cell.c.name) + '" data-api="' + drill.api + '" data-param="' + drill.param + '" data-val="' + esc(String(val)) + '" data-title="' + esc(drill.title + ' - ' + cell.c.name) + '"' : '') + '>');
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
      (val != null ? ' class="seg" tabindex="0" role="button" aria-label="' + esc(drill.title + ' - ' + x.name) + '" data-api="' + drill.api + '" data-param="' + drill.param + '" data-val="' + esc(String(val)) + '" data-title="' + esc(drill.title + ' - ' + x.name) + '"' : '') +
      '><title>' + esc(x.name) + ': ' + x.count + '(' + Math.round(x.count / total * 100) + '%)' + (val != null ? '(点击查看)' : '') + '</title></path>');
  });
  svg.push('<circle cx="' + cx + '" cy="' + cy + '" r="22" fill="#fff"/>');
  svg.push('<text x="' + cx + '" y="' + (cy - 1) + '" text-anchor="middle" font-size="17" font-weight="700" fill="var(--c-dark, #1d2c40)">' + total + '</text>');
  svg.push('<text x="' + cx + '" y="' + (cy + 14) + '" text-anchor="middle" font-size="10" fill="var(--c-muted, #8a97a8)">总计</text>');
  svg.push('</svg>');
  var legend = h('div', { class: 'donut-legend' });
  data.forEach(function (x, i) {
    legend.appendChild(h('div', { class: 'row' },
      h('span', { style: 'width:10px;height:10px;border-radius:2px;background:' + DASH_COLORS[i % DASH_COLORS.length] + ';flex:none' }),
      h('span', { style: 'flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap', title: x.name, text: x.name }),
      h('b', { style: 'color:var(--c-dark, #1d2c40)', text: String(x.count) })));
  });
  return h('div', { class: 'donut-box' }, h('div', { html: svg.join('') }), legend);
}

/** 仪表图:人员在职率等;圆环可点击下钻 */
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
  var color = pct >= 95 ? 'var(--c-ok, #2ea44f)' : pct >= 80 ? 'var(--pri, #2f6fd6)' : 'var(--c-warn, #e8960e)';
  var val = drill ? drill.valOf('在职') : null;
  var svg = ['<svg viewBox="0 0 240 160" style="width:100%;max-width:280px;display:block;margin:0 auto">'];
  svg.push('<path d="' + arc(start, start + sweep) + '" fill="none" stroke="var(--c-bg, #edf1f6)" stroke-width="' + SW + '" stroke-linecap="round"/>');
  if (pct > 0) {
    svg.push('<path d="' + arc(start, start + sweep * pct / 100) + '" fill="none" stroke="' + color + '" stroke-width="' + SW + '" stroke-linecap="round"' +
      (val != null ? ' class="seg" tabindex="0" role="button" aria-label="' + esc(drill.title) + '" data-api="' + drill.api + '" data-param="' + drill.param + '" data-val="' + esc(String(val)) + '" data-title="' + esc(drill.title) + '"' : '') +
      '><title>在位 ' + onDuty + ' / 共 ' + total + '(' + pct + '%)' + (val != null ? '(点击查看在位名单)' : '') + '</title></path>');
  }
  svg.push('<text x="' + cx + '" y="' + (cy - 6) + '" text-anchor="middle" font-size="30" font-weight="700" fill="var(--c-dark, #1d2c40)">' + pct + '%</text>');
  svg.push('<text x="' + cx + '" y="' + (cy + 16) + '" text-anchor="middle" font-size="11.5" fill="var(--c-muted, #8a97a8)">在位 ' + onDuty + ' / 共 ' + total + '</text>');
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
  if (api === '/api/personnel' && (param === 'departmentId' || param === 'locationPoint' || param === 'driverLicense' || param === 'actualDepartmentId')) {
    if (param === 'driverLicense' && val !== 'yes' && val !== 'no') return;
    if ((param === 'departmentId' && !(+val > 0)) || val == null || val === '' || val === '未填写') return;
    // 实际在岗下钻按实际单位精确匹配,不挂编制的下级包含标志
    if (param === 'actualDepartmentId' && !(+val > 0)) return;
    location.hash = 'personnel?' + param + '=' + encodeURIComponent(val) + (param === 'departmentId' ? '&includeDescendants=true' : '');
    return;
  }
  var params = {};
  if (param === 'entryYear') {
    params.entryDateStart = val + '-01-01';
    params.entryDateEnd = val + '-12-31';
  } else {
    params[param] = val;
  }
  // 年度维度与仪表盘统计口径对齐:休假按开始日期在本年,体测按考核日期在本年;不带年份会混入往年记录
  var year = new Date().getFullYear();
  if (api === '/api/leave' && param === 'leaveType') {
    params.startDate = year + '-01-01';
    params.startDateMax = year + '-12-31';
  } else if (api === '/api/fitness' && param === 'grade') {
    params.startDate = year + '-01-01';
    params.endDate = year + '-12-31';
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
  // 后端单页上限200:逐页取全量,避免只取首页漏记录(原 size=500 会被后端重置)
  function loadDrillPage(page, acc) {
    GET(api + '?' + qs + '&page=' + page + '&size=200').then(function (d) {
      var rows = acc.concat((d && d.list) || []);
      var total = d && d.total != null ? +d.total : rows.length;
      if (rows.length && rows.length < total && page < 200) { loadDrillPage(page + 1, rows); return; }
      body.innerHTML = '';
      var table = h('table', { class: 'grid', style: 'width:100%' });
      var thead = h('tr', {});
      cols.forEach(function (c) { thead.appendChild(h('th', { text: c[1] })); });
      table.appendChild(h('thead', {}, thead));
      var tbody = h('tbody');
      rows.forEach(function (r) {
        var tr = h('tr', {});
        cols.forEach(function (c) {
          var v = c[0] === 'departmentName' ? (r.departmentPath || r.departmentName) : r[c[0]];
          // 单位路径显示与全局一致去斜杠
          if (c[0] === 'departmentName' && v != null) { v = String(v).replace(/\s*\/\s*/g, ''); }
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
      body.appendChild(h('div', { style: 'margin-top:12px;font-size:13px;color:var(--c-sub, #66788c)', text: '共 ' + rows.length + ' 条' }));
      if (!rows.length) {
        body.appendChild(h('div', { class: 'empty-row', text: '没有符合条件的记录' }));
      }
    }).catch(function (e) {
      body.innerHTML = '';
      body.appendChild(h('div', { class: 'empty-row', text: e.message }));
    });
  }
  loadDrillPage(1, []);
}

function dashboardView() {
  var prefUserId = USER && USER.userId, prefToken = TOKEN;
  var widgetsKey = preferenceKey('dashWidgets'), cardsKey = preferenceKey('dashStatCards');
  function currentPrefs() { return !!prefToken && TOKEN === prefToken && USER && USER.userId === prefUserId; }
  var root = h('div', { class: 'loading-inline', text: '加载中…' });
  var widgets;
  try { widgets = JSON.parse(localStorage.getItem(widgetsKey) || 'null'); } catch (e) { widgets = null; }
  widgets = dashDriverLayout(widgets);
  if (!widgets.length) {
    widgets = markDashDriverLayout(DASH_DEFAULT.map(function (w) { return { dim: w.dim, kind: w.kind, size: w.size }; }));
  }
  if (currentPrefs()) {
    try { localStorage.setItem(widgetsKey, JSON.stringify(widgets)); } catch (e) { }
  }
  var lastData = null;
  var lastEvents = [];
  var lastEventsTotal = 0;
  var evFilter = '全部';
  /* 事件接口返回 {list,total}(旧版为数组):list 为截断后事件,total 为窗口内总数 */
  function adoptEvents(evs) {
    lastEvents = evs && evs.list ? evs.list : (evs || []);
    lastEventsTotal = evs && evs.total != null ? +evs.total : lastEvents.length;
  }
  var chartsGrid = h('div', { class: 'dash-grid' });
  var eventsBox = h('div');
  var dragState = null;
  var dashUserTouched = false;   // 用户已手动改过布局后,不再用服务端旧值覆盖

  /* 账号布局双存：不读取无来源的旧共享键，服务端只访问当前认证用户偏好。 */
  function adoptServerDashPrefs() {
    GET('/api/preferences/dashWidgets').then(function (saved) {
      if (!currentPrefs() || dashUserTouched) { return; }
      var remote = null;
      try { remote = dashDriverLayout(JSON.parse(saved || 'null')); } catch (e) { remote = null; }
      if (remote && remote.length) {
        if (JSON.stringify(remote) !== JSON.stringify(widgets)) {
          widgets = remote;
          try { localStorage.setItem(widgetsKey, JSON.stringify(widgets)); } catch (e) { }
          renderCharts();
        }
        // 将一次迁移标记写回同一账号偏好，换浏览器后也不会恢复手工删掉的卡。
        if (JSON.stringify(remote) !== saved) PUT('/api/preferences/dashWidgets', remote).catch(function () { });
      } else if (widgets.length) {
        PUT('/api/preferences/dashWidgets', widgets).catch(function () { });
      }
    }).catch(function () { });
    GET('/api/preferences/dashStatCards').then(function (saved) {
      if (!currentPrefs() || dashUserTouched) { return; }
      var remote = null;
      try { remote = JSON.parse(saved || 'null'); } catch (e) { remote = null; }
      if (remote && remote.length) {
        if (JSON.stringify(remote) !== JSON.stringify(getStatCards())) {
          try { localStorage.setItem(cardsKey, JSON.stringify(remote)); } catch (e) { }
          if (lastData) { build(lastData, lastEvents); }
        }
      } else {
        PUT('/api/preferences/dashStatCards', getStatCards()).catch(function () { });
      }
    }).catch(function () { });
  }
  adoptServerDashPrefs();

  function saveWidgets() {
    if (!currentPrefs()) return;
    dashUserTouched = true;
    markDashDriverLayout(widgets);
    try { localStorage.setItem(widgetsKey, JSON.stringify(widgets)); } catch (e) { /* 忽略 */ }
    PUT('/api/preferences/dashWidgets', widgets).catch(function () { });
  }

  /* ---------- 近期事件(带快捷筛选) ---------- */
  function renderEvents() {
    eventsBox.innerHTML = '';
    var counts = {};
    lastEvents.forEach(function (ev) { var t = ev.tag || '动态'; counts[t] = (counts[t] || 0) + 1; });
    var chipDefs = ['全部', '物资', '休假', '借还', '生日', '党务', '装备', '车辆'].filter(function (t) { return t === '全部' || counts[t]; });
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
    // 超过50条被服务端截断时明确提示,不再静默丢失
    if (lastEventsTotal > lastEvents.length) {
      listBox.appendChild(h('div', { style: 'font-size:12px;color:var(--c-sub,#66788c);padding:6px 2px 0', text: '事件较多，仅显示前 ' + lastEvents.length + ' 条（共 ' + lastEventsTotal + ' 条）' }));
    }
  }

  /* ---------- 图表卡片(尺寸可切 + 可拖动排序) ---------- */
    function makeCard(w) {
    var dim = DASH_DIMS[w.dim];
    var kind = w.kind;
    var label = dashCardLabel(w.dim, kind);
    // 普通分组按真实字段值下钻；单位专用图直接使用接口ID，绝不按同名反查。
    var drill = null;
    if (dim.drill) {
      var fixed = dim.drill.fixedVal;
      drill = {
        api: dim.drill.api,
        param: dim.drill.param,
        title: label,
        valOf: w.dim === 'driverLicense'
          ? function (name) { return name === '有驾驶证' ? 'yes' : name === '无驾驶证' ? 'no' : null; }
          : w.dim === 'actualDuty'
            // 接口给的是完整路径;两端做斜杠归一化后按路径在单位树上精确匹配(路径唯一),查不到不可点
            ? function (name) {
                var want = String(name).replace(/\s*\/\s*/g, '');
                var hit = null;
                for (var di = 0; di < Ctx.deptFlat.length; di++) {
                  var node = Ctx.deptFlat[di];
                  if (node.path && String(node.path).replace(/\s*\/\s*/g, '') === want) { hit = node; break; }
                }
                return hit ? hit.id : null;
              }
            : fixed != null
              ? function () { return fixed; }
              : function (name) { return name == null || name === '' || name === '未填写' || name === '未分配' ? null : name; }
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
      if (dim.hierarchy) {
        body = drawDepartmentHierarchy(items);
      } else if (kind === 'treemap') {
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
        h('span', { class: 'dash-card-title' },
          h('span', { class: 'dash-drag-handle', draggable: false, title: '按住拖动排序', 'aria-label': '拖动排序', text: '⠿' }),
          h('h3', { style: 'margin:0', text: label })),
        h('span', { style: 'display:flex;align-items:center;gap:8px' },
          sizeBtns,
          h('button', {
            type: 'button', class: 'btn btn-ghost btn-sm', text: '更换图表', title: '更换指标或图表类型',
            onclick: function (e) {
              e.stopPropagation();
              openPicker(widgets.indexOf(w));
            }
          }),
          h('button', {
            class: 'dash-card-x', text: '×', title: '移除此图表',
            onclick: function () {
              widgets = widgets.filter(function (x) { return !(x.dim === w.dim && x.kind === w.kind); });
              saveWidgets();
              renderCharts();
            }
          }))),
      body);
    var handle = $('.dash-drag-handle', card);
    var pointerEvents = !!window.PointerEvent;
    var downEvent = pointerEvents ? 'pointerdown' : 'mousedown';
    var moveEvent = pointerEvents ? 'pointermove' : 'mousemove';
    var upEvent = pointerEvents ? 'pointerup' : 'mouseup';
    handle.addEventListener(downEvent, function (e) {
      if (e.button !== 0 || dragState) { return; }
      e.preventDefault();
      var startX = e.clientX;
      var startY = e.clientY;
      var activePointer = e.pointerId;
      var started = false;
      var mirror = null;
      var rect = card.getBoundingClientRect();
      var grabX = startX - rect.left;
      var grabY = startY - rect.top;
      var dragWidth = rect.width;
      var dragHeight = rect.height;
      var gridRect = chartsGrid.getBoundingClientRect();
      var layoutLeft = rect.left - card.offsetLeft;
      var layoutTop = rect.top - card.offsetTop;
      var anchorX = startX;
      var anchorY = startY;
      var lastAxis = '';
      var lastDirection = 0;
      function movePointer(ev) {
        if (pointerEvents && ev.pointerId !== activePointer) { return; }
        if (ev.buttons === 0) { endDrag(); return; }
        ev.preventDefault();
        var dx = ev.clientX - startX;
        var dy = ev.clientY - startY;
        if (!started && Math.sqrt(dx * dx + dy * dy) < 4) { return; }
        if (!started) {
          started = true;
          mirror = card.cloneNode(true);
          mirror.classList.add('dash-drag-mirror');
          mirror.style.width = card.offsetWidth + 'px';
          mirror.style.height = card.offsetHeight + 'px';
          mirror.setAttribute('aria-hidden', 'true');
          mirror.style.left = ev.clientX - grabX + 'px';
          mirror.style.top = ev.clientY - grabY + 'px';
          document.body.appendChild(mirror);
          dragState = { card: card, mirror: mirror };
          card.classList.add('dragging');
          document.body.classList.add('dash-sorting');
        }
        if (mirror) {
          mirror.style.left = ev.clientX - grabX + 'px';
          mirror.style.top = ev.clientY - grabY + 'px';
        }
        var travelX = ev.clientX - anchorX;
        var travelY = ev.clientY - anchorY;
        var horizontal = Math.abs(travelX) >= Math.abs(travelY);
        var axis = horizontal ? 'x' : 'y';
        var travel = horizontal ? travelX : travelY;
        var direction = travel > 0 ? 1 : -1;
        var buffer = lastAxis === axis && lastDirection !== direction ? 20 : 8;
        if (Math.abs(travel) < buffer) { return; }
        var movingCard = dragState.card;
        var cards = $all('.dash-card', chartsGrid);
        var movingIndex = cards.indexOf(movingCard);
        var currentGridRect = chartsGrid.getBoundingClientRect();
        var originLeft = layoutLeft + currentGridRect.left - gridRect.left;
        var originTop = layoutTop + currentGridRect.top - gridRect.top;
        var left = ev.clientX - grabX;
        var top = ev.clientY - grabY;
        var target = null;
        var bestDistance = Infinity;
        cards.forEach(function (candidate, index) {
          if (candidate === movingCard || (index - movingIndex) * direction <= 0) { return; }
          var sameRow = Math.abs(movingCard.offsetTop - candidate.offsetTop) < 2;
          if (horizontal !== sameRow) { return; }
          // FLIP 的 transform 只用于动画，命中判断使用未变换的网格位置。
          var targetLeft = originLeft + candidate.offsetLeft;
          var targetTop = originTop + candidate.offsetTop;
          var width = candidate.offsetWidth;
          var height = candidate.offsetHeight;
          var overlapX = Math.min(left + dragWidth, targetLeft + width) - Math.max(left, targetLeft);
          var overlapY = Math.min(top + dragHeight, targetTop + height) - Math.max(top, targetTop);
          var minWidth = Math.min(dragWidth, width);
          var minHeight = Math.min(dragHeight, height);
          var overlap = horizontal ? overlapX : overlapY;
          var crossOverlap = horizontal ? overlapY : overlapX;
          var size = horizontal ? minWidth : minHeight;
          var crossSize = horizontal ? minHeight : minWidth;
          var threshold = Math.min(48, Math.max(12, size * .16));
          if (overlap < threshold || crossOverlap < crossSize * .35) { return; }
          var distanceX = (left + dragWidth / 2 - targetLeft - width / 2) / minWidth;
          var distanceY = (top + dragHeight / 2 - targetTop - height / 2) / minHeight;
          var distance = distanceX * distanceX + distanceY * distanceY;
          if (distance < bestDistance) { target = candidate; bestDistance = distance; }
        });
        if (!target) { return; }
        var reference = direction > 0 ? target.nextElementSibling : target;
        $all('.dash-card.drop-target', chartsGrid).forEach(function (el) { el.classList.remove('drop-target'); });
        target.classList.add('drop-target');
        animateGridMove(function () { chartsGrid.insertBefore(movingCard, reference); });
        anchorX = ev.clientX;
        anchorY = ev.clientY;
        lastAxis = axis;
        lastDirection = direction;
      }
      function endDrag(ev) {
        if (pointerEvents && ev && ev.type !== 'blur' && ev.pointerId !== activePointer) { return; }
        window.removeEventListener(moveEvent, movePointer);
        window.removeEventListener(upEvent, endDrag);
        window.removeEventListener('pointercancel', endDrag);
        window.removeEventListener('blur', endDrag);
        if (dragState && dragState.card === card) {
          card.classList.remove('dragging');
          document.body.classList.remove('dash-sorting');
          $all('.dash-card.drop-target', chartsGrid).forEach(function (el) { el.classList.remove('drop-target'); });
          if (mirror && mirror.parentNode) { mirror.parentNode.removeChild(mirror); }
          dragState = null;
          var order = [];
          Array.prototype.forEach.call(chartsGrid.querySelectorAll('.dash-card'), function (el) {
            var key = el.getAttribute('data-wid');
            for (var i = 0; i < widgets.length; i++) {
              if (widgets[i].dim + ':' + widgets[i].kind === key) { order.push(widgets[i]); break; }
            }
          });
          if (order.length === widgets.length) { widgets = order; saveWidgets(); }
        }
      }
      window.addEventListener(moveEvent, movePointer);
      window.addEventListener(upEvent, endDrag);
      window.addEventListener('pointercancel', endDrag);
      window.addEventListener('blur', endDrag);
    });
    handle.addEventListener('dragstart', function (e) { e.preventDefault(); });
    return card;
  }

  function animateGridMove(move) {
    var cards = $all('.dash-card', chartsGrid);
    var before = {};
    cards.forEach(function (el) {
      var rect = el.getBoundingClientRect();
      before[el.getAttribute('data-wid')] = { left: rect.left, top: rect.top };
    });
    move();
    cards.forEach(function (el) {
      if (el._dashMoveFrame) { cancelAnimationFrame(el._dashMoveFrame); }
      el.style.transition = 'none';
      el.style.transform = '';
    });
    cards.forEach(function (el) {
      var old = before[el.getAttribute('data-wid')];
      if (!old) { return; }
      var rect = el.getBoundingClientRect();
      var dx = old.left - rect.left;
      var dy = old.top - rect.top;
      el.style.transform = 'translate(' + dx + 'px,' + dy + 'px)';
    });
    chartsGrid.offsetHeight;
    cards.forEach(function (el) {
      el._dashMoveFrame = requestAnimationFrame(function () {
        el._dashMoveFrame = null;
        el.style.transition = '';
        el.style.transform = '';
      });
    });
  }

  function renderCharts() {
    chartsGrid.innerHTML = '';
    if (!widgets.length) {
      chartsGrid.appendChild(h('div', { class: 'empty-row', style: 'grid-column:1/-1', text: '还没有图表,点击上方"添加图表"选择要展示的内容' }));
      return;
    }
    widgets.forEach(function (w) { chartsGrid.appendChild(makeCard(w)); });
  }
  chartsGrid.addEventListener('click', function (e) {
    var el = e.target.closest ? e.target.closest('[data-api]') : null;
    if (!el) { return; }
    openDrillModal(el.getAttribute('data-title'), el.getAttribute('data-api'),
      el.getAttribute('data-param'), el.getAttribute('data-val'));
  });
  // 键盘下钻:聚焦的分段/条形按 Enter 或 Space 打开,与点击一致
  chartsGrid.addEventListener('keydown', function (e) {
    if (e.key !== 'Enter' && e.key !== ' ') { return; }
    var el = e.target && e.target.closest ? e.target.closest('[data-api]') : null;
    if (!el) { return; }
    e.preventDefault();
    openDrillModal(el.getAttribute('data-title'), el.getAttribute('data-api'),
      el.getAttribute('data-param'), el.getAttribute('data-val'));
  });

  /* ---------- 添加/更换图表:添加时选指标,更换时仅选当前指标的图表类型 ---------- */
  function openPicker(replaceIndex) {
    var replacing = typeof replaceIndex === 'number' && replaceIndex >= 0 && replaceIndex < widgets.length;
    var original = replacing ? widgets[replaceIndex] : null;
    // 不再自设 max-height/overflow:滚动交给弹窗的 .dialog-body,避免出现双滚动条
    var wrap = h('div');
    if (replacing) {
      var dim = DASH_DIMS[original.dim];
      var kinds = kindsForDim(dim);
      wrap.appendChild(h('div', { style: 'margin-bottom:12px;color:var(--c-sub,#66788c);font-size:13px',
        text: '当前指标：' + dim.label + '。点击下方图表类型按钮即可替换，指标类别保持不变。' }));
      var btns = h('div', { style: 'display:flex;flex-wrap:nowrap;gap:8px;margin:8px 0 16px;width:100%' });
      kinds.forEach(function (kind) {
        var current = original.kind === kind;
        btns.appendChild(h('button', {
          type: 'button', class: current ? 'btn btn-primary' : 'btn btn-ghost',
          style: 'display:inline-flex;flex:1 1 0;align-items:center;justify-content:center;min-width:0;min-height:36px;padding:6px 10px;white-space:nowrap;cursor:pointer',
          text: (current ? '当前：' : '切换为：') + DASH_KINDS[kind],
          title: current ? '当前图表样式' : '切换为' + DASH_KINDS[kind],
          onclick: function () {
            if (current) { m.close(); return; }
            original.kind = kind;
            saveWidgets();
            renderCharts();
            m.close();
          }
        }));
      });
      wrap.appendChild(btns);
      var m = openModal({
        title: '更换图表',
        body: wrap,
        footer: [h('button', { class: 'btn btn-primary', text: '关闭', onclick: function () { m.close(); } })]
      });
      return;
    }
    var cats = [];
    Object.keys(DASH_DIMS).forEach(function (k) {
      var c = DASH_DIMS[k].cat;
      if (cats.indexOf(c) < 0) { cats.push(c); }
    });
    function isAdded(dimKey, kind) {
      return widgets.some(function (w) { return w.dim === dimKey && w.kind === kind; });
    }
    function selectChart(dimKey, kind) {
      if (isAdded(dimKey, kind)) { toast('该图表已存在'); return; }
      widgets.push({ dim: dimKey, kind: kind, size: 'm' });
      saveWidgets();
      renderCharts();
      m.close();
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
            text: (added ? '✓ 已添加 · ' : '') + DASH_KINDS[kd],
            title: added ? '已添加到仪表盘' : '以' + DASH_KINDS[kd] + '展示' + dim.label,
            onclick: function () { selectChart(k, kd); }
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

  // 概况卡目录(可配置显示/隐藏);href=点击跳转的目标模块路由(带筛选的人员路由与统计口径一致)
  var STAT_CARDS = {
    total:          { label: '人员总数',   icon: 'users',    danger: false, href: 'personnel' },
    onDuty:         { label: '在位人员',   icon: 'check',    danger: false, href: 'personnel' },
    onLeave:        { label: '休假中',     icon: 'calendar', danger: false, href: 'personnel?status=' + encodeURIComponent('休假') },
    deptCount:      { label: '单位总数',   icon: 'building', danger: false, href: 'department' },
    vehicleCount:   { label: '车辆总数',   icon: 'truck',    danger: false, href: 'vehicle' },
    vehicleRepair:  { label: '车辆维修',   icon: 'truck',    danger: true,  href: 'vehicle' },
    equipmentTotal: { label: '装备总数',   icon: 'box',      danger: false, href: 'equipment' },
    equipmentRepair:{ label: '装备维修',   icon: 'box',      danger: true,  href: 'equipment' },
    materialTotal:  { label: '物资总数',   icon: 'box',      danger: false, href: 'material' },
    materialLow:    { label: '库存预警',   icon: 'box',      danger: true,  href: 'material' },
    leaveThisYear:  { label: '今年休假',   icon: 'calendar', danger: false, href: 'leave' },
    fitnessThisYear:{ label: '今年考核',   icon: 'activity', danger: false, href: 'fitness' },
    dePercent:      { label: '双争完成率', icon: 'check',    danger: false, href: 'double-excellence', suffix: '%' },
    aaPercent:      { label: '考评完成率', icon: 'activity', danger: false, href: 'annual-appraisal', suffix: '%' }
  };
  var STAT_DEFAULT = ['total', 'onDuty', 'onLeave', 'deptCount', 'vehicleCount', 'materialLow', 'dePercent', 'aaPercent'];

  function getStatCards() {
    try {
      var v = JSON.parse(localStorage.getItem(cardsKey) || 'null');
      if (v && v.length) { return v; }
    } catch (e) { }
    return STAT_DEFAULT.slice();
  }

  function saveStatCards(v) {
    if (!currentPrefs()) return;
    dashUserTouched = true;
    try { localStorage.setItem(cardsKey, JSON.stringify(v)); } catch (e) { }
    PUT('/api/preferences/dashStatCards', v).catch(function () { });
  }

  /** 统计卡配置: 分组按钮式(和图表添加一致,点亮=显示) */
  function openStatCardPicker() {
    var current = getStatCards();

    // 分组: 人员 / 装备物资 / 车辆 / 考勤 / 评比考评
    var groups = [
      { name: '人员', keys: ['total', 'onDuty', 'onLeave', 'deptCount'] },
      { name: '装备物资', keys: ['equipmentTotal', 'equipmentRepair', 'materialTotal', 'materialLow'] },
      { name: '车辆', keys: ['vehicleCount', 'vehicleRepair'] },
      { name: '考勤', keys: ['leaveThisYear', 'fitnessThisYear'] },
      { name: '评比考评', keys: ['dePercent', 'aaPercent'] }
    ];

    var wrap = h('div');
    var boxes = {};

    function paint() {
      Object.keys(boxes).forEach(function (k) {
        boxes[k].className = 'pick-kind-btn' + (current.indexOf(k) >= 0 ? ' mod-on' : '');
      });
      countLbl.textContent = '已选:' + current.length + ' 个';
    }

    groups.forEach(function (g) {
      wrap.appendChild(h('div', { class: 'dash-pick-cat', text: g.name }));
      var row = h('div', { style: 'display:flex;gap:8px;flex-wrap:wrap;margin-bottom:12px' });
      g.keys.forEach(function (k) {
        var def = STAT_CARDS[k];
        if (!def) { return; }
        var b = h('button', { type: 'button', class: 'pick-kind-btn', text: def.label });
        b.addEventListener('click', function () {
          var idx = current.indexOf(k);
          if (idx >= 0) { current.splice(idx, 1); } else { current.push(k); }
          paint();
        });
        boxes[k] = b;
        row.appendChild(b);
      });
      wrap.appendChild(row);
    });

    var countLbl = h('span', { style: 'font-size:12px;color:var(--pri-dark,#16437e);font-weight:600' });

    var m = openModal({
      title: '配置统计卡片', body: h('div', {},
        h('div', { style: 'font-size:13px;color:#66788c;margin-bottom:12px', text: '点亮=在仪表盘顶部显示(和图表添加方式一致)' }),
        wrap),
      footer: [
        h('button', { class: 'btn btn-ghost', text: '恢复默认', onclick: function () {
          saveStatCards(STAT_DEFAULT.slice());
          m.close();
          if (lastData) { build(lastData, lastEvents); }
        } }),
        h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m.close(); } }),
        h('button', { class: 'btn btn-primary', text: '确定', onclick: function () {
          if (!current.length) { toast('至少选择一个统计卡片', 'err'); return; }
          saveStatCards(current);
          m.close();
          if (lastData) { build(lastData, lastEvents); }
        } })
      ]
    });
    // 工具栏旁的已选数量提示
    paint();
  }

  function build(d, events) {
    root.className = '';
    root.innerHTML = '';
    // 顶部概况卡(可配置)
    var chips = h('div', { class: 'stats-cards' });
    function card(num, lbl, ic, danger, href) {
      var numEl = h('div', { class: 'num', text: String(num) });
      countUp(numEl, num);
      var body = h('div', {}, numEl, h('div', { class: 'lbl', text: lbl }));
      // 有跳转目标的卡用按钮承载:键盘可聚焦,Enter 直接打开
      if (href) {
        return h('button', { type: 'button', class: 'stat-card stat-card-btn' + (danger && +num > 0 ? ' danger' : ''),
          title: '点击打开' + lbl + '相关模块', 'aria-label': lbl + ' ' + num + '，点击打开相关模块',
          onclick: function () { location.hash = href; } },
          h('span', { class: 'ic', html: iconSvg(ic) }), body);
      }
      return h('div', { class: 'stat-card' + (danger && +num > 0 ? ' danger' : '') },
        h('span', { class: 'ic', html: iconSvg(ic) }), body);
    }
    var statSel = getStatCards();
    statSel.forEach(function (k, i) {
      var def = STAT_CARDS[k];
      if (def && d[k] !== undefined) {
        var el = card(String(d[k] || 0) + (def.suffix || ''), def.label, def.icon, def.danger, def.href);
        el.style.animationDelay = (i * 60) + 'ms'; // 级联入场:逐卡错峰(减动效时动画直达终态,延迟无感)
        chips.appendChild(el);
      }
    });
    root.appendChild(chips);

    root.appendChild(h('div', { class: 'dash-main' },
      h('div', { class: 'dash-left' },
        h('div', { class: 'dash-toolbar' },
          h('button', { class: 'btn btn-ghost', text: '统计卡', title: '配置顶部统计卡片', onclick: openStatCardPicker }),
          h('button', { class: 'btn btn-primary', text: '添加图表', onclick: openPicker }),
          h('button', {
            class: 'btn btn-ghost', text: '恢复默认',
            onclick: function () {
              widgets = DASH_DEFAULT.map(function (w) { return { dim: w.dim, kind: w.kind, size: w.size }; });
              saveWidgets();
              renderCharts();
            }
          }),
          h('span', { title: '卡片可拖动排序,小/中/大可切,布局保存在本机', style: 'width:18px;height:18px;border:1px solid var(--c-border, #c5cdd8);border-radius:50%;display:inline-flex;align-items:center;justify-content:center;font-size:11px;color:var(--c-muted, #8a97a8);cursor:help;flex:none', text: '?' })),
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
                  adoptEvents(evs);
                  renderEvents();
                }).catch(function (e) {
                  eventsBox.innerHTML = '';
                  eventsBox.appendChild(h('div', { class: 'empty-row', text: e.message }));
                });
              }
            }),
            ),
          eventsBox))));
    adoptEvents(events);
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
        h('span', { title: '自动记录:登录/登出/增删改/导入导出/备份', style: 'width:18px;height:18px;border:1px solid var(--c-border, #c5cdd8);border-radius:50%;display:inline-flex;align-items:center;justify-content:center;font-size:11px;color:var(--c-muted, #8a97a8);cursor:help;flex:none', text: '?' })),
      h('div', { class: 'table-wrap' }, table),
      pagerBox));
    container.appendChild(root);
    load();
  }
  return { render: render };
}

/* ================= 系统设置视图 ================= */

function settingsView(initialTab) {
  var vals = {
    siteName: SITE.name === '数智军营' ? '' : SITE.name,
    siteTitle: SITE.title === '数智军营人员管理系统' ? '' : SITE.title,
    siteSubtitle: (SITE.subtitle === '人员管理系统' || !SITE.subtitle) ? '' : SITE.subtitle,
    siteLogo: SITE.logo || '', siteFavicon: SITE.favicon || '',
    themeColor: SITE.theme || 'var(--pri, #2f6fd6)', pageSize: String(SITE.pageSize || 20)
  };
  var root = h('div');

  function section(title) {
    var box = h('div', { class: 'panel', style: 'margin-bottom:16px' });
    box.appendChild(h('div', { style: 'padding:16px 20px;border-bottom:1px solid #edf1f6;font-weight:600;color:var(--c-dark, #1d2c40)', text: title }));
    var body = h('div', { style: 'padding:16px 20px;display:grid;grid-template-columns:1fr 1fr;gap:16px 24px' });
    box.appendChild(body);
    return { box: box, body: body };
  }
  function field(label, el, hint) {
    return h('div', { class: 'form-item', style: 'margin:0' },
      h('label', { text: label, style: 'display:block;font-size:13px;color:var(--c-sub, #66788c);margin-bottom:8px' }),
      el,
      hint ? h('div', { style: 'font-size:12px;color:var(--c-muted, #8a97a8);margin-top:4px', text: hint }) : null);
  }
  function txt(key, ph) {
    var i = h('input', { type: 'text', placeholder: ph || '', style: 'width:100%;height:32px;padding:0 10px;border:1px solid #c5cdd8;border-radius:4px' });
    i.value = vals[key];
    i.addEventListener('input', function () { vals[key] = i.value; });
    return i;
  }

  /* ---- Tab1 基础信息 ---- */
  var s1 = section('基础信息');
  s1.body.appendChild(field('网站名称(侧栏主标题)', txt('siteName', '默认:数智军营')));
  s1.body.appendChild(field('系统标题(浏览器标签页)', txt('siteTitle', '默认:数智军营人员管理系统')));
  s1.body.appendChild(field('网站副标题(侧栏副标题)', txt('siteSubtitle', '默认:人员管理系统')));
  s1.body.appendChild(h('div'));
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
  s1.body.appendChild(field('LOGO(侧栏/登录页)', logoPrev, '建议 PNG 透明底,不超过 300KB'));
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
  s1.body.appendChild(field('网站图标(favicon)', favPrev, '建议 32x32 或 64x64,浏览器标签页小图标'));

  /* ---- Tab2 主题与显示 ---- */
  var s2 = section('主题色');
  var presets = ['var(--pri, #2f6fd6)', '#4a7c59', '#1f3a5f', '#8a4a2f', '#7048b7', '#b03a3a', '#0d8484', '#5a5f6b'];
  var swatches = h('div', { style: 'display:flex;gap:12px;flex-wrap:wrap;align-items:center' });
  presets.forEach(function (c) {
    var sw = h('span', { class: 'theme-sw', style: 'width:34px;height:32px;border-radius:8px;background:' + c + ';cursor:pointer;border:2px solid ' + (vals.themeColor === c ? 'var(--c-dark, #1d2c40)' : 'transparent'), title: c });
    sw.addEventListener('click', function () {
      vals.themeColor = c;
      applyThemeVars(c);
      Array.prototype.forEach.call(swatches.querySelectorAll('.theme-sw'), function (x) { x.style.borderColor = 'transparent'; });
      sw.style.borderColor = 'var(--c-dark, #1d2c40)';
      saveThemeNow(c);
    });
    swatches.appendChild(sw);
  });
  var customColor = h('input', { type: 'color', value: vals.themeColor, style: 'width:44px;height:32px;border:none;background:none;cursor:pointer' });
  var colorSaveTimer = null;
  customColor.addEventListener('input', function () {
    vals.themeColor = customColor.value;
    applyThemeVars(customColor.value);
    Array.prototype.forEach.call(swatches.querySelectorAll('.theme-sw'), function (x) { x.style.borderColor = 'transparent'; });
    clearTimeout(colorSaveTimer);
    colorSaveTimer = setTimeout(function () { saveThemeNow(customColor.value); }, 600);
  });
  swatches.appendChild(h('span', { style: 'color:var(--c-muted, #8a97a8);font-size:12px', text: '自定义:' }));
  swatches.appendChild(customColor);
  s2.body.appendChild(h('div', { style: 'grid-column:1/-1' }, swatches,
    h('div', { style: 'font-size:12px;color:var(--c-muted, #8a97a8);margin-top:8px', text: '点击即时应用并保存;全站按钮/菜单/图表/侧栏随主题变化' })));

  var s3 = section('显示与维护');
  var pageSizeSel = h('select', { style: 'width:100%;height:32px;padding:0 8px;border:1px solid #c5cdd8;border-radius:4px' });
  ['10', '20', '50', '100'].forEach(function (n) {
    pageSizeSel.appendChild(h('option', { value: n, text: n + ' 条/页' }));
  });
  pageSizeSel.value = vals.pageSize;
  pageSizeSel.addEventListener('change', function () { vals.pageSize = pageSizeSel.value; });
  s3.body.appendChild(field('列表每页条数', pageSizeSel, '保存后刷新页面生效'));
  s3.body.appendChild(field('自动备份保留份数', h('span', { text: '在“备份恢复”页的自动备份设置中统一配置' }), '保留份数、周期和模块均由备份设置管理'));

  /* ---- 保存条(基础+主题+显示通用) ---- */
  var saveBar = h('div', { class: 'panel', style: 'padding:16px 20px;display:flex;gap:12px;align-items:center;flex-wrap:wrap' });
  saveBar.appendChild(h('button', {
    class: 'btn btn-primary', text: '保存全部设置',
    onclick: function () {
      var keys = ['siteName', 'siteTitle', 'siteSubtitle', 'siteLogo', 'siteFavicon', 'themeColor', 'pageSize'];
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
  saveBar.appendChild(h('button', {
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
  saveBar.appendChild(h('span', { style: 'color:var(--c-muted, #8a97a8);font-size:12px', text: '设置保存在服务器,对所有使用本系统的人生效' }));

  /* ---- TAB 框架 ---- */
  var panels = {};
  var tabDefs = [
    ['basic', '基础信息', null],
    ['theme', '主题与显示', null],
    ['backup', '备份恢复', function () { if (!backupLoaded) { backupLoaded = true; backupView('backup').render(panels.backup.firstChild); } }],
    ['report', '数据上报', function () { if (!reportLoaded) { reportLoaded = true; backupView('report').render(panels.report.firstChild); } }]
  ];
  // 显式路由标签优先;普通设置页面仍记住最后浏览的TAB。
  var savedTab = initialTab;
  if (!savedTab) {
    try { savedTab = localStorage.getItem('settingsTab'); } catch (e) { /* 忽略 */ }
  }
  var activeIdx = 0;
  tabDefs.forEach(function (td, idx) { if (td[0] === savedTab) { activeIdx = idx; } });
  var tabBar = h('div', { class: 'tabs panel', style: 'padding:0 8px;margin-bottom:16px' });
  var panelsBox = h('div');
  var tabEls = [];
  tabDefs.forEach(function (td, idx) {
    var on = idx === activeIdx;
    var body = h('div', { style: on ? 'display:block' : 'display:none' });   // 修复:激活TAB的面板默认可见
    panels[td[0]] = body;
    var tab = h('div', { class: 'tab' + (on ? ' active' : ''), text: td[1], onclick: function () {
      Array.prototype.forEach.call(tabBar.children, function (t) { t.classList.remove('active'); });
      tab.classList.add('active');
      Object.keys(panels).forEach(function (k) { panels[k].style.display = 'none'; });
      body.style.display = 'block';
      try { localStorage.setItem('settingsTab', td[0]); } catch (e) { /* 忽略 */ }
      if (td[2]) { td[2](); }
    } });
    tabEls.push(tab);
    tabBar.appendChild(tab);
  });
  panels.basic.appendChild(s1.box);
  panels.theme.appendChild(s2.box);
  panels.theme.appendChild(s3.box);
  panels.theme.appendChild(saveBar);
  panels.backup.appendChild(h('div'));   // 备份恢复(懒加载)
  panels.report.appendChild(h('div'));   // 数据上报(懒加载)

  var backupLoaded = false, reportLoaded = false;

  function render(container) {
    container.appendChild(h('div', {}, tabBar, panelsBox));
    tabDefs.forEach(function (td) { panelsBox.appendChild(panels[td[0]]); });
    // 恢复到记忆的TAB:面板就绪后再触发懒加载
    if (activeIdx > 0 && tabDefs[activeIdx][2]) { tabDefs[activeIdx][2](); }
  }
  return { render: render };
}

/** 字节数 → 可读大小 */
function fmtSize(bytes) {
  if (bytes == null) { return ''; }
  if (bytes < 1024) { return bytes + ' B'; }
  if (bytes < 1024 * 1024) { return (bytes / 1024).toFixed(1) + ' KB'; }
  return (bytes / 1024 / 1024).toFixed(1) + ' MB';
}

function backupView(mode) {   // mode: 'all' 默认 | 'backup' 仅备份恢复 | 'report' 仅数据上报
  var root = h('div', { class: 'loading-inline', text: '加载中…' });
  var MODULES = [];   // [{key,label}]
  var restoring = false;

  function moduleLabel(key) {
    for (var i = 0; i < MODULES.length; i++) {
      if (MODULES[i].key === key) return MODULES[i].label;
    }
    return key;
  }

  /** 模块勾选组;返回 {el, get, setAll} */
  function checkGroup(checkedKeys, availableModules) {
    var boxes = [];
    var wrap = h('div', { class: 'chk-list' });
    (availableModules || MODULES).forEach(function (m) {
      var box = h('input', { type: 'checkbox', value: m.key });
      box.checked = checkedKeys.indexOf(m.key) >= 0;
      boxes.push(box);
      wrap.appendChild(h('label', {}, box, m.label || m.title));
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
    if (restoring) return;
    var avail = bk.modules ? Object.keys(bk.modules) : [];
    var availableModules = MODULES.filter(function (m) { return avail.indexOf(m.key) >= 0; });
    if (!availableModules.length) {
      toast('该备份没有当前系统支持的模块,无法恢复', 'err');
      return;
    }
    var group = checkGroup(avail, availableModules);
    var closed = false;
    var body = h('div');
    body.appendChild(h('div', { class: 'backup-tip', text: '恢复将覆盖所选模块的现有数据,请确认备份及勾选范围。恢复前系统会自动创建安全备份。所选模块的数据在同一个事务中恢复,不是逐模块独立提交。只恢复“人员管理”不会覆盖单位及装备、物资、车辆照片;v4 人员备份包含删除撤回记录和回收站照片。数据字典/系统数据(dict)包含账号及单位授权、自动备份设置和启动配置;旧包缺少授权表时保留本地账号及授权,缺少其他内容时会提示兼容限制。配置文件另行恢复,端口等启动配置需重启生效。恢复账号、授权或单位信息后需重新登录。' }));
    body.appendChild(h('div', { style: 'font-size:13px;color:var(--c-sub, #66788c);margin-bottom:8px', text: '选择要恢复的模块(仅显示备份中存在且当前系统支持的模块):' }));
    body.appendChild(group.el);
    var cancelBtn = h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m.close(); } });
    var restoreBtn = h('button', {
      class: 'btn btn-danger', text: '开始恢复',
      onclick: function () {
        if (restoring || closed) return;
        var mods = group.get();
        if (!mods.length) { toast('请至少勾选一个模块', 'err'); return; }
        restoring = true;
        restoreBtn.disabled = true;
        restoreBtn.textContent = '正在恢复…';
        cancelBtn.disabled = true;
        $all('input', group.el).forEach(function (box) { box.disabled = true; });
        POST('/api/backup/restore', { name: bk.name, modules: mods }).then(function (r) {
          m.close(true);
          var parts = (r.restored || []).map(function (x) {
            var rows = 0;
            var tables = x.tables || {};
            Object.keys(tables).forEach(function (t) { rows += Number(tables[t]) || 0; });
            return moduleLabel(x.module) + '(' + rows + '条)';
          });
          var message = '恢复完成:' + parts.join('、') + (r.photos ? ',照片' + r.photos + '张' : '') +
            (r.configFiles && r.configFiles.length ? ',系统配置' + r.configFiles.length + '个' : '') +
            (r.requiresRestart ? '(重启后生效)' : '');
          toast(message, 'ok');
          if (r.warnings && r.warnings.length) toast('恢复提示:' + r.warnings.join('；'), 'info');
          if (r.requiresRelogin) {
            forceLogin('恢复完成,账号、授权或单位信息已变更,请重新登录');
            return;
          }
          refreshAll();
          loadList();
        }).then(function () { restoring = false; }).catch(function (e) {
          restoring = false;
          restoreBtn.disabled = false;
          restoreBtn.textContent = '开始恢复';
          cancelBtn.disabled = false;
          $all('input', group.el).forEach(function (box) { box.disabled = false; });
          toast(e.message, 'err');
        });
      }
    });
    var m = openModal({
      title: '恢复备份 · ' + bk.createdText,
      body: body,
      footer: [cancelBtn, restoreBtn],
      beforeClose: function () { return !restoring; },
      onClose: function () { closed = true; }
    });
  }

  var uploading = false;
  var uploadInput = h('input', { type: 'file', accept: '.zip', style: 'display:none', 'aria-label': '选择 ZIP 备份文件' });
  var uploadBtn = h('button', {
    class: 'btn btn-primary', text: '上传备份并恢复',
    onclick: function () {
      if (uploading) return;
      uploadInput.value = '';
      uploadInput.click();
    }
  });
  function finishUpload() {
    uploading = false;
    uploadBtn.disabled = false;
    uploadBtn.textContent = '上传备份并恢复';
    uploadInput.disabled = false;
  }
  uploadInput.addEventListener('change', function () {
    var file = uploadInput.files && uploadInput.files[0];
    uploadInput.value = ''; // 成功、失败或校验拒绝后重选同一文件均能触发 change。
    if (uploading || !file) return;
    if (!file.size) { toast('不能上传空备份文件', 'err'); return; }
    if (!/\.zip$/i.test(file.name)) { toast('请选择 .zip 格式的备份文件', 'err'); return; }
    if (file.size > 100 * 1024 * 1024) { toast('备份文件不能超过 100MB', 'err'); return; }
    var fd = new FormData();
    fd.append('file', file);
    uploading = true;
    uploadBtn.disabled = true;
    uploadBtn.textContent = '正在上传…';
    uploadInput.disabled = true;
    POST('/api/backup/upload', fd).then(function (bk) {
      finishUpload();
      toast('备份已上传,请选择模块并确认恢复;取消后仍可从列表恢复', 'ok');
      loadList();
      openRestore(bk);
    }).catch(function (e) {
      finishUpload();
      toast('上传失败:' + e.message + ',可重新选择文件重试', 'err');
    });
  });

  var listTable = h('table', { class: 'grid' });
  var selectedBackups = {};
  var batchDlBtn = h('button', {
    class: 'btn btn-ghost', text: '批量下载', disabled: true, title: '下载勾选的备份文件',
    onclick: function () {
      var names = Object.keys(selectedBackups);
      if (!names.length) return;
      toast('开始下载 ' + names.length + ' 个备份文件', 'info');
      // 间隔触发,避免浏览器拦截多文件连续下载
      names.forEach(function (name, i) {
        setTimeout(function () {
          apiBlob('/api/backup/download?name=' + encodeURIComponent(name))
            .then(function (r) { downloadBlob(r.blob, r.filename); })
            .catch(function (e) { toast('下载 ' + name + ' 失败:' + e.message, 'err'); });
        }, i * 700);
      });
    }
  });
  var batchDelBtn = h('button', {
    class: 'btn btn-ghost', text: '批量删除', disabled: true, title: '删除勾选的备份文件',
    onclick: function () {
      var names = Object.keys(selectedBackups);
      if (!names.length) return;
      confirmBox('确定删除选中的 ' + names.length + ' 个备份文件吗?删除后不可恢复。', true).then(function (yes) {
        if (!yes) return;
        var okCount = 0, failCount = 0;
        var chain = Promise.resolve();
        names.forEach(function (name) {
          chain = chain.then(function () {
            return POST('/api/backup/delete', { name: name })
              .then(function () { okCount++; })
              .catch(function () { failCount++; });
          });
        });
        chain.then(function () {
          selectedBackups = {};
          toast('批量删除完成:成功 ' + okCount + ' 个' + (failCount ? ',失败 ' + failCount + ' 个' : ''),
            failCount ? 'info' : 'ok');
          loadList();
        });
      });
    }
  });
  function syncBatchBtns() {
    var n = Object.keys(selectedBackups).length;
    batchDlBtn.disabled = n === 0;
    batchDelBtn.disabled = n === 0;
    batchDlBtn.textContent = n ? '批量下载(' + n + ')' : '批量下载';
    batchDelBtn.textContent = n ? '批量删除(' + n + ')' : '批量删除';
    // 全选框半选态
    var cbAll = listTable.querySelector('.cb-all');
    if (cbAll) {
      var rows = $all('.cb-row', listTable);
      var nChecked = rows.filter(function (b) { return b.checked; }).length;
      cbAll.checked = rows.length > 0 && nChecked === rows.length;
      cbAll.indeterminate = nChecked > 0 && nChecked < rows.length;
    }
  }
  listTable.addEventListener('change', function (e) {
    var t = e.target;
    if (t.classList.contains('cb-row')) {
      var name = t.getAttribute('data-name');
      if (t.checked) selectedBackups[name] = true; else delete selectedBackups[name];
      syncBatchBtns();
    } else if (t.classList.contains('cb-all')) {
      $all('.cb-row', listTable).forEach(function (b) {
        var bn = b.getAttribute('data-name');
        b.checked = t.checked;
        if (t.checked) selectedBackups[bn] = true; else delete selectedBackups[bn];
      });
      syncBatchBtns();
    }
  });
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
  var listGeneration = 0;
  function loadList() {
    var generation = ++listGeneration;
    GET('/api/backup/list').then(function (list) {
      if (generation !== listGeneration) return;
      lastList = list || [];
      // 清掉已不存在的勾选,避免误删
      var alive = {};
      lastList.forEach(function (bk) { alive[bk.name] = true; });
      Object.keys(selectedBackups).forEach(function (k) { if (!alive[k]) delete selectedBackups[k]; });
      listTable.innerHTML = '<thead><tr>' +
        '<th class="cb-cell"><input type="checkbox" class="cb-all" title="全选本页"/></th>' +
        '<th>创建时间</th><th>类型</th><th>包含模块</th><th>大小</th><th>文件名</th><th class="action-col" style="width:170px">操作</th></tr></thead><tbody>' +
        (lastList.length ? lastList.map(function (bk) {
          return '<tr><td class="cb-cell"><input type="checkbox" class="cb-row" data-name="' + esc(bk.name) + '"' + (selectedBackups[bk.name] ? ' checked' : '') + '/></td><td>' + esc(bk.createdText) + '</td>' +
            '<td>' + (bk.auto ? '<span class="badge green">自动</span>' : '<span class="badge blue">手动</span>') + '</td>' +
            '<td>' + moduleBadges(bk.modules) + '</td>' +
            '<td>' + fmtSize(bk.size) + '</td>' +
            '<td title="' + esc(bk.name) + '">' + esc(bk.name) + '</td>' +
            '<td class="actions" style="width:140px;white-space:nowrap">' +
            '<button class="btn-link" data-act="restore" data-name="' + esc(bk.name) + '">恢复</button>' +
            '<button class="btn-link" data-act="download" data-name="' + esc(bk.name) + '">下载</button>' +
            '<button class="btn-link danger" data-act="delete" data-name="' + esc(bk.name) + '">删除</button>' +
            '</td></tr>';
        }).join('') : '<tr><td colspan="7" class="empty-row">暂无备份。可点击上方“上传备份并恢复”选择已有的 .zip 备份(最大 100MB),上传后选择模块并确认才会恢复;也可使用“立即备份”创建本机备份。</td></tr>') + '</tbody>';
      // 全选框状态同步(含半选)
      var cbAll = listTable.querySelector('.cb-all');
      if (cbAll) {
        var rows = $all('.cb-row', listTable);
        var nChecked = rows.filter(function (b) { return b.checked; }).length;
        cbAll.checked = rows.length > 0 && nChecked === rows.length;
        cbAll.indeterminate = nChecked > 0 && nChecked < rows.length;
      }
      syncBatchBtns();
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
        if (mode !== 'backup')
        root.appendChild(h('div', { class: 'panel', style: 'margin-bottom:16px' },
          h('div', { style: 'padding:16px 20px;border-bottom:1px solid #edf1f6;font-weight:600;color:var(--c-dark, #1d2c40)', text: '数据上报(下级单位 ↔ 上级单位)' }),
          h('div', { style: 'padding:16px 20px;display:flex;gap:16px;flex-wrap:wrap;align-items:center' },
            h('button', {
              class: 'btn btn-primary', text: '⬆ 导出上报数据包',
              title: '选择模块导出上报包,发送给上级单位',
              onclick: openReportExport
            }),
            h('button', {
              class: 'btn btn-ghost', text: '⬇ 接收下级数据包',
              title: '导入下级单位的上报包:自动检索/新建对应单位,按身份证合并人员',
              onclick: openReportReceive
            }),
            h('span', { style: 'color:var(--c-muted, #8a97a8);font-size:12px', text: '下级单位把"系统设置→网站名称"设为本单位名(如:XX班);上级接收时按该名称匹配/新建单位' }))));

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
          var mods = { personnel: true, equipment: true, material: true, vehicle: true, leave: true, fitness: true, phone: true, military: true };
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
            deptBox.appendChild(h('span', { style: 'color:var(--c-muted, #8a97a8);font-size:12px', text: '(暂无单位)' }));
          }
          var countLbl = h('span', { style: 'font-size:12px;color:var(--pri-dark,#16437e);font-weight:600' });

          // ② 数据模块
          var modBox = h('div', { style: 'display:flex;gap:8px;flex-wrap:wrap' });
          [['personnel', '人员管理'], ['equipment', '装备管理'], ['material', '物资管理'], ['vehicle', '车辆管理'], ['leave', '休假管理(含计划)'], ['fitness', '体能考核'], ['phone', '手机设备'], ['military', '军事考核']].forEach(function (m) {
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
            h('div', { style: 'font-weight:600;margin-bottom:8px;display:flex;align-items:center;gap:12px' },
              h('span', { text: '① 选择单位' }), countLbl),
            deptBox,
            h('div', { style: 'font-weight:600;margin:16px 0 8px;display:flex;align-items:center;gap:12px' },
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
            h('div', { style: 'color:var(--c-muted, #8a97a8);font-size:12px;margin-top:12px;line-height:1.7',
              text: '人员/休假(含计划)/体能/手机/军事考核按所选单位导出;装备/物资/车辆为全单位台账。' }));
          paint();

          var m = openModal({
            title: '导出上报数据包', body: body,
            footer: [
              h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m.close(); } }),
              h('button', {
                class: 'btn btn-primary', text: '导出',
                onclick: function () {
                  var sel = [];
                  ['personnel', 'equipment', 'material', 'vehicle', 'leave', 'fitness', 'phone', 'military'].forEach(function (k) { if (mods[k]) { sel.push(k); } });
                  if (!sel.length) { toast('至少选择一个数据模块', 'err'); return; }
                  var needPeople = sel.indexOf('personnel') >= 0 || sel.indexOf('leave') >= 0 || sel.indexOf('fitness') >= 0 || sel.indexOf('phone') >= 0 || sel.indexOf('military') >= 0;
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

          body.appendChild(h('div', { style: 'background:#eef4ff;border-radius:8px;padding:12px 16px;margin-bottom:16px;line-height:1.9;font-size:13.5px' },
            h('div', { html: '上报单位:<b>' + esc(d.unit) + '</b>(' + esc(d.exportedAt || '') + ')' }),
            h('div', { html: '人员 <b>' + d.personnelCount + '</b> 人(新 ' + d.newCount + ' / 本单位已存在 ' + d.existCount + '),子表 ' + d.subCount + ' 条,休假 ' + d.leaveCount + ' 条,休假计划 ' + (d.leavePlanCount || 0) + ' 条,体能 ' + d.fitnessCount + ' 条,军事考核 ' + (d.militaryCount || 0) + ' 条,手机 ' + (d.phoneCount || 0) + ' 条' }),
            h('div', { html: '装备 ' + d.equipmentCount + ' 项,物资 ' + d.materialCount + ' 项,车辆 ' + d.vehicleCount + ' 辆' })));

          var deptArea = h('div');
          function renderDeptChoice() {
            deptArea.innerHTML = '';
            if (matched.length) {
              deptArea.appendChild(h('div', { style: 'font-weight:600;margin-bottom:8px', text: '已检索到同名单位,数据并入:' }));
              matched.forEach(function (m) {
                var id = String(m.id);
                deptArea.appendChild(h('label', { style: 'display:flex;align-items:center;gap:8px;margin-bottom:8px;cursor:pointer' },
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
                h('span', { text: '都不对,新建单位「' + d.unit + '」' })));
              if (createNew) { deptArea.appendChild(parentPicker()); }
            } else {
              deptArea.appendChild(h('div', { style: 'font-weight:600;margin-bottom:8px', html: '本单位没有名为「<b>' + esc(d.unit) + '</b>」的单位,将<b>新建</b>该单位:' }));
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
            var top = h('option', { value: '0', text: '(作为顶级单位)' });
            parentSel.insertBefore(top, parentSel.firstChild);
            parentSel.value = '0';
            wrap.appendChild(h('span', { style: 'font-size:13px;color:var(--c-sub, #66788c);margin-right:8px', text: '上级:' }));
            wrap.appendChild(parentSel);
            return wrap;
          }
          renderDeptChoice();

          body.appendChild(deptArea);
          body.appendChild(h('div', { style: 'color:var(--c-muted, #8a97a8);font-size:12px;margin-top:12px;line-height:1.8',
            text: '合并规则:人员按身份证去重(新人入所选单位,老人仅更新不改单位);子表以下级为准替换;休假/体能跳过重复;装备按编号、物资按名称、车辆按车牌去重(有则更新,无则新增)。' }));

          var m = openModal({
            title: '接收下级数据 · ' + d.unit, body: body, wide: true,
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
                    if (!useDeptId) { toast('请选择接收单位', 'err'); return; }
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
                    }).catch(function (err) { /* 忽略 */ });
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
        var autoGroup = checkGroup(Array.isArray(settings.modules)
          ? settings.modules : MODULES.map(function (m) { return m.key; }));
        var statusLine = h('div', { style: 'color:var(--c-sub, #66788c);font-size:13px;margin-top:12px' });
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
          h('div', { style: 'font-size:13px;color:var(--c-sub, #66788c);margin:8px 0 2px', text: '自动备份包含的模块:' }),
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
                  if (r && r.warning) { toast(r.warning, 'err'); }
                  loadList();
                }).catch(function (e) { toast(e.message, 'err'); });
              }
            })),
          manualGroup.el,
          h('div', { class: 'backup-tip', text: '备份文件保存在本机程序的 data/backup/ 文件夹;可下载后拷到 U 盘保存。恢复时可选只恢复其中部分模块。' }));

        /* ---- 备份列表 ---- */
        var listCard = h('div', { class: 'chart-panel', style: 'padding:0' },
          h('div', { class: 'table-wrap' }, listTable));

        if (mode !== 'report') {
        root.appendChild(autoCard);
        root.appendChild(manualCard);
        listCard.insertBefore(h('h3', { style: 'padding:16px 20px 0;margin-bottom:12px', text: '备份文件' }), listCard.firstChild);
        listCard.insertBefore(h('div', { style: 'display:flex;align-items:center;gap:12px;flex-wrap:wrap;padding:0 20px 12px' },
          uploadBtn, uploadInput,
          h('span', { style: 'color:var(--c-muted, #8a97a8);font-size:12px', text: 'ZIP 备份最大 100MB;上传后须确认模块才恢复。勾选列表文件可批量操作' }),
          h('div', { style: 'flex:1' }),
          batchDlBtn,
          batchDelBtn), listCard.firstChild.nextSibling);
        root.appendChild(listCard);
        }
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
  var userInput = h('input', { type: 'text', placeholder: '请输入账号', autocomplete: 'username' });
  var pwInput = h('input', { type: 'password', placeholder: '请输入密码', autocomplete: 'current-password' });
  var rememberCb = h('input', { type: 'checkbox', id: 'remember-me' });
  var savedUser = '';
  try {
    savedUser = localStorage.getItem('savedUser') || '';
    // 旧版本曾将密码明文写入 localStorage;无条件清除,不再读取或迁移。
    localStorage.removeItem('savedPass');
    sessionStorage.removeItem('savedPass');
  } catch (e) { }
  if (savedUser) {
    userInput.value = savedUser;
    rememberCb.checked = true;
  } else {
    rememberCb.checked = false;
  }
  var btn = h('button', { class: 'btn btn-primary login-btn', text: '登  录' });

  function submit() {
    if (btn.disabled) return;
    var u = userInput.value.trim();
    var p = pwInput.value;
    if (!u || !p) { errBox.textContent = '请输入账号和密码'; return; }
    btn.disabled = true; btn.textContent = '登录中…';
    errBox.textContent = '';
    api('POST', '/api/auth/login', { username: u, password: p }).then(function (data) {
      if (rememberCb.checked) {
        try {
          localStorage.setItem('savedUser', u);
          localStorage.removeItem('savedPass');
        } catch (e) { }
      } else {
        try {
          localStorage.removeItem('savedUser');
          localStorage.removeItem('savedPass');
        } catch (e) { }
      }
      saveSession(data.token, normalizeProfile(data), rememberCb.checked);
      return refreshSessionProfile().then(refreshAll).then(renderShell);
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
          h('span', { text: '记住我' })),
        errBox,
        btn),
      h('div', { class: 'login-tip', text: '忘记密码请联系本机管理员(托盘菜单可重置)' }))));
  userInput.focus();
}

/* ================= 修改密码 ================= */

function openChangePassword() {
  // 三个字段各占整行,统一竖向排列,避免"确认新密码"孤占两列网格的半格
  var form = buildForm([
    { key: 'oldPassword', label: '旧密码', type: 'password', required: true, span: 'full' },
    { key: 'newPassword', label: '新密码(至少6位)', type: 'password', required: true, span: 'full' },
    { key: 'confirm', label: '确认新密码', type: 'password', required: true, span: 'full' }
  ], {});
  var saving = false, saved = false;
  var m = openModal({
    title: '修改密码',
    body: form.el,
    beforeClose: function () {
      if (saving) return false;
      return saved || !Object.keys(form.inputs).some(function (key) { return !!form.inputs[key].value; }) || confirm('密码尚未保存，确定放弃并关闭吗？');
    },
    footer: [
      h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m.close(); } }),
      h('button', {
        class: 'btn btn-primary', text: '确定修改',
        onclick: function () {
          if (saving) return;
          var data = form.get();
          if (!data) return;
          if (data.newPassword.length < 6 || data.newPassword.length > 256) { form.setError('新密码须为6至256位'); return; }
          if (data.newPassword !== data.confirm) { form.setError('两次输入的新密码不一致'); return; }
          saving = true;
          var button = m.dialog.querySelector('.btn-primary');
          if (button) button.disabled = true;
          POST('/api/auth/change-password', { oldPassword: data.oldPassword, newPassword: data.newPassword })
            .then(function () { saved = true; m.close(true); toast('密码修改成功,下次登录请使用新密码'); })
            .catch(function (e) { form.setError(e.message); }).then(function () {
              saving = false;
              if (button) button.disabled = false;
            });
        }
      })
    ]
  });
}

function openChangeUsername() {
  // 当前账号展示登录账号(USER.username),不是显示名;与修改密码弹窗同样整行竖排
  var current = (USER && USER.username) || '';
  var form = buildForm([
    { key: 'current', label: '当前账号', readonly: true, span: 'full' },
    { key: 'newUsername', label: '新账号', required: true, placeholder: '1至64位字母、数字或 _ . @ -', span: 'full' },
    { key: 'oldPassword', label: '当前密码', type: 'password', required: true, span: 'full' }
  ], {});
  form.inputs.current.value = current;
  var saving = false, saved = false;
  var m = openModal({
    title: '修改账号',
    body: form.el,
    beforeClose: function () {
      if (saving) return false;
      return saved || (!form.inputs.newUsername.value && !form.inputs.oldPassword.value) || confirm('账号尚未保存，确定放弃并关闭吗？');
    },
    footer: [
      h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m.close(); } }),
      h('button', {
        class: 'btn btn-primary', text: '确定修改',
        onclick: function () {
          if (saving) return;
          var data = form.get();
          if (!data) return;
          if (data.newUsername === current) { form.setError('新账号不能与当前账号相同'); return; }
          saving = true;
          var button = m.dialog.querySelector('.btn-primary');
          if (button) button.disabled = true;
          POST('/api/auth/change-username', { newUsername: data.newUsername, oldPassword: data.oldPassword })
            .then(function () {
              saved = true; m.close(true);
              forceLogin('账号已修改，请使用新账号重新登录');
            })
            .catch(function (e) { form.setError(e.message); }).then(function () {
              saving = false;
              if (button) button.disabled = false;
            });
        }
      })
    ]
  });
}

/* ================= 物品借还独立台账 ================= */
function itemLoanStatus(status) {
  return { borrowing: '借用中', returned: '已归还' }[status] || status || '—';
}

function setupItemLoanForm(form, row) {
  if (!row) form.inputs.borrowDate.value = formatCalendarDay(new Date());
  var handler = form.inputs.handlerId;
  var handlerId = row && row.handlerId ? String(row.handlerId) : '';
  if (handlerId && handlerId !== '0') {
    // 同ID改名也保留台账快照；仅克隆并修改当前字段的选项，不影响其他表单。
    var handlerFound = false;
    var handlerOptions = Ctx.personnelOptions.map(function (p) {
      var option = { v: String(p.id), l: p.name + ((p.departmentPath || p.departmentName) ? ' (' + (p.departmentPath || p.departmentName) + ')' : '') };
      if (option.v === handlerId) {
        handlerFound = true;
        option.l = row.handlerName || option.l;
      }
      return option;
    });
    if (!handlerFound) handlerOptions.push({ v: handlerId, l: row.handlerName || '原经手人（已删除或不可见）' });
    handler.setOptions(handlerOptions);
  }
  handler.value = handlerId === '0' ? '' : handlerId;
  var handlerPick = handler.parentElement.querySelector('.pick-btn');
  if (handlerPick) handlerPick.setAttribute('aria-label', '经手人');
  var handlerCard = form.inputs.handlerIdCard;
  function selectedHandlerCard() {
    var id = handler.value;
    if (!id) return '';
    if (row && String(row.handlerId) === id) return row.handlerIdCard || '';
    for (var i = 0; i < Ctx.personnelOptions.length; i++) {
      if (String(Ctx.personnelOptions[i].id) === id) return Ctx.personnelOptions[i].idCard || '';
    }
    return '';
  }
  function refreshHandlerCard() {
    if (handlerCard) handlerCard.value = selectedHandlerCard();
  }
  handler.addEventListener('change', refreshHandlerCard);
  refreshHandlerCard();
  var limits = { itemName: 100, specification: 200, unit: 20, borrowerName: 100, borrowerIdCard: 50, borrowerPhone: 50, borrowerUnit: 200, registrationDepartment: 200, remark: 1000 };
  Object.keys(limits).forEach(function (key) { form.inputs[key].setAttribute('maxlength', limits[key]); });
  itemLoanCfg.form.forEach(function (field) {
    var input = form.inputs[field.key];
    if (field.type === 'date') input = input.querySelector('input');
    if (field.type === 'select' || field.type === 'personnel') input = input.querySelector('.dd-btn');
    if (input) input.setAttribute('aria-label', field.label);
  });
  var get = form.get;
  form.get = function () {
    var data = get.call(form);
    if (!data) return null;
    var quantity = String(form.inputs.quantity.value || '').trim();
    if (!/^(?:\d+(?:\.\d{1,2})?|\.\d{1,2})$/.test(quantity) || !isFinite(data.quantity) || data.quantity <= 0 || data.quantity > 999999999.99) {
      form.setError('数量必须大于0、最多2位小数，且不超过999999999.99');
      return null;
    }
    var invalid = Object.keys(limits).some(function (key) {
      if (String(data[key] || '').length <= limits[key]) return false;
      var field = itemLoanCfg.form.filter(function (f) { return f.key === key; })[0];
      form.setError(field.label + '不能超过' + limits[key] + '字');
      return true;
    });
    if (invalid) return null;
    return data;
  };
}

function openItemLoanDetail(row, actions) {
  var grid = h('div', { class: 'detail-grid' });
  itemLoanCfg.columns.filter(function (field) { return !field.action; }).concat([
    { key: 'remark', label: '用途备注' },
    { key: 'returnCondition', label: '归还情况' },
    { key: 'returnRemark', label: '归还备注' }
  ]).forEach(function (field) {
    var isUnit = field.key === 'borrowerUnit' || field.key === 'registrationDepartment';
    var value = field.key === 'status' ? itemLoanStatus(row.status) : row[field.key];
    // 单位快照显示去斜杠:仅合并" / "路径分隔,不影响自由文本中的其他斜杠(如</script>、24/7)
    if (isUnit && value != null && value !== '') { value = String(value).split(' / ').join(''); }
    grid.appendChild(h('div', { class: 'detail-item' },
      h('div', { class: 'dl', text: field.label }),
      h('div', { class: 'dv', text: value === undefined || value === null || value === '' ? '—' : String(value), title: isUnit ? String(row[field.key] == null ? '' : row[field.key]) : undefined })));
  });
  var footer = [h('button', { class: 'btn btn-ghost', text: '关闭', onclick: function () { m.close(); } })];
  if (row.status !== 'returned' && actions && actions.edit) {
    footer.push(h('button', { class: 'btn btn-primary', text: '编辑', onclick: function () { m.close(); actions.edit(); } }));
  }
  if (row.status !== 'returned' && actions && actions.refresh) {
    footer.push(h('button', { class: 'btn btn-primary', text: '登记归还', onclick: function () { m.close(); openItemLoanReturn(row, actions); } }));
  }
  var m = openModal({ title: '物品借还详情 - ' + (row.itemName || ''), body: grid, wide: true, footer: footer });
}

function openItemLoanReturn(row, actions) {
  if (row.status === 'returned') return;
  var form = buildForm([
    { key: 'returnCondition', label: '归还情况', type: 'textarea', required: true },
    { key: 'returnRemark', label: '归还备注', type: 'textarea' }
  ]);
  form.inputs.returnCondition.setAttribute('aria-label', '归还情况');
  form.inputs.returnRemark.setAttribute('aria-label', '归还备注');
  form.inputs.returnCondition.setAttribute('maxlength', '500');
  form.inputs.returnRemark.setAttribute('maxlength', '1000');
  var saving = false, saved = false;
  var m = openModal({
    title: '整笔归还 · ' + row.itemName,
    body: h('div', {}, h('p', { text: '本次整笔归还 ' + row.quantity + ' ' + row.unit + '，不支持部分归还。' }), form.el),
    beforeClose: function () {
      if (saving) { form.setError('正在保存，请稍候'); return false; }
      if (saved) return true;
      var dirty = form.inputs.returnCondition.value || form.inputs.returnRemark.value;
      return !dirty || confirm('归还登记有未保存的修改，确定放弃并关闭吗？');
    },
    footer: [
      h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m.close(); } }),
      h('button', { class: 'btn btn-primary', text: '确认归还', onclick: function () {
        if (saving) return;
        var data = form.get();
        if (!data) return;
        if (data.returnCondition.length > 500 || data.returnRemark.length > 1000) { form.setError('归还情况不能超过500字，归还备注不能超过1000字'); return; }
        saving = true;
        var controls = $all('input, button, textarea', m.dialog).map(function (el) {
          var entry = { el: el, disabled: el.disabled }; el.disabled = true; return entry;
        });
        POST('/api/item-loans/' + encodeURIComponent(row.id) + '/return', data).then(function () {
          saved = true;
          m.close(true);
          toast('归还登记成功');
          actions.refresh();
        }).catch(function (e) { form.setError(e.message || '归还失败，请重试'); }).then(function () {
          saving = false;
          controls.forEach(function (entry) { entry.el.disabled = entry.disabled; });
        });
      } })
    ]
  });
}

var itemLoanCfg = {
  key: 'item-loans', title: '物品借还', icon: 'box', endpoint: '/api/item-loans', excel: false,
  addLabel: '新增借用登记', clickKey: 'itemName', rowTitle: function (row) { return row.itemName; },
  filters: [
    { key: 'itemName', label: '物品名称' },
    { key: 'borrowerName', label: '借用人' },
    { key: 'registrationDepartment', label: '登记单位' },
    { key: 'status', label: '状态', type: 'select', options: [
      { v: 'borrowing', l: '借用中' }, { v: 'returned', l: '已归还' }
    ] }
  ],
  columns: [
    { key: 'itemName', label: '物品名称' },
    { key: 'quickReturn', label: '快捷归还', action: true, render: function (row) {
      if (row.status === 'returned') return '<span class="badge green">已归还</span>';
      return '<button class="btn btn-primary btn-sm" data-act="return" data-id="' + esc(row.id) + '" aria-label="快捷归还">快捷归还</button>';
    } },
    { key: 'specification', label: '规格型号' },
    { key: 'borrowerUnit', label: '借用人单位', render: function (row) { return esc(String(row.borrowerUnit || '').split(' / ').join('')); } },
    { key: 'borrowerName', label: '借用人' }, { key: 'borrowerIdCard', label: '借用人身份证号' },
    { key: 'borrowerPhone', label: '借用人联系方式' },
    { key: 'registrationDepartment', label: '登记单位', render: function (row) { return esc(String(row.registrationDepartment || '').split(' / ').join('')); } },
    { key: 'handlerName', label: '经手人' },
    { key: 'handlerIdCard', label: '经手人身份证号' },
    { key: 'quantity', label: '数量' }, { key: 'unit', label: '物品单位' },
    { key: 'borrowDate', label: '借出日期' },
    { key: 'status', label: '状态', render: function (row) {
      return '<span class="badge ' + (row.status === 'returned' ? 'green' : 'blue') + '">' + esc(itemLoanStatus(row.status)) + '</span>';
    } }
  ],
  form: [
    { key: 'itemName', label: '物品名称', required: true },
    { key: 'specification', label: '规格型号' },
    { key: 'quantity', label: '数量', type: 'number', required: true, min: 0.01, max: 999999999.99, step: '0.01' },
    { key: 'unit', label: '物品单位', required: true },
    { key: 'borrowerUnit', label: '借用人单位' },
    { key: 'borrowerName', label: '借用人', required: true },
    { key: 'borrowerIdCard', label: '借用人身份证号' },
    { key: 'borrowerPhone', label: '借用人联系方式' },
    { key: 'registrationDepartment', label: '登记单位', required: true },
    { key: 'handlerId', label: '经手人', type: 'personnel', placeholder: '(不指定)' },
    { key: 'handlerIdCard', label: '经手人身份证号', readonly: true },
    { key: 'borrowDate', label: '借出日期', type: 'date', required: true },
    { key: 'remark', label: '用途备注', type: 'textarea', span: 'full' }
  ],
  formSetup: setupItemLoanForm,
  beforeSave: function (data) {
    var payload = {};
    itemLoanCfg.form.forEach(function (field) {
      if (field.key !== 'handlerIdCard') payload[field.key] = data[field.key];
    });
    payload.handlerId = Number(data.handlerId) || 0; // 空选项显式清空；姓名和证件号快照仅由服务端维护。
    return payload; // 仅提交借用表单字段；权限归属由服务端确定，状态、归还字段和旧关联不能混入通用写入。
  },
  onView: openItemLoanDetail,
  rowActions: [
    { key: 'view', label: '查看' },
    { key: 'edit', label: '编辑', visible: function (row) { return row.status !== 'returned'; } },
    { key: 'return', label: '登记归还', visible: function (row) { return row.status !== 'returned'; }, onClick: openItemLoanReturn },
    { key: 'del', label: '删除', danger: true }
  ]
};

/* ================= 双争评比：年度四季度全员矩阵 ================= */
var doubleExcellenceCfg = { key: 'double-excellence', title: '双争评比', icon: 'flag' };
/* 评定等级色彩：好=绿/中=蓝/一般=橙，取系统徽章配色，便于矩阵扫读；未映射的字典新选项用默认色 */
var DE_RATING_CLS = { '好': 'is-good', '中': 'is-mid', '一般': 'is-fair' };
/* 双争评定选项：数据字典 de_rating 启用项（管理员可在"数据字典"维护），字典缺失时回退默认三档 */
function deRatingOptions() {
  var labels = dictLabels('de_rating');
  return labels.length ? labels : ['好', '中', '一般'];
}

function doubleExcellenceFields() {
  return [
    { key: 'year', label: '年份', type: 'number', min: 1900, max: 9999, required: true },
    { key: 'quarter', label: '季度', type: 'select', options: [
      { v: '1', l: '第一季度' }, { v: '2', l: '第二季度' }, { v: '3', l: '第三季度' }, { v: '4', l: '第四季度' }
    ], required: true },
    { key: 'personnelName', label: '姓名', readonly: true, required: true },
    { key: 'idCard', label: '身份证号码', required: true },
    { key: 'rating', label: '评定', type: 'select', options: function () { return deRatingOptions(); }, required: true },
    { key: 'ratingDate', label: '评比日期', type: 'date', required: true },
    { key: 'personnelId', type: 'hidden' }
  ];
}

function doubleExcellencePayload(data) {
  var year = Number(data.year), quarter = Number(data.quarter), pid = Number(data.personnelId);
  if (!isFinite(year) || year % 1 || year < 1900 || year > 9999) throw new Error('请填写有效年份（1900至9999）');
  if (!isFinite(quarter) || quarter < 1 || quarter > 4 || quarter % 1) throw new Error('请选择季度');
  if (!isFinite(pid) || pid < 1 || pid % 1) throw new Error('请先选择现有人员或通过身份证号码完成反查');
  if (deRatingOptions().indexOf(data.rating) < 0) throw new Error('评定只能为' + deRatingOptions().join('、'));
  var day = parseCalendarDay(data.ratingDate);
  if (!day) throw new Error('请填写有效评比日期');
  // 姓名、证号仅用于身份确认，提交的关联始终为现有人员 ID；不创建或伪造档案。
  return { year: year, quarter: quarter, personnelId: pid, rating: data.rating, ratingDate: formatCalendarDay(day) };
}

function doubleExcellenceQuery(filters, recordsOnly) {
  var keys = ['year', 'quarter', 'personnelName', 'idCard', 'departmentId', 'rating'];
  if (recordsOnly) keys = keys.concat(['ratingDateStart', 'ratingDateEnd']);
  return keys.filter(function (key) {
    return filters[key] !== undefined && filters[key] !== null && String(filters[key]).trim() !== ''
      && !(recordsOnly && key === 'rating' && filters[key] === 'unrated');
  }).map(function (key) { return key + '=' + encodeURIComponent(String(filters[key]).trim()); }).join('&');
}

/* ================= 年度矩阵视图工厂：年度全员评定表公共骨架 =================
   双争评比(四季度列)与年度考评(年度评定列)共用：生命周期与会话守卫、筛选面板、
   年份切换、/stats 全量加载与请求代际、单元格/新增表单、历年弹窗、导入导出。
   模块差异(列定义、字段、文案、历年渲染)全部经 cfg 注入。 */
function yearMatrixView(cfg) {
  var endpoint = cfg.endpoint, now = new Date(), currentYear = now.getFullYear();
  var root = h('div', { class: 'double-excellence view-in' }); // 矩阵样式与对齐以双争为公共基准
  var sessionToken = TOKEN, sessionUser = USER && USER.userId;
  var state = { year: currentYear, filters: {},
    total: 0, rows: [], loading: false, generation: 0,
    writing: false, importing: false, confirmingImport: false, formOpen: false, disposed: false };
  var inputs = {}, filterTab, filterPanel, main, toolbar, tableBox, pagerBox, errorBox, count, importInput, importItem;
  var yearPrev, yearNext, yearText;
  var filterOpen = false, closeTimer = null, animations = [], observer = null;
  function alive() { return !state.disposed && TOKEN === sessionToken && USER && USER.userId === sessionUser && document.body.contains(root); }
  function cancelAnimations() {
    if (closeTimer !== null) { clearTimeout(closeTimer); closeTimer = null; }
    animations.forEach(function (cancel) { cancel(); }); animations = [];
  }
  function dispose() {
    if (state.disposed) return;
    state.disposed = true; state.generation++; cancelAnimations();
    if (filterTab) filterTab.remove();
    if (observer) observer.disconnect();
  }
  function animate(el, dx) { var cancel = slideFrom(el, dx); if (typeof cancel === 'function') animations.push(cancel); }
  function toggleFilter() {
    if (!alive()) { dispose(); return; }
    cancelAnimations(); filterOpen = !filterOpen;
    if (filterOpen) {
      filterPanel.classList.add('open'); animate(filterPanel, 270);
      main.classList.add('filter-padding'); animate(main, 270);
    } else {
      filterPanel.style.transition = 'transform ' + MOTION.panel + 'ms ' + MOTION.glide;
      filterPanel.style.transform = 'translateX(100%)';
      main.classList.remove('filter-padding'); animate(main, -270);
      closeTimer = setTimeout(function () {
        closeTimer = null;
        if (!alive()) { dispose(); return; }
        if (filterOpen) return;
        filterPanel.classList.remove('open'); filterPanel.style.transition = ''; filterPanel.style.transform = '';
      }, 310);
    }
    filterTab.classList.toggle('panel-open', filterOpen); filterTab.textContent = filterOpen ? '收 起' : '筛 选';
    filterTab.setAttribute('aria-expanded', String(filterOpen));
  }
  function setYear(year) {
    if (!alive()) return;
    year = Number(year);
    if (!isFinite(year) || year % 1 || year < 1900 || year > 9999 || year === state.year) return;
    state.year = year; load();
  }
  function readFilters() {
    var filters = {};
    ['personnelName', 'idCard', 'departmentId'].forEach(function (key) {
      if (inputs[key]) filters[key] = String(inputs[key].rawValue === undefined ? inputs[key].value || '' : inputs[key].rawValue).trim();
    });
    return filters;
  }
  function applyFilters() {
    if (!alive()) return;
    state.filters = readFilters();
    load();
  }
  function load() {
    if (!alive()) return;
    var generation = ++state.generation;
    state.rows = []; state.total = 0; state.loading = true; errorBox.textContent = '';
    renderData();
    var query = cfg.statQuery({ year: state.year, personnelName: state.filters.personnelName,
      idCard: state.filters.idCard, departmentId: state.filters.departmentId });
    // 与其它模块一致：一次加载全部人员（loadAllRows 自动循环按 200/页取全），矩阵不做分页
    loadAllRows(endpoint + '/stats?' + query).then(function (data) {
      if (!alive() || generation !== state.generation) return;
      state.rows = data && data.list || [];
      state.total = data && data.total || state.rows.length;
      state.loading = false;
      renderData();
    }).catch(function (e) {
      if (!alive() || generation !== state.generation) return;
      state.loading = false;
      errorBox.textContent = e.message || '加载失败，请重试';
      renderData(true);
    });
  }
  function cell(row, text) { row.appendChild(h('td', {}, text == null ? '' : String(text))); }
  // 姓名与身份证号码列：点击查看该人历年记录
  function personCell(tr, row, value) {
    tr.appendChild(h('td', { class: 'de-person-cell' },
      h('button', { type: 'button', class: 'btn-link de-person-btn',
        title: '查看' + row.personnelName + cfg.historyLabel,
        'aria-label': '查看' + row.personnelName + cfg.historyLabel,
        onclick: function () { openHistory(row); } },
        value == null ? '' : String(value))));
  }
  function renderData(failed) {
    tableBox.innerHTML = ''; pagerBox.innerHTML = '';
    yearText.textContent = state.year + ' 年';
    yearPrev.disabled = state.year <= 1900;
    yearNext.disabled = state.year >= 9999;
    count.textContent = state.loading ? '加载中…' : failed ? '加载失败' : '共 ' + state.total + ' 人';
    var table = h('table', { class: 'grid de-grid', 'aria-label': cfg.tableAria });
    var head = h('tr'), body = h('tbody');
    ['单位', '姓名', '身份证号码'].concat(cfg.headers).forEach(function (label) {
      head.appendChild(h('th', { text: label }));
    });
    table.appendChild(h('thead', {}, head)); table.appendChild(body);
    var ctx = { year: state.year, today: formatCalendarDay(now) };
    state.rows.forEach(function (row) {
      var tr = h('tr');
      cell(tr, String(row.departmentPath || '').replace(/\s*\/\s*/g, '')); personCell(tr, row, row.personnelName); personCell(tr, row, row.idCard);
      cfg.rowCells(row, openCellForm, ctx).forEach(function (td) { tr.appendChild(td); });
      body.appendChild(tr);
    });
    if (!state.rows.length) body.appendChild(h('tr', {}, h('td', {
      colspan: cfg.colspan, class: 'empty-row', text: state.loading ? '加载中…' : failed ? '加载失败，请重试' : '暂无人员，请先在「人员管理」中建档'
    })));
    tableBox.appendChild(table);
    // 不分页：全部人员一次展示，滚动即可
  }
  function openCellForm(values, recordId) {
    if (!alive() || state.writing || state.importing || state.formOpen) return;
    openForm(values, { recordId: recordId, fixed: true });
  }
  function afterChange() {
    if (alive()) load();
  }
  // 历年记录：按人员ID取全部记录（服务端 personnelIds 走权限校验），渲染交给模块 cfg
  function openHistory(row) {
    if (!alive()) return;
    var box = h('div', { class: 'de-history' },
      h('div', { class: 'de-history-empty', text: '正在加载 ' + row.personnelName + ' 的' + cfg.historyLabel + '…' }));
    openModal({ title: row.personnelName + ' · ' + cfg.historyLabel, body: box, wide: true });
    // loadAllRows 自动按 200/页取全:历年记录超过 200 条时不再截断
    loadAllRows(endpoint + '?personnelIds=' + encodeURIComponent(row.personnelId)).then(function (data) {
      if (!alive() || !document.body.contains(box)) return;
      cfg.renderHistory(box, row, (data && data.list) || []);
    }).catch(function (e) {
      if (!alive() || !document.body.contains(box)) return;
      box.innerHTML = '';
      box.appendChild(h('div', { class: 'de-history-empty', text: e.message || '加载失败，请关闭后重试' }));
    });
  }
  function openForm(values, options) {
    if (!alive() || state.writing || state.importing || state.formOpen) return;
    state.formOpen = true;
    options = options || {};
    var fixed = !!options.fixed, recordId = options.recordId || null;
    // 新增默认跟随正在查看的年度（而非系统当前年），避免查看历史年度时误录入今年；年度仍可手动调整
    if (!values) values = cfg.newValues(state.year, now);
    var form = buildForm(cfg.fields(), values);
    if (fixed) {
      // 单元格进入：人员与年度（季度）由所在行列锁定，只允许改评定与日期。
      cfg.fixedKeys.forEach(function (key) {
        var input = form.inputs[key];
        if (!input) return;
        // 下拉容器没有 readOnly，用禁用锁住；输入框用只读保持可复制
        if (input.classList && input.classList.contains('dd')) input.disabled = true;
        else input.readOnly = true;
        input.classList.add('ro');
      });
    } else {
      setupPersonLookup(form, { idCard: 'idCard', name: 'personnelName', pid: 'personnelId' });
    }
    var saving = false, saved = false, removing = false;
    var remove = recordId ? h('button', { class: 'btn btn-danger', text: '删除', onclick: function () {
      if (saving || removing) return;
      removing = true;
      confirmBox(cfg.deleteConfirmText(values), true).then(function (yes) {
        if (!yes || !alive()) return false;
        return DEL(endpoint + '/' + encodeURIComponent(recordId)).then(function () { return true; });
      }).then(function (changed) {
        removing = false;
        if (changed) { saved = true; m.close(true); if (alive()) { toast('已删除'); afterChange(); } }
      }).catch(function (e) { removing = false; if (alive()) toast(e.message || '删除失败，请重试', 'err'); });
    } }) : null;
    var save = h('button', { class: 'btn btn-primary', text: '保存', onclick: function () {
      if (saving || removing) return; // 删除进行中同样禁止保存,避免并发写同一条记录
      if (!alive()) { form.setError('页面或登录状态已变更，请关闭后重新进入'); return; }
      var data = form.get(), payload;
      if (!data || form.validatePerson && !form.validatePerson()) return;
      try { payload = cfg.payload(data); } catch (e) { form.setError(e.message); return; }
      saving = true; state.writing = true; save.disabled = true;
      if (remove) remove.disabled = true;
      var controls = $all('input, button, textarea, select', form.el).map(function (el) {
        var old = { el: el, disabled: el.disabled }; el.disabled = true; return old;
      });
      var request = recordId ? PUT(endpoint + '/' + encodeURIComponent(recordId), payload) : POST(endpoint, payload);
      request.then(function () {
        saved = true; m.close(true);
        if (alive()) { toast('保存成功'); afterChange(); }
      }).catch(function (e) { if (alive()) form.setError(e.message || '保存失败，请重试'); }).then(function () {
        saving = false; state.writing = false; save.disabled = false;
        if (remove) remove.disabled = false;
        controls.forEach(function (old) { old.el.disabled = old.disabled; });
      });
    } });
    var footer = [h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { m.close(); } })];
    if (remove) footer.push(remove);
    footer.push(save);
    // 单元格进入：标题点明人员与年度（季度），工具栏"新增"保持通用标题
    var m = openModal({ title: cfg.formTitle(values, recordId, fixed), body: form.el,
      onClose: function () { state.formOpen = false; },
      beforeClose: function () { if ((saving || removing) && !saved) { form.setError('正在保存或删除，请稍候'); return false; } return true; },
      footer: footer });
  }
  function download(path, title) {
    if (!alive()) return;
    var pm = showProgressModal(title);
    pm.setStatus('正在生成文件…');
    apiBlobWithProgress(endpoint + path, function (pct) { pm.setProgress(pct); }).then(function (r) {
      pm.close();
      if (alive()) downloadBlob(r.blob, r.filename);
    }).catch(function (e) { pm.close(); if (alive()) toast(e.message || '下载失败，请重试', 'err'); });
  }
  function exportRecords() {
    if (!alive() || state.loading) return;
    var filters = state.filters;
    // 无勾选与人员范围：导出当前年份＋筛选命中的全部记录（未评定不是记录）。
    var query = cfg.exportQuery({ year: state.year, personnelName: filters.personnelName,
      idCard: filters.idCard, departmentId: filters.departmentId });
    download('/export' + (query ? '?' + query : ''), cfg.exportTitle(state.year));
  }
  function setImportBusy(busy) {
    state.importing = busy;
    if (importInput) importInput.disabled = busy;
    if (importItem) { importItem.disabled = busy; importItem.textContent = busy ? '正在导入…' : '导入 Excel'; }
  }
  function importFile() {
    var file = importInput.files && importInput.files[0]; importInput.value = '';
    if (!file || state.importing || state.writing || !alive()) return;
    if (!/\.xlsx$/i.test(file.name)) { toast('请选择下载模板填写的 .xlsx 文件', 'err'); return; }
    if (file.size > 10 * 1024 * 1024) { toast(cfg.importOversizeMsg, 'err'); return; }
    setImportBusy(true);
    var fd = new FormData(); fd.append('file', file);
    var pm = showProgressModal(cfg.importModalTitle); pm.setStatus('正在上传 ' + file.name + '…');
    postFormWithProgress(endpoint + '/import-excel', fd, function (pct) {
      pm.setProgress(pct * 0.5); pm.setStatus(pct >= 100 ? '正在处理数据…' : '正在上传… ' + pct + '%');
    }).then(function (result) {
      pm.close();
      if (!alive()) return;
      result = result || {};
      var message = importResultSummary(result, false), errors = result.errors || [];
      if (result.error || errors.length) openImportResultModal(message, errors, result.error || '');
      else toast(message, result.failed ? 'info' : 'ok');
      // 部分失败也可能有新增/更新，重新请求当前视图；失败文件仍可重选重试。
      afterChange();
    }).catch(function (e) { pm.close(); if (alive()) toast(e.message || '导入失败，请检查后重试', 'err'); })
      .then(function () { setImportBusy(false); });
  }
  function filterField(key, label, options) {
    var input = options ? makeDropdown(options, '全部') : h('input', { type: 'text', placeholder: '回车搜索' });
    inputs[key] = input;
    input.setAttribute('data-fkey', key);
    input.value = state.filters[key] || '';
    var control = options ? input.querySelector('.dd-btn') : input;
    if (control) control.setAttribute('aria-label', label);
    if (key === 'departmentId') input.classList.add('personnel-path-dropdown');
    if (options) input.addEventListener('change', applyFilters);
    else input.addEventListener('keydown', function (e) { if (e.key === 'Enter') applyFilters(); });
    return h('div', { class: 'filter-field' }, h('label', { text: label }), input);
  }
  function render() {
    // 只在挂载时构建主视图；普通筛选开关不会重建 DOM 或重置滚动。
    $all('.filter-tab-btn', document).forEach(function (el) { el.remove(); });
    $all('.scroll-btns', document).forEach(function (el) { el.remove(); });
    root.innerHTML = ''; inputs = {};
    toolbar = h('div', { class: 'toolbar' });
    // 顶栏已显示模块名，工具栏不再重复标题，直接以年度切换开头
    yearPrev = h('button', { type: 'button', class: 'btn btn-ghost de-year-btn', text: '‹', 'aria-label': cfg.yearAria, title: cfg.yearAria, onclick: function () { setYear(state.year - 1); } });
    yearNext = h('button', { type: 'button', class: 'btn btn-ghost de-year-btn', text: '›', 'aria-label': cfg.yearAria === '上一年' ? '下一年' : '下一年度', title: cfg.yearAria === '上一年' ? '下一年' : '下一年度', onclick: function () { setYear(state.year + 1); } });
    yearText = h('span', { class: 'de-year-text', 'aria-live': 'polite', text: state.year + ' 年' });
    toolbar.appendChild(h('div', { class: 'de-year-nav', role: 'group', 'aria-label': '年份切换' }, yearPrev, yearText, yearNext));
    toolbar.appendChild(h('div', { class: 'spacer' }));
    importInput = h('input', { type: 'file', accept: '.xlsx', style: 'display:none', 'aria-label': cfg.importInputAria });
    importInput.addEventListener('change', importFile);
    var ioWrap = h('div', { class: 'dd io-dd' });
    var ioButton = h('button', { type: 'button', class: 'btn btn-ghost dd-btn io-btn', text: '导入导出 ▾', onclick: function (e) {
      e.stopPropagation(); if (!alive()) return;
      var opening = !ioWrap.classList.contains('open');
      $all('.dd.open, .cal.open').forEach(function (el) { el.classList.remove('open'); });
      ioWrap.classList.toggle('open', opening);
    } });
    importItem = h('button', { type: 'button', class: 'dd-item de-io-item', text: '导入 Excel', onclick: function () {
      ioWrap.classList.remove('open');
      if (!alive() || state.importing || state.writing || state.confirmingImport) return;
      state.confirmingImport = true;
      confirmBox(cfg.importConfirmText)
        .then(function (yes) { state.confirmingImport = false; if (yes && alive() && !state.importing) importInput.click(); })
        .catch(function () { state.confirmingImport = false; });
    } });
    var menu = h('div', { class: 'dd-list io-menu' }, importItem,
      h('button', { type: 'button', class: 'dd-item de-io-item', text: '导出 Excel', title: '导出当前年度与筛选命中的全部记录（未评定不是记录）',
        onclick: function () { ioWrap.classList.remove('open'); exportRecords(); } }));
    menu.addEventListener('click', function (e) { e.stopPropagation(); });
    ioWrap.appendChild(ioButton); ioWrap.appendChild(menu);
    toolbar.appendChild(importInput); toolbar.appendChild(ioWrap);
    toolbar.appendChild(h('button', { class: 'btn btn-ghost', text: '下载模板', onclick: function () { download('/template', cfg.templateTitle); } }));
    toolbar.appendChild(h('button', { class: 'btn btn-primary', text: cfg.addLabel, onclick: function () { openForm(null, null); } }));
    setImportBusy(state.importing);
    count = h('span', { class: 'de-count' });
    errorBox = h('div', { class: 'form-err de-error', role: 'alert' });
    tableBox = h('div', { class: 'table-wrap' }); pagerBox = h('div');
    main = h('div', { class: 'content-main' },
      h('div', { class: 'panel de-toolbar' }, toolbar), errorBox,
      h('div', { class: 'panel' }, h('div', { class: 'de-list-head' }, count), tableBox, pagerBox));
    var filterInner = h('div', { class: 'filter-panel-inner' }, h('h4', { text: '筛选条件' }),
      filterField('personnelName', '姓名'), filterField('idCard', '身份证号码'),
      filterField('departmentId', '单位（含下级）', Ctx.deptFlat.map(function (dept) { return { v: String(dept.id), l: dept.path || dept.name }; })));
    filterInner.appendChild(h('div', { class: 'filter-actions' },
      h('button', { class: 'btn btn-primary', text: '查询', onclick: applyFilters }),
      h('button', { class: 'btn btn-ghost', text: '重置', onclick: function () {
        if (!alive()) return;
        Object.keys(inputs).forEach(function (key) { inputs[key].value = ''; });
        applyFilters();
      } })));
    filterPanel = h('div', { class: 'filter-panel' + (filterOpen ? ' open' : '') }, filterInner);
    root.appendChild(h('div', { class: 'content-layout' }, main, filterPanel));
    filterTab = h('button', { type: 'button', class: 'filter-tab-btn' + (filterOpen ? ' panel-open' : ''), text: filterOpen ? '收 起' : '筛 选',
      'aria-label': cfg.filterTabAria, 'aria-expanded': String(filterOpen), onclick: toggleFilter });
    document.body.appendChild(filterTab);
  }
  return { render: function (container) {
    container.appendChild(root); render(); load();
    observer = new MutationObserver(function () { if (!alive()) dispose(); });
    observer.observe(document.body, { childList: true, subtree: true });
  }, destroy: dispose };
}

/* 双争评比矩阵：一人一行 × 四个季度评定单元格 */
var doubleExcellenceMatrixCfg = {
  endpoint: '/api/double-excellence',
  historyLabel: '历年双争评比',
  tableAria: '双争评比年度四季度表',
  yearAria: '上一年',
  filterTabAria: '切换双争评比筛选面板',
  importInputAria: '选择双争评比 Excel 文件',
  headers: ['第一季度', '第二季度', '第三季度', '第四季度'],
  colspan: 7,
  statQuery: function (filters) { return doubleExcellenceQuery(filters, false); },
  exportQuery: function (filters) { return doubleExcellenceQuery(filters, true); },
  exportTitle: function (year) { return '导出' + year + '年双争评比记录'; },
  importModalTitle: '导入双争评比',
  importOversizeMsg: '双争评比导入文件不能超过 10MiB，请拆分后重试',
  importConfirmText: '请使用六列模板填写。按身份证号码＋年份＋季度匹配：不存在则新增，已存在则更新评定和评比日期。人员必须已建档，评定仅限好、中、一般。确定继续导入吗？',
  templateTitle: '下载双争评比模板',
  addLabel: '新增评比记录',
  newValues: function (year) { return { year: year, quarter: String(Math.floor(new Date().getMonth() / 3) + 1), ratingDate: formatCalendarDay(new Date()) }; },
  fixedKeys: ['year', 'quarter', 'personnelName', 'idCard'],
  fields: function () { return doubleExcellenceFields(); },
  payload: function (data) { return doubleExcellencePayload(data); },
  formTitle: function (values, recordId, fixed) {
    var title = (recordId ? '编辑' : '新增') + ' · 双争评比记录';
    if (fixed && values.personnelName) {
      title = (recordId ? '编辑' : '填写') + '评定：' + values.personnelName + ' ' + values.year + '年第' + values.quarter + '季度';
    }
    return title;
  },
  deleteConfirmText: function (values) { return '确定删除' + values.year + '年第' + values.quarter + '季度的这条评比记录吗？删除后不可恢复。'; },
  rowCells: function (row, openCellForm, ctx) {
    var tds = [];
    for (var quarter = 1; quarter <= 4; quarter++) {
      (function (q) {
        var entry = row['q' + q];
        var ratingCls = entry ? DE_RATING_CLS[entry.rating] : 'is-unrated';
        tds.push(h('td', { class: 'de-cell' },
          h('button', { type: 'button', class: 'de-cell-btn' + (ratingCls ? ' ' + ratingCls : ''),
            'aria-label': row.personnelName + '第' + q + '季度评定：' + (entry ? entry.rating : '未评定'),
            title: entry ? entry.rating + (entry.ratingDate ? '（' + entry.ratingDate + '）' : '') + '，点击编辑' : '点击填写第' + q + '季度评定',
            onclick: function () { openCellForm(cellFormValues(row, q, entry, ctx), entry ? entry.id : null); } },
            // 日期（仅月/日）在前、评定在后同一行紧凑排列、整体靠左；评定字中心线与表头文字对齐，完整日期见悬停提示与历年弹窗
            h('span', { class: 'de-rating-line' },
              entry && entry.ratingDate ? h('small', { text: entry.ratingDate.slice(5).replace('-', '/') }) : null,
              h('b', { text: entry ? entry.rating : '未评定' })))));
      })(quarter);
    }
    return tds;
  },
  renderHistory: function (box, row, list) {
    box.innerHTML = '';
    var years = {};
    list.forEach(function (r) { (years[r.year] = years[r.year] || {})[r.quarter] = r; });
    var yearList = Object.keys(years).map(Number).sort(function (a, b) { return b - a; });
    box.appendChild(h('div', { class: 'de-history-head' },
      h('span', { class: 'de-history-who', text: '身份证号码 ' + (row.idCard || '—') }),
      h('span', { class: 'de-history-count', text: '共 ' + list.length + ' 条评定记录' })));
    if (!yearList.length) {
      box.appendChild(h('div', { class: 'de-history-empty', text: '暂无任何年度的评比记录' }));
      return;
    }
    var table = h('table', { class: 'de-history-grid', 'aria-label': row.personnelName + '历年双争评比表' });
    var head = h('tr');
    ['年度', '第一季度', '第二季度', '第三季度', '第四季度'].forEach(function (label) { head.appendChild(h('th', { text: label })); });
    var body = h('tbody');
    yearList.forEach(function (year) {
      var tr = h('tr');
      tr.appendChild(h('td', { class: 'de-h-year', text: year + ' 年' }));
      for (var quarter = 1; quarter <= 4; quarter++) {
        var entry = years[year][quarter];
        var cls = entry ? (DE_RATING_CLS[entry.rating] || '') : 'is-unrated';
        tr.appendChild(h('td', { class: 'de-h-cell' + (cls ? ' ' + cls : '') },
          h('span', { class: 'de-rating-line' },
            h('b', { text: entry ? entry.rating : '—' }),
            entry && entry.ratingDate ? h('small', { text: entry.ratingDate }) : null)));
      }
      body.appendChild(tr);
    });
    table.appendChild(h('thead', {}, head)); table.appendChild(body);
    box.appendChild(table);
    if (list.length >= 200) box.appendChild(h('div', { class: 'de-history-empty', text: '记录较多，仅显示最近 200 条' }));
  }
};
function cellFormValues(row, quarter, entry, ctx) {
  var values = { year: ctx.year, quarter: String(quarter),
    personnelName: row.personnelName, idCard: row.idCard, personnelId: row.personnelId,
    ratingDate: entry && entry.ratingDate ? entry.ratingDate : ctx.today };
  if (entry) values.rating = entry.rating;
  return values;
}

/* 年度考评矩阵：一人一行 × 年度评定单元格 */
var annualAppraisalMatrixCfg = {
  endpoint: '/api/annual-appraisal',
  historyLabel: '历年年度考评',
  tableAria: '年度考评评定表',
  yearAria: '上一年度',
  filterTabAria: '切换年度考评筛选面板',
  importInputAria: '选择年度考评 Excel 文件',
  headers: ['年度评定'],
  colspan: 4,
  statQuery: function (filters) { return annualAppraisalQuery(filters); },
  exportQuery: function (filters) { return annualAppraisalQuery(filters); },
  exportTitle: function (year) { return '导出' + year + '年年度考评记录'; },
  importModalTitle: '导入年度考评',
  importOversizeMsg: '年度考评导入文件不能超过 10MiB，请拆分后重试',
  importConfirmText: '请使用五列模板填写。按身份证号码＋年度匹配：不存在则新增，已存在则更新评定和考评日期。人员必须已建档，评定选项以数据字典为准。确定继续导入吗？',
  templateTitle: '下载年度考评模板',
  addLabel: '新增考评记录',
  newValues: function (year) { return { year: year, ratingDate: formatCalendarDay(new Date()) }; },
  fixedKeys: ['year', 'personnelName', 'idCard'],
  fields: function () { return annualAppraisalFields(); },
  payload: function (data) { return annualAppraisalPayload(data); },
  formTitle: function (values, recordId, fixed) {
    var title = (recordId ? '编辑' : '新增') + ' · 年度考评记录';
    if (fixed && values.personnelName) {
      title = (recordId ? '编辑' : '填写') + '考评：' + values.personnelName + ' ' + values.year + '年度';
    }
    return title;
  },
  deleteConfirmText: function (values) { return '确定删除' + values.year + '年度的这条考评记录吗？删除后不可恢复。'; },
  rowCells: function (row, openCellForm, ctx) {
    var entry = row.entry;
    var ratingCls = entry ? (AA_RATING_CLS[entry.rating] || '') : 'is-unrated';
    return [h('td', { class: 'de-cell' },
      h('button', { type: 'button', class: 'de-cell-btn' + (ratingCls ? ' ' + ratingCls : ''),
        'aria-label': row.personnelName + ' ' + ctx.year + '年度考评：' + (entry ? entry.rating : '未评定'),
        title: entry ? entry.rating + (entry.ratingDate ? '（' + entry.ratingDate + '）' : '') + '，点击编辑' : '点击填写' + ctx.year + '年度考评',
        onclick: function () {
          var values = { year: ctx.year,
            personnelName: row.personnelName, idCard: row.idCard, personnelId: row.personnelId,
            ratingDate: entry && entry.ratingDate ? entry.ratingDate : ctx.today };
          if (entry) values.rating = entry.rating;
          openCellForm(values, entry ? entry.id : null);
        } },
        // 评定靠左、考评日期（仅月/日）紧随其后；评定字中心线与表头文字对齐，完整日期见悬停提示与历年弹窗
        h('span', { class: 'de-rating-line' },
          h('b', { text: entry ? entry.rating : '未评定' }),
          entry && entry.ratingDate ? h('small', { text: entry.ratingDate.slice(5).replace('-', '/') }) : null)))];
  },
  renderHistory: function (box, row, list) {
    box.innerHTML = '';
    box.appendChild(h('div', { class: 'de-history-head' },
      h('span', { class: 'de-history-who', text: '身份证号码 ' + (row.idCard || '—') }),
      h('span', { class: 'de-history-count', text: '共 ' + list.length + ' 条考评记录' })));
    if (!list.length) {
      box.appendChild(h('div', { class: 'de-history-empty', text: '暂无任何年度的考评记录' }));
      return;
    }
    var table = h('table', { class: 'de-history-grid', 'aria-label': row.personnelName + '历年年度考评表' });
    var head = h('tr');
    ['年度', '评定', '考评日期'].forEach(function (label) { head.appendChild(h('th', { text: label })); });
    var body = h('tbody');
    list.forEach(function (r) {
      var cls = AA_RATING_CLS[r.rating] || '';
      body.appendChild(h('tr', {},
        h('td', { class: 'de-h-year', text: r.year + ' 年' }),
        h('td', { class: 'de-h-cell' + (cls ? ' ' + cls : '') },
          h('span', { class: 'de-rating-line' }, h('b', { text: r.rating || '—' }))),
        h('td', { class: 'de-h-cell' }, r.ratingDate || '—')));
    });
    table.appendChild(h('thead', {}, head)); table.appendChild(body);
    box.appendChild(table);
    if (list.length >= 200) box.appendChild(h('div', { class: 'de-history-empty', text: '记录较多，仅显示最近 200 条' }));
  }
};

function doubleExcellenceView() { return yearMatrixView(doubleExcellenceMatrixCfg); }
function annualAppraisalView() { return yearMatrixView(annualAppraisalMatrixCfg); }


/* ================= 年度考评：年度全员评定表 ================= */
var annualAppraisalCfg = { key: 'annual-appraisal', title: '年度考评', icon: 'fileText' };
/* 评定色彩：优秀=绿/称职=蓝/一般称职=橙/不称职=红，与系统徽章配色一致；未映射的字典新选项用默认色 */
var AA_RATING_CLS = { '优秀': 'is-good', '称职': 'is-mid', '一般称职': 'is-fair', '不称职': 'is-bad' };
/* 年度考评评定选项：数据字典 annual_rating 启用项（管理员可在"数据字典"维护），字典缺失时回退默认四档 */
function annualRatingOptions() {
  var labels = dictLabels('annual_rating');
  return labels.length ? labels : ['优秀', '称职', '一般称职', '不称职'];
}

function annualAppraisalFields() {
  return [
    { key: 'year', label: '年度', type: 'number', min: 1900, max: 9999, required: true },
    { key: 'personnelName', label: '姓名', readonly: true, required: true },
    { key: 'idCard', label: '身份证号码', required: true },
    { key: 'rating', label: '评定', type: 'select', options: function () { return annualRatingOptions(); }, required: true },
    { key: 'ratingDate', label: '考评日期', type: 'date', required: true },
    { key: 'personnelId', type: 'hidden' }
  ];
}

function annualAppraisalPayload(data) {
  var year = Number(data.year), pid = Number(data.personnelId);
  if (!isFinite(year) || year % 1 || year < 1900 || year > 9999) throw new Error('请填写有效年度（1900至9999）');
  if (!isFinite(pid) || pid < 1 || pid % 1) throw new Error('请先选择现有人员或通过身份证号码完成反查');
  var options = annualRatingOptions();
  if (options.indexOf(data.rating) < 0) throw new Error('评定只能为' + options.join('、'));
  var day = parseCalendarDay(data.ratingDate);
  if (!day) throw new Error('请填写有效考评日期');
  // 姓名、证号仅用于身份确认，提交的关联始终为现有人员 ID；不创建或伪造档案。
  return { year: year, personnelId: pid, rating: data.rating, ratingDate: formatCalendarDay(day) };
}

function annualAppraisalQuery(filters) {
  var keys = ['year', 'personnelName', 'idCard', 'departmentId', 'rating'];
  return keys.filter(function (key) {
    return filters[key] !== undefined && filters[key] !== null && String(filters[key]).trim() !== ''
      && !(key === 'rating' && filters[key] === 'unrated');
  }).map(function (key) { return key + '=' + encodeURIComponent(String(filters[key]).trim()); }).join('&');
}


function pendingModuleView(cfg) {
  return {
    render: function (container) {
      container.appendChild(h('div', { class: 'panel' },
        h('div', { class: 'empty-row' },
          h('h3', { text: cfg.title }),
          h('p', { text: '菜单已添加，具体功能待完善。' }))));
    }
  };
}

/* ================= 主框架 ================= */

var MODULES = [personnelCfg, militaryCfg, equipmentCfg, materialCfg, vehicleCfg, leaveCfg, phoneCfg, fitnessCfg, itemLoanCfg, doubleExcellenceCfg, annualAppraisalCfg];
var PENDING_MODULES = [];

/** 侧边栏分组定义:children 为模块key,分组子视图(枪支/大型/小型)仅用于导航 */
var NAV_GROUPS = [
  { key: 'military', icon: 'flag', label: '军事管理', children: [
    { key: 'military', label: '军事考核' },
    { key: 'fitness', label: '体能考核' }
  ] },
  { key: 'asset', icon: 'shield', label: '装备管理', children: [
    { key: 'equipment', label: '全部装备' },
    { key: 'vehicle', label: '车辆管理' },
    { key: 'equipment-gun', label: '枪支管理' },
    { key: 'equipment-large', label: '大型装备' },
    { key: 'equipment-small', label: '小型装备' }
  ] }
];
var NAV_VIEWS = MODULES.concat([equipmentGunCfg, equipmentLargeCfg, equipmentSmallCfg], PENDING_MODULES);
/** 页面 → 所属分组key(用于父级高亮) */
var GROUP_OF = {};
NAV_GROUPS.forEach(function (g) { g.children.forEach(function (c) { GROUP_OF[c.key] = g.key; }); });

function renderShell() {
  if (!TOKEN || !USER) {
    return; // 已被踢回登录页(令牌失效/退出),不再渲染主界面
  }
  var app = $('#app');
  app.innerHTML = '';

  var content = h('div', { class: 'content' });
  var titleEl = h('div', { class: 'topbar-title' });
  var menuItems = {};
  var currentView = 'stats';
  var shellUserId = USER.userId, shellToken = TOKEN;
  var lastViewKey = preferenceKey('lastView'), sidebarKey = preferenceKey('sidebarCollapsed');
  function currentShell() { return TOKEN === shellToken && USER && USER.userId === shellUserId; }

  function navigate(view) {
    if (!currentShell() || !TOKEN || !document.body.contains(content)) return;
    if (!canAccessView(view)) { toast('当前账号无权访问此全局管理页面', 'err'); view = 'stats'; }
    currentView = view;
    refreshDicts(); // 字典可能在别处被修改,切页时刷新缓存保证筛选/表单下拉最新
    // 记住当前页面:URL 锚点(刷新/分享/前进后退)+ localStorage 兜底
    var routeHash = '#' + view;
    if (view === 'personnel' && String(location.hash || '').indexOf('#personnel?') === 0) routeHash = location.hash;
    if (routeHash !== location.hash) {
      try { history.replaceState(null, '', routeHash); } catch (e) { location.hash = routeHash; }
    }
    localStorage.setItem(lastViewKey, view);
    Object.keys(menuItems).forEach(function (k) { menuItems[k].classList.remove('active'); });
    Object.keys(groupParents).forEach(function (k) { groupParents[k].classList.remove('active'); });
    if (menuItems[view]) menuItems[view].classList.add('active');
    var gk = GROUP_OF[view];
    if (gk && groupParents[gk]) { groupParents[gk].classList.add('active'); }

    // 切换页面时清理筛选按钮和滚动按钮(非 CRUD 页面不显示)
    $all('.filter-tab-btn', document).forEach(function (b) { b.remove(); });
    $all('.scroll-btns', document).forEach(function (b) { b.remove(); });

    content.innerHTML = '';
    var comp;
    if (view === 'accounts' && typeof accountsView === 'function') { titleEl.textContent = '账号管理'; comp = accountsView(); }
    else if (view === 'department') { titleEl.textContent = '单位管理'; comp = departmentView(); }
    else if (view === 'dict') { titleEl.textContent = '数据字典'; comp = dictView(); }
    else if (view === 'settings') { titleEl.textContent = '系统设置'; comp = settingsView(); }
    else if (view === 'backup') { titleEl.textContent = '系统设置'; comp = settingsView('backup'); }
    else if (view === 'stats') { titleEl.textContent = '仪表盘'; comp = dashboardView(); }
    else if (view === 'log') { titleEl.textContent = '操作日志'; comp = logView(); }
    else {
      var cfg = null;
      NAV_VIEWS.forEach(function (m) { if (m.key === view) cfg = m; });
      if (!cfg) { cfg = personnelCfg; view = 'personnel'; }
      titleEl.textContent = cfg.title;
      comp = cfg.pending ? pendingModuleView(cfg) : cfg.key === 'double-excellence' ? doubleExcellenceView() : cfg.key === 'annual-appraisal' ? annualAppraisalView() : crudView(cfg);
    }
    comp.render(content);
  }

  var menu = h('div', { class: 'menu' });
  var groupParents = {};
  function addMenu(key, icon, label) {
    if (!canAccessView(key)) return;
    var item = h('div', {
      class: 'menu-item', 'data-tip': label,
      onclick: function () { navigate(key); }
    },
      h('span', { class: 'ic', html: iconSvg(icon) }), h('span', { class: 'txt', text: label }));
    menu.appendChild(item);
    menuItems[key] = item;
  }
  /** 分组菜单:点击父级展开/收起,子项进入对应页面 */
  function addGroupMenu(g) {
    var open = localStorage.getItem('menuGroup:' + g.key) !== '0';
    var arrow = h('span', { class: 'ga', text: '▸' });
    var children = h('div', { class: 'menu-sub' + (open ? ' open' : '') });
    var item = h('div', {
      class: 'menu-item group' + (open ? ' open' : ''), 'data-tip': g.label,
      onclick: function () {
        open = !open;
        item.classList.toggle('open', open);
        children.classList.toggle('open', open);
        localStorage.setItem('menuGroup:' + g.key, open ? '1' : '0');
      }
    },
      h('span', { class: 'ic', html: iconSvg(g.icon) }),
      h('span', { class: 'txt', text: g.label }),
      arrow);
    g.children.forEach(function (c) {
      var sub = h('div', {
        class: 'menu-item sub', 'data-tip': c.label,
        onclick: function (e) { e.stopPropagation(); navigate(c.key); }
      },
        h('span', { class: 'dot' }),
        h('span', { class: 'txt', text: c.label }));
      children.appendChild(sub);
      menuItems[c.key] = sub;
    });
    menu.appendChild(item);
    menu.appendChild(children);
    groupParents[g.key] = item;
  }
  addMenu('stats', 'chart', '仪表盘');   // 仪表盘置顶
  addMenu('personnel', personnelCfg.icon, '人员管理');
  NAV_GROUPS.forEach(addGroupMenu);
  [materialCfg, leaveCfg, phoneCfg, itemLoanCfg, doubleExcellenceCfg, annualAppraisalCfg].forEach(function (m) { addMenu(m.key, m.icon, m.title); });
  PENDING_MODULES.forEach(function (m) { addMenu(m.key, m.icon, m.title); });
  addMenu('department', 'building', '单位管理');
  addMenu('accounts', 'users', '账号管理');
  addMenu('dict', 'book', '数据字典');
  addMenu('settings', 'shield', '系统设置');
  addMenu('log', 'fileText', '操作日志');

  var who = (USER && (USER.displayName || USER.username)) || '管理员';
  var logoutFn = function () {
    POST('/api/auth/logout').catch(function (err) { /* 忽略 */ }).then(function () {
      forceLogin('已退出登录');
    });
  };
  var topbar = h('div', { class: 'topbar' },
    titleEl,
    h('div', { class: 'topbar-user' },
      h('span', { class: 'who', text: who }),
      h('div', { class: 'topbar-avatar-wrap' },
        h('button', {
          class: 'topbar-avatar', type: 'button', 'aria-label': '账号菜单', 'aria-haspopup': 'true', 'aria-expanded': 'false',
          title: '账号菜单', text: who.slice(0, 1).toUpperCase(),
          onclick: function (event) {
            event.stopPropagation();
            var open = avatarMenu.classList.toggle('open');
            this.setAttribute('aria-expanded', open ? 'true' : 'false');
          }
        }),
        h('div', { class: 'topbar-menu', role: 'menu' },
          h('button', { type: 'button', class: 'topbar-menu-item', role: 'menuitem', text: '修改账号', onclick: function () { closeMenu(); openChangeUsername(); } }),
          h('button', { type: 'button', class: 'topbar-menu-item', role: 'menuitem', text: '修改密码', onclick: function () { closeMenu(); openChangePassword(); } }),
          h('button', { type: 'button', class: 'topbar-menu-item danger', role: 'menuitem', text: '退出登录', onclick: function () { closeMenu(); logoutFn(); } })))));

  // 注意:不能叫 menu,renderShell 上文已有侧边栏的 var menu;这里改名避免覆盖
  var avatarMenu = topbar.querySelector('.topbar-menu');
  function closeMenu() {
    avatarMenu.classList.remove('open');
    var trigger = topbar.querySelector('.topbar-avatar');
    if (trigger) trigger.setAttribute('aria-expanded', 'false');
  }
  document.addEventListener('click', closeMenu);
  document.addEventListener('keydown', function (event) { if (event.key === 'Escape') closeMenu(); });

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

  var logoEl = h('div', { class: 'sidebar-logo' },
      SITE.logo
        ? h('img', { class: 'logo-img', src: SITE.logo, alt: 'LOGO' })
        : h('span', { class: 'star', text: '★' }),
      h('div', {},
        h('div', { class: 't1', text: SITE.name || '数智军营' }),
        h('div', { class: 't2', text: SITE.subtitle || '人员管理系统' })));
  // 彩蛋:连点品牌区5次(2.2秒内)弹出致敬彩蛋(typeof 保护,独立测试环境未注入彩蛋函数时不影响壳层渲染)
  if (typeof attachEasterEgg === 'function') {
    attachEasterEgg(logoEl, function () { if (typeof openEasterEgg === 'function') { openEasterEgg(); } });
  }
  var sideEl = h('div', { class: 'sidebar' },
    logoEl,
    menu,
    h('div', { class: 'sidebar-toggle' },
      toggleBtn));

  var mainEl = h('div', { class: 'main' }, topbar, content);
  var SIDEBAR_W = 208, SIDEBAR_MINI = 64;

  // 窗口太窄自动收起菜单(与手动收起同一动画),变宽自动展开;900/960滞回避免临界抖动
  window.addEventListener('resize', function () {
    if (!currentShell() || !document.body.contains(sideEl)) return;
    var w = window.innerWidth;
    var collapsed = sideEl.classList.contains('collapsed');
    if (w < 900 && !collapsed && !manualExpand) { setCollapsed(true); }
    else if (w >= 960) {
      if (collapsed) { setCollapsed(false); }
      manualExpand = false;   // 回到宽屏,恢复自动接管
    }
  });

  function setCollapsed(collapsed) {
    if (!currentShell()) return;
    var was = sideEl.classList.contains('collapsed');
    sideEl.classList.toggle('collapsed', collapsed);
    document.body.classList.toggle('side-mini', collapsed);
    localStorage.setItem(sidebarKey, collapsed ? '1' : '0');
    toggleIcon.innerHTML = iconSvg(collapsed ? 'chevronR' : 'chevronL');
    toggleTxt.textContent = collapsed ? '展开菜单' : '收起菜单';
    toggleBtn.setAttribute('data-tip', collapsed ? '展开菜单' : '收起菜单');
    // 侧边栏由 CSS transform 过渡平移;主区 margin 瞬切 + FLIP transform 滑动,两者同步
    if (was !== collapsed) {
      slideFrom(mainEl, collapsed ? (SIDEBAR_W - SIDEBAR_MINI) : -(SIDEBAR_W - SIDEBAR_MINI));
    }
  }
  setCollapsed(localStorage.getItem(sidebarKey) === '1');

  app.appendChild(h('div', { class: 'layout' }, sideEl, mainEl));

  // 浏览器前进/后退键切换模块
  window.addEventListener('hashchange', function () {
    var v = (location.hash || '').replace('#', '').split('?')[0];
    if ((!canAccessView(v) || menuItems[v] || v === 'backup') && (v !== currentView || v === 'personnel')) navigate(v);
  });

  // 恢复上次所在页面:优先 URL 锚点,其次 localStorage
  var initialView = (location.hash || '').replace('#', '').split('?')[0];
  var validViews = Object.keys(menuItems);
  if (canAccessView('backup')) validViews.push('backup'); // 旧书签路由,不再有独立菜单项。
  if (!canAccessView(initialView)) initialView = 'stats';
  if (validViews.indexOf(initialView) < 0) {
    initialView = localStorage.getItem(lastViewKey) || 'stats';
  }
  if (validViews.indexOf(initialView) < 0) {
    initialView = 'personnel';
  }
  navigate(initialView);
}

/* ================= 启动 ================= */

(function boot() {
  try { localStorage.removeItem('savedPass'); sessionStorage.removeItem('savedPass'); } catch (e) { }
  // 站点外观(标题/LOGO/主题色)免登录可用,登录页也要显示
  var siteReady = loadSiteSettings();
  if (!TOKEN) { siteReady.then(function () { renderLogin(); }); return; }
  Promise.all([siteReady]).then(function () {
    return refreshSessionProfile();
  }).then(function () {
    return refreshAll().catch(function (e) { if (TOKEN) toast(e.message, 'err'); });
  }).then(function () {
    if (TOKEN && USER) renderShell();
  }).catch(function (e) {
    if (TOKEN) forceLogin('无法验证登录状态:' + e.message);
  });
})();
