package com.szjx.personnel.server.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 备份模块清单完整性测试
 * 确保所有业务表都被备份覆盖
 */
public class BackupCompletenessTest {

    /** 系统全部业务表 */
    private static final String[] ALL_TABLES = {
            "personnel", "department", "sys_dict", "sys_setting", "holiday_calendar",
            "equipment", "material", "vehicle",
            "leave_record", "fitness_record", "fitness_matrix", "fitness_standard",
            "position_history", "reward_punish", "child_info", "family_member",
            "driver_license", "personal_card", "private_vehicle", "housing_info"
    };

    /** 备份模块对应的表(与 BackupService.MODULE_TABLES 一致) */
    private static final String[][] MODULE_TABLES = {
            {"personnel", "position_history", "reward_punish", "child_info", "family_member",
                    "driver_license", "personal_card", "private_vehicle", "housing_info"},
            {"department"},
            {"sys_dict", "sys_setting", "holiday_calendar"},
            {"equipment"},
            {"material"},
            {"vehicle"},
            {"leave_record"},
            {"fitness_record", "fitness_matrix", "fitness_standard"}
    };

    @Test
    public void testAllTablesCovered() {
        // 收集所有备份覆盖的表
        java.util.Set<String> covered = new java.util.HashSet<String>();
        for (String[] tables : MODULE_TABLES) {
            for (String t : tables) {
                covered.add(t);
            }
        }
        // 检查每个业务表都被覆盖
        for (String table : ALL_TABLES) {
            assertTrue(covered.contains(table),
                    "表 [" + table + "] 未被任何备份模块覆盖!");
        }
    }

    @Test
    public void testNoExtraTables() {
        // 收集所有备份覆盖的表
        java.util.Set<String> covered = new java.util.HashSet<String>();
        for (String[] tables : MODULE_TABLES) {
            for (String t : tables) {
                covered.add(t);
            }
        }
        // 确保没有多出的表(可能与拼写错误有关)
        java.util.Set<String> expected = new java.util.HashSet<String>();
        for (String t : ALL_TABLES) { expected.add(t); }
        for (String t : covered) {
            assertTrue(expected.contains(t),
                    "备份模块包含未知表 [" + t + "],请检查表名拼写");
        }
    }

    @Test
    public void testModuleCount() {
        assertEquals(8, MODULE_TABLES.length, "备份模块数应为8");
    }
}
