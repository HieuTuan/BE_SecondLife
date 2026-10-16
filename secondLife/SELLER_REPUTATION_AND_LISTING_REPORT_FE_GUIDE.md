# Hướng dẫn FE — Điểm uy tín người bán và báo cáo bài đăng

Tài liệu đối chiếu code BE hiện tại. Base URL local: `http://localhost:8080`.
Các UUID trong JSON là minh họa; thay bằng ID thực tế. Request JSON dùng `Content-Type: application/json`.
API cần đăng nhập dùng `Authorization: Bearer <accessToken>`. Đổi tài khoản trong Authorize khi test Swagger.

## 1. Phân quyền

| Người dùng | Quyền và phạm vi |
|---|---|
| USER/BUYER đăng nhập | Báo cáo bài người khác, xem report của mình; chưa mua vẫn báo cáo được bài |
| Người upload bằng chứng | Cần `MEDIA_UPLOAD_SELF` |
| SELLER | Xem uy tín và lịch sử của mình với `PROFILE_READ_SELF`; xác nhận đơn của mình |
| STAFF | Cần role STAFF và `STAFF_FRAUD_REVIEW` để nhận/xử lý report |
| ADMIN xử lý report | Cần ADMIN và `ADMIN_RBAC_MANAGE` |
| ADMIN gỡ hạn chế | Cần ADMIN và `USER_STATUS_UPDATE` |

STAFF không được review report do mình gửi hoặc liên quan đến mình với tư cách seller.

## 2. Công thức điểm uy tín

```text
N = goodTransactions + badTransactions
transactionScore = round(100 × goodTransactions / N, 2)
score = max(0, transactionScore - confirmationPenaltyPoints)
```

Nếu N = 0, `score` và `transactionScore` là null: FE hiển thị “Chưa có giao dịch được đánh giá”, không hiển thị 0 điểm hoặc 100 điểm.

Ví dụ: 17 GOOD, 3 BAD → điểm giao dịch 85.00. Tổng phạt xác nhận đơn là 5 → điểm cuối 80.00.
Một đơn tối đa tính một kết quả GOOD/BAD; không cộng nhiều BAD vì có nhiều report hoặc nhiều lỗi trên cùng đơn.

- Đơn hoàn thành và ký quỹ đã giải ngân được đánh giá; lỗi seller đã xác nhận có thể khiến đơn được tính BAD.
- Các đơn hủy/hoàn tiền được đánh giá theo nguyên nhân và trách nhiệm đã ghi nhận; không phải mọi đơn hủy đều là BAD.
- Report đơn chưa kết thúc có thể ghi nhận lỗi trước; việc đánh giá giao dịch phụ thuộc trạng thái kết thúc.
- Report bài đăng chỉ ghi nhận vi phạm/cảnh báo; không tạo đơn BAD và không trực tiếp trừ điểm giao dịch.
- Vi phạm xác nhận đơn có phần phạt riêng, không được FE tự cộng/trừ lại.

`assessmentStatus`: `NO_TRANSACTIONS`, `INSUFFICIENT_EVIDENCE`, `EVALUATED`. FE dùng `minimumEvaluatedOrders` BE trả về để giải thích mức độ đủ dữ liệu, không hardcode ngưỡng.

## 3. API và UI điểm uy tín

### 3.1 Trang chi tiết bài hoặc hồ sơ seller

```http
GET /api/v1/sellers/{sellerId}/reputation
```

Các field minh họa trong `data` (không phải toàn bộ response):

```json
{
  "score": 80.00,
  "transactionScore": 85.00,
  "confirmationPenaltyPoints": 5,
  "goodTransactions": 17,
  "badTransactions": 3,
  "evaluatedOrders": 20,
  "minimumEvaluatedOrders": 5,
  "assessmentStatus": "EVALUATED",
  "confirmedIncidents": 1,
  "seriousIncidents": 1,
  "sellingRestricted": false,
  "warningCode": "SERIOUS_VIOLATION_HISTORY"
}
```

UI nên hiển thị điểm /100, số GOOD/BAD, số đơn đã đánh giá, điểm phạt xác nhận và cảnh báo riêng.
BE còn trả các chỉ số `sellerCancellationOrders`, `sellerReturnFaultOrders`, `nonFulfillmentOrders`, `nonFulfillmentRate`, `handoverEvaluatedOrders`, `lateHandoverOrders`, `lateHandoverRate`. FE dùng giá trị BE, không tự suy ra lỗi từ trạng thái vận chuyển.

