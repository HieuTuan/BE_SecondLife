# Layered Package Layout Implementation Plan

**Goal:** Apply the user's approved global layered layout while preserving Service + ServiceImpl and runtime behavior.
**Spec:** User-approved tree in this chat: controller, dto/request, dto/response, entity, repository, mapper, service/impl, integration, scheduler, seeder, config, security, exception, enums, common.
**Architecture:** Keep the existing Spring Boot application and root package. Relocate Java types and update imports/callers; retain external adapters under integration.
**Tech stack:** Java 21, Spring Boot, Maven, JUnit.

## Constraints
- Preserve endpoints, JSON fields, validation, bean names, transactions, database schema, and existing uncommitted work.
- Do not merge services, remove interfaces, change dependencies, commit, or create a worktree.
- Service interfaces live directly in service; business implementations and implementation helpers live in service/impl.
- Notification delivery types/adapters live in integration/notification; notification enums live in enums.
- DTOs live in dto/request or dto/response; nested DTOs retain their current enclosing types.

## Tasks
- [x] Inspect the checkout and run the existing architecture tests: 4 passed.
- [ ] Add a source-layout assertion to PackageArchitectureTest and observe the expected failure.
- [ ] Relocate types; update explicit imports, wildcard imports, same-package references, and affected tests.
- [ ] Update coding standards to the approved global layered layout.
- [ ] Run a clean architecture test, the full test suite, and a behavior-preservation comparison against the saved source baseline.

## Review focus
- Changed Java packages must agree with filesystem locations.
- Splitting DTO packages must preserve previously implicit imports and nested type references.
- Moving component classes must preserve their simple names and bean names.
- Clean compilation must remove stale bytecode from old packages before component scanning.
- Existing transaction boundaries and schema/API names must remain unchanged.

## Execution
Execute inline in the current checkout, which contains user changes. Original Java sources and a move manifest are saved outside the repository for comparison. The user's explicit request to apply the approved tree is execution authorization.
