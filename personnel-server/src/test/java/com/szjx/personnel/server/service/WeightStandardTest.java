package com.szjx.personnel.server.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 体重标准(体型标准)交叉表判定测试
 */
public class WeightStandardTest {

    @Test
    public void testAgeInGroup() {
        // 区间格式
        assertTrue(ageInGroup("0-24", 20));
        assertTrue(ageInGroup("0-24", 24));
        assertFalse(ageInGroup("0-24", 25));
        assertTrue(ageInGroup("25-29", 25));
        assertTrue(ageInGroup("25-29", 29));
        assertFalse(ageInGroup("25-29", 30));
        assertTrue(ageInGroup("60-99", 65));
        assertTrue(ageInGroup("60-99", 99));
        assertFalse(ageInGroup("60-99", 59));

        // 含"岁"字
        assertTrue(ageInGroup("24岁以下", 23));
        assertFalse(ageInGroup("24岁以下", 25));
        assertTrue(ageInGroup("60岁以上", 61));
        assertFalse(ageInGroup("60岁以上", 59));

        // ≤/≥ 符号
        assertTrue(ageInGroup("≤24", 20));
        assertFalse(ageInGroup("≤24", 25));
        assertTrue(ageInGroup("≥60", 65));
        assertFalse(ageInGroup("≥60", 59));
    }

    @Test
    public void testWeightRangeLookup() {
        // 模拟交叉表数据: 1.75m 25-29岁 → [58.2, 84.2]
        double[] range = findRange(1.75, 27);
        assertNotNull(range);
        assertEquals(58.2, range[0], 0.1);
        assertEquals(84.2, range[1], 0.1);
    }

    @Test
    public void testBmiRangeConversion() {
        // 验证 BMI 转体重: 1.75m, BMI 19.0~27.5 → 58.2~84.2
        double h = 1.75;
        double loBmi = 19.0, hiBmi = 27.5;
        double loW = Math.round(h * h * loBmi * 10) / 10.0;
        double hiW = Math.round(h * h * hiBmi * 10) / 10.0;
        assertEquals(58.2, loW, 0.1);
        assertEquals(84.2, hiW, 0.1);
    }

    @Test
    public void testHeightMatching() {
        // 身高匹配: 175cm 应匹配 1.75 行
        assertEquals(1.75, matchHeight(175), 0.001);
        // 175.4cm 也应匹配 1.75(最接近)
        assertEquals(1.75, matchHeight(175.4), 0.001);
        // 176cm 应匹配 1.76
        assertEquals(1.76, matchHeight(176), 0.001);
    }

    // === 测试用的模拟数据和方法 ===

    private static boolean ageInGroup(String label, Integer age) {
        if (age == null) { return false; }
        java.util.regex.Matcher mm = java.util.regex.Pattern.compile("[0-9]+").matcher(label);
        java.util.List<Integer> nums = new java.util.ArrayList<Integer>();
        while (mm.find()) { nums.add(Integer.parseInt(mm.group())); }
        if (label.contains("以下") || label.contains("≤") || label.contains("<=")) {
            return nums.isEmpty() || age <= nums.get(0);
        }
        if (label.contains("以上") || label.contains("≥") || label.contains(">=")) {
            return nums.isEmpty() || age >= nums.get(0);
        }
        if (nums.size() >= 2) {
            return age >= nums.get(0) && age <= nums.get(1);
        }
        return false;
    }

    private static double[] findRange(double heightM, int age) {
        // 模拟: BMI 19.0~27.5(25-29岁组)
        double lo = 19.0, hi = 27.5;
        return new double[]{
            Math.round(heightM * heightM * lo * 10) / 10.0,
            Math.round(heightM * heightM * hi * 10) / 10.0
        };
    }

    private static double matchHeight(double heightCm) {
        return Math.round(heightCm) / 100.0;
    }
}
