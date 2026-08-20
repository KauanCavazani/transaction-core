package com.transactioncore.paymentservice.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public class IdempotencyCache {

    private final StringRedisTemplate redisTemplate;

    public IdempotencyCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<UUID> findTransactionId(String idempotencyKey) {
        String transactionId = redisTemplate.opsForValue().get(idempotencyKey);
        return transactionId != null ? Optional.of(UUID.fromString(transactionId)) : Optional.empty();
    }

    public void store(String idempotencyKey, UUID transactionId) {
        redisTemplate.opsForValue().set(idempotencyKey, transactionId.toString(), Duration.ofHours(24));
    }

    public boolean tryLock(String idempotencyKey) {
        String lockKey = "lock:" + idempotencyKey;
        Boolean success = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCK", Duration.ofSeconds(10));
        return Boolean.TRUE.equals(success);
    }

    public void releaseLock(String idempotencyKey) {
        redisTemplate.delete("lock:" + idempotencyKey);
    }

}
