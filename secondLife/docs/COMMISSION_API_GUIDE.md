# Hướng dẫn FE/test API hoa hồng và settlement

Tài liệu đối chiếu với controller, DTO và service hiện tại của SecondLife.

## 1. Quy tắc đang áp dụng

- ADMIN cấu hình một chính sách **DEFAULT** áp dụng cho mọi sản phẩm. Chỉ được có một chính sách DEFAULT đang `active=true`.
- Request tạo/sửa không nhận `type`, `categoryId`, `transactionValueFrom` hay `transactionValueTo`.
- Khi tạo đơn thành công, backend tự chốt một **snapshot**: bản lưu tỷ lệ, min/max, phiên bản chính sách và giá sản phẩm của đơn tại thời điểm đó.
- ADMIN sửa hoặc tắt chính sách thì snapshot của đơn đã tạo vẫn giữ nguyên.
- BUYER trả **giá sản phẩm + phí vận chuyển** khi đặt đơn. Hoa hồng được trừ từ phần giá sản phẩm khi giải ngân cho SELLER; không cộng thêm hoa hồng vào tiền BUYER phải trả.
- GHN báo giao thành công, sau đó BUYER xác nhận nhận hàng thì hệ thống tạo settlement và cộng **tiền SELLER thực nhận** vào ví SELLER.
- Settlement là kết quả quyết toán của đơn, gồm giá sản phẩm, hoa hồng, tiền SELLER nhận và phí vận chuyển. GET settlement chỉ đọc kết quả, không thực hiện giải ngân.

## 2. Chuẩn bị trên Swagger

Mở Swagger của backend đang chạy, ví dụ `http://localhost:8080/swagger-ui/index.html` khi port là 8080. Đăng nhập lấy access token rồi dùng **Authorize**. Đổi token khi chuyển giữa ADMIN, BUYER và SELLER.

Khi gọi từ FE:

```http
Authorization: Bearer <access_token>
Content-Type: application/json
```

Điều kiện quyền:

| Nhóm API | Ai được gọi? |
| --- | --- |
| `/api/admin/commission-rules...` và API bổ sung snapshot | ADMIN có quyền `ADMIN_COMMISSION_MANAGE` |
| GET commission/settlement của đơn | BUYER hoặc SELLER của chính đơn đó |
| GET commission/settlement của đơn bất kỳ | ADMIN có `ADMIN_COMMISSION_MANAGE`, hoặc STAFF có `STAFF_PAYOUT_REVIEW` |
| PUT xác nhận nhận hàng | Chính BUYER của đơn |

Các UUID trong JSON dưới đây là ví dụ. Thay bằng ID thật trả về từ API; không dùng UUID ví dụ để truy cập dữ liệu thực tế.

## 3. Danh sách API commission

| Method | Endpoint | Chức năng | Thành công |
| --- | --- | --- | --- |
| GET | `/api/admin/commission-rules?page=0&size=10` | Danh sách chính sách DEFAULT, gồm cả chính sách đã tắt | 200 |
| GET | `/api/admin/commission-rules/{id}` | Chi tiết chính sách DEFAULT | 200 |
| POST | `/api/admin/commission-rules` | Tạo chính sách | 201 |
| PUT | `/api/admin/commission-rules/{id}` | Sửa toàn bộ cấu hình chính sách cho đơn mới | 200 |
| POST | `/api/admin/commission-rules/{id}/deactivate` | Tắt chính sách, giữ lịch sử | 200 |
| GET | `/api/admin/commission-rules/{id}/history?page=0&size=10` | Xem lịch sử thay đổi | 200 |
| POST | `/api/admin/orders/{orderId}/commission-snapshot` | Bổ sung snapshot cho đơn cũ chưa có snapshot, chưa quyết toán | 200 |
| GET | `/api/v1/orders/{orderId}/commission` | Đọc snapshot và tính tiền hoa hồng/SELLER thực nhận | 200 |
| GET | `/api/v1/orders/{orderId}/settlement` | Đọc settlement đã hoàn tất | 200 |

`id` trong API quản lý chính sách là **ID chính sách**. `orderId` là **ID đơn hàng**, không phải `postId`, `shipmentId` hoặc mã GHN.

