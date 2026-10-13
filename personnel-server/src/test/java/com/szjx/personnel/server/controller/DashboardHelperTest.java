package com.szjx.personnel.server.controller;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 仪表盘事件助手方法单元测试
 */
public class DashboardHelperTest {

    @Test
    public void anniversaryDateWithin7Days() {
        LocalDate today = LocalDate.of(2026, 10, 13);
        assertEquals("2026-10-15", DashboardController.anniversaryDate("2001-10-15", today, 7));
        assertEquals("2026-10-20", DashboardController.anniversaryDate("1999-10-20", today, 7));
        assertEquals("2026-10-13", DashboardController.anniversaryDate("2016-10-13", today, 7)); // 当天
    }

    @Test
    public void anniversaryDateOutOfRange() {
        LocalDate today = LocalDate.of(2026, 10, 13);
        assertNull(DashboardController.anniversaryDate("2001-10-21", today, 7));   // 超过7天
        assertNull(DashboardController.anniversaryDate("bad-date", today, 7));     // 非法格式
        assertNull(DashboardController.anniversaryDate("", today, 7));
    }

    @Test
    public void anniversaryDateYearWrap() {
        LocalDate today = LocalDate.of(2026, 12, 30);
        // 生日在1月2日 → 跨年落到明年
        assertEquals("2027-01-02", DashboardController.anniversaryDate("2000-01-02", today, 7));
    }

    @Test
    public void anniversaryYears() {
        assertEquals(10, DashboardController.anniversaryYears("2016-10-15", "2026-10-15"));
        assertEquals(0, DashboardController.anniversaryYears("bad", "2026-10-15"));
    }

    @Test
    public void daysBetween() {
        assertEquals(1, DashboardController.daysBetween("2026-10-13", "2026-10-13"));
        assertEquals(6, DashboardController.daysBetween("2026-10-13", "2026-10-18"));
        assertEquals(0, DashboardController.daysBetween("bad", "2026-10-18"));
    }

    @Test
    public void numStr() {
        assertEquals("17", DashboardController.numStr(17.0));
        assertEquals("17.5", DashboardController.numStr(17.5));
        assertEquals("abc", DashboardController.numStr("abc"));
    }

    @Test
    public void fmtDate() {
        assertEquals("10月15日", DashboardController.fmtDate("2026-10-15"));
        assertEquals("bad", DashboardController.fmtDate("bad"));
    }

}
