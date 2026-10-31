package com.szjx.personnel.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.auth.ForbiddenException;
import com.szjx.personnel.server.config.GlobalExceptionHandler;
import com.szjx.personnel.server.config.SchemaInitializer;
import com.szjx.personnel.server.dao.AdminUserDao;
import com.szjx.personnel.server.dao.ItemLoanDao;
import com.szjx.personnel.server.model.ItemLoan;
import com.szjx.personnel.server.service.AccountServiceTest;
import com.szjx.personnel.server.service.ItemLoanService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ItemLoanIntegrationTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;
    ItemLoanDao dao;
    ItemLoanService service;
    MockMvc mvc;
    ObjectMapper json = new ObjectMapper();
    LocalDate today;

    @BeforeEach void setup() {
        DataScope.clear(); today = LocalDate.now();
        jdbc = AccountServiceTest.database(temp.resolve("item-loan.sqlite"));
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY,name TEXT)");
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY,name TEXT,id_card TEXT,department_id INTEGER)");
        jdbc.update("INSERT INTO personnel(id,name,id_card,department_id) VALUES" +
                "(11,'经手甲','110101199001010011',10),(12,'经手乙','110101199002020022',20),(13,'经手丙','110101199003030033',10)");
        jdbc.execute("CREATE TABLE admin_user(id INTEGER PRIMARY KEY,parent_id INTEGER NOT NULL DEFAULT 0,username TEXT)");
        jdbc.update("INSERT INTO admin_user(id,parent_id,username) VALUES(1,0,'root'),(2,1,'viewer'),(3,2,'grandchild'),(4,1,'sibling')");
        ReflectionTestUtils.invokeMethod(new SchemaInitializer(jdbc, new AdminUserDao(jdbc)), "initializeItemLoans");
        dao = new ItemLoanDao(jdbc); service = new ItemLoanService(dao);
        mvc = MockMvcBuilders.standaloneSetup(new ItemLoanController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @AfterEach void cleanup() { DataScope.clear(); }

    ItemLoan loan() {
        ItemLoan value = new ItemLoan(); value.setItemName("工具箱"); value.setQuantity(new BigDecimal("1.25"));
        value.setUnit("箱"); value.setBorrowerName("张三"); value.setBorrowDate(today.minusDays(1).toString());
        value.setRegistrationDepartment("外单位登记处"); return value;
    }
    void scope(long user) { DataScope.set(new DataScope.Context(user, "u" + user, false, Collections.<Long>emptySet())); }
    ItemLoanService.ReturnRequest returned() {
        ItemLoanService.ReturnRequest r = new ItemLoanService.ReturnRequest(); r.returnCondition="完好"; return r;
    }

    @Test void ownerIsServerAssignedAndVisibleToCreatorAndAllDescendantsOnly() throws Exception {
        scope(2); ItemLoan input=loan(); input.setOwnerUserId(4L);
        String body=mvc.perform(post("/api/item-loans").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.ownerUserId").value(2))
                .andReturn().getResponse().getContentAsString();
        JsonNode response=json.readTree(body).path("data");
        assertFalse(response.has("departmentId")); assertFalse(response.has("departmentName"));
        long created=response.path("id").asLong();
        assertEquals(2L,jdbc.queryForObject("SELECT owner_user_id FROM item_loan WHERE id=?",Long.class,created));
        scope(3); assertThrows(ForbiddenException.class,()->dao.findById(created,today));
        scope(4); assertEquals(0,service.query(null,null,null,null,1,50).getTotal());
        assertThrows(ForbiddenException.class,()->dao.findById(created,today));
        DataScope.clear(); assertEquals(1,service.query(null,null,null,null,1,50).getTotal());
        mvc.perform(get("/api/item-loans").param("departmentId","2")).andExpect(status().isOk());
    }

    @Test void allReadsAndMutationsEnforceOwnerHierarchyAtomicallyAndOwnerZeroIsAdminOnly() {
        scope(2); ItemLoan created=service.create(loan()); scope(4);
        assertEquals(0,service.query(null,null,null,null,1,50).getTotal());
        assertThrows(ForbiddenException.class,()->service.update(created.getId(),loan()));
        assertThrows(ForbiddenException.class,()->service.returnLoan(created.getId(),returned()));
        assertThrows(ForbiddenException.class,()->service.delete(created.getId()));
        jdbc.update("INSERT INTO item_loan(item_name,quantity,unit,borrow_date,owner_user_id) VALUES('legacy','1','件','2024-01-01',0)");
        long orphan=jdbc.queryForObject("SELECT MAX(id) FROM item_loan",Long.class);
        assertEquals(0,service.query(null,null,null,null,1,50).getTotal());
        assertThrows(ForbiddenException.class,()->dao.findById(orphan,today));
        DataScope.clear(); assertEquals(2,service.query(null,null,null,null,1,50).getTotal());
        scope(2); assertEquals(1,dao.delete(created.getId(),created.getOwnerUserId()));
    }

    @Test void cyclicAccountGraphIsSafeAndDoesNotExpandOutsideReachability() {
        scope(2); ItemLoan child=service.create(loan());
        jdbc.update("UPDATE admin_user SET parent_id=3 WHERE id=2");
        assertEquals(1,service.query(null,null,null,null,1,50).getTotal());
        scope(4); assertEquals(0,service.query(null,null,null,null,1,50).getTotal());
        DataScope.set(new DataScope.Context(1L,"admin",true,Collections.<Long>emptySet()));
        assertEquals(1,service.query(null,null,null,null,1,50).getTotal());
    }

    @Test void statusAndEditsIgnoreCompatibilityDatesWithoutChangingThem() throws Exception {
        scope(2); ItemLoan created=service.create(loan());
        jdbc.update("UPDATE item_loan SET expected_return_date='2000-01-01',actual_return_date='2000-01-02' WHERE id=?",created.getId());
        assertEquals("borrowing",dao.findById(created.getId(),today).getStatus());
        assertEquals(1,service.query(null,null,null,"borrowing",1,50).getTotal());
        ItemLoan edit=loan(); edit.setItemName("更新物品");
        assertEquals("borrowing",service.update(created.getId(),edit).getStatus());
        assertEquals("2000-01-01",jdbc.queryForObject("SELECT expected_return_date FROM item_loan WHERE id=?",String.class,created.getId()));
        assertEquals("returned",service.returnLoan(created.getId(),returned()).getStatus());
        assertEquals("2000-01-02",jdbc.queryForObject("SELECT actual_return_date FROM item_loan WHERE id=?",String.class,created.getId()));
        assertEquals(0,service.query(null,null,null,"borrowing",1,50).getTotal());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->service.update(created.getId(),loan()));
        assertEquals(0,dao.returnLoan(created,"不能覆盖","不能覆盖"));
        assertEquals("完好",dao.findById(created.getId(),today).getReturnCondition());
    }

    @Test void handlerIsOptionalAndNameAlwaysComesFromPersonnel() throws Exception {
        ItemLoan input = loan(); input.setHandlerId(11L); input.setHandlerName("伪造姓名"); input.setHandlerIdCard("伪造证件");
        DataScope.set(new DataScope.Context(2L,"viewer",false,Collections.singleton(10L)));
        String response = mvc.perform(post("/api/item-loans").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(input)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.handlerId").value(11))
                .andExpect(jsonPath("$.data.handlerName").value("经手甲"))
                .andExpect(jsonPath("$.data.handlerIdCard").value("110101199001010011"))
                .andExpect(jsonPath("$.data.borrowerName").value("张三"))
                .andExpect(jsonPath("$.data.ownerUserId").value(2))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(response).path("data").path("id").asLong();
        assertEquals("经手甲", jdbc.queryForObject("SELECT handler_name FROM item_loan WHERE id=?", String.class, id));
        assertEquals("110101199001010011", jdbc.queryForObject("SELECT handler_id_card FROM item_loan WHERE id=?", String.class, id));
        ItemLoan empty = service.create(loan());
        assertEquals(0L, empty.getHandlerId()); assertEquals("", empty.getHandlerName()); assertEquals("", empty.getHandlerIdCard());
        scope(4); assertThrows(ForbiddenException.class, () -> dao.findById(id, today));
    }

    @Test void selectingHandlerRequiresExistingVisiblePersonnelOnCreateAndChange() {
        DataScope.set(new DataScope.Context(2L,"viewer",false,Collections.singleton(10L)));
        ItemLoan forbidden = loan(); forbidden.setHandlerId(12L);
        assertThrows(ForbiddenException.class, () -> service.create(forbidden));
        ItemLoan original = service.create(loan());
        assertThrows(ForbiddenException.class, () -> service.update(original.getId(), forbidden));
        assertEquals(0L, dao.findById(original.getId(),today).getHandlerId());
        ItemLoan missing = loan(); missing.setHandlerId(999L);
        assertThrows(ForbiddenException.class, () -> service.create(missing));
        DataScope.clear();
        assertThrows(IllegalArgumentException.class, () -> service.create(missing));
        ItemLoan negative = loan(); negative.setHandlerId(-1L);
        assertThrows(IllegalArgumentException.class, () -> service.create(negative));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM item_loan", Integer.class));
    }

    @Test void handlerSnapshotSurvivesRenameMoveDeletionAndUnrelatedEdits() {
        DataScope.set(new DataScope.Context(2L,"viewer",false,Collections.singleton(10L)));
        ItemLoan input = loan(); input.setHandlerId(11L);
        ItemLoan original = service.create(input);
        jdbc.update("UPDATE personnel SET name='新姓名',id_card='999999',department_id=20 WHERE id=11");
        ItemLoan edit = loan(); edit.setHandlerId(11L); edit.setHandlerName("伪造姓名");
        assertEquals("经手甲", service.update(original.getId(), edit).getHandlerName());
        jdbc.update("DELETE FROM personnel WHERE id=11");
        edit = loan(); edit.setRemark("只改备注");
        ItemLoan updated = service.update(original.getId(), edit);
        assertEquals(11L, updated.getHandlerId()); assertEquals("经手甲", updated.getHandlerName());
        assertEquals("110101199001010011", updated.getHandlerIdCard());
        assertEquals(2L, updated.getOwnerUserId());
        assertEquals("经手甲", service.returnLoan(original.getId(), returned()).getHandlerName());
        assertEquals("110101199001010011", dao.findById(original.getId(), today).getHandlerIdCard());
    }

    @Test void handlerCanBeReplacedAndExplicitlyClearedWithoutRebindingBorrower() throws Exception {
        DataScope.set(new DataScope.Context(2L,"viewer",false,Collections.singleton(10L)));
        ItemLoan input = loan(); input.setHandlerId(11L);
        ItemLoan original = service.create(input);
        ItemLoan edit = loan(); edit.setHandlerId(13L); edit.setHandlerName("不可信");
        ItemLoan changed = service.update(original.getId(), edit);
        assertEquals(13L, changed.getHandlerId()); assertEquals("经手丙", changed.getHandlerName());
        assertEquals("110101199003030033", changed.getHandlerIdCard());
        assertEquals("张三", changed.getBorrowerName()); assertEquals(0L, changed.getPersonnelId());
        edit.setHandlerId(0L); edit.setHandlerIdCard("不可信");
        mvc.perform(put("/api/item-loans/" + original.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(edit)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.handlerId").value(0))
                .andExpect(jsonPath("$.data.handlerName").value(""))
                .andExpect(jsonPath("$.data.handlerIdCard").value(""));
        jdbc.update("DELETE FROM personnel WHERE id=13");
        ItemLoan missing = loan(); missing.setHandlerId(13L);
        assertThrows(ForbiddenException.class, () -> service.update(original.getId(), missing));
    }

    @Test void returnNeedsNoDateAndResponseExposesOnlyTwoStatuses() throws Exception {
        scope(2); ItemLoan created=service.create(loan());
        String response=mvc.perform(post("/api/item-loans/"+created.getId()+"/return").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(returned())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode item=json.readTree(response).path("data");
        assertEquals("returned",item.path("status").asText());
        assertFalse(item.has("expectedReturnDate")); assertFalse(item.has("actualReturnDate"));
        assertEquals(1,jdbc.queryForObject("SELECT returned FROM item_loan WHERE id=?",Integer.class,created.getId()));
        assertEquals("",jdbc.queryForObject("SELECT actual_return_date FROM item_loan WHERE id=?",String.class,created.getId()));
        assertThrows(IllegalArgumentException.class,()->service.query(null,null,null,"overdue",1,50));
        assertEquals(1,service.query(null,null,null,"returned",1,50).getTotal());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->service.returnLoan(created.getId(),returned()));
    }
}
