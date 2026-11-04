package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.auth.ForbiddenException;
import com.szjx.personnel.server.controller.DashboardController;
import com.szjx.personnel.server.dao.DepartmentDao;
import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.model.Department;
import com.szjx.personnel.server.model.StatsSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

class DepartmentStatsScopeTest {
    @TempDir Path temp;
    private JdbcTemplate jdbc;
    private DepartmentDao departments;
    private DepartmentService service;
    private StatsService stats;

    @BeforeEach void setup() {
        DataScope.clear();
        jdbc = PersonnelIntegrityTest.temporaryDatabase(temp);
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY, name TEXT, parent_id INTEGER DEFAULT 0, sort INTEGER DEFAULT 0, remark TEXT DEFAULT '', leader_id INTEGER DEFAULT 0)");
        jdbc.execute("CREATE TABLE account_department(user_id INTEGER,department_id INTEGER)");
        jdbc.execute("CREATE TABLE driver_license(id INTEGER PRIMARY KEY,personnel_id INTEGER,license_type TEXT DEFAULT '',license_status TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY, name TEXT, department_id INTEGER, actual_department_id INTEGER DEFAULT 0, gender TEXT DEFAULT '男', status TEXT DEFAULT '在职', category TEXT DEFAULT '', rank TEXT DEFAULT '', education TEXT DEFAULT '', political_status TEXT DEFAULT '', native_place TEXT DEFAULT '', enlist_place TEXT DEFAULT '', entry_date TEXT DEFAULT '2026-01-01', birthday TEXT DEFAULT '', party_full_date TEXT DEFAULT '', location_point TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE equipment(id INTEGER PRIMARY KEY,name TEXT, keeper_id INTEGER, status TEXT DEFAULT '维修',category TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE material(id INTEGER PRIMARY KEY,name TEXT, keeper_id INTEGER, quantity REAL DEFAULT 0,min_quantity REAL DEFAULT 1,category TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE vehicle(id INTEGER PRIMARY KEY,plate_no TEXT, driver_id INTEGER, status TEXT DEFAULT '维修', type TEXT DEFAULT '',brand TEXT DEFAULT '',insurance_expiry TEXT DEFAULT '',inspection_expiry TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE leave_record(id INTEGER PRIMARY KEY,personnel_id INTEGER, start_date TEXT,end_date TEXT,leave_type TEXT DEFAULT '年假')");
        jdbc.execute("CREATE TABLE leave_plan(id INTEGER PRIMARY KEY,personnel_id INTEGER, start_date TEXT,end_date TEXT,leave_type TEXT DEFAULT '年假',status TEXT DEFAULT '计划中')");
        jdbc.execute("CREATE TABLE item_loan(id INTEGER PRIMARY KEY,item_name TEXT, borrower_name TEXT, expected_return_date TEXT DEFAULT '', returned INTEGER DEFAULT 0, owner_user_id INTEGER)");
        // 借还提醒的账号可见性递归查询依赖 admin_user 表
        jdbc.execute("CREATE TABLE admin_user(id INTEGER PRIMARY KEY, parent_id INTEGER DEFAULT 0)");
        jdbc.execute("CREATE TABLE fitness_record(id INTEGER PRIMARY KEY,personnel_id INTEGER,test_date TEXT,grade TEXT DEFAULT '良好')");
        jdbc.execute("CREATE TABLE double_excellence_record(id INTEGER PRIMARY KEY,personnel_id INTEGER,year INTEGER,quarter INTEGER,rating TEXT DEFAULT '',rating_date TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE annual_appraisal_record(id INTEGER PRIMARY KEY,personnel_id INTEGER,year INTEGER,rating TEXT DEFAULT '',rating_date TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE absence_record(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER, reason TEXT, leave_date TEXT, expected_return_date TEXT DEFAULT '', actual_return_date TEXT DEFAULT '')");
        jdbc.update("INSERT INTO department(id,name,parent_id,leader_id) VALUES(1,'A单位',0,0),(2,'B单位',1,3),(3,'B子单位',2,0),(4,'C单位',1,0)");
        jdbc.update("INSERT INTO personnel(id,name,department_id) VALUES(1,'B人员',2),(2,'B下级人员',3),(3,'C人员',4),(4,'未分配人员',0)");
        for (int i = 1; i <= 4; i++) {
            jdbc.update("INSERT INTO equipment(id,name,keeper_id) VALUES(?,?,?)",i,"装备"+i,i==4?null:i);
            jdbc.update("INSERT INTO material(id,name,keeper_id) VALUES(?,?,?)",i,"物资"+i,i==4?null:i);
            jdbc.update("INSERT INTO vehicle(id,plate_no,driver_id) VALUES(?,?,?)",i,"车辆"+i,i==4?null:i);
            String start = LocalDate.now().plusDays(1).toString();
            String end = LocalDate.now().plusDays(2).toString();
            jdbc.update("INSERT INTO leave_record(id,personnel_id,start_date,end_date) VALUES(?,?,?,?)",i,i,start,end);
            jdbc.update("INSERT INTO leave_plan(id,personnel_id,start_date,end_date) VALUES(?,?,?,?)",i,i,start,end);
            jdbc.update("INSERT INTO fitness_record(id,personnel_id,test_date) VALUES(?,?,?)",i,i,start);
        }
        departments = new DepartmentDao(jdbc);
        PersonnelDao people = new PersonnelDao(jdbc);
        service = new DepartmentService(departments, people);
        stats = new StatsService(jdbc, people);
    }

    @AfterEach void clear() { DataScope.clear(); }
    private void asChild() {
        DataScope.set(new DataScope.Context(2L,"B",false,new HashSet<Long>(Arrays.asList(2L,3L))));
    }

    @Test void departmentTreeContainsOnlyAuthorizedSubtreeAndHidesExternalLeader() {
        asChild();
        List<Department> roots = service.tree();
        assertEquals(1,roots.size());
        Department root = roots.get(0);
        assertEquals(2L,root.getId());
        assertEquals(2L,root.getTotal());
        assertEquals(1,root.getChildren().size());
        assertEquals(3L,root.getChildren().get(0).getId());
        assertEquals(0L,root.getLeaderId());
        assertNull(root.getLeaderName());
        assertNull(departments.findById(4L));
    }

    @Test void childCanCreateWithinScopeButCannotCreateRootMoveOrDeleteSibling() {
        asChild();
        Department d = new Department(); d.setName("B新子单位"); d.setParentId(2L);
        assertNotNull(service.create(d).getId());
        d.setParentId(0L);
        assertThrows(ForbiddenException.class,()->service.create(d));
        d.setParentId(4L);
        assertThrows(ForbiddenException.class,()->service.create(d));
        d.setParentId(3L);
        assertThrows(ForbiddenException.class,()->service.update(2L,d));
        assertThrows(ForbiddenException.class,()->service.delete(4L));
    }

    @Test void authorizedRootCanBeRenamedWithoutAccessToItsParent() {
        asChild();
        Department d = new Department(); d.setName("B单位改名"); d.setParentId(0L);
        Department result = service.update(2L,d);
        assertEquals(1L,result.getParentId());
        assertEquals("B单位改名",result.getName());
    }

    @Test void assignedEmptyDepartmentCannotBeDeleted() {
        jdbc.update("INSERT INTO department(id,name,parent_id) VALUES(5,'已授权空单位',2)");
        jdbc.update("INSERT INTO account_department(user_id,department_id) VALUES(9,5)");
        assertThrows(IllegalArgumentException.class,()->service.delete(5L));
        assertNotNull(departments.findById(5L));
    }

    @Test void childSummaryAndEveryDashboardGroupAreRestrictedBeforeAggregation() {
        asChild();
        StatsSummary summary = stats.summary();
        assertEquals(2L,summary.getTotal());
        assertEquals(2,summary.getDepartment().size());
        Map<String,Object> dashboard = stats.dashboard();
        for (String key : new String[]{"total","onDuty","deptCount","vehicleCount","equipmentTotal","equipmentRepair","materialTotal","materialLowStock","leaveThisYear","fitnessThisYear","vehicleRepair","materialLow"}) {
            assertEquals(2L,((Number)dashboard.get(key)).longValue(),key);
        }
        @SuppressWarnings("unchecked") Map<String,List<StatsSummary.NameCount>> groups = (Map<String,List<StatsSummary.NameCount>>)dashboard.get("groups");
        for (Map.Entry<String,List<StatsSummary.NameCount>> group : groups.entrySet()) {
            if ("department".equals(group.getKey())) continue; // 层级累计值不能相加作为人员总数。
            if ("actualDuty".equals(group.getKey())) continue; // 实际在岗分布只含有实际单位的人员,与 department 一样不可加总。
            long total = 0;
            for (StatsSummary.NameCount row : group.getValue()) total += row.getCount();
            assertEquals(2L,total,group.getKey());
        }
    }

    @Test void emptyScopeReturnsNoCountsNoDepartmentsAndNoEvents() {
        DataScope.set(new DataScope.Context(2L,"B",false,Collections.<Long>emptySet()));
        assertTrue(service.tree().isEmpty());
        assertEquals(0L,stats.summary().getTotal());
        assertEquals(0L,((Number)stats.dashboard().get("equipmentTotal")).longValue());
        assertTrue(((List<?>)((Map<?,?>)new DashboardController(jdbc, new com.szjx.personnel.server.dao.ItemLoanDao(jdbc)).events().getData()).get("list")).isEmpty());
    }

    @Test void eventListsExcludeSiblingsAndUnassignedAssets() {
        asChild();
        String events = String.valueOf(new DashboardController(jdbc, new com.szjx.personnel.server.dao.ItemLoanDao(jdbc)).events().getData());
        assertTrue(events.contains("B人员"));
        assertTrue(events.contains("B下级人员"));
        assertFalse(events.contains("C人员"));
        assertFalse(events.contains("未分配人员"));
        assertFalse(events.contains("装备3"));
        assertFalse(events.contains("装备4"));
        assertFalse(events.contains("物资3"));
        assertFalse(events.contains("物资4"));
    }

    @SuppressWarnings("unchecked")
    private List<StatsService.DepartmentCount> departmentRows() {
        return (List<StatsService.DepartmentCount>) ((Map<?, ?>) stats.dashboard().get("groups")).get("department");
    }

    @Test void departmentHierarchyCountsChildrenAndPreservesTreeSortNotPopulationOrder() {
        jdbc.update("DELETE FROM personnel");
        jdbc.update("DELETE FROM department");
        jdbc.update("INSERT INTO department(id,name,parent_id,sort) VALUES(10,'XX营',0,0),(20,'XX连',10,0),(30,'C排',20,0),(40,'A班',30,2),(50,'B班',30,1)");
        for (int i = 1; i <= 20; i++) {
            jdbc.update("INSERT INTO personnel(id,name,department_id) VALUES(?,?,?)", i, "人员" + i, i <= 10 ? 40 : 50);
        }
        List<StatsService.DepartmentCount> rows = departmentRows();
        assertEquals(Arrays.asList(10L,20L,30L,50L,40L), rows.stream().map(StatsService.DepartmentCount::getId).collect(java.util.stream.Collectors.toList()));
        assertEquals(Arrays.asList(20L,20L,20L,10L,10L), rows.stream().map(StatsService.DepartmentCount::getCount).collect(java.util.stream.Collectors.toList()));
        assertEquals(0, rows.get(0).getDepth());
        assertEquals(3, rows.get(4).getDepth());
        assertEquals(0L, rows.get(2).getDirectCount());
        assertEquals("XX营XX连C排A班", rows.get(4).getPath());
        jdbc.update("INSERT INTO personnel(id,name,department_id) VALUES(21,'排直属',30)");
        rows = departmentRows();
        assertEquals(21L, rows.get(2).getCount());
        assertEquals(1L, rows.get(2).getDirectCount());
        assertEquals(10L, rows.get(3).getCount());
        assertEquals(10L, rows.get(4).getCount());
    }

    @Test void dashboardHierarchyHasNoTopFifteenTruncationAndKeepsEmptyDepartments() {
        for (int i = 10; i < 30; i++) jdbc.update("INSERT INTO department(id,name,parent_id,sort) VALUES(?,?,0,?)", i, "空单位" + i, i);
        List<StatsService.DepartmentCount> rows = departmentRows();
        assertEquals(25, rows.size()); // 24 个单位及一项未分配。
        assertEquals(0L, rows.stream().filter(row -> row.getId() == 29L).findFirst().get().getCount());
        assertEquals(0L, rows.get(rows.size() - 1).getId());
    }

    @Test void sameNamedDepartmentsStaySeparateAndRestrictedCountsDoNotLeak() {
        jdbc.update("UPDATE department SET name='同名班' WHERE id IN(2,4)");
        List<StatsService.DepartmentCount> all = departmentRows();
        StatsService.DepartmentCount a = all.stream().filter(row -> row.getId() == 2L).findFirst().get();
        StatsService.DepartmentCount b = all.stream().filter(row -> row.getId() == 4L).findFirst().get();
        assertEquals(2L, a.getCount());
        assertEquals(1L, b.getCount());
        asChild();
        List<StatsService.DepartmentCount> visible = departmentRows();
        assertEquals(Arrays.asList(2L,3L), visible.stream().map(StatsService.DepartmentCount::getId).collect(java.util.stream.Collectors.toList()));
        assertEquals(2L, visible.get(0).getCount());
        assertEquals(1L, visible.get(1).getCount());
        assertEquals(1, visible.get(0).getDepth());
    }

    @Test void pointCountsIncludeEmptyValuesAndRespectScope() {
        jdbc.update("UPDATE personnel SET location_point='南营区' WHERE id IN(1,2)");
        jdbc.update("UPDATE personnel SET location_point='北营区' WHERE id=3");
        @SuppressWarnings("unchecked") List<StatsSummary.NameCount> points = (List<StatsSummary.NameCount>) ((Map<?, ?>) stats.dashboard().get("groups")).get("locationPoint");
        assertEquals(3, points.size());
        assertEquals("南营区", points.get(0).getName());
        assertEquals(2L, points.get(0).getCount());
        assertTrue(points.stream().anyMatch(row -> "未填写".equals(row.getName()) && row.getCount() == 1));
        asChild();
        @SuppressWarnings("unchecked") List<StatsSummary.NameCount> visible = (List<StatsSummary.NameCount>) ((Map<?, ?>) stats.dashboard().get("groups")).get("locationPoint");
        assertEquals(1, visible.size());
        assertEquals("南营区", visible.get(0).getName());
        assertEquals(2L, visible.get(0).getCount());
    }

    @Test void orphanAndUnassignedPeopleShareAnExplicitNonDepartmentGroup() {
        jdbc.update("INSERT INTO personnel(id,name,department_id) VALUES(5,'失联单位人员',999),(6,'空单位人员',NULL)");
        StatsService.DepartmentCount unassigned = departmentRows().stream().filter(row -> row.getId() == 0L).findFirst().get();
        assertEquals(3L, unassigned.getCount());
        assertEquals(3L, unassigned.getDirectCount());
        asChild();
        assertFalse(departmentRows().stream().anyMatch(row -> row.getId() == 0L));
    }

    @Test void rootRetainsGlobalCountsAndCanCreateTopLevelDepartments() {
        DataScope.set(new DataScope.Context(1L,"A",true,Collections.<Long>emptySet()));
        assertEquals(4L,stats.summary().getTotal());
        assertEquals(4L,((Number)stats.dashboard().get("equipmentTotal")).longValue());
        Department d = new Department(); d.setName("顶级单位");
        assertNotNull(service.create(d).getId());
    }
}
