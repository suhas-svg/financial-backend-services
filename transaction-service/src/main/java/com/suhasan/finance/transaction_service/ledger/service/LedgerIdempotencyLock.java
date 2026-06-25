package com.suhasan.finance.transaction_service.ledger.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

@Component
public class LedgerIdempotencyLock {

    private final EntityManager entityManager;

    public LedgerIdempotencyLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void acquire(String scope, String key) {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:lockKey, 0))")
                .setParameter("lockKey", scope + ":" + key)
                .getSingleResult();
    }
}
