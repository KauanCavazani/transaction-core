package com.transactioncore.accountservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank String ownerName,
        @NotNull @PositiveOrZero BigDecimal balanceAmount,
        @NotBlank @Size(min = 3, max = 3) String currency
) { }
