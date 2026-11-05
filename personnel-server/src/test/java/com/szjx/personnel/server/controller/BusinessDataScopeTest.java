package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.auth.ForbiddenException;
import com.szjx.personnel.server.config.GlobalExceptionHandler;
import com.szjx.personnel.server.dao.*;
import com.szjx.personnel.server.model.*;
import com.szjx.personnel.server.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Isolated SQLite and actual DAOs/controllers: no application startup, real data or timers. */
class BusinessDataScopeTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;
    PersonnelDao people;
    PhotoService photos;
    PersonnelService personnel;
    EquipmentDao equipment;
    MaterialDao material;
    VehicleDao vehicle;
    LeaveDao leave;
    FitnessDao fitness;
    MilitaryAssessmentDao military;
    SubRecordDao sub;
    FitnessService fitnessService;
    EquipmentService equipmentService;
    MaterialService materialService;
    VehicleService vehicleService;

    @BeforeEach void setup() {
        DataScope.clear();
        jdbc = PersonnelIntegrityTest.temporaryDatabase(temp);
        PersonnelIntegrityTest.personnelSchema(jdbc);
        DoubleExcellenceServiceTest.createSchema(jdbc);
        jdbc.update("INSERT INTO department(id,name,parent_id) VALUES(2,'子单位',1),(3,'外单位',0)");
        jdbc.execute("DROP TABLE phone_device");
        jdbc.execute("CREATE TABLE phone_device(id INTEGER PRIMARY KEY AUTOINCREMENT,personnel_id INTEGER,device_type TEXT,brand TEXT,model TEXT,serial_no TEXT,imei1 TEXT,imei2 TEXT,phone_number TEXT,purchase_date TEXT,status TEXT,remark TEXT,created_at TEXT,updated_at TEXT)");
        schema("equipment", new Equipment(), "keeperName", "photoBase64");
        schema("material", new Material(), "keeperName", "photoBase64");
        schema("vehicle", new Vehicle(), "driverName", "photoBase64");
        schema("leave_record", new LeaveRecord(), "personnelName");
        schema("fitness_record", new FitnessRecord(), "personnelName", "idCard", "birthday");
        schema("military_assessment", new MilitaryAssessment(), "personnelName", "idCard");
        jdbc.execute("CREATE TABLE leave_plan(id INTEGER PRIMARY KEY AUTOINCREMENT,personnel_id INTEGER,year TEXT,leave_type TEXT,start_date TEXT,end_date TEXT,days REAL,remark TEXT,created_at TEXT,updated_at TEXT)");
        for (String table : new String[]{"position_history", "reward_punish", "child_info", "family_member", "driver_license", "personal_card", "private_vehicle", "housing_info"}) {
            jdbc.execute("CREATE TABLE " + table + "(id INTEGER PRIMARY KEY,personnel_id INTEGER,remark TEXT,start_date TEXT,occur_date TEXT,birth_date TEXT,updated_at TEXT)");
        }
        people = new PersonnelDao(jdbc);
        for (int i = 1; i <= 4; i++) {
            Personnel p = new Personnel(); p.setName(i == 3 ? "外部隐私姓名" : "人员" + i);
            p.setIdCard("CARD" + i); p.setDepartmentId(i == 4 ? 0L : (long) i);
            p.setBirthday("1990-01-01"); p.setStatus("在职"); p.setPhoto("photo" + i + ".jpg");
            people.insert(p);
        }
        for (String table : new String[]{"equipment", "material", "vehicle", "phone_device", "leave_record", "leave_plan", "fitness_record", "military_assessment", "position_history", "reward_punish", "child_info", "family_member", "driver_license", "personal_card", "private_vehicle", "housing_info"}) {
            String owner = "vehicle".equals(table) ? "driver_id" : ("equipment".equals(table) || "material".equals(table)) ? "keeper_id" : "personnel_id";
            for (int i = 1; i <= 5; i++) {
                jdbc.update("INSERT INTO " + table + "(id," + owner + ") VALUES(?,?)", i, i == 5 ? 0 : i);
            }
        }
        photos = mock(PhotoService.class);
        personnel = new PersonnelService(people, photos, new DepartmentDao(jdbc), jdbc);
        equipment = new EquipmentDao(jdbc); material = new MaterialDao(jdbc); vehicle = new VehicleDao(jdbc);
        leave = new LeaveDao(jdbc); fitness = new FitnessDao(jdbc); military = new MilitaryAssessmentDao(jdbc); sub = new SubRecordDao(jdbc);
        fitnessService = new FitnessService(fitness, people, jdbc, mock(FitnessScoringService.class));
        equipmentService = new EquipmentService(equipment, people, photos);
        materialService = new MaterialService(material, people, photos);
        vehicleService = new VehicleService(vehicle, people, photos);
        scoped(1L, 2L);
    }

    @AfterEach void clearScope() { DataScope.clear(); }

    private void schema(String table, Object model, String... excluded) {
        BeanWrapper bean = new BeanWrapperImpl(model);
        List<String> skip = Arrays.asList(excluded);
        StringBuilder ddl = new StringBuilder("CREATE TABLE " + table + "(id INTEGER PRIMARY KEY AUTOINCREMENT");
        for (java.beans.PropertyDescriptor p : bean.getPropertyDescriptors()) {
            if (p.getWriteMethod() == null || "id".equals(p.getName()) || skip.contains(p.getName())) continue;
            String col = p.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
            ddl.append(',').append(col).append(p.getPropertyType() == Long.class || p.getPropertyType() == Integer.class ? " INTEGER DEFAULT 0" : p.getPropertyType() == Double.class ? " REAL" : " TEXT DEFAULT ''");
        }
        jdbc.execute(ddl.append(')').toString());
    }

    private void scoped(Long... ids) {
        DataScope.set(new DataScope.Context(9L, "sub-account", false, new LinkedHashSet<Long>(Arrays.asList(ids))));
    }

    private PersonnelController personnelController() { return new PersonnelController(personnel, new DepartmentDao(jdbc), jdbc, null, null); }
    private LeaveController leaveController() { return new LeaveController(new LeaveService(leave, people, mock(LeaveStatusSyncService.class), jdbc), jdbc); }
    private MilitaryAssessmentController militaryController() { return new MilitaryAssessmentController(military); }

    /** All query routes have trailing page/size, so use reflection only to omit unrelated optional filters. */
    private ApiResponse page(Object controller, int page, int size) throws Exception {
        for (Method m : controller.getClass().getMethods()) {
            if (!"query".equals(m.getName())) continue;
            Object[] args = new Object[m.getParameterCount()]; args[args.length - 2] = page; args[args.length - 1] = size;
            return (ApiResponse) m.invoke(controller, args);
        }
        throw new AssertionError("Missing query route");
    }

    private void assertPage(ApiResponse response, long total, int size) {
        Object data = response.getData();
        if (data instanceof PageResult) {
            PageResult<?> p = (PageResult<?>) data; assertEquals(total, p.getTotal()); assertEquals(size, p.getList().size());
        } else {
            Map<?, ?> p = (Map<?, ?>) data; assertEquals(total, ((Number) p.get("total")).longValue()); assertEquals(size, ((List<?>) p.get("list")).size());
        }
        if (DataScope.restricted()) assertFalse(String.valueOf(data).contains("外部隐私姓名"));
    }

    @Test void allPagedControllersFilterBeforeLimitAndCountAndEmptyScopeDeniesAll() throws Exception {
        Object[] controllers = {personnelController(), new EquipmentController(equipmentService), new MaterialController(materialService), new VehicleController(vehicleService), new PhoneDeviceController(new PhoneDeviceDao(jdbc)), leaveController(), new LeavePlanController(new LeavePlanDao(jdbc)), new FitnessController(fitnessService), militaryController()};
        for (Object c : controllers) {
            assertPage(page(c, 1, 1), 2, 1); assertPage(page(c, 2, 1), 2, 1); assertPage(page(c, 3, 1), 2, 0);
        }
        scoped();
        for (Object c : controllers) assertPage(page(c, 1, 20), 0, 0);
        DataScope.set(new DataScope.Context(1L, "admin", true, Collections.<Long>emptySet()));
        for (Object c : controllers) assertPage(page(c, 1, 20), c instanceof PersonnelController ? 4 : 5, c instanceof PersonnelController ? 4 : 5);
    }

    @Test void doubleExcellenceHttpScopesRowsBeforePaginationAndAggregatesAllAuthorizedPeople() throws Exception {
        DoubleExcellenceService service = new DoubleExcellenceService(jdbc);
        DataScope.clear();
        for (long personId : new long[]{1,2,3}) {
            DoubleExcellenceRecord r = new DoubleExcellenceRecord(); r.setPersonnelId(personId);
            r.setYear(2026); r.setQuarter(1); r.setRating("好"); r.setRatingDate("2026-10-18");
            service.create(r);
        }
        scoped(1L,2L);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DoubleExcellenceController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/double-excellence").param("size","1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.list.length()").value(1));
        mvc.perform(get("/api/double-excellence/stats").param("year","2026").param("size","1"))
                .andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.quarters[0].good").value(2))
                .andExpect(jsonPath("$.data.quarters[1].unrated").value(2));
        mvc.perform(get("/api/double-excellence/3")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/double-excellence/3")).andExpect(status().isForbidden());
        scoped();
        mvc.perform(get("/api/double-excellence")).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/double-excellence/stats")).andExpect(jsonPath("$.data.total").value(0));
    }

    @Test void daoListsCountsOptionsAndIdentityLookupsAreScopedButDuplicateChecksStayGlobal() {
        assertEquals(2, people.countAll()); assertEquals(0, people.countByDepartment(3L));
        assertEquals(2, people.findAllSimple().size()); assertEquals(2, personnel.findAllOptions().size());
        assertNull(people.findByIdCard(" card3 ")); assertTrue(people.existsOtherIdCard("card3", null));
        assertNull(personnelController().findByIdCard("card3").getData());
        assertTrue(((List<?>) leaveController().findPerson("CARD3").getData()).isEmpty());
        assertEquals(2, equipment.count(null, null, null, null, null, null, null, null, null)); assertEquals(2, equipment.query(null, null, null, null, null, null, null, null, null, 1, 20).size());
        assertEquals(2, material.count(null, null, null, null, null)); assertEquals(2, material.query(null, null, null, null, null, 1, 20).size());
        assertEquals(2, vehicle.count(null, null, null, null, null, null, null)); assertEquals(2, vehicle.query(null, null, null, null, null, null, null, 1, 20).size());
        assertEquals(2, leave.count(null)); assertEquals(2, leave.query(null, 1, 20).size());
        assertEquals(2, fitness.count(null, null)); assertEquals(2, fitness.query(null, null, 1, 20).size());
        assertEquals(2, military.count(null, null, null, null, null, null)); assertEquals(2, military.query(null, null, null, null, null, null, 1, 20).size());
        jdbc.update("UPDATE equipment SET category='枪支',code='GLOBAL-CODE' WHERE id=3");
        assertTrue(equipment.existsGunCode("global-code", null));
    }

    @Test void personnelPathHelpersExposeOnlyAuthorizedPeopleButIncludeAllAncestorNames() throws Exception {
        jdbc.update("UPDATE department SET name='总队',parent_id=0 WHERE id=1");
        jdbc.update("INSERT INTO department(id,name,parent_id) VALUES(5,'一班',2),(6,'一班',3)");
        jdbc.update("INSERT INTO personnel(id,name,id_card,military_id_no,department_id) VALUES(5,'授权人员','PATH5','000军证5',5),(6,'越权同名单位人员','PATH6','000军证6',6)");
        scoped(5L);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(personnelController()).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/personnel/options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(5))
                .andExpect(jsonPath("$.data[0].departmentName").value("一班"))
                .andExpect(jsonPath("$.data[0].departmentPath").value("总队子单位一班"))
                .andExpect(jsonPath("$.data[0].idCard").value("PATH5"))
                .andExpect(jsonPath("$.data[0].militaryIdNo").doesNotExist());
        mvc.perform(get("/api/personnel/by-idcard").param("idCard", " path5 "))
                .andExpect(jsonPath("$.data.departmentPath").value("总队子单位一班"))
                .andExpect(jsonPath("$.data.departmentName").value("一班"))
                .andExpect(jsonPath("$.data.militaryIdNo").value("000军证5"));
        for (String card : Arrays.asList("PATH6", "CARD1", "CARD2", "CARD3")) {
            mvc.perform(get("/api/personnel/by-idcard").param("idCard", card))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
        mvc.perform(get("/api/personnel/5")).andExpect(jsonPath("$.data.departmentPath").value("总队子单位一班"));
        mvc.perform(get("/api/personnel/6")).andExpect(status().isForbidden());
        scoped();
        mvc.perform(get("/api/personnel/options")).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test void explicitDescendantPersonnelFilterIntersectsScopeWithoutChangingDefaultDirectFilter() throws Exception {
        jdbc.update("INSERT INTO department(id,name,parent_id) VALUES(5,'孙单位',2)");
        jdbc.update("INSERT INTO personnel(id,name,id_card,department_id) VALUES(5,'孙单位人员','CARD5',5)");
        scoped(1L, 2L, 5L);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(personnelController()).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/personnel").param("departmentId", "1"))
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.list[0].id").value(1));
        mvc.perform(get("/api/personnel").param("departmentId", "1").param("includeDescendants", "true"))
                .andExpect(jsonPath("$.data.total").value(3)).andExpect(jsonPath("$.data.list.length()").value(3));
        scoped(2L, 5L); // Parent metadata may describe a selection; it never grants access to its personnel.
        mvc.perform(get("/api/personnel").param("departmentId", "1"))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/personnel").param("departmentId", "1").param("includeDescendants", "true").param("size", "1"))
                .andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.list.length()").value(1));
        for (String department : Arrays.asList("3", "999")) {
            mvc.perform(get("/api/personnel").param("departmentId", department).param("includeDescendants", "true"))
                    .andExpect(jsonPath("$.data.total").value(0)).andExpect(jsonPath("$.data.list.length()").value(0));
        }
        scoped();
        mvc.perform(get("/api/personnel").param("departmentId", "1").param("includeDescendants", "true"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test void explicitOutOfScopeFiltersCannotWidenResults() {
        assertPage(leaveController().query(null, null, null, null, null, null, null, null, 3L, 1, 20), 0, 0);
        assertPage(new LeavePlanController(new LeavePlanDao(jdbc)).query(null, null, null, null, null, null, null, 3L, 1, 20), 0, 0);
        assertPage(new PhoneDeviceController(new PhoneDeviceDao(jdbc)).query(null, null, null, null, null, 3L, 1, 20), 0, 0);
        assertEquals(0, fitnessService.countFiltered2(null, null, null, null, null, null, 3L, null, null, 3L));
        assertTrue(fitnessService.queryFiltered2(null, null, null, null, null, null, 3L, null, null, 1, 20, 3L).isEmpty());
    }

    @Test void personnelDetailsHelpersAndPhotosRejectExternalOrDepartmentlessPeopleBeforeFiles() {
        for (Long id : Arrays.asList(3L, 4L, 999L)) {
            assertThrows(ForbiddenException.class, () -> people.findById(id));
            assertThrows(ForbiddenException.class, () -> people.devicePhone(id));
            assertThrows(ForbiddenException.class, () -> personnelController().getAge(id, null));
            assertThrows(ForbiddenException.class, () -> personnelController().photo(id));
            assertThrows(ForbiddenException.class, () -> leaveController().checkTravel(id, "2026"));
            assertThrows(ForbiddenException.class, () -> leaveController().summary(id, "2026", null));
            assertThrows(ForbiddenException.class, () -> new PhoneDeviceController(new PhoneDeviceDao(jdbc)).byPerson(id));
        }
        verifyNoInteractions(photos);
    }

    @Test void assetOldAndNewOwnersAreGuardedAndNoPhotoSideEffectsOccur() {
        Equipment e = new Equipment(); e.setName("装备"); e.setKeeperId(1L); e.setPhotoBase64("/9j/");
        Material m = new Material(); m.setName("物资"); m.setKeeperId(1L); m.setPhotoBase64("/9j/");
        Vehicle v = new Vehicle(); v.setPlateNo("车牌"); v.setDriverId(1L); v.setPhotoBase64("/9j/");
        for (Long id : Arrays.asList(3L, 4L, 5L, 999L)) {
            assertThrows(ForbiddenException.class, () -> equipmentService.update(id, e));
            assertThrows(ForbiddenException.class, () -> materialService.update(id, m));
            assertThrows(ForbiddenException.class, () -> vehicleService.update(id, v));
            assertThrows(ForbiddenException.class, () -> equipmentService.delete(id));
            assertThrows(ForbiddenException.class, () -> materialService.delete(id));
            assertThrows(ForbiddenException.class, () -> vehicleService.delete(id));
            assertThrows(ForbiddenException.class, () -> new EquipmentController(equipmentService).photo(id));
            assertThrows(ForbiddenException.class, () -> new MaterialController(materialService).photo(id));
            assertThrows(ForbiddenException.class, () -> new VehicleController(vehicleService).photo(id));
        }
        for (Long owner : Arrays.asList(3L, 4L, 0L, null)) {
            e.setKeeperId(owner); m.setKeeperId(owner); v.setDriverId(owner);
            assertThrows(ForbiddenException.class, () -> equipmentService.create(e));
            assertThrows(ForbiddenException.class, () -> materialService.create(m));
            assertThrows(ForbiddenException.class, () -> vehicleService.create(v));
            assertThrows(ForbiddenException.class, () -> equipmentService.update(1L, e));
            assertThrows(ForbiddenException.class, () -> materialService.update(1L, m));
            assertThrows(ForbiddenException.class, () -> vehicleService.update(1L, v));
        }
        verifyNoInteractions(photos);
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM equipment", Integer.class));
    }

    @Test void assetsCanBeCreatedTransferredAndDeletedWithinExpandedSubtree() {
        Equipment e = new Equipment(); e.setName("范围内装备"); e.setKeeperId(2L);
        e = equipmentService.create(e); assertEquals(2L, e.getKeeperId()); e.setKeeperId(1L);
        assertEquals(1L, equipmentService.update(e.getId(), e).getKeeperId()); equipmentService.delete(e.getId());
        Material m = new Material(); m.setName("范围内物资"); m.setKeeperId(2L);
        m = materialService.create(m); assertEquals(2L, m.getKeeperId()); m.setKeeperId(1L);
        assertEquals(1L, materialService.update(m.getId(), m).getKeeperId()); materialService.delete(m.getId());
        Vehicle v = new Vehicle(); v.setPlateNo("范围内车辆"); v.setDriverId(2L);
        v = vehicleService.create(v); assertEquals(2L, v.getDriverId()); v.setDriverId(1L);
        assertEquals(1L, vehicleService.update(v.getId(), v).getDriverId()); vehicleService.delete(v.getId());
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM equipment", Integer.class));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM material", Integer.class));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM vehicle", Integer.class));
    }

    @Test void scopedPersonnelCanBeCreatedTransferredWithinScopeAndDeletedWithLeavePlanCascade() {
        Personnel p = new Personnel();
        p.setMilitaryIdNo("军证测试-002"); p.setName("新增人员"); p.setIdCard("11010519491231002X"); p.setGender("男"); p.setBirthday("1949-12-31");
        p.setDepartmentId(2L); p.setStatus("在职"); p.setLocationPoint("机关"); p.setCategory("军官"); p.setRank("上尉");
        p.setPost("参谋"); p.setEntryDate("2010-01-01"); p.setEquivEntryDate("2010-01-01"); p.setNation("汉族");
        p.setNativePlace("浙江"); p.setEnlistPlace("浙江"); p.setBloodType("A"); p.setOnlyChild("否"); p.setAddress("测试地址");
        Personnel saved = personnel.create(p); assertEquals(2L, saved.getDepartmentId());
        Personnel partial = new Personnel(); partial.setDepartmentId(1L);
        assertEquals(1L, personnel.update(saved.getId(), partial).getDepartmentId());
        jdbc.update("INSERT INTO leave_plan(personnel_id,remark) VALUES(?,'随人员删除')", saved.getId());
        when(photos.moveToTrash(anyString())).thenReturn("");
        when(photos.moveToTrash(isNull())).thenReturn("");
        personnel.delete(saved.getId());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM leave_plan WHERE personnel_id=?", Integer.class, saved.getId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM delete_undo", Integer.class));
        assertThrows(ForbiddenException.class, () -> personnel.undoList());
    }

    @Test void personnelOldDepartmentAndNewDepartmentAreCheckedBeforePhotoOrDeletion() {
        Personnel change = new Personnel(); change.setDepartmentId(1L); change.setPhotoBase64("/9j/");
        assertThrows(ForbiddenException.class, () -> personnel.update(3L, change));
        assertThrows(ForbiddenException.class, () -> personnel.delete(3L));
        change.setDepartmentId(3L);
        assertThrows(ForbiddenException.class, () -> personnel.update(1L, change));
        assertThrows(ForbiddenException.class, () -> personnel.create(change));
        assertThrows(ForbiddenException.class, () -> people.updatePhoto(3L, "new.jpg"));
        verifyNoInteractions(photos);
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM personnel", Integer.class));
    }

    @Test void scopedPersonnelDeletionCannotClearExternalDepartmentLeaderReference() {
        jdbc.update("UPDATE department SET leader_id=1 WHERE id=3");
        ForbiddenException error = assertThrows(ForbiddenException.class, () -> personnel.delete(1L));
        assertTrue(error.getMessage().contains("联系上级"));
        assertEquals(1L, jdbc.queryForObject("SELECT leader_id FROM department WHERE id=3", Long.class));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM personnel", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM delete_undo", Integer.class));
        verifyNoInteractions(photos);
    }

    @Test void allPersonRecordDaosRejectOldAndNewExternalOwners() {
        LeaveRecord l = new LeaveRecord(); l.setId(3L); l.setPersonnelId(1L);
        FitnessRecord f = new FitnessRecord(); f.setId(3L); f.setPersonnelId(1L);
        MilitaryAssessment m = new MilitaryAssessment(); m.setId(3L); m.setPersonnelId(1L);
        assertThrows(ForbiddenException.class, () -> leave.update(l)); assertThrows(ForbiddenException.class, () -> fitness.update(f)); assertThrows(ForbiddenException.class, () -> military.update(m));
        assertThrows(ForbiddenException.class, () -> leave.findById(3L)); assertThrows(ForbiddenException.class, () -> fitness.findById(3L)); assertThrows(ForbiddenException.class, () -> military.findById(3L));
        assertThrows(ForbiddenException.class, () -> leave.delete(3L)); assertThrows(ForbiddenException.class, () -> fitness.delete(3L)); assertThrows(ForbiddenException.class, () -> military.delete(3L));
        l.setId(1L); f.setId(1L); m.setId(1L); l.setPersonnelId(3L); f.setPersonnelId(3L); m.setPersonnelId(3L);
        assertThrows(ForbiddenException.class, () -> leave.update(l)); assertThrows(ForbiddenException.class, () -> fitness.update(f)); assertThrows(ForbiddenException.class, () -> military.update(m));
        assertThrows(ForbiddenException.class, () -> leave.insert(l)); assertThrows(ForbiddenException.class, () -> fitness.insert(f)); assertThrows(ForbiddenException.class, () -> military.insert(m));
        assertThrows(ForbiddenException.class, () -> military.personExists(3L));
    }

    @Test void phoneAndLeavePlanGuardOldRecordAndNewPerson() {
        PhoneDeviceController phone = new PhoneDeviceController(new PhoneDeviceDao(jdbc)); LeavePlanController plan = new LeavePlanController(new LeavePlanDao(jdbc));
        Map<String,Object> body = new HashMap<String,Object>(); body.put("personnelId", 1L);
        assertThrows(ForbiddenException.class, () -> phone.update(3L, body)); assertThrows(ForbiddenException.class, () -> phone.delete(3L));
        assertThrows(ForbiddenException.class, () -> plan.update(3L, body)); assertThrows(ForbiddenException.class, () -> plan.delete(3L));
        body.put("personnelId", 3L);
        assertThrows(ForbiddenException.class, () -> phone.create(body)); assertThrows(ForbiddenException.class, () -> phone.update(1L, body));
        assertThrows(ForbiddenException.class, () -> plan.create(body)); assertThrows(ForbiddenException.class, () -> plan.update(1L, body));
    }

    @Test void everySubrecordKindGuardsOldRecordAndNewRequestedPerson() {
        SubRecordController c = new SubRecordController(sub, fitnessService);
        String[] kinds = {"position-history", "reward-punish", "children", "family", "driver", "card", "pvehicle", "housing", "fitness"};
        Map<String,Object> body = new HashMap<String,Object>(); body.put("personnelId", 3L);
        for (String kind : kinds) {
            assertEquals(1, ((List<?>) c.list(kind, 1L).getData()).size());
            assertThrows(ForbiddenException.class, () -> c.list(kind, 3L));
            assertThrows(ForbiddenException.class, () -> c.create(kind, body));
            assertThrows(ForbiddenException.class, () -> c.update(kind, 3L, body));
            assertThrows(ForbiddenException.class, () -> c.update(kind, 1L, body));
            assertThrows(ForbiddenException.class, () -> c.delete(kind, 3L));
        }
    }

    @Test void fitnessAggregatesAndLegacyQueriesAreScopedAndGradeSqlCannotBypassScope() {
        jdbc.update("UPDATE fitness_record SET points=90,grade='90'");
        assertEquals(2, fitnessService.countFiltered(null, null, null, null, null, null, null, null));
        assertEquals(2, fitnessService.queryFiltered(null, null, null, null, null, null, null, null, 1, 20).size());
        assertEquals(2, ((Number) fitnessService.statsFiltered(null, null, null, null, null, null, null, null).get("total")).longValue());
        assertThrows(NumberFormatException.class, () -> fitnessService.countFiltered2(null, null, null, "0 OR 1=1 --分以上", null, null, null, null, null, null));
        assertThrows(NumberFormatException.class, () -> fitnessService.countFiltered(null, null, "0 OR 1=1 --分以上", null, null, null, null, null));
    }

    @Test void undoEndpointsAreAdminOnlyEvenWhenPersonnelHasBeenDeleted() {
        jdbc.update("INSERT INTO delete_undo(personnel_id,name,payload) VALUES(999,'外部已删人员','{}')");
        assertThrows(ForbiddenException.class, () -> personnelController().undoList());
        assertThrows(ForbiddenException.class, () -> personnelController().undo(1L));
        assertThrows(ForbiddenException.class, () -> personnelController().undoDiscard(1L));
        verifyNoInteractions(photos);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM delete_undo", Integer.class));
    }

    @Test void globalMilitarySchemeWritesAreAdminOnly() {
        assertThrows(ForbiddenException.class, () -> militaryController().saveScheme(Collections.<String,Object>emptyMap()));
        assertThrows(ForbiddenException.class, () -> militaryController().deleteScheme(1L));
    }

    @Test void globalLeaveStatusSyncStillUpdatesOutsideScopeWithoutReturningThoseRecords() {
        String today = java.time.LocalDate.now().toString();
        jdbc.update("UPDATE leave_record SET start_date=?,end_date=? WHERE personnel_id IN (1,3)", today, today);
        int[] result = new LeaveStatusSyncService(jdbc).sync();
        assertEquals(2, result[0]);
        assertEquals("休假", jdbc.queryForObject("SELECT status FROM personnel WHERE id=3", String.class));
        assertEquals(2, leave.count(null));
        assertThrows(ForbiddenException.class, () -> leave.findById(3L));
    }

    @Test void forbiddenRoutesReturn403WithoutExternalDetails() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(personnelController(), new PhoneDeviceController(new PhoneDeviceDao(jdbc)), leaveController()).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/personnel/3/photo")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        mvc.perform(get("/api/personnel/3/age")).andExpect(status().isForbidden());
        mvc.perform(get("/api/phone/by-person").param("personnelId", "3")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/phone/3")).andExpect(status().isForbidden());
        mvc.perform(get("/api/leave/summary").param("personnelId", "3").param("year", "2026")).andExpect(status().isForbidden());
        mvc.perform(get("/api/personnel/by-idcard").param("idCard", "CARD3")).andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
        verifyNoInteractions(photos);
    }
}
