# Shipping Vehicle Catalogue Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement the following task in this session.

**Goal:** Expose normalized Lalamove vehicle limits for a collapsible FE transport panel and immediate local validation; deliver a detailed Vietnamese FE handoff without changing frontend code.

**Architecture:** Add GET /api/v1/shipping/vehicles with optional paired pickup latitude/longitude. Preserve /cities and /provinces. Reuse one normalization path for catalogue responses and automatic vehicle selection. The response carries provider, application minimum provenance, positive input/dimension rules, and cities with per-vehicle limits in grams/cm.

**Tech Stack:** Java 21, Spring MVC, Jackson 3, existing JUnit/MockMvc.

**Spec:** User request in this chat; detailed contract and integration guide at docs/FE_SHIPPING_VEHICLE_CATALOGUE_GUIDE.md.

## Global Constraints

- Change backend and backend Markdown only; preserve all existing frontend changes.
- Per-vehicle minWeightGram=1 is an application input rule, identified by minWeightSource=APPLICATION; maximum capacity is from Lalamove.
- Shared rules keep height upright and permit swapping length/width only; inclusive maximums.
- Catalogue checks capacity only; quotes remain authoritative for route and fee.
- Do not introduce fixed 50 kg/200 cm limits, new dependencies, migrations or raw-API breaking changes.

## Review Focus

- Normalize g/kg/t and cm/m/mm consistently across API and quotation selection.
- Omit unverified/foreign service entries; respect configured allowed cities.
- Validate optional coordinate pair and bounds before provider I/O; distinguish no vehicles from provider failure.
- Document empty/loading/error/stale states and multi-vehicle matches without promising route availability.
- Include exact min/max provenance, types, API examples, frontend algorithms, UI checklist and manual QA matrix.

## Task 1: API and FE handoff

- [x] Add MockMvc coverage for normalized metadata, city filtering, invalid coordinates, empty/malformed/failed provider catalogue and backwards compatibility. Run before production changes and observe missing endpoint failures.
- [x] Add ShippingVehicleCatalogResponse, controller/service endpoint and shared selector normalization.
- [x] Deliver the detailed Vietnamese guide, link both existing shipping guides to it.
- [x] Run catalogue/selection regression checks, full suite and diff checks; independently review changed backend and guide. Record any unrelated existing failure explicitly.

Working-tree edits only; no commit or publish was requested.


## Verification record — 24/10/2026

- RED: the new API suite ran before implementation: 15 tests, 14 failures for the missing endpoint, no errors.
- GREEN, repeated after final explicit-import cleanup: `mvn -o "-Dtest=ShippingVehicleCatalogApiTest,ShippingCatalogServiceTest,LalamoveAutoVehicleTest,ShippingParcelApiValidationTest,PackageArchitectureTest,ShippingContactApiValidationTest" test` — 88 tests, zero failures/errors/skips.
- Full backend suite: `mvn -o test` — 1,151 tests, one pre-existing failure, no errors, 285 skipped. `SecondLifeApplicationTests.healthAndOpenApiArePublic:47` rejects the existing `/api/video/upload-signature` path. It also failed in the previous baseline; this change does not modify that route or test.
- An independent read-only backend review found no Critical or Important issues. The parent reviewed the completed FE guide against the implementation and existing frontend API client.
- All seven JSON examples parse; all four TypeScript/TSX examples transpile. Executed unit-conversion and capacity-matching examples cover 1 g precision, integer bounds, inclusive maximums, decimal capacities, length/width swapping and upright height. Relative Markdown links resolve; code fences are balanced.
- Backend whitespace checks pass. Frontend status and the CreateListingView.tsx SHA-256 match the start of this task; no frontend file was edited here.
- Verification uses provider fixtures; no live Lalamove quotation/order or deployment was performed.
