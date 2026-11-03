# API gói dịch vụ V68 — giá, trạng thái, biên nhận và retry

## 1. Triển khai và dữ liệu

Flyway V68 bổ sung active/version vào topup_packages và bảng topup_package_purchases.
Giữ nguyên giá bán, số lượt, lịch sử ví và ledger hiện có. Các gói cũ mặc định active=true, version=0.
Không tự cấu hình đơn giá hoặc tạo biên nhận giả cho giao dịch cũ. Lịch sử mua gói mới bắt đầu khi dùng API V68;
giao dịch cũ vẫn xem tại lịch sử ví. Chạy migration bằng cơ chế Flyway khi khởi động BE, không dùng ddl-auto=create.

Entity giữ cột discount_percentage cũ để không làm mất dữ liệu, nhưng API không dùng nó làm nguồn tính ưu đãi.
DELETE gói từ phiên bản này là ngừng bán, không xóa vật lý. Vì gói vẫn còn trong bảng, seeder không tự tạo lại catalog khi ngừng bán tất cả.

## 2. Công thức giá

Admin cấu hình đơn giá chuẩn qua GET/PUT /api/v1/admin/credit-pricing hiện có.
Chỉ dùng rule active=true, currency=VND, unitPrice>0 cho các loại lượt thực sự có trong gói.

referencePrice = postCredits × đơn giá LISTING + chatCredits × đơn giá AI_CHAT + valuationCredits × đơn giá VALUATION.
discountPercentage = (referencePrice - price) × 100 / referencePrice, làm tròn HALF_UP 2 chữ số thập phân.
price là tiền thực trả, không giảm lần hai.

Gói phải có ít nhất một lượt, số lượt không âm, price >= 1000, tối đa 17 chữ số nguyên và 2 số thập phân,
và price không vượt referencePrice. Thiếu cấu hình/giá không phù hợp trả 409 khi ghi hoặc mua gói.
Admin GET vẫn trả gói để sửa, kèm pricingAvailable=false và discountPercentage=null;
referencePrice có thể null nếu thiếu đơn giá. Không dựng giá giả hoặc giá gạch ngang suy ngược.
Public GET catalog trả 409 nếu có gói đang bán chưa có giá hợp lệ; admin cần chỉnh đơn giá/giá gói hoặc ngừng bán gói ấy.
Đổi đơn giá chuẩn tự đổi giá tham chiếu/% tiết kiệm; không tự đổi giá bán price.

## 3. API quản trị

Role ADMIN và authority ADMIN_PRICING_MANAGE bắt buộc.

| Method | Endpoint | Thành công |
| --- | --- | --- |
| GET | /api/v1/admin/topup-packages | 200 ApiResponse<List<TopupPackageResponse>>; gồm cả gói ngừng bán |
| GET | /api/v1/admin/topup-packages/{id} | 200 ApiResponse<TopupPackageResponse> |
| POST | /api/v1/admin/topup-packages | 201 ApiResponse<TopupPackageResponse> |
| PUT | /api/v1/admin/topup-packages/{id} | 200 ApiResponse<TopupPackageResponse> |
| PATCH | /api/v1/admin/topup-packages/{id}/status | 200 ApiResponse<TopupPackageResponse> |
| DELETE | /api/v1/admin/topup-packages/{id} | 200 ApiResponse<Void>, message Đã ngừng bán gói dịch vụ; alias ngừng bán |

POST/PUT dùng body đầy đủ, bỏ input % giảm:

```json
{
  "name": "Basic Combo",
  "description": "2 tin, 10 Chat AI, 2 định giá",
  "postCredits": 2,
  "chatCredits": 10,
  "valuationCredits": 2,
  "price": 59000,
  "active": true
}
```

Tên không trắng, tối đa 100; description null/rỗng hoặc tối đa 255. active bắt buộc.
Nếu client cũ gửi discountPercentage, BE bỏ qua trường đó; không tin giá trị client.
PATCH status:

```json
{ "active": false }
```

Ngừng bán luôn được phép dù đơn giá đang lỗi. Mở bán phải có cấu hình hợp lệ.
Mỗi thay đổi dữ liệu gói tăng packageVersion; bật lại vẫn giữ ID cũ. Không thay đổi quyền lợi user đã mua.

