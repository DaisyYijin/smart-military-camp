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

  /* ================= 4. 军事考核:方案化加权评分 ================= */

  /** 方案缓存:[{id,name,remark,items:[{id,name,weight,children:[...]}]}] */
  var MIL_SCHEMES = [];

  function loadMilSchemes() {
    return GET('/api/military/schemes').then(function (list) {
      MIL_SCHEMES = list || [];
      return MIL_SCHEMES;
    });
  }

  /** 方案的叶子课目(带有效占比):顶级无子项→自身占比;有子项→顶级占比×子项占比/100 */
  function milLeaves(scheme) {
    var out = [];
    (scheme && scheme.items ? scheme.items : []).forEach(function (top) {
      var tw = Number(top.weight) || 0;
      var kids = top.children || [];
      if (!kids.length) {
        out.push({ name: top.name, weight: tw, label: top.name + '(' + trimNumJs(tw) + '%)' });
      } else {
        kids.forEach(function (c) {
          var eff = Math.round(tw * (Number(c.weight) || 0)) / 100;
          out.push({ name: top.name + '·' + c.name, weight: eff, label: top.name + '·' + c.name + '(' + trimNumJs(eff) + '%)' });
        });
      }
    });
    return out;
  }

  function trimNumJs(v) {
    return Math.round(v * 10) / 10;
  }

  function milDetailText(r) {
    if (!r || !r.detail) { return r && r.score ? r.score : '—'; }
    try {
      var rows = JSON.parse(r.detail);
      return rows.map(function (x) { return x.name + trimNumJs(Number(x.score)); }).join('·');
    } catch (e) {
      return r.score || '—';
    }
  }

  /** 动态课目成绩录入状态(单表单内唯一) */
  var MIL_FORM_STATE = { leaves: [], inputs: [], totalEl: null, container: null };

  function renderMilLeafInputs(container, scheme, preset) {
    MIL_FORM_STATE.leaves = milLeaves(scheme);
    MIL_FORM_STATE.inputs = [];
    container.innerHTML = '';
    var presetMap = {};
    (preset || []).forEach(function (p) { presetMap[p.name] = p.score; });
    MIL_FORM_STATE.leaves.forEach(function (leaf) {
      var row = h('div', { style: 'display:flex;align-items:center;gap:10px;margin-bottom:8px' });
      var lab = h('div', { style: 'flex:1;min-width:0;font-size:13px', text: leaf.label });
      var input = h('input', { type: 'number', min: '0', max: '100', step: '0.5', placeholder: '0-100' });
      input.style.cssText = 'width:110px;padding:7px 9px;border:1px solid #d4dce8;border-radius:4px';
      if (presetMap[leaf.name] !== undefined && presetMap[leaf.name] !== '') { input.value = presetMap[leaf.name]; }
      row.appendChild(lab);
      row.appendChild(input);
      container.appendChild(row);
      MIL_FORM_STATE.inputs.push({ leaf: leaf, el: input });
    });
    var total = h('div', { style: 'margin-top:4px;padding:10px 12px;background:#f0f5fb;border-radius:6px;font-size:14px;font-weight:600' });
    MIL_FORM_STATE.totalEl = total;
    container.appendChild(total);
    function sync() {
      var sum = 0, missing = 0;
      MIL_FORM_STATE.inputs.forEach(function (x) {
        var v = parseFloat(x.el.value);
        if (isNaN(v)) { missing++; } else { sum += v * x.leaf.weight / 100; }
      });
      var t = Math.round(sum * 10) / 10;
      var g = t >= 90 ? '优秀' : t >= 80 ? '良好' : t >= 60 ? '及格' : '不及格';
      total.textContent = missing ? '百分制总分:—(还有 ' + missing + ' 项未填,按 0 分计则为 ' + t + ' 分/' + g + ')'
        : '百分制总分:' + t + ' 分    预评等级:' + g;
    }
    MIL_FORM_STATE.inputs.forEach(function (x) {
      x.el.addEventListener('input', sync);
      x.el.addEventListener('change', sync);
    });
    sync();
  }

  function setupMilitaryModule() {
    if (typeof militaryCfg === 'undefined' || !militaryCfg) {
      return; // app.js 未加载时跳过
    }

    militaryCfg.columns = [
      { key: 'personnelName', label: '姓名' },
      { key: 'idCard', label: '身份证号', render: function (r) { return r.idCard || '—'; } },
      { key: 'item', label: '考核方案' },
      { key: 'detailInfo', label: '课目明细', render: function (r) { return milDetailText(r); } },
      { key: 'points', label: '总分(百分制)' },
      { key: 'grade', label: '评定', render: function (r) { return badge(r.grade); } },
      { key: 'testDate', label: '考核日期' }
    ];

    militaryCfg.form = [
      { key: 'idCard', label: '身份证号', required: true, placeholder: '输入后自动带出姓名' },
      { key: 'personnelName', label: '姓名', readonly: true, placeholder: '按身份证号自动获取' },
      { key: 'personnelId', type: 'hidden', required: true },
      { key: 'schemeName', label: '考核方案', type: 'select', required: true,
        options: function () { return MIL_SCHEMES.map(function (s) { return s.name; }); },
        hint: '方案与各课目占比在「考核方案设置」中维护' },
      { key: 'testDate', label: '考核日期', type: 'date', required: true },
      { key: 'remark', label: '备注', type: 'textarea', span: 'full' }
    ];

    militaryCfg.formSetup = function (form, row) {
      var schemeEl = form.inputs['schemeName'];
      var container = h('div', { style: 'grid-column: 1 / -1; margin-top:4px' });
      MIL_FORM_STATE.container = container;
      var head = h('div', { style: 'grid-column: 1 / -1;font-weight:600;margin-top:6px;padding-top:8px;border-top:1px dashed #d4dce8', text: '课目成绩(百分制,按方案占比加权)' });
      form.el.appendChild(head);
      form.el.appendChild(container);
      function schemeByName(n) {
        for (var i = 0; i < MIL_SCHEMES.length; i++) {
          if (MIL_SCHEMES[i].name === n) { return MIL_SCHEMES[i]; }
        }
        return null;
      }
      function refresh(preset) {
        var s = schemeByName(String(schemeEl.value || ''));
        container.innerHTML = '';
        MIL_FORM_STATE.leaves = [];
        MIL_FORM_STATE.inputs = [];
        if (!s) {
          container.appendChild(h('div', { style: 'color:#8a94a6;font-size:12px', text: MIL_SCHEMES.length ? '请先选择考核方案' : '暂无考核方案,请先在工具栏「考核方案设置」中创建' }));
          return;
        }
        renderMilLeafInputs(container, s, preset);
      }
      schemeEl.addEventListener('change', function () { refresh(null); });
      var presetDetail = null;
      try { presetDetail = row && row.detail ? JSON.parse(row.detail) : null; } catch (e) { presetDetail = null; }
      loadMilSchemes().then(function () {
        // 编辑时回填方案与各项成绩
        if (row && row.item && schemeByName(row.item) && !schemeEl.value) {
          schemeEl.value = row.item;
        }
        refresh(presetDetail);
      });
    };

    militaryCfg.beforeSave = function (data) {
      var scheme = null;
      for (var i = 0; i < MIL_SCHEMES.length; i++) {
        if (MIL_SCHEMES[i].name === data.schemeName) { scheme = MIL_SCHEMES[i]; break; }
      }
      if (!scheme) {
        toast('请选择考核方案', 'err');
        return null;
      }
      var rows = [];
      for (var j = 0; j < MIL_FORM_STATE.leaves.length; j++) {
        var leaf = MIL_FORM_STATE.leaves[j];
        var el = MIL_FORM_STATE.inputs[j] ? MIL_FORM_STATE.inputs[j].el : null;
        var v = el ? parseFloat(el.value) : NaN;
        if (isNaN(v) || v < 0 || v > 100) {
          toast('「' + leaf.label + '」成绩需填写0-100的分数', 'err');
          return null;
        }
        rows.push({ name: leaf.name, weight: leaf.weight, score: v });
      }
      if (!rows.length) {
        toast('该方案没有课目,请先在「考核方案设置」中配置', 'err');
        return null;
      }
      data.schemeId = scheme.id;
      data.detail = JSON.stringify(rows);
      data.item = scheme.name;
      delete data.schemeName;
      delete data.idCard;
      delete data.personnelName;
      delete data.points;
      delete data.grade;
      delete data.score;
      delete data.category;
      return data;
    };

    militaryCfg.toolbarExtra = function (bar) {
      bar.appendChild(h('button', {
        class: 'btn btn-ghost', text: '考核方案设置',
        title: '维护考核方案:顶级课目与占比、实操等课目下的子项与占比(各层级合计须为100%)',
        onclick: function () { openMilSchemeManager(); }
      }));
    };
  }

  /** 考核方案管理弹窗 */
  function openMilSchemeManager() {
    loadMilSchemes().then(function () { renderMilSchemeList(); }).catch(function (e) { toast(e.message, 'err'); });
  }

  function milSchemeSummary(s) {
    return (s.items || []).map(function (top) {
      var kids = (top.children || []).map(function (c) { return c.name + trimNumJs(Number(c.weight)) + '%'; }).join('/');
      return top.name + trimNumJs(Number(top.weight)) + '%' + (kids ? '{' + kids + '}' : '');
    }).join(' + ');
  }

  function renderMilSchemeList() {
    var body = h('div');
    var list = h('div');
    MIL_SCHEMES.forEach(function (s) {
      var row = h('div', { style: 'display:flex;align-items:center;gap:10px;padding:10px 6px;border-bottom:1px solid #eef1f6' },
        h('div', { style: 'flex:1;min-width:0' },
          h('div', { style: 'font-weight:600', text: s.name }),
          h('div', { style: 'color:#66707f;font-size:12px;margin-top:2px', text: milSchemeSummary(s) })),
        h('button', { class: 'btn btn-ghost btn-sm', text: '编辑', onclick: function () { openMilSchemeEditor(s); } }),
        h('button', {
          class: 'btn btn-ghost btn-sm', text: '删除',
          onclick: function () {
            confirmBox('确定删除方案「' + s.name + '」吗?已录入的考核记录保留明细不受影响。', true).then(function (yes) {
              if (!yes) { return; }
              DEL('/api/military/schemes/' + s.id).then(function () {
                toast('已删除');
                loadMilSchemes().then(renderMilSchemeList);
              }).catch(function (err) { toast(err.message, 'err'); });
            });
          }
        }));
      list.appendChild(row);
    });
    if (!MIL_SCHEMES.length) {
      list.appendChild(h('div', { style: 'color:#8a94a6;padding:16px 0', text: '还没有考核方案,点右上角「新建方案」创建一个' }));
    }
    body.appendChild(list);
    var modal = openModal({
      title: '考核方案设置(顶级课目占比合计100%;有子项的课目,子项占比合计也为100%)',
      wide: true,
      body: body,
      footer: [h('button', { class: 'btn btn-ghost', text: '关闭', onclick: function () { modal.close(); } })]
    });
    modal.dialog.querySelector('.dialog-head').appendChild(h('button', {
      class: 'btn btn-primary btn-sm', style: 'margin-left:auto', text: '新建方案',
      onclick: function () { modal.close(); openMilSchemeEditor(null); }
    }));
  }

  function openMilSchemeEditor(scheme) {
    var state = scheme
      ? (scheme.items || []).map(function (top) {
          return { name: top.name, weight: Number(top.weight) || 0,
            children: (top.children || []).map(function (c) { return { name: c.name, weight: Number(c.weight) || 0 }; }) };
        })
      : [{ name: '', weight: 0, children: [] }];
    var nameEl = h('input', { type: 'text', placeholder: '如:军事课目综合考核' });
    nameEl.value = scheme ? scheme.name : '';
    nameEl.style.cssText = 'width:320px;padding:7px 9px;border:1px solid #d4dce8;border-radius:4px';
    var remarkEl = h('input', { type: 'text', placeholder: '备注(可选)' });
    remarkEl.value = scheme && scheme.remark ? String(scheme.remark) : '';
    remarkEl.style.cssText = 'width:320px;padding:7px 9px;border:1px solid #d4dce8;border-radius:4px;margin-left:12px';
    var box = h('div');
    function draw() {
      box.innerHTML = '';
      box.appendChild(h('div', { style: 'display:flex;align-items:center;gap:0;margin-bottom:14px' },
        h('span', { style: 'width:70px', text: '方案名称:' }), nameEl, h('span', { style: 'margin-left:12px', text: '备注:' }), remarkEl));
      state.forEach(function (top, ti) {
        var topRow = h('div', { style: 'display:flex;align-items:center;gap:8px;margin:10px 0 4px' });
        var tn = h('input', { type: 'text', placeholder: '课目名,如:理论' });
        tn.value = top.name;
        var tw = h('input', { type: 'number', min: '0', max: '100', step: '0.5', placeholder: '占比%' });
        tw.value = top.weight || '';
        tn.style.cssText = 'width:170px;padding:6px 9px;border:1px solid #d4dce8;border-radius:4px;font-weight:600';
        tw.style.cssText = 'width:90px;padding:6px 9px;border:1px solid #d4dce8;border-radius:4px';
        tn.addEventListener('input', function () { top.name = tn.value; });
        tw.addEventListener('input', function () { top.weight = parseFloat(tw.value) || 0; });
        topRow.appendChild(h('span', { style: 'font-weight:600', text: '课目' + (ti + 1) + ':' }));
        topRow.appendChild(tn);
        topRow.appendChild(tw);
        topRow.appendChild(h('span', { text: '%(占总分)' }));
        topRow.appendChild(h('button', { class: 'btn btn-ghost btn-sm', text: '+子项', title: '添加子项(如实操下的小项目)', onclick: function () { top.children.push({ name: '', weight: 0 }); draw(); } }));
        topRow.appendChild(h('button', { class: 'btn btn-ghost btn-sm', text: '删除课目', onclick: function () { state.splice(ti, 1); draw(); } }));
        box.appendChild(topRow);
        top.children.forEach(function (c, ci) {
          var cr = h('div', { style: 'display:flex;align-items:center;gap:8px;margin:4px 0 4px 64px' });
          var cn = h('input', { type: 'text', placeholder: '子项名,如:射击' });
          cn.value = c.name;
          var cw = h('input', { type: 'number', min: '0', max: '100', step: '0.5', placeholder: '占比%' });
          cw.value = c.weight || '';
          cn.style.cssText = 'width:150px;padding:6px 9px;border:1px solid #d4dce8;border-radius:4px';
          cw.style.cssText = 'width:90px;padding:6px 9px;border:1px solid #d4dce8;border-radius:4px';
          cn.addEventListener('input', function () { c.name = cn.value; });
          cw.addEventListener('input', function () { c.weight = parseFloat(cw.value) || 0; });
          cr.appendChild(h('span', { style: 'color:#66707f', text: '└' }));
          cr.appendChild(cn);
          cr.appendChild(cw);
          cr.appendChild(h('span', { text: '%(占该课目)' }));
          cr.appendChild(h('button', { class: 'btn btn-ghost btn-sm', text: '删除', onclick: function () { top.children.splice(ci, 1); draw(); } }));
          box.appendChild(cr);
        });
      });
      box.appendChild(h('button', { class: 'btn btn-ghost btn-sm', text: '+ 添加课目', onclick: function () { state.push({ name: '', weight: 0, children: [] }); draw(); } }));
    }
    draw();
    var modal = null;
    modal = openModal({
      title: scheme ? '编辑考核方案:' + scheme.name : '新建考核方案',
      wide: true,
      body: box,
      footer: [
        h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { modal.close(); } }),
        h('button', {
          class: 'btn btn-primary', text: '保存',
          onclick: function () {
            var payload = { name: nameEl.value.trim(), remark: remarkEl.value.trim(), items: state };
            if (scheme) { payload.id = scheme.id; }
            var topSum = 0;
            for (var i = 0; i < state.length; i++) {
              topSum += state[i].weight || 0;
              var cSum = 0;
              for (var j = 0; j < state[i].children.length; j++) { cSum += state[i].children[j].weight || 0; }
              if (state[i].children.length && Math.abs(cSum - 100) > 0.01) {
                toast('课目「' + (state[i].name || '?') + '」的子项占比合计须为100%(当前' + trimNumJs(cSum) + '%)', 'err');
                return;
              }
              if (!state[i].children.length && (state[i].name || '').trim() === '') {
                toast('第' + (i + 1) + '个课目名称不能为空', 'err');
                return;
              }
            }
            if (Math.abs(topSum - 100) > 0.01) {
              toast('顶级课目占比合计须为100%(当前' + trimNumJs(topSum) + '%)', 'err');
              return;
            }
            POST('/api/military/schemes', payload).then(function () {
              modal.close();
              toast('已保存');
              loadMilSchemes().then(renderMilSchemeList);
            }).catch(function (err) { toast(err.message, 'err'); });
          }
        })]
    });
  }

  setupMilitaryModule();

  /* ================= 5. 休假管理分组:休假计划 + 休假登记 ================= */

  var LEAVE_CUSTOM_ITEMS = [];

  function clearCustomActive() {
    LEAVE_CUSTOM_ITEMS.forEach(function (el) { el.classList.remove('active'); });
  }

  // 页面加载时(渲染前)捕获是否停在休假计划页:系统 navigate 会改写 hash,必须提前记住
  var LP_BOOT_HASH = (location.hash || '') === '#leave-plan';

  /** 把顶级「休假管理」菜单项改造成分组:休假登记(原页面)+休假计划(新页面) */
  function tryRestructureLeaveMenu(node) {
    try {
    var menu = node.nodeType === 1 && node.classList && node.classList.contains('menu') ? node
      : (node.querySelector ? node.querySelector('.menu') : null);
    if (!menu || menu.getAttribute('data-leavegrp')) {
      return;
    }
    var tops = menu.querySelectorAll('.menu-item');
    var leaveItem = null;
    for (var i = 0; i < tops.length; i++) {
      var t = tops[i];
      if (t.classList.contains('sub') || t.classList.contains('group')) {
        continue;
      }
      var txt = t.querySelector('.txt');
      if (txt && String(txt.textContent || '').trim() === '休假管理') {
        leaveItem = t;
        break;
      }
    }
    if (!leaveItem) {
      return;
    }
    menu.setAttribute('data-leavegrp', '1');
    var open = localStorage.getItem('menuGroup:leavegrp') !== '0';
    var iconHtml = '';
    var ic = leaveItem.querySelector('.ic');
    if (ic) { iconHtml = ic.innerHTML; }
    // 分组头
    var group = h('div', { class: 'menu-item group' + (open ? ' open' : ''), 'data-tip': '休假管理' },
      h('span', { class: 'ic', html: iconHtml }),
      h('span', { class: 'txt', text: '休假管理' }),
      h('span', { class: 'ga', text: '▸' }));
    group.addEventListener('click', function () {
      var isOpen = !group.classList.contains('open');
      group.classList.toggle('open', isOpen);
      sub.classList.toggle('open', isOpen);
      localStorage.setItem('menuGroup:leavegrp', isOpen ? '1' : '0');
    });
    // 子菜单
    var sub = h('div', { class: 'menu-sub' + (open ? ' open' : '') });
    if (open) { sub.style.height = 'auto'; }
    var regItem = h('div', { class: 'menu-item sub', 'data-tip': '休假登记' },
      h('span', { class: 'dot' }), h('span', { class: 'txt', text: '休假登记' }));
    regItem.setAttribute('data-leavecustom', '1');
    regItem.addEventListener('click', function (e) {
      e.stopPropagation();
      clearCustomActive();
      leaveItem.click(); // 复用原菜单项的导航逻辑
      regItem.classList.add('active');
    });
    var planItem = h('div', { class: 'menu-item sub', 'data-tip': '休假计划' },
      h('span', { class: 'dot' }), h('span', { class: 'txt', text: '休假计划' }));
    planItem.setAttribute('data-leavecustom', '1');
    planItem.addEventListener('click', function (e) {
      e.stopPropagation();
      clearCustomActive();
      renderLeavePlanPage();
      planItem.classList.add('active');
    });
    LEAVE_CUSTOM_ITEMS = [regItem, planItem];
    sub.appendChild(regItem);
    sub.appendChild(planItem);
    menu.insertBefore(group, leaveItem);
    menu.insertBefore(sub, leaveItem);
    leaveItem.style.display = 'none'; // 隐藏原项但保留其导航行为供「休假登记」调用
    } catch (err) {
      // 重组失败不影响其他功能
      try { console.warn('休假菜单重组失败', err); } catch (e2) { /* 忽略 */ }
    }
  }

  /** 休假计划页面(渲染到主内容区;点其他菜单时由 app.js navigate 自动清空) */
  function renderLeavePlanPage() {
    var content = document.querySelector('.content');
    if (!content) {
      return;
    }
    // 与系统一致的记忆机制:hash + lastView,刷新后可回到本页
    try { history.replaceState(null, '', '#leave-plan'); } catch (e) { /* 忽略 */ }
    try { localStorage.setItem('lastView', 'leave-plan'); } catch (e) { /* 忽略 */ }
    var title = document.querySelector('.topbar-title');
    if (title) { title.textContent = '休假计划'; }
    content.innerHTML = '';

    var state = { rows: [] };
    var nameInput = h('input', { type: 'text', placeholder: '按姓名搜索,回车' });
    nameInput.style.cssText = 'width:180px;padding:7px 10px;border:1px solid #d4dce8;border-radius:4px';
    var yearSel = h('select');
    yearSel.style.cssText = 'padding:7px 8px;border:1px solid #d4dce8;border-radius:4px';
    var nowYear = new Date().getFullYear();
    yearSel.options.add(new Option('全部年度', ''));
    for (var y = nowYear + 1; y >= nowYear - 2; y--) {
      yearSel.options.add(new Option(y + ' 年', String(y)));
    }

    var table = h('table', { class: 'grid' });
    table.innerHTML = '<thead><tr>' +
      '<th>姓名</th><th>身份证号</th><th>年度</th><th>休假类型</th><th>计划开始</th><th>计划结束</th>' +
      '<th>天数</th><th>备注</th><th style="width:110px">操作</th></tr></thead><tbody></tbody>';

    function load() {
      var q = [];
      if (String(nameInput.value || '').trim()) { q.push('personnelName=' + encodeURIComponent(nameInput.value.trim())); }
      if (yearSel.value) { q.push('year=' + encodeURIComponent(yearSel.value)); }
      GET('/api/leave-plan?page=1&size=200' + (q.length ? '&' + q.join('&') : '')).then(function (r) {
        state.rows = (r && r.list) || [];
        var tb = table.querySelector('tbody');
        tb.innerHTML = state.rows.length ? state.rows.map(function (row) {
          return '<tr data-vrow="1"><td><b>' + esc(row.personnel_name || '') + '</b></td>' +
            '<td>' + esc(row.id_card || '') + '</td>' +
            '<td>' + esc(row.year || '') + '</td>' +
            '<td>' + esc(row.leave_type || '') + '</td>' +
            '<td>' + esc(row.start_date || '') + '</td>' +
            '<td>' + esc(row.end_date || '') + '</td>' +
            '<td>' + (row.days || '') + '</td>' +
            '<td>' + esc(row.remark || '') + '</td>' +
            '<td class="actions" style="white-space:nowrap">' +
            '<button class="btn-link" data-act="edit" data-id="' + row.id + '">编辑</button> ' +
            '<button class="btn-link danger" data-act="del" data-id="' + row.id + '">删除</button></td></tr>';
        }).join('') : '<tr><td colspan="9" class="empty-row">暂无数据</td></tr>';
      }).catch(function (e) { toast(e.message, 'err'); });
    }

    nameInput.addEventListener('keydown', function (e) { if (e.key === 'Enter') { load(); } });
    yearSel.addEventListener('change', load);

    table.addEventListener('click', function (e) {
      var btn = e.target.closest('button[data-act]');
      if (!btn) { return; }
      var id = btn.getAttribute('data-id');
      var row = state.rows.filter(function (r) { return String(r.id) === String(id); })[0];
      if (!row) { return; }
      if (btn.getAttribute('data-act') === 'edit') {
        openPlanForm(row);
      } else {
        confirmBox('确定删除「' + (row.personnel_name || '') + '」的这条休假计划吗?', true).then(function (yes) {
          if (!yes) { return; }
          DEL('/api/leave-plan/' + id).then(function () { toast('已删除'); load(); })
            .catch(function (err) { toast(err.message, 'err'); });
        });
      }
    });

    var toolbar = h('div', { class: 'toolbar' },
      nameInput, yearSel,
      h('div', { class: 'spacer' }),
      h('span', { style: 'color:#66707f;font-size:12px;margin-right:10px', text: '年度休假安排;实际休假请到「休假登记」录入' }),
      h('button', { class: 'btn btn-primary', text: '新增休假计划', onclick: function () { openPlanForm(null); } }));

    var root = h('div', { class: 'content-main', 'data-lp': '1' },
      h('div', { class: 'panel', style: 'margin-bottom:12px' }, toolbar),
      h('div', { class: 'panel' }, h('div', { class: 'table-wrap' }, table)));
    content.appendChild(root);
    // 高亮菜单:清除全部后标记「休假计划」
    var allActive = document.querySelectorAll('.menu-item.active');
    for (var ai = 0; ai < allActive.length; ai++) { allActive[ai].classList.remove('active'); }
    if (LEAVE_CUSTOM_ITEMS[1]) { LEAVE_CUSTOM_ITEMS[1].classList.add('active'); }
    load();
  }

  /** 休假计划 编辑弹窗(与其他模块一致:输入身份证号自动带出姓名) */
  function openPlanForm(row) {
    function field(label, el) {
      return h('div', { style: 'margin-bottom:12px' }, h('div', { style: 'font-size:13px;color:#3c4a5c;margin-bottom:5px', text: label }), el);
    }
    var inputCss = 'width:100%;padding:8px 10px;border:1px solid #d4dce8;border-radius:4px;box-sizing:border-box';
    var idCardEl = h('input', { type: 'text', placeholder: '输入后自动带出姓名', maxlength: '18' });
    idCardEl.style.cssText = inputCss;
    var nameEl = h('input', { type: 'text', readonly: 'readonly', placeholder: '按身份证号自动获取' });
    nameEl.style.cssText = inputCss + ';background:#f4f6f9';
    var pidHolder = { pid: row ? row.personnel_id : 0 };
    var hint = h('div', { style: 'font-size:11px;color:#8a94a6;margin-top:3px;min-height:14px', text: '' });
    if (row) {
      idCardEl.value = row.id_card || '';
      nameEl.value = row.personnel_name || '';
    }
    function lookup() {
      var v = String(idCardEl.value || '').trim();
      nameEl.value = '';
      pidHolder.pid = 0;
      if (!v) {
        hint.style.color = '#8a94a6';
        hint.textContent = '';
        return;
      }
      if (v.length !== 18) {
        hint.style.color = '#c0392b';
        hint.textContent = '身份证号应为18位(当前' + v.length + '位)';
        return;
      }
      GET('/api/personnel/by-idcard?idCard=' + encodeURIComponent(v)).then(function (p) {
        if (p) {
          nameEl.value = p.name || '';
          pidHolder.pid = p.id;
          hint.style.color = '#1e7e34';
          hint.textContent = '已带出:' + (p.name || '') + (p.departmentName ? '(' + p.departmentName + ')' : '');
        } else {
          hint.style.color = '#c0392b';
          hint.textContent = '未找到该身份证对应的人员';
        }
      }).catch(function (e) {
        hint.style.color = '#c0392b';
        hint.textContent = e.message;
      });
    }
    idCardEl.addEventListener('input', function () {
      clearTimeout(idCardEl._t);
      idCardEl._t = setTimeout(lookup, 300);
    });
    idCardEl.addEventListener('change', lookup);
    var idCardBox = h('div', {}, idCardEl, hint);

    var yearSel = h('select');
    yearSel.style.cssText = inputCss;
    var nowYear = new Date().getFullYear();
    for (var y = nowYear + 1; y >= nowYear - 2; y--) {
      yearSel.options.add(new Option(y + ' 年', String(y)));
    }
    yearSel.value = row && row.year ? String(row.year) : String(nowYear);
    var typeSel = h('select');
    typeSel.style.cssText = inputCss;
    var types = dictLabels('leave_type');
    if (!types.length) { types = ['事假', '病假', '年假', '探亲假', '婚假', '其他']; }
    types.forEach(function (t) { typeSel.options.add(new Option(t, t)); });
    if (row) { typeSel.value = row.leave_type || types[0]; }
    var startEl = h('input', { type: 'date' });
    startEl.style.cssText = inputCss;
    var endEl = h('input', { type: 'date' });
    endEl.style.cssText = inputCss;
    if (row) { startEl.value = row.start_date || ''; endEl.value = row.end_date || ''; }
    var daysEl = h('input', { type: 'number', min: '0', step: '0.5', placeholder: '自动计算' });
    daysEl.style.cssText = inputCss;
    if (row && row.days) { daysEl.value = row.days; }
    function syncDays() {
      if (startEl.value && endEl.value) {
        var d = (new Date(endEl.value) - new Date(startEl.value)) / 86400000;
        if (d >= 0) { daysEl.value = d; }
      }
    }
    startEl.addEventListener('change', syncDays);
    endEl.addEventListener('change', syncDays);
    var remarkEl = h('input', { type: 'text', placeholder: '备注(可选)' });
    remarkEl.style.cssText = inputCss;
    if (row) { remarkEl.value = row.remark || ''; }

    // 与其他模块一致:姓名只读框旁提供「选择」按钮(弹窗按姓名/身份证/部门搜索选人);
    // 自建稳定的弹性行布局,透传系统统一的选人弹窗,避免悬浮定位在本弹窗内跳动
    var pickBtn = h('button', { type: 'button', class: 'btn btn-ghost', text: '选择' });
    pickBtn.style.cssText = 'flex:none;margin-left:8px';
    nameEl.style.cssText = inputCss + ';background:#f4f6f9;flex:1;min-width:0;padding-right:10px';
    pickBtn.addEventListener('click', function () {
      if (typeof openPersonPicker !== 'function') {
        toast('选人组件不可用', 'err');
        return;
      }
      openPersonPicker(function (p) {
        idCardEl.value = p.idCard || '';
        nameEl.value = p.name || '';
        pidHolder.pid = p.id;
        hint.style.color = '#1e7e34';
        hint.textContent = '已选:' + (p.name || '') + (p.departmentName ? '(' + p.departmentName + ')' : '');
      });
    });
    var nameRow = h('div', { style: 'display:flex;align-items:center' }, nameEl, pickBtn);
  }
    var modal = null;
    modal = openModal({
      title: row ? '编辑休假计划 - ' + (row.personnel_name || '') : '新增休假计划',
      body: h('div', { style: 'display:grid;grid-template-columns:1fr 1fr;gap:0 16px' },
        field('身份证号', idCardBox), field('姓名', nameRow),
        field('年度', yearSel), field('休假类型', typeSel),
        field('计划开始日期', startEl), field('计划结束日期', endEl),
        field('天数', daysEl), field('备注', remarkEl)),
      footer: [
        h('button', { class: 'btn btn-ghost', text: '取消', onclick: function () { modal.close(); } }),
        h('button', {
          class: 'btn btn-primary', text: '保存',
          onclick: function () {
            if (!pidHolder.pid) {
              toast('请输入身份证号带出人员,或点姓名旁的「选择」按钮选人', 'err');
              return;
            }
            var payload = {
              personnelId: pidHolder.pid,
              year: yearSel.value,
              leaveType: typeSel.value,
              startDate: startEl.value,
              endDate: endEl.value,
              days: daysEl.value,
              remark: remarkEl.value
            };
            var done = function (r) {
              modal.close();
              toast('已保存');
              var planBtn = document.querySelector('[data-leavecustom][data-tip="休假计划"]');
              if (planBtn) { planBtn.click(); } // 刷新列表
            };
            if (row) {
              PUT('/api/leave-plan/' + row.id, payload).then(done).catch(function (e) { toast(e.message, 'err'); });
            } else {
              POST('/api/leave-plan', payload).then(done).catch(function (e) { toast(e.message, 'err'); });
            }
          }
        })]
    });

  // 菜单渲染后重组休假菜单;启动停在 #leave-plan 时恢复计划页;均带异常保护与重试兑底
  new MutationObserver(function (muts) {
    try {
      for (var i = 0; i < muts.length; i++) {
        var nodes = muts[i].addedNodes;
        for (var j = 0; j < nodes.length; j++) {
          if (nodes[j].nodeType === 1) {
            tryRestructureLeaveMenu(nodes[j]);
          }
        }
      }
      if (LP_BOOT_HASH && !document.querySelector('.content [data-lp]')) {
        var lpContent = document.querySelector('.content');
        if (lpContent && lpContent.children.length) {
          LP_BOOT_HASH = false;
          renderLeavePlanPage();
        }
      }
    } catch (err) { /* 观察器异常不影响页面 */ }
  }).observe(document.body, { childList: true, subtree: true });

  // 兑底:菜单若因时序未被重组,定时重试(最多约30秒)
  var lpTries = 0;
  var lpMenuTimer = setInterval(function () {
    try {
      var menuEl = document.querySelector('.menu');
      if (menuEl && !menuEl.getAttribute('data-leavegrp')) {
        tryRestructureLeaveMenu(menuEl);
      }
      if ((menuEl && menuEl.getAttribute('data-leavegrp')) || ++lpTries > 60) {
        clearInterval(lpMenuTimer);
      }
    } catch (err) {
      clearInterval(lpMenuTimer);
    }
  }, 500);

  // 浏览器前进/后退回到 #leave-plan 时恢复计划页
  window.addEventListener('hashchange', function () {
    if ((location.hash || '') === '#leave-plan') {
      try { renderLeavePlanPage(); } catch (err) { /* 忽略 */ }
    }
  });

  document.addEventListener('click', function (e) {
    var clicked = e.target && e.target.closest ? e.target.closest('.menu-item') : null;
    if (clicked && !clicked.hasAttribute('data-leavecustom')) {
      clearCustomActive();
    }
  }, true);

  // 视图切换/重渲染后补挂按钮(去抖,等 DOM 稳定)
  var btnTimer = 0;
  new MutationObserver(function () {
    clearTimeout(btnTimer);
    btnTimer = setTimeout(addPrintButtons, 50);
  }).observe(document.body, { childList: true, subtree: true });
})();
