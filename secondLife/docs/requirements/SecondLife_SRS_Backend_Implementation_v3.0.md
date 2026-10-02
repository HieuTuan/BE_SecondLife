# SecondLife – Backend Implementation SRS v3.0

> **Đối tượng:** Backend Developers  
> **Stack:** Java Spring Boot Modular Monolith, PostgreSQL, Flyway, Spring Security, JWT, RBAC  
> **Mục tiêu:** Quy định chính xác thứ tự xây Backend để API ổn định và không code ngược dependency.

---

## 1. Coding Standard bắt buộc

```text
Controller
→ Service Interface
→ Service Impl
→ Repository
→ PostgreSQL
```

External:

```text
Service
→ Provider Interface
→ Vendor Adapter
```

Rules:

- Package-by-feature.
- Constructor injection.
- Inject interface, không inject Impl.
- Request/Response DTO dùng `record`.
- Jakarta Validation + `@Valid`.
- Entity không expose trực tiếp.
- `ApiResponse<T>` và `PageResponse<T>`.
- `GlobalExceptionHandler`.
- Flyway quản lý schema.
- `@Transactional` ở Service.
- Read method dùng `@Transactional(readOnly = true)`.
- Không log secrets/PII.
- Không hard-code mutable config.

---

## 2. Zero Default Configuration

`application.properties` chỉ map env:

```properties
server.port=${SERVER_PORT}
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
app.jwt.secret=${JWT_SECRET}
```

Không được:

```properties
${SERVER_PORT:8080}
```

Business config thay đổi vận hành nằm trong `system_configuration` hoặc pricing/rule tables.

---

## 3. Final Module Structure

```text
common
auth
user
rbac
sellerverification
credit
wallet
catalog
listing
ai
fraud
search
chat
offer
order
payment
escrow
commission
payout
inspection
shipment
dispute
review
staff
admin
support
audit
configuration
```

---

## 4. Role & Permission Model

Roles:

```text
BUYER
SELLER
INSPECTOR
STAFF
ADMIN
```

Permission nên chi tiết, ví dụ:

```text
LISTING_CREATE_SELF
LISTING_PUBLISH_SELF
SELLER_VERIFICATION_SUBMIT
STAFF_LISTING_REVIEW
STAFF_FRAUD_REVIEW
STAFF_DISPUTE_REVIEW
STAFF_PAYOUT_REVIEW
STAFF_PAYOUT_HOLD
STAFF_USER_WARN
STAFF_USER_TEMP_RESTRICT
ADMIN_STAFF_MANAGE
ADMIN_RBAC_MANAGE
ADMIN_PRICING_MANAGE
ADMIN_COMMISSION_MANAGE
ADMIN_CONFIG_MANAGE
ADMIN_PERMANENT_BAN
ADMIN_HIGH_VALUE_PAYOUT
```

---

## 5. Database Migration Order

```text
V1   users
V2   roles_permissions_user_roles_role_permissions
V3   refresh_tokens_email_verification_password_reset
V4   seller_verifications_identity_restrictions
V5   account_restrictions
V6   credit_types
V7   credit_pricing_rules_discount_tiers
V8   credit_purchases_balances_ledger
V9   categories_brands_condition_grades
V10  system_configuration_configuration_audit
V11  listings
V12  item_media_listing_evidences_reup_history
V13  ai_estimation_requests_price_estimates
V14  image_similarity_records_fraud_flags
V15  favorites
V16  conversations_messages
V17  offers
V18  orders_order_status_history_commission_snapshot
V19  payments
V20  escrows_escrow_transactions
V21  wallets_wallet_transactions_withdrawals
V22  inspection_centers_inspector_profiles
V23  inspection_checklists_inspections_reports
V24  delivery_partners_shipments_events
V25  disputes_evidences_decisions
V26  settlements_payout_reviews
V27  reviews_reputation_logs
V28  support_tickets
V29  audit_logs_indexes_constraints
```

Không sửa migration đã apply.

---

## 6. Implementation Sequence

### Phase 0 – Foundation

Deliverables:

```http
GET /api/health
```

Phải có:

- DB connection.
- Flyway.
- Swagger.
- Security skeleton.
- Response envelope.
- Exception handler.
- Error code.
- Testcontainers skeleton.
- `.env.example`.

**Gate 0:** App start + migration + Swagger + health stable.

---

### Phase 1 – Authentication & RBAC

```http
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
POST /api/auth/email-verification/send
POST /api/auth/email-verification/confirm
POST /api/auth/forgot-password
POST /api/auth/reset-password
POST /api/auth/change-password
GET   /api/users/me
PATCH /api/users/me
```

