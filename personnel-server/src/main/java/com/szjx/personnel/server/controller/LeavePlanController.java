package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.dao.LeavePlanDao;
import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.PageResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 休假计划(年度休假安排):与实际休假记录互补,状态 计划中/已执行/已取消。(SQL 已下沉 LeavePlanDao)
 */
@RestController
@RequestMapping("/api/leave-plan")
public class LeavePlanController {

    private final LeavePlanDao dao;

    public LeavePlanController(LeavePlanDao dao) {
        this.dao = dao;
    }

    @GetMapping
    public ApiResponse query(@RequestParam(required = false) String personnelName,
                             @RequestParam(required = false) String idCard,
                             @RequestParam(required = false) String year,
                             @RequestParam(required = false) String leaveType,
                             @RequestParam(required = false) String month,
                             @RequestParam(required = false) String startDate,
                             @RequestParam(required = false) String endDate,
                             @RequestParam(required = false) Long personnelId,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "20") int size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 200) {
            size = 20;
        }
        if (month != null && !month.trim().isEmpty()) {
            String selectedMonth = month.trim();
            if (!selectedMonth.matches("\\d{4}-\\d{2}") || Integer.parseInt(selectedMonth.substring(5)) < 1
                    || Integer.parseInt(selectedMonth.substring(5)) > 12) {
                throw new IllegalArgumentException("月份格式应为 YYYY-MM");
            }
        }
        long total = dao.count(personnelName, idCard, year, leaveType, month, startDate, endDate, personnelId);
        List<Map<String, Object>> list = dao.query(personnelName, idCard, year, leaveType, month, startDate, endDate,
                personnelId, page, size);
        return ApiResponse.ok(new PageResult<Map<String, Object>>(total, list));
    }

    @PostMapping
    public ApiResponse create(@RequestBody Map<String, Object> m) {
        validate(m);
        Long id = dao.insert(m);
        return ApiResponse.ok(dao.findById(id));
    }

    @PutMapping("/{id:\\d+}")
    public ApiResponse update(@PathVariable Long id, @RequestBody Map<String, Object> m) {
        if (dao.findById(id) == null) {
            return ApiResponse.error("计划不存在");
        }
        validate(m);
        dao.update(id, m);
        return ApiResponse.ok(dao.findById(id));
    }

    @DeleteMapping("/{id:\\d+}")
    public ApiResponse delete(@PathVariable Long id) {
        dao.requireRecord(id);
        dao.delete(id);
        return ApiResponse.ok();
    }

    private void validate(Map<String, Object> m) {
        Long pid = LeavePlanDao.lng(m.get("personnelId"));
        dao.requirePerson(pid);
        if (pid <= 0) {
            throw new IllegalArgumentException("请先选择人员");
        }
        if (!dao.personExists(pid)) {
            throw new IllegalArgumentException("所选人员不存在");
        }
        String year = LeavePlanDao.str(m.get("year"));
        if (year.isEmpty()) {
            throw new IllegalArgumentException("请填写年度");
        }
        if (!year.matches("[0-9]{4}") || Integer.parseInt(year) < 1900) {
            throw new IllegalArgumentException("年度应为1900至9999的4位数字");
        }
        if (LeavePlanDao.str(m.get("leaveType")).isEmpty()) {
            throw new IllegalArgumentException("请选择休假类型");
        }
        String startDate = LeavePlanDao.str(m.get("startDate"));
        String endDate = LeavePlanDao.str(m.get("endDate"));
        if (startDate.isEmpty() || endDate.isEmpty()) {
            throw new IllegalArgumentException("请填写计划的开始与结束日期");
        }
        java.time.LocalDate start = parseDate(startDate, "开始");
        java.time.LocalDate end = parseDate(endDate, "结束");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        // 严格解析通过后统一写回 yyyy-MM-dd 规范格式,避免接口层入库带空格或臆造格式
        m.put("startDate", start.toString());
        m.put("endDate", end.toString());
        double days;
        try {
            days = Double.parseDouble(LeavePlanDao.str(m.get("days")));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("天数必须为数字");
        }
        if (!Double.isFinite(days)) {
            throw new IllegalArgumentException("天数必须为有限数字");
        }
        if (days < 0) {
            throw new IllegalArgumentException("天数不能为负");
        }
    }

    /** 与休假登记同口径:严格按 yyyy-MM-dd 解析真实存在的日期 */
    private static java.time.LocalDate parseDate(String date, String label) {
        if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new IllegalArgumentException(label + "日期格式应为yyyy-MM-dd");
        }
        try {
            return java.time.LocalDate.parse(date);
        } catch (java.time.DateTimeException e) {
            throw new IllegalArgumentException(label + "日期不存在");
        }
    }
}