TopupPackageResponse (data bên trong wrapper admin):

```json
{
  "id": "83d7d4ec-7985-4dd2-b5aa-c4a67da8b1b1",
  "name": "Basic Combo",
  "description": "2 tin, 10 Chat AI, 2 định giá",
  "postCredits": 2,
  "chatCredits": 10,
  "valuationCredits": 2,
  "price": 59000,
  "discountPercentage": 7.81,
  "referencePrice": 64000,
  "pricingAvailable": true,
  "active": true,
  "packageVersion": 0
}
```

Số liệu ví dụ dùng đơn giá LISTING=20000, AI_CHAT=2000, VALUATION=2000; không phải dữ liệu mặc định được tự ghi vào DB.

## 4. API người dùng

GET /api/v1/topup/packages giữ response list trực tiếp (không wrapper), chỉ trả active=true.
Yêu cầu CREDIT_READ_SELF. Các item có đầy đủ field mới như ví dụ trên.
GET /api/v1/topup/my-credit giữ response trực tiếp {postCredits,chatCredits,valuationCredits}.

### Mua gói — contract thay đổi bắt buộc cập nhật FE

POST /api/v1/topup/purchase/{packageId}, authority CREDIT_PURCHASE_SELF.
Body bắt buộc:

```json
{
  "requestId": "c37c30e9-1b77-4d5e-9130-c1133835b055",
  "packageVersion": 0,
  "expectedPrice": 59000
}
```

FE lấy packageVersion/expectedPrice từ gói user đã xác nhận. UUID requestId sinh mới cho một ý định mua,
lưu trước POST và giữ nguyên khi retry timeout, mất mạng hoặc không chắc request trước đã commit.
Không tự tạo UUID mới mỗi lần retry. Muốn mua thêm thật sự cần thao tác xác nhận mới và UUID mới.

- 200: ApiResponse<TopupPackagePurchaseResponse>, không còn trả CreditBalanceResponse trực tiếp.
- Cùng user + requestId + cùng packageId/version/expectedPrice: trả lại cùng biên nhận, không trừ/cấp lần hai.
- Cùng requestId nhưng thay nội dung: 409.
- Gói ngừng bán, thay phiên bản hoặc giá đã xác nhận không khớp: 409, không tự mua theo giá mới.
- Request thiếu body/requestId/version/expectedPrice, version âm, expectedPrice < 1000: 400.
- Ví thiếu tiền: 400, rollback; có thể nạp thêm rồi retry cùng requestId nếu nội dung vẫn đúng.
- Gói chưa từng tồn tại: 404. Replay biên nhận thành công vẫn hoạt động dù gói đã ngừng bán/đổi giá.

