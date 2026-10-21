# Order dispute returns implementation plan

Goal: Accept buyer order disputes only before delivery + 48 hours and before settlement; freeze escrow, approve a linked return, and refund the product price once after physical return.

Standards: docs/BACKEND_CODING_STANDARDS.md. No commits, pushes, FE changes or dependency additions. Work in the current clean backend checkout.

1. Add backward-compatible record DTO fields, MapStruct mapping, report/return audit columns and PostgreSQL V55 migration. Verify JSON defaults and existing data preservation.
2. Add OrderDisputeService/Impl for the shared window, held funds, approval, shipment binding and refund audit. Update existing report and shipment orchestration; retain unrelated listing reports and physical outbound-return refunds.
3. Protect return approval/booking/refund with existing STAFF permissions. Send in-app lifecycle notifications and document the API workflow.
4. Verify deadline equality, confirmation/settlement races, multiple holds, late retries, failed carrier requests, wrong-order shipments, rollback and repeated refund using unit and PostgreSQL integration tests. Review final diff against standards.

Execution: data/contracts and integration tests delegated with disjoint files; main agent implements shared workflow. The dispute window is fixed at 48h; orderReleaseClock is reused and automatic release retains its existing 48h default. Only delivered, unsettled orders may create order reports; existing reports can be reviewed after the window closes.

## Verification

- [x] Data contracts and mapper compatibility.
- [x] Report acceptance, hold, staff decision, carrier return and refund audit.
- [x] Method authorization, existing listing/shipping regression tests.
- [x] Real PostgreSQL rollback and concurrent request tests.
- [x] V54-to-V55 upgrade, historical data/backfill and constraint tests.

Result: 149 workflow/architecture/security/compatibility tests plus 1 PostgreSQL upgrade test passed; no failures, errors or skips. External carrier and notification transports were mocked. Reviewed independent shipping holds, lock order and persisted timestamp precision. No commits or deployment performed.
