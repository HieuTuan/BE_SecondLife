# SecondLife – Software Requirements Specification (SRS) v3.0

> **Tên dự án:** SecondLife - AI Powered Second-Hand Marketplace with Price Estimation and Authentication Service  
> **Mục tiêu tài liệu:** Tài liệu nghiệp vụ chung để **Frontend, Backend, AI và QA** cùng triển khai nhất quán.  
> **Phiên bản:** 3.0  
> **Trạng thái:** Baseline triển khai Capstone  
> **Nguyên tắc:** Mọi tham số nghiệp vụ có thể thay đổi phải được cấu hình, không hard-code trong source code.

---

## 1. Mục tiêu hệ thống

SecondLife là sàn giao dịch đồ cũ hỗ trợ:

- Xác thực người dùng và phân quyền.
- Seller Onboarding và eKYC.
- Chứng minh Seller có hàng thật để bán.
- Credit/Package để tạo doanh thu cho nền tảng.
- AI hỗ trợ tạo mô tả và định giá tham khảo.
- Phát hiện ảnh trùng/tái sử dụng.
- Chống spam tin đăng.
- Tìm kiếm, nhắn tin và thương lượng.
- Đặt hàng, thanh toán và Escrow.
- Kiểm định sản phẩm.
- Vận chuyển và tracking.
- Khiếu nại, tranh chấp, refund và payout.
- Wallet nội bộ dạng ledger.
- Commission theo cấu hình/rule.
- Review, reputation, moderation.
- Staff vận hành và Admin quản trị.

---

## 2. Quyết định nghiệp vụ cuối cùng

### 2.1 Credit

Hệ thống dùng hai loại credit độc lập:

- **Listing Credit:** quyền Publish một Listing mới.
- **Valuation Credit:** quyền thực hiện một lần AI Price Estimation hợp lệ.

Mô hình này thay thế baseline cũ “AI attempts per listing”. AI credit không bị buộc cứng vào một Listing entitlement.

Gọi:

- `L` = số Listing Credit mua.
- `V` = số Valuation Credit mua.
- `P_L` = đơn giá một Listing Credit.
- `P_V` = đơn giá một Valuation Credit.
- `D` = tỷ lệ giảm giá áp dụng.

```text
Subtotal = (L × P_L) + (V × P_V)
DiscountAmount = Subtotal × D
FinalFee = Subtotal - DiscountAmount
```

Baseline discount theo tổng credit `Q = L + V`:

| Tổng credit Q | Discount |
|---:|---:|
| 1–4 | 0% |
| 5–9 | 5% |
| 10–29 | 10% |
| 30–49 | 15% |
| >= 50 | 20% |

Các mức trên là cấu hình ban đầu. Admin được thay đổi bằng pricing rule; Backend không hard-code. VAT/Tax không thuộc baseline Capstone.

### 2.2 Commission

Commission Base mặc định là **giá sản phẩm cuối cùng/giá giao dịch đã thống nhất**, không gồm shipping fee và inspection fee, trừ khi rule được cấu hình khác.

Baseline commission tier:

| Transaction Value | Commission Rate |
|---|---:|
| < 500.000 VND | 7% |
| 500.000 – < 2.000.000 VND | 5% |
| 2.000.000 – < 5.000.000 VND | 4% |
| >= 5.000.000 VND | 3% |

Các mức này không phải constant trong code.

```text
RawCommission = CommissionBase × CommissionRate

PlatformCommission =
max(MinCommission,
    min(RawCommission, MaxCommission))
```

Luôn bảo đảm:

```text
PlatformCommission <= CommissionBase
```

Commission Rule phải được snapshot cho Order/Transaction.

### 2.3 Wallet

SecondLife có **Internal Ledger Wallet**, không được mô tả như ví điện tử production.

Wallet dùng cho:

- Seller payout sau settlement.
- Buyer refund.
- Mua Credit bằng Wallet balance nếu bật.
- Withdrawal request.
- Admin adjustment/reversal có kiểm soát.

Không hỗ trợ:

- P2P transfer.
- User chuyển tiền tùy ý cho nhau.
- User tự chỉnh balance.

