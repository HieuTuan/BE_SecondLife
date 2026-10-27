# Identity performance and module boundaries — implementation plan

> Implement the user-approved items 2–5 in the current backend checkout. Preserve the layered architecture and existing changes. No commit, push or production database deployment is included.

**Goal:** add evidenced foreign-key indexes, keep cached authorities consistent with committed security changes, provide a restricted runtime database role, and enforce useful logical module boundaries.

**Architecture:** keep the current Spring Boot monolith, layers and foreign keys. Every authentication reads a small current DB security projection; full user/authority details are cached with a bounded TTL and a role-permissions revision. Separate optional runtime and migration database credentials.

**Tech stack:** existing Java 21, Spring Boot, Spring Data JPA, PostgreSQL, Flyway, JUnit/Mockito. No new runtime dependency.

**Spec:** user-approved assessment in the conversation and `docs/BACKEND_CODING_STANDARDS.md`.

## Constraints and review focus

- Do not change API JSON, JWT claims, money workflows, transaction locking or account policy.
- Do not change login timestamp handling (item 1 was excluded).
- Preserve V1–V62; V63 is reserved for selective indexes, V64 for role-permission revision.
- Follow service/implementation, DTO record, MapStruct and JpaRepository conventions.
- Do not remove FK constraints, move packages to feature roots or add Redis/Kafka.
- Use dedicated loopback databases for migration/role tests. Never use application `.env` database credentials.
- A warm cache must reject a revoked token or newly locked user on the next authentication.
- Permission changes must refresh authorities without requiring all affected users to log in again.
- Cache entries loaded concurrently with invalidation must not overwrite a newer generation.
- Role provisioning must fail when ownership/inherited privileges defeat its guarantees.
- New indexes must preserve data and avoid equivalent leading-key indexes.

## Task 1 — selective FK indexes

- [x] Audit actual query patterns and index metadata; prepare regression tests pinned to V62→V63.
- [x] Observe failing checks, implement V63 and a read-only audit query.
- [x] Verify data preservation, index equivalence detection and selective query plans on local PostgreSQL.

## Task 2 — current security state and bounded authority cache

- [x] Add failing warm-cache tests for account lock and token revocation; add permission-change regression coverage.
- [x] Add `roles.permissions_version` in V64 and a narrow authentication-state repository projection.
- [x] Compare cached details with the current DB state on every HTTP/WebSocket authentication; reload only stale/missing details.
- [x] Increment role revision in the shared entity permission mutations (also covers the seeder); invalidate local cache after successful service commit.
- [x] Bound cache size, expire entries, and protect against in-flight stale writes after eviction.
- [x] Verify cold/warm behavior, rollback, missing users, expired JWT, principal compatibility and actual SQL count/shape.

## Task 3 — restricted runtime role

- [x] Write local privilege tests before the provisioning SQL/profile.
- [x] Add an idempotent admin-run provisioning script, optional runtime profile and rollout guide.
- [x] Verify normal runtime DML, account disabling, denied user deletion/truncation, denied DDL/history writes and separate owner migrations.

## Task 4 — logical module boundaries

- [x] Map ownership/dependency directions within the existing layers.
- [x] Add meaningful dependency checks and an IdentityAccessService contract actually consumed by ListingAccess.
- [x] Preserve documented exceptions; do not introduce unused interfaces or perform wholesale package moves.

### Evidence so far

- Initial red: module checks failed on two expected boundaries; runtime role checks failed because the provisioning script did not exist; V63 test could not find the new migration.
- Cache red: 14 authentication tests, three new warm-cache cases failed with a previously accepted principal (lock, token version, user deletion).
- First coordinated green: 87 tests, zero failures/errors/skips, including all five V63/PostgreSQL plan tests and all three real psql privilege tests.
- Real JPA/PostgreSQL authentication: six integration tests passed, including exactly two cold queries and one small warm query; lock/password/permission changes worked across independent cache instances, rollback preserved the committed principal.
- Fresh read-only review reported no actionable finding.
- Final widened check: **198 tests in 21 affected classes, zero failures/errors/skips**. `mvn -DskipTests package` succeeded; git diff check passed and V1–V62 remained unchanged. Owned local PostgreSQL was stopped after verification.

## Verification and execution notes

Run coordinated Maven commands from the root agent only because all workers share `target/`. Workers own separate files for index, database-role and module tasks. Root owns security/repository/role revision integration. Record red/green evidence and final affected test totals; baseline failures are reported separately. Run a final package build and inspect the diff for accidental credentials or unrelated edits.
