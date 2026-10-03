package com.secondlife.secondlife.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CatalogWriteLock {
    private final JdbcTemplate jdbc;
    /** Serialize catalog names/seeding across application instances until transaction end. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock() { jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> { }, 0x534C434154414C4FL); }
}
