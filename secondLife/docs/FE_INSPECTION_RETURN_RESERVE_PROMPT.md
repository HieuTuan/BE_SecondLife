# Prompt cho AI sửa FE web — Phí kiểm định tại trung tâm

Copy phần dưới đây vào AI đang mở repo FE SecondLife.

---

## Nhiệm vụ

Sửa FE React TypeScript của SecondLife cho luồng mua hàng kèm kiểm định `AT_CENTER`. Tái sử dụng API client, auth, UI và điều hướng hiện có. Bắt đầu từ `src/services/inspectorService.ts`, `src/components/modals/CheckoutModal.tsx`, `src/pages/BuyerInspectionDecisionView.tsx`, `src/pages/EscrowOrdersView.tsx` và mục `inspection-governance` trong Admin Dashboard. Tìm thêm các caller của `confirmPurchase` để cập nhật đồng bộ.

## 1. Buyer lấy báo giá và tạo yêu cầu

- `GET /api/v1/inspection/centers` trả danh sách trong `data`. Cho Buyer chọn trung tâm trước khi lấy báo giá.
- `POST /api/v1/inspection/quote` trả DTO trực tiếp. Gửi `{"postId":"<postId>","orderType":"AT_CENTER","inspectionCenterId":"<centerId>"}`.
- Hiển thị bốn dòng lấy từ BE: **Phí kiểm định** (`baseInspectionFee`), **Cước Seller → Trung tâm** (`shippingToCenterFee`), **Tạm giữ dự phòng cước trả về Seller** (`returnShippingReserveFee`) và **Tổng phí thu trước** (`atCenterTotalFee`). Ghi rõ dự phòng sẽ được hoàn nếu Buyer mua hoặc hoàn phần dư sau khi đối soát cước trả hàng.
- Đọc `shippingQuote.id`, `shippingQuote.expiresAt`, `inspectionConfiguration.feeProposalId` và `inspectionConfiguration.checklistTemplateId`. Với báo giá mới, `shippingQuote.returnReserve.providerQuotationId` có thể null vì đây là khoản ước tính. Các trường phương tiện ở chặng dự phòng cũng có thể null.
- Khi đổi trung tâm hoặc báo giá hết hạn, bỏ quote ID cũ và lấy báo giá mới. Disable nút gửi khi thiếu dữ liệu giá hoặc thiếu quote hợp lệ. Với `409` do báo giá đổi, tải lại và cho Buyer xác nhận số tiền mới.
- `POST /api/v1/inspection/requests` trả DTO trực tiếp. Body:

```json
{
  "postId": "<postId>",
  "orderType": "AT_CENTER",
  "inspectionCenterId": "<centerId>",
  "feePayer": "BUYER_PAYS",
  "buyerConsentZin": true,
  "expectedFeeProposalId": "<inspectionConfiguration.feeProposalId>",
  "expectedChecklistTemplateId": "<inspectionConfiguration.checklistTemplateId>",
  "inspectionShippingQuoteId": "<shippingQuote.id>"
}
```

Chỉ gửi consent khi Buyer đã tick. Giữ `buyerDeliveryAddress` và `negotiationId` khi luồng hiện tại có các giá trị này. Hiển thị ba lựa chọn `BUYER_PAYS`, `SELLER_PAYS`, `SPLIT_50_50` từ dữ liệu báo giá BE. Sau khi tạo đơn, dùng `buyerFeeShare` và `sellerFeeShare` đã lưu.

## 2. Phản hồi đề xuất và thanh toán phí

| Trạng thái | Vai trò và hành động |
| --- | --- |
| `WAITING_SELLER_APPROVAL` | Seller gọi `POST /api/v1/inspection/requests/{id}/respond`. Ví dụ đồng ý `{"action":"ACCEPT","sellerConsentZin":true}`, từ chối `{"action":"REJECT","rejectionReason":"..."}`, đề xuất lại `{"action":"COUNTER","counterFeePayer":"SPLIT_50_50"}`. |
| `WAITING_BUYER_APPROVAL` | Buyer gọi `POST /api/v1/inspection/requests/{id}/buyer-respond` với `{"action":"ACCEPT","buyerConsentZin":true}` hoặc `{"action":"REJECT","note":"..."}`. |
| `WAITING_FEE_PAYMENT` | Chỉ hiện nút cho bên còn phải trả và còn hạn `paymentDeadline`. Buyer phải trả `buyerFeeShare + buyerCommitmentDeposit`, Seller trả `sellerFeeShare`. Kiểm tra `buyerPaidAt` hoặc `sellerPaidAt` theo vai trò. Gọi `POST /api/v1/inspection/orders/{id}/pay-fee` với body rỗng. |
| `CONFIRMED_PENDING_DISPATCH` | Hiện đã thu đủ phí và tiến trình gửi hàng đến trung tâm. |

