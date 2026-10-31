package com.szjx.personnel.server.util;

import java.util.List;

/**
 * 编制岗位与实际在岗分离的只读筛选。
 * yes(在编在岗)：未标注实际单位(NULL/0)或实际单位等于编制单位；
 * no(借岗)：实际单位>0 且不同于编制单位，编制单位不再计为在岗；
 * actualDepartmentId：按实际单位精确匹配，不展开子树(与图表下钻口径一致)。
 * 语义只读：仅收窄既有权限查询，从不写库、从不扩大范围。
 */
public final class ActualDutyFilter {
    private final String onDuty;
    private final Long actualDepartmentId;

    private ActualDutyFilter(String onDuty, Long actualDepartmentId) {
        this.onDuty = onDuty;
        this.actualDepartmentId = actualDepartmentId;
    }

    public static ActualDutyFilter of(String onDuty, String actualDepartmentId) {
        String presence = trim(onDuty);
        if (!presence.isEmpty() && !"yes".equals(presence) && !"no".equals(presence)) {
            throw new IllegalArgumentException("onDuty 仅支持空值、yes 或 no");
        }
        Long department = null;
        String raw = trim(actualDepartmentId);
        if (!raw.isEmpty()) {
            long parsed;
            try {
                parsed = Long.parseLong(raw);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("actualDepartmentId 必须为正整数");
            }
            if (parsed <= 0) {
                throw new IllegalArgumentException("actualDepartmentId 必须为正整数");
            }
            department = parsed;
        }
        return new ActualDutyFilter(presence, department);
    }

    public boolean isEmpty() {
        return onDuty.isEmpty() && actualDepartmentId == null;
    }

    /** Append only fixed SQL fragments against the personnel alias p; the department value is always bound. */
    public void appendTo(StringBuilder sql, List<Object> args) {
        if ("yes".equals(onDuty)) {
            sql.append(" AND (p.actual_department_id IS NULL OR p.actual_department_id=0 OR p.actual_department_id=p.department_id)");
        }
        if ("no".equals(onDuty)) {
            sql.append(" AND p.actual_department_id>0 AND (p.department_id IS NULL OR p.actual_department_id<>p.department_id)");
        }
        if (actualDepartmentId != null) {
            sql.append(" AND p.actual_department_id=?");
            args.add(actualDepartmentId);
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
