# Hướng dẫn FE: Ví hệ thống, giải ngân và STAFF duyệt bài nghi trùng

Tài liệu đối chiếu API/DTO trong BE hiện tại ngày 13/10/2026. Response ví dụ dùng số liệu minh họa; thay UUID bằng ID thật nhận từ API. FE không gọi API vận chuyển hoặc tự sửa số dư để giải ngân.

## 1. Kết nối và phân quyền

Base URL local: `http://localhost:8080`. Khi triển khai, lấy base URL từ cấu hình FE.

Gửi `Authorization: Bearer <accessToken>` cho các API trong tài liệu. Request có JSON gửi thêm `Content-Type: application/json`. GET và API không có body không gửi JSON mẫu `string`.

| Màn hình | Điều kiện quyền BE |
|---|---|
| Ví hệ thống ADMIN | Role `ADMIN` và permission `ADMIN_COMMISSION_MANAGE` |
| STAFF kiểm duyệt bài | Role `STAFF` và permission `STAFF_LISTING_REVIEW` |
| ADMIN kiểm duyệt bài bằng API STAFF | Role `ADMIN` và permission `POST_REVIEW` |
| BUYER xác nhận nhận hàng | Đăng nhập, đúng BUYER của đơn |
| Xem commission/settlement | BUYER hoặc SELLER của đơn; ADMIN có `ADMIN_COMMISSION_MANAGE`; STAFF có `STAFF_PAYOUT_REVIEW` |

Ẩn menu/nút theo quyền để UI rõ ràng. BE vẫn kiểm tra quyền; không coi việc FE ẩn nút là cơ chế bảo mật.

## 2. Chú ý cấu trúc response

Các endpoint có cấu trúc khác nhau, cần đọc đúng:

| Endpoint | Nơi đọc dữ liệu |
|---|---|
| GET ví hệ thống | `response.data` |
| GET giao dịch ví hệ thống | `response.data.items` |
| GET danh sách STAFF | `response.data.content` |
| GET chi tiết STAFF, POST approve/reject | `response.data` |
| GET commission/settlement | `response.data` |
| GET wallets/me | Response trực tiếp WalletResponseDTO, đọc `response.balance` |
| GET wallets/me/transactions | PageResponse trực tiếp, đọc `response.items` |
| GET orders/buyer, orders/seller | `response.content` — Spring Page không bọc ApiResponse |
| PUT orders/{id}/delivered | Response trực tiếp là OrderResponseDTO, đọc `response.id`, `response.status` |

Ở đây `response` là JSON body đã parse, không phải object Axios. Với Axios phải lấy `axiosResponse.data` trước.

## 3. Màn hình ADMIN — Ví hệ thống

### 3.1. Bố cục UI

Hiển thị bốn thẻ số dư, đơn vị VND, và bảng lịch sử giao dịch bên dưới. Có nút tải lại, loading, trạng thái rỗng và thông báo lỗi.

| Field | Nhãn UI | Ý nghĩa |
|---|---|---|
| `heldBalance` | Tiền sản phẩm đang ký quỹ | Bao gồm tiền đang HELD và FROZEN, chưa trả SELLER hoặc hoàn BUYER |
| `shippingBalance` | Phí vận chuyển đã thu | Phí đã thu, trừ phần phí đã hoàn; không phải lợi nhuận hoa hồng |
| `revenueBalance` | Hoa hồng đã nhận | Hoa hồng của các đơn đã quyết toán |
| `totalBalance` | Tổng số dư ghi nhận | Tổng ba khoản trên |
| `currency` | Đơn vị tiền | Hiện là VND |
| `updatedAt` | Ví cập nhật lúc | Thời điểm ghi nhận thay đổi số dư gần nhất |

Đây là ví kế toán nội bộ phục vụ đơn hàng và hoa hồng. Hiện không có API nạp/rút ví hệ thống, chuyển về ngân hàng hoặc tự động đối soát khoản chi từ tài khoản Lalamove. Không hiển thị nút cho các thao tác đó.