### 3.2 Dashboard SELLER

```http
GET /api/v1/seller/reputation/me
GET /api/v1/seller/reputation/me/history?page=0&size=10
```

Lịch sử nằm ở `data.items`, phân trang có `page`, `size`, `totalElements`, `totalPages`, `isLast`.
Hiển thị `eventType`, `createdAt`, `orderId`/`reportId` khi có, `scoreBefore`, `scoreAfter`, `penaltyBefore`, `penaltyAfter`, `goodBefore`, `goodAfter`, `badBefore`, `badAfter`.
Một số bản ghi lịch sử cũ có snapshot null: hiển thị “Không có dữ liệu lịch sử”, không tự dựng điểm.

### 3.3 Xác nhận đơn và phạt quá hạn

Sau khi đơn thanh toán thành công, SELLER gọi:

```http
GET /api/v1/orders/{orderId}/confirmation
PUT /api/v1/orders/{orderId}/confirm
```

PUT không có body. Dùng `confirmationDeadline`, `confirmationCancelAt`, `sellerConfirmedAt`, `confirmationStatus`, `latePenaltyPoints`, `timeoutPenaltyPoints` để vẽ UI.
Mặc định local đã thiết lập mốc 24h/48h; mức phạt và mốc là cấu hình, có snapshot trên từng đơn. FE phải dùng response, không hardcode 2/3 điểm hoặc tự tính bằng giờ trên máy người dùng.
Khi job quá hạn được bật và chạy: quá hạn xác nhận lần đầu áp dụng phạt/thông báo; đến mốc hủy vẫn chưa xác nhận thì hủy, hoàn tiền và áp dụng phần phạt tiếp theo theo logic BE.
GET chỉ đọc; FE không gọi GET để kích hoạt phạt. Không có API công khai chỉnh đồng hồ hoặc deadline để test nhanh.

## 4. Luồng UI báo cáo bài đăng

Chi tiết bài → “Báo cáo bài đăng” → chọn lý do → nhập mô tả → đính kèm ảnh tùy chọn → gửi → xem kết quả trong “Báo cáo của tôi”.

| reasonCode | Nhãn UI |
|---|---|
| FRAUD | Nghi ngờ lừa đảo |
| MISLEADING_DESCRIPTION | Thông tin sai thực tế |
| DUPLICATE_LISTING | Đăng trùng bài |
| OTHER | Nội dung vi phạm khác |

Không hiển thị hàng giả/hàng nhái trong form báo cáo bài đăng mới. Dữ liệu lịch sử và report đơn hàng dùng bộ lý do riêng, không bị xóa theo thay đổi này.
`description` bắt buộc, tối đa 2000 ký tự. Chỉ báo cáo bài đã đăng, không báo cáo draft hoặc bài của chính mình.

## 5. Test tuần tự USER → STAFF

### Bước 1 — Lấy bài đăng

```http
GET /api/v1/posts?page=0&size=10
GET /api/v1/posts/{postId}
```

Lấy `postId`, `sellerId`. Muốn test chính sách hai lần, chọn hai bài khác nhau A/B của cùng seller chưa có gian lận trước đó. Kiểm tra uy tín ban đầu bằng API mục 3.1.

### Bước 2 — Upload bằng chứng tùy chọn

```http
POST /api/v1/reports/evidence
```

Dùng multipart/form-data, trường `file`, chọn JPEG/PNG/WEBP. Mỗi lần upload một file. Lấy `data.evidenceUrl`; không tự dùng URL Cloudinary công khai.

```json
{
  "evidenceId": "5c880bbe-f72c-4614-9be0-0371dc742824",
  "evidenceUrl": "/api/v1/reports/evidence/5c880bbe-f72c-4614-9be0-0371dc742824",
  "contentType": "image/png",
  "size": 12345
}
```

Đây là dữ liệu minh họa trong `data`. Ảnh riêng tư được đọc qua GET chính `evidenceUrl` có Bearer token. FE fetch blob rồi tạo object URL; không dùng thẳng URL riêng tư vào img khi request không có token. Hủy object URL khi component kết thúc.
Trước khi gửi report chỉ chủ ảnh đọc được; sau khi gắn report, reviewer có quyền cũng đọc được. Seller bị report không tự được xem ảnh riêng tư.

