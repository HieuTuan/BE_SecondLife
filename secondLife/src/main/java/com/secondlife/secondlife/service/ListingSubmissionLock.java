package com.secondlife.secondlife.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@RequiredArgsConstructor
public class ListingSubmissionLock {
    private final JdbcTemplate jdbc;
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock() { jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> { }, 0x534C4C495354494EL); }
}