Response ví dụ (IDs, timestamp, số dư minh họa):

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "id": "57fa1b9f-76ba-46c2-ad5b-ea224b683add",
    "requestId": "c37c30e9-1b77-4d5e-9130-c1133835b055",
    "packageId": "83d7d4ec-7985-4dd2-b5aa-c4a67da8b1b1",
    "packageVersion": 0,
    "packageName": "Basic Combo",
    "packageDescription": "2 tin, 10 Chat AI, 2 định giá",
    "postCredits": 2,
    "chatCredits": 10,
    "valuationCredits": 2,
    "price": 59000,
    "currency": "VND",
    "referencePrice": 64000,
    "discountPercentage": 7.81,
    "listingUnitPrice": 20000,
    "chatUnitPrice": 2000,
    "valuationUnitPrice": 2000,
    "walletBalanceBefore": 100000,
    "walletBalanceAfter": 41000,
    "postBalanceAfter": 2,
    "chatBalanceAfter": 10,
    "valuationBalanceAfter": 2,
    "createdAt": "2026-11-01T05:00:00Z"
  },
  "timestamp": "2026-11-01T12:00:00"
}
```

Biên nhận là snapshot tại lúc mua, kể cả số dư sau giao dịch. Replay không trả số dư hiện tại.
Sau mua FE gọi lại wallet và my-credit để hiển thị số dư hiện tại; không cộng lượt ở client.

### Lịch sử biên nhận

Authority CREDIT_READ_SELF, luôn giới hạn user đăng nhập:

GET /api/v1/topup/purchases?page=0&size=20
→ ApiResponse<PageResponse<TopupPackagePurchaseResponse>>
→ data: {items:[...],page:0,size:20,totalElements:1,totalPages:1,isLast:true}.

GET /api/v1/topup/purchases/{purchaseId}
→ ApiResponse<TopupPackagePurchaseResponse>; ID người khác trả 404.
page >= 0, size 1..100; sort cố định createdAt DESC, id DESC, không gửi sort từ FE.
createdAt là ISO UTC; FE hiển thị theo múi giờ người dùng/Asia_Ho_Chi_Minh.

## 5. Tính nguyên tử và đồng thời

Một transaction chứa khóa ví, đọc gói có khóa, trừ tiền, cấp lượt, ghi ledger và biên nhận.
Cùng user được serialize bằng khóa ví, sau khi lấy khóa kiểm tra requestId lần nữa.
DB có unique(user_id,request_id) và FK user/package; không dựa vào nút disabled ở FE để chống mua trùng.
Lỗi bất kỳ bước cấp lượt/ghi biên nhận rollback cả tiền và credit; không để biên nhận thành công giả.
Snapshot không bị sửa khi admin đổi tên, số lượt, giá hoặc đơn giá chuẩn.

## 6. Test Swagger

1. ADMIN cấu hình 3 đơn giá bằng PUT /api/v1/admin/credit-pricing/{LISTING|AI_CHAT|VALUATION}, body {"unitPrice":...}.
2. Tạo gói Basic như JSON ở trên, lưu id và packageVersion.
3. Dùng user có CREDIT_PURCHASE_SELF, ví đủ tiền. POST mua với UUID mới, version và price đúng.
4. Lưu biên nhận. Gửi lại y hệt: cùng data.id, số dư/credit không đổi thêm.
5. Giữ requestId nhưng đổi expectedPrice: 409.
6. Admin đổi quyền lợi hoặc giá gói; user mua bằng version cũ và UUID mới: 409, không trừ tiền.
7. PATCH active=false: mất khỏi public catalog, vẫn có trong admin và lịch sử. Mua mới bị 409.
8. Gửi lại request của lần mua thành công trước khi ngừng bán: vẫn trả biên nhận cũ.
9. Ví thiếu tiền: 400, không có biên nhận/ledger thành công; nạp thêm và retry theo cùng yêu cầu.
10. GET lịch sử với user khác không xem được biên nhận của user đầu.
11. Tắt rule dùng bởi gói trong môi trường test: admin pricingAvailable=false; public catalog/mua trả 409.
12. Xác nhận lịch sử ví/credit trước V68 không mất, nhưng không được tạo biên nhận snapshot giả.

Lệnh test tập trung (PostgreSQL Testcontainers, không DB thật):

```powershell
mvn "-Dtest=AdminTopupPackageControllerTest,TopupPurchaseControllerTest,AdminTopupPackageServiceTest,AdminTopupPackageIntegrationTest,TopupPackageV68MigrationTest,PackageArchitectureTest,MapStructMapperTest,ControllerResponseSerializationTest" test
```

Kết quả xác minh: 70 test đạt trong nhóm liên quan, không skipped; gồm 9 test tích hợp nghiệp vụ PostgreSQL và 1 test nâng cấp V65→V68 giữ nguyên dữ liệu cũ. Không chạy toàn bộ suite hoặc áp migration lên DB thật trong phiên này.

## Cấu hình đơn giá lần đầu khi bảng giá rỗng

PUT /api/v1/admin/credit-pricing/{creditType} tạo cấu hình còn thiếu hoặc cập nhật dòng hiện có.
Body vẫn là {"unitPrice": <giá admin nhập>}. Không tự đặt mức giá mặc định, không cần sửa Flyway.
GET /api/v1/admin/credit-pricing luôn có đủ LISTING, AI_CHAT, VALUATION; loại chưa cấu hình
trả unitPrice=null, active=false, currency=VND. Đây là trạng thái chưa cấu hình, không phải giá bán 0.
API PUT đã tồn tại ít nhất từ commit 245feec (18/10/2026). Lỗi Credit price not found trước đây
phát sinh ở service khi thiếu dòng trong credit_pricing_rules, không phải thiếu endpoint.
