package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.auth.ForbiddenException;
import com.szjx.personnel.server.dao.AdminUserDao;
import com.szjx.personnel.server.dao.AdminUserDao.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

@Service
public class AccountService {
    private final AdminUserDao users;
    private final JdbcTemplate jdbc;
    private final TokenManager tokens;

    public AccountService(AdminUserDao users, JdbcTemplate jdbc, TokenManager tokens) {
        this.users = users;
        this.jdbc = jdbc;
        this.tokens = tokens;
    }

    /** Only mutable fields understood by account management; parent/admin can never be client supplied. */
    public static class AccountRequest {
        public String username;
        public String displayName;
        public String password;
        public Boolean enabled;
        public Set<Long> departmentIds;
    }

    @Transactional(readOnly = true)
    public DataScope.Context authenticateScope(String username) {
        User current = users.findByUsername(username);
        if (current == null) throw new ForbiddenException("账号不存在或已失效");
        User identity = current;
        Set<Long> visited = new HashSet<Long>();
        Set<Long> effective = null;
        Map<Long, Long> tree = departmentTree();
        while (true) {
            if (!visited.add(current.id) || !current.enabled || current.parentId == null || current.parentId < 0) {
                throw new ForbiddenException("账号或祖先账号已禁用，或账号层级无效");
            }
            if (current.parentId == 0) break;
            Set<Long> expanded = expand(users.departmentIds(current.id), tree);
            if (effective == null) effective = expanded;
            else effective.retainAll(expanded);
            current = users.findById(current.parentId);
            if (current == null) throw new ForbiddenException("账号祖先不存在");
        }
        boolean admin = identity.parentId == 0;
        return new DataScope.Context(identity.id, identity.username, admin,
                admin ? tree.keySet() : (effective == null ? Collections.<Long>emptySet() : effective));
    }

