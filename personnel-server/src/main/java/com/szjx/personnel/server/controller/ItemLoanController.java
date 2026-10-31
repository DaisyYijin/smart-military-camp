package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.ItemLoan;
import com.szjx.personnel.server.service.ItemLoanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/item-loans")
public class ItemLoanController {
    private final ItemLoanService service;

    public ItemLoanController(ItemLoanService service) { this.service = service; }

    @GetMapping
    public ApiResponse query(@RequestParam(required = false) String itemName,
                             @RequestParam(required = false) String borrowerName,
                             @RequestParam(required = false) String registrationDepartment,
                             @RequestParam(required = false) String status,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(service.query(itemName, borrowerName, registrationDepartment, status, page, size));
    }

    @PostMapping
    public ApiResponse create(@RequestBody ItemLoan loan) { return ApiResponse.ok(service.create(loan)); }

    @PutMapping("/{id:[0-9]+}")
    public ApiResponse update(@PathVariable Long id, @RequestBody ItemLoan loan) {
        return ApiResponse.ok(service.update(id, loan));
    }

    @DeleteMapping("/{id:[0-9]+}")
    public ApiResponse delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id:[0-9]+}/return")
    public ApiResponse returnLoan(@PathVariable Long id, @RequestBody ItemLoanService.ReturnRequest request) {
        return ApiResponse.ok(service.returnLoan(id, request));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse> invalidParameter(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "请求参数类型错误:" + exception.getName()));
    }
}
