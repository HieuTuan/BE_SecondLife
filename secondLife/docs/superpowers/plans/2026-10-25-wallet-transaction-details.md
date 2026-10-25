# Wallet Transaction Details Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add accurate, private transaction details and history filters to the existing BE wallet APIs.

**Architecture:** Extend the existing ledger entity/record and MapStruct mapper. Keep the write paths' locking and accounting; add explicit source categories and owner-scoped repository reads through WalletQueryService.

**Tech Stack:** Existing Java 21, Spring Boot, Spring Data JPA, MapStruct, Flyway/PostgreSQL and JUnit/Mockito.

**Spec:** ../specs/2026-10-25-wallet-transaction-details-design.md

## Global Constraints

- Backend only; follow docs/BACKEND_CODING_STANDARDS.md.
- Preserve original fields, response envelope, signed amounts, money movement and lock order.
- Legacy snapshot fields remain null and referenceType remains UNKNOWN.
- No production database writes or automatic commits/pushes.

## Review Focus

- Foreign transaction UUIDs must return 404, just like unknown UUIDs.
- Old rows and rows with zero amounts must serialize without fabricated snapshots.
- A rejected withdrawal is a cash refund; a pending withdrawal is not proof of bank payout.
- Non-order references must not be labelled as normal orders.
- Default time ordering needs a unique tie-breaker and filter bounds must not overlap adjacent ranges.

## Tasks

### 1. Ledger details

Files: WalletTransaction, WalletTransactionResponseDTO, WalletMapper, new reference/direction enums, V60 migration, WalletServiceImpl and non-order write callers.

- [x] Add failing assertions for ledger descriptions/directions and before/after balance snapshots.
- [x] Verify expected failures, then add nullable snapshots and explicit reference categories without changing amounts.
- [x] Update non-order callers and their existing tests to pass the correct category.
- [x] Verify new mapper/snapshot tests and existing withdrawal, credit and inspection tests.

### 2. Private detail and history queries

Files: WalletTransactionRepository, WalletQueryService/Impl, WalletController and query/controller tests.

- [x] Add tests for owner-only details, empty history, optional filters and sort validation.
- [x] Add owner-scoped queries, half-open date filtering, stable default ordering and GET detail.
- [x] Verify existing response shape and rejected/malformed queries.

### 3. Database/API verification and documentation

Files: wallet history integration/migration tests, backend Swagger guide.

- [x] Migrate an isolated PostgreSQL fixture; assert legacy compatibility and snapshot consistency.
- [x] Verify owner/foreign-user HTTP behavior, date boundaries and pagination against the database.
- [x] Run focused regression tests, inspect the diff and publish exact API examples/limitations.

