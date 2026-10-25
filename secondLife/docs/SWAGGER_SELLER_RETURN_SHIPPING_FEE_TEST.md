# Test Swagger: seller chịu phí vận chuyển hoàn hàng

Tài liệu kiểm tra chính sách thu phí hoàn hàng từ seller khi STAFF xác nhận lỗi seller. Luồng tạo đơn, khiếu nại, duyệt và tạo chuyến đầy đủ: [SWAGGER_STAFF_RETURN_REFUND_TEST.md](SWAGGER_STAFF_RETURN_REFUND_TEST.md). Tài liệu này là kịch bản và kết quả mong đợi; không phải báo cáo các ca đã chạy đạt.

## 1. Chuẩn bị tài khoản và đơn mới

Mở Swagger của môi trường test, đăng nhập tại `POST /api/auth/login`, dùng `data.accessToken` trong **Authorize** và đổi token theo từng vai trò:

- BUYER: buyer của đơn.
- SELLER: seller của đơn; ghi lại số dư khả dụng trước khi hoàn tiền.
- STAFF: `ROLE_STAFF` + `STAFF_FRAUD_REVIEW` + `STAFF_DISPUTE_REVIEW`, không phải buyer/seller của đơn. ADMIN dùng `ROLE_ADMIN` + `ADMIN_RBAC_MANAGE`.
- Một SELLER khác và STAFF thiếu `STAFF_DISPUTE_REVIEW` để kiểm tra quyền đọc phí.

Dùng đơn test mới, đã thanh toán và được hãng xác nhận `DELIVERED`, escrow còn `HELD`/`FROZEN`, buyer chưa xác nhận nhận hàng. Buyer phải mở report trước `shippingDeliveredAt + 48 giờ`. Mỗi ca tài chính dùng đơn/report/vận đơn hoàn mới; đơn đã hoàn tiền trước khi tính năng có hiệu lực không bị thu phí hồi tố khi replay.

Không dùng `PUT /api/v1/orders/{orderId}/delivered` để giả lập carrier giao hàng: thao tác này là buyer xác nhận và có thể giải ngân. Dùng callback hợp lệ hoặc `POST /api/v1/shipments/{shipmentId}/sync` sau khi carrier/Sandbox thực sự cập nhật trạng thái.

Ghi lại:

| Ký hiệu | Dữ liệu |
| --- | --- |
| `P` | `Order.finalPrice`: toàn bộ tiền sản phẩm được hoàn |
| `B` | `balance` ví seller ngay trước refund, không âm |
| `U` | `balance` ví buyer ngay trước refund |
| `D` | Tổng nợ hiện tại của seller trước ca test |
| `F` | `actualFee` của đúng chuyến hoàn `BUYER_TO_SELLER` sau sync |

`GET /api/v1/wallets/me` trả DTO trực tiếp: đọc `balance`, không đọc `data.balance`. Không có giao dịch ví khác trong lúc đối chiếu. Phí thực tế tùy chuyến và carrier; không ép `actualFee=30000` hoặc sửa phí bằng request FE để khớp ví dụ bên dưới. `quotedFee` không phải nguồn tính khoản thu.

## 2. Buyer mở report, STAFF duyệt lỗi seller và tạo chuyến hoàn

Dùng token BUYER, `POST /api/v1/reports`:

```json
{
  "requestId": "11111111-1111-4111-8111-111111111111",
  "orderId": "ORDER_ID_THAT",
  "reasonCode": "MISLEADING_DESCRIPTION",
  "description": "Sản phẩm nhận được khác mô tả; đề nghị xem xét hoàn hàng và hoàn tiền.",
  "evidenceUrls": []
}
```

Thay các ID bằng UUID thật. Dùng UUID `requestId` mới cho ca độc lập; giữ nguyên ID/body khi retry. `201`: lưu `data.id` là `reportId`. Khi cần kiểm tra bằng chứng, dùng upload riêng tư theo tài liệu liên kết ở đầu, thay vì URL CDN tự nhập.

