# Wallet transaction history verification

Scope: backend only; additive transaction details, explicit reference kinds, balance snapshots, owner-only details and filtered history.

## Result

The final selected regression run completed successfully: **126 tests, 0 failures, 0 errors, 0 skipped**. This is not a claim that the entire repository test suite was run.

| Test class | Tests |
|---|---:|
| PackageArchitectureTest | 6 |
| WalletControllerTest | 7 |
| OrderBankTransferIntegrationTest | 21 |
| OrderDisputeIntegrationTest | 40 |
| ReturnShippingFeeMigrationTest | 4 |
| WalletTransactionDetailsMigrationTest | 1 |
| WalletTransactionHistoryIntegrationTest | 9 |
| MapStructMapperTest | 4 |
| InspectionEscrowExpirySchedulerTest | 1 |
| InspectionEscrowServiceTest | 4 |
| WalletServiceWithdrawalTest | 7 |
| WalletTransactionDetailsTest | 13 |
| WalletTransactionSnapshotTest | 9 |

Command:

```shell
mvn -o -Dtest=WalletTransactionHistoryIntegrationTest,WalletTransactionDetailsMigrationTest,WalletTransactionSnapshotTest,WalletTransactionDetailsTest,WalletServiceWithdrawalTest,WalletControllerTest,InspectionEscrowServiceTest,InspectionEscrowExpirySchedulerTest,MapStructMapperTest,PackageArchitectureTest,ReturnShippingFeeMigrationTest,OrderDisputeIntegrationTest,OrderBankTransferIntegrationTest test
```

Database integration tests ran on dedicated loopback PostgreSQL 18.4 test databases at port 55439, selected through WALLET_HISTORY_TEST_JDBC_URL, ORDER_DISPUTE_TEST_JDBC_URL, BANK_PAYMENT_TEST_JDBC_URL and GHN_FLOW_TEST_JDBC_URL. GhnTestDatabase rejects arbitrary URLs and only accepts its secondlife_ghn_test_ database naming convention. No tests used the running application's database.

Local run log: `target/wallet-history-final-verification.log`.

## Regression evidence

- Original snapshot tests were run before implementation: five expected assertion failures, no errors or skipped tests (`target/wallet-history-red.log`).
- Initial implementation passed 56 selected tests, including real HTTP/database tests.
- Independent read-only review found a precision edge case: 50000.005 could be accepted for a withdrawal but independently rounded by NUMERIC(18,2), violating snapshot consistency.
- Reproduced the issue before fixing: the real withdrawal API returned 500 instead of 400, and four posting-method tests failed due to absent precision validation (`target/wallet-history-precision-red.log`).
- Fixed by checking representability at two fractional decimal places before any wallet mutation. No client money is rounded. Extra trailing zeros remain representable and accepted.
- Re-ran the expanded 126-test selection successfully. Independent review found no remaining blocking issues in the fix.

## Covered contracts

- Original response fields and PageResponse envelope remain available; MapStruct generates the enriched DTO.
- Signed amounts, directions, VND currency and distinct withdrawal-refund / return-shipping descriptions.
- Snapshots match actual successful debit/credit operations; legacy snapshots remain null with UNKNOWN provenance.
- Database migration preserves old financial values and rejects inconsistent or partial snapshots.
- Authenticated own details return 200; foreign and unknown transaction IDs both return 404; missing authentication returns 401.
- Filtering includes from and excludes to; filtering never mixes different users' wallets.
- Equal timestamps use deterministic id ordering, including successive pages.
- Invalid type/date/interval/sort inputs return 400.
- Empty history does not create a wallet.
- Existing bank-direct-to-escrow, disputed refunds, seller return shipping collection/debt and inspection regressions still pass.

The pre-existing V59 migration test now targets V59 explicitly, keeping that test's historical-row comparison about the V59 migration instead of also applying the unrelated additive V60 fields.

API examples and operational limits: [BE_WALLET_TRANSACTION_HISTORY_GUIDE.md](BE_WALLET_TRANSACTION_HISTORY_GUIDE.md).
