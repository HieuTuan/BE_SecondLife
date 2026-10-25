# Seller return shipping fees implementation plan

**Goal:** Implement the approved policy: seller fault makes the seller liable for return shipping; buyer receives the full product refund; insufficient seller funds become recorded debt.

**Architecture:** Extend the existing dispute refund transaction. After carrier delivery and the approved SELLER decision, collect the carrier's actual return fee up to the seller wallet balance, record the cash transfer in both ledgers, and persist the remaining debt in a fee assessment. No new dependencies, no negative wallets, and no deduction from the buyer refund.

**Scope and decisions**

- Apply only to the selected BUYER_TO_SELLER shipment of a CONFIRMED RETURN_AND_REFUND report whose responsibleParty is SELLER.
- Assess at successful refund, not approval or booking. Use actualFee only; zero is valid. If missing, save PENDING_FEE and still refund the buyer. After carrier sync, staff retries the existing refund API to assess that pending fee.
- Persist one fee assessment per shipment/order/report. Store feeAmount, collectedAmount, debtAmount, assessedBy/At. Assessed snapshots cannot change; later carrier fee changes require separate reconciliation and never silently charge again.
- Lock order remains order -> shipment/report -> system wallet MAIN -> user wallets. System shipping balance increases only by collected cash, never by debt. The existing system ledger uniqueness protects one fee event/order.
- Seller charge journal uses RETURN_SHIPPING_FEE and shipmentId reference. Buyer product refund still uses finalPrice and existing order reference. Buyer refund, charge/debt, reputation and report audit commit or roll back together.
- Debt is a receivable recorded on each assessment; aggregate it for the seller. Do not duplicate the same debt into a wallet balance column. Automatic recovery on deposits/earnings and manual debt settlement are outside this change.
- Historical completed refunds are not retroactively charged. Replays only assess an existing PENDING_FEE assessment.
- Private fee APIs allow only the owning seller or authorized dispute staff/admin. Do not add seller debt to report/shipment DTOs readable by buyers.
- Follow docs/BACKEND_CODING_STANDARDS.md: Service + Impl, record DTOs, concrete MapStruct mapper, JpaRepository, explicit imports and a new Flyway migration. Preserve existing local changes; no commit/push/deploy.

## Tasks

- [x] Write and run a PostgreSQL regression proving the current refund fails to collect a funded seller's return fee.
- [x] Implement partial wallet collection and paired system shipping cash journal using the existing wallet lock conventions.
- [x] Add fee assessment/debt persistence, immutable audit constraints, owner/staff read APIs and pending-fee replay.
- [x] Integrate assessment into the existing staff refund; preserve full buyer refund, responsibility checks and retry behavior.
- [x] Verify full/partial/zero balances, actual versus quote, free/missing fees, non-seller decisions, concurrency, rollback after charge, privacy, and no retroactive charges.
- [x] Update Swagger test JSON and FE/BE documentation; run relevant regression suites and obtain an independent code review.

## Verification

Use a disposable PostgreSQL cluster and dedicated secondlife_ghn_test_* databases, never the user's application database. Run OrderDisputeIntegrationTest, related migration/authorization/wallet/shipping tests and PackageArchitectureTest. Report passed/skipped/failing counts accurately. No real carrier bookings or money operations are used in tests.

Final focused verification: **126 tests passed, 0 failures/errors/skips** (`target/return-fee-final.log`). Full repository run: 1,128 tests, 1,040 passed, 17 failures, 1 error, 70 skipped; unrelated failures are documented in [the verification report](../../SELLER_RETURN_SHIPPING_FEE_VERIFICATION.md). No commit, push or deployment performed.