Đổi token STAFF, gọi `POST /api/v1/staff/reports/{reportId}/start-review` không body, rồi `POST /api/v1/staff/reports/{reportId}/resolve`:

```json
{
  "decision": "CONFIRMED",
  "responsibleParty": "SELLER",
  "reason": "Đã đối chiếu bằng chứng, xác nhận lỗi seller và chấp thuận hoàn hàng.",
  "resolutionAction": "RETURN_AND_REFUND"
}
```

`200`: report là `CONFIRMED/RETURN_AND_REFUND`, có `returnApprovedAt`. Bước duyệt chưa trừ ví seller và chưa hoàn tiền buyer.

`POST /api/v1/orders/{orderId}/shipments`:

```json
{
  "requestId": "22222222-2222-4222-8222-222222222222",
  "leg": "BUYER_TO_SELLER",
  "reason": "Tạo chuyến hoàn theo quyết định đã duyệt.",
  "reportId": "REPORT_ID_THAT"
}
```

Lưu `data.shipmentId` là `returnShipmentId`. `GET /api/v1/reports/{reportId}` phải có `data.returnShipmentId` trùng ID này. Không chọn outbound `SELLER_TO_BUYER`. HTTP `200` với `CREATION_UNCERTAIN` chưa chứng minh booking thành công; theo luồng đối soát hiện có, không tạo chuyến mới để né trạng thái chưa rõ.

Chờ hàng thực sự về seller, gọi `POST /api/v1/shipments/{returnShipmentId}/sync` không body. Đọc `GET /api/v1/orders/{orderId}/shipments`: đúng vận đơn hoàn phải `DELIVERED`. Lưu `actualFee` nếu đã có; `0` là phí hợp lệ, khác với `null`.

## 3. STAFF hoàn tiền và thu phí một lần

Trước khi gọi, đọc lại số dư seller/buyer và tổng nợ seller. Dùng token STAFF, `POST /api/v1/shipments/{returnShipmentId}/refund`:

```json
{
  "reason": "Carrier đã xác nhận giao hàng hoàn về seller; duyệt hoàn toàn bộ tiền hàng."
}
```

Không gửi `amount`, `feeAmount`, `collectedAmount`, `debtAmount` hoặc số tiền do FE tự tính. Body chỉ cần `reason`; BE dùng trách nhiệm của report đã duyệt. Nếu caller cũ có gửi `responsibleParty`, giá trị phải khớp quyết định.

`200` vẫn trả `ShipmentResponse`. Tải lại report: `refundedAt/refundedBy/refundAmount` đã có, `refundAmount=P`; report vẫn `CONFIRMED`, không có enum report `REFUNDED`. Order trở thành `CANCELLED/REFUNDED`, seller không nhận payout, không có commission settlement.

Khi đã có `F=actualFee`, BE chốt:

```text
collectedAmount = min(B, F)
debtAmount = F - collectedAmount
sellerBalanceAfter = B - collectedAmount
buyerBalanceAfter = U + P
sellerOutstandingDebtAfter = D + debtAmount
```

Buyer nhận đủ `finalPrice`, không trừ phí hoàn hàng vào khoản hoàn này. Phí outbound đã phát sinh vẫn theo chính sách hiện có, không được cộng vào khoản hoàn sản phẩm; khoản thu mới chỉ là phí chuyến hoàn.

## 4. Đọc phí và tổng nợ bằng đúng tài khoản

STAFF có `STAFF_DISPUTE_REVIEW`, ADMIN có `ADMIN_RBAC_MANAGE`, hoặc SELLER sở hữu khoản phí gọi:

```http
GET /api/v1/shipments/{returnShipmentId}/return-shipping-fee
```

Ví dụ `F=30000`, `B=10000`; nội dung `data`:

