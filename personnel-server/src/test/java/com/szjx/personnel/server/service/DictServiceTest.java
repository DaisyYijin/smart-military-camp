package com.szjx.personnel.server.service;

import com.szjx.personnel.server.config.GlobalExceptionHandler;
import com.szjx.personnel.server.config.SchemaInitializer;
import com.szjx.personnel.server.controller.DictController;
import com.szjx.personnel.server.dao.AdminUserDao;
import com.szjx.personnel.server.dao.DictDao;
import com.szjx.personnel.server.model.DictItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DictServiceTest {

    @TempDir Path temp;
    JdbcTemplate jdbc;
    DictDao dao;
    DictService service;

    @BeforeEach void setup() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setUrl("jdbc:sqlite:" + temp.resolve("dict.sqlite").toAbsolutePath());
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE sys_dict(id INTEGER PRIMARY KEY AUTOINCREMENT, dict_type TEXT, dict_label TEXT, " +
                "sort INTEGER DEFAULT 0, enabled INTEGER DEFAULT 1, remark TEXT DEFAULT '', parent_id INTEGER DEFAULT 0, " +
                "created_at TEXT DEFAULT '', updated_at TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE material(id INTEGER PRIMARY KEY AUTOINCREMENT, location TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE equipment(id INTEGER PRIMARY KEY AUTOINCREMENT, brand TEXT DEFAULT '')");
        dao = new DictDao(jdbc);
        service = new DictService(dao, jdbc);
    }

    @Test void listTypesOutputsBusinessGroupsWithFallbackForUnknown() {
        jdbc.update("INSERT INTO sys_dict(dict_type, dict_label, sort) VALUES('blood_type','A',1)");
        jdbc.update("INSERT INTO sys_dict(dict_type, dict_label, sort) VALUES('card_status','正常',1)");
        jdbc.update("INSERT INTO sys_dict(dict_type, dict_label, sort) VALUES('region_province','北京',1)");
        jdbc.update("INSERT INTO sys_dict(dict_type, dict_label, sort) VALUES('custom_domain','x',1)");
        java.util.List<java.util.Map<String, Object>> types = service.listTypes();
        java.util.Map<String, String> groupOf = new java.util.LinkedHashMap<String, String>();
        java.util.List<String> order = new java.util.ArrayList<String>();
        for (java.util.Map<String, Object> t : types) {
            groupOf.put(String.valueOf(t.get("type")), String.valueOf(t.get("group")));
            order.add(String.valueOf(t.get("type")));
        }
        assertEquals("人员档案", groupOf.get("blood_type"));
        assertEquals("证件与卡", groupOf.get("card_status"));
        assertEquals("行政区划", groupOf.get("region_province"));
        assertEquals("其他", groupOf.get("custom_domain"), "未映射的自建类型应落入其他组");
        assertTrue(order.indexOf("blood_type") < order.indexOf("region_province"), "人员档案组应排在行政区划组之前");
    }

    private long addLocation(String label) {
        jdbc.update("INSERT INTO sys_dict(dict_type, dict_label) VALUES(?, ?)", "material_location", label);
        return jdbc.queryForObject("SELECT id FROM sys_dict WHERE dict_type=? AND dict_label=?", Long.class,
                "material_location", label);
    }

    private long addDict(String type, String label, int sort, long parentId) {
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,sort,parent_id) VALUES(?,?,?,?)",
                type, label, sort, parentId);
        return jdbc.queryForObject("SELECT id FROM sys_dict WHERE dict_type=? AND dict_label=? AND parent_id=?",
                Long.class, type, label, parentId);
    }

    private int sortOf(long id) {
        return jdbc.queryForObject("SELECT sort FROM sys_dict WHERE id=?", Integer.class, id);
    }

    @Test void renamingEquipmentBrandUpdatesEquipmentRecords() {
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label) VALUES('equipment_brand','旧品牌')");
        Long id = jdbc.queryForObject("SELECT id FROM sys_dict WHERE dict_type='equipment_brand'", Long.class);
        jdbc.update("INSERT INTO equipment(brand) VALUES('旧品牌')");
        DictItem edit = new DictItem();
        edit.setDictType("equipment_brand");
        edit.setDictLabel("新品牌");
        service.update(id, edit);
        assertEquals("新品牌", jdbc.queryForObject("SELECT brand FROM equipment", String.class));
        assertEquals("装备品牌", DictService.TYPE_NAMES.get("equipment_brand"));
    }

    @Test void deletingEquipmentBrandClearsEquipmentReference() {
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label) VALUES('equipment_brand','待删除品牌')");
        Long id = jdbc.queryForObject("SELECT id FROM sys_dict WHERE dict_type='equipment_brand'", Long.class);
        jdbc.update("INSERT INTO equipment(brand) VALUES('待删除品牌')");
        assertEquals(1, service.delete(id));
        assertEquals("", jdbc.queryForObject("SELECT brand FROM equipment", String.class));
    }

    @Test void renamingMaterialLocationUpdatesMaterialRecords() {
        long id = addLocation("旧库房");
        jdbc.update("INSERT INTO material(location) VALUES(?)", "旧库房");

        DictItem edit = new DictItem();
        edit.setDictType("material_location");
        edit.setDictLabel("新库房");
        service.update(id, edit);

        assertEquals("新库房", jdbc.queryForObject("SELECT location FROM material", String.class));
    }

    @Test void deletingMaterialLocationClearsMaterialRecords() {
        long id = addLocation("待清除库房");
        jdbc.update("INSERT INTO material(location) VALUES(?)", "待清除库房");

        int cleared = service.delete(id);

        assertEquals(1, cleared);
        assertEquals("", jdbc.queryForObject("SELECT location FROM material", String.class));
    }

    @Test void newOptionsAppendAfterExistingMaxSortUnlessExplicit() {
        addDict("rank", "甲", 2, 0);
        addDict("rank", "乙", 7, 0);

        DictItem created = new DictItem();
        created.setDictType("rank");
        created.setDictLabel("丙");
        assertEquals(8, service.create(created).getSort().intValue());

        DictItem explicit = new DictItem();
        explicit.setDictType("rank");
        explicit.setDictLabel("丁");
        explicit.setSort(0);
        assertEquals(0, service.create(explicit).getSort().intValue());
    }

    @Test void updateWithoutSortKeepsExistingOrder() {
        long id = addDict("holiday_type", "旧名称", 5, 0);
        DictItem edit = new DictItem();
        edit.setDictLabel("新名称");

        assertEquals(5, service.update(id, edit).getSort().intValue());
    }

    @Test void reorderRenumbersOnlyTheCompleteSiblingGroup() {
        long a = addDict("holiday_type", "甲", 1, 0);
        long b = addDict("holiday_type", "乙", 2, 0);
        long c = addDict("holiday_type", "甲一", 1, a);
        long d = addDict("holiday_type", "甲二", 2, a);
        long e = addDict("holiday_type", "乙一", 3, b);

        service.reorder("holiday_type", Arrays.asList(b, a));
        assertEquals(2, sortOf(a));
        assertEquals(1, sortOf(b));
        assertEquals(1, sortOf(c));
        assertEquals(2, sortOf(d));
        assertEquals(3, sortOf(e));

        service.reorder("holiday_type", Arrays.asList(d, c));
        assertEquals(2, sortOf(c));
        assertEquals(1, sortOf(d));
        assertEquals(2, sortOf(a));
        assertEquals(1, sortOf(b));
    }

    @Test void reorderEndpointPersistsNewSiblingOrder() throws Exception {
        long a = addDict("holiday_type", "甲", 1, 0);
        long b = addDict("holiday_type", "乙", 2, 0);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DictController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(put("/api/dict/reorder").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictType\":\"holiday_type\",\"ids\":[" + b + "," + a + "]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        assertEquals(2, sortOf(a));
        assertEquals(1, sortOf(b));
    }

    @Test void deletingOptionRenumbersOnlyItsSiblingGroup() {
        long a = addDict("holiday_type", "甲", 1, 0);
        long b = addDict("holiday_type", "乙", 2, 0);
        long c = addDict("holiday_type", "丙", 4, 0);
        long childB1 = addDict("holiday_type", "乙一", 9, b);
        long childB2 = addDict("holiday_type", "乙二", 10, b);

        service.delete(a);
        assertEquals(1, sortOf(b));
        assertEquals(2, sortOf(c));
        assertEquals(9, sortOf(childB1));
        assertEquals(10, sortOf(childB2));

        service.delete(childB1);
        assertEquals(1, sortOf(childB2));
        assertEquals(1, sortOf(b));
        assertEquals(2, sortOf(c));
    }

    @Test void educationMigrationRemovesUnusedOldOptionAndOrdersNewLevels() {
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY, education TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE sys_setting(skey TEXT PRIMARY KEY, svalue TEXT DEFAULT '')");
        addDict("education", "高中及以下", 5, 0);
        String[] labels = {"博士", "硕士", "本科", "大专", "职高", "中专", "初中", "小学", "其他"};
        for (int i = 0; i < labels.length; i++) addDict("education", labels[i], 20 + i, 0);
        Object dictSeed = ReflectionTestUtils.getField(new SchemaInitializer(jdbc, new AdminUserDao(jdbc)), "dictSeed");

        ReflectionTestUtils.invokeMethod(dictSeed, "migrateEducationOptionsOnce");

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dict WHERE dict_type='education' AND dict_label='高中及以下'", Integer.class));
        assertEquals(Arrays.asList(labels), jdbc.queryForList(
                "SELECT dict_label FROM sys_dict WHERE dict_type='education' ORDER BY sort,id", String.class));
        assertEquals("1", jdbc.queryForObject(
                "SELECT svalue FROM sys_setting WHERE skey='education_options_v1'", String.class));
    }

    @Test void educationMigrationDisablesOldOptionWhenPersonnelStillUseIt() {
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY, education TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE sys_setting(skey TEXT PRIMARY KEY, svalue TEXT DEFAULT '')");
        long old = addDict("education", "高中及以下", 5, 0);
        jdbc.update("INSERT INTO personnel(id,education) VALUES(1,'高中及以下')");
        Object dictSeed = ReflectionTestUtils.getField(new SchemaInitializer(jdbc, new AdminUserDao(jdbc)), "dictSeed");

        ReflectionTestUtils.invokeMethod(dictSeed, "migrateEducationOptionsOnce");

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dict WHERE id=?", Integer.class, old));
        assertEquals(0, jdbc.queryForObject(
                "SELECT enabled FROM sys_dict WHERE id=?", Integer.class, old));
        assertEquals("高中及以下", jdbc.queryForObject(
                "SELECT education FROM personnel WHERE id=1", String.class));
    }

    @Test void oneTimeMigrationCompactsExistingSortGaps() {
        jdbc.execute("CREATE TABLE sys_setting(skey TEXT PRIMARY KEY, svalue TEXT DEFAULT '')");
        long a = addDict("holiday_type", "甲", 2, 0);
        long b = addDict("holiday_type", "乙", 3, 0);
        long c = addDict("holiday_type", "丙", 4, 0);
        long child1 = addDict("holiday_type", "甲一", 7, a);
        long child2 = addDict("holiday_type", "甲二", 9, a);
        Object dictSeed = ReflectionTestUtils.getField(new SchemaInitializer(jdbc, new AdminUserDao(jdbc)), "dictSeed");

        ReflectionTestUtils.invokeMethod(dictSeed, "normalizeDictSortsOnce");
        assertEquals(1, sortOf(a));
        assertEquals(2, sortOf(b));
        assertEquals(3, sortOf(c));
        assertEquals(1, sortOf(child1));
        assertEquals(2, sortOf(child2));

        jdbc.update("UPDATE sys_dict SET sort=9 WHERE id=?", a);
        ReflectionTestUtils.invokeMethod(dictSeed, "normalizeDictSortsOnce");
        assertEquals(9, sortOf(a));
    }

    @Test void deletingParentWithChildrenKeepsSiblingOrder() {
        long a = addDict("holiday_type", "甲", 1, 0);
        long b = addDict("holiday_type", "乙", 5, 0);
        addDict("holiday_type", "乙一", 1, b);

        assertThrows(IllegalArgumentException.class, () -> service.delete(b));
        assertEquals(1, sortOf(a));
        assertEquals(5, sortOf(b));
    }

    @Test void reorderRejectsIncompleteMixedForeignAndDuplicateIds() {
        long a = addDict("holiday_type", "甲", 1, 0);
        long b = addDict("holiday_type", "乙", 2, 0);
        long c = addDict("holiday_type", "丙", 3, 0);
        long child = addDict("holiday_type", "甲一", 1, a);
        long foreign = addDict("vehicle_brand", "品牌", 1, 0);

        assertThrows(IllegalArgumentException.class,
                () -> service.reorder("holiday_type", Arrays.asList(a, b)));
        assertThrows(IllegalArgumentException.class,
                () -> service.reorder("holiday_type", Arrays.asList(a, child)));
        assertThrows(IllegalArgumentException.class,
                () -> service.reorder("holiday_type", Arrays.asList(a, b, c, foreign)));
        assertThrows(IllegalArgumentException.class,
                () -> service.reorder("holiday_type", Arrays.asList(a, a, b, c)));

        assertEquals(1, sortOf(a));
        assertEquals(2, sortOf(b));
        assertEquals(3, sortOf(c));
        assertEquals(1, sortOf(child));
        assertEquals(1, sortOf(foreign));
    }
}
