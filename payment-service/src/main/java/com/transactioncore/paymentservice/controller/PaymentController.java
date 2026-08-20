package com.transactioncore.paymentservice.controller;

import com.transactioncore.paymentservice.domain.Transaction;
import com.transactioncore.paymentservice.dto.TransferRequest;
import com.transactioncore.paymentservice.dto.TransferResponse;
import com.transactioncore.paymentservice.service.TransactionService;
import com.transactioncore.shared.valueobject.IdempotencyKey;
import com.transactioncore.shared.valueobject.Money;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final TransactionService service;

    public PaymentController(TransactionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        IdempotencyKey idempotencyKey = IdempotencyKey.of(request.idempotencyKey());
        Money money = Money.of(request.amount(), request.currency());
        Transaction transaction = service.transfer(idempotencyKey, request.sourceAccountId(), request.destinationAccountId(), money);
        return ResponseEntity.status(HttpStatus.CREATED).body(TransferResponse.from(transaction));
    }

}