Rules:

- Register default BUYER.
- Role không nhận từ frontend.
- Refresh token rotation.
- Disabled/suspended enforcement.
- Permission-based authorization.

**Gate 1:** Register → Login → Refresh → Protected endpoint → Logout.

---

### Phase 2 – Seller Verification

```http
POST /api/seller-verifications
GET  /api/seller-verifications/me
POST /api/seller-verifications/{id}/resubmit
```

Staff/Admin:

```http
GET  /api/staff/seller-verifications
GET  /api/staff/seller-verifications/{id}
GET  /api/admin/seller-verifications/{id}
POST /api/admin/seller-verifications/{id}/approve
POST /api/admin/seller-verifications/{id}/reject
```

Provider:

```text
EkycProvider
├── VnptEkycProvider
└── MockEkycProvider
```

Identity hash restriction check trước approval.

**Gate 2:** BUYER → Verification → APPROVED → SELLER Role.

---

### Phase 3 – Credit Pricing & Purchase

Tables:

```text
credit_types
credit_pricing_rules
credit_discount_tiers
credit_purchases
credit_balances
credit_ledger
```

Credit types:

```text
LISTING
VALUATION
```

API:

```http
GET  /api/seller/credits
GET  /api/seller/credit-pricing
POST /api/seller/credit-purchases
GET  /api/seller/credit-purchases
GET  /api/seller/credit-ledger
```

Admin:

```http
GET  /api/admin/credit-pricing
PUT  /api/admin/credit-pricing/{creditType}
GET  /api/admin/credit-discount-tiers
POST /api/admin/credit-discount-tiers
PUT  /api/admin/credit-discount-tiers/{id}
```

Formula:

```text
Subtotal = L×PL + V×PV
Discount = Subtotal×D
FinalFee = Subtotal-Discount
```

Purchase snapshot:

```text
L
V
PL
PV
discount tier/rate
subtotal
discount
final_fee
```

Trusted payment callback phải idempotent.

**Gate 3:** Purchase success → balances tăng đúng một lần.

---

### Phase 4 – Catalog & System Configuration

```http
GET /api/categories
GET /api/brands
GET /api/condition-grades
```

Admin CRUD tương ứng.

Config service phải typed, không parse string rải rác.

Tối thiểu:

```text
ANTI_SPAM_COOLDOWN_HOURS
PRODUCT_SIMILARITY_THRESHOLD
MIN_PRODUCT_IMAGES
AUTO_ESCROW_RELEASE_HOURS
PAYOUT_HIGH_VALUE_THRESHOLD
DISPUTE_WINDOW_HOURS
```

**Gate 4:** Business service đọc config qua một abstraction thống nhất.

---

### Phase 5 – Listing Draft, Media & Proof

```http
POST  /api/listings
GET   /api/listings/{id}
PATCH /api/listings/{id}
DELETE /api/listings/{id}
POST   /api/listings/{id}/media
GET    /api/listings/{id}/media
DELETE /api/listings/{id}/media/{mediaId}
POST   /api/listings/{id}/evidence
GET    /api/listings/{id}/evidence
DELETE /api/listings/{id}/evidence/{evidenceId}
```

Draft không consume Listing Credit.

Proof type enum:

```text
PRODUCT_PHOTO
RECEIPT
INVOICE
WARRANTY
PURCHASE_DOCUMENT
SERIAL_IMEI
OTHER
```

Staff proof review:

```http
GET  /api/staff/listings/{id}/evidence
POST /api/staff/listings/{id}/evidence/{evidenceId}/accept
POST /api/staff/listings/{id}/evidence/{evidenceId}/reject
POST /api/staff/listings/{id}/request-evidence
```

**Gate 5:** Seller tạo Draft + media/evidence; Staff xem/review evidence.

---

### Phase 6 – AI & Fraud

AI:

```http
POST /api/listings/{id}/ai-price-estimation
GET  /api/listings/{id}/ai-price-estimation
GET  /api/listings/{id}/ai-price-estimation/history
```

Flow:

```text
Validate ownership/input
→ Check Valuation Credit
→ Reserve/debit safely
→ Call AI
→ Persist immutable AI result
→ Return result
```

Fail trước model execution → compensate/no debit.

Duplicate:

```http
POST /api/listings/{id}/duplicate-check
GET  /api/listings/{id}/fraud-flags
GET  /api/staff/fraud-flags
POST /api/staff/fraud-flags/{id}/resolve
```

AI result không cho Staff/Admin sửa trực tiếp.

