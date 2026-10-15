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

  /** 拉取部门树并展平:id → {name,parent,seq}(parent 为上级部门名,顶级为 '';seq 为树内先后顺序) */
  function fetchDeptIndex() {
    return GET('/api/departments').then(function (tree) {
      var idx = {}, seq = 0;
      function walk(nodes, parentName) {
        (nodes || []).forEach(function (n) {
          idx[String(n.id)] = { name: n.name || '', parent: parentName || '', seq: seq++ };
          walk(n.children, n.name);
        });
      }
      walk(tree, '');
      return idx;
    });
  }

  /** 某部门及其全部下级的 id 集合(部门筛选是精确匹配,含下级需自行展开) */
  function subtreeIds(tree, rootId) {
    var out = {};
    function walk(nodes, inside) {
      (nodes || []).forEach(function (n) {
        var hit = inside || String(n.id) === String(rootId);
        if (hit) out[String(n.id)] = true;
        walk(n.children, hit);
      });
    }
    walk(tree, false);
    return out;
  }

  /** 按当前筛选分页拉取全部人员(接口单页上限 200);departmentId 展开为含下级的子树过滤;
   *  返回 {rows, deptIdx} */
  function fetchAllPersonnel(filters) {
    var f = {};
    Object.keys(filters || {}).forEach(function (k) {
      if (k !== 'departmentId') f[k] = filters[k];
    });
    var qs = Object.keys(f).map(function (k) {
      return k + '=' + encodeURIComponent(f[k]);
    }).join('&');
    var deptId = filters && filters.departmentId ? String(filters.departmentId) : '';
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
    var ready = deptId
      ? GET('/api/departments').then(function (tree) { return subtreeIds(tree, deptId); })
      : Promise.resolve(null);
    return Promise.all([next(), fetchDeptIndex(), ready]).then(function (rs) {
      var rows = rs[0], deptIdx = rs[1], allowed = rs[2];
      if (allowed) {
        rows = rows.filter(function (r) { return allowed[String(r.departmentId)]; });
      }
      return { rows: rows, deptIdx: deptIdx };
    });
  }

  /** 部门全名:有上级时为 上级+本部门(如 一连一班),否则为本部门名 */
  function fullDeptName(r, deptIdx) {
    var d = deptIdx && deptIdx[String(r.departmentId)];
    if (!d) return r.departmentName || '未分配部门';
    return d.parent ? d.parent + d.name : d.name;
  }

  /** 按部门分组(优先用部门树全名),保持服务端返回顺序 */
  function groupByDepartment(rows, deptIdx) {
    var groups = [], index = {};
    rows.forEach(function (r) {
      var name = fullDeptName(r, deptIdx);
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

  /** 点名册:竖向 A4。表头=大单位/小单位;正文=单位→班两级分组的在位人员姓名格;底部=不在位人员(原因在左,姓名横排,休假显示归队时间) */
  function rollCallHtml(rows, deptIdx, leaveMap) {
    var PRESENT = ['在位', '在职']; // 视为在位的状态;其余(休假/住院/培训/借调等)归入不在位
    var present = [], absent = [];
    rows.forEach(function (r) {
      (PRESENT.indexOf(r.status) >= 0 ? present : absent).push(r);
    });

    // 单位与班:有上级部门 → 上级=单位,本部门=班;顶级部门 → 自身即单位(单级展示)
    function unitOf(r) {
      var d = deptIdx && deptIdx[String(r.departmentId)];
      if (!d) return { unit: r.departmentName || '未分配部门', squad: '' };
      return d.parent ? { unit: d.parent, squad: d.name } : { unit: d.name, squad: '' };
    }

    // 小单位表头:全部人员同属一个单位时自动带出,否则留空手填
    var unitSet = {};
    rows.forEach(function (r) { unitSet[unitOf(r).unit] = true; });
    void unitSet; // 仅用于统计口径,不再在表头展示单位名称

    var ROWS_PER_COL = 10; // 每列人名行数:班内人名先自上而下填满一列,再多换到右侧新列
    /** 一个班/一类 → 物理列数与行数(列内纵向填名) */
    function colLayout(names) {
      var n = names.length;
      var cols = Math.max(1, Math.ceil(n / ROWS_PER_COL));
      var rows = Math.ceil(n / cols);
      return { names: names, cols: cols, rows: rows };
    }
    /** 某班第 r 行的单元格:逐列取名,索引 = 列号×行数 + 行号(先下后右);fmt 为单元格内容格式化函数 */
    function cellsAtRow(g, r, fmt) {
      var tds = '';
      for (var c = 0; c < g.cols; c++) {
        var i = c * g.rows + r; // 先下后右
        tds += '<td class="nm">' + (i < g.names.length ? fmt(g.names[i]) : '') + '</td>';
      }
      return tds;
    }
    function defaultNameFmt(name) { return '<b>' + esc(name) + '</b>'; }
    /** 矩阵表:一级表头(单位/原因)左右并排,二级表头(班)也在右侧并排,姓名按列纵向填充 */
    function matrixTable(level1) {
      var totalCols = 0, maxRows = 0, hasSplit = false;
      level1.forEach(function (u) {
        u.cols = u.grids.reduce(function (t, x) { return t + x.g.cols; }, 0);
        u.total = u.grids.reduce(function (t, x) { return t + x.g.names.length; }, 0);
        totalCols += u.cols;
        if (u.split) hasSplit = true;
        u.grids.forEach(function (x) { if (x.g.rows > maxRows) maxRows = x.g.rows; });
      });
      if (!totalCols) return '';
      var colgroup = '';
      for (var ci = 0; ci < totalCols; ci++) { colgroup += '<col style="width:' + (100 / totalCols).toFixed(2) + '%">'; }
      var head1 = '';
      level1.forEach(function (u) {
        head1 += '<td colspan="' + u.cols + '"' + (u.split || !hasSplit ? '' : ' rowspan="2"') + '>' +
          esc(u.label) + '(' + u.total + '人)</td>';
      });
      var head2 = '';
      level1.forEach(function (u) {
        if (!u.split) return;
        u.grids.forEach(function (x) {
          head2 += '<td colspan="' + x.g.cols + '">' + esc(x.label) + '(' + x.g.names.length + '人)</td>';
        });
      });
      var body = '';
      for (var r = 0; r < maxRows; r++) {
        body += '<tr>';
        level1.forEach(function (u) {
          u.grids.forEach(function (x) { body += cellsAtRow(x.g, r, x.fmt || defaultNameFmt); });
        });
        body += '</tr>';
      }
      return '<table><colgroup>' + colgroup + '</colgroup>' +
        '<thead><tr class="grp">' + head1 + '</tr>' +
        (head2 ? '<tr class="sq">' + head2 + '</tr>' : '') + '</thead>' +
        '<tbody>' + body + '</tbody></table>';
    }

    // 在位人员:按部门树顺序组织成 单位(连)→ 班 两级,同级左右并排、姓名纵填
    var unitOrder = [], unitMap = {};
    present.forEach(function (r) {
      var u = unitOf(r);
      if (!unitMap[u.unit]) { unitMap[u.unit] = { label: u.unit, squads: {}, order: [], seq: Infinity, split: false }; unitOrder.push(u.unit); }
      var U = unitMap[u.unit];
      var d = deptIdx && deptIdx[String(r.departmentId)];
      var seq = d ? d.seq : Infinity;
      if (seq < U.seq) U.seq = seq;
      if (u.squad) U.split = true;
      var sq = u.squad || u.unit;
      if (!U.squads[sq]) { U.squads[sq] = { names: [], seq: seq }; U.order.push(sq); }
      U.squads[sq].names.push(r.name || '');
    });
    unitOrder.sort(function (a, b) { return unitMap[a].seq - unitMap[b].seq; });
    var presentUnits = unitOrder.map(function (un) {
      var U = unitMap[un];
      U.order.sort(function (a, b) { return U.squads[a].seq - U.squads[b].seq; });
      return {
        label: un, split: U.split,
        grids: U.order.map(function (s) { return { label: s, g: colLayout(U.squads[s].names) }; })
      };
    });
    var presentBlock = matrixTable(presentUnits);

    // 不在位人员:原因放左侧(整块合并),姓名横向排列;休假人员在姓名下方显示归队时间
    var reasonOrder = [], reasonMap = {};
    absent.forEach(function (r) {
      var s = r.status || '其他';
      if (!reasonMap[s]) { reasonMap[s] = []; reasonOrder.push(s); }
      reasonMap[s].push({ name: r.name || '', ret: leaveMap ? leaveMap[String(r.id)] : '' });
    });
    var reasonText = reasonOrder.map(function (s) { return esc(s) + reasonMap[s].length + '人'; }).join('、');
    var ABSENT_PER_ROW = 5; // 不在位人员每行姓名数(横向填充)
    var absentBlock = '';
    if (reasonOrder.length) {
      absentBlock = '<table><tbody>';
      reasonOrder.forEach(function (s) {
        var people = reasonMap[s];
        var cells = people.map(function (p) {
          var inner = '<b>' + esc(p.name) + '</b>' +
            (p.ret ? '<div class="ret">归队:' + esc(p.ret) + '</div>' : '');
          return '<td class="nm">' + inner + '</td>';
        });
        var rowsN = Math.max(1, Math.ceil(cells.length / ABSENT_PER_ROW));
        for (var r = 0; r < rowsN; r++) {
          absentBlock += '<tr>' +
            (r === 0 ? '<td class="rs" rowspan="' + rowsN + '">' + esc(s) + '(' + people.length + '人)</td>' : '');
          for (var c = 0; c < ABSENT_PER_ROW; c++) {
            var i = r * ABSENT_PER_ROW + c; // 横向填充:先左后右
            absentBlock += i < cells.length ? cells[i] : '<td class="nm"></td>';
          }
          absentBlock += '</tr>';
        }
      });
      absentBlock += '</tbody></table>';
    } else {
      absentBlock = '<table><tbody><tr><td class="none">无</td></tr></tbody></table>';
    }

    return '<div class="docname">人员点名册</div>' +
      '<div class="sub"><span>日期:' + cnToday() + '</span></div>' +
      '<h2>在位人员(共' + present.length + '人)</h2>' +
      (presentBlock || '<table><tbody><tr><td class="none">无</td></tr></tbody></table>') +
      '<h2>不在位人员(共' + absent.length + '人' + (reasonText ? ':' + reasonText : '') + ')</h2>' +
      (absentBlock || '<table><tbody><tr><td class="none">无</td></tr></tbody></table>') +
      '<div class="foot"><span>应到:' + rows.length + '人</span><span>在位:' + present.length + '人</span><span>不在位:' + absent.length + '人</span>' +
      '<span>实到:<span class="blank" style="min-width:50px"></span>人</span><span>点名人:<span class="blank" style="min-width:70px"></span></span></div>';
  }

  /** 花名册:横向 A4,按部门(含上级全名)分组,含基本情况和联系方式 */
  function rosterHtml(rows, deptIdx) {
    var groups = groupByDepartment(rows, deptIdx);
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

  /** 打印/下载文档共用的完整 HTML(含样式) */
  function buildDocument(docTitle, landscape, bodyHtml) {
    var css =
      '@page{size:A4 ' + (landscape ? 'landscape' : 'portrait') + ';margin:10mm 9mm;}' +
      '*{box-sizing:border-box;}' +
      'body{margin:0;font-family:"Microsoft YaHei","PingFang SC","SimSun",sans-serif;color:#111;font-size:12px;}' +
      'h1{margin:0 0 2px;text-align:center;font-size:19px;letter-spacing:3px;}' +
      'h2{margin:12px 0 5px;font-size:13.5px;letter-spacing:1px;page-break-after:avoid;}' +
      '.docname{text-align:center;font-size:17px;font-weight:600;letter-spacing:10px;margin:0 0 10px;}' +
      'td.none{text-align:center;color:#666;}' +
      '.sub{display:flex;justify-content:space-between;font-size:12px;margin-bottom:8px;}' +
      'table{width:100%;border-collapse:collapse;table-layout:fixed;}' +
      'th,td{border:1px solid #333;padding:4px 3px;text-align:center;word-break:break-all;}' +
      'thead{display:table-header-group;}' +
      'tr{page-break-inside:avoid;}' +
      '.grp td{background:#e8eef7;font-weight:600;text-align:center;}' +
      '.sq td{background:#f4f7fb;font-weight:600;text-align:center;}' +
      'td.nm{height:28px;font-size:13px;}' +
      '.ret{font-size:10px;color:#555;font-weight:400;line-height:1.3;}' +
      'td.rs{background:#f4f7fb;font-weight:600;width:78px;}' +
      '.foot{display:flex;justify-content:space-between;font-size:12px;margin-top:12px;}' +
      '.blank{display:inline-block;min-width:110px;border-bottom:1px solid #333;height:14px;vertical-align:bottom;}';
    return '<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8"><title>' + esc(docTitle) + '</title><style>' + css + '</style></head><body>' + bodyHtml + '</body></html>';
  }

  /** 以 HTML 文件方式下载(浏览器打开后可直接打印,版式与打印完全一致) */
  function downloadHtmlDocument(filename, landscape, bodyHtml) {
    var blob = new Blob([buildDocument(filename.replace(/\.html$/, ''), landscape, bodyHtml)], { type: 'text/html;charset=utf-8' });
    downloadBlob(blob, filename);
  }

  /** 生成打印文档并在隐藏 iframe 中调起浏览器打印 */
  function printHtmlDocument(docTitle, landscape, bodyHtml) {
    if (window.__printFrame) { window.__printFrame.remove(); }
    var frame = document.createElement('iframe');
    frame.setAttribute('title', docTitle);
    frame.style.cssText = 'position:fixed;right:0;bottom:0;width:1px;height:1px;border:0;opacity:0;';
    window.__printFrame = frame;
    document.body.appendChild(frame);
    var doc = frame.contentWindow.document;
    doc.open();
    doc.write(buildDocument(docTitle, landscape, bodyHtml));
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

  /** 休假归队时间表:personnelId → 当前在休(结束日期>=今天)记录中最晚的结束日期 */
  function leaveReturnMap(list) {
    var t = new Date();
    var iso = t.getFullYear() + '-' + ('0' + (t.getMonth() + 1)).slice(-2) + '-' + ('0' + t.getDate()).slice(-2);
    var map = {};
    (list || []).forEach(function (l) {
      if (!l.personnelId || !l.endDate) return;
      if (l.endDate < iso) return; // 已结束的记录不代表当前归队时间
      var pid = String(l.personnelId);
      if (!map[pid] || l.endDate > map[pid]) { map[pid] = l.endDate; }
    });
    return map;
  }

  /** 入口:按当前筛选拉取人员并生成点名册/花名册调起打印;点名册附带休假归队时间 */
  function printPersonnelDoc(kind, filters) {
    var isRoll = kind === 'rollcall';
    var pm = showProgressModal(isRoll ? '生成点名册' : '生成花名册');
    pm.setStatus('正在按当前筛选条件读取人员…');
    var leaveReady = isRoll
      ? GET('/api/leave?page=1&size=200').then(function (r) { return leaveReturnMap((r && r.list) || []); })
      : Promise.resolve(null);
    Promise.all([fetchAllPersonnel(filters), leaveReady]).then(function (rs) {
      var res = rs[0], leaveMap = rs[1];
      var rows = res.rows, deptIdx = res.deptIdx;
      if (!rows.length) {
        pm.close();
        toast('当前筛选条件下没有可打印的人员', 'err');
        return;
      }
      pm.setProgress(90);
      pm.setStatus('正在生成' + (isRoll ? '点名册' : '花名册') + '(' + rows.length + '人)…');
      setTimeout(function () {
        pm.setProgress(100);
        if (isRoll) {
          // 点名册:横版 A4 版式的 HTML 文件下载(浏览器打开后可直接打印,版式一致)
          var t = new Date();
          var stamp = t.getFullYear() + '-' + ('0' + (t.getMonth() + 1)).slice(-2) + '-' + ('0' + t.getDate()).slice(-2);
          pm.setStatus('已开始下载:人员点名册-' + stamp + '.html');
          downloadHtmlDocument('人员点名册-' + stamp + '.html', true, rollCallHtml(rows, deptIdx, leaveMap));
        } else {
          pm.setStatus('已调起打印');
          printHtmlDocument('人员花名册', true, rosterHtml(rows, deptIdx));
        }
        setTimeout(function () { pm.close(); }, 500);
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
        class: 'btn btn-ghost', text: '点名册下载',
        title: '按当前筛选条件生成点名册文件并下载(横版A4版式,浏览器打开后可直接打印)',
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
