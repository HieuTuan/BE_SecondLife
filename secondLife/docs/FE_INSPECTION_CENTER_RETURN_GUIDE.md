# Hướng dẫn FE Web — Trung tâm trả hàng sau kiểm định SecondLife

## 1. Luồng nghiệp vụ

- Trước khi có quyết định mua, Buyer và Seller chỉ đóng phí kiểm định theo thỏa thuận. Với `AT_CENTER`, gói phí có cước Seller → Trung tâm và dự phòng cước Trung tâm → Seller. Cọc cam kết Buyer là khoản riêng nếu có cấu hình. Tiền sản phẩm được thu khi Buyer xác nhận mua sau kiểm định đạt.
- Khi kiểm định không đạt (`FAILED`), BE hạch toán phí kiểm định và cước chiều đi, hoàn cọc cam kết đã thu cho Buyer nếu có và giữ dự phòng cước chiều về.
- Khi kiểm định đạt nhưng Buyer bấm từ chối mua, hoặc hết hạn `finalPaymentDeadline` mà chưa mua, BE chuyển đơn kiểm định sang `CANCELLED`, hạch toán phí và chuyển cọc cam kết đã thu cho Seller nếu có. Khoản dự phòng tiếp tục dùng cho cước chiều về.
- Đơn `AT_CENTER` có hàng đang ở trung tâm thì trung tâm phụ trách tạo chuyến `CENTER_TO_SELLER`. Với đơn đã gắn trung tâm, Staff/Admin xử lý hỗ trợ đối soát. Đơn cũ chưa gắn trung tâm tiếp tục theo quyền Staff/Admin hiện có. Luồng `AT_HOME` không có chuyến từ trung tâm về Seller.
- Khi trả hàng xong, trạng thái chuyến là `DELIVERED`. Kết quả kiểm định `FAILED` và hạn khiếu nại của Seller tiếp tục hiển thị riêng.

## 2. Điểm cần sửa trong FE hiện tại

| Vị trí | Việc cần làm |
| --- | --- |
| `src/pages/InspectionCenterDashboardView.tsx` | Tách thao tác “Tạo vận đơn trả Seller” khỏi form nhập địa chỉ thủ công. Nhánh `CENTER_TO_SELLER` gửi body tối giản theo mục 4. Bỏ tọa độ, tên và số điện thoại fallback ở nhánh này. |
| `src/services/shippingService.ts` | Tái sử dụng `createInspectionShipment`, `getInspectionShipments`, `syncShipment`. Để các trường địa chỉ tùy chọn với chặng trả Seller. |
| `src/pages/BuyerInspectionDecisionView.tsx` | Hiển thị chặng trả Seller ở nhánh `FAILED` hoặc `CANCELLED`. Khi hết đếm ngược, refresh đơn cho đến khi thấy trạng thái BE mới. |
| `src/pages/EscrowOrdersView.tsx` và giao diện khiếu nại Seller | Tách badge kết quả kiểm định khỏi badge chuyến trả hàng. Điều kiện khiếu nại dựa vào đơn kiểm định, kể cả khi chuyến đã giao về Seller. |
| `src/components/modals/OrderTrackingModal.tsx` | Hiển thị đúng hướng Trung tâm → Seller, `orderCode`, `shareLink`, `actualFee` và trạng thái chuyến. |

Dùng DTO của BE và danh sách chuyến lấy qua API làm nguồn hiển thị. Giữ `requestId` cục bộ khi cần retry, tách khỏi dữ liệu vận đơn. Mọi path dưới đây đã có prefix `/api/v1`; tránh thêm lần hai nếu API client đã đặt prefix.

## 3. Điều kiện hiện nút ở dashboard trung tâm

Lấy dữ liệu đơn từ API trung tâm hiện có hoặc `GET /api/v1/inspection/orders/{inspectionId}`. Lấy chuyến qua `GET /api/v1/inspection-orders/{inspectionId}/shipments`.