**Gate 6:** AI consume đúng một valid credit; duplicate chỉ tạo review signal.

---

### Phase 7 – Publish, Anti-Spam & Re-up

```http
POST /api/listings/{id}/publish
POST /api/listings/{id}/re-up
POST /api/listings/{id}/deactivate
```

Publish transaction:

```text
Check Seller APPROVED
→ Check ownership
→ Check Listing completeness
→ Check mandatory media
→ Check Proof requirement
→ Anti-spam/similarity rule
→ Lock credit balance
→ Consume 1 Listing Credit
→ Ledger CONSUME
→ Mark charged
→ ACTIVE
```

Similar in cooldown:

```text
409 SIMILAR_LISTING_COOLDOWN
existingListingId
suggestedAction=RE_UP
```

Re-up không consume lại credit.

**Gate 7:** Draft → Publish atomic; concurrency không double-debit.

---

### Phase 8 – Search/Favorites

```http
GET /api/search/listings
POST /api/favorites/{listingId}
DELETE /api/favorites/{listingId}
GET /api/favorites
```

Index chính:

```text
status
category_id
brand_id
final_price
published_at
seller_id
```

---

### Phase 9 – Chat & Offer

```http
POST /api/conversations
GET  /api/conversations
GET  /api/conversations/{id}
POST /api/conversations/{id}/messages
GET  /api/conversations/{id}/messages
POST /api/listings/{id}/offers
POST /api/offers/{id}/counter
POST /api/offers/{id}/accept
POST /api/offers/{id}/reject
POST /api/offers/{id}/cancel
```

Accepted Offer là immutable price reference cho Order.

---

### Phase 10 – Order & Commission Snapshot

```http
POST /api/orders
GET  /api/orders
GET  /api/orders/{id}
POST /api/orders/{id}/cancel
```

Create Order transaction:

```text
Check Listing ACTIVE
→ Check Buyer != Seller
→ Determine agreed price
→ Reserve Listing
→ Create Order
→ Resolve Commission Rule
→ Snapshot rate/min/max/base
```

Commission Rule lookup:

```text
category-specific active rule nếu có
else transaction-value rule
else configured default
```

Không có valid rule → block flow thay vì arbitrary fallback.

---

### Phase 11 – Payment & Escrow

Provider:

```text
PaymentProvider
├── MockPaymentProvider
└── Real/SandboxPaymentProvider
```

```http
POST /api/orders/{id}/payments
GET  /api/orders/{id}/payment
POST /api/payments/callback
GET  /api/orders/{id}/escrow
```

Success:

```text
Payment SUCCESS
→ Escrow HELD exactly once
```

---

### Phase 12 – Wallet

Tables:

```text
wallets
wallet_transactions
withdrawal_requests
```

```http
GET  /api/wallet
GET  /api/wallet/transactions
POST /api/wallet/withdrawals
GET  /api/wallet/withdrawals
```

Optional:

```http
POST /api/wallet/credit-purchases
```

WalletService là đường duy nhất update balance:

```text
credit(...)
debit(...)
reverse(...)
```

Mỗi operation có unique `idempotency_key`.

Không có endpoint kiểu `PUT /wallet/balance`.

---

### Phase 13 – Inspection

```http
POST /api/orders/{id}/inspections
GET  /api/inspections/{id}
POST /api/inspections/{id}/receive
POST /api/inspections/{id}/start
POST /api/inspections/{id}/results
POST /api/inspections/{id}/complete
POST /api/staff/inspections/{id}/assign
GET  /api/staff/inspections
```

Staff chỉ coordinate/view; không sửa Inspector result.

---

### Phase 14 – Shipping

```http
POST /api/orders/{id}/shipments
GET  /api/orders/{id}/shipments
GET  /api/shipments/{id}/events
POST /api/shipping/callback
```

Provider webhook idempotent.

---

### Phase 15 – Dispute

```http
POST /api/orders/{id}/disputes
GET  /api/disputes/{id}
POST /api/disputes/{id}/evidence
GET  /api/staff/disputes
POST /api/staff/disputes/{id}/request-evidence
POST /api/staff/disputes/{id}/recommend
POST /api/admin/disputes/{id}/decision
```

Open dispute → Escrow FROZEN.

---

### Phase 16 – Settlement, Payout Review & Wallet Credit

Settlement:

```text
CommissionBase
→ Commission Rule Snapshot
→ RawCommission
→ min/max cap
→ SellerPayout
```

Tạo `settlement` immutable calculation.

Payout starts:

```text
Settlement → payout_review=PENDING_REVIEW
```

Staff:

