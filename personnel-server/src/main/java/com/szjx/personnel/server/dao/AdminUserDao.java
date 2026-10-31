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

    /** Internal identity, never serialize this object in an API response. */
    public static final class User {
        public final long id;
        public final String username;
        public final String displayName;
        public final String passwordHash;
        public final Long parentId;
        public final boolean enabled;

        private User(java.sql.ResultSet rs) throws java.sql.SQLException {
            id = rs.getLong("id");
            username = rs.getString("username");
            displayName = rs.getString("display_name");
            passwordHash = rs.getString("password_hash");
            long parent = rs.getLong("parent_id");
            parentId = rs.wasNull() ? null : parent;
            enabled = rs.getInt("enabled") == 1;
        }
    }

    private static final org.springframework.jdbc.core.RowMapper<User> USER_MAPPER = (rs, row) -> new User(rs);
    private static final String USER_COLUMNS = "id,username,display_name,password_hash,parent_id,enabled";

    public User findByUsername(String username) {
        List<User> users = jdbc.query("SELECT " + USER_COLUMNS + " FROM admin_user WHERE username=?", USER_MAPPER, username);
        return users.isEmpty() ? null : users.get(0);
    }

    public User findById(Long id) {
        List<User> users = jdbc.query("SELECT " + USER_COLUMNS + " FROM admin_user WHERE id=?", USER_MAPPER, id);
        return users.isEmpty() ? null : users.get(0);
    }

    public List<User> children(Long parentId) {
        return jdbc.query("SELECT " + USER_COLUMNS + " FROM admin_user WHERE parent_id=? ORDER BY id", USER_MAPPER, parentId);
    }

    public java.util.Set<Long> departmentIds(Long userId) {
        return new java.util.LinkedHashSet<Long>(jdbc.queryForList(
                "SELECT department_id FROM account_department WHERE user_id=? ORDER BY department_id", Long.class, userId));
    }

    public long insertChild(Long parentId, String username, String hash, String displayName, boolean enabled) {
        org.springframework.jdbc.support.KeyHolder key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            java.sql.PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO admin_user(parent_id,username,password_hash,display_name,enabled) VALUES(?,?,?,?,?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, parentId);
            statement.setString(2, username);
            statement.setString(3, hash);
            statement.setString(4, displayName);
            statement.setInt(5, enabled ? 1 : 0);
            return statement;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("账号创建失败");
        return key.getKey().longValue();
    }

    public void updateChild(Long id, String displayName, boolean enabled, String hash) {
        if (hash == null) {
            jdbc.update("UPDATE admin_user SET display_name=?,enabled=? WHERE id=?", displayName, enabled ? 1 : 0, id);
        } else {
            jdbc.update("UPDATE admin_user SET display_name=?,enabled=?,password_hash=? WHERE id=?", displayName, enabled ? 1 : 0, hash, id);
        }
    }

    public void replaceDepartments(Long id, java.util.Set<Long> departmentIds) {
        jdbc.update("DELETE FROM account_department WHERE user_id=?", id);
        for (Long departmentId : departmentIds) {
            jdbc.update("INSERT INTO account_department(user_id,department_id) VALUES(?,?)", id, departmentId);
        }
    }

    /** Acquire SQLite's writer reservation before any authorization read in a mutation. */
    public void lockAccount(Long id) {
        jdbc.update("UPDATE admin_user SET id=id WHERE id=?", id);
    }

    public List<Map<String, Object>> findAll() {
        return jdbc.queryForList("SELECT id, username, display_name FROM admin_user ORDER BY id");
    }
}
