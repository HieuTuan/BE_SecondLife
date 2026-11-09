# Hướng dẫn mobile — Kiểm định tại trung tâm SecondLife

Tài liệu dành cho app mobile, dùng API BE trực tiếp qua lớp HTTP/auth của app. Cấu hình base URL theo môi trường. Đường dẫn dưới đây là đường dẫn đầy đủ, kiểm tra prefix `/api/v1` nếu HTTP client đã thêm sẵn. Các thao tác dùng token của đúng Buyer, Seller hoặc Staff/Admin.

## 1. Luồng màn hình

1. **Chi tiết sản phẩm → Kiểm định tại trung tâm.** Chọn trung tâm, tải báo giá, chọn cách chia phí và xác nhận điều khoản kiểm định.
2. **Chi tiết yêu cầu.** Seller đồng ý, từ chối hoặc đề xuất lại. Buyer phản hồi nếu Seller đề xuất lại.
3. **Thanh toán phí kiểm định.** Bên phải trả nộp tiền từ ví. Khi đủ tiền, app hiển thị tiến trình gửi đến trung tâm.
4. **Kết quả kiểm định.** Nếu đạt, Buyer chọn mua tiếp hoặc từ chối. Nếu mua, lấy báo giá giao từ trung tâm và thanh toán tiền hàng cùng cước giao.
5. **Theo dõi và đối soát.** Khi cần trả hàng, theo dõi chuyến từ trung tâm về Seller. Xem khoản thực hoàn ở lịch sử ví.

## 2. Các API chính

| Thao tác | Method và path | Response |
| --- | --- | --- |
| Danh sách trung tâm | `GET /api/v1/inspection/centers` | `data` là danh sách |
| Báo giá kiểm định | `POST /api/v1/inspection/quote` | DTO báo giá trực tiếp |
| Tạo yêu cầu | `POST /api/v1/inspection/requests` | DTO đơn kiểm định trực tiếp |
| Seller phản hồi | `POST /api/v1/inspection/requests/{id}/respond` | DTO trực tiếp |
| Buyer phản hồi đề xuất lại | `POST /api/v1/inspection/requests/{id}/buyer-respond` | DTO trực tiếp |
| Nộp phí | `POST /api/v1/inspection/orders/{id}/pay-fee` | DTO trực tiếp, body rỗng |
| Chi tiết đơn | `GET /api/v1/inspection/orders/{id}` | DTO trực tiếp |
| Danh sách của Buyer hoặc Seller | `GET /api/v1/inspection/my-buyer-orders?page=0&size=20` hoặc `my-seller-orders` | Phân trang trong `data` |
| Báo giá giao Buyer | `POST /api/v1/shipping/quotes` | DTO báo giá trong `data` |
| Mua tiếp hoặc từ chối | `POST /api/v1/inspection/orders/{id}/confirm-purchase` hoặc `reject-purchase` | DTO trực tiếp |
| Chuyến của đơn kiểm định | `GET /api/v1/inspection-orders/{id}/shipments` | `data` là danh sách chuyến |

`{id}` trong các path kiểm định là ID **đơn kiểm định**, lấy từ `id` của response tạo yêu cầu. `linkedOrderId` là ID **đơn mua hàng** sau khi mua tiếp thành công.

## 3. Báo giá và tạo yêu cầu

Body báo giá:

```json
{
  "postId": "<postId>",
  "orderType": "AT_CENTER",
  "inspectionCenterId": "<centerId>"
}
```

Response mẫu rút gọn:

