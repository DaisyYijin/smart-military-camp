package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.FitnessRecord;
import com.szjx.personnel.server.model.PageResult;
import com.szjx.personnel.server.service.FitnessService;
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
 * 体能接口
 */
@RestController
@RequestMapping("/api/fitness")
public class FitnessController {

    private final FitnessService fitnessService;

    public FitnessController(FitnessService fitnessService) {
        this.fitnessService = fitnessService;
    }

    @GetMapping
    public ApiResponse query(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String personnelName,
                             @RequestParam(required = false) String idCard,
                             @RequestParam(required = false) String item,
                             @RequestParam(required = false) String grade,
                             @RequestParam(required = false) String startDate,
                             @RequestParam(required = false) String endDate,
                             @RequestParam(required = false) Long departmentId,
                             @RequestParam(required = false) String minScore,
                             @RequestParam(required = false) String maxScore,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "50") int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 200) size = 50;
        List<FitnessRecord> list = fitnessService.queryFiltered2(personnelName, idCard, item, grade, startDate, endDate, departmentId, minScore, maxScore, page, size);
        return ApiResponse.ok(new PageResult<FitnessRecord>(fitnessService.countFiltered2(personnelName, idCard, item, grade, startDate, endDate, departmentId, minScore, maxScore), list));
    }

    @GetMapping("/stats")
    public ApiResponse stats(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String item,
                             @RequestParam(required = false) String grade,
                             @RequestParam(required = false) String startDate,
                             @RequestParam(required = false) String endDate,
                             @RequestParam(required = false) Long departmentId,
                             @RequestParam(required = false) String minScore,
                             @RequestParam(required = false) String maxScore) {
        return ApiResponse.ok(fitnessService.statsFiltered(keyword, item, grade, startDate, endDate, departmentId, minScore, maxScore));
    }

    @PostMapping
    public ApiResponse create(@RequestBody FitnessRecord r) {
        return ApiResponse.ok(fitnessService.create(r));
    }

    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody FitnessRecord r) {
        return ApiResponse.ok(fitnessService.update(id, r));
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        fitnessService.delete(id);
        return ApiResponse.ok();
    }
}
