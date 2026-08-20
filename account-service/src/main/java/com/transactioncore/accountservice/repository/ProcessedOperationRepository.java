package com.transactioncore.accountservice.repository;

import com.transactioncore.accountservice.domain.ProcessedOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedOperationRepository extends JpaRepository<ProcessedOperation, UUID> {
}