```http
GET  /api/staff/payouts
GET  /api/staff/payouts/{id}
POST /api/staff/payouts/{id}/approve
POST /api/staff/payouts/{id}/hold
```

Normal:

```text
STAFF_APPROVED
→ Payment Service Execute
→ Seller Wallet CREDIT
```

Exceptional/high-value:

```text
STAFF_APPROVED
→ ADMIN_REVIEW_REQUIRED
```

Admin:

```http
GET  /api/admin/payouts/review
POST /api/admin/payouts/{id}/approve
POST /api/admin/payouts/{id}/hold
POST /api/admin/payouts/{id}/reject
```

Payment Service lấy payout amount từ Settlement, không nhận arbitrary amount từ Staff/Admin request.

Refund:

```text
Decision/System Calculation
→ Buyer Wallet CREDIT exactly once
```

---

### Phase 17 – Review/Reputation/Restriction

```http
POST /api/orders/{id}/reviews
GET  /api/users/{id}/reviews
POST /api/staff/users/{id}/warn
POST /api/staff/users/{id}/restrictions
POST /api/admin/users/{id}/suspend
POST /api/admin/users/{id}/permanent-ban
```

Permanent ban có reason/evidence/audit + identity restriction khi policy yêu cầu.

---

### Phase 18 – Admin

Admin APIs:

```text
Users
Staff
RBAC
Master Data
Credit Pricing
Discount Tier
Commission Rules
Inspection Fees
System Configuration
Exceptional Payout
Ban
Reports
Audit
```

Mỗi sensitive write operation audit:

```text
actor
action
entity
old_value
new_value
reason
timestamp
```

---

## 7. Money & Credit Transaction Rules

### Credit purchase

```text
Create Purchase Snapshot
→ Payment
→ trusted SUCCESS
→ transaction lock/idempotency
→ credit balance +L/+V
→ ledger GRANT
```

### Publish

```text
Lock Seller Credit Balance
→ require listing_credit >= 1
→ debit 1
→ ledger CONSUME
→ listing ACTIVE
```

### AI

```text
Validate
→ reserve/debit valuation credit
→ process
→ consume or compensate
```

### Wallet

Không được update balance rải rác. Mọi thay đổi đi qua WalletService.

### Settlement

Dùng `BigDecimal`, explicit precision/rounding rule.

---

## 8. Core Interfaces

```java
public interface EkycProvider {}
public interface PaymentProvider {}
public interface ShippingProvider {}
public interface AiPriceProvider {}
public interface ImageSimilarityProvider {}
public interface WalletService {}
public interface CreditService {}
public interface CommissionService {}
public interface SettlementService {}
public interface PayoutService {}
public interface ConfigurationService {}
```

---

## 9. Concurrency/Idempotency Keys

```text
CREDIT_PURCHASE_PAYMENT:{purchaseId}
CREDIT_GRANT:{purchaseId}
LISTING_PUBLISH:{listingId}
AI_CREDIT:{requestId}
ORDER_PAYMENT:{paymentId/providerRef}
ESCROW_HOLD:{orderId}
REFUND:{refundId}
PAYOUT:{settlementId}
WALLET_TX:{businessReference}
SHIPPING_EVENT:{providerEventId}
```

---

## 10. Security Rules

- Authorization server-side.
- Staff endpoint yêu cầu exact permission, không chỉ role.
- Admin configuration write yêu cầu Admin permission.
- Staff không gọi Admin write API.
- Payout amount không nhận từ client.
- AI/Inspection result immutable.
- Sensitive evidence access cần authorization.
- CCCD/eKYC credential không log.
- Password/JWT/API keys không log.

---

## 11. API Freeze Gates

### Freeze A
```text
Auth
User
RBAC
```

### Freeze B
```text
Seller Verification
Credit
Catalog
Configuration read contract
```

### Freeze C
```text
Listing
Media
Evidence
AI
Publish/Re-up
```

### Freeze D
```text
Search
Chat
Offer
Order
Payment
Escrow
Wallet
```

### Freeze E
```text
Inspection
Shipping
Dispute
Settlement
Payout
Staff/Admin
```

Breaking change sau freeze phải version hoặc được FE/BE đồng thuận.

---

## 12. Definition of Done

Mỗi task phải có:

- Migration nếu cần.
- Entity.
- Repository.
- DTO records.
- Validation.
- Service interface/impl.
- Controller.
- RBAC.
- Error code.
- Unit test.
- Integration test khi cần.
- OpenAPI.
- Audit nếu sensitive.
- Idempotency nếu money/credit.
- Không hard-code.
- Peer review.