Các API commission trả dữ liệu trong `data` của `ApiResponse`. API danh sách/lịch sử trả các bản ghi ở `data.items`. Không cần nhập `sort` khi test; danh sách chính sách được backend sắp theo thời gian tạo giảm dần, lịch sử theo thời gian thay đổi giảm dần.

## 4. JSON tạo/sửa và ý nghĩa từng field

```json
{
  "name": "Hoa hồng mặc định 5%",
  "rate": 0.05,
  "minCommission": 10000,
  "maxCommission": 100000,
  "active": true,
  "reason": "Áp dụng hoa hồng mặc định cho đơn hàng mới"
}
```

| Field | Bắt buộc | Ý nghĩa và validation |
| --- | --- | --- |
| `name` | Có | Tên chính sách, chuỗi không trắng, tối đa 150 ký tự |
| `rate` | Có | Tỷ lệ từ 0 đến 1, tối đa 6 chữ số thập phân. `0.05` = 5%, `0.1` = 10%; gửi `5` là sai |
| `minCommission` | Có | Mức hoa hồng tối thiểu theo VND, từ 0 trở lên; tối đa 16 chữ số phần nguyên và 2 chữ số thập phân |
| `maxCommission` | Không | Mức hoa hồng tối đa theo VND; nếu có thì phải ≥ `minCommission`, cùng giới hạn số chữ số như min |
| `active` | Có | Boolean `true` hoặc `false`; chỉ chính sách đang bật được dùng cho đơn mới |
| `reason` | Có | Lý do tạo/sửa để ghi lịch sử, chuỗi không trắng, tối đa 1000 ký tự |

Các số phải là JSON number: dùng `0.05`, không dùng `"0.05"`; dùng `10000`, không dùng `"10.000đ"`. Boolean phải là `true`/`false`, không dùng `"true"` hoặc `1`. Không thêm field khác vào request.

### Không giới hạn mức tối đa

```json
{
  "name": "Hoa hồng 5%, không giới hạn tối đa",
  "rate": 0.05,
  "minCommission": 0,
  "maxCommission": null,
  "active": true,
  "reason": "Bỏ mức phí tối thiểu và tối đa cho đơn mới"
}
```

Có thể bỏ field `maxCommission` thay vì gửi `null`.

`maxCommission: 0` là **giới hạn hoa hồng bằng 0**, không phải không giới hạn. Nó chỉ hợp lệ khi `minCommission: 0`; khi đó hoa hồng thực tế bằng 0. `minCommission: 10000` đi cùng `maxCommission: 0` trả 400. Khi `rate: 0` nhưng min lớn hơn 0, mức phí tối thiểu vẫn áp dụng.

### Công thức đúng theo code

```text
commissionBase = finalPrice của đơn (giá mua, có thể là giá đã thương lượng)
rawCommission = làm tròn HALF_UP(commissionBase × rate, 2 chữ số thập phân)
cappedCommission = rawCommission nếu maxCommission là null
                   min(rawCommission, maxCommission) nếu có maxCommission
platformCommission = làm tròn HALF_UP(
    min(commissionBase, max(minCommission, cappedCommission)), 2 chữ số thập phân)
sellerPayout = làm tròn HALF_UP(commissionBase - platformCommission, 2 chữ số thập phân)
```

Phí hoa hồng luôn được chặn không vượt giá sản phẩm. Phí vận chuyển không nằm trong `commissionBase` và không được cộng vào `sellerPayout`.

Ví dụ với rate 5%, min 10.000đ, max 100.000đ:

| Giá sản phẩm | Phí theo 5% | Hoa hồng thực tế | SELLER thực nhận |
| --- | --- | --- | --- |
| 5.000đ | 250đ | 5.000đ (chặn theo giá sản phẩm) | 0đ |
| 100.000đ | 5.000đ | 10.000đ (áp dụng min) | 90.000đ |
| 1.000.000đ | 50.000đ | 50.000đ | 950.000đ |
| 5.000.000đ | 250.000đ | 100.000đ (áp dụng max) | 4.900.000đ |

## 5. Test quản lý chính sách bằng token ADMIN

### Bước 1 — Xem chính sách đang có

```http
GET /api/admin/commission-rules?page=0&size=10
```

Tìm chính sách có `active=true` trong `data.items` và lưu `id` làm `ruleId`. Nếu có nhiều trang, kiểm tra các trang tiếp theo. Backend chỉ cho phép một DEFAULT đang bật trên toàn bộ dữ liệu.

