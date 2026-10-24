# Verification: seller return shipping fees

Date: 2026-10-25. Scope: the seller return shipping fee and debt extension described in [the implementation plan](superpowers/plans/2026-10-25-seller-return-shipping-fees.md).

## Regression evidence

Before implementation, `OrderDisputeIntegrationTest#sellerFaultCollectsReturnShippingWithoutReducingBuyerProductRefund` ran against a new local PostgreSQL database and failed its seller balance assertion: the old code retained 50,000 instead of collecting the 27,000 return fee. One failure, no errors or skips. Log: `target/return-fee-red.log`.

The completed implementation passed all 40 `OrderDisputeIntegrationTest` cases and all 4 `ReturnShippingFeeMigrationTest` cases in the full suite. These include funded/partial/zero/missing wallets, free and missing actual fees, pending assessment after sync/retry, multiple orders for one seller, concurrent refunds, rollback after seller debit, buyer privacy, immutable assessments, and no historical backfill. External carrier, mail and storage calls are mocked; PostgreSQL transactions, locks, constraints and migrations are real.

Final focused run on fresh databases: **126 tests passed, zero failures/errors/skips; BUILD SUCCESS**. Log: `target/return-fee-final.log`. Selected classes: `OrderDisputeIntegrationTest`, `ReturnShippingFeeMigrationTest`, `OrderDisputeMigrationTest`, `SystemWalletMigrationTest`, `LalamoveShippingIntegrationTest`, `LalamoveReturnSafetyTest`, `LalamoveShipmentServiceTest`, `WalletServiceWithdrawalTest`, `WalletControllerTest`, `OrderDisputeAuthorizationTest`, `OrderDisputeDtoCompatibilityTest`, `PackageArchitectureTest`, and `UserReportReturnMapperTest`.

## Full repository suite

Command: `mvn -o -Dspring.datasource.hikari.maximum-pool-size=4 -Dspring.test.context.cache.maxSize=4 test`, with separate disposable loopback databases for the supported `*_TEST_JDBC_URL` settings.

Result: **1,128 tests: 1,040 passed, 17 failures, 1 error, 70 skipped. BUILD FAILURE.** Tests requiring Docker directly were skipped because Docker was unavailable. Full output: `target/return-fee-full-suite.log`.

The following failures were inspected separately from the fee changes; their failing tests and implicated existing behavior were not changed by this task:

- `SecondLifeApplicationTests.healthAndOpenApiArePublic`: namespace assertion rejects the existing `/api/video/upload-signature` route from the prior video feature.
- `DefaultCommissionMigrationTest.adoptsExistingCommissionTablesAndRetiresScopedPoliciesWithoutChangingHistoricalSnapshots`: expects three migrations while upgrading without a target to the latest schema. The pre-existing V34–V58 migrations already exceed that expectation.
- `SharedBankTransferMigrationTest.freshDatabaseInstallsOriginalDeviceMigrationAndSeparateCheckoutMigration`: assumes the latest version is 49.1 despite existing later migrations.
- `SharedBankTransferMigrationTest.sharedV52HistoryUpgradesWithoutRepairOrLosingExistingDeposit`: history validation expectation fails before V59 is executed.
- `MainFlow1IntegrationTest.concurrentPublishRetryConsumesOneCreditAndUnapprovedSellerCannotPublish`: expectation conflicts with the existing SELLER role publish exemption.
- `MainFlow1IntegrationTest.inspectionChargesOnlyWhenPassedAndNeverWhenFailed`: expects ACTIVE for a PENDING_INSPECTION fixture; the current activation branch accepts PENDING_APPROVAL.
- `MainFlow1IntegrationTest.sixImagesAreStoredInOrderAndAllSentToAi` and `draftChatAndFinalizeAreFreeThenValuationAndPublishChargeSeparately`: fixtures do not supply the AI_CHAT credit required by the existing initialization flow.
- Nine `SellerReputationIntegrationTest` methods fail when creating reports using fixtures that do not meet the existing delivered-order/48-hour eligibility rule: `buyerReturnAfterConfirmedLatenessDoesNotBecomeASellerReturnFault`, `confirmedFaultBeforeSettlementDoesNotLaterGainGoodPoints`, `confirmedSellerFaultConvertsGoodToBadOnceAndResolutionRetryCannotChangeDecision`, `latePickupAloneNeverPenalizesSellerAndStaffCanAttributeCarrierFault`, `multipleFaultTypesForOneOrderStillCountAsOneFailedTransaction`, `oldOrOnTimeOrdersCannotBeConfirmedAsLateAndReviewRollsBack`, `orderFraudEscalationCountsOnceAndSecondOrderTriggersRestriction`, `pendingDismissedAndCarrierFaultReportsDoNotReduceReputation`, and `twoConcurrentReportsForSameSaleCannotCountTheBadOutcomeTwice`.
- `OrderConfirmationIntegrationTest.pendingOrderReportAndTimeoutStillCreateOnlyOneBadOutcome`: creates an order dispute before carrier delivery and receives the existing eligibility conflict.

An independent review found no substantive correctness or security issues in the scoped fee changes. The full suite is not claimed to pass.

## Operational boundaries

- V59 creates the assessment table and new journal compatibility; historical refunds are not backfilled.
- Fee/debt recording occurs inside the same transaction as the buyer product refund. No real carrier bookings or money transfers were made during tests.
- Collected cash increases the system shipping balance. Outstanding debt is a receivable, not available cash or platform commission revenue.
- Outstanding debt is recorded and readable; automatic debt recovery, manual debt settlement and later carrier fee adjustments are outside this feature.
- Swagger workflow and JSON: [SWAGGER_SELLER_RETURN_SHIPPING_FEE_TEST.md](SWAGGER_SELLER_RETURN_SHIPPING_FEE_TEST.md).
