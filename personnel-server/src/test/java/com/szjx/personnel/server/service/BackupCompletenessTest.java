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
            "organization", "organization_member", "organization_column", "transfer_record", "makeup_record",
            "message", "approval_request"
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
        assertEquals(17, BackupService.MODULE_TABLES.size(), "消息与审批模块加入后应为17个");
        assertEquals(17, BackupService.allModuleKeys().size());
        assertTrue(BackupService.allModuleKeys().contains("message"), "消息与审批模块未登记");
        assertEquals("annual-appraisal", BackupService.allModuleKeys().get(16), "最后一个模块应为年度考评");
    }

    /** 机制化守护:从 SchemaInitializer 源码提取全部建表,与备份 ALL_TABLES 比对差集为空——新增表漏登记将直接测试失败 */
    @Test
    public void schemaInitializerTablesAllRegistered() throws Exception {
        java.nio.file.Path src = java.nio.file.Paths.get(
                "src/main/java/com/szjx/personnel/server/config/SchemaInitializer.java");
        assertTrue(java.nio.file.Files.exists(src), "未找到 SchemaInitializer 源码(工作目录应为模块根): " + src.toAbsolutePath());
        String content = new String(java.nio.file.Files.readAllBytes(src), java.nio.charset.StandardCharsets.UTF_8);
        java.util.Set<String> created = new java.util.TreeSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("CREATE TABLE IF NOT EXISTS ([a-z_]+)").matcher(content);
        while (m.find()) { created.add(m.group(1)); }
        assertTrue(created.size() >= 40, "源码建表提取异常,仅提取到 " + created.size() + " 张表");
        // account_department 走生成/上传/恢复三处独立的动态链路(BackupService L124 表存在才导出),
        // 不经 MODULE_TABLES 静态登记——历史合理设计,此处显式豁免;其余新表漏登记仍将直接失败
        created.remove("account_department");
        java.util.Set<String> registered = new java.util.TreeSet<>(Arrays.asList(ALL_TABLES));
        java.util.Set<String> missing = new java.util.TreeSet<>(created);
        missing.removeAll(registered);
        assertTrue(missing.isEmpty(),
                "以下表已在 SchemaInitializer 建表但未登记备份清单(请同步 BackupService.MODULE_TABLES 与本类 ALL_TABLES): " + missing);
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
