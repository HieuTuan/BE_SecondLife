# Luồng BE: tranh chấp đơn hàng, hoàn hàng và hoàn tiền

Hướng dẫn triển khai màn hình STAFF, request/response và ma trận nút: [FE_STAFF_RETURN_REFUND_GUIDE.md](FE_STAFF_RETURN_REFUND_GUIDE.md).

Áp dụng cho tranh chấp sau khi hãng vận chuyển xác nhận giao hàng. Các endpoint bên dưới dùng Bearer access token và wrapper `ApiResponse`; dữ liệu thành công nằm trong `data`. Báo cáo vi phạm bài đăng vẫn dùng luồng listing hiện có.

## 1. Điều kiện và trạng thái

Chỉ buyer của đơn được tạo báo cáo có `orderId`. Đơn phải có `status=DELIVERED`, `shippingDeliveredAt` hợp lệ và escrow còn `HELD` hoặc `FROZEN`. Thời điểm nhận yêu cầu phải **nhỏ hơn** `shippingDeliveredAt + 48 giờ`; tại đúng hạn cũng bị từ chối. Cửa sổ khiếu nại cố định là 48 giờ, dùng đồng hồ server `orderReleaseClock`; không mở rộng hạn khiếu nại khi thay đổi cấu hình tác vụ tự giải ngân. Tác vụ tự giải ngân hiện mặc định 48 giờ; BE là nguồn quyết định hạn.

Đơn chưa giao, đã xác nhận hoàn tất/giải ngân hoặc đã hoàn tiền không nhận tranh chấp mới. Tạo báo cáo thành công chuyển escrow sang `FROZEN` trong cùng transaction với lưu báo cáo và gắn bằng chứng. Khi bị giữ tiền, cả buyer xác nhận nhận hàng và tác vụ tự giải ngân đều không thanh toán cho seller.

| Sự kiện | Report | Order / escrow |
| --- | --- | --- |
| Buyer tạo tranh chấp hợp lệ | `OPEN`, `resolutionAction=NONE` | `DELIVERED / FROZEN` |
| Nhân viên tiếp nhận | `IN_REVIEW`, có `reviewerId` | Tiếp tục giữ tiền |
| Bác báo cáo hoặc xác nhận không hoàn hàng | `DISMISSED` hoặc `CONFIRMED`, action `NONE` | Về `HELD` khi không còn hold khác |
| Duyệt hoàn hàng | `CONFIRMED`, action `RETURN_AND_REFUND` | Tiếp tục `FROZEN` |
| Tạo vận đơn hoàn | Có `returnShipmentId` | Tiếp tục `FROZEN` |
| Hãng vận chuyển giao hàng hoàn cho seller | Vận đơn hoàn `DELIVERED` | Chờ nhân viên hoàn tiền |
| Nhân viên hoàn tiền thành công | Có audit hoàn tiền | `CANCELLED / REFUNDED` |

Cờ `orders.shipping_hold` giữ riêng lý do đóng băng do sự cố vận chuyển/POD. Migration giữ cờ này cho các đơn `FROZEN` cũ vì chưa xác định được lý do giữ tiền lịch sử. Giải quyết một báo cáo không tự bỏ hold của báo cáo khác. Báo cáo `OPEN`/`IN_REVIEW`, quyết định hoàn hàng chưa hoàn tiền, vận đơn hoàn còn hiệu lực hoặc sự cố vận chuyển vẫn giữ tiền. Khi hold cuối được gỡ, đơn trở về `HELD`; lần buyer xác nhận hoặc lượt tự giải ngân đến hạn mới thực hiện settlement.

## 2. Buyer tạo và theo dõi tranh chấp

Có thể tải ảnh qua `POST /api/v1/reports/evidence` (`multipart/form-data`, field `file`) trước khi tạo báo cáo. Dùng `evidenceUrl` riêng tư do endpoint này trả về; ảnh phải thuộc người tạo và không được dùng URL CDN công khai. `evidenceUrls` có thể bỏ qua hoặc là mảng rỗng.

`POST /api/v1/reports` trả `201 Created`:

```json
{
  "requestId": "11111111-1111-4111-8111-111111111111",
  "orderId": "22222222-2222-4222-8222-222222222222",
  "reasonCode": "MISLEADING_DESCRIPTION",
  "description": "Sản phẩm nhận được khác mô tả; đề nghị kiểm tra và hoàn hàng.",
  "evidenceUrls": []
}
```

Cung cấp đúng một trong `orderId` hoặc `postId`. Với tranh chấp mua hàng dùng `orderId`; `DUPLICATE_LISTING` và `relatedPostId` chỉ dành cho listing. `description` bắt buộc, tối đa 2000 ký tự.

