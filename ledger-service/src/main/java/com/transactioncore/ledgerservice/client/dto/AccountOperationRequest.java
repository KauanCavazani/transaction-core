package com.transactioncore.ledgerservice.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountOperationRequest(
        UUID operationId,
        BigDecimal amount,
        String currency
) { }
