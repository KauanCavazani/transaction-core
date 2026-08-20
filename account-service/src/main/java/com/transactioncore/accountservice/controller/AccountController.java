package com.transactioncore.accountservice.controller;

import com.transactioncore.accountservice.domain.Account;
import com.transactioncore.accountservice.dto.AccountResponse;
import com.transactioncore.accountservice.dto.CreateAccountRequest;
import com.transactioncore.accountservice.service.AccountService;
import com.transactioncore.shared.valueobject.Money;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Currency;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        Money money = new Money(request.balanceAmount(), Currency.getInstance(request.currency()));
        Account newAccount = service.create(request.ownerName(), money);
        return ResponseEntity.created(URI.create("/accounts/" + newAccount.getId())).body(AccountResponse.from(newAccount));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable UUID accountId) {
        Account account = service.findById(accountId);
        return ResponseEntity.ok(AccountResponse.from(account));
    }

}
