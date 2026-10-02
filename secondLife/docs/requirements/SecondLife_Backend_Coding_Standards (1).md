# SecondLife Backend Coding Standards

> **Project:** SecondLife - AI Powered Second-Hand Marketplace  
> **Backend:** Spring Boot Modular Monolith  
> **Database:** PostgreSQL  
> **Principles:** package-by-feature, clean layering, externalized configuration, secure defaults, consistent API.

---

## 1. Architecture

Use **Modular Monolith**: one Spring Boot application, separated by business domain.

```text
src/main/java/com/secondlife
├── common/
│   ├── api/
│   ├── config/
│   ├── exception/
│   ├── security/
│   └── util/
├── auth/
├── user/
├── rbac/
├── sellerverification/
├── listing/
├── ai/
├── chat/
├── offer/
├── order/
├── inspection/
├── escrow/
├── shipment/
├── dispute/
├── review/
├── notification/
└── audit/
```

Each feature should follow:

```text
feature/
├── controller/
├── dto/
│   ├── request/
│   └── response/
├── entity/
├── repository/
├── service/
├── service/impl/
└── mapper/
```

External integrations may add `provider/` or `client/`.

Dependency direction:

```text
Controller
   ↓
Service Interface
   ↓
Service Implementation
   ↓
Repository
   ↓
Database
```

External services:

```text
Service
   ↓
Provider Interface
   ↓
Vendor Adapter
```

### Mandatory rules

- Controller must not call Repository directly.
- Controller contains no business logic.
- Service interface goes in `service/`.
- Service implementation goes in `service/impl/`.
- Use constructor injection only.
- Inject interfaces, not implementation classes.
- Transaction boundaries belong in Service layer.

---

## 2. Configuration & `.env`

### Zero-Default-Value Rule

All configurable values must come from environment variables.

**Not allowed:**

```properties
server.port=${SERVER_PORT:8080}
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/secondlife}
```

**Required:**

```properties
server.port=${SERVER_PORT}
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
```

If a required environment variable is missing, application startup should fail.

### `.env`

Actual local values stay in `.env`.

```env
APP_NAME=SecondLife
SERVER_PORT=8080

DB_URL=jdbc:postgresql://localhost:5432/secondlife
DB_USERNAME=postgres
DB_PASSWORD=secret

JPA_DDL_AUTO=validate
JPA_SHOW_SQL=false
FLYWAY_ENABLED=true

JWT_SECRET=secret
JWT_ACCESS_TOKEN_EXPIRATION_MS=900000
JWT_REFRESH_TOKEN_EXPIRATION_MS=604800000

CORS_ALLOWED_ORIGINS=http://localhost:5173

VNPT_EKYC_API_BASE_URL=https://api.idg.vnpt.vn
VNPT_EKYC_TOKEN_ID=secret
VNPT_EKYC_TOKEN_KEY=secret
VNPT_EKYC_ACCESS_TOKEN=secret
VNPT_EKYC_TIMEOUT_MS=5000
VNPT_EKYC_MAX_RETRIES=2

INSPECTION_HIGH_VALUE_THRESHOLD=10000000
```

### `.env.example`

Commit `.env.example`, but leave secrets and environment-specific values blank.

```env
APP_NAME=
SERVER_PORT=

DB_URL=
DB_USERNAME=
DB_PASSWORD=

JPA_DDL_AUTO=
JPA_SHOW_SQL=
FLYWAY_ENABLED=

JWT_SECRET=
JWT_ACCESS_TOKEN_EXPIRATION_MS=
JWT_REFRESH_TOKEN_EXPIRATION_MS=

CORS_ALLOWED_ORIGINS=

VNPT_EKYC_API_BASE_URL=
VNPT_EKYC_TOKEN_ID=
VNPT_EKYC_TOKEN_KEY=
VNPT_EKYC_ACCESS_TOKEN=
VNPT_EKYC_TIMEOUT_MS=
VNPT_EKYC_MAX_RETRIES=

INSPECTION_HIGH_VALUE_THRESHOLD=
```

### `.gitignore`

```gitignore
.env
.env.local
.env.dev
.env.test
.env.prod

target/
.idea/
*.iml
```

### Values that must be externalized

- Database URL, username, password
- JWT secret and expiration
- API URLs
- API keys / access tokens
- Port
- CORS settings
- Timeout
- Retry count
- Upload limits
- Pagination defaults
- Business thresholds
- Mail settings
- Storage configuration

### Values that may stay in code

Stable domain vocabulary should stay as enums.

```java
public enum ListingStatus {
    DRAFT,
    ACTIVE,
    RESERVED,
    SOLD,
    REJECTED
}
```

