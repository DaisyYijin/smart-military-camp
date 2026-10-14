package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.dao.MilitaryAssessmentDao;
import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.MilitaryAssessment;
import com.szjx.personnel.server.model.PageResult;
import com.szjx.personnel.server.util.Dates;
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

/**
 * 军事考核(理论/射击/战术等)接口
 */
@RestController
@RequestMapping("/api/military")
public class MilitaryAssessmentController {

    private final MilitaryAssessmentDao dao;

    public MilitaryAssessmentController(MilitaryAssessmentDao dao) {
        this.dao = dao;
    }

    @GetMapping
    public ApiResponse query(@RequestParam(required = false) String personnelName,
                             @RequestParam(required = false) String category,
                             @RequestParam(required = false) String item,
                             @RequestParam(required = false) String grade,
                             @RequestParam(required = false) String startDate,
                             @RequestParam(required = false) String endDate,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "20") int size) {
        if (page < 1) { page = 1; }
        if (size < 1 || size > 200) { size = 20; }
        List<MilitaryAssessment> list = dao.query(personnelName, category, item, grade, startDate, endDate, page, size);
        long total = dao.count(personnelName, category, item, grade, startDate, endDate);
        return ApiResponse.ok(new PageResult<MilitaryAssessment>(total, list));
    }

    @PostMapping
    public ApiResponse create(@RequestBody MilitaryAssessment m) {
        validate(m);
        long id = dao.insert(m);
        return ApiResponse.ok(dao.findById(id));
    }

    @PutMapping("/{id:\\d+}")
    public ApiResponse update(@PathVariable Long id, @RequestBody MilitaryAssessment m) {
        if (dao.findById(id) == null) {
            return ApiResponse.error("记录不存在");
        }
        validate(m);
        m.setId(id);
        dao.update(m);
        return ApiResponse.ok(dao.findById(id));
    }

    @DeleteMapping("/{id:\\d+}")
    public ApiResponse delete(@PathVariable Long id) {
        dao.delete(id);
        return ApiResponse.ok();
    }

    private void validate(MilitaryAssessment m) {
        if (!dao.personExists(m.getPersonnelId())) {
            throw new IllegalArgumentException("请选择考核人员");
        }
        if (m.getItem() == null || m.getItem().trim().isEmpty()) {
            throw new IllegalArgumentException("请填写考核项目");
        }
        if (m.getTestDate() == null || m.getTestDate().trim().isEmpty()) {
            throw new IllegalArgumentException("请填写考核日期");
        }
        if (!Dates.validOrEmpty(m.getTestDate())) {
            throw new IllegalArgumentException("考核日期格式应为 yyyy-MM-dd");
        }
        if (m.getPoints() != null && (m.getPoints() < 0 || m.getPoints() > 100)) {
            throw new IllegalArgumentException("分数应在 0~100 之间");
        }
    }
}
