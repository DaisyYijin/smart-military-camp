package com.szjx.personnel.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.auth.ForbiddenException;
import com.szjx.personnel.server.service.AccountServiceTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.Path;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class PreferenceControllerTest {
    @TempDir Path temp;
    private PreferenceController controller;
    private JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach void setup() {
        DataScope.clear();
        jdbc = AccountServiceTest.database(temp.resolve("preferences.sqlite"));
        jdbc.execute("CREATE TABLE sys_setting(skey TEXT PRIMARY KEY,svalue TEXT)");
        jdbc.update("INSERT INTO sys_setting VALUES('dashWidgets','legacy-shared-layout')");
        controller = new PreferenceController(jdbc);
    }
    @AfterEach void cleanup() { DataScope.clear(); }
    private void as(long id) { DataScope.set(new DataScope.Context(id, "account" + id, false, Collections.emptySet())); }

    @Test void settingsArePrivateToAuthenticatedIdentityNotSharedGlobalDefaults() throws Exception {
        as(1);
        assertEquals("", controller.get("dashWidgets").getData());
        controller.put("dashWidgets", mapper.readTree("[{\"type\":\"gender\"}]"));
        assertEquals("[{\"type\":\"gender\"}]", controller.get("dashWidgets").getData());
        as(2);
        assertEquals("", controller.get("dashWidgets").getData());
        controller.put("dashWidgets", mapper.readTree("[]"));
        as(1);
        assertEquals("[{\"type\":\"gender\"}]", controller.get("dashWidgets").getData());
        assertEquals("legacy-shared-layout", jdbc.queryForObject("SELECT svalue FROM sys_setting WHERE skey='dashWidgets'", String.class));
    }

    @Test void endpointsRequireAnActualUserEvenForInternalInvocation() {
        assertThrows(ForbiddenException.class, () -> controller.get("dashWidgets"));
    }

    @Test void unknownKeysAndObjectBodiesCannotOverwriteOtherConfiguration() throws Exception {
        as(1);
        assertThrows(IllegalArgumentException.class, () -> controller.get("siteName"));
        assertThrows(IllegalArgumentException.class, () -> controller.put("dashWidgets", mapper.readTree("{\"userId\":2}")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_setting", Integer.class));
    }

    @Test void payloadIsBoundedByItemCountAndUtf8Bytes() throws Exception {
        as(1);
        com.fasterxml.jackson.databind.node.ArrayNode many = mapper.createArrayNode();
        for (int i = 0; i < 101; i++) many.add(i);
        assertThrows(IllegalArgumentException.class, () -> controller.put("dashWidgets", many));
        com.fasterxml.jackson.databind.node.ArrayNode large = mapper.createArrayNode();
        large.add(new String(new char[23000]).replace('\0', '汉'));
        assertThrows(IllegalArgumentException.class, () -> controller.put("dashWidgets", large));
        assertEquals("", controller.get("dashWidgets").getData());
    }
}
