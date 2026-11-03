# Wallet transaction details

User request: enrich transaction history in the backend only. Keep the existing wallet accounting and HTTP response envelope.

## Scope and approach

Extend the existing history DTO with a Vietnamese description, VND currency, direction, explicit reference type, and historical before/after balances. Reuse WalletQueryService and WalletMapper; add an owner-scoped detail endpoint and optional type/from/to filters to the list endpoint.

Alternatives considered: infer business sources by probing several tables, or store the reference type when posting the transaction. Store the type: a UUID currently identifies several unrelated kinds of business records, and probing can misclassify old transactions. Do not join unrelated records or expose another user's business data. Keep the authoritative referenceId so clients can use the existing business APIs.

This is an implementation choice within the requested BE enhancement. No frontend changes, new dependency, arbitrary-user admin access, or extra debit/credit operation.

## Contract

- GET /api/v1/wallets/me/transactions keeps PageResponse and the original six item fields.
- Optional type, from and to: ISO-8601 instants, from inclusive, to exclusive; invalid or empty date intervals return 400.
- Unspecified sorting uses createdAt DESC, id DESC. Supported sort fields are id, createdAt, amount, type and referenceId; unsupported fields return 400.
- GET /api/v1/wallets/me/transactions/{transactionId} returns the same enriched item. Missing and foreign-owned transactions both return 404.
- New fields: description, currency, direction (CREDIT/DEBIT/NEUTRAL), referenceType, balanceBefore and balanceAfter.
- referenceType identifies ORDER, INSPECTION_ORDER, DEPOSIT_REQUEST, WITHDRAWAL, SHIPMENT, CREDIT_PURCHASE, TOPUP_PACKAGE or UNKNOWN. It is independent of the transaction type (PAYMENT, REFUND, etc.).
- Legacy rows retain UNKNOWN and null balances. Do not reconstruct financial history from the current wallet balance.
- Before/after snapshots are recorded atomically alongside the existing transaction while the existing wallet lock is held. No lock-order or monetary calculation changes.
- Reject amounts that cannot be represented exactly at NUMERIC(18,2) fractional precision before mutation (400), rather than allowing PostgreSQL to round amount and balance independently. Extra trailing zeros remain accepted; do not round client money.
- A withdrawal debit records the reservation/removal of wallet funds; it does not claim that a bank transfer has completed. A rejected withdrawal refund gets a distinct explanation.
- Bank payments that never move the personal wallet remain outside this ledger. Uncollected return shipping debt remains outside the cash movement ledger.

## Verification

Pin descriptions and signed directions, positive/negative balance snapshots, explicit reference categories at non-order callers, ownership isolation, deterministic pagination, filtering boundaries, invalid parameters, old row compatibility, and migration constraints. Run focused tests using a disposable PostgreSQL database, never the running application's database.
