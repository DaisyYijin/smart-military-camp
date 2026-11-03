package com.szjx.personnel.server.service;

import com.szjx.personnel.server.dao.EquipmentDao;
import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.model.Equipment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class EquipmentGunServiceTest {

    @TempDir Path temp;
    EquipmentDao dao;
    EquipmentService service;

    @BeforeEach void setup() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setUrl("jdbc:sqlite:" + temp.resolve("equipment.sqlite").toAbsolutePath());
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT DEFAULT '', id_card TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE equipment(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT DEFAULT '', code TEXT DEFAULT '', " +
                "ident_code TEXT DEFAULT '', category TEXT DEFAULT '', brand TEXT DEFAULT '', model TEXT DEFAULT '', status TEXT DEFAULT '', keeper_id INTEGER DEFAULT 0, " +
                "location TEXT DEFAULT '', buy_date TEXT DEFAULT '', equip_date TEXT DEFAULT '', remark TEXT DEFAULT '', photo TEXT DEFAULT '', " +
                "created_at TEXT DEFAULT '', updated_at TEXT DEFAULT '')");
        dao = new EquipmentDao(jdbc);
        service = new EquipmentService(dao, new PersonnelDao(jdbc), mock(PhotoService.class));
    }

    private Equipment gun(String code) {
        Equipment e = new Equipment();
        e.setName("测试枪支");
        e.setCode(code);
        e.setCategory("枪支");
        e.setStatus("在用");
        return e;
    }

    @Test void gunEquipmentNumberIsRequired() {
        Equipment e = gun("  ");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.create(e));
        assertEquals("枪支装备编号不能为空", error.getMessage());
    }

    @Test void gunEquipmentNumberIsUniqueIgnoringCaseAndWhitespace() {
        service.create(gun("Q-001"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.create(gun(" q-001 ")));
        assertEquals("枪支装备编号已存在", error.getMessage());
    }

    @Test void creatingEquipmentPersistsBrand() {
        Equipment equipment = gun("Q-003");
        equipment.setBrand("测试品牌甲");

        Equipment saved = service.create(equipment);

        assertEquals("测试品牌甲", saved.getBrand());
        assertEquals("测试品牌甲", service.get(saved.getId()).getBrand());
        assertEquals("测试品牌甲", service.query(null, null, null, null, null, null, null, null, null, 1, 20).getList().get(0).get("brand"));
    }

    @Test void updatingEquipmentPersistsBrand() {
        Equipment equipment = gun("Q-004");
        equipment.setBrand("测试品牌甲");
        Equipment saved = service.create(equipment);
        Equipment edit = gun("Q-004");
        edit.setBrand("测试品牌乙");

        Equipment updated = service.update(saved.getId(), edit);

        assertEquals("测试品牌乙", updated.getBrand());
        assertEquals("测试品牌乙", service.get(saved.getId()).getBrand());
    }

    @Test void updatingEquipmentWithoutBrandPreservesExistingBrand() {
        Equipment equipment = gun("Q-005");
        equipment.setBrand("测试品牌甲");
        Equipment saved = service.create(equipment);
        Equipment edit = gun("Q-005");
        edit.setName("更新后的装备");

        Equipment updated = service.update(saved.getId(), edit);

        assertEquals("更新后的装备", updated.getName());
        assertEquals("测试品牌甲", updated.getBrand());
        assertEquals("测试品牌甲", service.get(saved.getId()).getBrand());
    }

    @Test void updatingEquipmentCanExplicitlyClearBrand() {
        Equipment equipment = gun("Q-006");
        equipment.setBrand("测试品牌甲");
        Equipment saved = service.create(equipment);
        Equipment edit = gun("Q-006");
        edit.setBrand("");

        Equipment updated = service.update(saved.getId(), edit);

        assertEquals("", updated.getBrand());
        assertEquals("", service.get(saved.getId()).getBrand());
    }

    @Test void updatingGunCanKeepItsOwnNumberAndPreservesRemovedPurchaseDate() {
        Equipment old = gun("Q-002");
        old.setBuyDate("2020-01-02");
        long id = dao.insert(old);
        Equipment edit = gun("Q-002");
        edit.setBuyDate(null);
        Equipment saved = service.update(id, edit);
        assertEquals("Q-002", saved.getCode());
        assertEquals("2020-01-02", saved.getBuyDate());
    }
}