    private Map<Long, Long> departmentTree() {
        Map<Long, Long> tree = new LinkedHashMap<Long, Long>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT id,parent_id FROM department ORDER BY id")) {
            Long id = ((Number) row.get("id")).longValue();
            Object parent = row.get("parent_id");
            if (id > 0) tree.put(id, parent == null ? 0L : ((Number) parent).longValue());
        }
        return tree;
    }

    private Set<Long> expand(Set<Long> roots, Map<Long, Long> tree) {
        Set<Long> expanded = new LinkedHashSet<Long>();
        for (Long root : roots) if (root != null && root > 0 && tree.containsKey(root)) expanded.add(root);
        boolean changed;
        do {
            changed = false;
            for (Map.Entry<Long, Long> entry : tree.entrySet()) {
                if (expanded.contains(entry.getValue()) && expanded.add(entry.getKey())) changed = true;
            }
        } while (changed);
        return expanded;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> profile(String username) {
        authenticateScope(username);
        return profile(users.findByUsername(username));
    }

    public Map<String, Object> profile(User user) {
        if (user == null) throw new ForbiddenException("账号不存在");
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        Set<Long> roots = users.departmentIds(user.id);
        List<String> names = new ArrayList<String>();
        for (Long id : roots) {
            List<String> found = jdbc.queryForList("SELECT name FROM department WHERE id=?", String.class, id);
            names.add(found.isEmpty() ? "(单位已删除)" : found.get(0));
        }
        result.put("id", user.id);
        result.put("userId", user.id);
        result.put("username", user.username);
        result.put("displayName", user.displayName);
        result.put("isAdmin", Long.valueOf(0).equals(user.parentId));
        result.put("departmentIds", roots);
        result.put("departmentNames", names);
        result.put("parentId", user.parentId);
        result.put("enabled", user.enabled);
        return result;
    }

    private DataScope.Context actor() {
        DataScope.Context context = DataScope.get();
        if (context == null || context.userId == null) throw new ForbiddenException("缺少账号身份");
        DataScope.Context fresh = authenticateScope(context.username);
        if (!fresh.userId.equals(context.userId)) throw new ForbiddenException("账号身份已变更");
        return fresh;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listChildren() {
        DataScope.Context actor = actor();
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (User child : users.children(actor.userId)) result.add(profile(child));
        return result;
    }

    /** Read-only explanation of the existing descendant/intersection semantics, never an authorization write. */
    @Transactional(readOnly = true)
    public Map<String, Object> scopePreview(Long accountId, Set<Long> proposedRoots, boolean draft) {
        DataScope.Context actor = actor();
        User target = accountId == null ? null : users.findById(accountId);
        if (accountId != null && (target == null || !actor.userId.equals(target.parentId))) {
            throw new ForbiddenException("只能预览直属子账号授权");
        }
        Map<Long, Long> tree = departmentTree();
        Set<Long> roots = draft ? new LinkedHashSet<Long>(proposedRoots == null ? Collections.<Long>emptySet() : proposedRoots)
                : target == null ? Collections.<Long>emptySet() : users.departmentIds(target.id);
        Set<Long> effective = expand(roots, tree);
        if (draft) {
            for (Long id : roots) {
                if (id == null || !tree.containsKey(id) || id <= 0) throw new IllegalArgumentException("授权单位不存在或无效");
            }
            if (!actor.admin && !actor.departmentIds.containsAll(effective)) {
                throw new ForbiddenException("预览授权不得超出本人及所有祖先的有效范围");
            }
        }
        effective.retainAll(actor.departmentIds);
        List<Map<String, Object>> nodes = new ArrayList<Map<String, Object>>();
        Map<Long, Map<String, Object>> byId = new LinkedHashMap<Long, Map<String, Object>>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT id,name,parent_id FROM department ORDER BY id")) {
            Long id = ((Number) row.get("id")).longValue();
            if (!effective.contains(id)) continue;
            Map<String, Object> node = new LinkedHashMap<String, Object>();
            node.put("id", id); node.put("name", row.get("name")); node.put("parentId", tree.get(id));
            node.put("children", new ArrayList<Map<String, Object>>());
            byId.put(id, node);
        }
        // A corrupt department cycle must not create a cyclic JSON graph.
        for (Map.Entry<Long, Map<String, Object>> entry : byId.entrySet()) {
            Long parent = tree.get(entry.getKey());
            Set<Long> seen = new HashSet<Long>(); seen.add(entry.getKey());
            Long cursor = parent; boolean cycle = false;
            while (cursor != null && byId.containsKey(cursor)) {
                if (!seen.add(cursor)) { cycle = true; break; }
                cursor = tree.get(cursor);
            }
            if (!cycle && byId.containsKey(parent)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) byId.get(parent).get("children");
                children.add(entry.getValue());
            } else nodes.add(entry.getValue());
        }
        List<Map<String, Object>> overlaps = new ArrayList<Map<String, Object>>();
        for (User sibling : users.children(actor.userId)) {
            if (accountId != null && sibling.id == accountId.longValue()) continue;
            Set<Long> shared = expand(users.departmentIds(sibling.id), tree);
            shared.retainAll(effective);
            if (shared.isEmpty()) continue;
            Map<String, Object> overlap = new LinkedHashMap<String, Object>();
            overlap.put("accountId", sibling.id); overlap.put("username", sibling.username);
            overlap.put("enabled", sibling.enabled); overlap.put("departmentCount", shared.size());
            overlaps.add(overlap);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("departmentIds", roots); result.put("effectiveDepartmentCount", effective.size());
        result.put("effectiveTree", nodes); result.put("overlaps", overlaps);
        result.put("enabled", target == null || target.enabled);
        result.put("draft", draft);
        result.put("explanation", "授权包含所有下级单位，并与本人及所有祖先的有效范围取交集；停用账号不能登录。重叠范围共享同一业务数据，不会复制或独占数据，也不改变现有权限语义。重叠提示仅比较本人直属子账号，包含已停用账号的配置范围。");
        return result;
    }

    private void reserveWriter() {
        DataScope.Context context = DataScope.get();
        if (context == null || context.userId == null) throw new ForbiddenException("缺少账号身份");
        users.lockAccount(context.userId);
    }

    private Set<Long> validate(AccountRequest request, DataScope.Context actor, boolean create) {
        if (request == null) throw new IllegalArgumentException("请求体不能为空");
        if (create && (request.username == null || !request.username.matches("[A-Za-z0-9_.@-]{1,64}"))) {
            throw new IllegalArgumentException("用户名须为1至64位字母、数字或 _ . @ -");
        }
        if (request.displayName == null || request.displayName.trim().isEmpty() || request.displayName.length() > 100) {
            throw new IllegalArgumentException("显示名称不能为空且不能超过100字");
        }
        if ((create || (request.password != null && !request.password.isEmpty())) &&
                (request.password == null || request.password.length() < 6 || request.password.length() > 256)) {
            throw new IllegalArgumentException("密码长度须为6至256位");
        }
        if (request.departmentIds == null) throw new IllegalArgumentException("departmentIds 必须提供，空数组表示无单位权限");
        if (!create && request.enabled == null) throw new IllegalArgumentException("enabled 必须提供");
        Map<Long, Long> tree = departmentTree();
        Set<Long> roots = new LinkedHashSet<Long>(request.departmentIds);
        for (Long id : roots) {
            if (id == null || id <= 0 || !tree.containsKey(id)) throw new IllegalArgumentException("授权单位不存在或无效");
        }
        if (!actor.admin && !actor.departmentIds.containsAll(expand(roots, tree))) {
            throw new ForbiddenException("授予单位及其后代不得超出本人及所有祖先的有效范围");
        }
        return roots;
    }

    @Transactional
    public Map<String, Object> create(AccountRequest request) {
        reserveWriter();
        DataScope.Context actor = actor();
        Set<Long> roots = validate(request, actor, true);
        if (users.findByUsername(request.username) != null) throw new IllegalArgumentException("用户名已存在");
        long id = users.insertChild(actor.userId, request.username, PasswordUtil.hash(request.password),
                request.displayName.trim(), request.enabled == null || request.enabled);
        users.replaceDepartments(id, roots);
        return profile(users.findById(id));
    }

    @Transactional
    public Map<String, Object> update(Long id, AccountRequest request) {
        reserveWriter();
        DataScope.Context actor = actor();
        User target = users.findById(id);
        if (target == null || target.id == actor.userId || !actor.userId.equals(target.parentId)) {
            throw new ForbiddenException("只能管理直属子账号");
        }
        if (request != null && request.username != null && !target.username.equals(request.username)) {
            throw new IllegalArgumentException("用户名不可修改");
        }
        Set<Long> roots = validate(request, actor, false);
        String hash = request.password == null || request.password.isEmpty() ? null : PasswordUtil.hash(request.password);
        boolean securityChanged = hash != null || target.enabled != request.enabled || !users.departmentIds(id).equals(roots);
        users.updateChild(id, request.displayName.trim(), request.enabled, hash);
        users.replaceDepartments(id, roots);
        if (securityChanged) revokeTree(id);
        return profile(users.findById(id));
    }

    @Transactional
    public void changePassword(String username, String oldPassword, String newPassword) {
        reserveWriter();
        DataScope.Context actor = actor();
        if (!actor.username.equals(username)) throw new ForbiddenException("账号身份不匹配");
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 256) {
            throw new IllegalArgumentException("新密码长度须为6至256位");
        }
        User user = users.findById(actor.userId);
        if (oldPassword == null || !PasswordUtil.verify(oldPassword, user.passwordHash)) {
            throw new IllegalArgumentException("旧密码错误");
        }
        users.updatePassword(username, PasswordUtil.hash(newPassword));
        revokeTree(user.id);
    }

    /** 本人凭旧密码修改登录账号;成功后本人及后代令牌撤销,需用新账号重新登录。 */
    @Transactional
    public Map<String, Object> changeUsername(String username, String oldPassword, String newUsername) {
        reserveWriter();
        DataScope.Context actor = actor();
        if (!actor.username.equals(username)) throw new ForbiddenException("账号身份不匹配");
        String next = newUsername == null ? "" : newUsername.trim();
        if (!next.matches("[A-Za-z0-9_.@-]{1,64}")) {
            throw new IllegalArgumentException("账号须为1至64位字母、数字或 _ . @ -");
        }
        if (next.equals(username)) throw new IllegalArgumentException("新账号不能与当前账号相同");
        if (users.findByUsername(next) != null) throw new IllegalArgumentException("用户名已存在");
        User user = users.findById(actor.userId);
        if (oldPassword == null || !PasswordUtil.verify(oldPassword, user.passwordHash)) {
            throw new IllegalArgumentException("当前密码错误");
        }
        if (users.rename(username, next) != 1) throw new IllegalArgumentException("账号修改失败,请刷新后重试");
        // 旧账号名下的存量令牌也要撤销:revokeTree 在改名后取到的是新账号名
        revokeTree(user.id, username);
        return profile(users.findById(user.id));
    }

    private void revokeTree(Long id, String... extraNames) {
        Set<Long> visited = new HashSet<Long>();
        List<Long> pending = new ArrayList<Long>();
        final Set<String> names = new HashSet<String>();
        pending.add(id);
        for (int i = 0; i < pending.size(); i++) {
            Long next = pending.get(i);
            if (!visited.add(next)) continue;
            User user = users.findById(next);
            if (user != null) names.add(user.username);
            for (User child : users.children(next)) pending.add(child.id);
        }
        for (String extra : extraNames) names.add(extra);
        // Rollbacks must not kill valid sessions, and in-flight logins use TokenManager versions.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { tokens.revokeUsers(names); }
            });
        } else tokens.revokeUsers(names);
    }
}
