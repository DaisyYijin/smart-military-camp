package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.config.GlobalExceptionHandler;
import com.szjx.personnel.server.config.SchemaInitializer;
import com.szjx.personnel.server.controller.PersonnelController;
import com.szjx.personnel.server.dao.AdminUserDao;
import com.szjx.personnel.server.dao.DepartmentDao;
import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.model.Personnel;
import com.szjx.personnel.server.model.StatsSummary;
import com.szjx.personnel.server.util.ActualDutyFilter;
import com.szjx.personnel.server.util.DriverLicenseFilter;
import com.szjx.personnel.server.util.PersonnelExtraFilters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 编制岗位与实际在岗分离:筛选口径、交集、scope、控制器绑定、统计树序与增改保留/清空。 */
class ActualDutyFeatureTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;
    PersonnelDao dao;

    static JdbcTemplate database(Path path) {
        JdbcTemplate db = PersonnelIntegrityTest.temporaryDatabase(path);
        PersonnelIntegrityTest.personnelSchema(db);
        // actual_post/actual_post_date 为历史列:正式库由 SchemaInitializer 保留,隔离 schema 按模型生成已不含,补齐使下方旧 INSERT 仍合法
        db.execute("ALTER TABLE personnel ADD COLUMN actual_post TEXT DEFAULT ''");
        db.execute("ALTER TABLE personnel ADD COLUMN actual_post_date TEXT DEFAULT ''");
        db.update("UPDATE department SET name='一连',parent_id=0,sort=1 WHERE id=1");
        db.update("INSERT INTO department(id,name,parent_id,sort) VALUES(2,'一排',1,1),(3,'二连',0,2),(4,'三排',3,1)");
        db.execute("CREATE TABLE driver_license(id INTEGER PRIMARY KEY,personnel_id INTEGER,license_type TEXT,license_no TEXT,vehicle_class TEXT,issue_date TEXT,expiry_date TEXT,license_status TEXT,review_date TEXT,issuing_auth TEXT)");
        db.execute("CREATE TABLE equipment(id INTEGER PRIMARY KEY,keeper_id INTEGER,status TEXT,category TEXT)");
        db.execute("CREATE TABLE material(id INTEGER PRIMARY KEY,keeper_id INTEGER,quantity REAL,min_quantity REAL,category TEXT)");
        db.execute("CREATE TABLE vehicle(id INTEGER PRIMARY KEY,driver_id INTEGER,status TEXT,type TEXT,brand TEXT)");
        db.execute("CREATE TABLE leave_record(id INTEGER PRIMARY KEY,personnel_id INTEGER,start_date TEXT,end_date TEXT,leave_type TEXT)");
        db.execute("CREATE TABLE fitness_record(id INTEGER PRIMARY KEY,personnel_id INTEGER,test_date TEXT,grade TEXT)");
        db.execute("CREATE TABLE double_excellence_record(id INTEGER PRIMARY KEY,personnel_id INTEGER,year INTEGER,quarter INTEGER,rating TEXT DEFAULT '',rating_date TEXT DEFAULT '')");
        db.execute("CREATE TABLE annual_appraisal_record(id INTEGER PRIMARY KEY,personnel_id INTEGER,year INTEGER,rating TEXT DEFAULT '',rating_date TEXT DEFAULT '')");
        // 编制/实际组合:1/0 未分离、2/NULL 未分离、1/1 同单位=在编、2/3 与 3/2 与 1/4、3/0 未分离
        db.update("INSERT INTO personnel(id,name,id_card,department_id,actual_department_id,actual_post,actual_post_date,location_point,gender,status) VALUES(1,'周明','CARD1',1,0,'','','机关','男','在职')");
        db.update("INSERT INTO personnel(id,name,id_card,department_id,actual_department_id,actual_post,actual_post_date,location_point,gender,status) VALUES(2,'李宁','CARD2',2,NULL,'','','营区','男','在职')");
        db.update("INSERT INTO personnel(id,name,id_card,department_id,actual_department_id,actual_post,actual_post_date,location_point,gender,status) VALUES(3,'陈晓','CARD3',1,1,'代理连长','2026-01-10','机关','男','在职')");
        db.update("INSERT INTO personnel(id,name,id_card,department_id,actual_department_id,actual_post,actual_post_date,location_point,gender,status) VALUES(4,'王强','CARD4',2,3,'借调参谋','2026-02-01','营区','男','在职')");
        db.update("INSERT INTO personnel(id,name,id_card,department_id,actual_department_id,actual_post,actual_post_date,location_point,gender,status) VALUES(5,'刘静','CARD5',3,2,'帮助工作','2026-02-15','机关','男','在职')");
        db.update("INSERT INTO personnel(id,name,id_card,department_id,actual_department_id,actual_post,actual_post_date,location_point,gender,status) VALUES(6,'段岩','CARD6',1,4,'代职排长','2026-03-01','营区','男','在职')");
        db.update("INSERT INTO personnel(id,name,id_card,department_id,actual_department_id,actual_post,actual_post_date,location_point,gender,status) VALUES(7,'赵林','CARD7',3,0,'','','机关','男','在职')");
        db.update("INSERT INTO personnel(id,name,id_card,department_id,actual_department_id,actual_post,actual_post_date,location_point,gender,status) VALUES(8,'孙平','CARD8',1,4,'借训驾驶员','2026-03-10','营区','男','在职')");
        db.update("INSERT INTO driver_license(id,personnel_id,license_type,license_status) VALUES(11,1,'军本','有效')");
        db.update("INSERT INTO driver_license(id,personnel_id,license_type,license_status) VALUES(41,4,'军本','有效')");
        return db;
    }

    static PersonnelService people(JdbcTemplate db) {
        return new PersonnelService(new PersonnelDao(db), mock(PhotoService.class), new DepartmentDao(db), db);
    }

    @BeforeEach void setup() { DataScope.clear(); jdbc = database(temp); dao = new PersonnelDao(jdbc); }
    @AfterEach void clear() { DataScope.clear(); }

    private List<Personnel> query(PersonnelExtraFilters filter, int page, int size, Long department, boolean descendants, String location) {
        return dao.query(null, null, null, department, null, null, null, null, null, null, null, null, null, null, null, null, null, location, null, null, page, size, descendants, filter);
    }

    private List<Long> selected(ActualDutyFilter duty) {
        return query(PersonnelExtraFilters.of(null, duty), 1, 100, null, false, null)
                .stream().map(Personnel::getId).sorted().collect(Collectors.toList());
    }

    private long count(ActualDutyFilter duty) {
        return dao.count(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, false, PersonnelExtraFilters.of(null, duty));
    }

    private List<Long> ids(List<Personnel> rows) {
        return rows.stream().map(Personnel::getId).collect(Collectors.toList());
    }

    @Test void onDutyTreatsBlankZeroAndSameUnitAsAuthorizedAndDifferentUnitAsBorrowed() {
        assertEquals(Arrays.asList(1L, 2L, 3L, 7L), selected(ActualDutyFilter.of("yes", null)));
        assertEquals(Arrays.asList(4L, 5L, 6L, 8L), selected(ActualDutyFilter.of("no", null)));
        assertEquals(4, count(ActualDutyFilter.of(" yes ", null)));
        assertEquals(4, count(ActualDutyFilter.of("no ", null)));
        assertTrue(ActualDutyFilter.of("", " ").isEmpty());
        assertEquals(8, query(PersonnelExtraFilters.empty(), 1, 100, null, false, null).size());
        assertEquals(2, query(PersonnelExtraFilters.of(null, ActualDutyFilter.of("yes", null)), 1, 2, null, false, null).size());
        assertEquals(0, query(PersonnelExtraFilters.of(null, ActualDutyFilter.of("yes", null)), 3, 2, null, false, null).size());
    }

    @Test void actualDepartmentIdMatchesExactlyWithoutSubtreeAndRejectsInvalidValues() {
        assertEquals(Collections.singletonList(3L), selected(ActualDutyFilter.of(null, "1")));
        assertEquals(Collections.singletonList(5L), selected(ActualDutyFilter.of(null, "2")));
        assertEquals(Collections.singletonList(4L), selected(ActualDutyFilter.of(null, "3")));
        assertEquals(Arrays.asList(6L, 8L), selected(ActualDutyFilter.of(null, "4")));
        // 1 的子树含 2,但精确匹配不展开:actual=2 的人不出现在 actualDepartmentId=1 结果里
        assertFalse(selected(ActualDutyFilter.of(null, "1")).contains(5L));
        assertEquals(Collections.singletonList(3L), selected(ActualDutyFilter.of("yes", "1")));
        assertEquals(0, count(ActualDutyFilter.of(null, "999")));
        for (String invalid : new String[]{"0", "-2", "abc", "1.5", "3 OR 1=1"}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> ActualDutyFilter.of(null, invalid), invalid);
            assertTrue(error.getMessage().contains("actualDepartmentId"), invalid);
        }
        IllegalArgumentException onDutyError = assertThrows(IllegalArgumentException.class, () -> ActualDutyFilter.of("maybe", null));
        assertEquals("onDuty 仅支持空值、yes 或 no", onDutyError.getMessage());
        assertEquals(Collections.singletonList(3L), selected(ActualDutyFilter.of(null, " 1 ")));
    }

    @Test void appendToUsesFixedFragmentsAndBindsValuesOnly() {
        StringBuilder sql = new StringBuilder(); List<Object> args = new ArrayList<>();
        ActualDutyFilter.of("yes", null).appendTo(sql, args);
        assertEquals(" AND (p.actual_department_id IS NULL OR p.actual_department_id=0 OR p.actual_department_id=p.department_id)", sql.toString());
        assertTrue(args.isEmpty());
        sql = new StringBuilder(); args = new ArrayList<>();
        ActualDutyFilter.of("no", null).appendTo(sql, args);
        assertEquals(" AND p.actual_department_id>0 AND (p.department_id IS NULL OR p.actual_department_id<>p.department_id)", sql.toString());
        assertTrue(args.isEmpty());
        sql = new StringBuilder(); args = new ArrayList<>();
        ActualDutyFilter.of(null, "3").appendTo(sql, args);
        assertEquals(" AND p.actual_department_id=?", sql.toString());
        assertEquals(Collections.singletonList(3L), args);
        sql = new StringBuilder(); args = new ArrayList<>();
        ActualDutyFilter.of("yes", "3").appendTo(sql, args);
        String combined = sql.toString();
        assertEquals(2, combined.split(" AND ", -1).length - 1);
        assertTrue(combined.contains("p.actual_department_id=p.department_id"));
        assertTrue(combined.endsWith(" AND p.actual_department_id=?"));
        assertEquals(Collections.singletonList(3L), args);
    }

    private List<Long> sortedIds(List<Personnel> rows) {
        return rows.stream().map(Personnel::getId).sorted().collect(Collectors.toList());
    }

    @Test void actualDutyIntersectsDepartmentDriverLicenseAndPointFilters() {
        assertEquals(Arrays.asList(6L, 8L), sortedIds(query(PersonnelExtraFilters.of(null, ActualDutyFilter.of("no", null)), 1, 100, 1L, false, null)));
        assertEquals(Arrays.asList(4L, 6L, 8L), sortedIds(query(PersonnelExtraFilters.of(null, ActualDutyFilter.of("no", null)), 1, 100, 1L, true, null)));
        assertEquals(Collections.singletonList(4L), selectedCombined(DriverLicenseFilter.of("yes", null, null), ActualDutyFilter.of("no", null)));
        assertEquals(Collections.singletonList(1L), selectedCombined(DriverLicenseFilter.of("yes", null, null), ActualDutyFilter.of("yes", null)));
        assertEquals(Arrays.asList(4L, 6L, 8L), sortedIds(query(PersonnelExtraFilters.of(null, ActualDutyFilter.of("no", null)), 1, 100, null, false, "营区")));
        assertEquals(Collections.singletonList(4L), sortedIds(query(PersonnelExtraFilters.of(DriverLicenseFilter.of("yes", null, null), ActualDutyFilter.of("no", null)), 1, 100, null, false, "营区")));
    }

    private List<Long> selectedCombined(DriverLicenseFilter driver, ActualDutyFilter duty) {
        return query(PersonnelExtraFilters.of(driver, duty), 1, 100, null, false, null)
                .stream().map(Personnel::getId).sorted().collect(Collectors.toList());
    }

    @Test void scopeNeverWidensActualDutyResultsToForeignPeople() {
        DataScope.set(new DataScope.Context(9L, "child", false, new HashSet<>(Arrays.asList(1L, 2L))));
        assertEquals(Arrays.asList(1L, 2L, 3L), selected(ActualDutyFilter.of("yes", null)));
        assertEquals(Arrays.asList(4L, 6L, 8L), selected(ActualDutyFilter.of("no", null)));
        // 实际单位可指向 scope 外单位,但人员编制单位必须仍在 scope 内
        assertEquals(Collections.singletonList(4L), selected(ActualDutyFilter.of(null, "3")));
        DataScope.set(new DataScope.Context(9L, "empty", false, Collections.emptySet()));
        assertEquals(0, count(ActualDutyFilter.of("yes", null)));
        assertEquals(0, count(ActualDutyFilter.of("no", null)));
        assertEquals(0, count(ActualDutyFilter.of(null, "3")));
    }

    @Test void retainMatchingIsLazyPreservesOrderAndRunsOneScopedQuery() {
        JdbcTemplate unused = mock(JdbcTemplate.class);
        List<Personnel> candidates = Arrays.asList(person(8, 1), person(3, 1), person(4, 2), person(1, 1));
        assertSame(candidates, PersonnelExtraFilters.empty().retainMatching(unused, candidates));
        assertTrue(PersonnelExtraFilters.of(null, ActualDutyFilter.of("no", null)).retainMatching(unused, Collections.emptyList()).isEmpty());
        verifyNoInteractions(unused);
        DataScope.set(new DataScope.Context(9L, "child", false, new HashSet<>(Arrays.asList(1L, 2L))));
        // 保序:候选 [6,4,1,8] 中 no → [6,4,8];组合驾驶证筛选再收窄为 [4]
        assertEquals(Arrays.asList(6L, 4L, 8L), ids(PersonnelExtraFilters.of(null, ActualDutyFilter.of("no", null)).retainMatching(jdbc, Arrays.asList(person(6, 1), person(4, 2), person(1, 1), person(8, 1)))));
        JdbcTemplate tracked = spy(jdbc);
        assertEquals(Collections.singletonList(4L), ids(PersonnelExtraFilters.of(DriverLicenseFilter.of("yes", null, null), ActualDutyFilter.of("no", null)).retainMatching(tracked, candidates)));
        verify(tracked, times(1)).queryForList(anyString(), eq(Long.class), any(Object[].class));
    }

    @SuppressWarnings("unchecked")
    @Test void dashboardActualDutyCountsPeopleByTreeOrderedPathsAndMatchesDaoFilters() {
        StatsService stats = new StatsService(jdbc, dao);
        List<StatsSummary.NameCount> rows = (List<StatsSummary.NameCount>) ((Map<?, ?>) stats.dashboard().get("groups")).get("actualDuty");
        assertEquals(Arrays.asList("一连", "一连一排", "二连", "二连三排"),
                rows.stream().map(StatsSummary.NameCount::getName).collect(Collectors.toList()));
        // 树序而非人数序:三排 2 人仍排在最后
        assertEquals(Arrays.asList(1L, 1L, 1L, 2L), rows.stream().map(StatsSummary.NameCount::getCount).collect(Collectors.toList()));
        long[] departmentIds = {1, 2, 3, 4};
        for (int i = 0; i < departmentIds.length; i++) {
            assertEquals(rows.get(i).getCount(), count(ActualDutyFilter.of(null, String.valueOf(departmentIds[i]))));
        }
        // 组含所有 actual>0 人员(在编同单位者也入组),与筛选 no 的口径不同,总数按 SQL 直接核对
        long actualMarked = jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE actual_department_id>0", Long.class);
        assertEquals(actualMarked, rows.stream().mapToLong(StatsSummary.NameCount::getCount).sum());
        assertFalse(rows.stream().anyMatch(row -> row.getName().contains("未分配")));
    }

    @SuppressWarnings("unchecked")
    @Test void actualDutyGroupIsRestrictedBeforeAggregationLikeEveryOtherGroup() {
        StatsService stats = new StatsService(jdbc, dao);
        DataScope.set(new DataScope.Context(9L, "child", false, new HashSet<>(Arrays.asList(1L, 2L))));
        List<StatsSummary.NameCount> rows = (List<StatsSummary.NameCount>) ((Map<?, ?>) stats.dashboard().get("groups")).get("actualDuty");
        assertEquals(Arrays.asList("一连", "二连", "二连三排"),
                rows.stream().map(StatsSummary.NameCount::getName).collect(Collectors.toList()));
        assertEquals(Arrays.asList(1L, 1L, 2L), rows.stream().map(StatsSummary.NameCount::getCount).collect(Collectors.toList()));
        DataScope.set(new DataScope.Context(9L, "empty", false, Collections.emptySet()));
        assertTrue(((List<?>) ((Map<?, ?>) stats.dashboard().get("groups")).get("actualDuty")).isEmpty());
    }

    @Test void personnelControllerBindsActualDutyParamsAndTotalsMatchList() throws Exception {
        PersonnelService service = people(jdbc);
        DepartmentService departments = new DepartmentService(new DepartmentDao(jdbc), new PersonnelDao(jdbc));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PersonnelController(service, new DepartmentDao(jdbc), jdbc,
                new RollCallExcelService(service, departments, jdbc), new RosterExcelService(service, departments, jdbc)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/personnel").param("onDuty", "no").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.list.length()").value(4));
        mvc.perform(get("/api/personnel").param("onDuty", "yes").param("actualDepartmentId", "1").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list.length()").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(3))
                .andExpect(jsonPath("$.data.list[0].actualDepartmentId").value(1))
                .andExpect(jsonPath("$.data.list[0].actualDepartmentName").value("一连"))
                .andExpect(jsonPath("$.data.list[0].actualDepartmentPath").value("一连"));
        mvc.perform(get("/api/personnel").param("onDuty", " no ").param("size", "50"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(4));
        mvc.perform(get("/api/personnel")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(8));
        mvc.perform(get("/api/personnel").param("onDuty", "wrong")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/personnel").param("actualDepartmentId", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/personnel").param("actualDepartmentId", "abc")).andExpect(status().isBadRequest());
    }

    /** 人员表单必填项已统一填齐,各用例只聚焦实际在岗校验点 */
    private Personnel person(String card) {
        Personnel p = new Personnel();
        p.setName("测试人员");
        p.setIdCard(card);
        p.setMilitaryIdNo("军证实际-001");
        p.setGender("男");
        p.setBirthday("1990-01-01");
        p.setDepartmentId(1L);
        p.setLocationPoint("机关");
        p.setCategory("军官");
        p.setRank("上尉");
        p.setPost("参谋");
        p.setEntryDate("2010-06-01");
        p.setEquivEntryDate("2010-06-01");
        p.setNation("汉族");
        p.setNativePlace("浙江省/杭州市/上城区");
        p.setEnlistPlace("浙江省/杭州市/上城区");
        p.setBloodType("A");
        p.setOnlyChild("否");
        p.setAddress("杭州市XX路XX号");
        p.setStatus("在职");
        return p;
    }

    @Test void createValidatesNormalizesPreservesAndClearsActualDutyFields() {
        PersonnelService service = people(jdbc);
        Personnel p = person("11010519491231002X");
        p.setActualDepartmentId(3L);
        Personnel saved = service.create(p);
        assertEquals(3L, saved.getActualDepartmentId());
        assertEquals("二连", saved.getActualDepartmentName());
        assertEquals("二连", saved.getActualDepartmentPath());
        assertFalse(jdbc.queryForMap("SELECT * FROM personnel WHERE id=?", saved.getId()).containsKey("actual_department_name"));
        assertFalse(jdbc.queryForMap("SELECT * FROM personnel WHERE id=?", saved.getId()).containsKey("actual_department_path"));

        // null/<=0 归一存 0
        Personnel second = person("110105195001010012");
        second.setActualDepartmentId(-5L);
        Personnel savedSecond = service.create(second);
        assertEquals(0L, savedSecond.getActualDepartmentId());
        assertEquals("", savedSecond.getActualDepartmentName()); // IFNULL 语义:无实际单位时名称为空串
        assertNull(savedSecond.getActualDepartmentPath());

        // 局部更新(null 保留)不得清空实际单位
        Personnel partial = new Personnel(); partial.setRemark("仅改备注");
        Personnel kept = service.update(saved.getId(), partial);
        assertEquals(3L, kept.getActualDepartmentId());

        // 校验:实际单位必须真实存在
        Personnel missing = new Personnel(); missing.setActualDepartmentId(999L);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.update(saved.getId(), missing));
        assertTrue(error.getMessage().contains("实际单位不存在"));

        // 调整实际单位 → 名称/路径随之变化;空串/0 明确清空
        Personnel change = new Personnel(); change.setActualDepartmentId(2L);
        Personnel updated = service.update(saved.getId(), change);
        assertEquals("一连一排", updated.getActualDepartmentPath());
        assertEquals("一排", updated.getActualDepartmentName());
        Personnel clear = new Personnel(); clear.setActualDepartmentId(0L);
        Personnel cleared = service.update(saved.getId(), clear);
        assertEquals(0L, cleared.getActualDepartmentId());
        assertNull(cleared.getActualDepartmentPath());

        // 实际单位不要求在调用者数据范围内:子账号 scope={1} 仍可保存 actual=2 的人员
        Personnel borrowed = new Personnel(); borrowed.setActualDepartmentId(2L);
        assertEquals(2L, service.update(saved.getId(), borrowed).getActualDepartmentId());
        DataScope.set(new DataScope.Context(9L, "child", false, new HashSet<>(Collections.singletonList(1L))));
        Personnel note = new Personnel(); note.setRemark("子账号改备注");
        assertEquals(2L, service.update(saved.getId(), note).getActualDepartmentId());
    }

    @Test void legacyDatabaseEnsureColumnIsIdempotentAndKeepsExistingRows() throws Exception {
        java.nio.file.Path legacyDir = temp.resolve("legacy");
        java.nio.file.Files.createDirectories(legacyDir);
        JdbcTemplate legacy = PersonnelIntegrityTest.temporaryDatabase(legacyDir);
        legacy.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY, name TEXT, department_id INTEGER, post_date TEXT DEFAULT '')");
        legacy.update("INSERT INTO personnel(id,name,department_id,post_date) VALUES(7,'旧库人员',1,'2020-01-01')");
        SchemaInitializer initializer = new SchemaInitializer(legacy, new AdminUserDao(legacy));
        for (int i = 0; i < 2; i++) { // 幂等:重复执行不报错、不丢数据
            ReflectionTestUtils.invokeMethod(initializer, "ensureColumn", "personnel", "actual_department_id", "actual_department_id INTEGER DEFAULT 0");
            ReflectionTestUtils.invokeMethod(initializer, "ensureColumn", "personnel", "actual_post", "actual_post TEXT DEFAULT ''");
            ReflectionTestUtils.invokeMethod(initializer, "ensureColumn", "personnel", "actual_post_date", "actual_post_date TEXT DEFAULT ''");
        }
        assertEquals(1, legacy.queryForObject("SELECT COUNT(*) FROM personnel", Integer.class));
        assertEquals("旧库人员", legacy.queryForObject("SELECT name FROM personnel WHERE id=7", String.class));
        assertEquals("2020-01-01", legacy.queryForObject("SELECT post_date FROM personnel WHERE id=7", String.class));
        assertEquals(0L, legacy.queryForObject("SELECT actual_department_id FROM personnel WHERE id=7", Long.class));
        assertEquals("", legacy.queryForObject("SELECT actual_post FROM personnel WHERE id=7", String.class));
        assertEquals("", legacy.queryForObject("SELECT actual_post_date FROM personnel WHERE id=7", String.class));
        for (String column : new String[]{"actual_department_id", "actual_post", "actual_post_date"}) {
            long seen = 0;
            for (Map<String, Object> col : legacy.queryForList("PRAGMA table_info(personnel)")) {
                if (column.equals(col.get("name"))) seen++;
            }
            assertEquals(1, seen, column);
        }
    }

    private static Personnel person(long id, long department) {
        Personnel p = new Personnel();
        p.setId(id);
        p.setDepartmentId(department);
        return p;
    }
}