```java
public enum RoleCode {
    BUYER,
    SELLER,
    ADMIN,
    INSPECTOR
}
```

Do **not** move domain states to `.env`.

---

## 3. Configuration Binding

Prefer `@ConfigurationProperties`.

```java
@ConfigurationProperties(prefix = "app.jwt")
@Validated
public record JwtProperties(
        @NotBlank String secret,
        @Positive long accessTokenExpirationMs,
        @Positive long refreshTokenExpirationMs
) {
}
```

Avoid scattered `@Value` fields.

---

## 4. DTO Standard

All Request/Response DTOs use Java `record`.

### Request

```java
public record CreateListingRequest(
        @NotNull UUID categoryId,
        @NotBlank String title,
        @NotNull @Positive BigDecimal finalPrice
) {
}
```

### Response

```java
public record ListingResponse(
        UUID listingId,
        String title,
        BigDecimal finalPrice,
        String status
) {
}
```

### Rules

- Never expose JPA Entity directly through API.
- Use Jakarta Validation.
- Controller request DTO must use `@Valid`.

---

## 5. Controller Standard

Controller responsibilities:

```text
Receive Request
→ Validate
→ Call Service
→ Return Response
```

Example:

```java
@RestController
@RequestMapping("${api.paths.listings}")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;

    @PostMapping
    public ResponseEntity<ApiResponse<ListingResponse>> create(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody CreateListingRequest request
    ) {
        ListingResponse result =
                listingService.createListing(principal.userId(), request);

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
```

Controller must not contain repository calls, business calculations, transaction logic, or external provider logic.

---

## 6. Service Standard

Interface:

```java
public interface ListingService {

    ListingResponse createListing(
            UUID userId,
            CreateListingRequest request
    );

    ListingResponse getListing(UUID listingId);
}
```

Implementation:

```java
@Service
@RequiredArgsConstructor
@Transactional
public class ListingServiceImpl implements ListingService {

    private final ListingRepository listingRepository;

    @Override
    public ListingResponse createListing(
            UUID userId,
            CreateListingRequest request
    ) {
        // business logic
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public ListingResponse getListing(UUID listingId) {
        return null;
    }
}
```

### Rules

- Business logic belongs in Service.
- Write operations use transaction.
- Read operations use `@Transactional(readOnly = true)`.
- Do not inject `ServiceImpl` if a service interface exists.

---

## 7. Repository Standard

Repository is only for persistence/data access.

```java
@Repository
public interface ListingRepository
        extends JpaRepository<Listing, UUID> {

    Optional<Listing> findByIdAndSellerId(
            UUID listingId,
            UUID sellerId
    );
}
```

Do not put business decisions inside Repository.

---

## 8. Entity Standard

```java
@Entity
@Table(name = "listings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID listingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal finalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingStatus status;
}
```

### Rules

- Use `UUID` for primary keys.
- Use `BigDecimal` for money.
- Prefer `EnumType.STRING`.
- Use `LAZY` relationships where possible.
- Do not expose Entity directly to Controller.
- Avoid default business values directly in fields.

---

## 9. API Response Standard

### Success/Error wrapper

```java
public record ApiResponse<T>(
        boolean success,
        T data,
        String code,
        String message
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> error(
            String code,
            String message
    ) {
        return new ApiResponse<>(false, null, code, message);
    }
}
```

### Pagination

```java
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
```

---

## 10. Exception Handling

Use centralized exception handling.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
            NotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                        "NOT_FOUND",
                        exception.getMessage()
                ));
    }
}
```

Required custom exceptions:

- `BadRequestException` → 400
- `UnauthorizedException` → 401
- `ForbiddenException` → 403
- `NotFoundException` → 404
- `ConflictException` → 409

Rules:

- Never expose stack traces.
- Never use `printStackTrace()`.
- Use stable error codes.
- Log internal details server-side only.

---

## 11. Security Standard

Use:

```text
Spring Security
JWT
RBAC
```

RBAC flow:

```text
User
 ↓
Role
 ↓
Permission
```

Rules:

- Role assignment is server-side only.
- Never trust role or permission sent by frontend.
- Passwords must be hashed.
- Protect sensitive endpoints with role/permission checks.
- Do not log JWT, refresh token, password, API keys or eKYC credentials.

---

## 12. External Provider Standard

Every external integration must have an interface.

```java
public interface EkycProvider {
    EkycResult verify(EkycRequest request);
}
```

Vendor adapter:

```java
@Component
@RequiredArgsConstructor
public class VnptEkycProvider implements EkycProvider {

    private final VnptEkycProperties properties;

