package com.szjx.personnel.server.util;

/**
 * 日期格式校验工具
 */
public final class Dates {

    private Dates() {
    }

    /** 严格 yyyy-MM-dd 且为真实日历日;空/未填视为合法 */
    public static boolean validOrEmpty(String s) {
        if (s == null || s.trim().isEmpty()) {
            return true;
        }
        String v = s.trim();
        if (!v.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return false;
        }
        try {
            java.time.LocalDate.parse(v);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