Các API phản hồi và thanh toán trả DTO trực tiếp. Tải lại `GET /api/v1/inspection/orders/{id}` và số dư ví sau thao tác. Hiển thị cọc cam kết riêng nếu lớn hơn 0. Nếu phần phải trả bằng 0, hiển thị “Không cần thanh toán” cho bên đó. Khóa nút khi request đang chạy, tải lại trạng thái nếu timeout.

## 3. Sau kiểm định

- Buyer thấy **Mua tiếp** và **Từ chối mua** khi đơn `PASSED`, còn hạn `finalPaymentDeadline` và chưa có `finalPaymentPaidAt`.
- Với Mua tiếp, lấy `POST /api/v1/shipping/quotes` bằng `postId` và `deliveryAddress` gồm `name`, `phone`, `latitude`, `longitude`. Response bọc `data`. Với `AT_CENTER`, cần `data.leg === "CENTER_TO_BUYER"`. Hiển thị `data.productPrice`, `data.shippingFee`, `data.totalPayable`, và khoản dự phòng được hoàn ở dòng riêng.
- Mở rộng `inspectorService.confirmPurchase` nhận body `{"requestId":"<uuid>","shippingQuoteId":"<data.quoteId>","paymentMethod":"WALLET"}` và gửi đến `POST /api/v1/inspection/orders/{id}/confirm-purchase`. Giữ cùng `requestId` khi gửi lại cùng thao tác sau timeout. Điều hướng theo `linkedOrderId` trả về khi mở chi tiết đơn mua. ID đường dẫn khi thao tác kiểm định là `id` của đơn kiểm định.
- Từ chối qua `POST /api/v1/inspection/orders/{id}/reject-purchase` với `{"reason":"..."}`. Sau đó hoặc khi `FAILED`, hiển thị tiến trình trả hàng từ `GET /api/v1/inspection-orders/{id}/shipments` (bọc `data`), lọc `CENTER_TO_SELLER`. Dùng `status`, `quotedFee`, `actualFee` của chuyến để hiển thị tiến trình và cước. Dùng lịch sử ví hiện có để xác nhận tiền thực nhận vì response đơn kiểm định chưa có trường quyết toán riêng cho khoản hoàn dự phòng. `feeSettledAt` là mốc hạch toán phí kiểm định chung.
- Với giao diện Staff/Admin đang có chức năng tạo chuyến, dùng chặng `CENTER_TO_SELLER` sau khi đơn đã chốt trả hàng và hàng đã tới trung tâm. Nếu BE trả `409` do vượt dự phòng, hiển thị thông báo và giữ trạng thái chờ xử lý chênh lệch. Với chuyến đã tạo, có thể dùng `POST /api/v1/shipments/{shipmentId}/sync` theo phân quyền hiện có.

## 4. Admin cấu hình tỷ lệ

Thêm mục “Dự phòng cước trả hàng kiểm định” trong `/admin/inspection-governance`. Chỉ cho Admin có `INSPECTION_FEE_MANAGE` truy cập thao tác. `GET /api/v1/admin/inspection/return-shipping-policy` và `PUT` cùng URL trả `data.markupPercentage`. Body PUT là `{"markupPercentage":20}`. Nhập từ **0 đến 100**, tối đa 2 chữ số thập phân. Cho phép giá trị 0. Dùng dữ liệu BE làm nguồn giá trị sau khi lưu. Thay đổi áp dụng khi lấy báo giá mới.

## 5. Kiểm tra và bàn giao

- Dữ liệu mẫu có phí kiểm định 50.000đ, cước đi 30.000đ và dự phòng 36.000đ. Buyer trả trước tương ứng 116.000đ, 0đ hoặc 58.000đ theo ba cách chia phí, chưa tính cọc nếu có.
- Nếu mua, với tiền hàng 500.000đ và cước giao Buyer 40.000đ, màn hình mua tiếp hiển thị tổng 540.000đ và dòng hoàn dự phòng riêng. Nếu trả hàng và cước thực tế 32.000đ, tổng phần dự phòng dư là 4.000đ.
- Kiểm tra quote hết hạn, double click, mất mạng, bên đã thanh toán, phần phải trả 0đ, `403`, `409` và phí trả hàng vượt dự phòng. Với `AT_HOME`, tiếp tục dùng bộ field `atHome*`.
- Chạy kiểm tra type, lint và test có sẵn. Báo danh sách file sửa và kết quả chạy.