Order payment có thể đi trực tiếp:

```text
Buyer → Payment Provider → Escrow
```

Sau settlement:

```text
Escrow → Seller Wallet
hoặc
Escrow → Buyer Wallet
```

Wallet và Escrow là hai miền nghiệp vụ khác nhau.

---

## 3. Actors & Roles

### 3.1 Buyer

- Register/Login.
- Search/Filter.
- Favorites.
- Chat Seller.
- Offer/Counter-offer.
- Create Order.
- Pay.
- Track Inspection/Shipping.
- Confirm receipt.
- Open Complaint/Dispute.
- Submit Evidence.
- Receive Refund into Wallet.
- Review Seller/Product when eligible.

### 3.2 Seller

Buyer sau khi Seller Verification được APPROVED có thêm role Seller.

- Buy Listing/Valuation Credits.
- View credit balance/history.
- Create Draft.
- Upload actual product photos.
- Upload Proof of Possession.
- Request AI Price Estimation.
- Publish khi đủ điều kiện.
- Re-up existing Listing.
- Chat/Offer handling.
- Fulfill Order.
- Receive payout into Wallet.
- Request withdrawal.

### 3.3 Inspector

- View assigned inspections.
- Execute checklist.
- Upload inspection evidence.
- Record actual condition.
- Submit inspection result/report.

Inspector không được thay đổi Commission, Credit Pricing, RBAC, AI result hoặc unrelated System Configuration.

### 3.4 Staff

Staff là role vận hành/moderation, tách khỏi Admin và Inspector.

Staff có thể:

- Review Listings.
- Review Suspicious Listings.
- Review Duplicate Images.
- Verify Proof of Possession.
- Handle Reports.
- Handle Disputes.
- Request Additional Evidence.
- Review Refund Requests.
- Review / Approve / Hold Payouts trong hạn mức/quy tắc được cấp.
- Coordinate Inspection.
- View Inspection Results.
- Monitor Transactions.
- Warn Users.
- Tạo temporary restriction theo permission được cấp.
- Handle Support Tickets.

Staff **không được**:

- Thay đổi Commission Rate/Rule.
- Thay đổi giá Listing/Valuation Credit hoặc Package.
- Tạo/xóa Staff.
- Quản lý Role/Permission.
- Thay đổi System Configuration.
- Sửa trực tiếp Inspection Result.
- Sửa trực tiếp AI Result.
- Tự nhập số tiền để chuyển cho Seller.
- Ban vĩnh viễn tài khoản nếu không có Admin permission.
- Tự ý sửa Wallet balance.

### 3.5 Administrator

Admin có thể:

- Manage Users.
- Manage Staff.
- Manage Roles & Permissions.
- Manage Categories/Brands/Condition Grades.
- Manage Commission Rules.
- Manage Listing/Valuation Credit Pricing & Packages.
- Manage Discount Tiers.
- Manage Inspection Fees.
- Manage System Configuration.
- View All Transactions.
- Handle Exceptional / High-value Payouts.
- Suspend / Permanently Ban Accounts.
- Review Seller Verification exceptions.
- Manage Inspection Centers.
- View Reports & Analytics.
- View Audit Logs.

### 3.6 External/System Actors

- eKYC Provider.
- AI Price Estimation Service.
- Image Similarity Service.
- Payment Provider.
- Delivery Provider.
- System Scheduler/Worker.

---

## 4. RBAC & Separation of Duties

Roles:

```text
BUYER
SELLER
INSPECTOR
STAFF
ADMIN
```

Một User có thể có nhiều Role. Role và Permission chỉ được Backend/Admin cấp.

### 4.1 Payout Separation of Duties

Normal transaction:

```text
Transaction eligible
→ System calculates payout amount
→ Staff reviews transaction
→ Staff APPROVE or HOLD
→ Payment Service executes exact system-calculated payout
```

Exceptional/high-value transaction:

```text
Transaction eligible
→ Staff review
→ Escalate to Admin
→ Admin APPROVE/HOLD/REJECT
→ Payment Service executes exact approved system-calculated amount
```

Staff/Admin không nhập tùy ý Seller payout amount. Amount phải xuất phát từ Settlement calculation đã audit. High-value threshold là System Configuration.

