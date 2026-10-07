# Execution ledger — plan: docs/superpowers/plans/2026-10-02-main-flow-1.md

- Description regression RED: finalize returned zero price and performed uncharged valuation. GREEN after removing the price call (MainFlowDescriptionTest).
- AI output tests RED: missing provider; GREEN: structured VND range, malformed/negative/inconsistent/text-number output (OllamaAiPriceProviderTest).
- MainFlow1IntegrationTest: 10/10 pass on actual PostgreSQL container, covering free draft/chat/finalize, valuation and publish retry/concurrency, failure rollback, RBAC/owner/validation, duplicate content/image, rate limits and inspection transition.
- Migration V19 successfully applied on a disposable PostgreSQL 16 container.
- Ruling: keep the current feature branch/workspace and leave changes uncommitted — user requested implementation here; preserve their repository state — cost if wrong: changes need moving to another branch later.
- Ruling: use synchronous rollback semantics for provider failures, including malformed output — matches the approved design of charging only usable successful valuation — cost if wrong: a model execution followed by rollback is unpaid and may execute again on retry.
- Ruling: MainFlow1IntegrationTest uses ddl-auto=none, consistent with existing migration tests — full validation fails on pre-existing missing chat_messages, outside Main Flow 1 — cost if wrong: unrelated schema completeness is not verified; Main Flow tables are exercised through repositories/API.
- Ruling: accepted high-value submit consumes Listing Credit on transition to PENDING_INSPECTION — keeps the existing inspection path; retries do not charge again — cost if wrong: inspection rejection has no automatic credit refund in this scope.
- Ruling: initial duplicate guard checks exact normalized content and identical image bytes within the seller's submitted posts — avoids declaring cross-seller fraud from one signal — cost if wrong: cropped/re-encoded images and paraphrased duplicates may pass; embedding/fraud-review flow remains separate.
- Final review: fresh read-only reviewer found no Critical issues, one Important migration fixture issue, no Minor findings.
- Final: fixed legacy posts fixture missing user_id — full suite reproduced V19 index failure; fixture now includes legacy user_id and explicit insert columns. GREEN: MainFlowV19MigrationTest 1/1 and full suite 239/239.
- Existing ownership regression fixture now mocks findByIdForUpdate alongside findById; expectations remain 403/no writes. Existing seller-init test now uses multipart with valid categoryId because file DTO is not JSON.
- Error handling regression: previous JSON call to newly multipart-only init returned generic 500. Added 415 handling and 400 for malformed JSON/UUID; integration assertions added.
- Final: Ruling: broader Hibernate/Flyway schema completeness remains baseline scope — not required to exercise Main Flow tables — cost if wrong: unrelated tables may still require separate migrations.
- Final: Ruling: inspection payments/refunds, re-up, embedding/fraud workflow remain excluded — approved scope — cost if wrong: those workflows need a separate implementation before claiming full SRS compliance.
- Final: Ruling: real model valuation accuracy and prompt-injection resistance remain unproven — integration tests mock external AI, and moderation prompt largely predates this change — cost if wrong: model answers can be inaccurate or manipulated; live model evaluation remains necessary.
- Final: Ruling: publish throttle counts accepted posts only — explicitly documented bounded acceptance policy — cost if wrong: repeated rejection/insufficient-credit requests can still call moderation; attempt throttling needs further work.
- Final: Ruling: legacy images retain null fingerprints — remote assets are not downloaded during migration — cost if wrong: historical image-only duplicates are not detected unless content also matches.
- Final verification: mvn -q test exit 0; 239 current tests, 0 failures, 0 errors, 0 skipped. Excluded one stale CheckDbUserTest report whose source does not exist. Actual Testcontainers PostgreSQL used; AI/Cloudinary mocked. git diff --check passed.
- Completion: all plan tasks complete; keep edits on existing feature/tuan-identity branch without committing, merging, pushing or restarting the application. FE guide opened in Codex. No deferred minor findings.

## Startup checksum correction

- Existing application database had already applied original V19 (checksum 1319288901) during development. Later additions to the same file changed its checksum to 602796874, blocking startup validation.
- Read-only database inspection confirmed V19 success and absence of the later audit check, immutable trigger and valuation assignment policy.
- AppliedV19ChecksumTest reproduced the mismatch RED (expected 1319288901, actual 602796874).
- Restored the exact original V19 checksum and moved the additions unchanged to V20__protect_ai_valuation_audit.sql. No repair, history update or database migration was run on the application database.
- GREEN: checksum regression, upgrade V19→V20, MainFlow integration and Spring startup/OpenAPI tests: 14 tests passed across focused runs. The current database's applied migration history also passed Flyway validate (pending V20 allowed).
- Restart backend with FLYWAY_ENABLED=true to apply V20. This verification does not claim the user's running backend has already restarted successfully.
