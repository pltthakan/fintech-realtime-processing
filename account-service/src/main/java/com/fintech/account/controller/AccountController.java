package com.fintech.account.controller;

import com.fintech.account.entity.Account;
import com.fintech.account.service.AccountService;
import com.fintech.common.dto.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Account>> getAccountById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getAccountById(id)));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<Account>>> getAccountsByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getAccountsByUserId(userId)));
    }

    @GetMapping("/number/{accountNumber}")
    public ResponseEntity<ApiResponse<Account>> getAccountByNumber(@PathVariable String accountNumber) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getAccountByNumber(accountNumber)));
    }
}