### Bước 3 — USER gửi report bài A

```http
POST /api/v1/reports/listings/{postId_A}
```

```json
{
  "requestId": "41bda890-6872-4e18-b3a7-aa50d55bf810",
  "reasonCode": "FRAUD",
  "description": "Người bán yêu cầu chuyển cọc riêng ngoài hệ thống để giữ hàng.",
  "evidenceUrls": []
}
```

Nếu đã upload, thay mảng rỗng bằng các `evidenceUrl` thực tế. Kết quả 201, `data.status=OPEN`, `data.targetType=LISTING`. Lưu `data.id` làm `reportId_A`.
Không gửi sellerId/reporterId từ FE: BE xác định từ bài đăng và token.

Báo cáo trùng bài dùng JSON sau; thay relatedPostId bằng bài công khai thực tế khác bài đang báo cáo:

```json
{
  "requestId": "e4bfdc9c-185f-4e3b-8440-561f45d04f0b",
  "reasonCode": "DUPLICATE_LISTING",
  "description": "Hai bài cùng rao bán một sản phẩm và sử dụng cùng bộ ảnh.",
  "evidenceUrls": [],
  "relatedPostId": "bb803e19-52cb-4cc5-9e97-47114d63e2d5"
}
```

relatedPostId là tùy chọn, chỉ dùng với DUPLICATE_LISTING. Không gửi trường này cho lý do khác.

### Bước 4 — STAFF xem và nhận xử lý

```http
GET /api/v1/notifications?type=REPORT&page=0&size=10
GET /api/v1/staff/reports?targetType=LISTING&status=OPEN&page=0&size=10
GET /api/v1/reports/{reportId_A}
POST /api/v1/staff/reports/{reportId_A}/start-review
```

POST không body. Kết quả `IN_REVIEW`, `reviewerId` là STAFF hiện tại. Chỉ người nhận review được resolve.
Thông báo API trả phân trang trực tiếp với `items`; report API bọc phân trang trong `data.items`.
UI STAFF có danh sách lọc trạng thái, chi tiết bài, mô tả, bằng chứng, bài đối chiếu và nút nhận/xử lý theo trạng thái.

### Bước 5 — STAFF xác nhận hoặc bác bỏ

```http
POST /api/v1/staff/reports/{reportId_A}/resolve
```

Xác nhận:

```json
{
  "decision": "CONFIRMED",
  "responsibleParty": "SELLER",
  "reason": "Đã kiểm tra bằng chứng và xác nhận người bán yêu cầu chuyển cọc ngoài hệ thống."
}
```

Hoặc bác bỏ, chọn một trong hai quyết định cho mỗi report:

```json
{
  "decision": "DISMISSED",
  "responsibleParty": "NONE",
  "reason": "Bằng chứng chưa đủ để xác nhận vi phạm."
}
```

Với report bài đăng xác nhận lỗi seller dùng SELLER; không mặc định chọn BUYER/CARRIER. Report bị bác bỏ không tăng số vi phạm.
Kết quả đã resolve không được đổi bằng cách gửi lại quyết định khác.

### Bước 6 — USER theo dõi

```http
GET /api/v1/reports/me?targetType=LISTING&page=0&size=10
GET /api/v1/reports/{reportId_A}
GET /api/v1/notifications?type=REPORT&page=0&size=10
```

Chuỗi trạng thái: OPEN → IN_REVIEW → CONFIRMED hoặc DISMISSED.
STAFF được thông báo khi có report mới; người gửi được thông báo khi tiếp nhận và có kết quả. Đây là thông báo trong ứng dụng và WebSocket, không tự gửi email/SMS cho các sự kiện report này.

## 6. Test gian lận lần một và tái phạm lần hai

Với seller ban đầu có seriousIncidents=0 và sellingRestricted=false:

| Tình huống | seriousIncidents | sellingRestricted | BAD do report bài đăng |
|---|---:|---|---|
| Chỉ gửi report | 0 | false | Không tăng |
| STAFF xác nhận FRAUD bài A | 1 | false | Không tăng |
| Nhiều người report FRAUD cùng bài A, đều được xác nhận | 1 | false | Không tăng |
| STAFF xác nhận FRAUD bài B khác, cùng seller | 2 | true | Không tăng |

