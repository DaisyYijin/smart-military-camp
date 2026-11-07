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
 *      - 点名册:弹窗选择党员/团员/驾驶员标志后,后端生成 Excel(单位→班两级矩阵、姓名纵填、不在位原因左侧、休假归队时间)
 *      - 花名册:先弹窗勾选需要的字段(基本档案字段,不含任职经历等子表),后端生成 Excel
 *      两者均遵循页面当前筛选条件,横版 A4、公文字体(小标宋/黑体/仿宋)
 */
(function () {
  'use strict';

  /** 需要隐藏的侧栏分组子菜单名称(精确匹配菜单文字) */
  var HIDDEN_SUBMENUS = ['全部装备'];

  /* ================= 账号管理(只管理直属子账号) ================= */
  function accountIsDirectChild(row, user) {
    return !!(row && user && user.userId != null && row.id != null && row.parentId != null &&
      String(row.parentId) === String(user.userId) && String(row.id) !== String(user.userId) &&
      (user.parentId == null || String(row.id) !== String(user.parentId)));
  }

  // 服务端树已按 effective scope 过滤。父级授权收缩后,profile 原始根可能不在有效树,
  // 因此只信任此树作为可授根白名单,不可再按 USER.departmentIds 二次裁剪。
  function accountDepartmentOptions(tree) {
    var out = [], seen = {};
    function walk(nodes, depth, ancestors) {
      (nodes || []).forEach(function (node) {
        var id = Number(node.id), key = '$' + id;
        if (!(id > 0 && id % 1 === 0 && id <= 9007199254740991) || seen[key]) return;
        seen[key] = true;
        out.push({ id: id, name: node.name || String(id), depth: depth, ancestors: ancestors.slice() });
        walk(node.children, depth + 1, ancestors.concat([id]));
      });
    }
    walk(tree, 0, []);
    return out;
  }

  function accountPayload(values, row, user, tree, requirePassword) {
    if (!user || user.userId == null) throw new Error('登录状态已变更,请重新登录');
    if (row && !accountIsDirectChild(row, user)) throw new Error('只能管理自己的直属子账号');
    var username = String(row ? row.username || '' : values.username || '').trim();
    var displayName = String(values.displayName || '').trim();
    var password = String(values.password || '');
    if (!username) throw new Error('请输入账号');
    if (!row && !/^[A-Za-z0-9_.@-]{1,64}$/.test(username)) throw new Error('账号须为1至64位字母、数字或 _ . @ -');
    if (!displayName || displayName.length > 100) throw new Error('显示名称不能为空且不能超过100字');
    if ((!row || requirePassword || password.length > 0) && (password.length < 6 || password.length > 256)) throw new Error('密码长度须为6至256位');
    var options = accountDepartmentOptions(tree), selected = [], ids = values.departmentIds || [];
    ids.forEach(function (raw) {
      var id = Number(raw), found = false;
      options.forEach(function (opt) { if (opt.id === id) found = true; });
      if (!found) throw new Error('授权不得超出本人现有单位范围,请取消不可授权的单位');
      if (selected.indexOf(id) < 0) selected.push(id);
    });
    // 只提交勾选根:选了上级时,去掉已经被该上级覆盖的下级根。
    selected = selected.filter(function (id) {
      var option = options.filter(function (opt) { return opt.id === id; })[0];
      return !option.ancestors.some(function (parent) { return selected.indexOf(parent) >= 0; });
    });
    return { username: username, displayName: displayName, password: password,
      enabled: values.enabled === true, departmentIds: selected };
  }

  function openAccountForm(row, reload, action) {
    if (row && !accountIsDirectChild(row, USER)) { toast('只能管理自己的直属子账号', 'err'); return; }
    var owner = USER && USER.userId, token = TOKEN, tree = [], ready = false, saving = false, closed = false;
    var username = h('input', { type: 'text', autocomplete: 'off', readonly: !!row, placeholder: '例如：unit.b', maxlength: '64', 'data-account-field': 'username' });
    var displayName = h('input', { type: 'text', autocomplete: 'off', placeholder: '填写使用人或单位名称', maxlength: '100', 'data-account-field': 'displayName' });
    var password = h('input', { type: 'password', autocomplete: 'new-password', 'data-account-field': 'password',
      placeholder: row && action !== 'reset' ? '留空不修改密码' : '至少6位' });
    var enabled = h('select', { 'data-account-field': 'enabled' },
      h('option', { value: 'true', text: '启用' }), h('option', { value: 'false', text: '停用' }));
    username.value = row ? row.username || '' : '';
    displayName.value = row ? row.displayName || '' : '';
    enabled.value = (action === 'toggle' ? row.enabled !== true : (!row || row.enabled === true)) ? 'true' : 'false';
    var departmentBox = h('div', { class: 'account-departments', text: '正在加载本人可授权单位…' });
    var selection = h('span', { class: 'account-selection', 'aria-live': 'polite', text: '正在加载…' });
    var error = h('div', { class: 'account-error', role: 'alert', 'aria-live': 'polite' });
    function field(label, input, hint, required) {
      input.setAttribute('aria-label', label);
      return h('label', { class: 'account-field' }, h('span', { class: 'account-label' },
        h('span', { text: label }), required ? h('i', { class: 'account-required', text: '*', 'aria-hidden': 'true' }) : null),
        input, h('small', { class: 'account-field-help', text: hint }));
    }
    function sectionTitle(number, title, description) {
      return h('div', { class: 'account-section-title' }, h('span', { class: 'account-step', text: number, 'aria-hidden': 'true' }),
        h('div', {}, h('h3', { text: title }), h('p', { text: description })));
    }
    var body = h('form', { class: 'account-form', onsubmit: function (e) { e.preventDefault(); submit(); } },
      h('section', { class: 'account-form-section' }, sectionTitle('01', '基本信息', '设置登录信息与账号使用状态'),
        h('div', { class: 'account-fields' },
          field('账号', username, row ? '账号创建后不可修改' : '支持字母、数字及 _ . @ -', !row),
          field('显示名称', displayName, '用于识别账号的使用人或单位', true),
          field(row ? '新密码' : '登录密码', password, row && action !== 'reset' ? '留空则保留原密码' : '密码长度为6至256位', !row || action === 'reset'),
          field('状态', enabled, '停用后，该账号及其下级将无法登录', false))),
      h('section', { class: 'account-form-section account-grants' },
        sectionTitle('02', '单位授权', '可多选；选中单位自动包含所有下级单位'),
        h('div', { class: 'account-grant-bar' }, h('span', { text: '可授权单位' }), selection), departmentBox,
        h('button', { type: 'button', class: 'btn-link account-preview-button', text: '预览有效授权树 / 查看重叠提示', onclick: function () {
          if (!ready || closed || TOKEN !== token || !USER || String(USER.userId) !== String(owner)) return;
          var selected = $all('input[data-department-id]', departmentBox).filter(function (input) { return input.checked; })
            .map(function (input) { return input.getAttribute('data-department-id'); });
          openAccountScopePreview(row, selected);
        } }),
        h('p', { class: 'account-hint', text: '不勾选表示无单位业务权限。只能授予本人有效范围内的单位。可点击预览展开有效树并查看重叠账号；重叠范围共享同一业务数据，不会复制或独占数据。' })),
      h('p', { class: 'account-save-note', text: '密码、状态或授权变更后，该账号及其下级需重新登录。' }), error);
    var save = h('button', { type: 'button', class: 'btn btn-primary', text: '保存', disabled: true, onclick: submit });
    var cancel = h('button', { type: 'button', class: 'btn btn-ghost', text: '取消', onclick: function () { modal.close(); } });
    var modal = openModal({ title: !row ? '新增子级账号' : action === 'reset' ? '重置密码 - ' + row.username :
      action === 'toggle' ? (row.enabled === true ? '停用账号 - ' : '启用账号 - ') + row.username : '编辑子账号 - ' + row.username,
      body: body, footer: [cancel, save], onClose: function () { closed = true; } });
    modal.dialog.classList.add('account-dialog');
    function submit() {
      if (saving || !ready || closed) return;
      error.textContent = '';
      var payload;
      try {
        if (!TOKEN || TOKEN !== token || !USER || String(USER.userId) !== String(owner)) throw new Error('登录状态已变更,请重新登录');
        payload = accountPayload({ username: username.value, displayName: displayName.value, password: password.value,
          enabled: enabled.value === 'true', departmentIds: $all('input[data-department-id]', departmentBox)
            .filter(function (input) { return input.checked; }).map(function (input) { return input.getAttribute('data-department-id'); })
        }, row, USER, tree, action === 'reset');
      } catch (e) { error.textContent = e.message; return; }
      saving = true; save.disabled = true; cancel.disabled = true; save.textContent = '保存中…';
      var request = row ? PUT('/api/accounts/' + encodeURIComponent(row.id), payload) : POST('/api/accounts', payload);
      request.then(function () {
        if (!closed) modal.close();
        if (TOKEN === token) { toast('账号已保存；密码、状态或授权变更后,该账号需重新登录'); reload(); }
      }).catch(function (e) {
        if (!closed) error.textContent = e.message || '保存失败,请重试';
      }).then(function () {
        saving = false; save.disabled = !ready; cancel.disabled = false; save.textContent = '保存';
      });
    }
    function updateSelection() {
      var inputs = $all('input[data-department-id]', departmentBox), count = 0;
      inputs.forEach(function (input) {
        if (input.checked) count++;
        input.parentNode.classList.toggle('selected', input.checked);
      });
      selection.textContent = '已选 ' + count + ' 个单位';
    }
    function loadDepartments() {
      ready = false; save.disabled = true; error.textContent = '';
      selection.textContent = '正在加载…';
      GET('/api/departments').then(function (data) {
        if (closed || TOKEN !== token) return;
        tree = data || [];
        var options = accountDepartmentOptions(tree), known = [];
        departmentBox.innerHTML = '';
        function addOption(id, name, depth, unavailable) {
          var input = h('input', { type: 'checkbox', 'data-department-id': id, onchange: updateSelection });
          input.checked = !!(row && (row.departmentIds || []).map(String).indexOf(String(id)) >= 0);
          departmentBox.appendChild(h('label', { class: 'account-department' + (unavailable ? ' unavailable' : ''),
            style: 'padding-left:' + (14 + Math.min(depth, 12) * 18) + 'px' }, input,
            h('span', { text: name + (unavailable ? '(已不在本人范围,请取消)' : '') })));
        }
        options.forEach(function (opt) { known.push(String(opt.id)); addOption(opt.id, opt.name, opt.depth, false); });
        (row && row.departmentIds || []).forEach(function (id, index) {
          if (known.indexOf(String(id)) < 0) addOption(id, (row.departmentNames || [])[index] || '单位' + id, 0, true);
        });
        if (!options.length) departmentBox.appendChild(h('div', { class: 'account-departments-empty' },
          h('strong', { text: '暂无可授权单位' }), h('p', { text: '可先保存账号，之后再分配单位；子账号也可联系上级调整授权。' })));
        updateSelection();
        ready = true; save.disabled = false;
        if (action === 'reset') password.focus();
      }).catch(function (e) {
        if (closed) return;
        departmentBox.innerHTML = '';
        departmentBox.appendChild(h('button', { type: 'button', class: 'btn btn-ghost', text: '重新加载单位', onclick: loadDepartments }));
        selection.textContent = '加载失败';
        error.textContent = e.message || '单位加载失败，请重试';
      });
    }
    loadDepartments();
  }

  function renderAccountsPage(root) {
    var sequence = 0;
    root.innerHTML = '';
    var page = h('div', { class: 'accounts-page' });
    var error = h('div', { class: 'account-error', role: 'alert' });
    var table = h('table', { class: 'grid account-table', 'aria-label': '直属子账号列表' });
    var refresh = h('button', { type: 'button', class: 'btn btn-ghost', text: '刷新', onclick: load });
    var total = h('strong', { text: '—' }), active = h('strong', { text: '—' }), inactive = h('strong', { text: '—' });
    var listCount = h('span', { class: 'account-list-count', text: '加载中' });
    function metric(label, value, style) {
      return h('div', { class: 'account-metric ' + style }, h('span', { class: 'account-metric-label', text: label }),
        h('div', { class: 'account-metric-value' }, value, h('span', { text: '个' })));
    }
    page.appendChild(h('div', { class: 'account-page-head' },
      h('div', { class: 'account-page-title' }, h('p', { text: '为不同单位分配账号与单位权限，让协同管理更清晰。' })),
      h('div', { class: 'toolbar account-page-tools' }, refresh,
        h('button', { type: 'button', class: 'btn btn-primary', text: '新增子级账号', onclick: function () { openAccountForm(null, load); } }))));
    page.appendChild(h('div', { class: 'account-metrics' }, metric('直属子账号', total, 'total'), metric('已启用', active, 'active'), metric('已停用', inactive, 'inactive')));
    var card = h('section', { class: 'account-list-card' },
      h('div', { class: 'account-list-head' }, h('div', {}, h('h2', { text: '账号列表' }),
        h('p', { text: '查看和管理由当前账号创建的直属子账号' })), listCount), error, h('div', { class: 'table-wrap' }, table));
    page.appendChild(card);
    page.appendChild(h('div', { class: 'account-rules' },
      h('div', {}, h('strong', { text: '单位权限自动继承' }), h('p', { text: '授权包含所有下级单位，后续新增的下级单位也自动纳入。' })),
      h('div', {}, h('strong', { text: '仅管理直属子账号' }), h('p', { text: '子账号可继续创建下级，但不能授予超出自身范围的权限。' })),
      h('div', {}, h('strong', { text: '停用代替删除' }), h('p', { text: '保留账号层级；资产按保管人、车辆按驾驶员的单位归属。' }))));
    root.appendChild(page);
    function tableHead() {
      var head = h('tr');
      ['账号', '显示名称', '状态', '管理单位范围', '操作'].forEach(function (label) { head.appendChild(h('th', { text: label })); });
      table.appendChild(h('thead', {}, head));
    }
    function load() {
      if (!TOKEN || !USER) return;
      var current = ++sequence, token = TOKEN, owner = USER.userId;
      refresh.disabled = true; error.textContent = ''; table.innerHTML = '';
      total.textContent = active.textContent = inactive.textContent = '—'; listCount.textContent = '加载中';
      tableHead();
      table.appendChild(h('tbody', {}, h('tr', {}, h('td', { colspan: '5', class: 'account-loading', text: '正在加载账号…' }))));
      GET('/api/accounts').then(function (rows) {
        if (current !== sequence || TOKEN !== token || !USER || String(USER.userId) !== String(owner)) return;
        table.innerHTML = ''; tableHead();
        var children = (rows || []).filter(function (row) { return accountIsDirectChild(row, USER); });
        var enabledCount = children.filter(function (row) { return row.enabled === true; }).length;
        total.textContent = String(children.length); active.textContent = String(enabledCount); inactive.textContent = String(children.length - enabledCount);
        listCount.textContent = '共 ' + children.length + ' 个账号';
        var body = h('tbody');
        children.forEach(function (row) {
          var actions = h('td', { class: 'account-actions' });
          [['编辑', 'edit'], ['重置密码', 'reset'], [row.enabled === true ? '停用' : '启用', 'toggle']].forEach(function (item) {
            actions.appendChild(h('button', { type: 'button', class: 'btn-link' + (item[1] === 'toggle' && row.enabled === true ? ' danger' : ''), text: item[0],
              onclick: function () { openAccountForm(row, load, item[1]); } }));
          });
          actions.appendChild(h('button', { type: 'button', class: 'btn-link', text: '授权预览',
            onclick: function () { openAccountScopePreview(row); } }));
          var scopes = h('td', { class: 'account-scope' });
          (row.departmentNames || []).forEach(function (name) { scopes.appendChild(h('span', { class: 'account-scope-chip', text: name })); });
          if (!scopes.children.length) scopes.appendChild(h('span', { class: 'account-no-scope', text: '未授权单位' }));
          body.appendChild(h('tr', {}, h('td', {}, h('div', { class: 'account-identity' },
            h('span', { class: 'account-avatar', 'aria-hidden': 'true', text: String(row.displayName || row.username || '?').charAt(0) }),
            h('span', { class: 'account-username', text: row.username || '' }))), h('td', { text: row.displayName || '—' }),
            h('td', {}, h('span', { class: 'account-status ' + (row.enabled === true ? 'is-active' : 'is-inactive'), text: row.enabled === true ? '启用' : '停用' })), scopes, actions));
        });
        if (!body.children.length) body.appendChild(h('tr', {}, h('td', { colspan: '5', class: 'account-empty-cell' },
          h('div', { class: 'account-empty' }, h('span', { class: 'account-empty-icon', 'aria-hidden': 'true',
            html: '<svg width="52" height="52" viewBox="0 0 52 52" fill="none" xmlns="http://www.w3.org/2000/svg"><rect x="7" y="5" width="38" height="42" rx="7" fill="white" stroke="currentColor" stroke-width="1.6"/><circle cx="26" cy="20" r="6" stroke="currentColor" stroke-width="1.8"/><path d="M16 36c0-5.5 4.5-9 10-9s10 3.5 10 9M18 41h16" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>' }),
            h('h3', { text: '暂无直属子账号' }), h('p', { text: '点击右上角「新增子级账号」，为使用人分配账号和管理单位。' })))));
        table.appendChild(body);
      }).catch(function (e) {
        if (current !== sequence || TOKEN !== token) return;
        table.innerHTML = ''; tableHead(); listCount.textContent = '加载失败';
        table.appendChild(h('tbody', {}, h('tr', {}, h('td', { colspan: '5', class: 'account-loading', text: '账号列表暂时不可用，请点击右上角刷新重试。' }))));
        error.textContent = e.message || '账号加载失败,请重试';
      }).then(function () { if (current === sequence) refresh.disabled = false; });
    }
    load();
  }

  function openAccountScopePreview(row, selected) {
    if (!TOKEN || !USER || (row && !accountIsDirectChild(row, USER))) { toast('只能预览直属子账号授权', 'err'); return; }
    var token = TOKEN, owner = USER.userId, closed = false;
    var body = h('div', { class: 'account-scope-preview', text: '正在展开有效授权范围…' });
    var modal = openModal({ title: '授权范围预览' + (row ? ' - ' + row.username : ''), body: body,
      footer: [h('button', { type: 'button', class: 'btn btn-ghost', text: '关闭', onclick: function () { modal.close(); } })],
      onClose: function () { closed = true; } });
    var query = [];
    if (row) query.push('accountId=' + encodeURIComponent(row.id));
    if (selected !== undefined) {
      query.push('draft=true');
      if (selected.length) query.push('departmentIds=' + encodeURIComponent(selected.join(',')));
    }
    GET('/api/accounts/scope-preview' + (query.length ? '?' + query.join('&') : '')).then(function (data) {
      if (closed || TOKEN !== token || !USER || String(USER.userId) !== String(owner)) return;
      body.innerHTML = '';
      body.appendChild(h('p', { class: 'account-hint', text: data.explanation || '重叠范围共享同一业务数据，不改变现有权限语义。' }));
      if (data.draft) body.appendChild(h('p', { class: 'account-hint', text: '这是当前勾选范围的只读预览，尚未保存。保存时服务端会重新校验。' }));
      if (!data.enabled) body.appendChild(h('p', { class: 'account-hint', text: '该账号当前已停用，以下仅展示配置范围，不表示可以登录访问。' }));
      body.appendChild(h('h3', { text: '有效单位树（' + Number(data.effectiveDepartmentCount || 0) + ' 个单位）' }));
      var treeBox = h('div', { class: 'account-preview-tree' });
      var options = accountDepartmentOptions(data.effectiveTree || []);
      options.forEach(function (node) {
        treeBox.appendChild(h('div', { style: 'padding-left:' + (12 + Math.min(node.depth, 12) * 18) + 'px', text: node.name }));
      });
      if (!options.length) treeBox.appendChild(h('p', { text: '当前没有有效单位业务权限。' }));
      body.appendChild(treeBox);
      body.appendChild(h('h3', { text: '与其他直属子账号的重叠配置' }));
      (data.overlaps || []).forEach(function (item) {
        body.appendChild(h('p', { class: 'account-overlap', text: item.username + '：重叠 ' + Number(item.departmentCount || 0) + ' 个单位' +
          (item.enabled ? '' : '（已停用，启用后适用）') }));
      });
      if (!(data.overlaps || []).length) body.appendChild(h('p', { class: 'account-hint', text: '未发现与其他直属子账号重叠；此提示不比较非直属账号。' }));
    }).catch(function (err) {
      if (closed || TOKEN !== token || !USER || String(USER.userId) !== String(owner)) return;
      body.textContent = '预览失败：' + (err.message || '请关闭后重试');
    });
  }

  window.accountsView = function () { return { render: renderAccountsPage }; };

  // 近期事件没有稳定记录 ID；明确跳转相关列表，不用姓名猜测目标记录。
  function recentEventTarget(tag, text) {
    if (tag === '休假') return text.indexOf('按计划') >= 0 ? { key: 'leave-plan', label: '前往休假计划' } : { key: 'leave', label: '前往休假登记' };
    var targets = { '物资': ['material', '前往物资列表'], '车辆': ['vehicle', '前往车辆列表'],
      '装备': ['equipment', '前往装备列表'], '生日': ['personnel', '前往人员列表'], '党务': ['personnel', '前往人员列表'],
      '借还': ['item-loans', '前往物品借还'] };
    return targets[tag] ? { key: targets[tag][0], label: targets[tag][1] } : null;
  }
  function enhanceRecentEvents() {
    $all('.dash-event', document).forEach(function (event) {
      if (event.querySelector('[data-event-location]')) return;
      var tag = event.querySelector('.tag'), text = event.querySelector('.txt');
      var target = recentEventTarget(tag ? tag.textContent : '', text ? text.textContent : '');
      if (!target) return;
      event.appendChild(h('button', { type: 'button', class: 'btn-link event-location', 'data-event-location': '1', text: target.label,
        title: '打开相关业务列表；请按事件内容核对记录', onclick: function () {
          if (!TOKEN || !USER) return;
          if (target.key === 'leave-plan') { location.hash = 'leave-plan'; return; }
          if (target.key === 'equipment') {
            // “全部装备”菜单被本扩展隐藏，通过原生 hash 路由仍可打开列表。
            location.hash = target.key; return;
          }
          location.hash = target.key;
        } }));
    });
  }
  if (document.body && typeof MutationObserver !== 'undefined') {
    new MutationObserver(function () { enhanceRecentEvents(); })
      .observe(document.body, { childList: true, subtree: true });
  }
  enhanceRecentEvents();

  /* ================= 字典下拉统一接入 ================= */
  function dictOptions(type, fallback) {
    return function () {
      var labels = dictLabels(type);
      return labels.length ? labels : fallback.slice();
    };
  }
  function replaceColumnOptions(config, key, options) {
    if (!config || !config.columns) return;
    config.columns.forEach(function (column) {
      if (column.key === key) column.options = options;
    });
  }
  if (typeof FAMILY_SUB !== 'undefined') replaceColumnOptions(FAMILY_SUB, 'relationship', dictOptions('family_relation', FAMILY_RELATIONS));
  if (typeof DRIVER_SUB !== 'undefined') replaceColumnOptions(DRIVER_SUB, 'license_type', dictOptions('license_type', ['军本', '地方本']));
  if (typeof HOUSING_SUB !== 'undefined') replaceColumnOptions(HOUSING_SUB, 'housing_type', dictOptions('housing_type', ['自有住房', '按揭住房', '部队公寓', '租房', '其他']));
  if (typeof REWARD_SUB !== 'undefined') replaceColumnOptions(REWARD_SUB, 'kind', dictOptions('reward_punish_kind', ['奖励', '处分']));
  if (typeof personnelCfg !== 'undefined') {
    (personnelCfg.moreFilters || []).forEach(function (field) {
      if (field.key === 'gender') field.options = dictOptions('gender', ['男', '女']);
      if (field.key === 'onlyChild') field.options = dictOptions('only_child', ['是', '否']);
    });
    (personnelCfg.form || []).forEach(function (field) {
      if (field.key === 'gender') field.options = dictOptions('gender', ['男', '女']);
    });
  }

  /* 历史补丁已移除:"卡状态紧跟卡类型"现由字典业务分组(证件与卡)保证;原 MutationObserver 补丁因列表外包 .dict-types-list 早已失效空转 */

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
      { key: 'education', label: '学历', type: 'select', required: true, options: function () { return dictLabels('education'); } },
      { key: 'eduDegree', label: '学位', type: 'select', options: function () { return dictLabels('edu_degree'); } },
      { key: 'eduEnrollDate', label: '入学时间', type: 'date' },
      { key: 'eduGraduateDate', label: '毕业时间', type: 'date' },
      { key: 'eduForm', label: '学历形式', type: 'select', options: function () { return dictLabels('edu_form'); } },
      { key: 'eduSchool', label: '毕业院校' },
      { key: 'eduMajor', label: '所学专业' },
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
    if (!out.departmentId) delete out.includeDescendants;
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

  /** 点名册:标志与排序仅用于本次 Excel 下载,每次打开重新初始化。 */
  function downloadRollcall() {
    var options = [
      { key: 'party', on: false, label: '党员标志 ☭', tip: '政治面貌含「党员」的人员,姓名前加党徽' },
      { key: 'league', on: false, label: '团员标志 ✪', tip: '政治面貌含「团员」的人员,姓名前加团徽(圆环五角星)' },
      { key: 'driver', on: false, label: '驾驶员标志 ⊛', tip: '有驾驶证记录的人员(吊销除外),姓名后加方向盘标志' }
    ];
    var sortFields = [
      { key: 'serviceYears', label: '军龄', initial: 'desc', asc: '低到高', desc: '高到低' },
      { key: 'rank', label: '军衔', initial: 'desc', asc: '低到高', desc: '高到低' },
      { key: 'age', label: '年龄', initial: 'desc', asc: '小到大', desc: '大到小' },
      { key: 'name', label: '姓名', initial: 'asc', asc: '中文正序', desc: '中文倒序' },
      { key: 'entryDate', label: '入伍/入职时间', initial: 'asc', asc: '早到晚', desc: '晚到早' },
      { key: 'postDate', label: '任职时间', initial: 'asc', asc: '早到晚', desc: '晚到早' },
      { key: 'category', label: '人员类别', initial: 'asc', asc: '中文正序', desc: '中文倒序' },
      { key: 'education', label: '学历', initial: 'desc', asc: '低到高', desc: '高到低' },
      { key: 'post', label: '职务/岗位', initial: 'asc', asc: '中文正序', desc: '中文倒序' }
    ];
    var ordinals = ['第一', '第二', '第三', '第四', '第五'];
    var wrapStyle = 'display:flex;flex-wrap:wrap;align-items:center;gap:8px';
    var noteStyle = 'font-size:12.5px;color:#66707f;margin:8px 0;line-height:1.7';
    var sortRows = [];
    var sortList = h('div', { class: 'rollcall-sort-list' });
    var error = h('div', { class: 'rollcall-error', role: 'alert', style: 'color:#c0392b;font-size:13px' });
    function setMark(opt, on) {
      opt.on = on;
      opt.chip.classList.toggle('on', on);
      opt.chip.setAttribute('aria-pressed', on ? 'true' : 'false');
    }
    var chips = options.map(function (opt) {
      opt.chip = h('button', {
        type: 'button', class: 'field-chip', text: opt.label, title: opt.tip,
        'aria-pressed': 'false', 'data-rollcall-mark': opt.key,
        onclick: function () { setMark(opt, !opt.on); }
      });
      return opt.chip;
    });
    // 单位分组:编制/实际单位二选一,默认编制;仅本次下载生效,每次打开重置。
    var actualUnitOn = false;
    var actualUnitChip = h('button', {
      type: 'button', class: 'field-chip', text: '按实际单位排列',
      title: '开启后按人员的实际在岗单位分组排列;关闭时按编制单位',
      'aria-pressed': 'false', 'data-rollcall-actual-unit': '1',
      onclick: function () { setUnitMode(!actualUnitOn); }
    });
    var establishmentChip = h('button', {
      type: 'button', class: 'field-chip on', text: '按编制单位',
      title: '按编制单位分组排列', 'aria-pressed': 'true', 'data-rollcall-unit-mode': 'establishment',
      onclick: function () { setUnitMode(false); }
    });
    function setUnitMode(actual) {
      actualUnitOn = actual;
      actualUnitChip.classList.toggle('on', actual);
      actualUnitChip.setAttribute('aria-pressed', actual ? 'true' : 'false');
      establishmentChip.classList.toggle('on', !actual);
      establishmentChip.setAttribute('aria-pressed', actual ? 'false' : 'true');
    }
    function setAllMarks(on) {
      options.forEach(function (opt) { setMark(opt, on); });
    }
    function fieldFor(key) {
      for (var i = 0; i < sortFields.length; i++) {
        if (sortFields[i].key === key) return sortFields[i];
      }
      return null;
    }
    function renderSortRows() {
      sortList.textContent = '';
      sortRows.forEach(function (row, index) {
        var label = ordinals[index] + '排序条件';
        row.label.textContent = label;
        row.field.setAttribute('aria-label', label + '字段');
        row.direction.setAttribute('aria-label', label + '方向');
        row.up.disabled = index === 0;
        row.down.disabled = index === sortRows.length - 1;
        row.up.setAttribute('aria-label', '上移' + label);
        row.down.setAttribute('aria-label', '下移' + label);
        row.remove.setAttribute('aria-label', '移除' + label);
        sortList.appendChild(row.el);
      });
      addSort.disabled = sortRows.length >= 5;
      addSort.textContent = addSort.disabled ? '最多5个排序条件' : '添加' + ordinals[sortRows.length] + '排序条件';
      error.textContent = '';
    }
    function moveSortRow(row, offset) {
      var index = sortRows.indexOf(row);
      var next = index + offset;
      if (index < 0 || next < 0 || next >= sortRows.length) return;
      sortRows.splice(index, 1);
      sortRows.splice(next, 0, row);
      renderSortRows();
      row.field.focus();
    }
    function addSortRow() {
      if (sortRows.length >= 5) return;
      var row = {};
      row.label = h('span', { style: 'font-size:13px' });
      row.direction = h('select', { 'data-rollcall-direction': '1', disabled: true, style: 'max-width:100%' });
      row.field = h('select', {
        'data-rollcall-field': '1', style: 'flex:1 1 150px;min-width:0;max-width:100%',
        onchange: function () {
          var field = fieldFor(row.field.value);
          row.direction.textContent = '';
          row.direction.disabled = !field;
          if (field) {
            row.direction.appendChild(h('option', { value: 'asc', text: field.asc }));
            row.direction.appendChild(h('option', { value: 'desc', text: field.desc }));
            row.direction.value = field.initial;
          } else {
            row.direction.appendChild(h('option', { value: '', text: '不排序' }));
            row.direction.value = '';
          }
          error.textContent = '';
        }
      }, h('option', { value: '', text: '请选择字段（不排序）' }), sortFields.map(function (field) {
        return h('option', { value: field.key, text: field.label });
      }));
      row.direction.appendChild(h('option', { value: '', text: '不排序' }));
      row.up = h('button', { type: 'button', class: 'btn btn-ghost btn-sm', text: '上移', onclick: function () { moveSortRow(row, -1); } });
      row.down = h('button', { type: 'button', class: 'btn btn-ghost btn-sm', text: '下移', onclick: function () { moveSortRow(row, 1); } });
      row.remove = h('button', {
        type: 'button', class: 'btn btn-ghost btn-sm', text: '移除',
        onclick: function () {
          var index = sortRows.indexOf(row);
          if (index < 0) return;
          sortRows.splice(index, 1);
          renderSortRows();
          if (sortRows.length) sortRows[Math.min(index, sortRows.length - 1)].field.focus();
          else addSort.focus();
        }
      });
      row.el = h('div', { class: 'rollcall-sort-row', style: wrapStyle + ';margin:6px 0' },
        row.label, row.field, row.direction, row.up, row.down, row.remove);
      sortRows.push(row);
      renderSortRows();
    }
    var addSort = h('button', {
      type: 'button', class: 'btn btn-ghost btn-sm', 'data-rollcall-add-sort': '1',
      onclick: function () {
        if (sortRows.length >= 5) return;
        addSortRow();
        sortRows[sortRows.length - 1].field.focus();
      }
    });
    addSortRow();
    var modal = null;
    var cardStyle = 'border:1px solid #e3e9f2;border-radius:10px;padding:12px 14px;margin-bottom:12px;background:#fbfcfe';
    var cardHead = 'display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap';
    var hintStyle = 'font-size:12px;color:#8a97a8;margin:8px 0 0;line-height:1.6';
    modal = openModal({
      title: '点名册下载',
      body: h('div', { style: 'font-size:13.5px' },
        h('div', { style: cardStyle },
          h('strong', { text: '生成说明' }),
          h('p', { style: noteStyle + ';margin:6px 0 0', text: '按当前筛选条件生成点名册 Excel（横向A4，含休假归队时间）。下载后使用 Excel/WPS 的打印预览与打印。' }),
          h('details', { style: 'margin-top:6px' },
            h('summary', { style: 'cursor:pointer;color:#2f6fd6;font-size:12.5px;outline:none', text: '版式与排序规则说明' }),
            h('p', { style: noteStyle, text: '同级单位从左到右排列，姓名行与所属单位表头等宽合并；本级人员下方再显示子单位及人员。不在位人员仍按原因集中列示。' }),
            h('p', { style: noteStyle, text: '第一条件相同再比下一条件；空行忽略，无排序条件时保留原来人员顺序。排序不改变单位层级及不在位原因分组；仅在各单位直属姓名、不在位原因块内部比较人员。' }),
            h('p', { style: noteStyle, text: '军龄优先使用有效相当入伍日期，非法或未来时兜底有效入伍日期，按精确日期比较而非取整年数。空值、无效值及未知军衔始终排最后，军龄/年龄的未来日期无效。军衔按明确高低，四级军士长与一级上士同档、上士与二级上士同档；学历按后台明确等级（高中/职高/中专同档），不使用字典序号。姓名、人员类别及职务/岗位按中文文本排序，职务/岗位不表示职位高低。' }))),
        h('div', { style: cardStyle },
          h('div', { style: cardHead },
            h('strong', { text: '单位分组' })),
          h('div', { style: wrapStyle + ';margin-top:8px' }, establishmentChip, actualUnitChip),
          h('p', { style: hintStyle, text: '默认按编制单位分组；开启后按实际在岗单位分组' })),
        h('div', { style: cardStyle },
          h('div', { style: cardHead },
            h('strong', { text: '身份标志（默认全不选）' }),
            h('div', { style: wrapStyle },
              h('button', { type: 'button', class: 'btn btn-ghost btn-sm', text: '全不选标志', onclick: function () { setAllMarks(false); } }),
              h('button', { type: 'button', class: 'btn btn-ghost btn-sm', text: '全选标志', onclick: function () { setAllMarks(true); } }))),
          h('div', { style: wrapStyle + ';margin-top:8px' }, chips),
          h('p', { style: hintStyle, text: '点击标志按钮选中/取消（蓝色=选中）；允许不加标志。党员同时是驾驶员时两个标志都会加上。' })),
        h('div', { style: cardStyle },
          h('div', { style: cardHead },
            h('strong', { text: '人员排序（可选，最多5个条件）' })),
          h('div', { style: 'margin-top:8px' }, sortList, addSort),
          error)),
      footer: [
        h('button', { type: 'button', class: 'btn btn-ghost', text: '取消', onclick: function () { modal.close(); } }),
        h('button', {
          type: 'button', class: 'btn btn-primary', text: '下载 Excel',
          onclick: function () {
            var sort = [];
            var seen = {};
            for (var i = 0; i < sortRows.length; i++) {
              var row = sortRows[i];
              var key = row.field.value;
              if (!key) continue;
              if (!fieldFor(key) || (row.direction.value !== 'asc' && row.direction.value !== 'desc')) {
                error.textContent = '请选择有效的排序字段和方向';
                row.field.focus();
                return;
              }
              if (seen[key]) {
                error.textContent = '排序字段不能重复：' + fieldFor(key).label;
                row.field.focus();
                return;
              }
              seen[key] = true;
              sort.push(key + ':' + row.direction.value);
            }
            var f = readFilterParams();
            // 下载选项由本弹窗独立控制,不保存到筛选面板或其他导出。
            delete f.marks;
            delete f.sort;
            delete f.actualUnit;
            var on = options.filter(function (opt) { return opt.on; }).map(function (opt) { return opt.key; });
            if (on.length) f.marks = on.join(',');
            if (sort.length) f.sort = sort.join(',');
            if (actualUnitOn) f.actualUnit = 'true';
            modal.close();
            var pm = showProgressModal('生成点名册');
            pm.setStatus('正在按当前筛选条件生成…');
            downloadExcel('/api/personnel/rollcall/excel?' + toQueryString(f), '点名册', pm);
          }
        })
      ]
    });
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
      // 不自设内部滚动,字段区随弹窗 .dialog-body 一起滚动,避免出现双滚动条。
      var grid = h('div', { style: 'display:flex;flex-wrap:wrap;gap:8px;padding:4px 2px' });
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
          grid,
          h('p', { style: 'font-size:12px;color:#66707f;margin-top:10px', text: '驾驶证按登记记录导出，多证在同一人员行内分行显示。实际单位显示完整路径。' })),
        footer: [
          h('button', {
            type: 'button', class: 'btn btn-ghost', text: '全选',
            onclick: function () { setAll(true); }
          }),
          h('button', {
            type: 'button', class: 'btn btn-ghost', text: '全不选',
            onclick: function () { setAll(false); }
          }),
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
      // 独立标记保证子账号隐藏「删除撤回」后,点名册/花名册仍然可用。
      var btns = toolbar.querySelectorAll('button');
      var isPersonnel = toolbar.getAttribute('data-personnel-toolbar') === '1';
      for (var j = 0; j < btns.length; j++) {
        if (btns[j].textContent.indexOf('删除撤回') >= 0) { isPersonnel = true; break; }
      }
      if (!isPersonnel) continue;
      toolbar.setAttribute('data-printbtns', '1');
      var grp = h('div', { class: 'toolbar-left' });
      grp.appendChild(h('button', {
        class: 'btn btn-ghost', text: '点名册下载',
        title: '按当前筛选条件生成点名册 Excel(横版A4,含休假归队时间;可勾选党员/团员/驾驶员标志)',
        onclick: function () { downloadRollcall(); }
      }));
      grp.appendChild(h('button', {
        class: 'btn btn-ghost', text: '花名册下载',
        title: '先勾选需要的字段(姓名/身份证/单位/所在点位等),再生成花名册 Excel 下载',
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
      { key: 'departmentPath', label: '单位' },
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
    if (!hasGlobalAccess()) { toast('当前账号无权修改全局考核方案', 'err'); return; }
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
    if (!hasGlobalAccess()) { toast('当前账号无权修改全局考核方案', 'err'); return; }
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

  // 包装现有工具栏,不改业务 CRUD 和只读字典/评分缓存加载。
  function installAccountRoleGuards() {
    var personnelToolbar = personnelCfg.toolbarExtra;
    personnelCfg.toolbarExtra = function (bar, actions) {
      bar.setAttribute('data-personnel-toolbar', '1');
      personnelToolbar(bar, actions);
      if (!hasGlobalAccess()) $all('button', bar).forEach(function (button) {
        if (button.textContent === '删除撤回') button.remove();
      });
    };
    [fitnessCfg, militaryCfg].forEach(function (cfg) {
      var original = cfg.toolbarExtra;
      cfg.toolbarExtra = function (bar, actions) { if (hasGlobalAccess() && original) original(bar, actions); };
    });
    function protect(original) {
      return function () {
        if (!hasGlobalAccess()) { toast('当前账号无权操作此全局配置', 'err'); return; }
        return original.apply(this, arguments);
      };
    }
    openPersonnelUndo = protect(openPersonnelUndo);
    openFitnessStdManager = protect(openFitnessStdManager);
    openGradeRuleManager = protect(openGradeRuleManager);
    openBodyStdManager = protect(openBodyStdManager);
    importStdExcel = protect(importStdExcel);
  }
  installAccountRoleGuards();

  // 视图切换/重渲染后补挂按钮(去抖,等 DOM 稳定)
  var btnTimer = 0;
  new MutationObserver(function () {
    clearTimeout(btnTimer);
    btnTimer = setTimeout(addPrintButtons, 50);
  }).observe(document.body, { childList: true, subtree: true });

  /* ================= 6. 装备/物资照片(每件一张) =================
     在模块配置之后挂接,不覆盖 app.js 业务流程与保存逻辑:
     表单顶部照片区(预览/选择/替换/移除)+ 列表"照片"列缩略图异步加载 */
  function entityPhotoBlockFactory(opts) {
    return function (row) {
      var state = { data: null, remove: false };
      var preview = h('div', { class: opts.cssPrefix + '-preview', text: '无照片' });
      if (row && row.photo) {
        apiBlob(opts.api + '/' + row.id + '/photo').then(function (r) {
          bindBlobUrl(preview, r.blob, true);
          preview.textContent = '';
        }).catch(function () { preview.textContent = '无照片'; });
      }
      var input = h('input', { type: 'file', accept: 'image/jpeg,image/png', style: 'display:none' });
      input.addEventListener('change', function () {
        var file = input.files && input.files[0];
        input.value = '';
        if (!file) return;
        var mime = String(file.type || '').toLowerCase();
        if (!/\.(jpe?g|png)$/i.test(file.name) || (mime && !/^image\/(jpeg|png)$/.test(mime))) {
          toast('仅支持 JPG 或 PNG 图片', 'err');
          return;
        }
        var reader = new FileReader();
        reader.onload = function () {
          state.data = reader.result;
          state.remove = false;
          preview.style.backgroundImage = 'url(' + state.data + ')';
          preview.textContent = '';
        };
        reader.onerror = function () { toast('读取图片失败', 'err'); };
        reader.readAsDataURL(file);
      });
      var el = h('div', { class: 'form-item full' });
      el.appendChild(h('label', { text: opts.label }));
      el.appendChild(h('div', { class: opts.cssPrefix + '-box' }, preview,
        h('div', {}, input,
          h('div', { class: 'photo-actions' },
            h('button', { class: 'btn btn-ghost btn-sm photo-action', text: '选择照片', onclick: function () { input.click(); } }),
            h('button', { class: 'btn btn-ghost btn-sm photo-action', text: '移除照片', onclick: function () {
              state.data = null; state.remove = true;
              preview.style.backgroundImage = ''; preview.textContent = '无照片';
            } })),
          h('div', { class: 'hint', text: '支持 JPG、PNG 格式' }))));
      return { el: el, get: function () {
        if (state.data) return { photoBase64: state.data };
        if (state.remove) return { photo: '' };
        return {};
      } };
    };
  }

  function loadEntityPhotos(root, opts) {
    $all('img[data-entity="' + opts.attr + '"]', root).forEach(function (img) {
      apiBlob(opts.api + '/' + img.getAttribute('data-id') + '/photo').then(function (r) {
        bindBlobUrl(img, r.blob, false);
      }).catch(function () { img.style.display = 'none'; });
    });
  }

  /** 挂接一个模块:表单照片块 + 列表照片列 + 缩略图加载(本地登记,防重复挂接) */
  var PHOTO_HOOKED = [];
  function hookEntityPhoto(cfg, opts) {
    if (!cfg || !cfg.columns || PHOTO_HOOKED.indexOf(cfg) >= 0) return;
    PHOTO_HOOKED.push(cfg);
    cfg.formTop = entityPhotoBlockFactory(opts);
    cfg.afterTable = function (root) { loadEntityPhotos(root, opts); };
    if (!(cfg.columns[0] && cfg.columns[0].key === '__photo')) {
      cfg.columns.unshift({ key: '__photo', label: '照片', w: 64, render: function (r) {
        return r.photo
          ? '<img class="' + opts.cssPrefix + '-thumb" data-entity="' + opts.attr + '" data-id="' + r.id + '" alt="照片"/>'
          : '<span class="photo-empty">无</span>';
      } });
    }
  }

  try {
    if (typeof equipmentCfg !== 'undefined') {
      hookEntityPhoto(equipmentCfg, { label: '装备照片', cssPrefix: 'equipment-photo', api: '/api/equipment', attr: 'equipment' });
      [typeof equipmentGunCfg !== 'undefined' ? equipmentGunCfg : null,
       typeof equipmentLargeCfg !== 'undefined' ? equipmentLargeCfg : null,
       typeof equipmentSmallCfg !== 'undefined' ? equipmentSmallCfg : null].forEach(function (cfg) {
        hookEntityPhoto(cfg, { label: '装备照片', cssPrefix: 'equipment-photo', api: '/api/equipment', attr: 'equipment' });
      });
    }

    if (typeof vehicleCfg !== 'undefined') {
      hookEntityPhoto(vehicleCfg, { label: '车辆照片', cssPrefix: 'vehicle-photo', api: '/api/vehicle', attr: 'vehicle' });
    }

    if (typeof materialCfg !== 'undefined') {
      hookEntityPhoto(materialCfg, { label: '物资照片', cssPrefix: 'material-photo', api: '/api/material', attr: 'material' });
    }
  } catch (err) {
    try { console.warn('装备/物资照片挂接失败', err); } catch (e2) { }
  }

  /* 人员照片提示文案:app.js 写入通道受限,单张 5MB 业务限制已取消,
     旧提示"支持 jpg/png,不超过 5MB"不再符合现状,渲染后由这里纠正 */
  (function fixPersonnelPhotoHint() {
    var OLD = '支持 jpg/png,不超过 5MB';
    var NEW = '支持 JPG、PNG 格式';
    function apply() {
      var nodes = document.querySelectorAll('.photo-box div[style*="a6b3c4"]');
      for (var i = 0; i < nodes.length; i++) {
        if (nodes[i].textContent === OLD) nodes[i].textContent = NEW;
      }
      var buttons = document.querySelectorAll('.photo-box button');
      for (var j = 0; j < buttons.length; j++) buttons[j].classList.add('photo-action');
    }
    apply();
    if (typeof MutationObserver !== 'undefined' && document.body) {
      var t = 0;
      new MutationObserver(function () { clearTimeout(t); t = setTimeout(apply, 30); })
        .observe(document.body, { childList: true, subtree: true });
    }
  })();
})();