### Bước 2 — Tạo hoặc sửa chính sách 5%

Nếu chưa có chính sách đang bật:

```http
POST /api/admin/commission-rules
```

Gửi JSON 5% ở mục 4. Kỳ vọng HTTP 201, lấy `data.id` làm `ruleId`; chính sách mới có `revision=1`.

Nếu đã có chính sách đang bật, dùng:

```http
PUT /api/admin/commission-rules/{ruleId}
```

Gửi đầy đủ JSON 5% ở mục 4. Kỳ vọng HTTP 200, `revision` tăng thêm 1. Đây là PUT toàn bộ request; chỉ gửi mỗi `rate` sẽ thiếu các field bắt buộc.

Gọi lại:

```http
GET /api/admin/commission-rules/{ruleId}
```

Kiểm tra `data.rate`, `data.minCommission`, `data.maxCommission`, `data.active`, `data.revision`.

### Bước 3 — Xem audit

```http
GET /api/admin/commission-rules/{ruleId}/history?page=0&size=10
```

Các bản ghi ở `data.items` có `action` (`CREATE`, `UPDATE`, `DEACTIVATE`), `actorId`, `reason`, `changedAt`, `oldValue`, `newValue`. `oldValue` và `newValue` là chuỗi chứa JSON; FE có thể parse để hiển thị cấu hình trước/sau. `oldValue` có thể null hoặc không xuất hiện khi tạo mới.

### Bước 4 — Tắt và bật lại (test sau luồng đặt hàng)

```http
POST /api/admin/commission-rules/{ruleId}/deactivate
```

```json
{
  "reason": "Tạm dừng chính sách để kiểm tra luồng cấu hình"
}
```

Kỳ vọng `data.active=false`. Tắt một chính sách đã tắt trả lại dữ liệu, không tăng revision hoặc thêm audit lần nữa.

Nếu không còn DEFAULT đang bật, tạo đơn mới trả 409 `Default commission policy is not configured`. Đơn đã có snapshot vẫn có thể quyết toán theo snapshot cũ.

Để bật lại, gọi PUT với đầy đủ JSON và `active:true`. Nếu muốn thay bằng chính sách khác, tắt chính sách đang bật trước rồi tạo/bật chính sách mới. Không có API DELETE chính sách.

## 6. Test từ đặt hàng đến SELLER nhận tiền sau hoa hồng

Luồng này dùng một bài `ACTIVE` giá **1.000.000đ**, giao trực tiếp `SELLER_TO_BUYER`, và chính sách 5%/min 10.000đ/max 100.000đ ở trên. SELLER đã có địa chỉ lấy hàng; BUYER khác SELLER và ví đủ giá hàng + phí ship. Bài cần kiểm định phải hoàn tất luồng kiểm định trước; ví dụ này dành cho bài giao trực tiếp thông thường.

### Bước 1 — Lấy bài và chuẩn bị kiện hàng

BUYER gọi:

```http
GET /api/v1/posts?page=0&size=10
```

Chọn bài, lấy `data.content[].postId` làm `postId`. Endpoint `/api/v1/listings` đã được bỏ; dùng `/api/v1/posts`.

Nếu chưa lưu kích thước kiện, SELLER của bài gọi:

```http
PUT /api/v1/posts/{postId}/shipping-package
```

```json
{
  "weight": 1000,
  "length": 20,
  "width": 15,
  "height": 10
}
```

`weight` tính bằng gram; kích thước tính bằng cm. Chuẩn bị trước khi lấy báo giá; đổi kiện hàng có thể làm báo giá đã lấy không còn hợp lệ.

### Bước 2 — BUYER lấy báo giá vận chuyển

```http
POST /api/v1/shipping/quotes
```

```json
{
  "postId": "11111111-1111-4111-8111-111111111111",
  "deliveryAddress": {
    "name": "Nguyễn Văn A",
    "phone": "0912345678",
    "address": "456 Nguyễn Huệ",
    "provinceName": "Hồ Chí Minh",
    "wardName": "Phường Sài Gòn",
    "newAddress": true
  }
}
```

