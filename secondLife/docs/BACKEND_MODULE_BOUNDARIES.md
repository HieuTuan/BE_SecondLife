# Logical backend module boundaries

SecondLife remains one Spring Boot application with one database. The modules below describe business ownership and service contracts inside that monolith. They do not change table ownership, JPA relations, foreign keys, API DTOs, transactions or deployment.

The mandatory [backend coding standards](BACKEND_CODING_STANDARDS.md) still apply: `controller/`, `service/`, `service/impl/`, `repository/`, `entity/`, `mapper/` and DTO folders remain global layers. Do not create feature roots such as `identity/service` or `wallet/repository`.

## Business ownership

| Logical context | Responsibility and primary data | Existing service contracts |
|---|---|---|
| Identity | Accounts, profiles, roles, permissions, tokens, seller onboarding and identity verification | `AuthService`, `TokenService`, `UserService`, `AdminRbacService`, `AdminUserRoleService`, `SellerVerificationService`, `SellerOnboardingService`; `IdentityAccessService` provides role lookup for business callers |
| Listing | Catalog, listing drafts, publication, listing access and seller reputation policy; `Post`, `Category`, `Item` | `CatalogService`, `PostService`, `ListingDraftService`, `ListingPublicationService`, `ListingAccessService`, `MarketplaceListingService`, `SellerReputationService` |
| Order | Purchase and negotiated price workflow, confirmation, cancellation and disputes; `Order`, `Negotiation` | `OrderService`, `NegotiationService`, `OrderConfirmationService`, `OrderDisputeService`, `PaidOrderService` |
| Inspection | Inspection requests, checklists, results, fee proposals and seals | `InspectionService`, `InspectionChecklistService`, `InspectionOrderChecklistService`, `InspectionPricingService`, `InspectionEscrowService`, `TamperSealService` |
| Shipping | Pickup addresses, quotes, shipments, shipment events and carrier coordination | `ShippingQuoteService`, `ShippingCatalogService`, `ShipmentService`, `ShipmentTimelineService`, `ReturnShippingFeeService` |
| Wallet | User/system balances, holds, refunds, settlement, withdrawals, credits and payment reconciliation | `WalletService`, `WalletQueryService`, `SystemWalletService`, `SettlementService`, `DepositService`, `CreditService`, `CreditBalanceService`, `PaymentCallbackInboxService` |

Notifications, chat, media, reports and external-provider adapters support these workflows. They are not assigned artificial ownership solely to make six isolated dependency graphs. Service names in the table identify useful entrypoints; the table is not a registration list or an exhaustive class inventory.

## Rules for new and changed code

- Invoke another business capability through its existing interface in `service/`. Injecting a business `ServiceImpl` bypasses that contract. Plain local helpers may remain concrete classes.
- Keep new persistence access with the capability that owns its decisions. Prefer an existing owning service contract when a caller only needs an identity fact, shipping operation or balance mutation. Do not add a facade that merely copies an existing contract.
- Use IDs, booleans or existing DTOs when they express the needed information. Existing entity-based contracts and JPA associations remain supported; this change does not replace them across the application.
- Preserve the transaction owning an atomic workflow, lock acquisition order, refresh/flush timing, idempotency, and all money/credit ledger writes. Do not route locking writes through a new read-only lookup to satisfy a dependency rule.
- Retain database foreign keys and referential integrity. Logical context ownership is not a reason to remove a relationship or move to another database.
- Keep external clients in `integration/` and use the existing provider contracts. No message broker, distributed cache, new runtime dependency or separate service is required for these boundaries.

## Identity lookup used by Listing

`ListingAccessServiceImpl.requireVerifiedSeller` retains its existing decision order:

1. Require selling to be allowed by `SellerReputationService`.
2. Accept an approved seller verification.
3. Otherwise accept a current `SELLER` or `ADMIN` role obtained through `IdentityAccessService.hasAnyRole`.
4. Otherwise throw the existing publication conflict.

