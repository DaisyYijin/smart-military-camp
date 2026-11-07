package com.szjx.personnel.server.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 检查生产模块清单,避免测试维护另一份清单而漏掉实际备份缺项。 */
public class BackupCompletenessTest {

    /** 静态模块直接包含的业务表;account_department/旧fitness_standard按实际schema追加。 */
    private static final String[] ALL_TABLES = {
            "personnel", "department", "sys_dict", "sys_setting", "holiday_calendar",
            "admin_user", "op_log", "dash_setting",
            "equipment", "material", "item_loan", "vehicle", "vehicle_fault_history",
            "leave_record", "leave_plan", "fitness_record", "fitness_matrix",
            "military_assessment", "mil_scheme", "mil_scheme_item", "phone_device",
            "position_history", "reward_punish", "child_info", "family_member",
            "driver_license", "personal_card", "private_vehicle", "housing_info", "delete_undo",
            "double_excellence_record", "annual_appraisal_record", "absence_record",
            "organization", "organization_member", "organization_column", "transfer_record"
    };

    private Set<String> coveredTables() {
        Set<String> covered = new HashSet<>();
        for (String[] tables : BackupService.MODULE_TABLES.values()) {
            covered.addAll(Arrays.asList(tables));
        }
        return covered;
    }

    @Test
    public void testAllTablesCovered() {
        Set<String> covered = coveredTables();
        for (String table : ALL_TABLES) {
            assertTrue(covered.contains(table), "表 [" + table + "] 未被任何备份模块覆盖!");
        }
    }

    @Test
    public void testNoExtraTables() {
        Set<String> expected = new HashSet<>(Arrays.asList(ALL_TABLES));
        for (String table : coveredTables()) {
            assertTrue(expected.contains(table), "备份模块包含未知表 [" + table + "],请检查表名拼写");
        }
    }

    @Test
    public void testModuleCount() {
        assertEquals(16, BackupService.MODULE_TABLES.size(), "双争/年度/在外/组织建设模块加入后应为16个");
        assertEquals(16, BackupService.allModuleKeys().size());
        assertEquals("double-excellence", BackupService.allModuleKeys().get(14));
        assertEquals("annual-appraisal", BackupService.allModuleKeys().get(15));
    }

    @Test
    public void doubleExcellenceHasOneIndependentModuleAndChineseLabel() {
        assertArrayEquals(new String[]{"double_excellence_record"}, BackupService.MODULE_TABLES.get("double-excellence"));
        for (Map.Entry<String, String[]> module : BackupService.MODULE_TABLES.entrySet()) {
            if (!"double-excellence".equals(module.getKey())) {
                assertFalse(Arrays.asList(module.getValue()).contains("double_excellence_record"), module.getKey());
            }
        }
        com.szjx.personnel.server.controller.BackupController controller = new com.szjx.personnel.server.controller.BackupController(
                org.mockito.Mockito.mock(BackupService.class));
        java.util.List<?> modules = (java.util.List<?>) controller.modules().getData();
        Map<?, ?> module = modules.stream().map(value -> (Map<?, ?>) value)
                .filter(value -> "double-excellence".equals(value.get("key"))).findFirst().get();
        assertEquals("双争评比", module.get("label"));
        assertEquals(Boolean.FALSE, module.get("includesPhotos"));
    }

    @Test
    public void itemLoansHaveExactlyOneIndependentBackupModule() {
        assertArrayEquals(new String[]{"item_loan"}, BackupService.MODULE_TABLES.get("item-loans"));
        for (Map.Entry<String, String[]> module : BackupService.MODULE_TABLES.entrySet()) {
            if (!"item-loans".equals(module.getKey())) {
                assertFalse(Arrays.asList(module.getValue()).contains("item_loan"), module.getKey());
            }
        }
    }
}