Thay `postId` bằng bài thật; chọn tên tỉnh/phường từ `GET /api/v1/shipping/provinces` và `GET /api/v1/shipping/wards?provinceId={provinceId}`. Tên trong JSON chỉ là ví dụ, cần khớp dữ liệu GHN của môi trường đang dùng. Đây là địa chỉ mới (`newAddress:true`) nên bỏ thông tin quận và mã địa chỉ legacy. Không gửi các field kiểm tra `legacyCodesValid`/`districtNameValid` nếu Swagger gợi ý chúng.

Mua ngay không cần `negotiationId`. Lưu `data.quoteId` làm `shippingQuoteId`, kiểm tra `data.productPrice`, `data.shippingFee`, `data.totalPayable`, `data.expiresAt`. BUYER phải có đủ `totalPayable` trong ví; báo giá hết hạn thì lấy lại.

### Bước 3 — BUYER đặt đơn, backend tự tạo snapshot

```http
POST /api/v1/orders
```

```json
{
  "shippingQuoteId": "22222222-2222-4222-8222-222222222222",
  "requestId": "33333333-3333-4333-8333-333333333333",
  "postId": "11111111-1111-4111-8111-111111111111"
}
```

Thay `shippingQuoteId` và `postId` bằng dữ liệu thật. Tạo UUID mới cho `requestId` của thao tác mới; khi retry cùng thao tác phải giữ nguyên requestId và toàn bộ nội dung request.

API này trả trực tiếp `OrderResponseDTO`, **không bọc trong `data`**. Lấy trường **`id`** làm `orderId`. Kỳ vọng `status=PROCESSING`, `escrowStatus=HELD`; ví BUYER giảm `finalPrice + shippingFee`. Snapshot được tạo tự động trong cùng giao dịch, chưa cộng tiền cho SELLER.

### Bước 4 — Xem hoa hồng của đơn

BUYER, SELLER của đơn hoặc tài khoản có quyền xem toàn bộ gọi:

```http
GET /api/v1/orders/{orderId}/commission
```

Các giá trị cần kiểm tra trong `data` cho ví dụ 1.000.000đ:

```json
{
  "ruleType": "DEFAULT",
  "rate": 0.05,
  "minCommission": 10000,
  "maxCommission": 100000,
  "baseType": "PRODUCT_PRICE",
  "commissionBase": 1000000,
  "rawCommission": 50000,
  "platformCommission": 50000,
  "sellerPayout": 950000,
  "currency": "VND"
}
```

Đây là trích các field để đối chiếu, không phải toàn bộ response. Response còn có `snapshotId`, `orderId`, `ruleId`, `ruleRevision`, `ruleName`, `snapshottedAt`, `capturedBy`, `reason`, `categoryId`, `transactionValueFrom`, `transactionValueTo` (field null có thể được bỏ khi serialize).

`categoryId` trong snapshot lưu danh mục sản phẩm để đối chiếu lịch sử; không có nghĩa đang áp dụng chính sách theo category. Không gửi những field này để cấu hình chính sách mới.

Trước khi BUYER xác nhận nhận hàng, GET `/api/v1/orders/{orderId}/settlement` trả 404 `Order has not been settled` là đúng.

### Bước 5 — SELLER tạo vận đơn, GHN báo giao hàng

```http
POST /api/v1/orders/{orderId}/shipments
```

```json
{
  "requestId": "44444444-4444-4444-8444-444444444444",
  "leg": "SELLER_TO_BUYER"
}
```

Địa chỉ lấy hàng lấy từ hồ sơ SELLER, địa chỉ nhận lấy từ đơn/báo giá. Lưu `data.shipmentId` và `data.orderCode`. Nếu `status=CREATION_UNCERTAIN`, retry cùng requestId để xác nhận việc tạo vận đơn trước khi tiếp tục.

GHN gửi webhook `picked` rồi `delivered`. Để test trên staging/local, có thể gọi webhook giả lập **riêng cho đơn test**:

```http
POST /api/v1/shipping/callback
Content-Type: application/json
X-GHN-Secret: <giá trị GHN_WEBHOOK_SECRET của backend test>
```

JSON 1 — nhận hàng:

```json
{
  "ShopID": 123456,
  "OrderCode": "MA_GHN_THAT",
  "Type": "switch_status",
  "Status": "picked"
}
```

JSON 2 — giao thành công:

```json
{
  "ShopID": 123456,
  "OrderCode": "MA_GHN_THAT",
  "Type": "switch_status",
  "Status": "delivered"
}
```

