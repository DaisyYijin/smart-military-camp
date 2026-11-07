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

    public List<Map<String, Object>> query(String keyword, String action, String user, String startDate, String endDate, int page, int size) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, username, action, detail, ip, created_at FROM op_log");
        List<Object> args = new ArrayList<Object>();
        appendWhere(sql, args, keyword, action, user, startDate, endDate);
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public long count(String keyword, String action, String user, String startDate, String endDate) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM op_log");
        List<Object> args = new ArrayList<Object>();
        appendWhere(sql, args, keyword, action, user, startDate, endDate);
        Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    /** 组合筛选:关键字(用户/操作/详情/IP 模糊) AND 操作类型 AND 用户 AND 时间范围。 */
    private void appendWhere(StringBuilder sql, List<Object> args,
                             String keyword, String action, String user, String startDate, String endDate) {
        List<String> conds = new ArrayList<String>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String like = "%" + keyword.trim() + "%";
            conds.add("(username LIKE ? OR action LIKE ? OR detail LIKE ? OR ip LIKE ?)");
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        if (action != null && !action.trim().isEmpty()) {
            conds.add("action LIKE ?");
            args.add("%" + action.trim() + "%");
        }
        if (user != null && !user.trim().isEmpty()) {
            conds.add("username LIKE ?");
            args.add("%" + user.trim() + "%");
        }
        if (startDate != null && !startDate.trim().isEmpty()) {
            conds.add("created_at >= ?");
            args.add(startDate.trim() + " 00:00:00");
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            conds.add("created_at <= ?");
            args.add(endDate.trim() + " 23:59:59");
        }
        if (!conds.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conds));
        }
    }
}