Hiện nút **Tạo vận đơn trả Seller** khi đủ các điều kiện:

- Người đăng nhập có role `INSPECTION_CENTER` và ID của trung tâm trùng `order.inspectionCenterId`.
- `order.orderType === "AT_CENTER"` và `order.status` là `FAILED` hoặc `CANCELLED`.
- Có `feeSettledAt`, `returnShippingReserveFee` khác null và `finalPaymentPaidAt` còn null. Giá trị dự phòng bằng 0 vẫn là một giá trị hợp lệ.
- Danh sách chuyến có `leg === "SELLER_TO_CENTER"` với `status === "DELIVERED"`.
- Chưa có chuyến `CENTER_TO_SELLER` đang tồn tại ngoài trạng thái `CANCELLED`. Nếu chuyến đã có, ưu tiên hiển thị **Theo dõi vận đơn**. Khi `CREATING` hoặc `CREATION_UNCERTAIN`, giữ mã yêu cầu và dùng quy trình đối soát hiện có.

Trong modal xác nhận, ghi rõ **Địa chỉ lấy và giao được lấy từ vận đơn chiều đi đã giao thành công** và hiển thị khoản **Dự phòng cước chiều về** từ `returnShippingReserveFee`. Backend kiểm tra quyền và điều kiện lần cuối.

## 4. Gọi API đặt chuyến trả Seller

`POST /api/v1/inspection-orders/{inspectionId}/shipments` với token trung tâm. ID là `id` của đơn kiểm định.

```json
{
  "requestId": "781527ed-cfd8-4573-bb08-ad981683c50e",
  "leg": "CENTER_TO_SELLER"
}
```

Sinh UUID khi người dùng bắt đầu một lần đặt chuyến. Khóa nút trong lúc gửi. Nếu mất mạng hoặc timeout, tải lại danh sách chuyến và dùng **cùng UUID** nếu cần gửi lại cùng yêu cầu. Chỉ sinh UUID mới khi người dùng bắt đầu lần đặt mới sau khi đã xác nhận chuyến trước bị hủy hoặc yêu cầu cũ đã kết thúc rõ ràng.

Bỏ `fromAddress` và `toAddress` trong request của trung tâm. BE đảo điểm lấy/giao từ vận đơn Seller → Trung tâm đã giao thành công. Trường `reason` là tùy chọn. Chặng này không cần `reportId`.

Response mẫu rút gọn:

```json
{
  "success": true,
  "data": {
    "shipmentId": "<shipmentId>",
    "inspectionOrderId": "<inspectionId>",
    "leg": "CENTER_TO_SELLER",
    "provider": "LALAMOVE",
    "orderCode": "<ma-van-don>",
    "status": "READY_TO_PICK",
    "providerStatus": "ASSIGNING_DRIVER",
    "quotedFee": 36000,
    "actualFee": 32000
  }
}
```

Đọc kết quả theo `data.status`. `200 OK` có thể đi kèm `CREATION_UNCERTAIN` hoặc `CANCELLED` từ bước tạo với hãng. Dùng `lastError` để hiển thị thông báo nếu có. Sau thao tác, tải lại đơn và danh sách chuyến.

| Tình huống | Xử lý FE |
| --- | --- |
| `403` | Hiện thông báo trung tâm không phụ trách đơn hoặc tài khoản thiếu quyền. |
| `400` do gửi địa chỉ | Sửa payload về hai trường bắt buộc như ví dụ. |
| `409` do trạng thái hoặc đã có chuyến | Hiện thông báo BE và tải lại đơn, vận đơn. |
| `409` do cước vượt dự phòng | Hiện “Cước chiều về vượt khoản dự phòng đã thu, cần xử lý chênh lệch trước khi đặt chuyến”. Không hiển thị chuyến đã tạo. |
| `CREATION_UNCERTAIN` | Hiện “Chờ đối soát với Lalamove” và đưa vận đơn vào quy trình hỗ trợ/đối soát hiện có. |