Thay `ShopID` bằng **GHN_SHOP_ID** đang cấu hình (JSON number, không phải mã Client), `OrderCode` bằng `data.orderCode` thật. Bỏ `Time` thì backend dùng thời điểm nhận callback; gửi hai request lần lượt, tại thời điểm khác nhau. Nếu tự gửi `Time`, dùng thời gian ISO-8601 tăng dần, không ở tương lai; event được chống lặp theo mã đơn + loại event + thời điểm.

Callback là đầu nhận sự kiện của GHN, không phải API STAFF dùng để đổi trạng thái đơn thật. Secret chỉ dùng trong backend/công cụ test, không đưa vào React. Giả lập callback cập nhật SecondLife, không đổi trạng thái trên website GHN.

Kiểm tra bằng GET `/api/v1/orders/{orderId}/shipments` và GET `/api/v1/orders/buyer?page=0&size=10`. Sau `delivered`, đơn phải có `status=DELIVERED`, `shippingDeliveredAt` khác null, `escrowStatus=HELD`.

### Bước 6 — BUYER xác nhận nhận hàng để quyết toán

```http
PUT /api/v1/orders/{orderId}/delivered
```

**Không có request body.** Dùng token BUYER của đơn. Không nhập JSON hoa hồng vào API này.

Backend tạo settlement từ snapshot, cộng 950.000đ vào ví SELLER cho ví dụ trên, rồi chuyển đơn sang `COMPLETED` và ký quỹ sang `RELEASED`. Gọi lại sau khi hoàn tất trả đơn hiện có, không giải ngân thêm lần nữa.

GHN mới `picked`, chưa `delivered`, hoặc ký quỹ đang `FROZEN` thì chưa thể hoàn tất bước này.

### Bước 7 — Xem settlement và đối chiếu ví SELLER

```http
GET /api/v1/orders/{orderId}/settlement
```

Kiểm tra `data.commissionBase=1000000`, `data.rawCommission=50000`, `data.platformCommission=50000`, `data.sellerPayout=950000`, `data.currency=VND`, `data.roundingMode=HALF_UP`, cùng `data.settlementId`, `data.commissionSnapshotId`, `data.shippingFee`, `data.settledAt`.

Đổi sang token SELLER:

```http
GET /api/v1/wallets/me
GET /api/v1/wallets/me/transactions?page=0&size=10
```

So sánh số dư ví trước/sau xác nhận; mức tăng phải bằng `sellerPayout`. Lịch sử ở `items` của response giao dịch có `type=EARNING`, `amount=950000`, `referenceId=orderId`. Khi payout bằng 0, không tạo giao dịch EARNING.

`platformCommission` được ghi trong settlement. Luồng hiện tại không có bước cộng khoản này vào một ví nền tảng riêng; không suy diễn rằng GET settlement đã chuyển tiền hoa hồng tới một ví ADMIN.

## 7. Test snapshot không đổi khi ADMIN sửa chính sách

1. Tạo đơn A khi chính sách đang là 5%, ghi lại GET commission của đơn A.
2. ADMIN gọi PUT `/api/admin/commission-rules/{ruleId}` với JSON:

```json
{
  "name": "Hoa hồng mặc định 10%",
  "rate": 0.1,
  "minCommission": 10000,
  "maxCommission": 100000,
  "active": true,
  "reason": "Đổi tỷ lệ cho các đơn hàng tạo sau thời điểm cập nhật"
}
```

3. GET commission của đơn A vẫn có `rate=0.05`, phiên bản cũ và payout 950.000đ nếu giá sản phẩm là 1.000.000đ.
4. Dùng **bài ACTIVE khác** để tạo đơn B sau cập nhật. Với giá 1.000.000đ, đơn B có `rate=0.1`, commission 100.000đ, payout 900.000đ và `ruleRevision` mới.
5. Hoàn tất đơn A: settlement vẫn dùng 5%, không dùng 10% vừa cấu hình.

GET commission tính số tiền từ snapshot. Việc có snapshot không có nghĩa đã trả tiền cho SELLER; chỉ settlement sau xác nhận mới giải ngân.

## 8. Bổ sung snapshot cho đơn cũ

Chỉ dùng khi đơn tạo trước tính năng commission, chưa có snapshot và chưa hoàn tất/hủy. ADMIN gọi:

```http
POST /api/admin/orders/{orderId}/commission-snapshot
```

```json
{
  "reason": "Bổ sung chính sách hoa hồng cho đơn cũ trước khi quyết toán"
}
```

Đơn phải còn ký quỹ `HELD` hoặc `FROZEN`. API dùng chính sách DEFAULT đang bật **tại thời điểm gọi**, không phục hồi một tỷ lệ lịch sử chưa được lưu. Nếu đơn đã có snapshot hợp lệ, API giữ snapshot đó. Đơn `COMPLETED`/`CANCELLED` bị từ chối.

Việc bổ sung snapshot không xử lý trạng thái FROZEN, không tạo vận đơn và không giải ngân. Vẫn cần hoàn tất điều kiện giao hàng/ký quỹ rồi BUYER xác nhận nhận hàng. Đơn mới đã tự tạo snapshot nên FE không gọi API này trong luồng đặt hàng thông thường.

## 9. Các trường hợp lỗi nên test

| Trường hợp | Kỳ vọng |
| --- | --- |
| Tạo thêm DEFAULT active khi đã có một DEFAULT active | 409 `An active default commission policy already exists` |
| Đặt đơn khi không có DEFAULT active | 409 `Default commission policy is not configured` |
| Thiếu `name`, `rate`, `minCommission`, `active` hoặc `reason` | 400, `errors` chỉ field sai |
| `rate` là `5`, âm hoặc nhiều hơn 6 chữ số thập phân | 400 |
| `minCommission`/`maxCommission` âm hoặc quá 2 chữ số thập phân | 400 |
| `maxCommission` nhỏ hơn `minCommission` | 400, lỗi tại `maxCommission` |
| Số/boolean gửi dưới dạng chuỗi; thêm `type` hoặc `categoryId` vào request | 400 |
| `ruleId`/`orderId` sai định dạng UUID | 400 |
| ID chính sách hoặc đơn không tồn tại | 404 |
| BUYER/SELLER gọi API quản lý chính sách ADMIN | 403 với tài khoản đã đăng nhập |
| Người không liên quan đọc commission/settlement của đơn, không có quyền xem toàn bộ | 403 |
| Đọc commission của đơn cũ chưa có snapshot | 409, cần ADMIN bổ sung snapshot |
| Đọc settlement trước quyết toán | 404 `Order has not been settled` |
| BUYER xác nhận nhận hàng trước khi GHN báo delivered | 409 |
| BUYER xác nhận khi ký quỹ không ở HELD | 409 |
| Callback sai `ShopID` | 403 `Webhook shop does not match` |
| Callback thiếu/sai `X-GHN-Secret` | 403 `Invalid GHN webhook credentials` |

Ví dụ response validation (timestamp minh họa):

```json
{
  "success": false,
  "message": "Validation failed",
  "path": "/api/admin/commission-rules",
  "errors": [
    {
      "field": "maxCommission",
      "message": "maxCommission must be greater than or equal to minCommission"
    }
  ],
  "timestamp": "2026-10-09T15:00:00"
}
```

FE hiển thị `errors[].message` tại field tương ứng và `message` cho lỗi nghiệp vụ. Không tự tính payout để gửi lên server; các API tạo/sửa đơn không nhận `platformCommission` hay `sellerPayout` từ FE.

## 10. Màn hình FE có thể nối từ các API này

| Màn hình/thao tác | API sử dụng |
| --- | --- |
| ADMIN xem và chỉnh chính sách hiện tại | List/get/create/update commission rule |
| ADMIN tắt/bật và xem ai đã sửa | Deactivate, PUT active=true, history |
| SELLER xem hoa hồng và tiền dự kiến nhận của từng đơn | GET order commission |
| BUYER xác nhận đã nhận hàng | PUT order delivered |
| SELLER xem tiền thực nhận sau giao dịch | GET order settlement và wallet/transactions |
| ADMIN/STAFF có quyền xem tài chính đơn | GET order commission/settlement |
| ADMIN xử lý đơn cũ thiếu snapshot | POST commission-snapshot |

Màn hình SELLER nên tách “Dự kiến nhận” từ commission và “Đã quyết toán” từ settlement. Không hiển thị đã nhận tiền chỉ dựa vào trạng thái shipment DELIVERED.
