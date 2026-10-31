package com.szjx.personnel.server.util;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.model.Personnel;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 组合式人员列表筛选(驾驶证 + 实际在岗)，只委托两个子筛选，不重复造轮子；语义只读，仅收窄不扩大。 */
public final class PersonnelExtraFilters {
    private final DriverLicenseFilter driver;
    private final ActualDutyFilter duty;

    private PersonnelExtraFilters(DriverLicenseFilter driver, ActualDutyFilter duty) {
        this.driver = driver;
        this.duty = duty;
    }

    public static PersonnelExtraFilters of(DriverLicenseFilter driver, ActualDutyFilter duty) {
        return new PersonnelExtraFilters(
                driver == null ? DriverLicenseFilter.of(null, null, null) : driver,
                duty == null ? ActualDutyFilter.of(null, null) : duty);
    }

    public static PersonnelExtraFilters empty() {
        return of(null, null);
    }

    public static PersonnelExtraFilters fromQuery(Map<String, String> query) {
        if (query == null) return empty();
        return of(DriverLicenseFilter.fromQuery(query), ActualDutyFilter.of(query.get("onDuty"), query.get("actualDepartmentId")));
    }

    public boolean isEmpty() {
        return driver.isEmpty() && duty.isEmpty();
    }

    /** Append only fixed SQL fragments against the personnel alias p; values are always bound by the sub-filters. */
    public void appendTo(StringBuilder sql, List<Object> args) {
        driver.appendTo(sql, args);
        duty.appendTo(sql, args);
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
}