### 3.2. Lấy số dư

```http
GET /api/v1/admin/system-wallet
```

Ví dụ response:

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "id": "MAIN",
    "currency": "VND",
    "heldBalance": 1000000,
    "shippingBalance": 27000,
    "revenueBalance": 0,
    "totalBalance": 1027000,
    "updatedAt": "2026-10-13T05:00:00Z"
  },
  "timestamp": "2026-10-13T12:00:00"
}
```

### 3.3. Lấy lịch sử

```http
GET /api/v1/admin/system-wallet/transactions?page=0&size=10
```

Trang bắt đầu từ 0. Sắp xếp mới nhất trước do BE quyết định; FE không cần gửi `sort`. Kích thước trang phải nằm trong giới hạn phân trang của môi trường BE.

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "items": [
      {
        "id": 12,
        "orderId": "6fd6fa8e-5e68-4736-916f-c582cc4f16a9",
        "type": "SETTLEMENT",
        "heldDelta": -1000000,
        "shippingDelta": 0,
        "revenueDelta": 50000,
        "heldBalanceAfter": 0,
        "shippingBalanceAfter": 27000,
        "revenueBalanceAfter": 50000,
        "createdAt": "2026-10-13T06:00:00Z"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 2,
    "totalPages": 1,
    "isLast": true
  },
  "timestamp": "2026-10-13T13:00:00"
}
```

| Type | Nhãn UI | Cách hiểu |
|---|---|---|
| `OPENING_BALANCE` | Số dư đối chiếu ban đầu | Migration đối chiếu đơn cũ từ lịch sử thanh toán, hoàn tiền và thu nhập; không phải một lần thanh toán mới |
| `PAYMENT_HOLD` | Nhận ký quỹ | BUYER thanh toán; tăng tiền sản phẩm giữ hộ và phí vận chuyển |
| `SETTLEMENT` | Giải ngân | Giảm toàn bộ ký quỹ sản phẩm, ghi nhận hoa hồng; SELLER nhận phần còn lại |
| `REFUND` | Hoàn tiền | Giảm ký quỹ và phần phí vận chuyển được hoàn theo quyết định BE |

Các field `*Delta` là thay đổi có dấu; `*BalanceAfter` là số dư toàn ví sau giao dịch, không phải số dư riêng của đơn. Lịch sử không có API chỉnh sửa/xóa.

Khi bấm `orderId`, ADMIN có thể gọi `GET /api/v1/orders/{orderId}/commission` và `GET /api/v1/orders/{orderId}/settlement` để xem số tiền theo đơn. Settlement trả 404 nếu chưa quyết toán: hiển thị “Chưa giải ngân”.

### 3.4. Công thức hiển thị và ví dụ

Giá sản phẩm 1.000.000đ, phí vận chuyển 27.000đ, hoa hồng theo snapshot của đơn 50.000đ:

1. BUYER trả 1.027.000đ. Ký quỹ tăng 1.000.000đ, phí vận chuyển tăng 27.000đ.
2. Khi quyết toán, SELLER nhận 950.000đ vào ví cá nhân.
3. Ký quỹ giảm 1.000.000đ, hoa hồng tăng 50.000đ. Phí vận chuyển vẫn ghi riêng.

FE hiển thị giá trị commission/sellerPayout do BE trả. Không tự lấy rule hoa hồng hiện tại để tính lại đơn cũ vì mỗi đơn dùng snapshot tại thời điểm tạo đơn.

## 4. BUYER xác nhận nhận hàng và giải ngân tự động

### 4.1. Nút “Đã nhận hàng”

Lấy đơn từ `GET /api/v1/orders/buyer?page=0&size=10`. Chỉ bật nút khi:

- Đúng BUYER của đơn.
- `status === "DELIVERED"`.
- `escrowStatus === "HELD"`.
- `shippingDeliveredAt` có giá trị.

BUYER xác nhận bằng hộp thoại, sau đó gọi:

```http
PUT /api/v1/orders/{id}/delivered
```

Không có request body. `{id}` là `id` của order, không phải `postId`, `shipmentId` hoặc mã đơn Lalamove.

Response trực tiếp (không có `data` bọc ngoài):

```json
{
  "id": "6fd6fa8e-5e68-4736-916f-c582cc4f16a9",
  "postId": "1c2dd5e3-36db-420b-b313-9ed606b017a9",
  "postTitle": "Nồi cơm điện",
  "buyerId": "a7e86c92-c863-4853-900d-a63eaf294949",
  "sellerId": "542010c9-4da6-4881-a5bb-fbb10189c69d",
  "negotiationId": null,
  "finalPrice": 1000000,
  "shippingFee": 27000,
  "totalPaid": 1027000,
  "shippingQuoteId": "e456898e-7f33-47c9-8ea1-cdb3f4eb4202",
  "shippingDeliveredAt": "2026-10-13T05:00:00Z",
  "status": "COMPLETED",
  "escrowStatus": "RELEASED",
  "createdAt": "2026-10-13T04:00:00Z"
}
```

Sau thành công, cập nhật đơn từ response, tải lại ví cá nhân bằng `GET /api/v1/wallets/me` nếu màn hình có hiển thị ví, và gọi settlement để hiển thị quyết toán. Nếu job đã giải ngân trước nút bấm, gọi lại trên đơn COMPLETED/RELEASED trả kết quả cũ, không cộng tiền lần nữa.

### 4.2. Quyết toán theo đơn

```http
GET /api/v1/orders/{orderId}/settlement
```

Các field cần đưa lên UI:

| Field | Ý nghĩa |
|---|---|
| `commissionBase` | Giá sản phẩm dùng tính hoa hồng |
| `rawCommission` | Hoa hồng trước áp dụng giới hạn |
| `platformCommission` | Hoa hồng thực thu |
| `sellerPayout` | Tiền SELLER thực nhận |
| `shippingFee` | Phí vận chuyển của đơn |
| `settledAt` | Thời điểm giải ngân |
| `settlementId`, `commissionSnapshotId` | ID đối chiếu lịch sử |
| `currency`, `roundingMode` | Đơn vị tiền và quy tắc làm tròn |

### 4.3. Tự giải ngân sau 48 giờ

BE tính từ `shippingDeliveredAt`, là thời điểm nhà vận chuyển xác nhận giao thành công. Không tính từ lúc đặt hàng, thanh toán hoặc lúc FE mở trang.

Cấu hình local hiện tại:

```env
ORDER_AUTO_RELEASE_ENABLED=true
ORDER_AUTO_RELEASE_DELAY_HOURS=48
ORDER_AUTO_RELEASE_INTERVAL_MS=60000
ORDER_AUTO_RELEASE_BATCH_SIZE=100
```

Job chỉ giải ngân khi đơn DELIVERED và ký quỹ HELD; đơn FROZEN, REFUNDED, CANCELLED hoặc đã RELEASED không được giải ngân tự động. Backend phải đang chạy; khi khởi động lại, job xử lý các đơn quá hạn.

FE không cần gọi API “chạy job”. Có thể hiển thị mốc dự kiến giao thành công + 48 giờ, nhưng countdown chỉ phục vụ hiển thị, không quyết định trạng thái tiền. Nếu môi trường đổi thời hạn, FE cần dùng cấu hình tương ứng; hiện API chưa trả field riêng cho hạn giải ngân.

Sau khi đơn đổi thành COMPLETED/RELEASED, SELLER tải lại `GET /api/v1/orders/seller`, ví cá nhân và settlement. Không hiển thị nút STAFF/Admin tự giải ngân bằng API xác nhận của BUYER.

## 5. STAFF — Hàng chờ bài nghi trùng

### 5.1. Phần BE đã có và phần chưa có

BE đã có:

- Kiểm tra khi SELLER gọi `POST /api/v1/posts/submit/{postId}`.
- So sánh ảnh giống hệt, fingerprint ảnh tương tự và nội dung cùng sản phẩm của cùng SELLER. Đây là phát hiện nghi vấn, không phải kết luận chắc chắn mọi ảnh crop/ảnh tương tự đều trùng.
- Đối chiếu với các bài ACTIVE, PENDING_INSPECTION và PENDING.
- Bài nghi trùng chuyển PENDING, lưu `reviewReason`, `duplicateMatches`, chưa trừ Listing Credit.
- API danh sách chờ duyệt, xem chi tiết, chấp nhận, từ chối; ghi người và thời điểm duyệt trong BE.

BE chưa có thông báo riêng cho bài nghi trùng: chưa có hộp thông báo lưu theo STAFF, API unread/mark-read, hoặc event realtime cho luồng này. WebSocket chat hiện có không phải kênh thông báo kiểm duyệt.

FE hiện có thể làm menu “Bài chờ kiểm duyệt”, lấy danh sách từ API và badge số bài chờ. Badge là số công việc chờ, không phải số thông báo chưa đọc.

### 5.2. Lấy danh sách

```http
GET /api/v1/staff/listings?page=0&size=10
```

BE trả ApiResponse bọc Spring Page:

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "content": [
      {
        "postId": "11111111-1111-4111-8111-111111111111",
        "categoryId": null,
        "itemId": null,
        "title": "Nồi cơm điện đã qua sử dụng",
        "description": "Hoạt động tốt, có vết xước nhẹ",
        "itemCondition": "USED",
        "imageUrl": "https://res.cloudinary.com/example/image/upload/photo.jpg",
        "imageUrls": ["https://res.cloudinary.com/example/image/upload/photo.jpg"],
        "price": 1000000,
        "aiSuggestedPrice": null,
        "status": "PENDING",
        "rejectionReason": null,
        "listingCreditCharged": false,
        "aiDescription": null,
        "descriptionAccepted": true,
        "reviewReason": "Possible duplicate product images or listing content; STAFF review required",
        "duplicateMatches": ["22222222-2222-4222-8222-222222222222"]
      }
    ],
    "number": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true,
    "numberOfElements": 1,
    "empty": false
  },
  "timestamp": "2026-10-13T12:00:00"
}
```

Ví dụ rút gọn phần metadata của Spring Page; URL ảnh chỉ minh họa, không dùng làm dữ liệu upload/test thực tế. Danh sách trả mọi bài PENDING, không có query filter riêng `duplicate=true`. FE đánh dấu nghi trùng khi `duplicateMatches.length > 0`; tổng `totalElements` là tổng bài chờ duyệt, không phải tổng bài trùng.

Danh sách hiện sắp theo createdAt tăng dần. DTO chưa trả sellerId, tên SELLER, createdAt hay người duyệt; không suy đoán hoặc hiển thị các giá trị đó từ field khác.

### 5.3. Chi tiết và so sánh ảnh

```http
GET /api/v1/staff/listings/{postId}
```

Đọc `response.data`. Thiết kế hai cột:

1. Bài chờ duyệt: title, description, price, categoryId/itemId và gallery từ imageUrls.
2. Các bài nghi trùng: với mỗi UUID trong duplicateMatches, gọi cùng endpoint chi tiết bằng UUID đó để lấy ảnh và mô tả so sánh.

Không dùng API danh sách bài công khai để lấy bài đối chiếu PENDING/PENDING_INSPECTION vì bài chưa đăng có thể không nằm trong dữ liệu công khai. Dùng token STAFF cho cả bài đang duyệt và các bài đối chiếu.

Hiển thị reviewReason là lý do BE đưa bài vào hàng chờ. duplicateMatches chỉ là danh sách postId, không chứa ảnh hoặc điểm tương đồng. Nếu bài đối chiếu trả 404, hiển thị “Bài đối chiếu không còn tồn tại” và vẫn cho STAFF đọc các bài còn lại.

### 5.4. Chấp nhận

```http
POST /api/v1/staff/listings/{postId}/approve
```

Không có body. Chỉ bật nút cho bài đang PENDING. Khi response thành công:

- ACTIVE: đã được đăng, Listing Credit được trừ một lần.
- PENDING_INSPECTION: đã duyệt bước nghi trùng nhưng còn chờ kiểm định; chưa coi là đang bán, credit đăng bài được trừ khi kích hoạt sau kiểm định đạt.

Nếu thiếu Listing Credit khi cần kích hoạt, BE trả 409 và bài vẫn PENDING. FE hiển thị message, không tự chuyển sang ACTIVE.

Sau thao tác, tải lại danh sách, badge và chi tiết. Khi danh sách trang hiện tại trống do xử lý bài cuối cùng, quay về trang trước hoặc trang 0.

### 5.5. Từ chối

```http
POST /api/v1/staff/listings/{postId}/reject
Content-Type: application/json
```

```json
{
  "reason": "Ảnh sản phẩm trùng với bài đăng của người bán khác; vui lòng cung cấp ảnh thực tế của sản phẩm."
}
```

reason bắt buộc, sau trim không được rỗng, tối đa 2000 ký tự. Response `data.status` là REJECTED, `data.rejectionReason` chứa lý do. Không trừ Listing Credit.

Không tự động gọi reject chỉ vì BE báo nghi trùng. STAFF xem bằng chứng rồi quyết định. Disable các nút trong lúc gửi request để tránh thao tác lặp hoặc approve/reject cùng lúc.

### 5.6. Cập nhật badge và hàng chờ khi chưa có realtime

Đề xuất FE:

- Khi mở dashboard STAFF, gọi `GET /api/v1/staff/listings?page=0&size=1`, lấy `data.totalElements` làm badge “Chờ duyệt”.
- Trong màn hình danh sách, gọi API với trang đang xem.
- Có nút tải lại; có thể polling mỗi 30–60 giây khi tab đang hiển thị, dừng khi tab ẩn hoặc đăng xuất.
- Không tạo interval mới mỗi lần render; cleanup timer/AbortController khi component unmount, tránh request chồng nhau.
- Nếu tổng bài tăng, FE có thể hiện banner “Có bài mới chờ duyệt”; không coi đó là thông báo chưa đọc được BE lưu.

Trong API hiện tại chưa có field phân biệt thời điểm mới vào hàng chờ, nên không dùng thứ tự createdAt để kết luận chính xác bài vừa gửi duyệt.

## 6. Ví dụ kết nối bằng fetch

```js
// baseUrl lấy từ cấu hình FE; getAccessToken dùng cơ chế auth sẵn có của dự án.
async function api(path, { method = 'GET', body, signal } = {}) {
  const headers = { Authorization: `Bearer ${getAccessToken()}` };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const response = await fetch(`${baseUrl}${path}`, {
    method, headers, signal,
    ...(body !== undefined ? { body: JSON.stringify(body) } : {})
  });
  const payload = await response.json();
  if (!response.ok) {
    const error = new Error(payload.message || 'Không thể thực hiện yêu cầu');
    error.status = response.status;
    error.payload = payload;
    throw error;
  }
  return payload;
}

