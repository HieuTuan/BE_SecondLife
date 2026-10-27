# Missing Foreign Keys Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore missing database relationships without changing existing application rows, API contracts or entity mappings.

**Architecture:** One PostgreSQL Flyway V61 migration restores only relationships already declared by earlier migrations. Detect equivalent keys structurally; preserve existing constraints and deletion actions. Add missing keys as NOT VALID, validate clean relationships, and leave legacy orphan relationships pending without rewriting data.

**Tech Stack:** Existing Spring Boot / Flyway, PostgreSQL, JUnit 5 / JdbcTemplate.

**Spec:** User-approved scope in the 2026-10-27 conversation; docs/BACKEND_CODING_STANDARDS.md.

## Global Constraints

- No table/column rename, row deletion, backfill, API/service/DTO changes, dependency addition or remote database execution.
- Never modify already-applied migrations; do not alter existing equivalent foreign-key actions.
- Exclude polymorphic/external identifiers and retained AI/audit snapshot identifiers.
- Tests use loopback-only disposable GhnTestDatabase databases.

## Review Focus

- Fresh schema already has equivalent keys with different names: do not duplicate or replace them.
- Drifted schemas miss composite supporting unique keys: restore them without touching data.
- Historical orphan references: retain rows, reject newly invalid references, document pending validation.
- Draft deletion/custom permission deletion: retain historical identifiers without introducing blocking FKs.
- API transaction ordering, financial snapshots and original delete actions stay unchanged.

## Task 1: Migration and verification

**Files:** Create V61__restore_missing_foreign_keys.sql and MissingForeignKeysMigrationTest.java; pin the existing V60-specific test to target 60; document deployment/validation.

- [x] Audit original migrations, current mappings and backup metadata; identify 46 missing declarations and two required composite keys.
- [x] Write regression tests and run before V61 exists; confirm upgrade fails because migration 61 is absent.
- [x] Implement V61 with structural duplicate detection and safe legacy validation.
- [x] Verify clean/drifted/orphan schemas, composite key enforcement and original deletion semantics.
- [x] Upgrade an isolated local copy of the user backup and compare all application row fingerprints before/after.
- [x] Run relevant API integration and architecture checks; inspect final diff and report exact results (112/114 related checks; two failures reproduced on V60; full-suite limits documented).
