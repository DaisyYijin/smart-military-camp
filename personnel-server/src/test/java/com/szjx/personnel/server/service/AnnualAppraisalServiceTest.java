package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.auth.ForbiddenException;
import com.szjx.personnel.server.model.AnnualAppraisalRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Isolated SQLite only: never starts the application or reads real data. */
public class AnnualAppraisalServiceTest {
    @TempDir Path directory;
    JdbcTemplate jdbc;
    AnnualAppraisalService service;

    /** 评定选项与生产一致:数据字典 annual_rating 启用项,字典缺失时服务回退默认四档。 */
    public static void createSchema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE annual_appraisal_record(id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "personnel_id INTEGER NOT NULL,year INTEGER NOT NULL CHECK(year BETWEEN 1900 AND 9999)," +
                "rating TEXT NOT NULL,rating_date TEXT NOT NULL," +
                "created_at TEXT DEFAULT (datetime('now','localtime')),updated_at TEXT DEFAULT (datetime('now','localtime')))");
        jdbc.execute("CREATE UNIQUE INDEX idx_annual_appraisal_person_year ON annual_appraisal_record(personnel_id,year)");
        jdbc.execute("CREATE INDEX idx_annual_appraisal_year ON annual_appraisal_record(year)");
        jdbc.execute("CREATE TABLE sys_dict(id INTEGER PRIMARY KEY AUTOINCREMENT,dict_type TEXT NOT NULL,dict_label TEXT NOT NULL," +
                "sort INTEGER DEFAULT 0,enabled INTEGER DEFAULT 1,parent_id INTEGER DEFAULT 0)");
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,sort,enabled,parent_id) VALUES" +
                "('annual_rating','优秀',1,1,0),('annual_rating','称职',2,1,0),('annual_rating','一般称职',3,1,0),('annual_rating','不称职',4,1,0)");
    }

    @BeforeEach void setup() {
        DataScope.clear();
        jdbc = PersonnelIntegrityTest.temporaryDatabase(directory);
        PersonnelIntegrityTest.personnelSchema(jdbc);
        createSchema(jdbc);
        jdbc.update("UPDATE department SET parent_id=0,sort=0 WHERE id=1");
        jdbc.update("INSERT INTO department(id,name,parent_id,sort) VALUES(2,'下级',1,0),(3,'外部',0,0)");
        jdbc.update("INSERT INTO personnel(id,name,id_card,department_id) VALUES(1,'张三','CARD1',1),(2,'李四','CARD2',2),(3,'外部姓名','CARD3',3)");
        service = PersonnelIntegrityTest.transactional(new AnnualAppraisalService(jdbc), jdbc);
    }
    @AfterEach void clear() { DataScope.clear(); }

    private void scoped(Long... departments) { DataScope.set(new DataScope.Context(2L,"child",false,new LinkedHashSet<>(Arrays.asList(departments)))); }
    private AnnualAppraisalRecord record(long person, int year, String rating) {
        AnnualAppraisalRecord r = new AnnualAppraisalRecord();
        r.setPersonnelId(person); r.setYear(year); r.setRating(rating); r.setRatingDate("2026-10-22"); return r;
    }
    private Map<String,String> filters(String... pairs) { Map<String,String> map = new LinkedHashMap<>(); for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]); return map; }
    private long total(Map<String,Object> page) { return ((Number) page.get("total")).longValue(); }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> rows(Map<String,Object> result) { return (List<Map<String,Object>>) result.get("list"); }

    @Test void createRejectsDuplicatesAndImportUpsertsByPersonAndYear() {
        AnnualAppraisalRecord first = service.create(record(1, 2026, "优秀"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.create(record(1, 2026, "称职"))).getMessage().contains("已存在"),
                "同一人员同一年度仅一条记录");
        // 身份证大小写与两端空白在导入路径统一归一;既有组合键命中则更新而非新增
        Map<String,Object> imported = service.importRecord(filters(
                "year", "2026", "personnelName", "张三", "idCard", " card1 ", "rating", "称职", "ratingDate", "2026-10-22"));
        assertEquals(true, imported.get("updated"));
        assertEquals(first.getId(), ((AnnualAppraisalRecord) imported.get("record")).getId());
        assertEquals("称职", service.get(first.getId()).getRating());
        assertEquals(false, service.importRecord(filters(
                "year", "2027", "personnelName", "李四", "idCard", "CARD2", "rating", "优秀", "ratingDate", "2026-10-22")).get("updated"));
        assertEquals(2, total(service.query(Collections.emptyMap())));
        // 姓名与身份证必须指向同一档案;导入绝不按姓名猜测或新建人员
        assertThrows(IllegalArgumentException.class, () -> service.importRecord(filters(
                "year", "2027", "personnelName", "李四", "idCard", "CARD1", "rating", "优秀", "ratingDate", "2026-10-22")));
    }

    @Test void unratedAndRatingStatsFiltersBindParametersCorrectly() {
        // 回归:rating=unrated 生成的 a.id IS NULL 无占位符,不得再追加参数,否则参数与占位符错位
        service.create(record(1, 2026, "优秀"));
        assertEquals(3, total(service.stats(filters("year", "2026"))));
        Map<String,Object> unrated = service.stats(filters("year", "2026", "rating", "unrated"));
        assertEquals(2, total(unrated));
        for (Map<String,Object> row : rows(unrated)) assertNull(row.get("entry"));
        Map<String,Object> rated = service.stats(filters("year", "2026", "rating", "优秀"));
        assertEquals(1, total(rated));
        assertNotNull(rows(rated).get(0).get("entry"));
    }

    @Test void dictionaryDrivesRatingOptionsWithDefaultFallback() {
        // 字典全部停用:服务回退默认四档,既有默认值仍可保存
        jdbc.update("UPDATE sys_dict SET enabled=0 WHERE dict_type='annual_rating'");
        assertEquals("称职", service.create(record(1, 2026, "称职")).getRating());
        // 自定义启用项立即可保存;不在启用项中的值拒绝
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,sort,enabled,parent_id) VALUES('annual_rating','特别优秀',9,1,0)");
        assertEquals("特别优秀", service.create(record(2, 2027, "特别优秀")).getRating());
        assertThrows(IllegalArgumentException.class, () -> service.create(record(2, 2028, "好")));
    }

    @Test void scopeIsEnforcedOnReadUpdateDeleteAndImport() {
        AnnualAppraisalRecord out = service.create(record(3, 2026, "不称职"));
        scoped(1L);
        AnnualAppraisalRecord in = service.create(record(1, 2026, "优秀"));
        assertThrows(ForbiddenException.class, () -> service.get(out.getId()));
        assertThrows(ForbiddenException.class, () -> service.update(out.getId(), record(3, 2027, "称职")));
        assertThrows(ForbiddenException.class, () -> service.delete(out.getId()));
        assertThrows(ForbiddenException.class, () -> service.create(record(3, 2028, "优秀")));
        assertThrows(ForbiddenException.class, () -> service.importRecord(filters(
                "year", "2026", "personnelName", "外部姓名", "idCard", "CARD3", "rating", "优秀", "ratingDate", "2026-10-22")));
        assertEquals(1, total(service.query(Collections.emptyMap())));
        assertEquals(1, total(service.stats(filters("year", "2026"))));
        assertEquals(0, total(service.stats(filters("year", "2026", "rating", "unrated"))));
    }

    @Test void strictRatingDateValidation() {
        for (String date : new String[]{"2026-02-30", "2026/10/22", "", "26-10-22"}) {
            AnnualAppraisalRecord r = record(1, 2026, "优秀"); r.setRatingDate(date);
            assertThrows(IllegalArgumentException.class, () -> service.create(r), date);
        }
        AnnualAppraisalRecord leap = record(1, 2024, "优秀");
        leap.setRatingDate("2024-02-29");
        assertEquals("2024-02-29", service.create(leap).getRatingDate());
        assertEquals("2026-10-22", service.create(record(1, 2026, "优秀")).getRatingDate());
    }
}
