package com.szjx.personnel.server.service;

import com.szjx.personnel.server.dao.AdminUserDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 本机启动向导和托盘的管理员恢复入口，不通过 HTTP 暴露。 */
@Service
public class LocalAdminService {
    private final AdminUserDao users;
    private final TokenManager tokens;

    public LocalAdminService(AdminUserDao users, TokenManager tokens) {
        this.users = users;
        this.tokens = tokens;
    }

    @Transactional
    public void configureRoot(String from, String to, String password) {
        if (to == null || to.trim().isEmpty() || to.length() > 64) throw new IllegalArgumentException("管理员账号不能为空且不能超过64位");
        if (password != null && (password.length() < 6 || password.length() > 256)) throw new IllegalArgumentException("密码长度须为6至256位");
        AdminUserDao.User user = users.findByUsername(from);
        if (user == null || !Long.valueOf(0).equals(user.parentId)) {
            throw new IllegalArgumentException("本机设置中的管理员账号不存在或不是根管理员，请检查本机配置；子账号须由上级账号管理");
        }
        users.lockAccount(user.id);
        if (from.equals(to) && password == null) return;
        final Set<String> names = new HashSet<String>();
        List<Long> pending = new ArrayList<Long>();
        Set<Long> visited = new HashSet<Long>();
        pending.add(user.id);
        for (int i = 0; i < pending.size(); i++) {
            Long id = pending.get(i);
            if (!visited.add(id)) continue;
            AdminUserDao.User current = users.findById(id);
            if (current != null) names.add(current.username);
            for (AdminUserDao.User child : users.children(id)) pending.add(child.id);
        }
        if (!from.equals(to)) {
            if (users.findByUsername(to) != null) throw new IllegalArgumentException("管理员账号名已被使用");
            if (users.rename(from, to) != 1) throw new IllegalStateException("管理员账号更新失败");
            names.add(to);
        }
        if (password != null) users.updatePassword(to, PasswordUtil.hash(password));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { tokens.revokeUsers(names); }
            });
        } else tokens.revokeUsers(names);
    }
}
