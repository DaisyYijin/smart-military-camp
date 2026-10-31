package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.AnnualAppraisalRecord;
import com.szjx.personnel.server.service.AnnualAppraisalService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/annual-appraisal")
public class AnnualAppraisalController {
    private final AnnualAppraisalService service;
    public AnnualAppraisalController(AnnualAppraisalService service) { this.service = service; }

    @GetMapping
    public ApiResponse query(@RequestParam Map<String,String> filters) { return ApiResponse.ok(service.query(httpPage(filters))); }
    @GetMapping("/stats")
    public ApiResponse stats(@RequestParam Map<String,String> filters) { return ApiResponse.ok(service.stats(httpPage(filters))); }
    @GetMapping("/{id}")
    public ApiResponse get(@PathVariable Long id) { return ApiResponse.ok(service.get(id)); }
    @PostMapping
    public ApiResponse create(@RequestBody AnnualAppraisalRecord record) { return ApiResponse.ok(service.create(record)); }
    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody AnnualAppraisalRecord record) { return ApiResponse.ok(service.update(id, record)); }
    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) { service.delete(id); return ApiResponse.ok(); }

    private Map<String,String> httpPage(Map<String,String> filters) {
        Map<String,String> copy = new HashMap<>(filters);
        String size = copy.get("size");
        if (size != null && !size.trim().isEmpty()) {
            try {
                int number = Integer.parseInt(size.trim());
                if (number < 1 || number > 200) throw new IllegalArgumentException("每页条数必须为1至200");
            } catch (NumberFormatException e) { throw new IllegalArgumentException("每页条数必须为1至200"); }
        }
        return copy;
    }
}