## 5. Theo dõi vận đơn và quyết toán ví

- Đọc danh sách chuyến bằng `GET /api/v1/inspection-orders/{inspectionId}/shipments`, lọc `CENTER_TO_SELLER`. Có thể đồng bộ qua `POST /api/v1/shipments/{shipmentId}/sync` theo quyền hiện có.
- Hiển thị tiến trình theo trạng thái shipment, ví dụ `READY_TO_PICK`, `ASSIGNED`, `PICKED`, `DELIVERED`. Hiện `deliveredAt` khi có.
- Với chặng này, `returnShippingReserveFee` là khoản đã giữ và `actualFee` là cước hãng ghi nhận. `quotedFee` có thể mang giá trị trần dự phòng, nên không dùng trường này làm cước thực trả.
- Sau khi giao thành công và có phí thực tế, BE hoàn phần dư theo tỷ lệ `feePayer` đã chốt. Ví dụ dự phòng 36.000đ, cước 32.000đ, Buyer chịu 100% thì Buyer nhận 4.000đ. Nếu chia 50/50, mỗi bên nhận 2.000đ.
- Xác nhận đã nhận tiền bằng lịch sử ví. `feeSettledAt` là mốc hạch toán kiểm định ban đầu và `actualFee` có thể xuất hiện trước khi giao xong. DTO chưa trả mốc hoàn dư riêng. Nếu chuyến đã giao nhưng phí còn thiếu hoặc vượt dự phòng, hiển thị đang chờ đối soát.

## 6. Hết hạn mua và quyền khiếu nại

- Trên màn Buyer sau kiểm định đạt, dùng `finalPaymentDeadline` để đếm ngược. Khi hết hạn, khóa nút mua và tải lại đơn. Scheduler BE quét mỗi 60 giây. Chờ BE trả `CANCELLED` rồi mới hiển thị “Đã hủy do hết hạn thanh toán”. `paymentDeadline` là hạn đóng phí kiểm định ở bước trước.
- Trên màn Seller, hiển thị nút khiếu nại khi `status === "FAILED"`, `appealStatus` chưa được gửi (`NONE` hoặc null), `appealDeadline` còn hiệu lực và người xem là Seller của đơn. Việc chuyến `CENTER_TO_SELLER` đã `DELIVERED` vẫn cho phép khiếu nại trong thời hạn.
- Gửi khiếu nại qua `POST /api/v1/inspection/orders/{inspectionId}/appeal`. Body tối thiểu là `{"reason":"Kết quả chưa phản ánh đúng tình trạng sản phẩm"}`. Dùng lại `evidenceUrls`, `contestedItemIds`, `note` của form hiện có nếu phù hợp.

## 7. Kiểm thử bàn giao

1. Trung tâm A phụ trách đơn `FAILED` đã nhận hàng. A đặt chuyến được bằng body chỉ có `requestId` và `leg`. Trung tâm B gọi cùng API nhận `403`.
2. Gửi lại cùng UUID sau timeout. API trả lại cùng chuyến. Khi hãng chưa xác nhận tạo, màn hình chuyển sang chờ đối soát.
3. Đơn `AT_HOME`, đơn chưa tới trung tâm hoặc chưa hạch toán phí không hiện nút. Kiểm tra thêm API từ chối đặt chuyến nếu gọi trực tiếp.
4. Đơn `PASSED` đã qua `finalPaymentDeadline` và chưa mua chuyển sang `CANCELLED` sau scheduler. Đơn Buyer đã thanh toán vẫn theo luồng giao Buyer.
5. Chuyến hoàn giao thành công trước hết hạn khiếu nại. Buyer/Seller thấy “Đã trả về Seller”, kết quả vẫn “Không đạt”, Seller vẫn gửi khiếu nại được.
6. Kiểm tra ba tỷ lệ đóng phí, trường hợp cước đúng bằng dự phòng, cước thấp hơn, báo giá vượt dự phòng, hãng chưa gửi cước và callback lặp.

