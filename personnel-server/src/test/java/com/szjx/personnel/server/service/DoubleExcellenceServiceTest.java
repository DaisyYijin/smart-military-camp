package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.auth.ForbiddenException;
import com.szjx.personnel.server.config.GlobalExceptionHandler;
import com.szjx.personnel.server.controller.DoubleExcellenceController;
import com.szjx.personnel.server.model.DoubleExcellenceRecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Isolated SQLite only: never starts the application or reads real data. */
public class DoubleExcellenceServiceTest {
    @TempDir Path directory;
    JdbcTemplate jdbc;
    DoubleExcellenceService service;

    /** Shared with Excel tests; creates only the new business table and indexes. */
    public static void createSchema(JdbcTemplate jdbc) {
        // 与生产一致:评定值由数据字典 de_rating 驱动,表不再带固定评级 CHECK
        jdbc.execute("CREATE TABLE double_excellence_record(id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "personnel_id INTEGER NOT NULL,year INTEGER NOT NULL CHECK(year BETWEEN 1900 AND 9999)," +
                "quarter INTEGER NOT NULL CHECK(quarter BETWEEN 1 AND 4)," +
                "rating TEXT NOT NULL,rating_date TEXT NOT NULL," +
                "created_at TEXT DEFAULT (datetime('now','localtime')),updated_at TEXT DEFAULT (datetime('now','localtime')))" );
        jdbc.execute("CREATE UNIQUE INDEX idx_double_excellence_person_year_quarter ON double_excellence_record(personnel_id,year,quarter)");
        jdbc.execute("CREATE INDEX idx_double_excellence_year ON double_excellence_record(year,quarter)");
        // 核心服务与 Excel 服务的评级校验都查询 sys_dict(de_rating),隔离库需要同表同数据
        jdbc.execute("CREATE TABLE sys_dict(id INTEGER PRIMARY KEY AUTOINCREMENT,dict_type TEXT NOT NULL,dict_label TEXT NOT NULL," +
                "sort INTEGER DEFAULT 0,enabled INTEGER DEFAULT 1,parent_id INTEGER DEFAULT 0)");
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,sort,enabled,parent_id) VALUES" +
                "('de_rating','好',1,1,0),('de_rating','中',2,1,0),('de_rating','一般',3,1,0)");
    }

    @BeforeEach void setup() {
        DataScope.clear();
        jdbc = PersonnelIntegrityTest.temporaryDatabase(directory);
        PersonnelIntegrityTest.personnelSchema(jdbc);
        createSchema(jdbc);
        jdbc.update("UPDATE department SET parent_id=0,sort=0 WHERE id=1");
        jdbc.update("INSERT INTO department(id,name,parent_id,sort) VALUES(2,'下级',1,0),(3,'外部',0,0)");
        jdbc.update("INSERT INTO personnel(id,name,id_card,department_id) VALUES(1,'张三','CARD1',1),(2,'李四','CARD2',2),(3,'外部姓名','CARD3',3),(4,'无评级','CARD4',1)");
        service = PersonnelIntegrityTest.transactional(new DoubleExcellenceService(jdbc), jdbc);
    }
    @AfterEach void clear() { DataScope.clear(); }
    private void scoped(Long... departments) { DataScope.set(new DataScope.Context(2L,"child",false,new LinkedHashSet<>(Arrays.asList(departments)))); }
    private DoubleExcellenceRecord record(long person, int year, int quarter, String rating) {
        DoubleExcellenceRecord r = new DoubleExcellenceRecord(); r.setPersonnelId(person); r.setYear(year); r.setQuarter(quarter);
        r.setRating(rating); r.setRatingDate("2026-10-18"); return r;
    }
    private Map<String,String> filters(String... pairs) { Map<String,String> map = new LinkedHashMap<>(); for (int i=0;i<pairs.length;i+=2) map.put(pairs[i],pairs[i+1]); return map; }
    private long total(Map<String,Object> page) { return ((Number) page.get("total")).longValue(); }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> statsRows(Map<String,Object> result) { return (List<Map<String,Object>>) result.get("list"); }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> quarters(Map<String,Object> result) { return (List<Map<String,Object>>) result.get("quarters"); }
    private Map<String,String> importRow(String card, String name, String rating) { return filters("year","2026","quarter","1","personnelName",name,"idCard",card,"rating",rating,"ratingDate","2026-10-18"); }

    @Test void createRejectsDuplicatesUpdateRejectsConflictsAndImportAloneUpserts() {
        DoubleExcellenceRecord first = service.create(record(1,2026,1,"好"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.create(record(1,2026,1,"一般"))).getMessage().contains("编辑"));
        DoubleExcellenceRecord second = service.create(record(1,2026,2,"中"));
        assertThrows(IllegalArgumentException.class, () -> service.update(second.getId(),record(1,2026,1,"一般")));
        assertEquals("中",service.get(second.getId()).getRating());
        Map<String,Object> imported = service.importRecord(importRow(" card1 ","张三","一般"));
        assertEquals(true, imported.get("updated"));
        assertEquals(first.getId(),((DoubleExcellenceRecord)imported.get("record")).getId());
        assertEquals("一般",service.get(first.getId()).getRating());
        assertEquals(false,service.importRecord(importRow("CARD2","李四","好")).get("updated"));
        assertEquals(3,total(service.query(Collections.emptyMap())));
        assertThrows(RuntimeException.class, () -> jdbc.update("INSERT INTO double_excellence_record(personnel_id,year,quarter,rating,rating_date) VALUES(1,2026,1,'好','2026-01-01')"));
        assertTrue(jdbc.queryForList("PRAGMA foreign_key_list(double_excellence_record)").isEmpty());
    }

    @Test void updateAndReadAlwaysUseCurrentPersonnelAndNeverOverwriteArchives() {
        DoubleExcellenceRecord r = service.create(record(1,2026,1,"好"));
        jdbc.update("UPDATE personnel SET name='已改名',id_card='NEWCARD' WHERE id=1");
        assertEquals("已改名",service.get(r.getId()).getPersonnelName());
        assertEquals("NEWCARD",service.get(r.getId()).getIdCard());
        DoubleExcellenceRecord change = record(1,2026,1,"中"); change.setPersonnelName("伪造姓名"); change.setIdCard("NEWCARD");
        assertThrows(IllegalArgumentException.class, () -> service.update(r.getId(),change));
        assertEquals("已改名",jdbc.queryForObject("SELECT name FROM personnel WHERE id=1",String.class));
        change.setPersonnelName("已改名"); change.setIdCard("OTHER");
        assertThrows(IllegalArgumentException.class, () -> service.update(r.getId(),change));
        assertThrows(IllegalArgumentException.class, () -> service.importRecord(importRow("NEWCARD","不匹配姓名","好")));
        assertThrows(IllegalArgumentException.class, () -> service.importRecord(importRow("","已改名","好")));
        jdbc.update("INSERT INTO personnel(id,name,id_card,department_id) VALUES(5,'重号','NEWCARD',1)");
        assertThrows(IllegalArgumentException.class, () -> service.importRecord(importRow("NEWCARD","已改名","好")));
    }

    @Test void validatesYearQuarterRatingAndStrictRealDateButAllowsCrossQuarterSupplement() {
        for (String rating : new String[]{null,""," ","未评定","unrated","优秀","好 "}) {
            assertThrows(IllegalArgumentException.class, () -> service.create(record(1,2026,1,rating)));
        }
        for (int year : new int[]{1899,10000}) assertThrows(IllegalArgumentException.class, () -> service.create(record(1,year,1,"好")));
        for (int quarter : new int[]{0,5}) assertThrows(IllegalArgumentException.class, () -> service.create(record(1,2026,quarter,"好")));
        for (String date : new String[]{null,"","2026-2-03","2026/02/03","2026-02-30","2025-02-29","0000-01-01","2026-01-01T00:00:00"," 2026-01-01"}) {
            DoubleExcellenceRecord r = record(1,2026,1,"好"); r.setRatingDate(date);
            assertThrows(IllegalArgumentException.class, () -> service.create(r),date);
        }
        DoubleExcellenceRecord leap = record(1,1900,1,"好"); leap.setRatingDate("2024-02-29");
        assertEquals("2024-02-29",service.create(leap).getRatingDate());
        assertNotNull(service.create(record(1,9999,4,"中")));
        assertEquals("2026-10-18",service.create(record(1,2026,1,"好")).getRatingDate());
    }

    @Test void oldAndNewPersonScopeAreEnforcedBeforeWritesIncludingImportAndZeroScope() {
        DoubleExcellenceRecord in = service.create(record(1,2026,1,"好")), out = service.create(record(3,2026,1,"好"));
        scoped(1L,2L);
        assertThrows(ForbiddenException.class, () -> service.get(out.getId()));
        assertThrows(ForbiddenException.class, () -> service.update(out.getId(),record(1,2026,2,"好")));
        assertThrows(ForbiddenException.class, () -> service.update(in.getId(),record(3,2026,2,"好")));
        assertThrows(ForbiddenException.class, () -> service.delete(out.getId()));
        assertThrows(ForbiddenException.class, () -> service.create(record(3,2026,2,"好")));
        assertThrows(ForbiddenException.class, () -> service.importRecord(importRow("CARD3","外部姓名","中")));
        assertEquals(1,total(service.query(filters("size","1"))));
        assertEquals(3,total(service.stats(filters("year","2026","size","1"))));
        assertEquals(0,total(service.query(filters("departmentId","3"))));
        assertEquals(0,total(service.stats(filters("departmentId","3"))));
        DoubleExcellenceRecord moved = service.update(in.getId(),record(2,2026,1,"中"));
        assertEquals(2L,moved.getPersonnelId());
        scoped();
        assertEquals(0,total(service.query(Collections.emptyMap())));
        Map<String,Object> empty = service.stats(filters("year","2026")); assertEquals(0,total(empty));
        for (Map<String,Object> quarter : quarters(empty)) assertEquals(0L,quarter.get("unrated"));
        assertThrows(ForbiddenException.class, () -> service.get(in.getId()));
        assertThrows(ForbiddenException.class, () -> service.create(record(1,2026,2,"好")));
    }

    @Test void statsStartFromPeopleIncludeFourNullQuartersAndCountFullMatchedSetNotPage() {
        service.create(record(1,2026,1,"好")); service.create(record(1,2026,2,"中"));
        service.create(record(2,2026,1,"一般")); service.create(record(3,2025,1,"好"));
        Map<String,Object> result = service.stats(filters("year","2026","size","1"));
        assertEquals(4,total(result)); assertEquals(1,statsRows(result).size());
        assertEquals(1L,quarters(result).get(0).get("good")); assertEquals(1L,quarters(result).get(0).get("general"));
        assertEquals(2L,quarters(result).get(0).get("rated")); assertEquals(2L,quarters(result).get(0).get("unrated"));
        assertEquals(1L,quarters(result).get(1).get("middle")); assertEquals(4L,quarters(result).get(3).get("unrated"));
        for (Map<String,Object> q : quarters(result)) assertEquals(total(result),((Number)q.get("good")).longValue()+((Number)q.get("middle")).longValue()+((Number)q.get("general")).longValue()+((Number)q.get("unrated")).longValue());
        Map<String,Object> never = statsRows(service.stats(filters("year","2026","personnelName","无评级"))).get(0);
        for (int q=1;q<=4;q++) assertNull(never.get("q"+q));
        // 主页面矩阵的“单位”列：按人员编制单位展示完整路径。
        Map<String,Object> zhang = null, li = null;
        for (Map<String,Object> row : statsRows(service.stats(filters("year","2026","size","4")))) {
            if ("张三".equals(row.get("personnelName"))) zhang = row;
            if ("李四".equals(row.get("personnelName"))) li = row;
        }
        assertNotNull(zhang); assertNotNull(li);
        assertEquals("机关", zhang.get("departmentPath"));
        assertEquals("机关下级", li.get("departmentPath"));
        assertEquals(4,total(service.stats(filters("year","2026","quarter","4"))));
        assertEquals(2,total(service.stats(filters("year","2026","quarter","1","rating","unrated"))));
        assertEquals(4,total(service.stats(filters("year","2026","rating","unrated"))));
        assertEquals(1,total(service.stats(filters("year","2026","rating","中"))));
        assertEquals(0,total(service.stats(filters("year","2026","quarter","1","rating","中"))));
        assertEquals(1,total(service.stats(filters("year","2026","quarter","2","rating","中"))));
        assertEquals(LocalDate.now().getYear(),service.stats(Collections.emptyMap()).get("year"));
        // Stats deliberately ignore record-only date filters.
        assertEquals(4,total(service.stats(filters("year","2026","ratingDateStart","invalid"))));
        Map<String,Object> later = service.stats(filters("year","2026","page","99","size","1"));
        assertTrue(statsRows(later).isEmpty()); assertEquals(4,total(later)); assertEquals(quarters(result),quarters(later));
    }

    @Test void recordFiltersPaginationEscapingAndDepartmentSubtreeAlwaysIntersectScope() {
        service.create(record(1,2026,1,"好")); service.create(record(1,2025,2,"中")); service.create(record(2,2026,1,"一般"));
        service.create(record(3,2026,1,"好")); scoped(1L,2L);
        assertEquals(3,total(service.query(filters("size","1"))));
        assertEquals(1,((List<?>)service.query(filters("page","2","size","1")).get("list")).size());
        assertEquals(0,((List<?>)service.query(filters("page","4","size","1")).get("list")).size());
        assertEquals(2,total(service.query(filters("year","2026","quarter","1"))));
        assertEquals(1,total(service.query(filters("personnelName","李","idCard","CARD2","rating","一般"))));
        assertEquals(3,total(service.query(filters("departmentId","1"))));
        assertEquals(1,total(service.query(filters("departmentId","2"))));
        scoped(2L); assertEquals(1,total(service.query(filters("departmentId","1"))));
        assertEquals(1,total(service.stats(filters("year","2026","departmentId","1"))));
        assertEquals(0,total(service.query(filters("ratingDateStart","2026-10-19"))));
        assertEquals(1,total(service.query(filters("ratingDateStart","2026-10-18","ratingDateEnd","2026-10-18","size","50000"))));
        assertThrows(IllegalArgumentException.class, () -> service.query(filters("size","50001")));
        assertThrows(IllegalArgumentException.class, () -> service.query(filters("page","0")));
        assertThrows(IllegalArgumentException.class, () -> service.query(filters("ratingDateStart","2026-12-01","ratingDateEnd","2026-01-01")));
        assertThrows(IllegalArgumentException.class, () -> service.query(filters("rating","unrated")));
        assertEquals(0,total(service.query(filters("personnelName","%"))));
        assertEquals(0,total(service.query(filters("idCard","_"))));
        assertEquals(0,total(service.query(filters("personnelName","' OR 1=1 --"))));
        assertEquals(0,total(service.query(filters("departmentId","999"))));
    }

    @Test void operationLogsHaveChineseModuleNameAndDoNotPrefetchOutOfScopeNames() {
        DoubleExcellenceRecord r = service.create(record(1,2026,1,"好"));
        LogService logs = org.mockito.Mockito.mock(LogService.class);
        com.szjx.personnel.server.auth.OpLogInterceptor interceptor = new com.szjx.personnel.server.auth.OpLogInterceptor(logs,jdbc);
        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();
        for (String[] operation : new String[][]{{"POST","","新增双争评比"},{"GET","/export","导出双争评比"},{"POST","/import-excel","导入双争评比"}}) {
            org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest(operation[0],"/api/double-excellence"+operation[1]);
            request.setAttribute("authUser","admin"); request.setRemoteAddr("127.0.0.1");
            interceptor.afterCompletion(request,response,new Object(),null);
            org.mockito.Mockito.verify(logs).record(org.mockito.ArgumentMatchers.eq("admin"),org.mockito.ArgumentMatchers.eq(operation[2]),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("127.0.0.1"));
        }
        scoped(3L);
        org.springframework.mock.web.MockHttpServletRequest denied = new org.springframework.mock.web.MockHttpServletRequest("DELETE","/api/double-excellence/"+r.getId());
        interceptor.preHandle(denied,response,new Object()); assertNull(denied.getAttribute("opLogName"));
        scoped(1L);
        org.springframework.mock.web.MockHttpServletRequest delete = new org.springframework.mock.web.MockHttpServletRequest("DELETE","/api/double-excellence/"+r.getId());
        delete.setAttribute("authUser","admin"); delete.setRemoteAddr("127.0.0.1");
        interceptor.preHandle(delete,response,new Object());
        service.delete(r.getId()); interceptor.afterCompletion(delete,response,new Object(),null);
        org.mockito.Mockito.verify(logs).record("admin","删除双争评比","张三 · 2026年第1季度(ID "+r.getId()+")","127.0.0.1");
    }

    @Test void publicHttpPagesAreBoundedAndCrudUsesStableJsonModel() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DoubleExcellenceController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
        String body = "{\"year\":2026,\"quarter\":1,\"personnelName\":\"张三\",\"idCard\":\"CARD1\",\"rating\":\"好\",\"ratingDate\":\"2026-10-18\"}";
        mvc.perform(post("/api/double-excellence").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.data.personnelName").value("张三"));
        mvc.perform(get("/api/double-excellence").param("size","200")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        for (String route : Arrays.asList("/api/double-excellence","/api/double-excellence/stats")) {
            for (String size : Arrays.asList("201","50000","0","-1","abc")) mvc.perform(get(route).param("size",size)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/double-excellence/1")).andExpect(jsonPath("$.data.ratingDate").value("2026-10-18"));
        mvc.perform(put("/api/double-excellence/1").contentType(MediaType.APPLICATION_JSON).content(body.replace("好","中"))).andExpect(jsonPath("$.data.rating").value("中"));
        mvc.perform(get("/api/double-excellence/999")).andExpect(status().isBadRequest());
        scoped(3L);
        mvc.perform(get("/api/double-excellence/1")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/double-excellence/1")).andExpect(status().isForbidden());
        DataScope.clear(); mvc.perform(delete("/api/double-excellence/1")).andExpect(status().isOk());
        assertEquals(0,total(service.query(Collections.emptyMap())));
    }
}
