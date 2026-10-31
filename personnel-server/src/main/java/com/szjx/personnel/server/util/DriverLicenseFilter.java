package com.szjx.personnel.server.util;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.model.Personnel;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable registration filters. A registered record does not imply driving eligibility. */
public final class DriverLicenseFilter {
    public static final String HAS_RECORD_SQL = "EXISTS (SELECT 1 FROM driver_license dl_has WHERE dl_has.personnel_id=p.id)";

    private final String registered;
    private final String type;
    private final String status;

    private DriverLicenseFilter(String registered, String type, String status) {
        this.registered = registered;
        this.type = type;
        this.status = status;
    }

    public static DriverLicenseFilter fromQuery(Map<String, String> query) {
        return query == null ? of(null, null, null)
                : of(query.get("driverLicense"), query.get("driverLicenseType"), query.get("driverLicenseStatus"));
    }

    public static DriverLicenseFilter of(String registered, String type, String status) {
        String value = trim(registered);
        if (!value.isEmpty() && !"yes".equals(value) && !"no".equals(value)) {
            throw new IllegalArgumentException("driverLicense 仅支持空值、yes 或 no");
        }
        return new DriverLicenseFilter(value, trim(type), trim(status));
    }

    public boolean isEmpty() {
        return registered.isEmpty() && type.isEmpty() && status.isEmpty();
    }

    /** Append only fixed SQL fragments against the personnel alias p; values are always bound. */
    public void appendTo(StringBuilder sql, List<Object> args) {
        if ("yes".equals(registered)) sql.append(" AND ").append(HAS_RECORD_SQL);
        if ("no".equals(registered)) sql.append(" AND NOT ").append(HAS_RECORD_SQL);
        if (!type.isEmpty() || !status.isEmpty()) {
            sql.append(" AND EXISTS (SELECT 1 FROM driver_license dl_filter WHERE dl_filter.personnel_id=p.id");
            if (!type.isEmpty()) {
                sql.append(" AND dl_filter.license_type=?");
                args.add(type);
            }
            if (!status.isEmpty()) {
                sql.append(" AND dl_filter.license_status=?");
                args.add(status);
            }
            sql.append(')');
        }
    }

    /** Narrow an already selected list in one read, preserving its order and never expanding its scope. */
    public List<Personnel> retainMatching(JdbcTemplate jdbc, List<Personnel> candidates) {
        if (isEmpty() || candidates.isEmpty()) return candidates;
        StringBuilder sql = new StringBuilder("SELECT p.id FROM personnel p WHERE ")
                .append(DataScope.departmentSql("p.department_id"));
        List<Object> args = new ArrayList<Object>();
        appendTo(sql, args);
        Set<Long> ids = new HashSet<Long>(jdbc.queryForList(sql.toString(), Long.class, args.toArray()));
        List<Personnel> result = new ArrayList<Personnel>();
        for (Personnel person : candidates) {
            if (ids.contains(person.getId())) result.add(person);
        }
        return result;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