---

## 5. Main End-to-End Flow

```text
Register
→ Verify Email
→ Login
→ Seller Application
→ eKYC
→ Seller Risk Rules
→ APPROVED
→ Grant SELLER
→ Buy Credits
→ Credit Balance
→ Create Draft
→ Upload Product Images
→ Upload Proof of Possession if required
→ AI Price Estimation (-1 Valuation Credit)
→ Seller sets Final Price
→ Duplicate/Anti-Spam Check
→ Publish (-1 Listing Credit)
→ ACTIVE
→ Buyer Search
→ Chat
→ Offer/Counter-offer
→ Order
→ Payment
→ Escrow HELD
→ Inspection if required
→ Shipping
→ Delivered
→ Buyer Confirm OR configured timeout
→ Dispute? Freeze
→ Settlement
→ Staff/Admin Payout Review
→ Commission
→ Seller Wallet / Buyer Refund
→ COMPLETED
→ Review/Reputation
```

---

## 6. Seller Verification & Trust

Provider result chuẩn hóa:

```text
PASSED
FAILED
UNCERTAIN
PROVIDER_ERROR
```

Verification states:

```text
SUBMITTED
EKYC_PENDING
RESUBMIT_REQUIRED
NEEDS_REVIEW
APPROVED
REJECTED
```

Rules:

- PASSED + Risk CLEAR → APPROVED.
- PASSED + Risk REVIEW → NEEDS_REVIEW.
- PASSED + Risk BLOCK → REJECTED.
- FAILED hard failure → REJECTED.
- UNCERTAIN do ảnh xấu → RESUBMIT_REQUIRED.
- UNCERTAIN ambiguous → NEEDS_REVIEW.
- Provider timeout/unavailable → retry/pending, không auto-reject.
- Không log full CCCD.
- Có thể lưu masked identifier + normalized identity hash.
- Permanent Seller Ban đã xác nhận có thể tạo Identity Restriction.
- Seller Application mới trùng identity hash → BLOCK hoặc MANUAL REVIEW theo policy.
- Duplicate-image flag chưa review không đủ cơ sở permanent ban.

---

## 7. Listing & Proof of Possession

Seller phải có khả năng chứng minh có hàng thật:

- Nhiều ảnh sản phẩm thật.
- Checklist ảnh theo category.
- Mặt trước/mặt sau.
- Khu vực hư hỏng.
- Serial/IMEI nếu có.
- Hóa đơn/biên nhận.
- Phiếu bảo hành.
- Giấy tờ mua bán.
- Evidence khác theo category/risk rule.

Evidence status:

```text
SUBMITTED
ACCEPTED
REJECTED
NEEDS_REVIEW
```

Staff được Verify Proof of Possession nhưng không được sửa nội dung gốc do Seller upload.

---

## 8. Listing Anti-Spam & Re-up

Trước Publish, hệ thống kiểm tra:

- Same Seller.
- Same/similar Category.
- Brand/Model similarity.
- Serial/IMEI nếu có.
- Image similarity.
- Listing gần đây.

Nếu phát hiện sản phẩm tương tự trong cooldown baseline 24 giờ:

```text
BLOCK new Publish
→ Return existing Listing
→ Suggest RE-UP
```

Cooldown phải configurable.

Re-up:

- Không tạo Listing mới.
- Không consume thêm Listing Credit nếu Listing đã charge.
- Lưu history/audit.
- Tần suất re-up có thể configurable.

---

## 9. Functional Requirements

### FE-01 Authentication & Account Security
- Register email/password.
- Login.
- Email verification.
- Refresh token rotation.
- Logout/revoke.
- Forgot/reset/change password.
- Account status enforcement.
- SSO chỉ nếu triển khai thực tế.

### FE-02 RBAC
- User ↔ Role N:N.
- Role ↔ Permission N:N.
- Backend authorization.
- Staff/Admin separation of duties.

### FE-03 Seller Onboarding & Identity Verification
- Seller Application.
- eKYC.
- Risk rules.
- Resubmit.
- Admin exception review.
- Identity restriction.
- Grant SELLER only after APPROVED.

