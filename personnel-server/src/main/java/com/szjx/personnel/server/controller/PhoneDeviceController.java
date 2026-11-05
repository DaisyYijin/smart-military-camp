package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.dao.PhoneDeviceDao;
import com.szjx.personnel.server.model.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 手机设备管理:手机/手表/手环等智能终端(SQL 已下沉 PhoneDeviceDao)
 */
@RestController
@RequestMapping("/api/phone")
public class PhoneDeviceController {

    private final PhoneDeviceDao dao;

    public PhoneDeviceController(PhoneDeviceDao dao) {
        this.dao = dao;
    }

    @GetMapping
    public ApiResponse query(@RequestParam(required = false) String personnelName,
                             @RequestParam(required = false) String idCard,
                             @RequestParam(required = false) String deviceType,
                             @RequestParam(required = false) String brand,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false) Long personnelId,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "20") int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 200) size = 20;
        long total = dao.count(personnelId, personnelName, idCard, deviceType, brand, status);
        List<Map<String, Object>> list = dao.query(personnelId, personnelName, idCard, deviceType, brand, status, page, size);
        dao.fillDepartmentPath(list);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("total", total);
        result.put("list", list);
        return ApiResponse.ok(result);
    }

    @PostMapping
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse create(@RequestBody Map<String, Object> r) {
        String error = validate(r);
        if (error != null) { return ApiResponse.error(error); }
        Long pid = PhoneDeviceDao.lng(r.get("personnelId"));
        dao.insert(r);
        // 联动:人员手机号统一由本模块维护(取最新一个有号码的设备)
        dao.syncPersonnelPhone(pid);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse update(@PathVariable Long id, @RequestBody Map<String, Object> r) {
        dao.requireRecord(id);
        Long oldPid = dao.personnelIdOf(id);
        if (oldPid == null) { return ApiResponse.error("记录不存在或已被删除"); }
        String error = validate(r);
        if (error != null) { return ApiResponse.error(error); }
        Long pid = PhoneDeviceDao.lng(r.get("personnelId"));
        int n = dao.update(id, r);
        if (n == 0) { return ApiResponse.error("记录不存在或已被删除"); }
        // 设备转移时旧持有人也必须重新取剩余设备的号码。
        if (oldPid > 0 && !oldPid.equals(pid)) { dao.syncPersonnelPhone(oldPid); }
        dao.syncPersonnelPhone(pid);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse delete(@PathVariable Long id) {
        dao.requireRecord(id);
        Long pid = dao.personnelIdOf(id);
        dao.delete(id);
        if (pid != null) { dao.syncPersonnelPhone(pid); }
        return ApiResponse.ok();
    }

    /** 某人的所有手机设备(人员管理联动查看) */
    @GetMapping("/by-person")
    public ApiResponse byPerson(@RequestParam Long personnelId) {
        dao.requirePerson(personnelId);
        return ApiResponse.ok(dao.byPerson(personnelId));
    }

    private String validate(Map<String, Object> r) {
        Long pid = PhoneDeviceDao.lng(r.get("personnelId"));
        dao.requirePerson(pid);
        if (pid <= 0) { return "请先填写身份证号带出人员"; }
        if (!dao.personExists(pid)) { return "所选人员不存在"; }
        if (PhoneDeviceDao.str(r.get("deviceType")).isEmpty()) { return "请选择设备类型"; }
        if (PhoneDeviceDao.str(r.get("brand")).isEmpty()) { return "请填写品牌"; }
        return null;
    }
}
