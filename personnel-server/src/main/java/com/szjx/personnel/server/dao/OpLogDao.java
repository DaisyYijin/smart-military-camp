package com.szjx.personnel.server.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 操作日志数据访问
 */
@Repository
public class OpLogDao {

    private final JdbcTemplate jdbc;

    public OpLogDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String username, String action, String detail, String ip) {
        jdbc.update("INSERT INTO op_log(username, action, detail, ip) VALUES(?,?,?,?)",
                username == null ? "" : username,
                action == null ? "" : action,
                detail == null ? "" : detail,
                ip == null ? "" : ip);
    }

    public List<Map<String, Object>> query(String keyword, int page, int size) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, username, action, detail, ip, created_at FROM op_log");
        List<Object> args = new ArrayList<Object>();
        appendWhere(sql, args, keyword);
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public long count(String keyword) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM op_log");
        List<Object> args = new ArrayList<Object>();
        appendWhere(sql, args, keyword);
        Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    private void appendWhere(StringBuilder sql, List<Object> args, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return;
        }
        String like = "%" + keyword.trim() + "%";
        sql.append(" WHERE username LIKE ? OR action LIKE ? OR detail LIKE ? OR ip LIKE ?");
        args.add(like);
        args.add(like);
        args.add(like);
        args.add(like);
    }
}