    @Override
    public EkycResult verify(EkycRequest request) {
        // provider-specific integration
        return null;
    }
}
```

Rules:

- Vendor DTOs must not leak into Controller/domain.
- API URL, token, timeout, retry and threshold come from environment variables.
- Provider failures must be normalized into internal result/error types.
- Do not log raw PII or credentials.

---

## 13. Database & Flyway

Flyway owns schema migrations.

```text
src/main/resources/db/migration/
├── V1__create_users.sql
├── V2__create_rbac.sql
├── V3__create_seller_verification.sql
├── V4__create_listing.sql
├── V5__create_ai_tables.sql
└── ...
```

Recommended environment values:

```env
JPA_DDL_AUTO=validate
FLYWAY_ENABLED=true
```

Rules:

- Do not use `ddl-auto=create` in shared/dev/staging/prod environments.
- Never edit an already-applied Flyway migration.
- Add a new migration for every schema change.
- Add indexes and unique constraints deliberately.

---

## 14. Naming Convention

### Java

```text
Class: PascalCase
Method/variable: camelCase
Constant: UPPER_SNAKE_CASE
Package: lowercase
```

### Database

Use `snake_case`.

```text
users
seller_verifications
inspection_reports
created_at
seller_id
```

### API

Use plural nouns and kebab-case when needed.

```text
/api/users
/api/listings
/api/orders
/api/seller-verifications
```

---

## 15. Logging Standard

Use SLF4J.

```java
log.info("Creating listing for userId={}", userId);
log.warn("Seller verification requires review, userId={}", userId);
```

Never log:

- Password
- JWT
- Refresh token
- API key
- Access token
- Full CCCD/identity number
- Raw eKYC documents
- Sensitive PII

---

## 16. Business Thresholds

Configurable business values must not be hard-coded.

**Not allowed:**

```java
if (price.compareTo(BigDecimal.valueOf(10_000_000)) >= 0) {
    requireInspection();
}
```

**Required:**

```env
INSPECTION_HIGH_VALUE_THRESHOLD=10000000
```

```properties
app.inspection.high-value-threshold=${INSPECTION_HIGH_VALUE_THRESHOLD}
```

Bind with `@ConfigurationProperties`.

---

## 17. Git & Team Rules

Branch naming:

```text
feature/listing-create
feature/seller-verification
fix/order-status
refactor/rbac
```

Commit examples:

```text
feat: add listing creation API
fix: validate seller verification status
refactor: extract ekyc provider interface
db: add inspection report migration
```

Rules:

- Never commit `.env`.
- Never commit secrets.
- Keep pull requests focused.
- Do not mix unrelated features in one PR.
- Run tests before merge.

---

## 18. Testing Standard

Minimum expected tests:

```text
Service unit tests
Repository tests for important queries
Controller/integration tests for critical endpoints
Security authorization tests
External provider mapping tests
```

Cover:

```text
Happy path
Validation failure
Unauthorized
Forbidden
Not found
Conflict
Provider failure
Boundary values
```

Tests must not depend on production credentials.

---

## 19. Code Review Checklist

- [ ] Correct package/domain
- [ ] Controller has no business logic
- [ ] Service interface + implementation used
- [ ] Constructor injection only
- [ ] DTOs use `record`
- [ ] Request uses Jakarta Validation
- [ ] Entity is not returned directly
- [ ] Config values come from environment
- [ ] No `${ENV:default}`
- [ ] No secrets in source/logs
- [ ] Exceptions handled centrally
- [ ] Transactions are in Service
- [ ] Flyway migration added if schema changed
- [ ] Authorization checked
- [ ] Tests added/updated
- [ ] Naming conventions followed

---

## 20. Final Mandatory Rules

```text
1. Spring Boot Modular Monolith.
2. Package by feature/domain.
3. Controller -> Service -> Repository.
4. Service interface in service/.
5. Implementation in service/impl/.
6. Constructor injection only.
7. Inject interfaces, not implementations.
8. Request/Response DTOs use Java record.
9. Request DTOs use Jakarta Validation + @Valid.
10. Never expose JPA Entity directly.
11. Use ApiResponse<T> and PageResponse<T>.
12. Use GlobalExceptionHandler.
13. Use custom exceptions for 400/401/403/404/409.
14. Transaction boundaries belong in Service.
15. PostgreSQL + Flyway.
16. Spring Security + JWT + RBAC.
17. Roles/permissions are assigned server-side only.
18. External providers must be behind interfaces.
19. No configurable value is hard-coded in Java.
20. application.properties uses ${ENV_VAR} only.
21. No ${ENV_VAR:default}.
22. .env is gitignored.
23. .env.example is committed with blank placeholders.
24. Never log secrets or sensitive PII.
25. Domain enums remain in code; deploy/config values belong in environment.
```

---

**This document is the default coding convention for the SecondLife backend.**
