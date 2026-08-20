package com.transactioncore.paymentservice.service;

import com.transactioncore.paymentservice.cache.IdempotencyCache;
import com.transactioncore.paymentservice.domain.Transaction;
import com.transactioncore.paymentservice.repository.TransactionRepository;
import com.transactioncore.shared.exceptions.TransactionProcessingException;
import com.transactioncore.shared.valueobject.IdempotencyKey;
import com.transactioncore.shared.valueobject.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionProcessor transactionProcessor;

    @Mock
    private IdempotencyCache idempotencyCache;

    private TransactionService transactionService;

    private final UUID sourceAccountId = UUID.randomUUID();
    private final UUID destinationAccountId = UUID.randomUUID();
    private final Money amount = Money.brl(new BigDecimal("150.00"));
    private final IdempotencyKey idempotencyKey = IdempotencyKey.of("key-1");

    private TransactionService newService() {
        return new TransactionService(transactionRepository, transactionProcessor, idempotencyCache);
    }

    @Test
    @DisplayName("transfer should return the cached transaction without acquiring a lock or calling the processor")
    void transferShouldReturnTheCachedTransactionWithoutAcquiringALockOrCallingTheProcessor() {
        transactionService = newService();
        UUID cachedTransactionId = UUID.randomUUID();
        Transaction cachedTransaction = new Transaction(idempotencyKey.value(), sourceAccountId, destinationAccountId, amount);

        when(idempotencyCache.findTransactionId(idempotencyKey.value())).thenReturn(Optional.of(cachedTransactionId));
        when(transactionRepository.findById(cachedTransactionId)).thenReturn(Optional.of(cachedTransaction));

        Transaction result = transactionService.transfer(idempotencyKey, sourceAccountId, destinationAccountId, amount);

        assertThat(result).isEqualTo(cachedTransaction);
        verify(idempotencyCache, never()).tryLock(any());
        verify(transactionProcessor, never()).processTransfer(any(), any(), any(), any());
    }

    @Test
    @DisplayName("transfer should fall through to the full flow when the cached id exists but the transaction is not found in the database")
    void transferShouldFallThroughWhenTheCachedIdExistsButTheTransactionIsNotFoundInTheDatabase() {
        transactionService = newService();
        UUID cachedTransactionId = UUID.randomUUID();
        Transaction createdTransaction = new Transaction(idempotencyKey.value(), sourceAccountId, destinationAccountId, amount);

        when(idempotencyCache.findTransactionId(idempotencyKey.value())).thenReturn(Optional.of(cachedTransactionId));
        when(transactionRepository.findById(cachedTransactionId)).thenReturn(Optional.empty());
        when(idempotencyCache.tryLock(idempotencyKey.value())).thenReturn(true);
        when(transactionProcessor.processTransfer(idempotencyKey, sourceAccountId, destinationAccountId, amount))
                .thenReturn(createdTransaction);

        Transaction result = transactionService.transfer(idempotencyKey, sourceAccountId, destinationAccountId, amount);

        assertThat(result).isEqualTo(createdTransaction);
        verify(transactionProcessor, times(1)).processTransfer(idempotencyKey, sourceAccountId, destinationAccountId, amount);
    }

    @Test
    @DisplayName("transfer should acquire the lock, call the processor and store the result in cache")
    void transferShouldAcquireTheLockCallTheProcessorAndStoreTheResultInCache() {
        transactionService = newService();
        Transaction createdTransaction = new Transaction(idempotencyKey.value(), sourceAccountId, destinationAccountId, amount);

        when(idempotencyCache.findTransactionId(idempotencyKey.value())).thenReturn(Optional.empty());
        when(idempotencyCache.tryLock(idempotencyKey.value())).thenReturn(true);
        when(transactionProcessor.processTransfer(idempotencyKey, sourceAccountId, destinationAccountId, amount))
                .thenReturn(createdTransaction);

        Transaction result = transactionService.transfer(idempotencyKey, sourceAccountId, destinationAccountId, amount);

        assertThat(result).isEqualTo(createdTransaction);
        verify(idempotencyCache, times(1)).store(idempotencyKey.value(), createdTransaction.getId());
        verify(idempotencyCache, times(1)).releaseLock(idempotencyKey.value());
    }

    @Test
    @DisplayName("transfer should throw TransactionProcessingException when the lock cannot be acquired")
    void transferShouldThrowTransactionProcessingExceptionWhenTheLockCannotBeAcquired() {
        transactionService = newService();

        when(idempotencyCache.findTransactionId(idempotencyKey.value())).thenReturn(Optional.empty());
        when(idempotencyCache.tryLock(idempotencyKey.value())).thenReturn(false);

        assertThatThrownBy(() -> transactionService.transfer(idempotencyKey, sourceAccountId, destinationAccountId, amount))
                .isInstanceOf(TransactionProcessingException.class);

        verify(transactionProcessor, never()).processTransfer(any(), any(), any(), any());
        // Como o lock nunca foi adquirido, ele também não deveria ser liberado.
        verify(idempotencyCache, never()).releaseLock(any());
    }

    @Test
    @DisplayName("transfer should release the lock even when the processor throws an exception")
    void transferShouldReleaseTheLockEvenWhenTheProcessorThrowsAnException() {
        transactionService = newService();

        when(idempotencyCache.findTransactionId(idempotencyKey.value())).thenReturn(Optional.empty());
        when(idempotencyCache.tryLock(idempotencyKey.value())).thenReturn(true);
        doThrow(new RuntimeException("simulated failure"))
                .when(transactionProcessor).processTransfer(idempotencyKey, sourceAccountId, destinationAccountId, amount);

        assertThatThrownBy(() -> transactionService.transfer(idempotencyKey, sourceAccountId, destinationAccountId, amount))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated failure");

        // O finally garante a liberação do lock mesmo com a exceção.
        verify(idempotencyCache, times(1)).releaseLock(idempotencyKey.value());
        // E, como falhou, o resultado nunca deveria ser gravado no cache.
        verify(idempotencyCache, never()).store(any(), any());
    }
}