async function loadSystemWallet(page = 0, size = 10) {
  const [wallet, history] = await Promise.all([
    api('/api/v1/admin/system-wallet'),
    api(`/api/v1/admin/system-wallet/transactions?page=${page}&size=${size}`)
  ]);
  return { wallet: wallet.data, rows: history.data.items, pagination: history.data };
}

async function loadReviewQueue(page = 0, size = 10) {
  const result = await api(`/api/v1/staff/listings?page=${page}&size=${size}`);
  return { rows: result.data.content, total: result.data.totalElements, pagination: result.data };
}

async function approveListing(postId) {
  return (await api(`/api/v1/staff/listings/${postId}/approve`, { method: 'POST' })).data;
}

async function rejectListing(postId, reason) {
  return (await api(`/api/v1/staff/listings/${postId}/reject`, {
    method: 'POST', body: { reason: reason.trim() }
  })).data;
}

async function confirmReceipt(orderId) {
  // OrderResponseDTO trực tiếp, không lấy result.data.
  return api(`/api/v1/orders/${orderId}/delivered`, { method: 'PUT' });
}
```

Tích hợp cơ chế refresh token sẵn có khi 401; không retry vô hạn, không ghi accessToken vào log. Khi POST/PUT bị timeout, tải lại trạng thái trước khi báo thất bại; BE bảo vệ các thao tác lặp nhưng FE vẫn cần phản ánh trạng thái thực tế.

## 7. Xử lý lỗi và kiểm thử UI

| Mã | Cách xử lý FE |
|---|---|
| 400 | Hiển thị lỗi dữ liệu, page/size hoặc reason; đối chiếu `errors` nếu có |
| 401 | Refresh token theo luồng auth hoặc yêu cầu đăng nhập lại |
| 403 | Không có quyền hoặc không được xem dữ liệu; dừng polling màn hình đó |
| 404 | Tải lại danh sách; riêng settlement là “Chưa giải ngân” khi đơn chưa quyết toán |
| 409 | Trạng thái đã đổi, thiếu credit hoặc ký quỹ bị khóa; hiển thị message và tải lại dữ liệu |
| 5xx/network | Giữ dữ liệu đang hiển thị, cho thử lại; không cập nhật số dư/trạng thái bằng giả định |

Checklist bàn giao FE:

1. ADMIN đọc đúng bốn số dư và `data.items`; BUYER/SELLER không mở được ví hệ thống.
2. Khi BUYER đặt đơn thành công, ví ADMIN có ký quỹ và phí; chưa ghi hoa hồng.
3. Sau callback/sync xác nhận giao thành công, đơn DELIVERED/HELD và BUYER có nút nhận hàng.
4. BUYER bấm nhận hàng: đơn COMPLETED/RELEASED, SELLER nhận sellerPayout, ADMIN thấy SETTLEMENT và hoa hồng.
5. Đơn giao quá 48 giờ được BE giải ngân; FE cập nhật khi tải lại. Test mốc thời gian trong môi trường test, không tự sửa database thật để ép giải ngân.
6. Đơn FROZEN không có nút nhận hàng và không tự giải ngân; hoàn tiền phản ánh REFUND trong lịch sử.
7. SELLER gửi bài nghi trùng: PENDING, chưa trừ credit. STAFF lấy được bằng API hàng chờ.
8. STAFF xem bài chờ và từng bài duplicateMatches, rồi approve hoặc reject với reason.
9. Sau duyệt/từ chối, badge và danh sách cập nhật; SELLER đọc trạng thái/lý do qua API bài của mình.
10. Phân biệt badge công việc chờ duyệt với hệ thống thông báo chưa đọc. Chỉ sử dụng các API có trong tài liệu; phần thông báo realtime/unread cần triển khai BE riêng nếu muốn bổ sung.

## 8. Nguồn code để đối chiếu khi API thay đổi

- `controller/admin/AdminSystemWalletController.java`, `dto/response/SystemWalletResponse.java`, `dto/response/SystemWalletTransactionResponse.java`.
- `service/impl/SystemWalletServiceImpl.java`, `service/impl/SettlementServiceImpl.java`.
- `controller/OrderController.java`, `controller/OrderSettlementController.java`, `service/impl/OrderAutoReleaseService.java`, `service/impl/OrderAutoReleaseScheduler.java`.
- `controller/StaffListingController.java`, `service/ListingReviewService.java`, `service/ListingPublicationService.java`.
- `dto/response/ListingDraftResponse.java`, `dto/request/ListingReviewRejectRequest.java`, `service/impl/PostServiceImpl.java`.

Các đường dẫn Java ở trên tính từ `src/main/java/com/secondlife/secondlife/`. Khi FE triển khai hoặc test, ưu tiên response thật của BE đang chạy và phiên bản code tương ứng.
