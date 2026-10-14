/**
 * 扩展功能脚本(独立于 app.js,原生 ES5,零第三方依赖)
 * ------------------------------------------------------------
 * 本文件承载后加的界面增强功能,复用 app.js 暴露的全局函数
 * (GET/h/toast/esc/showProgressModal/SITE),须在 app.js 之后加载。
 *
 * 当前包含:
 *   1) 侧栏分组子菜单展开/收起动画(高度+透明度过渡,配合 app.css 的 .menu-sub 规则)
 *   2) 人员管理工具栏左上角:点名册打印 / 花名册打印
 *      (按当前筛选条件分页拉取全部人员,生成 A4 打印文档,经隐藏 iframe 调起浏览器打印)
 *   3) 按名称隐藏分组子菜单(如「装备管理」下的「全部装备」);要恢复删掉对应名称即可
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

  /* ================= 2. 点名册 / 花名册打印 ================= */

  /** 按当前筛选分页拉取全部人员(接口单页上限 200) */
  function fetchAllPersonnel(filters) {
    var qs = Object.keys(filters || {}).map(function (k) {
      return k + '=' + encodeURIComponent(filters[k]);
    }).join('&');
    var out = [], page = 1;
    function next() {
      return GET('/api/personnel?page=' + page + '&size=200' + (qs ? '&' + qs : '')).then(function (r) {
        var list = (r && r.list) || [];
        out = out.concat(list);
        var total = (r && r.total) || 0;
        if (list.length && out.length < total) { page++; return next(); }
        return out;
      });
    }
    return next();
  }

  /** 按部门分组,保持服务端返回顺序 */
  function groupByDepartment(rows) {
    var groups = [], index = {};
    rows.forEach(function (r) {
      var name = r.departmentName || '未分配部门';
      if (!index[name]) { index[name] = { name: name, rows: [] }; groups.push(index[name]); }
      index[name].rows.push(r);
    });
    return groups;
  }

  function cnToday() {
    var d = new Date();
    return d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日';
  }

  function ageOfYear(birthday) {
    if (!birthday) return '';
    var b = new Date(String(birthday).replace(/[\s.\/]/g, '-'));
    if (isNaN(b.getTime())) return '';
    var n = new Date();
    var age = n.getFullYear() - b.getFullYear();
    var m = n.getMonth() - b.getMonth();
    if (m < 0 || (m === 0 && n.getDate() < b.getDate())) age--;
    return (age >= 0 && age < 130) ? String(age) : '';
  }

  /** 点名册:竖向 A4,按部门分组,含签到/备注空栏 */
  function rollCallHtml(rows) {
    var groups = groupByDepartment(rows);
    var no = 0;
    var body = groups.map(function (g) {
      var trs = g.rows.map(function (r) {
        no++;
        return '<tr><td>' + no + '</td><td>' + esc(r.departmentName || '') + '</td><td><b>' + esc(r.name || '') + '</b></td>' +
          '<td>' + esc(r.post || '') + '</td><td>' + esc(r.rank || '') + '</td><td class="sign"></td><td></td></tr>';
      }).join('');
      return '<tr class="grp"><td colspan="7">' + esc(g.name) + '(' + g.rows.length + '人)</td></tr>' + trs;
    }).join('');
    var male = rows.filter(function (r) { return r.gender === '男'; }).length;
    return '<h1>' + esc(SITE.name) + '人员点名册</h1>' +
      '<div class="sub"><span>日期:' + cnToday() + '</span><span>地点:<span class="blank"></span></span><span>应到:' + rows.length + '人(男' + male + '/女' + (rows.length - male) + ')</span></div>' +
      '<table><thead><tr><th style="width:34px">序号</th><th style="width:16%">部门</th><th style="width:12%">姓名</th><th style="width:16%">职务</th><th style="width:12%">军衔/职级</th><th style="width:11%">签到</th><th>备注</th></tr></thead><tbody>' + body + '</tbody></table>' +
      '<div class="foot"><span>实到:<span class="blank" style="min-width:60px"></span>人</span><span>未到:<span class="blank" style="min-width:60px"></span>人</span><span>点名人:<span class="blank" style="min-width:80px"></span></span></div>';
  }

  /** 花名册:横向 A4,按部门分组,含基本情况和联系方式 */
  function rosterHtml(rows) {
    var groups = groupByDepartment(rows);
    var no = 0;
    var body = groups.map(function (g) {
      var trs = g.rows.map(function (r) {
        no++;
        return '<tr><td>' + no + '</td><td><b>' + esc(r.name || '') + '</b></td><td>' + esc(r.gender || '') + '</td>' +
          '<td>' + esc((r.birthday || '').substring(0, 7)) + '</td><td>' + ageOfYear(r.birthday) + '</td>' +
          '<td>' + esc(r.rank || '') + '</td><td>' + esc(r.post || '') + '</td>' +
          '<td>' + esc(r.politicalStatus || '') + '</td><td>' + esc(r.education || '') + '</td>' +
          '<td>' + esc(r.entryDate || '') + '</td><td>' + esc(r.phone || '') + '</td></tr>';
      }).join('');
      return '<tr class="grp"><td colspan="11">' + esc(g.name) + '(' + g.rows.length + '人)</td></tr>' + trs;
    }).join('');
    var male = rows.filter(function (r) { return r.gender === '男'; }).length;
    return '<h1>' + esc(SITE.name) + '人员花名册</h1>' +
      '<div class="sub"><span>打印日期:' + cnToday() + '</span><span>合计:' + rows.length + '人(男' + male + '/女' + (rows.length - male) + ')</span></div>' +
      '<table><thead><tr><th style="width:30px">序号</th><th style="width:8%">姓名</th><th style="width:4%">性别</th><th style="width:7%">出生年月</th><th style="width:4%">年龄</th><th style="width:9%">军衔/职级</th><th style="width:9%">职务</th><th style="width:8%">政治面貌</th><th style="width:6%">文化程度</th><th style="width:8%">入伍时间</th><th>联系电话</th></tr></thead><tbody>' + body + '</tbody></table>' +
      '<div class="foot"><span>制表人:<span class="blank" style="min-width:80px"></span></span><span>审核人:<span class="blank" style="min-width:80px"></span></span><span>日期:' + cnToday() + '</span></div>';
  }

  /** 生成打印文档并在隐藏 iframe 中调起浏览器打印 */
  function printHtmlDocument(docTitle, landscape, bodyHtml) {
    if (window.__printFrame) { window.__printFrame.remove(); }
    var frame = document.createElement('iframe');
    frame.setAttribute('title', docTitle);
    frame.style.cssText = 'position:fixed;right:0;bottom:0;width:1px;height:1px;border:0;opacity:0;';
    window.__printFrame = frame;
    document.body.appendChild(frame);
    var css =
      '@page{size:A4 ' + (landscape ? 'landscape' : 'portrait') + ';margin:10mm 9mm;}' +
      '*{box-sizing:border-box;}' +
      'body{margin:0;font-family:"Microsoft YaHei","PingFang SC","SimSun",sans-serif;color:#111;font-size:12px;}' +
      'h1{margin:0 0 6px;text-align:center;font-size:19px;letter-spacing:3px;}' +
      '.sub{display:flex;justify-content:space-between;font-size:12px;margin-bottom:8px;}' +
      'table{width:100%;border-collapse:collapse;table-layout:fixed;}' +
      'th,td{border:1px solid #333;padding:4px 3px;text-align:center;word-break:break-all;}' +
      'thead{display:table-header-group;}' +
      'tr{page-break-inside:avoid;}' +
      '.grp td{background:#e8eef7;font-weight:600;text-align:left;padding-left:8px;}' +
      '.sign{height:26px;}' +
      '.foot{display:flex;justify-content:space-between;font-size:12px;margin-top:12px;}' +
      '.blank{display:inline-block;min-width:110px;border-bottom:1px solid #333;height:14px;vertical-align:bottom;}';
    var doc = frame.contentWindow.document;
    doc.open();
    doc.write('<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8"><title>' + esc(docTitle) + '</title><style>' + css + '</style></head><body>' + bodyHtml + '</body></html>');
    doc.close();
    setTimeout(function () {
      try {
        frame.contentWindow.focus();
        frame.contentWindow.print();
      } catch (e) {
        toast('调起打印失败:' + e.message, 'err');
      }
    }, 80);
  }

  /** 入口:按当前筛选拉取人员并生成点名册/花名册调起打印 */
  function printPersonnelDoc(kind, filters) {
    var isRoll = kind === 'rollcall';
    var pm = showProgressModal(isRoll ? '生成点名册' : '生成花名册');
    pm.setStatus('正在按当前筛选条件读取人员…');
    fetchAllPersonnel(filters).then(function (rows) {
      if (!rows.length) {
        pm.close();
        toast('当前筛选条件下没有可打印的人员', 'err');
        return;
      }
      pm.setProgress(90);
      pm.setStatus('正在生成' + (isRoll ? '点名册' : '花名册') + '(' + rows.length + '人)…');
      setTimeout(function () {
        pm.setProgress(100);
        pm.close();
        printHtmlDocument(isRoll ? '人员点名册' : '人员花名册', !isRoll, isRoll ? rollCallHtml(rows) : rosterHtml(rows));
      }, 50);
    }).catch(function (e) {
      pm.close();
      toast(e.message, 'err');
    });
  }

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

  /** 人员管理视图渲染后,在工具栏最左侧(spacer 之前)插入两个打印按钮 */
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
        class: 'btn btn-ghost', text: '点名册打印',
        title: '按当前筛选条件生成点名册(按部门分组,含签到栏)并发送打印',
        onclick: function () { printPersonnelDoc('rollcall', readFilterParams()); }
      }));
      grp.appendChild(h('button', {
        class: 'btn btn-ghost', text: '花名册打印',
        title: '按当前筛选条件生成花名册(按部门分组,含军衔/职务/联系方式等)并发送打印',
        onclick: function () { printPersonnelDoc('roster', readFilterParams()); }
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
