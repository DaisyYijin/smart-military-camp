package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.service.AccountService;
import org.springframework.web.bind.annotation.*;

/** Direct children only; account deletion and root-management endpoints deliberately do not exist. */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService accounts;

    public AccountController(AccountService accounts) { this.accounts = accounts; }

    @GetMapping
    public ApiResponse list() { return ApiResponse.ok(accounts.listChildren()); }

    @GetMapping("/scope-preview")
    public ApiResponse scopePreview(@RequestParam(required = false) Long accountId,
                                    @RequestParam(required = false) java.util.Set<Long> departmentIds,
                                    @RequestParam(defaultValue = "false") boolean draft) {
        return ApiResponse.ok(accounts.scopePreview(accountId, departmentIds, draft));
    }

    @PostMapping
    public ApiResponse create(@RequestBody AccountService.AccountRequest request) {
        return ApiResponse.ok(accounts.create(request));
    }

    @PutMapping("/{id:\\d+}")
    public ApiResponse update(@PathVariable Long id, @RequestBody AccountService.AccountRequest request) {
        return ApiResponse.ok(accounts.update(id, request));
    }
}