Để test lần hai, USER gọi POST /api/v1/reports/listings/{postId_B}:

```json
{
  "requestId": "3ccff7e0-03c4-4aca-a7b5-4fb0c72f0436",
  "reasonCode": "FRAUD",
  "description": "Bài đăng khác này tiếp tục yêu cầu thanh toán riêng ngoài hệ thống.",
  "evidenceUrls": []
}
```

Lấy reportId_B, STAFF lặp lại start-review rồi resolve CONFIRMED/SELLER ở mục 5. Kiểm tra lại GET reputation.
Lần đầu warningCode=SERIOUS_VIOLATION_HISTORY. Từ lần hai warningCode=SELLING_RESTRICTED.
Các lý do MISLEADING_DESCRIPTION/DUPLICATE_LISTING/OTHER không tự tăng seriousIncidents.
Ngưỡng lấy từ REPUTATION_FRAUD_RESTRICTION_THRESHOLD=2 trong .env; môi trường deploy cũng phải khai báo biến này. Không có fallback mặc định; ngưỡng phải ít nhất 2.

Khi bị hạn chế: chặn xuất bản bài mới và tạo đơn mua mới từ seller; trả 403. Bài đang bán không tự ẩn, đơn đã tạo không tự hủy. FE hiển thị trạng thái hạn chế từ BE và xử lý 403, không chỉ ẩn nút.

## 7. ADMIN gỡ hạn chế

```http
POST /api/v1/admin/sellers/{sellerId}/reputation/lift-restriction
```

```json
{
  "reason": "Đã kiểm tra việc khắc phục vi phạm và cho phép người bán hoạt động trở lại."
}
```

sellingRestricted trở về false; số vi phạm và lịch sử không bị xóa. Vi phạm gian lận mới sau khi gỡ có thể làm seller bị hạn chế lại. Retry report cũ không tự chặn lại.
Seller bị chặn theo chính sách cũ vẫn cần ADMIN gỡ; cập nhật code không tự mở khóa dữ liệu cũ.

## 8. Phân biệt report đơn hàng

API cũ POST /api/v1/reports vẫn phục vụ report đơn hàng. Chỉ buyer của đơn được gửi; dùng orderId và không gửi postId cùng lúc.

```json
{
  "requestId": "94a55294-f649-4db2-9c1c-671f01bedc7e",
  "orderId": "d9bafdc8-7f2a-473d-8607-5d1f285e7458",
  "reasonCode": "MISLEADING_DESCRIPTION",
  "description": "Sản phẩm nhận được khác mô tả trong bài đăng.",
  "evidenceUrls": []
}
```

STAFF dùng cùng start-review/resolve. Lọc queue bằng targetType=ORDER. Nếu xác nhận lỗi SELLER, đơn được đánh giá BAD khi đủ trạng thái kết thúc; một đơn nhiều report vẫn chỉ một BAD. Report không tự thực hiện hoàn tiền/giải quyết trả hàng thay cho luồng tranh chấp.

## 9. Lỗi và checklist FE/test

| Kết quả | Cách xử lý |
|---|---|
| 400 | Kiểm tra reasonCode, mô tả bắt buộc, UUID, relatedPostId, evidenceUrl |
| 401 | Đăng nhập hoặc refresh token |
| 403 | Sai quyền, xem bằng chứng người khác, tự review, hoặc seller bị hạn chế |
| 404 | ID bài/report không tồn tại |
| 409 | Report cùng lý do đang chờ, đã có reviewer, chưa start-review, đổi nội dung requestId cũ hoặc bằng chứng đã gắn report khác |

- Report mới tạo requestId mới; retry giữ nguyên requestId và payload, tránh nhân đôi report/thông báo.
- Bằng chứng dùng lại cho report khác phải upload lại. Retry cùng report giữ nguyên URL.
- Không cho report chính bài của mình, draft hoặc dùng lý do COUNTERFEIT trên API báo cáo bài đăng mới.
- Không tính GOOD/BAD từ số report, không tự tính lại điểm hoặc ghi điểm từ FE.
- Không suy diễn từ 200 của API resolve thành seller luôn bị chặn: đọc lại reputation.
- Dùng page=0&size=10; không nhập sort nếu không cần.
- Test lại danh sách, chi tiết, notification, lịch sử uy tín sau mỗi lần resolve.
