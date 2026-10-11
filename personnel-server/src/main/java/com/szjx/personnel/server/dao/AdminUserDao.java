package com.szjx.personnel.server.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 管理账号数据访问
 */
@Repository
public class AdminUserDao {

    private final JdbcTemplate jdbc;

    public AdminUserDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String findPasswordHash(String username) {
        List<String> list = jdbc.queryForList(
                "SELECT password_hash FROM admin_user WHERE username=?",
                String.class, username);
        return list.isEmpty() ? null : list.get(0);
    }

    public String findDisplayName(String username) {
        List<String> list = jdbc.queryForList(
                "SELECT display_name FROM admin_user WHERE username=?",
                String.class, username);
        return list.isEmpty() ? "" : list.get(0);
    }

    public void updatePassword(String username, String passwordHash) {
        jdbc.update("UPDATE admin_user SET password_hash=? WHERE username=?",
                passwordHash, username);
    }

    /** 改管理员账号名;返回受影响行数(0 = 原账号不存在,可能已改过名) */
    public int rename(String from, String to) {
        return jdbc.update("UPDATE admin_user SET username=? WHERE username=?", to, from);
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM admin_user", Long.class);
        return n == null ? 0 : n;
    }

    public void insertAdmin(String username, String passwordHash, String displayName) {
        jdbc.update("INSERT INTO admin_user(username, password_hash, display_name) VALUES(?,?,?)",
                username, passwordHash, displayName);
    }

    public List<Map<String, Object>> findAll() {
        return jdbc.queryForList("SELECT id, username, display_name FROM admin_user ORDER BY id");
    }
}