Giữ nguyên `requestId` và nội dung khi retry sau timeout. Yêu cầu giống hệt trả lại cùng báo cáo, kể cả khi hạn khiếu nại đã qua. Đổi nội dung với `requestId` đã dùng trả `409`. Một báo cáo cùng target/reason còn chờ review cũng chặn tạo thêm bằng `requestId` mới.

Các API đọc:

- `GET /api/v1/reports/me?targetType=ORDER&page=0&size=20`: báo cáo của chính buyer.
- `GET /api/v1/reports/{reportId}`: chi tiết báo cáo và audit hoàn hàng/hoàn tiền.
- `GET /api/v1/orders/{orderId}/shipments`: danh sách vận đơn của đơn, theo quyền hiện có.

Không tự đánh dấu đã hoàn tiền trên FE khi report vừa `CONFIRMED`; cần đọc `resolutionAction` và `refundedAt`.

## 3. Nhân viên nhận và ra quyết định

Queue và review giữ phân quyền hiện có: `STAFF` cần `STAFF_FRAUD_REVIEW`; `ADMIN` cần `ADMIN_RBAC_MANAGE`. Quyết định `RETURN_AND_REFUND` yêu cầu thêm `STAFF_DISPUTE_REVIEW` đối với nhân viên. Tạo vận đơn hoàn và hoàn tiền yêu cầu `STAFF_DISPUTE_REVIEW` đối với nhân viên; quyền quản trị vẫn dùng `ADMIN_RBAC_MANAGE`.

1. `GET /api/v1/staff/reports?targetType=ORDER&status=OPEN&page=0&size=20`.
2. `POST /api/v1/staff/reports/{reportId}/start-review` không cần body; trả `200`.
3. `POST /api/v1/staff/reports/{reportId}/resolve`; chỉ reviewer đã nhận được giải quyết, và không được review vụ việc liên quan đến chính mình.

Body duyệt hoàn hàng:

```json
{
  "decision": "CONFIRMED",
  "responsibleParty": "SELLER",
  "reason": "Đã đối chiếu bằng chứng và chấp thuận hoàn hàng.",
  "resolutionAction": "RETURN_AND_REFUND"
}
```

`responsibleParty` của quyết định xác nhận phải là `SELLER`, `BUYER` hoặc `CARRIER`. Không dùng `NONE` để duyệt hoàn hàng. Một đơn chỉ có một quyết định hoàn hàng được duyệt; quyết định phải gắn báo cáo được mở trong thời hạn hợp lệ.

Body bác báo cáo:

```json
{
  "decision": "DISMISSED",
  "responsibleParty": "NONE",
  "reason": "Bằng chứng không xác nhận nội dung khiếu nại.",
  "resolutionAction": "NONE"
}
```

`resolutionAction` bị bỏ qua hoặc `null` mặc định thành `NONE`, giữ tương thích request review cũ. `CONFIRMED` với action `NONE` chỉ xác nhận kết quả review; không duyệt hoàn hàng. Quyết định đã giải quyết chỉ được retry với cùng decision, party, reason và action; không đổi bằng cách gửi lại request.

## 4. Tạo vận đơn hoàn đã được duyệt

`POST /api/v1/orders/{orderId}/shipments` trả `200`:

```json
{
  "requestId": "33333333-3333-4333-8333-333333333333",
  "leg": "BUYER_TO_SELLER",
  "reason": "Tạo vận đơn hoàn theo quyết định đã duyệt.",
  "reportId": "44444444-4444-4444-8444-444444444444"
}
```

`reportId` bắt buộc cho `BUYER_TO_SELLER` và phải là báo cáo của chính đơn này, đã `CONFIRMED` với action `RETURN_AND_REFUND`. BE dùng địa chỉ giao hàng và địa chỉ nhận hàng của seller từ đơn/quote đã thanh toán để tạo chiều hoàn. Response vận đơn giữ contract hiện có; đọc report để lấy liên kết `returnShipmentId`.

Giữ `requestId`, `reportId` và nội dung khi retry; không tạo key mới để tránh một vận đơn đang hoạt động hoặc trạng thái tạo chưa rõ. Nếu carrier booking chưa rõ kết quả, dùng luồng sync/reconcile hiện có. Chỉ có thể thay vận đơn được chọn sau khi vận đơn trước đã được hủy; vận đơn cũ không còn quyền hoàn tiền thay cho vận đơn đang được chọn.

Việc carrier nhận booking chưa đồng nghĩa hàng đã được hoàn về seller. Chờ callback hợp lệ hoặc sync hãng vận chuyển cập nhật vận đơn `DELIVERED`.

## 5. Hoàn tiền sau khi hàng hoàn được giao

