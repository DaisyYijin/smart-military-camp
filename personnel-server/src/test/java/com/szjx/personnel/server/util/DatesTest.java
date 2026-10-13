package com.szjx.personnel.server.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 日期校验工具单元测试
 */
public class DatesTest {

    @Test
    public void validDates() {
        assertTrue(Dates.validOrEmpty("2026-10-13"));
        assertTrue(Dates.validOrEmpty("2024-02-29"));      // 闰年
        assertTrue(Dates.validOrEmpty(""));
        assertTrue(Dates.validOrEmpty(null));
        assertTrue(Dates.validOrEmpty("  2026-01-01  "));  // 容忍首尾空格
    }

    @Test
    public void invalidDates() {
        assertFalse(Dates.validOrEmpty("2023-02-29"));     // 平年2月29
        assertFalse(Dates.validOrEmpty("2026-13-01"));     // 月越界
        assertFalse(Dates.validOrEmpty("2026-10-32"));     // 日越界
        assertFalse(Dates.validOrEmpty("2026/10/13"));     // 分隔符
        assertFalse(Dates.validOrEmpty("2026-1-3"));       // 位数
        assertFalse(Dates.validOrEmpty("20261013"));
    }
}
