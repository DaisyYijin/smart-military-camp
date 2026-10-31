package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.config.GlobalExceptionHandler;
import com.szjx.personnel.server.controller.PersonnelController;
import com.szjx.personnel.server.dao.DepartmentDao;
import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.model.Personnel;
import com.szjx.personnel.server.model.StatsSummary;
import com.szjx.personnel.server.util.DriverLicenseFilter;
import com.szjx.personnel.server.util.PersonnelExtraFilters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DriverLicenseFeatureTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;
    PersonnelDao dao;

    static JdbcTemplate database(Path path) {
        JdbcTemplate db = PersonnelIntegrityTest.temporaryDatabase(path);
        PersonnelIntegrityTest.personnelSchema(db);
        db.update("UPDATE department SET name='一连',parent_id=0,sort=1 WHERE id=1");
        db.update("INSERT INTO department(id,name,parent_id,sort) VALUES(2,'一排',1,1),(3,'二连',0,2)");
        db.execute("CREATE TABLE driver_license(id INTEGER PRIMARY KEY,personnel_id INTEGER,license_type TEXT,license_no TEXT,vehicle_class TEXT,issue_date TEXT,expiry_date TEXT,license_status TEXT,review_date TEXT,issuing_auth TEXT)");
        db.execute("CREATE TABLE equipment(id INTEGER PRIMARY KEY,keeper_id INTEGER,status TEXT,category TEXT)");
        db.execute("CREATE TABLE material(id INTEGER PRIMARY KEY,keeper_id INTEGER,quantity REAL,min_quantity REAL,category TEXT)");
        db.execute("CREATE TABLE vehicle(id INTEGER PRIMARY KEY,driver_id INTEGER,status TEXT,type TEXT,brand TEXT)");
        db.execute("CREATE TABLE leave_record(id INTEGER PRIMARY KEY,personnel_id INTEGER,start_date TEXT,end_date TEXT,leave_type TEXT)");
        db.execute("CREATE TABLE fitness_record(id INTEGER PRIMARY KEY,personnel_id INTEGER,test_date TEXT,grade TEXT)");
        db.execute("CREATE TABLE double_excellence_record(id INTEGER PRIMARY KEY,personnel_id INTEGER,year INTEGER,quarter INTEGER,rating TEXT DEFAULT '',rating_date TEXT DEFAULT '')");
        db.execute("CREATE TABLE annual_appraisal_record(id INTEGER PRIMARY KEY,personnel_id INTEGER,year INTEGER,rating TEXT DEFAULT '',rating_date TEXT DEFAULT '')");
        String[] names = {"周明","李宁","陈晓","王强","刘静","段岩","赵林"};
        int[] departments = {1,2,1,3,3,0,3};
        for (int i=0; i<names.length; i++) db.update("INSERT INTO personnel(id,name,id_card,department_id,status,location_point,gender) VALUES(?,?,?,?,?,?,?)",
                i+1,names[i],i==5 ? "" : "CARD"+(i+1),departments[i],"在职",i==1 ? "营区" : "机关","男");
        db.update("INSERT INTO driver_license VALUES(11,1,'军本','00123','B2','20200101','2028-01-01','吊销','',NULL)");
        db.update("INSERT INTO driver_license VALUES(12,1,'地方本','00000456','C1','2018-02-03','2024-01-01','有效','2026-01-01','车管所')");
        db.update("INSERT INTO driver_license VALUES(21,2,'军本','00008888','B2','2020-04-01','2030-04-01','有效','','部队车管所')");
        db.update("INSERT INTO driver_license VALUES(22,2,'军本','00008888','B2','2020-04-01','2030-04-01','有效','','部队车管所')");
        db.update("INSERT INTO driver_license VALUES(41,4,'地方本','00004004','C1','2010-01-01','2020-01-01','过期','','车管所')");
        db.update("INSERT INTO driver_license VALUES(51,5,'军本','00005005','B2','日期待核','','吊销','','')");
        db.update("INSERT INTO driver_license VALUES(61,6,'临时证','00006006','C1','','','','','')");
        db.update("INSERT INTO driver_license VALUES(99,999,'军本','OUTSIDE-ORPHAN','B2','','','有效','','')");
        return db;
    }

    static PersonnelService people(JdbcTemplate db) {
        return new PersonnelService(new PersonnelDao(db),mock(PhotoService.class),new DepartmentDao(db),db);
    }

    static DepartmentService departments(JdbcTemplate db) {
        return new DepartmentService(new DepartmentDao(db),new PersonnelDao(db));
    }

    @BeforeEach void setup() { DataScope.clear(); jdbc=database(temp); dao=new PersonnelDao(jdbc); }
    @AfterEach void clear() { DataScope.clear(); }

    private List<Personnel> query(DriverLicenseFilter filter,int page,int size,Long department,boolean descendants,String location) {
        return dao.query(null,null,null,department,null,null,null,null,null,null,null,null,null,null,null,null,null,location,null,null,page,size,descendants,PersonnelExtraFilters.of(filter,null));
    }
    private List<Long> selected(String has,String type,String status) {
        return query(DriverLicenseFilter.of(has,type,status),1,100,null,false,null).stream().map(Personnel::getId).sorted().collect(Collectors.toList());
    }
    private long count(DriverLicenseFilter filter) {
        return dao.count(null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,false,PersonnelExtraFilters.of(filter,null));
    }

    @Test void existenceCountsPeopleNotRecordsAndIncludesExpiredRevokedAndBlankStatus() {
        assertEquals(Arrays.asList(1L,2L,4L,5L,6L),selected("yes",null,null));
        assertEquals(Arrays.asList(3L,7L),selected("no",null,null));
        assertEquals(5,count(DriverLicenseFilter.of("yes",null,null)));
        assertEquals(2,count(DriverLicenseFilter.of("no",null,null)));
        assertEquals(1,query(DriverLicenseFilter.of("yes",null,null),1,1,null,false,null).size());
        assertEquals(1,query(DriverLicenseFilter.of("yes",null,null),5,1,null,false,null).size());
        assertEquals(0,query(DriverLicenseFilter.of("yes",null,null),6,1,null,false,null).size());
    }

    @Test void typeAndStatusMustMatchTheSameLicense() {
        assertEquals(Collections.singletonList(2L),selected("yes","军本","有效"));
        assertEquals(Collections.singletonList(1L),selected(null,"地方本","有效"));
        assertEquals(Arrays.asList(1L,5L),selected(null,"军本","吊销"));
        assertEquals(Collections.singletonList(4L),selected(null,null,"过期"));
        assertEquals(Collections.singletonList(6L),selected(null,"临时证",null));
        assertTrue(selected("no","军本",null).isEmpty());
        assertTrue(selected("yes","不存在的类型",null).isEmpty());
    }

    @Test void filtersRemainBoundAndInvalidPresenceIsRejected() {
        assertThrows(IllegalArgumentException.class,()->DriverLicenseFilter.of("all",null,null));
        assertThrows(IllegalArgumentException.class,()->DriverLicenseFilter.of("true",null,null));
        assertEquals(5,count(DriverLicenseFilter.fromQuery(Collections.singletonMap("driverLicense"," yes "))));
        String hostile="军本' OR 1=1 --";
        assertTrue(selected(null,hostile,null).isEmpty());
        StringBuilder sql=new StringBuilder(); List<Object> args=new ArrayList<>();
        DriverLicenseFilter.of(null,hostile,"有效").appendTo(sql,args);
        assertFalse(sql.toString().contains(hostile));
        assertEquals(Arrays.asList(hostile,"有效"),args);
    }

    @Test void scopeAndOtherPersonnelFiltersIntersectWithoutExpandingRows() {
        DataScope.set(new DataScope.Context(2L,"child",false,new HashSet<>(Arrays.asList(1L,2L))));
        assertEquals(Arrays.asList(1L,2L),selected("yes",null,null));
        assertEquals(Collections.singletonList(3L),selected("no",null,null));
        assertEquals(Collections.singletonList(1L),query(DriverLicenseFilter.of("yes",null,null),1,100,1L,false,null).stream().map(Personnel::getId).collect(Collectors.toList()));
        assertEquals(2,query(DriverLicenseFilter.of("yes",null,null),1,100,1L,true,null).size());
        assertEquals(Collections.singletonList(2L),query(DriverLicenseFilter.of("yes",null,null),1,100,null,false,"营区").stream().map(Personnel::getId).collect(Collectors.toList()));
        DataScope.set(new DataScope.Context(2L,"empty",false,Collections.emptySet()));
        assertEquals(0,count(DriverLicenseFilter.of("yes",null,null)));
        assertEquals(0,count(DriverLicenseFilter.of("no",null,null)));
    }

    @Test void retainMatchingIsLazyPreservesOrderAndRejectsUnscopedCandidates() {
        JdbcTemplate unused=mock(JdbcTemplate.class);
        List<Personnel> candidates=Arrays.asList(person(4,3),person(3,1),person(1,1),person(2,2));
        assertSame(candidates,DriverLicenseFilter.of(null,null,null).retainMatching(unused,candidates));
        assertTrue(DriverLicenseFilter.of("yes",null,null).retainMatching(unused,Collections.emptyList()).isEmpty());
        verifyNoInteractions(unused);
        DataScope.set(new DataScope.Context(2L,"child",false,new HashSet<>(Arrays.asList(1L,2L))));
        assertEquals(Arrays.asList(1L,2L),DriverLicenseFilter.of("yes",null,null).retainMatching(jdbc,candidates).stream().map(Personnel::getId).collect(Collectors.toList()));
    }

    @SuppressWarnings("unchecked")
    @Test void dashboardTwoBucketsEqualPersonnelFiltersForRootChildAndEmptyScopes() {
        StatsService stats=new StatsService(jdbc,dao);
        for (int mode=0; mode<3; mode++) {
            if (mode==1) DataScope.set(new DataScope.Context(2L,"child",false,new HashSet<>(Arrays.asList(1L,2L))));
            if (mode==2) DataScope.set(new DataScope.Context(2L,"empty",false,Collections.emptySet()));
            Map<String,Object> dashboard=stats.dashboard();
            List<StatsSummary.NameCount> licenses=(List<StatsSummary.NameCount>)((Map<?,?>)dashboard.get("groups")).get("driverLicense");
            assertEquals(2,licenses.size());
            assertEquals("有驾驶证",licenses.get(0).getName());
            assertEquals("无驾驶证",licenses.get(1).getName());
            assertEquals(count(DriverLicenseFilter.of("yes",null,null)),licenses.get(0).getCount());
            assertEquals(count(DriverLicenseFilter.of("no",null,null)),licenses.get(1).getCount());
            assertEquals(((Number)dashboard.get("total")).longValue(),licenses.stream().mapToLong(StatsSummary.NameCount::getCount).sum());
        }
    }

    @Test void personnelControllerBindsNewFiltersAndUsesTheSameTotal() throws Exception {
        PersonnelService service=people(jdbc); DepartmentService departments=departments(jdbc);
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new PersonnelController(service,new DepartmentDao(jdbc),jdbc,
                new RollCallExcelService(service,departments,jdbc),new RosterExcelService(service,departments,jdbc)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/personnel").param("driverLicense","yes").param("driverLicenseType","军本").param("driverLicenseStatus","有效").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.list[0].id").value(2));
        mvc.perform(get("/api/personnel").param("driverLicense","wrong")).andExpect(status().isBadRequest());
    }

    private static Personnel person(long id,long department) { Personnel p=new Personnel();p.setId(id);p.setDepartmentId(department);return p; }
}