```json
{
  "baseInspectionFee": 50000,
  "shippingToCenterFee": 30000,
  "returnShippingReserveFee": 36000,
  "atCenterTotalFee": 116000,
  "atCenterSplitOptions": {
    "BUYER_PAYS": 116000,
    "SELLER_PAYS": 116000,
    "SPLIT_50_50": 58000
  },
  "inspectionConfiguration": {
    "inspectionCenterId": "<centerId>",
    "checklistTemplateId": "<checklistTemplateId>",
    "feeProposalId": "<feeProposalId>"
  },
  "shippingQuote": {
    "id": "<inspectionShippingQuoteId>",
    "expiresAt": "2026-11-08T08:30:00Z",
    "returnReserve": {
      "leg": "CENTER_TO_SELLER",
      "shippingFee": 36000,
      "providerQuotationId": null
    }
  }
}
```

Giá trị trong `atCenterSplitOptions` là số tiền của bên chịu phí theo phương án tương ứng. Ví dụ `SELLER_PAYS: 116000` nghĩa là **Seller** chịu 116.000đ. Dùng field từ BE để hiển thị, kể cả khi tỷ lệ Admin thay đổi. Field báo giá null nghĩa là chưa có dữ liệu tương ứng. `shippingQuote.returnReserve` là ước tính, nên ID báo giá hãng và phương tiện có thể null.

Body tạo yêu cầu, thay các ID bằng dữ liệu thực:

```json
{
  "postId": "<postId>",
  "orderType": "AT_CENTER",
  "feePayer": "BUYER_PAYS",
  "buyerConsentZin": true,
  "inspectionCenterId": "<centerId>",
  "expectedFeeProposalId": "<feeProposalId>",
  "expectedChecklistTemplateId": "<checklistTemplateId>",
  "inspectionShippingQuoteId": "<shippingQuote.id>"
}
```

App lấy hai trường `expected*` từ `inspectionConfiguration`. Gửi consent sau khi người dùng tick. Khi trung tâm đổi, quote hết hạn hoặc BE báo thay đổi báo giá, lấy báo giá mới và hiển thị lại số tiền trước khi gửi. Chỉ nhánh `AT_CENTER` sử dụng quy trình dự phòng này.

## 4. Hiển thị thao tác theo vai trò và trạng thái

| Trạng thái | Thao tác trên app |
| --- | --- |
| `WAITING_SELLER_APPROVAL` | Seller đồng ý với `{"action":"ACCEPT","sellerConsentZin":true}`, từ chối với `{"action":"REJECT","rejectionReason":"..."}`, hoặc đề xuất `{"action":"COUNTER","counterFeePayer":"SPLIT_50_50"}`. |
| `WAITING_BUYER_APPROVAL` | Buyer dùng `buyer-respond`, gửi `{"action":"ACCEPT","buyerConsentZin":true}` hoặc `{"action":"REJECT","note":"..."}`. |
| `WAITING_FEE_PAYMENT` | Hiện nút trả tiền nếu còn hạn `paymentDeadline`, phần phải trả > 0 và mốc đã trả của bên đó còn null. Buyer trả `buyerFeeShare + buyerCommitmentDeposit` (cọc có thể bằng 0). Seller trả `sellerFeeShare`. |
| `CONFIRMED_PENDING_DISPATCH` | Hiện đã đủ phí, chờ gửi hàng. |
| `PASSED` | Buyer có thể mua tiếp hoặc từ chối khi còn hạn `finalPaymentDeadline` và `finalPaymentPaidAt` còn null. |
| `FAILED` hoặc `CANCELLED` | Đọc danh sách chuyến để xác định tiến trình trả hàng nếu có. |
| `DELIVERING_TO_BUYER` | Hiện trạng thái giao tới Buyer và liên kết đến đơn mua qua `linkedOrderId`. |

Kiểm tra ID người đăng nhập khớp `buyerId` hoặc `sellerId` trước khi hiện nút. Sau mỗi thao tác, dùng response mới để cập nhật UI và tải lại khi app quay lại foreground hoặc người dùng kéo làm mới. Khi request đang chạy, khóa nút gửi. Sau timeout, đọc lại trạng thái trước khi thực hiện tiếp.

## 5. Thanh toán khi Buyer mua tiếp

