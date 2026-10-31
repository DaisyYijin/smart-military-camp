package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.DictItem;
import com.szjx.personnel.server.service.DictService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据字典接口
 */
@RestController
@RequestMapping("/api/dict")
public class DictController {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    /** 所有类型及其全部项(管理页面用) */
    @GetMapping("/types")
    public ApiResponse types() {
        return ApiResponse.ok(dictService.listTypes());
    }

    /** 某类型启用的字典项(下拉框用) */
    @GetMapping("/{type}")
    public ApiResponse list(@PathVariable String type) {
        return ApiResponse.ok(dictService.listEnabled(type));
    }

    @PostMapping
    public ApiResponse create(@RequestBody DictItem d) {
        return ApiResponse.ok(dictService.create(d));
    }

    /** 同级字典选项拖动排序 */
    @PutMapping("/reorder")
    public ApiResponse reorder(@RequestBody(required = false) ReorderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("排序信息不能为空");
        }
        dictService.reorder(request.dictType, request.ids);
        return ApiResponse.ok();
    }

    public static class ReorderRequest {
        public String dictType;
        public List<Long> ids;
    }

    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody DictItem d) {
        return ApiResponse.ok(dictService.update(id, d));
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        int cleared = dictService.delete(id);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("cleared", cleared);
        return ApiResponse.ok(data);
    }
}