`POST /api/v1/shipments/{returnShipmentId}/refund` trả `200`:

```json
{
  "reason": "Hãng vận chuyển đã xác nhận giao lại hàng cho seller.",
  "responsibleParty": "SELLER"
}
```

Đối với buyer return, `responsibleParty` có thể bỏ qua để dùng quyết định đã duyệt; nếu gửi thì phải khớp quyết định đó. BE kiểm tra vận đơn là `BUYER_TO_SELLER`, đã `DELIVERED`, liên kết với đúng báo cáo đã duyệt và là `returnShipmentId` hiện được chọn. Escrow phải đang `FROZEN` trước lần hoàn tiền đầu.

Số tiền do BE tính bằng toàn bộ `finalPrice` của sản phẩm. API không nhận số tiền hoàn một phần; phí vận chuyển đã phát sinh không nằm trong khoản hoàn sản phẩm này. Transaction bao gồm trừ số dư tiền giữ của hệ thống, cộng ví buyer, ledger, trạng thái đơn và audit báo cáo. Seller không nhận payout và hệ thống không ghi commission settlement cho giao dịch hoàn tiền.

Retry cùng vận đơn/quyết định trả kết quả đã hoàn, không cộng tiền hoặc ghi ledger/audit lần hai. Khi lỗi giữa chừng, toàn bộ thay đổi tiền và audit rollback để có thể retry. Bản ghi seller fault của quyết định hoàn hàng được hoàn tất theo kết quả hoàn tiền.

Luồng cũ vẫn hỗ trợ vận đơn outbound đã `RETURNED` vật lý và tiền đang `FROZEN`; không dùng luồng này để bỏ qua phê duyệt cho vận đơn `BUYER_TO_SELLER`.

## 6. Field bổ sung và lỗi API

`ReportResponse` bổ sung:

| Field | Ý nghĩa |
| --- | --- |
| `resolutionAction` | `NONE` hoặc `RETURN_AND_REFUND` |
| `returnApprovedAt` | Thời điểm quyết định hoàn hàng được duyệt |
| `returnShipmentId` | Vận đơn hoàn hiện được chọn cho quyết định |
| `refundedAt` | Thời điểm hoàn tiền thành công; `null` khi chưa hoàn |
| `refundedBy` | UUID nhân viên xử lý hoàn tiền |
| `refundAmount` | Số tiền sản phẩm thực tế đã hoàn |

Những field audit chưa phát sinh có thể `null` hoặc bị lược bỏ theo cấu hình JSON chung. Request tạo vận đơn bổ sung `reportId`; request resolve bổ sung `resolutionAction`. Endpoint cũ được giữ nguyên.

| HTTP | Trường hợp điển hình |
| --- | --- |
| `400` | Body sai, thiếu `reportId` khi tạo buyer return, dùng `NONE` cho quyết định xác nhận hoặc dùng action hoàn hàng cho `DISMISSED`/listing |
| `401` | Chưa xác thực |
| `403` | Không phải buyer, không phải reviewer được giao hoặc thiếu quyền nhân viên |
| `404` | Order/report/shipment không tồn tại |
| `409` | Hết hạn/chưa giao/đã đóng đơn, report chưa duyệt hoặc sai đơn, đang có vận đơn hoàn, hàng hoàn chưa giao, chưa giữ tiền, party không khớp hoặc retry thay đổi nội dung |

BE tạo thông báo in-app cho nhân viên/buyer/seller khi có báo cáo, tiếp nhận, quyết định và hoàn tiền theo các bước liên quan. FE có thể dùng Notification API và kênh notification hiện có.

## 7. Kiểm thử PostgreSQL

`OrderDisputeIntegrationTest` dùng PostgreSQL thật qua `GhnTestDatabase`: Testcontainers khi có Docker, hoặc biến `ORDER_DISPUTE_TEST_JDBC_URL`. URL ngoài phải là loopback và database riêng có tên `secondlife_ghn_test_*`; dùng `GHN_TEST_DB_USERNAME`/`GHN_TEST_DB_PASSWORD` nếu cần. Không trỏ fixture ghi dữ liệu vào database ứng dụng.

```powershell
.\mvnw.cmd '-Dtest=OrderDisputeIntegrationTest,ListingReportIntegrationTest,LalamoveShippingIntegrationTest' test
```

Suite bao phủ biên hạn 48 giờ, replay sau hạn, freeze/rollback, nhiều hold, quyền reviewer, liên kết return/report, delivery trước refund, retry, rollback ledger/audit và request đồng thời. Carrier, mail và các tích hợp bên ngoài được mock; repository, transaction, tiền và migration dùng database thật. Không có PostgreSQL/Docker thì lớp test được skip; kết quả skip không chứng minh workflow đạt.