### FE-04 Credit Pricing & Purchase
- View Listing/Valuation Credit prices.
- View discount tiers.
- Purchase credit bundle.
- Purchase snapshot.
- Payment idempotency.
- Credit Ledger.
- Credit balance.
- Admin pricing management.

### FE-05 Listing Management
- Create/Edit Draft.
- Images.
- Proof of Possession.
- AI assistance.
- Publish.
- Deactivate.
- Re-up.
- Listing lifecycle.

### FE-06 AI-Assisted Listing & Price Estimation
- Analyze product inputs.
- Generate category/condition questions.
- Structured description.
- Suggested price/range.
- Consume one Valuation Credit per valid accepted request.
- Seller controls final price.
- AI result immutable/auditable.

### FE-07 Duplicate/Reused Product Image Detection
- Embedding/feature extraction.
- Similarity search.
- Configurable threshold.
- Fraud flag.
- Staff review.
- Không automatic fraud conclusion từ một signal.

### FE-08 Search & Discovery
- Keyword.
- Category.
- Brand.
- Condition.
- Price range.
- Location nếu hỗ trợ.
- Sort.
- Pagination.
- Favorites.

### FE-09 Chat & Negotiation
- Listing-based conversation.
- In-app message.
- Submit Offer.
- Counter-offer.
- Accept/Reject/Cancel.
- Negotiation history.
- Scam warning signal nếu triển khai.

### FE-10 Order Management
- Create từ list price hoặc accepted offer.
- Reserve Listing.
- State transitions.
- Cancel rules.
- Status history.
- Commission snapshot.

### FE-11 Payment & Escrow
- Payment intent/record.
- Trusted provider confirmation.
- Escrow HELD.
- Buyer confirmation.
- Configurable auto-release timeout.
- Freeze on dispute.
- Refund/release.
- Idempotency.

### FE-12 Inspection & Authentication
- Inspection trigger.
- Center routing.
- Inspector assignment.
- Checklist by category.
- Evidence.
- Condition comparison.
- PASS/PASS_WITH_NOTES/FAIL.
- Report immutable sau issue; correction qua controlled workflow.

### FE-13 Shipping & Tracking
- Provider adapter.
- Shipment legs.
- Tracking events.
- Delivered.
- Return shipment nếu dispute decision yêu cầu.

### FE-14 Complaint & Dispute
- Eligible time window.
- Evidence.
- Staff review.
- Request more evidence.
- Admin escalation.
- Refund/Release decision.
- Restriction/reputation outcome.

### FE-15 Wallet
- Một active wallet/user/currency.
- View balance.
- View ledger.
- Receive payout/refund.
- Pay credit purchase from balance nếu bật.
- Withdrawal request.
- Admin adjustment/reversal có audit.
- Không direct balance edit.

### FE-16 Commission & Settlement
- Commission rule lookup.
- Transaction/category tier.
- Min/max.
- Snapshot.
- Settlement breakdown.
- Payout amount system-calculated.
- Staff approval/hold.
- Admin exceptional/high-value approval.
- Payment Service executes transfer.

### FE-17 Ratings & Reputation
- Buyer reviews Seller.
- Seller review Buyer nếu bật.
- One review per actor/order.
- Rating history.
- Report abusive review.

### FE-18 Staff Operations
- Moderation queues.
- Listing proof review.
- Fraud/duplicate review.
- Dispute queue.
- Refund queue.
- Payout queue.
- Support tickets.
- Transaction monitoring.
- Temporary warning/restriction.

### FE-19 Administration
- User/Staff/RBAC.
- Categories/master data.
- Credit pricing/packages.
- Discount tiers.
- Commission.
- Inspection fees.
- System Configuration.
- High-value payout.
- Ban.
- Reports.
- Audit logs.

### FE-20 Reporting & Audit
- Credit sales/usage.
- AI usage.
- Listings.
- Orders.
- Commission.
- Payout/refund.
- Inspection.
- Dispute.
- Moderation.
- Configuration history.

---

## 10. Core Business Rules