`IdentityAccessServiceImpl` owns the `UserRepository.findByIdWithAuthorities` read and returns only a boolean. A missing user still returns false; role matching retains `User.hasRole` semantics. No identity entity escapes this contract. The lookup does not cache roles, assign roles, check token validity or replace endpoint authorization. Listing no longer injects `UserRepository` for this read. Its existing `SellerVerificationRepository` access remains an explicit exception below.

## Existing cross-context persistence and entity contracts

The application is not fully isolated by context. The following couplings are retained because replacing them requires workflow-specific work beyond this change:

| Caller | Retained coupling | Reason and constraint |
|---|---|---|
| `ListingAccessServiceImpl` | Identity's `SellerVerificationRepository` | Existing approved-verification shortcut precedes role fallback. Keep that ordering and error behavior. |
| `ListingPublicationServiceImpl` | `InspectionOrderRepository`; entity-based listing/credit operations | High-value publication creates an inspection and updates the post in the same workflow. |
| `OrderServiceImpl`, `OrderConfirmationServiceImpl` | Listing and Shipping repositories, alongside order repositories | Stock/negotiation validation and confirmation timeout/cancellation inspect shipping state while holding existing locks. Order confirmation retains `PostRepository` and `ShipmentRepository`; wallet mutations use `WalletService`/`SystemWalletService`. |
| `ShippingQuoteServiceImpl`, `ShipmentServiceImpl` | Listing, Order and Inspection data | Quotes validate accepted prices and inspection legs; shipment booking, completion and return/refund workflows coordinate those states. Preserve their current transaction and compensation behavior. |
| `InspectionServiceImpl`, `InspectionPricingServiceImpl`, `InspectionEscrowServiceImpl`, `InspectionChecklistServiceImpl` | Listing/catalog/Identity repositories and entity links | Inspection ownership, proposer/reviewer attribution, fees and escrow already depend on these records. Wallet mutation remains behind its existing service contract. |
| `WalletServiceImpl`, `DepositServiceImpl`, credit services | Identity references and account lookup | Existing wallet creation, user attribution and ledger writes retain their FK and transaction semantics. |
| `UserServiceImpl`, onboarding and verification services | Reputation/Shipping/Notification service contracts | Account state, seller onboarding and verification already coordinate these capabilities. Identity is not claimed to be a dependency-free module. |
| Existing report, notification, chat and media workflows | Shared entities and repositories | Supporting workflows remain in place. Their migration is not implied by the representative checks below. |

Existing service contracts accepting entities, including wallet/system-wallet order operations, remain valid within this monolith. Removing those contracts or moving their writes between transactions needs separate analysis and regression coverage.

## Executable checks and their limits

`ModuleBoundariesTest` uses JDK reflection and existing JUnit dependencies, without starting Spring or a database:

- All discovered concrete business services in `service/impl/` may not inject another business implementation annotated `@Service`; helper classes remain allowed.
- Six representative seams have explicit allowed repository dependencies: Identity `AuthServiceImpl`, Listing `ListingAccessServiceImpl`, Order `OrderConfirmationServiceImpl`, Inspection `InspectionOrderChecklistServiceImpl`, Shipping `ShippingCatalogServiceImpl`, Wallet `WalletQueryServiceImpl`.
- Listing access must depend on the Identity access contract and seller reputation contract. Its repository allowance excludes `UserRepository`.
- The Identity role lookup may depend only on `UserRepository`, preventing marketplace workflow dependencies from entering this small read boundary.

The Order confirmation allowance explicitly includes its existing Listing and Shipping repositories. An unexpected repository at any checked seam fails with the service, field and dependency name. Update a rule only alongside a justified workflow change and this document.

`PackageArchitectureTest` continues to enforce the global layer layout and existing layer rules. Neither test proves full context isolation: the module check examines direct injected field types, not every method call, local variable, entity graph, SQL statement or constructor-created object. It deliberately does not enumerate every class or fail unrelated existing couplings. Extend coverage at a real seam when that workflow is changed.

Run the checks from the backend root:

```powershell
.\mvnw.cmd "-Dtest=ModuleBoundariesTest,PackageArchitectureTest,IdentityAccessServiceTest,ListingAccessServiceImplTest" test
```
