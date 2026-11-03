# User foreign-key index audit and selected history indexes

V63 adds three B-tree indexes without changing rows, foreign keys, API contracts,
pagination or sort rules:

| Index | Existing query | Reason |
|---|---|---|
| `idx_orders_buyer_created (buyer_id, created_at DESC)` | `OrderRepository.findByBuyerId` | Buyer order history and its count; default sort is `createdAt DESC`. |
| `idx_orders_seller_created (seller_id, created_at DESC)` | `OrderRepository.findBySellerId` | Seller order history and its count, with the same default sort. |
| `idx_negotiations_buyer_created (buyer_id, created_at DESC)` | `NegotiationRepository.findByBuyerId` | Buyer negotiation history and its count; default sort is `createdAt DESC`. |

The callers are `OrderServiceImpl.getBuyerOrders/getSellerOrders` and
`NegotiationServiceImpl.getBuyerNegotiations`. Each sanitizes pageable input with
the existing newest-first default. The index helps the user predicate and default
ordering; other allowed client sort fields can still require a sort. It does not
change ordering or add a tie breaker to the API.

## Existing coverage and deferred candidates

V24's `uq_order_buyer_request (buyer_id, request_id)` only contains rows with a
non-null request ID. It cannot cover a general buyer history or all referencing
rows for a users FK check. Existing due-settlement and due-confirmation indexes
have different leading columns and predicates. V24 has no corresponding seller
history or buyer negotiation index.

Existing B-tree keys already start with user IDs in profiles, user roles, tokens,
seller onboarding/verifications, credit balances/purchases/ledger, posts, chat
rooms/sessions, notifications, device tokens, wallet ownership/withdrawals,
seller reputations/events/faults, return shipping fees and video uploads. The
read-only audit reports actual catalog coverage, including constraints backed by
unique indexes, rather than assuming index names or a clean migration history.

Missing coverage alone is not a reason to add an index. Actor/reviewer/audit IDs,
shipment participants, shipping quote buyers and settlement participants are
deferred: inspected repositories use primary keys, order/inspection keys or staff
queue filters rather than selective predicates on those users columns. Their
existing parent/order indexes serve those queries. Revisit them after measuring
a relevant query or user-delete/FK-check workload. This migration does not attempt
to index every FK or remove existing indexes.

## Read-only catalog audit

Run `scripts/database/audit_user_foreign_key_indexes.sql` against the application
schema selected by `search_path`. It is a single SELECT and can run inside a
read-only transaction. It reports every FK targeting that schema's `users`, its
ordered referencing columns, validation state, and matching index names. Filter
`has_covering_index = false` in a client to inspect gaps.

Coverage requires a valid, ready, live, non-partial B-tree with no expression
keys and all FK columns at the beginning of its search keys in FK declaration
order. Extra trailing search keys and INCLUDE columns are allowed; INCLUDE
columns never count as search keys. Composite FKs are assessed together. Hash,
partial, expression and invalid indexes are conservatively excluded. A differently
ordered composite FK key may still help equality queries, but is deliberately
reported for manual review. Audit coverage describes general FK equality
support, not evidence that an index satisfies every application sort or predicate.

V63 independently checks its two-column history prefixes, so an existing index
with only `buyer_id` is not enough to skip the timestamp index. Equivalent
differently named and longer-prefix indexes are retained. An ascending timestamp
key also serves the default descending order through a backward scan because the
leading user key is fixed and timestamps are non-null. No duplicate is added for
that case.

## PostgreSQL verification

`UserForeignKeyIndexesMigrationTest` starts at V62 and targets V63 explicitly.
It checks row preservation, exactly three additions on a clean baseline, upgrade
idempotence, equivalent/longer prefixes, partial/expression/INCLUDE/trailing keys,
invalid indexes left by a failed concurrent build, composite FKs and read-only
audit execution.

The execution-plan test inserts 100,000 orders and 100,000 negotiations across
1,000 users, runs ANALYZE, and compares the same first 20 rows and count (100)
before and after migration for all three predicates. It records
`EXPLAIN (ANALYZE, BUFFERS)` through JUnit `TestReporter`, requires sequential
scans before V63 and the selected index without a sort afterwards. Fixtures use
unique timestamps, null order request IDs and cancelled orders so pre-existing
partial indexes do not accidentally cover the workload. This synthetic selective
history workload verifies the access paths; it is not a production latency claim.

Run with Docker available, or supply a dedicated disposable loopback database:

```powershell
$env:USER_FK_INDEXES_TEST_JDBC_URL = 'jdbc:postgresql://127.0.0.1:5432/secondlife_ghn_test_indexes'
$env:GHN_TEST_DB_USERNAME = 'postgres'
$env:GHN_TEST_DB_PASSWORD = '<local test password>'
./mvnw.cmd '-Dtest=UserForeignKeyIndexesMigrationTest' test
```

The existing test database helper rejects remote hosts and database names outside
the `secondlife_ghn_test_` prefix. The suite creates and drops separate temporary
databases; the test role needs CREATEDB. Never point these fixtures at application
or production data.

## Rollout and write cost

V63 runs in Flyway's normal transaction using regular CREATE INDEX. Each newly
created index adds storage and write maintenance; order inserts update two indexes
and negotiation inserts update one. Existing equivalent prefixes avoid duplicate
cost. Schedule rollout for an appropriate maintenance window: regular index
builds block writes to the indexed table until the migration transaction ends.
The migration neither uses concurrent index builds inside a transaction nor
modifies applied migrations. Inspect table sizes and run the read-only audit
before rollout; monitor index usage, storage and write latency afterwards.
