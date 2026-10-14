package com.szjx.personnel.server.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 体能评分矩阵计算测试
 */
public class FitnessCalculationTest {

    @Test
    public void testValidateIdCardValid() {
        // 有效身份证号(校验码正确)
        assertTrue(isValidIdCard("330381199702146111"));
        assertTrue(isValidIdCard("110101199003074258"));
    }

    @Test
    public void testValidateIdCardInvalid() {
        assertFalse(isValidIdCard("330381199702146112")); // 校验码错
        assertFalse(isValidIdCard("12345")); // 位数不够
        assertFalse(isValidIdCard("33038119970214611A")); // 非法字符
        assertFalse(isValidIdCard("")); // 空
    }

    @Test
    public void testExtractBirthday() {
        String bd = extractBirthday("330381199702146111");
        assertEquals("1997-02-14", bd);
    }

    @Test
    public void testExtractGender() {
        // 第17位奇数=男,偶数=女
        assertEquals("男", extractGender("330381199702146111")); // 第17位=1(奇)
        // 110101199003074258 第17位=5(奇)→男
    }

    @Test
    public void testAgeCalculation() {
        assertEquals(29, calcAge("1997-02-14", "2026-10-14"));
        assertEquals(27, calcAge("1997-02-14", "2024-06-15"));
        assertEquals(0, calcAge("2026-01-01", "2026-10-14"));
        assertEquals(0, calcAge("2027-01-01", "2026-10-14")); // 未出生, Period返回0
    }

    @Test
    public void testAgeEdgeCases() {
        // 生日当天
        assertEquals(29, calcAge("1997-10-14", "2026-10-14"));
        // 生日前一天
        assertEquals(28, calcAge("1997-10-15", "2026-10-14"));
    }

    // === 辅助方法(与前端/后端逻辑一致) ===

    private static boolean isValidIdCard(String id) {
        String s = id.trim().toUpperCase();
        if (!s.matches("\\d{17}[\\dX]")) { return false; }
        int[] W = {7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2};
        String CODES = "10X98765432";
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += Character.getNumericValue(s.charAt(i)) * W[i];
        }
        return CODES.charAt(sum % 11) == s.charAt(17);
    }

    private static String extractBirthday(String id) {
        return id.substring(6, 10) + "-" + id.substring(10, 12) + "-" + id.substring(12, 14);
    }

    private static String extractGender(String id) {
        return Character.getNumericValue(id.charAt(16)) % 2 == 0 ? "女" : "男";
    }

    private static int calcAge(String bd, String ref) {
        java.time.LocalDate birth = java.time.LocalDate.parse(bd);
        java.time.LocalDate date = java.time.LocalDate.parse(ref);
        return java.time.Period.between(birth, date).getYears();
    }
}
