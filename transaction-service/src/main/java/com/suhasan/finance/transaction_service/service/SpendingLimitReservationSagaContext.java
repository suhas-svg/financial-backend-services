package com.suhasan.finance.transaction_service.service;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SpendingLimitReservationSagaContext {
    private final ThreadLocal<Context> current = new ThreadLocal<>();

    public Scope open(String userId, String idempotencyKey) {
        Context previous = current.get();
        current.set(new Context(userId, idempotencyKey));
        return () -> {
            if (previous == null) {
                current.remove();
            } else {
                current.set(previous);
            }
        };
    }

    public Optional<Context> current() {
        return Optional.ofNullable(current.get());
    }

    public record Context(String userId, String idempotencyKey) {
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
