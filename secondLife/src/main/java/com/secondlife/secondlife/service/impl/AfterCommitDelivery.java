package com.secondlife.secondlife.service.impl;

import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class AfterCommitDelivery {
    private AfterCommitDelivery() {}

    static void run(Runnable delivery) {
        Runnable safe = () -> {
            try {
                delivery.run();
            } catch (RuntimeException e) {
                LoggerFactory.getLogger(AfterCommitDelivery.class).warn("Post-commit delivery failed ({})", e.getClass().getSimpleName());
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { safe.run(); }
            });
        } else {
            safe.run();
        }
    }
}
