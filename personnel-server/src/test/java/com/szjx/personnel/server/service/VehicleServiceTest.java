package com.szjx.personnel.server.service;

import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.dao.VehicleDao;
import com.szjx.personnel.server.model.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 车辆表单已移除购置日期/保险/里程/燃料类型,编辑时这些历史字段必须原样保留 */
public class VehicleServiceTest {

    @TempDir Path temp;
    JdbcTemplate jdbc;
    VehicleDao dao;
    VehicleService service;

    @BeforeEach void setup() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setUrl("jdbc:sqlite:" + temp.resolve("isolated.sqlite").toAbsolutePath());
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE vehicle(id INTEGER PRIMARY KEY AUTOINCREMENT, plate_no TEXT DEFAULT '', vin TEXT DEFAULT '', " +
                "engine_no TEXT DEFAULT '', brand TEXT DEFAULT '', model TEXT DEFAULT '', type TEXT DEFAULT '', color TEXT DEFAULT '', " +
                "fuel_type TEXT DEFAULT '', status TEXT DEFAULT '', driver_id INTEGER DEFAULT 0, seats INTEGER, mileage REAL, " +
                "purchase_date TEXT DEFAULT '', equip_date TEXT DEFAULT '', insurance_expiry TEXT DEFAULT '', inspection_expiry TEXT DEFAULT '', " +
                "remark TEXT DEFAULT '', photo TEXT DEFAULT '', created_at TEXT DEFAULT '', updated_at TEXT DEFAULT '')");
        dao = new VehicleDao(jdbc);
        service = new VehicleService(dao, new PersonnelDao(jdbc), mock(PhotoService.class));
    }

    private long legacyVehicle() {
        Vehicle v = new Vehicle();
        v.setPlateNo("京A12345");
        v.setBrand("东风");
        v.setStatus("在用");
        v.setFuelType("柴油");
        v.setMileage(12000d);
        v.setPurchaseDate("2019-03-01");
        v.setInsuranceExpiry("2027-03-01");
        long id = dao.insert(v);
        return id;
    }

    @Test void updateKeepsFieldsRemovedFromTheForm() {
        long id = legacyVehicle();
        // 新表单不再提交这四个字段(反序列化后为 null)
        Vehicle edit = new Vehicle();
        edit.setPlateNo("京A12345");
        edit.setStatus("维修");
        Vehicle saved = service.update(id, edit);

        assertEquals("维修", saved.getStatus());
        assertEquals("柴油", saved.getFuelType());
        assertEquals(12000d, saved.getMileage());
        assertEquals("2019-03-01", saved.getPurchaseDate());
        assertEquals("2027-03-01", saved.getInsuranceExpiry());
    }

    @Test void explicitEmptyValuesAreStillAccepted() {
        long id = legacyVehicle();
        Vehicle edit = new Vehicle();
        edit.setPlateNo("京A12345");
        edit.setFuelType("");
        edit.setMileage(0d);
        edit.setPurchaseDate("");
        edit.setInsuranceExpiry("");
        Vehicle saved = service.update(id, edit);

        assertEquals("", saved.getFuelType());
        assertEquals(0d, saved.getMileage());
        assertEquals("", saved.getPurchaseDate());
        assertEquals("", saved.getInsuranceExpiry());
    }
}
