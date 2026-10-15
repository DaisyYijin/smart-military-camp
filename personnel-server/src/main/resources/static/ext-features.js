/**
 * 扩展功能脚本(独立于 app.js,原生 ES5,零第三方依赖)
 * ------------------------------------------------------------
 * 本文件承载后加的界面增强功能,复用 app.js 暴露的全局函数
 * (GET/h/toast/esc/openModal/showProgressModal/apiBlobWithProgress/downloadBlob),
 * 须在 app.js 之后加载。
 *
 * 当前包含:
 *   1) 侧栏分组子菜单展开/收起动画(高度+透明度过渡,配合 app.css 的 .menu-sub 规则)
 *   2) 按名称隐藏分组子菜单(如「装备管理」下的「全部装备」);要恢复删掉对应名称即可
 *   3) 人员管理工具栏左上角:点名册下载 / 花名册下载
 *      - 点名册:后端生成 Excel(单位→班两级矩阵、姓名纵填、不在位原因左侧、休假归队时间)
 *      - 花名册:先弹窗勾选需要的字段(基本档案字段,不含任职经历等子表),后端生成 Excel
 *      两者均遵循页面当前筛选条件,横版 A4、公文字体(小标宋/黑体/仿宋)
 */
(function () {
  'use strict';

  /** 需要隐藏的侧栏分组子菜单名称(精确匹配菜单文字) */
  var HIDDEN_SUBMENUS = ['全部装备'];

  /* ================= 1. 侧栏分组子菜单动画 ================= */

  /** 展开:0 → 实际高度,结束后放开为 auto 以适应子项增减 */
  function openSub(el) {
    if (el._ht) { clearTimeout(el._ht); el._ht = 0; }
    el.style.height = el.scrollHeight + 'px';
    el._ht = setTimeout(function () {
      el._ht = 0;
      if (el.classList.contains('open')) el.style.height = 'auto';
    }, 260);
  }
  /** 收起:先固定当前高度(强制回流)再过渡到 0 */
  function closeSub(el) {
    if (el._ht) { clearTimeout(el._ht); el._ht = 0; }
    if (!el.style.height || el.style.height === 'auto') {
      el.style.height = el.scrollHeight + 'px';
      void el.offsetWidth; // 强制回流,确保从当前高度过渡到 0
    }
    el.style.height = '0px';
  }

  // 菜单由 app.js 在登录后动态渲染:新增 .menu-sub 节点时,已展开的直接置为 auto(不播动画);
  // 同时按 HIDDEN_SUBMENUS 移除需要隐藏的子菜单项(在渲染后、绘制前执行,用户不可见)
  new MutationObserver(function (muts) {
    for (var i = 0; i < muts.length; i++) {
      var added = muts[i].addedNodes;
      for (var j = 0; j < added.length; j++) {
        var n = added[j];
        if (n.nodeType !== 1) continue;
        var subs = n.classList && n.classList.contains('menu-sub') ? [n] :
          (n.querySelectorAll ? n.querySelectorAll('.menu-sub') : []);
        for (var k = 0; k < subs.length; k++) {
          if (subs[k].classList.contains('open')) subs[k].style.height = 'auto';
          var items = subs[k].querySelectorAll('.menu-item.sub');
          for (var m = 0; m < items.length; m++) {
            var txt = items[m].querySelector('.txt');
            if (txt && HIDDEN_SUBMENUS.indexOf(String(txt.textContent || '').trim()) >= 0) {
              items[m].remove();
            }
          }
        }
      }
    }
  }).observe(document.body, { childList: true, subtree: true });

  // 事件委托:分组父级点击(app.js 已同步切换 .open 与本地存储)后按最终状态播放动画
  document.addEventListener('click', function (e) {
    var item = e.target && e.target.closest ? e.target.closest('.menu-item.group') : null;
    if (!item) return;
    var sub = item.nextElementSibling;
    if (!sub || !sub.classList.contains('menu-sub')) return;
    if (sub.classList.contains('open')) openSub(sub); else closeSub(sub);
  });

  /* ================= 3. 人员管理新增「学历情况」表单字段 ================= */

  /** 学历情况为普通表单字段(每人一套,直接填写);因 app.js 受写入通道限制,由本脚本注入到人员表单 */
  (function registerEduFields() {
    if (typeof personnelCfg === 'undefined' || !personnelCfg || !personnelCfg.form) {
      return; // app.js 未加载或结构变化时静默跳过
    }
    // 已注册过则跳过(避免重复)
    for (var i = 0; i < personnelCfg.form.length; i++) {
      if (personnelCfg.form[i] && personnelCfg.form[i].key === 'eduSchool') {
        return;
      }
    }
    var idx = -1;
    for (var j = 0; j < personnelCfg.form.length; j++) {
      if (personnelCfg.form[j] && personnelCfg.form[j].section === '任职经历') { idx = j; break; }
    }
    var fields = [
      { section: '学历情况' },
      { key: 'eduEnrollDate', label: '入学时间', type: 'date' },
      { key: 'eduGraduateDate', label: '毕业时间', type: 'date' },
      { key: 'eduForm', label: '学历形式', type: 'select', options: function () { return dictLabels('edu_form'); } },
      { key: 'eduSchool', label: '毕业院校' },
      { key: 'eduMajor', label: '所学专业' },
      { key: 'eduDegree', label: '学位', type: 'select', options: function () { return dictLabels('edu_degree'); }, hint: '选项在数据字典「学位」中维护' },
      { key: 'eduCertNo', label: '毕业证号' }
    ];
    if (idx >= 0) {
      personnelCfg.form.splice.apply(personnelCfg.form, [idx, 0].concat(fields));
    } else {
      personnelCfg.form.push.apply(personnelCfg.form, fields);
    }
  })();

  /* ================= 2. 点名册 / 花名册下载 ================= */

  /** 读取右侧筛选面板当前条件(与列表查询同口径:非空才参与) */
  function readFilterParams() {
    var out = {};
    var els = document.querySelectorAll('[data-fkey]');
    for (var i = 0; i < els.length; i++) {
      var v = String(els[i].value || '').trim();
      if (v) out[els[i].getAttribute('data-fkey')] = v;
    }
    return out;
  }

  function toQueryString(obj) {
    return Object.keys(obj || {}).map(function (k) {
      return k + '=' + encodeURIComponent(obj[k]);
    }).join('&');
  }

  /** 通用:从后端下载 Excel(带进度) */
  function downloadExcel(url, pmTitle, pm) {
    return apiBlobWithProgress(url, function (pct) {
      pm.setProgress(pct * 0.9);
      pm.setStatus('正在下载… ' + pct + '%');
    }).then(function (r) {
      pm.setProgress(100);
      pm.setStatus('下载完成');
      downloadBlob(r.blob, r.filename);
      setTimeout(function () { pm.close(); }, 600);
    }).catch(function (e) {
      pm.close();
      toast(e.message, 'err');
    });
  }

  /** 点名册:后端生成 Excel 并下载 */
  function downloadRollcall() {
    var pm = showProgressModal('生成点名册');
    pm.setStatus('正在按当前筛选条件生成…');
    var qs = toQueryString(readFilterParams());
    downloadExcel('/api/personnel/rollcall/excel' + (qs ? '?' + qs : ''), '点名册', pm);
  }

  /** 花名册默认不预选任何字段(由用户自行点选) */
  var ROSTER_DEFAULT = [];

  /** 花名册:先弹窗按钮式选取字段,再由后端生成 Excel 下载 */
  function openRosterDialog() {
    GET('/api/personnel/roster/fields').then(function (list) {
      list = list || [];
      if (!list.length) {
        toast('获取字段清单失败', 'err');
        return;
      }
      var chips = [];
      var grid = h('div', { style: 'display:flex;flex-wrap:wrap;gap:8px;max-height:320px;overflow:auto;padding:4px 2px' });
      var dlBtn = null;
      function selectedKeys() {
        return chips.filter(function (c) { return c.on; }).map(function (c) { return c.key; });
      }
      function syncCount() {
        if (dlBtn) dlBtn.textContent = '下载 Excel(' + selectedKeys().length + '列)';
      }
      function setAll(v) {
        chips.forEach(function (c) {
          c.on = v;
          c.chip.classList.toggle('on', v);
        });
        syncCount();
      }
      list.forEach(function (f) {
        var entry = { key: f.key, on: ROSTER_DEFAULT.indexOf(f.key) >= 0, chip: null };
        entry.chip = h('button', {
          type: 'button', class: 'field-chip' + (entry.on ? ' on' : ''), text: f.label,
          title: entry.on ? '点击取消该列' : '点击选中该列',
          onclick: function () {
            entry.on = !entry.on;
            entry.chip.classList.toggle('on', entry.on);
            syncCount();
          }
        });
        chips.push(entry);
        grid.appendChild(entry.chip);
      });
      var modal = null;
      modal = openModal({
        title: '花名册下载 - 点击按钮选择需要的字段',
        body: h('div', {},
          h('div', { style: 'display:flex;align-items:center;gap:10px;margin-bottom:10px' },
            h('span', { style: 'color:#66707f;font-size:12px', text: '点击字段按钮选中/取消(蓝色=选中):' }),
            h('button', {
              type: 'button', class: 'btn btn-ghost btn-sm', text: '全选',
              onclick: function () { setAll(true); }
            }),
            h('button', {
              type: 'button', class: 'btn btn-ghost btn-sm', text: '全不选',
              onclick: function () { setAll(false); }
            })),
          grid),
        footer: [
          h('button', {
            class: 'btn btn-ghost', text: '取消',
            onclick: function () { modal.close(); }
          }),
          h('button', {
            class: 'btn btn-primary', text: '下载 Excel',
            onclick: function () {
              var keys = selectedKeys();
              if (!keys.length) {
                toast('请至少选择一个字段', 'err');
                return;
              }
              modal.close();
              var pm = showProgressModal('生成花名册');
              pm.setStatus('正在按当前筛选条件生成…');
              var f = readFilterParams();
              f.fields = keys.join(',');
              downloadExcel('/api/personnel/roster/excel?' + toQueryString(f), '花名册', pm);
            }
          })
        ]
      });
      dlBtn = modal.dialog.querySelector('.dialog-foot .btn-primary');
      syncCount();
    }).catch(function (e) {
      toast(e.message, 'err');
    });
  }

  /** 人员管理视图渲染后,在工具栏最左侧(spacer 之前)插入两个下载按钮 */
  function addPrintButtons() {
    var toolbars = document.querySelectorAll('.content .toolbar');
    for (var i = 0; i < toolbars.length; i++) {
      var toolbar = toolbars[i];
      if (toolbar.getAttribute('data-printbtns')) continue;
      // 人员管理工具栏的独有标记:「删除撤回」按钮(personnelCfg.toolbarExtra 注入)
      var btns = toolbar.querySelectorAll('button');
      var isPersonnel = false;
      for (var j = 0; j < btns.length; j++) {
        if (btns[j].textContent.indexOf('删除撤回') >= 0) { isPersonnel = true; break; }
      }
      if (!isPersonnel) continue;
      toolbar.setAttribute('data-printbtns', '1');
      var grp = h('div', { class: 'toolbar-left' });
      grp.appendChild(h('button', {
        class: 'btn btn-ghost', text: '点名册下载',
        title: '按当前筛选条件生成点名册 Excel 并下载(横版A4版式,含休假归队时间)',
        onclick: function () { downloadRollcall(); }
      }));
      grp.appendChild(h('button', {
        class: 'btn btn-ghost', text: '花名册下载',
        title: '先勾选需要的字段(姓名/身份证/部门/所在点位等),再生成花名册 Excel 下载',
        onclick: function () { openRosterDialog(); }
      }));
      toolbar.insertBefore(grp, toolbar.firstChild); // 在 spacer 之前 → 工具栏左上角
    }
  }

  // 视图切换/重渲染后补挂按钮(去抖,等 DOM 稳定)
  var btnTimer = 0;
  new MutationObserver(function () {
    clearTimeout(btnTimer);
    btnTimer = setTimeout(addPrintButtons, 50);
  }).observe(document.body, { childList: true, subtree: true });
})();