### Credit
- **BR-CRD-01:** Draft không consume Listing Credit.
- **BR-CRD-02:** First successful Publish consume đúng 1 Listing Credit.
- **BR-CRD-03:** Re-up/reactivate cùng Listing đã charge không consume lại.
- **BR-CRD-04:** AI request hợp lệ accepted for processing consume 1 Valuation Credit.
- **BR-CRD-05:** Validation/provider failure trước model execution không consume Valuation Credit.
- **BR-CRD-06:** Credit balance không âm.
- **BR-CRD-07:** Pricing/discount rules configurable và snapshotted khi purchase.

### Listing/Spam
- **BR-LST-01:** Seller phải APPROVED và có Listing Credit trước Publish.
- **BR-LST-02:** Proof checklist theo category/configuration.
- **BR-SPAM-01:** Similar product within cooldown → block new Publish.
- **BR-SPAM-02:** Suggest Re-up existing Listing.
- **BR-SPAM-03:** Similarity là risk signal, không auto-fraud.

### Wallet
- **BR-WAL-01:** Wallet balance chỉ thay đổi qua Wallet Transaction/Ledger.
- **BR-WAL-02:** Wallet operations idempotent.
- **BR-WAL-03:** Escrow không phải Wallet balance.
- **BR-WAL-04:** Refund/Payout credit Wallet đúng một lần.
- **BR-WAL-05:** User không tự chỉnh balance.
- **BR-WAL-06:** Withdrawal không vượt available balance.

### Commission/Settlement
- **BR-COM-01:** Commission finalized khi settlement eligible.
- **BR-COM-02:** Cancel/full refund trước completion → không final commission.
- **BR-COM-03:** Dispute active → freeze settlement.
- **BR-COM-04:** Commission rule snapshot immutable cho transaction.
- **BR-COM-05:** Payout amount phải do hệ thống tính.
- **BR-COM-06:** Staff chỉ APPROVE/HOLD, không nhập payout amount.
- **BR-COM-07:** High-value/exceptional payout cần Admin.
- **BR-COM-08:** Payment Service thực thi transfer sau approval.

### Moderation
- **BR-MOD-01:** Staff không thay đổi pricing/configuration/RBAC.
- **BR-MOD-02:** Staff không sửa AI/Inspection result.
- **BR-MOD-03:** Permanent ban yêu cầu Admin permission.
- **BR-MOD-04:** Moderation decision có actor, reason, timestamp, evidence nếu có.

---

## 11. State Machines

### Listing
```text
DRAFT → ACTIVE → RESERVED → SOLD
ACTIVE → DEACTIVATED
DRAFT/ACTIVE → REJECTED
```

### Order
```text
CREATED → PAYMENT_PENDING → PAID → PROCESSING → SHIPPED → DELIVERED → COMPLETED
→ CANCELLED
→ DISPUTED
→ REFUNDED
```

### Escrow
```text
PENDING → HELD → RELEASED
HELD → FROZEN
HELD/FROZEN → REFUNDED
```

### Wallet Transaction
```text
PENDING → COMPLETED
PENDING → FAILED
COMPLETED → REVERSED
```

### Payout Review
```text
PENDING_REVIEW
→ STAFF_APPROVED
→ EXECUTION_PENDING
→ PAID

PENDING_REVIEW → HELD
STAFF_APPROVED → ADMIN_REVIEW_REQUIRED
ADMIN_REVIEW_REQUIRED → ADMIN_APPROVED
ADMIN_REVIEW_REQUIRED → HELD/REJECTED
```

---

## 12. System Configuration

Tối thiểu:

```text
CREDIT_LISTING_UNIT_PRICE
CREDIT_VALUATION_UNIT_PRICE
CREDIT_DISCOUNT_TIERS
ANTI_SPAM_COOLDOWN_HOURS
PRODUCT_SIMILARITY_THRESHOLD
MIN_PRODUCT_IMAGES
PLATFORM_COMMISSION_RULES
PLATFORM_COMMISSION_MIN
PLATFORM_COMMISSION_MAX
COMMISSION_BASE_RULE
AUTO_ESCROW_RELEASE_HOURS
DISPUTE_WINDOW_HOURS
PAYOUT_HIGH_VALUE_THRESHOLD
INSPECTION_PRICE_THRESHOLD
INSPECTION_FEES
INSPECTION_RULES
WITHDRAWAL_MIN_AMOUNT
WITHDRAWAL_MAX_AMOUNT
```

