package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.Department;
import com.szjx.personnel.server.service.DepartmentService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 单位接口
 */
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public ApiResponse tree() {
        return ApiResponse.ok(departmentService.tree());
    }

    @PostMapping
    public ApiResponse create(@RequestBody Department d) {
        return ApiResponse.ok(departmentService.create(d));
    }

    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody Department d) {
        return ApiResponse.ok(departmentService.update(id, d));
    }

    /** 同级单位拖动排序 */
    @PutMapping("/reorder")
    public ApiResponse reorder(@RequestBody(required = false) ReorderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("排序信息不能为空");
        }
        departmentService.reorder(request.ids);
        return ApiResponse.ok();
    }

    public static class ReorderRequest {
        public java.util.List<Long> ids;
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        departmentService.delete(id);
        return ApiResponse.ok();
    }
}
