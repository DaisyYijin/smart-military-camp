package com.szjx.personnel.server.auth;

import com.szjx.personnel.server.config.GlobalExceptionHandler;
import com.szjx.personnel.server.config.SchemaInitializer;
import com.szjx.personnel.server.controller.ItemLoanController;
import com.szjx.personnel.server.dao.AdminUserDao;
import com.szjx.personnel.server.dao.ItemLoanDao;
import com.szjx.personnel.server.service.AccountServiceTest;
import com.szjx.personnel.server.service.ItemLoanService;
import com.szjx.personnel.server.service.TokenManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ItemLoanAuthTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;
    MockMvc mvc;
    String token;
    TokenManager tokens;

    @BeforeEach void setup() {
        DataScope.clear(); jdbc=AccountServiceTest.database(temp.resolve("item-loan-auth.sqlite"));
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY,name TEXT,parent_id INTEGER DEFAULT 0)");
        jdbc.execute("CREATE TABLE admin_user(id INTEGER PRIMARY KEY AUTOINCREMENT,username TEXT NOT NULL UNIQUE,password_hash TEXT NOT NULL,display_name TEXT DEFAULT '',created_at TEXT)");
        AdminUserDao users=new AdminUserDao(jdbc); users.insertAdmin("root","unused","管理员");
        SchemaInitializer schema=new SchemaInitializer(jdbc,users); schema.migrateAccounts();
        long root=users.findByUsername("root").id;
        long child=users.insertChild(root,"child","unused","子账号",true);
        users.insertChild(child,"grandchild","unused","孙账号",true);
        ReflectionTestUtils.invokeMethod(schema,"initializeItemLoans");
        tokens=new TokenManager(); token=tokens.create("child");
        mvc=MockMvcBuilders.standaloneSetup(new ItemLoanController(new ItemLoanService(new ItemLoanDao(jdbc))))
                .addInterceptors(new AuthInterceptor(tokens,new com.szjx.personnel.server.service.AccountService(users,jdbc,tokens)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @AfterEach void cleanup() { DataScope.clear(); }

    @Test void authenticatedCreateUsesAccountOwnerAndChildVisibilityButDoesNotAcceptForgedOwner() throws Exception {
        String request="{\"itemName\":\"物品\",\"unit\":\"件\",\"quantity\":1,\"borrowerName\":\"借用人\",\"registrationDepartment\":\"登记处\",\"borrowDate\":\""+java.time.LocalDate.now()+"\",\"ownerUserId\":0}";
        String response=mvc.perform(post("/api/item-loans").header(AuthInterceptor.HEADER_TOKEN,token)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.ownerUserId").value(2)).andReturn().getResponse().getContentAsString();
        long id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("data").path("id").asLong();
        assertEquals(2L,jdbc.queryForObject("SELECT owner_user_id FROM item_loan WHERE id=?",Long.class,id));
        mvc.perform(get("/api/item-loans").header(AuthInterceptor.HEADER_TOKEN,token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/item-loans").header(AuthInterceptor.HEADER_TOKEN,tokens.create("grandchild")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/item-loans").header(AuthInterceptor.HEADER_TOKEN,tokens.create("root")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
    }

    @Test void administratorCreatedRecordsKeepTheAuthenticatedRegistrarId() throws Exception {
        String request="{\"itemName\":\"管理员物品\",\"unit\":\"件\",\"quantity\":1,\"borrowerName\":\"借用人\",\"registrationDepartment\":\"登记处\",\"borrowDate\":\""+java.time.LocalDate.now()+"\"}";
        String response=mvc.perform(post("/api/item-loans").header(AuthInterceptor.HEADER_TOKEN,tokens.create("root"))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.ownerUserId").value(1)).andReturn().getResponse().getContentAsString();
        long id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("data").path("id").asLong();
        assertEquals(1L,jdbc.queryForObject("SELECT owner_user_id FROM item_loan WHERE id=?",Long.class,id));
    }

    @Test void unauthenticatedAccessAndForgedDepartmentQueryDoNotChangeOwnershipModel() throws Exception {
        mvc.perform(get("/api/item-loans")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/item-loans").header(AuthInterceptor.HEADER_TOKEN,token).param("departmentId","999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
    }
}