Secret/API credential dùng environment variable, không lưu business configuration table.

---

## 13. Core Data Entities

```text
users
roles
permissions
user_roles
role_permissions
refresh_tokens
seller_verifications
identity_restrictions
account_restrictions
credit_types
credit_pricing_rules
credit_discount_tiers
credit_purchases
credit_balances
credit_ledger
categories
brands
condition_grades
listings
item_media
listing_evidences
listing_reup_history
ai_estimation_requests
ai_price_estimates
image_similarity_records
fraud_flags
favorites
conversations
chat_messages
offers
orders
order_status_history
order_commission_snapshots
payments
escrows
escrow_transactions
wallets
wallet_transactions
withdrawal_requests
inspection_centers
inspector_profiles
inspection_checklist_templates
inspection_checklist_items
inspections
inspection_item_results
inspection_reports
delivery_partners
shipments
shipment_events
disputes
dispute_evidences
dispute_decisions
payout_reviews
settlements
reviews
reputation_logs
support_tickets
system_configuration
configuration_audit
audit_logs
```

---

## 14. API Contract Groups cho FE/BE

### Auth
```http
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
POST /api/auth/forgot-password
POST /api/auth/reset-password
```

### Seller Verification
```http
POST /api/seller-verifications
GET  /api/seller-verifications/me
POST /api/seller-verifications/{id}/resubmit
```

### Credits
```http
GET  /api/seller/credits
GET  /api/seller/credit-pricing
POST /api/seller/credit-purchases
GET  /api/seller/credit-purchases
GET  /api/seller/credit-ledger
```

### Listings
```http
POST  /api/listings
GET   /api/listings/{id}
PATCH /api/listings/{id}
POST  /api/listings/{id}/media
POST  /api/listings/{id}/evidence
POST  /api/listings/{id}/ai-price-estimation
POST  /api/listings/{id}/publish
POST  /api/listings/{id}/re-up
```

### Marketplace
```http
GET  /api/search/listings
POST /api/favorites/{listingId}
POST /api/conversations
POST /api/conversations/{id}/messages
POST /api/listings/{id}/offers
POST /api/offers/{id}/counter
POST /api/offers/{id}/accept
POST /api/offers/{id}/reject
```

### Transaction
```http
POST /api/orders
GET  /api/orders/{id}
POST /api/orders/{id}/payments
GET  /api/orders/{id}/escrow
POST /api/orders/{id}/confirm-received
```

### Wallet
```http
GET  /api/wallet
GET  /api/wallet/transactions
POST /api/wallet/withdrawals
GET  /api/wallet/withdrawals
```

### Staff
```http
GET  /api/staff/review-queues/listings
GET  /api/staff/review-queues/fraud
GET  /api/staff/disputes
POST /api/staff/disputes/{id}/request-evidence
GET  /api/staff/payouts
POST /api/staff/payouts/{id}/approve
POST /api/staff/payouts/{id}/hold
POST /api/staff/users/{id}/warn
POST /api/staff/users/{id}/restrictions
```

### Admin
```http
/api/admin/users/**
/api/admin/staff/**
/api/admin/roles/**
/api/admin/permissions/**
/api/admin/categories/**
/api/admin/credit-pricing/**
/api/admin/commission-rules/**
/api/admin/inspection-fees/**
/api/admin/configuration/**
/api/admin/payouts/**
/api/admin/audit-logs
```

---

## 15. Non-Functional Requirements

