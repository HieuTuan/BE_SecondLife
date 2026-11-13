# FE tích hợp hoàn ứng cước kiểm định qua Lalamove Sandbox

## Bật môi trường demo

Áp dụng Flyway V77 và cấu hình `INSPECTION_CASH_SHIPPING_SIMULATION_ENABLED=true` trên BE test kết nối Lalamove Sandbox. Dùng database và ví thử nghiệm. Số tiền hoàn ứng được cộng vào ví SecondLife của môi trường đó. Giao diện cần gắn nhãn **Mô phỏng Sandbox** ở các bước xác nhận chi và hoàn ứng.

Luồng này áp dụng cho đơn mới có `cashShippingSimulation: true` và hai chặng `SELLER_TO_CENTER`, `CENTER_TO_SELLER`. Chặng `CENTER_TO_BUYER` dùng quy trình thanh toán mua hàng hiện hành.

## Báo giá và thu trước

1. Gọi `POST /api/v1/inspection/quote` như hiện tại. Response có `cashShippingSimulationEnabled` để FE biết gói tại Hub có thể áp dụng hoàn ứng.
2. `atCenterTotalFee` vẫn là tổng dự toán dịch vụ và vận chuyển. Khi hiển thị số tiền sẽ trừ ví, dùng quy tắc bên dưới cho màn hình xem trước và dùng `buyerFeeShare`, `sellerFeeShare` từ response đơn sau khi tạo hoặc đổi tỷ lệ.
3. `POST /api/v1/inspection/requests` và các endpoint phản hồi vẫn dùng `feePayer` hoặc `counterFeePayer` với `BUYER_PAYS`, `SELLER_PAYS` hoặc `SPLIT_50_50`. Khi thanh toán qua `POST /api/v1/inspection/orders/{orderId}/pay-fee`, buyer trả thêm `buyerCommitmentDeposit` nếu có.

| Tỷ lệ | Thu trước phần cước Seller → Hub | Phí kiểm định và dự phòng Hub → Seller |
| --- | --- | --- |
| `BUYER_PAYS` | Buyer nộp 100% cước dự kiến | Buyer nộp 100% |
| `SELLER_PAYS` | Seller trả tiền mặt khi giao vận | Seller nộp 100% |
| `SPLIT_50_50` | Buyer nộp 50% cước dự kiến. Seller ứng cước khi giao vận. | Mỗi bên nộp 50% |

Với đơn có `cashShippingSimulation: true`, `buyerFeeShare` và `sellerFeeShare` là số tiền cần nộp trước, đã loại phần cước đi seller tự chịu. Tách nhãn **Nộp trước** khỏi **Cước seller ứng khi giao**. Khi mở lại đơn hoặc có phản hồi thương lượng, tải lại DTO của BE để lấy số tiền mới.

## Tạo và theo dõi vận đơn

- Seller gọi `POST /api/v1/inspection-orders/{inspectionId}/shipments` với `leg: "SELLER_TO_CENTER"` và `requestId` UUID. Seller là người ứng cước tiền mặt.
- Khi đã chốt trả hàng, Hub gọi `POST /api/v1/inspection-orders/{inspectionId}/shipments/return-quote` rồi dùng báo giá còn hiệu lực khi tạo `CENTER_TO_SELLER`. Hub là người ứng tiền mặt. Giữ `requestId` cũ khi gửi lại cùng yêu cầu.

```json
{
  "requestId": "cf160128-5ccc-4ca2-86d0-c0cc24266e90",
  "leg": "CENTER_TO_SELLER",
  "maxReturnFee": 55000
}
```

Đọc `GET /api/v1/inspection-orders/{inspectionId}/shipments`. Danh sách nằm trong `data` của response. BE gắn `cashPayerId` và gửi ghi chú về người ứng cho tài xế. FE không nhập người ứng, phương thức thanh toán hoặc địa chỉ thay cho BE trong luồng này.

| Dữ liệu vận đơn | Cách hiển thị |
| --- | --- |
| `cashPayerId == null` | Hiển thị vận đơn theo luồng hiện tại. |
| `cashPayerId != null`, vận đơn chưa giao xong | **Seller ứng cước** hoặc **Hub ứng cước**, tùy `leg`. |
| `status == "DELIVERED"`, `providerStatus == "COMPLETED"`, `cashConfirmedAt == null` | **Chờ xác nhận cước tiền mặt (Sandbox)**. |
| `cashConfirmedAt != null` | **Đã quyết toán cước (Sandbox)**, hiển thị `cashPaidAmount` và `cashReimbursedAmount`. Giá trị hoàn ứng 0 hợp lệ khi seller chịu toàn bộ cước đi. |

## Staff/Admin xác nhận hoàn ứng

Hiện nút **Xác nhận chi cước mô phỏng** cho Staff có quyền `STAFF_DISPUTE_REVIEW` hoặc Admin có quyền `ADMIN_RBAC_MANAGE` khi có `cashPayerId`, có mã `orderCode`, chuyến đã giao xong, có `actualFee` và chưa xác nhận.

Hiển thị mã vận đơn, người ứng, cước thực tế và ô nhập tham chiếu biên nhận. Kiểm tra chứng từ mô phỏng trước khi gọi `POST /api/v1/shipments/{shipmentId}/cash-confirmation`. Dùng `shipmentId` nội bộ SecondLife trên URL.

```json
{
  "paidAmount": 55000,
  "receiptReference": "DEMO-BIEN-NHAN-CHANG-HOAN-001"
}
```

- `200` trả lại vận đơn trong `data`, có `cashConfirmedAt`, `cashPaidAmount` và `cashReimbursedAmount`. Tải lại dữ liệu vận đơn và ví. Lịch sử ví lọc được giao dịch `SHIPPING_REIMBURSE` theo tham chiếu `SHIPMENT`.
- `400` hiển thị lỗi nhập liệu. `403` hiển thị thiếu quyền. `409` hiển thị thông báo từ BE, chẳng hạn chưa giao xong, cước biên nhận lệch cước hãng hoặc vượt khoản đã giữ. Nếu số cước chưa cập nhật, dùng chức năng đồng bộ vận đơn trước khi xác nhận.
- Khi request bị timeout, gửi lại **cùng số tiền và tham chiếu biên nhận**. BE trả kết quả đã ghi và chỉ hoàn ứng một lần cho mỗi chặng.

## Kịch bản demo 50/50

Phí kiểm định 50.000 ₫, cước đi 340.000 ₫, dự phòng cước hoàn 60.000 ₫. Buyer nộp trước 225.000 ₫, seller nộp 55.000 ₫, chưa tính cọc cam kết nếu có.

1. Seller ứng 340.000 ₫. Sau khi chuyến tới Hub và Staff xác nhận, seller được hoàn ứng 170.000 ₫ từ phần cước đi buyer đã đóng.
2. Khi phải trả hàng, Hub ứng 55.000 ₫ cho chuyến về. Sau khi Staff xác nhận, Hub được hoàn ứng 55.000 ₫ từ dự phòng.
3. Dự phòng còn dư 5.000 ₫. Ví buyer và ví seller mỗi bên nhận lại 2.500 ₫. Nếu buyer mua tiếp sau kiểm định, quy trình quyết toán kiểm định hoàn lại phần dự phòng chặng hoàn theo tỷ lệ đã chốt.

