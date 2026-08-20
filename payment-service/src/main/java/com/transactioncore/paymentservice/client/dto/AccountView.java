package com.transactioncore.paymentservice.client.dto;

import java.util.UUID;

public record AccountView(
        UUID id,
        String status
) { }