```json
{
  "shipmentId": "33333333-3333-4333-8333-333333333333",
  "orderId": "44444444-4444-4444-8444-444444444444",
  "reportId": "55555555-5555-4555-8555-555555555555",
  "sellerId": "66666666-6666-4666-8666-666666666666",
  "status": "OUTSTANDING",
  "feeAmount": 30000,
  "collectedAmount": 10000,
  "debtAmount": 20000,
  "createdAt": "2026-10-25T03:00:00Z",
  "assessedAt": "2026-10-25T03:00:00Z",
  "assessedBy": "77777777-7777-4777-8777-777777777777"
}
```

Đây là các UUID/thời gian minh họa, không phải dữ liệu nhập để thu phí. Số tiền thực tế phải khớp `F` và số dư được BE khóa lúc xử lý.

Đổi token SELLER chủ khoản phí:

```http
GET /api/v1/seller/return-shipping-fees?page=0&size=20&outstandingOnly=true
GET /api/v1/seller/return-shipping-fees/debt
```

Danh sách dùng `data.items`; `page` bắt đầu từ `0`. Bỏ `outstandingOnly` hoặc dùng `false` để xem cả các khoản phí đã chốt không còn nợ. Endpoint nợ trả `data`:

```json
{
  "sellerId": "66666666-6666-4666-8666-666666666666",
  "outstandingDebt": 20000
}
```

Tổng nợ cộng tất cả khoản nợ của seller, không chỉ đơn đang kiểm tra. Nếu seller đã có nợ `D`, đối chiếu mức tăng `debtAmount`, không mặc định tổng nợ bằng khoản mới.

Không có tham số `sellerId` để tra tài khoản khác ở hai endpoint seller. BUYER, seller khác, hoặc STAFF thiếu quyền tranh chấp không được đọc chi tiết phí này (`403` khi đã có khoản phí). Không đưa nợ seller vào DTO report/shipment dùng chung cho buyer.

## 5. Ma trận số dư và phí

Các con số `30000` chỉ để minh họa. Với dữ liệu thật, chọn ca theo quan hệ `B`/`F` và dùng số tiền carrier trả về.

| Ca | Ví dụ `F` | Ví dụ `B` | `collectedAmount` | `debtAmount` | Ví seller còn | Trạng thái |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Đủ số dư | 30000 | 30000 | 30000 | 0 | 0 | `PAID` |
| Có một phần | 30000 | 10000 | 10000 | 20000 | 0 | `OUTSTANDING` |
| Không có số dư | 30000 | 0 | 0 | 30000 | 0 | `OUTSTANDING` |
| Phí thực tế miễn phí | 0 | 10000 | 0 | 0 | 10000 | `PAID` |
| Chưa có phí thực tế | `null` | Bất kỳ | Chưa chốt | Chưa chốt | Không đổi | `PENDING_FEE` |

Chuẩn bị số dư bằng tài khoản/fixture của môi trường test; Swagger không có chức năng ghi đè số dư hoặc phí carrier. Ca phí `0`, thiếu phí, callback đổi phí và lỗi giữa transaction cần provider/fixture test kiểm soát được nếu Sandbox không tạo được tình huống đó.

## 6. Chưa có actualFee: hoàn buyer trước, xử lý phí sau

Nếu lần refund đầu thấy `actualFee=null`, buyer vẫn được hoàn đủ `P`, audit hoàn tiền vẫn được ghi. Khoản phí lưu `status=PENDING_FEE`; chưa trừ ví seller hoặc tăng nợ theo giá quote. Các field tiền/thời điểm đánh giá chưa chốt có thể `null` hoặc bị lược bỏ theo cấu hình JSON; không hiển thị là phí miễn phí.

Khi carrier đã có phí:

1. STAFF gọi `POST /api/v1/shipments/{returnShipmentId}/sync` không body.
2. Đọc lại vận đơn, xác nhận `actualFee` đã có.
3. STAFF gửi lại chính `POST /api/v1/shipments/{returnShipmentId}/refund` với body `reason` như phần 3.
4. Đọc chi tiết phí: trạng thái chuyển sang `PAID` hoặc `OUTSTANDING`, `assessedAt/assessedBy` được chốt.

Replay này chỉ đánh giá khoản `PENDING_FEE` đã tồn tại; không hoàn buyer lần hai và không sửa audit hoàn tiền trước đó. Sync/callback riêng lẻ chỉ cập nhật dữ liệu carrier, không tự thu phí. Chưa có `actualFee` thì replay vẫn chờ phí. Không có endpoint “collect” riêng.

## 7. Retry, quyền và đối chiếu ledger

| Ca kiểm tra | Kết quả mong đợi |
| --- | --- |
| Refund trước khi chuyến hoàn `DELIVERED` | `409`; không hoàn buyer, không thu phí |
| Report chưa duyệt, sai order hoặc sai selected return | Bị từ chối theo luồng hiện có; không thu phí |
| `responsibleParty=BUYER/CARRIER` hoặc action `NONE` | Không áp chính sách phí lỗi seller này |
| Retry sau khi phí đã `PAID/OUTSTANDING` | Không trừ ví, tăng nợ hoặc ghi ledger lần hai |
| Hai STAFF đồng thời refund cùng chuyến | Một khoản hoàn buyer và một đánh giá phí; số dư/nợ đúng một lần |
| Carrier đổi `actualFee` sau khi đã chốt | Các snapshot `feeAmount/collectedAmount/debtAmount/assessedAt/assessedBy` giữ nguyên; replay không thu thêm |
| Lỗi sau khi thu phí nhưng trước khi hoàn tất transaction đầu | Buyer refund, thu ví seller, nợ, ledger và audit cùng rollback; retry xử lý một lần |
| Replay đơn đã hoàn đầy đủ trước tính năng, không có assessment | Không tạo khoản phí hồi tố |
| BUYER đọc chi tiết phí của đơn mình | `403`; quyền xem shipment không mở quyền xem nợ seller |
| SELLER khác hoặc STAFF thiếu `STAFF_DISPUTE_REVIEW` đọc chi tiết | `403` |
| Seller nạp tiền/nhận doanh thu sau khi phát sinh nợ | Không tự thu hồi nợ; nợ đã ghi giữ nguyên |

Đối chiếu ví bằng token từng chủ ví:

```http
GET /api/v1/wallets/me
GET /api/v1/wallets/me/transactions?page=0&size=20
```

Wallet transaction trả trang trực tiếp ở `items`. Buyer có một giao dịch `REFUND`, `amount=P`, `referenceId=orderId`. Khi có tiền thực thu, seller có giao dịch `RETURN_SHIPPING_FEE`, `amount=-collectedAmount`, `referenceId=returnShipmentId`. Nợ không phải số dư ví âm và không phải một khoản tiền mặt đã thu.

ADMIN có quyền xem ví hệ thống có thể đối chiếu `GET /api/v1/admin/system-wallet` và `GET /api/v1/admin/system-wallet/transactions?page=0&size=20`: sự kiện `RETURN_SHIPPING_FEE` tham chiếu `orderId`, `shippingDelta=collectedAmount`; không cộng `debtAmount` vào tiền khả dụng. So sánh mức tăng với baseline vì ví hệ thống dùng chung cho nhiều đơn.

## 8. Phạm vi đối soát

Tính năng ghi nhận nợ seller; chưa có tự động thu hồi khi nạp tiền/nhận doanh thu và chưa có API thanh toán nợ thủ công. Thu phí đã chốt không tự đổi khi callback phí đến muộn.

Thanh toán và đối soát thực tế với hãng vận chuyển được xử lý ngoài tính năng này. Khoản thu cộng vào `shippingBalance` là ghi sổ nội bộ; không có API mới để tạo thêm một khoản thu bên carrier.