- **NFR-01 Security:** RBAC enforced Backend-side.
- **NFR-02 Privacy:** Không log password/token/full identity document.
- **NFR-03 Consistency:** Money/Credit operations transactional.
- **NFR-04 Idempotency:** Payment, credit grant, refund, payout, wallet operation chống double processing.
- **NFR-05 Auditability:** Financial/moderation/configuration changes audit được.
- **NFR-06 Performance:** Search, balance checks, listing checks có latency phù hợp demo.
- **NFR-07 Reliability:** External provider errors không phá state.
- **NFR-08 Maintainability:** Modular Monolith, package-by-feature.
- **NFR-09 Configuration:** Mutable business value không hard-code.
- **NFR-10 Testability:** Provider adapters có mock/sandbox.
- **NFR-11 Concurrency:** Publish, credit debit, order reservation, settlement có lock/version/idempotency phù hợp.
- **NFR-12 Observability:** Structured logs với request/reference ID; không log secrets/PII.

---

## 16. Error Codes chính

```text
EMAIL_ALREADY_EXISTS
INVALID_CREDENTIALS
ACCOUNT_DISABLED
EMAIL_NOT_VERIFIED
SELLER_NOT_VERIFIED
SELLER_VERIFICATION_NEEDS_REVIEW
IDENTITY_RESTRICTED
LISTING_CREDIT_EXHAUSTED
VALUATION_CREDIT_EXHAUSTED
CREDIT_PURCHASE_PAYMENT_FAILED
CREDIT_PURCHASE_ALREADY_PROCESSED
LISTING_INCOMPLETE
LISTING_PROOF_REQUIRED
SIMILAR_LISTING_COOLDOWN
DUPLICATE_IMAGE_REVIEW_REQUIRED
LISTING_NOT_AVAILABLE
OFFER_INVALID_STATE
ORDER_ALREADY_EXISTS
PAYMENT_FAILED
ESCROW_NOT_HELD
SETTLEMENT_BLOCKED_BY_DISPUTE
WALLET_INSUFFICIENT_BALANCE
WALLET_TRANSACTION_ALREADY_PROCESSED
WITHDRAWAL_NOT_ALLOWED
PAYOUT_REVIEW_REQUIRED
PAYOUT_ADMIN_APPROVAL_REQUIRED
PAYOUT_ALREADY_FINALIZED
COMMISSION_RULE_NOT_FOUND
COMMISSION_CONFIG_INVALID
INSPECTION_REQUIRED
INSPECTION_RESULT_IMMUTABLE
FORBIDDEN_OPERATION
```

---

## 17. Acceptance Criteria tiêu biểu

- Seller chưa APPROVED không Publish được.
- Draft không consume Listing Credit.
- Publish mới consume đúng một Listing Credit.
- Re-up không consume lại.
- AI request valid accepted consume đúng một Valuation Credit.
- Invalid AI input trước model không consume credit.
- Similar Listing trong 24h baseline bị chặn và gợi ý Re-up.
- Credit purchase callback lặp không cộng credit hai lần.
- Order payment callback lặp không tạo Escrow hai lần.
- Commission tier được snapshot.
- Dispute freeze settlement.
- Staff không thể đổi Commission/Credit Pricing/System Configuration.
- Staff không thể nhập payout amount.
- High-value payout bắt buộc Admin approval.
- Payment Service chỉ execute amount Settlement tính.
- Payout/Refund credit Wallet đúng một lần.
- Wallet balance không âm.
- Permanent ban chỉ từ authorized Admin flow.
- Identity hash permanent ban có thể block/manual-review Seller application mới.

---

## 18. FE Implementation Notes

Frontend phải dựa vào permission Backend trả về, nhưng ẩn button không thay thế authorization.

UI chính:

```text
Buyer Marketplace
Seller Dashboard
Credit Purchase
Listing Editor
AI Estimation
Search
Chat/Offer
Order Detail
Inspection/Shipping Tracking
Wallet
Dispute
Review
Staff Workspace
Admin Console
```

Staff Workspace và Admin Console phải tách quyền rõ ràng.

---

## 19. Out of Scope / Simplification cho Capstone

- Wallet là internal ledger, không phải ví điện tử production.
- Withdrawal có thể mock/sandbox.
- Payment provider có thể sandbox/mock adapter.
- Delivery provider có thể sandbox/mock.
- Advanced recommendation engine không bắt buộc.
- Tax/VAT không thuộc baseline.
- WebSocket chat có thể thay REST/polling nếu thiếu thời gian.
- Partial refund formula chỉ triển khai nếu team chốt riêng.