Gửi `POST /api/v1/shipping/quotes`:

```json
{
  "postId": "<postId>",
  "deliveryAddress": {
    "name": "Nguyễn Văn An",
    "phone": "0912345678",
    "latitude": 10.87,
    "longitude": 106.6846
  }
}
```

Kiểm tra `data.leg` là `CENTER_TO_BUYER`. Hiển thị `productPrice`, `shippingFee`, `totalPayable` từ báo giá. Hiện dòng dự phòng hoàn riêng để tránh trừ tiền hai lần trên giao diện. Gửi xác nhận mua:

```json
{
  "requestId": "<uuid>",
  "shippingQuoteId": "<data.quoteId>",
  "paymentMethod": "WALLET"
}
```

Giữ UUID khi retry cùng một lần thanh toán. Với từ chối mua, gửi `{"reason":"Không tiếp tục mua sau khi xem kết quả kiểm định"}` đến endpoint `reject-purchase`. Sau khi mua thành công, refresh chi tiết đơn và ví. Với tài khoản Buyer trả 100%, khoản dự phòng 36.000đ được hoàn toàn bộ khi mua thành công.

## 6. Theo dõi chiều về và xử lý lỗi

- Lọc chặng `CENTER_TO_SELLER` trong danh sách chuyến. Hiển thị `quotedFee` là cước khi báo giá chuyến và `actualFee` là cước hãng đã ghi nhận. Cước thực tế 32.000đ với dự phòng 36.000đ tạo phần dư 4.000đ, chia theo tỷ lệ đã chốt.
- Xác nhận số tiền thực nhận từ lịch sử ví. `feeSettledAt` chỉ là mốc hạch toán phí kiểm định chung. Khi chuyến hoàn chưa có giá xác nhận, có thể hiển thị “Đang chờ đối soát”.
- Nếu app có màn Staff/Admin, dùng `POST /api/v1/inspection-orders/{id}/shipments` với `{"requestId":"<uuid>","leg":"CENTER_TO_SELLER"}` theo quyền hiện có. Có thể đồng bộ chuyến bằng `POST /api/v1/shipments/{shipmentId}/sync`. Khi cước vượt dự phòng, hiển thị thông báo chờ xử lý chênh lệch.
- Với `401`, xử lý theo luồng refresh token của app. Với `403`, báo giới hạn quyền. Với `409`, hiển thị thông báo từ BE và tải lại dữ liệu liên quan. Khi quote hết hạn, yêu cầu xác nhận lại giá mới.
- Nếu mobile có màn cấu hình Admin, dùng `GET` và `PUT /api/v1/admin/inspection/return-shipping-policy` với `{"markupPercentage":20}`. Cần role Admin và quyền `INSPECTION_FEE_MANAGE`.

## 7. Checklist test trên thiết bị

| Kịch bản | Kỳ vọng |
| --- | --- |
| Buyer chịu 100%, tổng 116.000đ | Buyer có nút trả 116.000đ, Seller có phần phí 0đ. |
| Seller chịu 100% | Seller có nút trả 116.000đ, Buyer có phần phí 0đ. Kiểm tra cọc Buyer nếu cấu hình có cọc. |
| Chia 50/50 | Mỗi bên 58.000đ, chỉ chuyển sang đủ phí sau khi cả hai đã trả. |
| Đạt và mua | Với tiền hàng 500.000đ, ship 40.000đ, hiển thị tổng mua 540.000đ và khoản hoàn riêng. |
| Trả hàng, cước 32.000đ | Tổng hoàn dư 4.000đ, với 50/50 mỗi bên nhận 2.000đ. |
| Mất mạng lúc thanh toán | Mở lại chi tiết và đối chiếu trạng thái trước khi retry. |
| Đổi trung tâm hoặc quote hết hạn | Xóa lựa chọn báo giá cũ, tải lại và xác nhận giá mới. |

