package com.szjx.personnel.server.auth;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Request-local effective scope. No context means a trusted internal invocation. */
public final class DataScope {
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<Context>();
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

    private DataScope() { }

    public static final class Context {
        public final Long userId;
        public final String username;
        public final boolean admin;
        /** Expanded effective departments, not merely the configured authorization roots. */
        public final Set<Long> departmentIds;

        public Context(Long userId, String username, boolean admin, Set<Long> departmentIds) {
            this.userId = userId;
            this.username = username;
            this.admin = admin;
            Set<Long> ids = new LinkedHashSet<Long>();
            if (departmentIds != null) {
                for (Long id : departmentIds) {
                    if (id != null && id > 0) ids.add(id);
                }
            }
            this.departmentIds = Collections.unmodifiableSet(ids);
        }
    }

    public static void set(Context context) { if (context == null) clear(); else CURRENT.set(context); }
    public static void clear() { CURRENT.remove(); }
    public static Context get() { return CURRENT.get(); }
    public static boolean restricted() { return get() != null && !get().admin; }
    public static boolean isAdmin() { return !restricted(); }
    public static Long userId() { return get() == null ? null : get().userId; }

    private static String identifier(String value) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("非法 SQL 标识符");
        }
        return value;
    }

    public static String departmentSql(String column) {
        if (!restricted()) return "1=1";
        identifier(column);
        if (get().departmentIds.isEmpty()) return "1=0";
        StringBuilder sql = new StringBuilder(column).append(" IN (");
        for (Long id : get().departmentIds) {
            if (sql.charAt(sql.length() - 1) != '(') sql.append(',');
            sql.append(id.longValue());
        }
        return sql.append(')').toString();
    }

    public static String personSql(String column) {
        if (!restricted()) return "1=1";
        return identifier(column) + " IN (SELECT id FROM personnel WHERE " + departmentSql("department_id") + ")";
    }

    public static boolean allowsDepartment(Long id) {
        return !restricted() || (id != null && id > 0 && get().departmentIds.contains(id));
    }

    public static void requireDepartment(Long id) {
        if (!allowsDepartment(id)) throw new ForbiddenException("无权访问该单位");
    }

    public static void requirePerson(JdbcTemplate jdbc, Long id) {
        if (!restricted()) return;
        if (id == null || id <= 0) throw new ForbiddenException("无权访问未关联人员的数据");
        List<Long> departments = jdbc.queryForList("SELECT department_id FROM personnel WHERE id=?", Long.class, id);
        if (departments.size() != 1) throw new ForbiddenException("无权访问该人员");
        requireDepartment(departments.get(0));
    }

    public static void requireRecord(JdbcTemplate jdbc, String table, String ownerColumn, Long id) {
        if (!restricted()) return;
        if (id == null || id <= 0) throw new ForbiddenException("无权访问该记录");
        List<Long> owners = jdbc.queryForList("SELECT " + identifier(ownerColumn) + " FROM " + identifier(table) + " WHERE id=?", Long.class, id);
        if (owners.size() != 1) throw new ForbiddenException("无权访问该记录");
        requirePerson(jdbc, owners.get(0));
    }

    public static void requireAdmin() {
        if (restricted()) throw new ForbiddenException("仅全局管理员可执行此操作");
    }
}
