package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.auth.ForbiddenException;
import com.szjx.personnel.server.dao.VehicleDao;
import com.szjx.personnel.server.dao.VehicleFaultHistoryDao;
import com.szjx.personnel.server.model.VehicleFaultHistory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.*;

class VehicleFaultHistoryServiceTest {
    @TempDir Path temp;
    private JdbcTemplate jdbc;
    private VehicleFaultHistoryDao dao;
    private VehicleFaultHistoryService service;

    @BeforeEach void setup() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setUrl("jdbc:sqlite:" + temp.resolve("faults.sqlite").toAbsolutePath());
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY, department_id INTEGER)");
        jdbc.execute("CREATE TABLE vehicle(id INTEGER PRIMARY KEY, driver_id INTEGER DEFAULT 0)");
        jdbc.execute("CREATE TABLE vehicle_fault_history(id INTEGER PRIMARY KEY AUTOINCREMENT, vehicle_id INTEGER NOT NULL, fault_date TEXT NOT NULL DEFAULT '', description TEXT NOT NULL DEFAULT '', created_at TEXT DEFAULT '', updated_at TEXT DEFAULT '')");
        jdbc.update("INSERT INTO personnel(id,department_id) VALUES(10,1),(20,2)");
        jdbc.update("INSERT INTO vehicle(id,driver_id) VALUES(1,10),(2,20)");
        dao = new VehicleFaultHistoryDao(jdbc);
        service = new VehicleFaultHistoryService(dao);
        DataScope.set(new DataScope.Context(9L, "scoped", false, new LinkedHashSet<Long>(Collections.singleton(1L))));
    }

    @AfterEach void clearScope() { DataScope.clear(); }

    @Test void scopedVehicleFaultHistorySupportsCreateReadUpdateAndDelete() {
        VehicleFaultHistory record = new VehicleFaultHistory();
        record.setFaultDate("2026-10-17");
        record.setDescription("发动机异响");
        VehicleFaultHistory created = service.create(1L, record);
        assertNotNull(created.getId());
        assertEquals(1L, created.getVehicleId());
        assertEquals(1, service.list(1L).size());

        VehicleFaultHistory edit = new VehicleFaultHistory();
        edit.setFaultDate("2026-10-18");
        edit.setDescription("已检查，异响仍存在");
        assertEquals("2026-10-18", service.update(1L, created.getId(), edit).getFaultDate());
        assertEquals("已检查，异响仍存在", service.list(1L).get(0).getDescription());
        service.delete(1L, created.getId());
        assertTrue(service.list(1L).isEmpty());
    }

    @Test void rejectsOutOfScopeVehiclesAndInvalidFaultRows() {
        VehicleFaultHistory record = new VehicleFaultHistory();
        record.setFaultDate("2026-02-30");
        record.setDescription("故障");
        assertThrows(IllegalArgumentException.class, () -> service.create(1L, record));
        record.setFaultDate("2026-10-17");
        assertThrows(ForbiddenException.class, () -> service.list(2L));
        assertThrows(ForbiddenException.class, () -> service.create(2L, record));
    }

    @Test void deletingVehicleRemovesItsFaultHistory() {
        DataScope.clear();
        VehicleFaultHistory record = new VehicleFaultHistory();
        record.setFaultDate("2026-10-17");
        record.setDescription("制动系统报警");
        service.create(1L, record);
        new VehicleDao(jdbc).delete(1L);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM vehicle_fault_history WHERE vehicle_id=1", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM vehicle WHERE id=2", Integer.class));
    }
}
