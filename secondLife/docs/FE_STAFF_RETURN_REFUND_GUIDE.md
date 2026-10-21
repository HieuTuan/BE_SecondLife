# FE: Màn hình STAFF xử lý hoàn hàng và hoàn tiền sau tranh chấp

Tài liệu đối chiếu controller, DTO và service BE hiện tại. Phạm vi: tranh chấp có `orderId`, hoàn hàng `BUYER_TO_SELLER`, hoàn toàn bộ tiền hàng vào ví SecondLife. Các trạng thái giao diện dưới đây là trạng thái FE suy ra, không phải enum mới để gửi lên BE.

## 1. Quy tắc cần hiểu trước khi dựng màn hình

- Buyer chỉ mở tranh chấp khi đơn đã được carrier giao thành công và thời điểm BE chấp nhận nhỏ hơn `shippingDeliveredAt + 48 giờ`. Tại đúng hạn bị từ chối. Buyer đã xác nhận nhận hàng/đơn đã giải ngân thì không mở tranh chấp mới.
- Quy tắc 48 giờ áp dụng lúc buyer mở tranh chấp. STAFF vẫn nhận, xét duyệt, tạo chuyến và hoàn tiền cho report hợp lệ sau hạn này. Không khóa nút STAFF chỉ vì đồng hồ hiện tại đã qua 48 giờ.
- Report hợp lệ làm escrow `FROZEN`. `CONFIRMED` chỉ nói kết luận khiếu nại; phải có `resolutionAction=RETURN_AND_REFUND` mới được tạo chuyến hoàn.
- Carrier xác nhận vận đơn hoàn `DELIVERED` chưa đồng nghĩa đã hoàn tiền. Chỉ hiển thị hoàn tiền thành công khi tải lại report thấy `refundedAt`.
- BE tính số tiền hoàn bằng `Order.finalPrice`; phí vận chuyển không được cộng vào khoản hoàn tiền hàng này. Không gửi `amount`, phí, địa chỉ hoặc loại xe tự nhập trong thao tác hoàn của màn hình này.
- Các điều kiện nút là kiểm tra sớm cho UX. BE luôn kiểm tra lại trạng thái, quyền, tiền giữ, liên kết và request đồng thời.

## 2. Phân quyền và dữ liệu người đang đăng nhập

FE cần UUID người dùng hiện tại cùng role/authority từ cơ chế auth đang dùng. Không so sánh bằng email hoặc tên hiển thị.

| Nhóm thao tác | STAFF | ADMIN |
| --- | --- | --- |
| Xem queue/chi tiết report/ảnh bằng chứng, nhận xử lý, kết luận thông thường | `ROLE_STAFF` + `STAFF_FRAUD_REVIEW` | `ROLE_ADMIN` + `ADMIN_RBAC_MANAGE` |
| Duyệt `RETURN_AND_REFUND` | Cả `STAFF_FRAUD_REVIEW` và `STAFF_DISPUTE_REVIEW` | `ADMIN_RBAC_MANAGE` |
| Tạo chuyến hoàn, hoàn tiền | `ROLE_STAFF` + `STAFF_DISPUTE_REVIEW` | `ROLE_ADMIN` + `ADMIN_RBAC_MANAGE` |
| Đọc thông tin xác nhận đơn qua `/confirmation` | `ROLE_STAFF` + `STAFF_DISPUTE_REVIEW` | `ADMIN_RBAC_MANAGE` |
| Đọc commission snapshot để xem tiền hàng trước refund (tùy chọn) | `ROLE_STAFF` + `STAFF_PAYOUT_REVIEW` | `ROLE_ADMIN` + `ADMIN_COMMISSION_MANAGE` |

GET danh sách vận đơn/events/tracking và các thao tác sync/cancel/reconcile kiểm tra STAFF/ADMIN và ownership theo code hiện có; riêng reconcile có annotation role STAFF/ADMIN. UI màn hình này nên chỉ cung cấp thao tác vận hành cho tài khoản có quyền xử lý hoàn hàng nêu trên. Không nhầm giới hạn UX này với việc mọi endpoint vận chuyển đều kiểm tra cùng một authority.

Chỉ reviewer được giao (`report.reviewerId === currentUser.id`) được kết luận report, kể cả ADMIN. Không có API chuyển người phụ trách hoặc mở lại quyết định. Người nhận/xét duyệt không được đồng thời là `reporterId` hoặc `sellerId`. Thao tác vận hành chuyến/hoàn tiền của STAFF đã được cấp quyền không bắt buộc do chính reviewer thực hiện.

## 3. Bố cục màn hình

### 3.1. Danh sách `/staff/reports?targetType=ORDER`

Bốn tab lọc thật ở BE: **Mới** (`OPEN`), **Đang xem xét** (`IN_REVIEW`), **Đã kết luận** (`CONFIRMED`), **Đã bác** (`DISMISSED`). Có thể bỏ `status` để xem tất cả.

Cột đề xuất: mã report, mã đơn, buyer/seller UUID hoặc tên từ nguồn đã có quyền đọc, lý do, ngày tạo, reviewer, trạng thái report, kết quả xử lý, tình trạng hoàn tiền. Row click mở trang chi tiết.

BE chưa hỗ trợ filter `reviewerId`, `resolutionAction`, `refundedAt`, giai đoạn hoàn hàng, tìm kiếm `orderId` hoặc đếm toàn bộ “chờ hoàn tiền”. Không gửi các query này rồi coi là đã lọc. Filter/nhãn tính từ dữ liệu trang chỉ có nghĩa trên trang đang tải; `totalElements` vẫn là tổng của filter BE. Không tạo các tab giai đoạn tài chính với tổng toàn cục suy từ một trang.

### 3.2. Chi tiết `/staff/reports/:reportId`

1. **Hồ sơ khiếu nại:** mã report/đơn, buyer/seller, `reasonCode`, `description`, bằng chứng, ngày tạo.
2. **Xử lý:** reviewer, kết luận, `responsibleParty`, `decisionReason`, `resolvedAt`, `returnApprovedAt`.
3. **Hoàn hàng:** vận đơn được chọn, mã carrier, hai trạng thái, timeline, POD, lỗi gần nhất và hành động vận chuyển.
4. **Hoàn tiền:** giải thích hoàn toàn bộ tiền hàng vào ví; audit `refundAmount/refundedAt/refundedBy` sau thành công.
5. **Thanh hành động:** chỉ hiện các nút phù hợp theo phần 6; mỗi thao tác có loading và chặn gửi lặp.

Nếu chưa có dữ liệu tiền hàng trước refund, hiển thị “Hoàn toàn bộ tiền hàng theo đơn; BE xác định số tiền”. Không dùng `quotedFee`/`actualFee` của shipment làm tiền hoàn. Phần 10 chỉ rõ dữ liệu API hiện có và còn thiếu.

## 4. API đọc và contract response

Tất cả request dùng `Authorization: Bearer <accessToken>`. JSON request thêm `Content-Type: application/json`. Các API report/shipment/confirmation/commission trong tài liệu trả `ApiResponse`; `data` chứa kết quả. Field null có thể bị lược bỏ; kiểm tra bằng `value == null`, không chỉ `=== null`.

`Instant` nghiệp vụ có múi giờ (ví dụ `2026-10-21T03:00:00Z`); đổi sang giờ địa phương khi hiển thị. `timestamp` của envelope là `LocalDateTime`, không dùng nó làm hạn khiếu nại hoặc mốc sắp xếp nghiệp vụ.

### 4.1. Queue

```http
GET /api/v1/staff/reports?targetType=ORDER&status=OPEN&page=0&size=20
```

Response minh họa rút gọn các field của từng report:

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "items": [
      {
        "id": "11111111-1111-4111-8111-111111111111",
        "orderId": "22222222-2222-4222-8222-222222222222",
        "targetType": "ORDER",
        "status": "OPEN",
        "resolutionAction": "NONE",
        "reasonCode": "MISLEADING_DESCRIPTION",
        "createdAt": "2026-10-21T03:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "isLast": true
  },
  "timestamp": "2026-10-21T10:00:00"
}
```

- Đọc `response.data.data.items` nếu thư viện HTTP của FE dùng `response.data` cho body; không tìm `content` hoặc `data` là array trực tiếp.
- Page bắt đầu từ 0. Query repository sắp xếp cố định `created_at DESC, id DESC`; không dựa vào `sort` tùy chọn để đổi thứ tự.
- Trả trang rỗng là trạng thái hợp lệ.

### 4.2. Chi tiết report

```http
GET /api/v1/reports/{reportId}
```

Response mẫu đầy đủ field của DTO, ghi null tường minh để minh họa:

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "id": "11111111-1111-4111-8111-111111111111",
    "requestId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
    "reporterId": "44444444-4444-4444-8444-444444444444",
    "sellerId": "33333333-3333-4333-8333-333333333333",
    "postId": "99999999-9999-4999-8999-999999999999",
    "orderId": "22222222-2222-4222-8222-222222222222",
    "reasonCode": "MISLEADING_DESCRIPTION",
    "description": "Sản phẩm nhận được khác mô tả.",
    "evidenceUrls": ["/api/v1/reports/evidence/88888888-8888-4888-8888-888888888888"],
    "status": "OPEN",
    "reviewerId": null,
    "responsibleParty": null,
    "decisionReason": null,
    "createdAt": "2026-10-21T03:00:00Z",
    "updatedAt": "2026-10-21T03:00:00Z",
    "resolvedAt": null,
    "relatedPostId": null,
    "targetType": "ORDER",
    "resolutionAction": "NONE",
    "returnShipmentId": null,
    "returnApprovedAt": null,
    "refundedAt": null,
    "refundedBy": null,
    "refundAmount": null
  },
  "timestamp": "2026-10-21T10:00:00"
}
```

Sau refund, report vẫn `status=CONFIRMED`, action vẫn `RETURN_AND_REFUND`; `refundedAt`, `refundedBy`, `refundAmount` được điền. Không chờ enum report `REFUNDED` vì enum đó không tồn tại.

### 4.3. Vận đơn và chọn đúng chiều hoàn

```http
GET /api/v1/orders/{orderId}/shipments
```

Response `data` là array `ShipmentResponse`, gồm cả chiều giao và chiều hoàn. Một phần tử mẫu:

```json
{
  "shipmentId": "66666666-6666-4666-8666-666666666666",
  "orderId": "22222222-2222-4222-8222-222222222222",
  "inspectionOrderId": null,
  "leg": "BUYER_TO_SELLER",
  "orderCode": "1234567890123456789",
  "status": "READY_TO_PICK",
  "providerStatus": "ASSIGNING_DRIVER",
  "quotedFee": 30000,
  "actualFee": 30000,
  "expectedDeliveryTime": null,
  "deliveredAt": null,
  "reason": "Hoàn hàng theo quyết định tranh chấp.",
  "lastError": null,
  "podUrl": null,
  "provider": "LALAMOVE",
  "driverId": null,
  "shareLink": null
}
```

Chọn shipment bằng:

```ts
const selectedReturn = shipments.find(s =>
  s.shipmentId === report.returnShipmentId &&
  s.orderId === report.orderId &&
  s.leg === 'BUYER_TO_SELLER'
);
```

Không chọn phần tử cuối array, không chọn mọi shipment `DELIVERED`, không dùng vận đơn giao đi làm căn cứ refund của tranh chấp này. `ShipmentResponse` hiện không trả `reportId`; liên kết chính ở `ReportResponse.returnShipmentId`.

Nếu report có `returnShipmentId` nhưng không thấy phần tử phù hợp: khóa thao tác tiền/tạo chuyến, tải lại report và shipments. Nếu vẫn lệch thì hiển thị cần đối soát, không tự xóa liên kết hoặc đoán shipment thay thế.

### 4.4. Timeline và tracking

```http
GET /api/v1/shipments/{shipmentId}/events
GET /api/v1/shipments/{shipmentId}/tracking
```

`events.data` là array gồm `id`, `shipmentId`, `eventKey`, `type`, `status`, `occurredAt`, `reason`, `payload`. `payload` là JSON lưu dưới dạng **string**; không phải object. Hiển thị timeline từ `type/status/occurredAt/reason`; không render toàn bộ payload chứa dữ liệu liên hệ vào UI/log công khai.

Tracking `data` gồm `shipmentId`, `provider`, `orderCode`, `status`, `providerStatus`, `shareLink`, `driverId`, `locationStatus` (`AVAILABLE`/`UNAVAILABLE`), `driver`. `driver.coordinates` có thể null. Không có tọa độ không có nghĩa chuyến thất bại; sau giao xong thường không còn vị trí hiện tại.

Ví dụ tracking rút gọn:

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "shipmentId": "66666666-6666-4666-8666-666666666666",
    "provider": "LALAMOVE",
    "orderCode": "1234567890123456789",
    "status": "PICKED",
    "providerStatus": "PICKED_UP",
    "locationStatus": "UNAVAILABLE",
    "driver": null
  },
  "timestamp": "2026-10-22T10:00:00"
}
```

Không poll tracking nhanh hơn 10 giây/lần. Poll các API đọc trạng thái đã lưu (report/shipments) khoảng 15–30 giây khi đang chờ; dừng khi tab ẩn, unmount hoặc hồ sơ hoàn tiền xong. Không dùng POST sync làm polling liên tục vì nó gọi carrier.

### 4.5. Xem bằng chứng riêng tư

Các phần tử `evidenceUrls` đã là đường dẫn API ảnh. Fetch đúng backend với Bearer token, đọc blob, tạo object URL:

```ts
const res = await fetch(`${apiOrigin}${evidenceUrl}`, {
  headers: { Authorization: `Bearer ${token}` }
});
if (!res.ok) throw new Error('Không tải được bằng chứng');
const objectUrl = URL.createObjectURL(await res.blob());
// dùng objectUrl cho <img>; khi đóng ảnh/unmount:
// URL.revokeObjectURL(objectUrl);
```

`apiOrigin` là origin backend, không chứa thêm `/api/v1` vì `evidenceUrl` đã có prefix đó. Nếu axios baseURL đã chứa `/api/v1`, adapter cần tránh ghép prefix hai lần. Không đưa token vào query string hoặc dùng `<img src=evidenceUrl>` rồi giả định header Authorization tự đi kèm. API trả bytes, không có `ApiResponse` cho nội dung ảnh; không tải bằng credential tới URL tùy ý ngoài backend.

## 5. Các API thao tác và request/response

| Thao tác | Method và endpoint | Body | `data` thành công |
| --- | --- | --- | --- |
| Nhận xử lý | `POST /api/v1/staff/reports/{id}/start-review` | Không có | `ReportResponse` |
| Kết luận/duyệt hoàn | `POST /api/v1/staff/reports/{id}/resolve` | `Resolve` bên dưới | `ReportResponse` |
| Tạo vận đơn hoàn | `POST /api/v1/orders/{orderId}/shipments` | `CreateShipmentRequest` bên dưới | `ShipmentResponse` |
| Đồng bộ carrier | `POST /api/v1/shipments/{id}/sync` | Không có | `ShipmentResponse` |
| Đối soát chuyến tạo chưa rõ | `POST /api/v1/shipments/{id}/reconcile-creation` | `{ "orderCode": "..." }` | `ShipmentResponse` |
| Hủy vận đơn hoàn | `POST /api/v1/shipments/{id}/cancel` | Không có | `ShipmentResponse` |
| Hoàn tiền | `POST /api/v1/shipments/{id}/refund` | `ShippingDecisionRequest` bên dưới | `ShipmentResponse` |

Các thao tác này trả HTTP 200 khi request được xử lý thành công ở tầng API. Riêng tạo shipment có thể trả `success=true` nhưng `data.status=CANCELLED` hoặc `CREATION_UNCERTAIN`; phải đọc trạng thái và `lastError`.

### 5.1. Nhận xử lý

```http
POST /api/v1/staff/reports/11111111-1111-4111-8111-111111111111/start-review
```

Response là report đầy đủ với các thay đổi sau (mẫu rút gọn):

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "id": "11111111-1111-4111-8111-111111111111",
    "status": "IN_REVIEW",
    "reviewerId": "55555555-5555-4555-8555-555555555555",
    "resolutionAction": "NONE"
  }
}
```

Reviewer tự nhận lại hồ sơ đang được giao cho mình là retry hợp lệ. Người khác nhận report đã có người xử lý bị từ chối; FE tải lại và chuyển sang chỉ đọc.

### 5.2. Duyệt hoàn hàng và hoàn tiền

```json
{
  "decision": "CONFIRMED",
  "responsibleParty": "SELLER",
  "reason": "Đã đối chiếu bằng chứng, chấp thuận hoàn hàng về seller.",
  "resolutionAction": "RETURN_AND_REFUND"
}
```

`responsibleParty` chọn một trong `SELLER`, `BUYER`, `CARRIER`. Chọn trách nhiệm không tự quyết định người trả phí vận chuyển. `reason` bắt buộc, không chỉ khoảng trắng, tối đa 2000 ký tự.

Response là report đầy đủ, các field thay đổi chính:

```json
{
  "status": "CONFIRMED",
  "responsibleParty": "SELLER",
  "decisionReason": "Đã đối chiếu bằng chứng, chấp thuận hoàn hàng về seller.",
  "resolutionAction": "RETURN_AND_REFUND",
  "returnApprovedAt": "2026-10-21T03:30:00Z",
  "resolvedAt": "2026-10-21T03:30:00Z"
}
```

Đây là đoạn trích trong `data`, không phải envelope khác. Duyệt xong không tự tạo chuyến, không tự hoàn tiền; UI chuyển sang “Chờ tạo chuyến hoàn”. Một đơn chỉ được duyệt một quyết định hoàn hàng.

### 5.3. Bác khiếu nại hoặc xác nhận không hoàn hàng

Bác:

```json
{
  "decision": "DISMISSED",
  "responsibleParty": "NONE",
  "reason": "Bằng chứng không xác nhận lỗi được khiếu nại.",
  "resolutionAction": "NONE"
}
```

Xác nhận nội dung khiếu nại nhưng không duyệt hoàn:

```json
{
  "decision": "CONFIRMED",
  "responsibleParty": "CARRIER",
  "reason": "Xác nhận sự cố được báo cáo; không quyết định hoàn hàng trong kết luận này.",
  "resolutionAction": "NONE"
}
```

Response dùng `ReportResponse`, tương ứng `DISMISSED/NONE` hoặc `CONFIRMED/NONE`. Không tự hiển thị “đã giải ngân” sau hai thao tác này: BE chỉ đưa escrow về `HELD` nếu không có hold khác; settlement là bước khác.

Popup kết luận phải nhắc quyết định không có API sửa/mở lại. Không gửi nhầm action `NONE` với kỳ vọng lần sau sẽ đổi sang `RETURN_AND_REFUND`; thiếu/null action cũng mặc định `NONE`. Retry quyết định chỉ hợp lệ khi toàn bộ decision, party, reason sau trim và action giữ nguyên.

### 5.4. Tạo chuyến hoàn

```json
{
  "requestId": "77777777-7777-4777-8777-777777777777",
  "leg": "BUYER_TO_SELLER",
  "reason": "Tạo chuyến hoàn theo quyết định tranh chấp đã duyệt.",
  "reportId": "11111111-1111-4111-8111-111111111111"
}
```

BE lấy điểm lấy hàng buyer, điểm trả seller, kiện hàng và loại xe từ snapshot đã lưu. FE không gửi `fromAddress`, `toAddress`, `serviceType`, số tiền hoặc chọn xe lại.

Response là envelope với `data` theo mẫu ShipmentResponse ở 4.3. Sau response, tải lại report để nhận `returnShipmentId` và tải danh sách shipments. Có `orderCode` mới là đã có mã carrier; HTTP 200 riêng lẻ không chứng minh đã gọi được tài xế.

Lưu `requestId` cùng nội dung thao tác trước khi gửi. Nếu timeout/rớt mạng, giữ nguyên key và body để retry sau khi tải lại trạng thái; không sinh UUID mỗi lần render/click. Không cho tạo chuyến mới khi đã có chuyến hoạt động hoặc chưa rõ kết quả. Chỉ dùng requestId mới sau khi chuyến trước đã `CANCELLED`, đã xác nhận trạng thái đó và đã xử lý nguyên nhân lỗi.

### 5.5. Sync, đối soát và hủy

Sync không body. Chỉ hữu ích khi có `orderCode`; BE đối chiếu carrier rồi cập nhật snapshot. Khi không có mã carrier, sync trả conflict và yêu cầu đối soát.

Đối soát request:

```json
{ "orderCode": "1234567890123456789" }
```

Áp dụng khi Lalamove shipment đang `CREATING` hoặc `CREATION_UNCERTAIN`. Nhân viên nhập mã chuyến **đã tồn tại** tra được từ Lalamove; BE kiểm tra reference/route. Không nhập `requestId` của FE hoặc mã đơn SecondLife. Format hiện tại: 1–50 ký tự, chữ/số/gạch dưới/gạch ngang. API này không tạo chuyến mới.

Hủy không body. Với chuyến Lalamove chưa bị hủy: BE yêu cầu có `orderCode` và `providerStatus` là `ASSIGNING_DRIVER` hoặc `ON_GOING`. Sau pickup không có nút ép hủy. Hủy thành công trả `status=CANCELLED`; không hoàn tiền tự động và không đổi quyết định tranh chấp. Có thể tạo chuyến thay thế bằng requestId mới sau khi hủy xác nhận.

### 5.6. Hoàn tiền

```http
POST /api/v1/shipments/66666666-6666-4666-8666-666666666666/refund
```

Body khuyến nghị để BE dùng bên chịu trách nhiệm đã chốt:

```json
{ "reason": "Carrier đã xác nhận giao hàng hoàn về seller; duyệt hoàn tiền hàng." }
```

Có thể gửi thêm `responsibleParty`, nhưng phải khớp report. Không hiển thị dropdown cho STAFF đổi bên chịu trách nhiệm tại bước này. `reason` bắt buộc, tối đa 2000 ký tự.

Response **vẫn là ShipmentResponse**: vận đơn thường giữ `status=DELIVERED`, `providerStatus=COMPLETED`; `reason` cập nhật theo lý do refund. Không có field `refundedAt` trong response shipment.

Sau HTTP 200, tải lại report. Khi thành công, các field trong `data` report có dạng:

```json
{
  "id": "11111111-1111-4111-8111-111111111111",
  "status": "CONFIRMED",
  "resolutionAction": "RETURN_AND_REFUND",
  "returnShipmentId": "66666666-6666-4666-8666-666666666666",
  "refundedAt": "2026-10-22T05:00:00Z",
  "refundedBy": "55555555-5555-4555-8555-555555555555",
  "refundAmount": 1000000
}
```

Đây là đoạn trích report. Hiển thị “Đã hoàn 1.000.000 ₫ vào ví buyer” từ audit này. BE đồng thời đưa Order về `CANCELLED`, escrow `REFUNDED`. Nếu request refund thành công nhưng GET report sau đó lỗi, hiển thị “Yêu cầu đã được BE xử lý; đang tải xác nhận” và khóa thao tác trong lúc tải lại, không diễn giải lỗi GET là refund thất bại rồi cho tạo hành động khác.

## 6. Điều kiện hiển thị và khóa nút

Quy ước: `canReview` và `canOperateReturn` theo phần 2; `mine = reviewerId === currentUser.id`; `selfCase = currentUser.id === reporterId || currentUser.id === sellerId`; `approved = status === CONFIRMED && resolutionAction === RETURN_AND_REFUND && returnApprovedAt != null`; `refunded = refundedAt != null`; `selectedReturn` theo 4.3.

Mọi nút ghi dữ liệu bị disable khi dữ liệu chưa tải xong, đang refresh sau xung đột hoặc đã có mutation khác đang chạy. Không dựa vào tab danh sách cũ để bật nút; dùng detail vừa tải.

| Nút | Điều kiện UI để hiện/bật | Khi không thỏa |
| --- | --- | --- |
| Xem hồ sơ/ảnh | `canReview` | Không mở màn hình riêng tư nếu thiếu quyền |
| Nhận xử lý | `canReview`, report `OPEN`, `!selfCase` | `IN_REVIEW` hiển thị reviewer; không có nút giành/chuyển người phụ trách |
| Bác khiếu nại | `canReview`, `IN_REVIEW`, `mine`, `!selfCase` | Chỉ đọc khi người khác phụ trách hoặc report đã kết luận |
| Xác nhận không hoàn hàng | Như bác; form `CONFIRMED/NONE`, party khác NONE | Không dùng làm bước tạm để sau đó duyệt hoàn |
| Duyệt hoàn hàng | `canReview && canOperateReturn`, `IN_REVIEW`, `mine`, `!selfCase`, report thuộc ORDER | Nếu có order status mới nhất là COMPLETED/CANCELLED thì khóa; BE còn kiểm tra FROZEN, thời điểm tạo report và quyết định khác cùng đơn |
| Tạo chuyến hoàn | `canOperateReturn`, `approved`, `!refunded`, chưa có `returnShipmentId`; không có buyer-return đang hoạt động/không rõ kết quả trong shipments | Không tạo khi link bị lệch hoặc chưa tải xong danh sách |
| Tạo chuyến thay thế | Như tạo chuyến, nhưng selectedReturn đã `CANCELLED`; không có buyer-return khác đang hoạt động | RequestId mới chỉ sau hủy xác nhận và sửa nguyên nhân |
| Đồng bộ trạng thái | Có shipment, `orderCode != null`; tài khoản được vận hành, chưa refund | Khóa khi thiếu mã; hiển thị đối soát cho trạng thái tạo chưa rõ |
| Đối soát chuyến | `canOperateReturn`, provider LALAMOVE, status CREATING/CREATION_UNCERTAIN, chưa refund | Không tạo chuyến thứ hai để thay việc đối soát |
| Theo dõi / xem timeline | Có selectedReturn; tracking nên có orderCode | Cho xem timeline trước khi có vị trí tài xế; không coi thiếu tọa độ là lỗi chuyến |
| Hủy chuyến hoàn | `canOperateReturn`, LALAMOVE, có orderCode, providerStatus ASSIGNING_DRIVER/ON_GOING, chưa refund và status chưa terminal | Khi PICKED/DELIVERED/FAILED hoặc chưa rõ booking thì không có thao tác ép hủy |
| Hoàn tiền | `canOperateReturn`, `approved`, `!refunded`, selectedReturn đúng order/leg/id và `status=DELIVERED` | Nếu biết order status COMPLETED/CANCELLED hoặc escrow khác FROZEN thì khóa; BE là nguồn kiểm tra cuối cho escrow hiện tại |
| Xem biên nhận hoàn tiền | `refunded` | Đọc amount, time, actor từ report; không cho sửa |

Hai điểm cần giữ đúng:

1. Người duyệt mới là người bị ràng buộc `mine`; không tự bắt tài khoản vận hành chuyến/refund phải là reviewer nếu tài khoản đó đã có quyền phù hợp.
2. ReportResponse không có `escrowStatus`; vì vậy FE hiện không thể đánh giá đầy đủ điều kiện FROZEN. Điều kiện refund ở bảng là điều kiện sơ bộ từ các field đang có, BE vẫn có thể từ chối 409. Không gán `escrowStatus='FROZEN'` giả chỉ vì report đã duyệt.

## 7. Trạng thái vận chuyển và nhãn giao diện

| `Shipment.status` | `providerStatus` Lalamove thường tương ứng | Nhãn FE / hành động |
| --- | --- | --- |
| `CREATING` | Có thể chưa có | Đang tạo; khóa tạo thêm, tải lại/đối soát nếu kéo dài |
| `CREATION_UNCERTAIN` | Có thể chưa có | Chưa xác định kết quả; đối soát mã đã tồn tại |
| `READY_TO_PICK` | `ASSIGNING_DRIVER` | Đang tìm tài xế; có thể hủy nếu có mã |
| `ASSIGNED` | `ON_GOING` | Tài xế đang đến lấy; có thể hủy nếu BE còn cho phép |
| `PICKED` | `PICKED_UP` | Đã lấy hàng hoàn; theo dõi, chưa refund |
| `DELIVERED` | `COMPLETED` | Hàng đã về seller; chờ STAFF hoàn tiền |
| `CANCELLED` | `CANCELED` hoặc bị từ chối tạo trước khi có mã | Đã hủy; đọc lastError, có thể chuẩn bị chuyến thay thế |
| `FAILED` | `REJECTED` / `EXPIRED` | Cần xử lý ngoại lệ/đồng bộ và hỗ trợ vận hành; chưa refund |

`FAILED` không tương đương `CANCELLED`. BE hiện chỉ cho thay returnShipmentId khi chuyến cũ đã CANCELLED; không có API ép chuyển FAILED thành CANCELLED hoặc bỏ link. Nếu carrier đã reject/expire mà endpoint hủy không còn cho phép, cần hỗ trợ BE/vận hành, không hiển thị một nút “Tạo lại” luôn thành công.

Với dữ liệu carrier/trạng thái cũ khác bảng: hiển thị trạng thái thô cùng nhãn trung tính và cho tải lại; không suy thành DELIVERED hoặc cho refund tự động. Luồng outbound `RETURNED` có refund riêng ở BE, nhưng màn hình tranh chấp này chỉ dùng linked `BUYER_TO_SELLER`; không trộn hai luồng.

## 8. Luồng gọi API, refresh và retry

Khi vào chi tiết:

1. GET report bằng route `reportId`, kiểm tra `targetType=ORDER` và có orderId.
2. GET `/orders/{orderId}/shipments` và tùy quyền GET `/orders/{orderId}/confirmation`.
3. Chọn return theo link; nếu có thì tải events. Tracking chỉ tải khi cần xem.
4. Tính trạng thái và nút từ bộ dữ liệu này. Nếu một API phụ lỗi, không coi dữ liệu đó là array rỗng để bật tạo chuyến.

Sau từng mutation:

| Mutation | Dữ liệu cần tải lại |
| --- | --- |
| start-review / resolve | Report detail và trang queue hiện tại; shipments nếu chuẩn bị bước hoàn |
| create / cancel / reconcile / sync | Report + shipments; events của shipment hiện được chọn |
| refund | Bắt buộc GET report để lấy audit; refresh shipments và queue; order confirmation nếu đang hiển thị |

Quy tắc retry:

- Resolve timeout: đọc lại report. Nếu đã kết luận cùng nội dung thì hiển thị kết quả; nếu còn IN_REVIEW có thể gửi lại cùng body. Không đổi kết luận ở lần retry.
- Booking timeout: tải lại report + shipments, giữ UUID requestId/body gốc. Có CREATION_UNCERTAIN thì đối soát, không tạo requestId mới. Nếu biết chắc lỗi xảy ra trước bước tạo booking và không có shipment mới thì vẫn có thể retry key gốc.
- Refund timeout: khóa nút, GET report. Có refundedAt thì kết thúc; chưa có audit sau khi refresh thì cho retry cùng shipment và body có kiểm soát. BE chống hoàn lặp.
- Request đồng thời nhận xử lý/hoàn tiền ở hai tab: tin trạng thái trả mới từ BE, hủy state/modal cũ và refresh. Không tự optimistic cập nhật số dư ví, audit hoặc trạng thái đóng đơn.

## 9. Lỗi API và trải nghiệm người dùng

Lỗi không có `data` thành công:

```json
{
  "success": false,
  "message": "Return refund requires frozen funds",
  "path": "/api/v1/shipments/66666666-6666-4666-8666-666666666666/refund",
  "errors": [],
  "timestamp": "2026-10-22T12:00:00"
}
```

| HTTP | Xử lý FE |
| --- | --- |
| 400 | Hiển thị lỗi field/reason/party/reportId; giữ form để sửa |
| 401 | Xử lý hết phiên theo luồng auth; không tự gửi lại mutation khi chưa xác minh kết quả trước đó |
| 403 | Ẩn/khóa hành động tương ứng, báo thiếu quyền hoặc sai reviewer; không tự retry |
| 404 | Report/shipment/order không tồn tại hoặc đã đổi; cho về danh sách/tải lại |
| 409 | Refresh report + shipment + order status nếu có; giải thích xung đột theo message. Không gộp mọi409 thành hết hạn48h |
| 429 | Dừng gọi dồn, giữ form, cho chờ/thử lại có kiểm soát; không tự vòng lặp retry |
| 502/503/504 hoặc rớt mạng | Giữ state thao tác, kiểm tra kết quả đã lưu trước khi retry, đặc biệt booking/refund |

Validation `errors` gồm `{field,message}`. Không log Bearer token, ảnh bằng chứng, thông tin liên hệ hoặc toàn bộ raw carrier payload vào console dùng chung.

Popup hành động nên có:

- **Duyệt hoàn:** trách nhiệm, lý do, nhắc chưa hoàn tiền ngay và kết luận không đổi bằng API retry.
- **Tạo chuyến:** xác nhận dùng địa chỉ snapshot, phí shipment là phí vận chuyển, có loading chống double-click.
- **Hủy chuyến:** nhắc chỉ hủy chuyến, chưa hoàn tiền và chưa kết thúc tranh chấp.
- **Hoàn tiền:** tiền hàng vào ví buyer, không cộng phí vận chuyển; lý do; nếu chưa đọc được số tiền thì không hiển thị con số ước lượng sai.

## 10. Dữ liệu BE hiện có và giới hạn cần biết

Tài liệu này không giả định có `GET /api/v1/orders/{id}` trả OrderResponseDTO: OrderController hiện không có endpoint đó.

API bổ sung dùng được:

```http
GET /api/v1/orders/{orderId}/confirmation
```

Trả `ApiResponse<OrderConfirmationResponse>`, có `orderId`, `postId`, `postTitle`, `buyerId`, `sellerId`, `status`, `confirmationStatus`, các mốc xác nhận của seller và điểm phạt snapshot. Có thể lấy tên bài/UUID các bên và trạng thái đơn mới nhất. **Không có finalPrice, escrowStatus, shippingDeliveredAt hoặc shippingHold**. `sellerConfirmedAt` là seller xác nhận chuẩn bị đơn; không phải buyer xác nhận nhận hàng.

```http
GET /api/v1/orders/{orderId}/commission
```

Tùy chọn khi có quyền đọc tài chính nêu ở phần2. Trả `ApiResponse<OrderCommissionResponse>`; `data.commissionBase` là tiền hàng snapshot có thể dùng để hiển thị số tiền dự kiến hoàn. `sellerPayout` và `platformCommission` là số tính theo policy, không phải tiền hoàn hoặc bằng chứng đã giải ngân. Không gọi endpoint này bắt buộc cho mọi STAFF hoặc nới role chỉ để form không lỗi403. Khi chưa có snapshot, BE có thể trả lỗi nghiệp vụ; không suy ra từ giá listing hiện tại.

`GET /api/v1/staff/orders` hiện phục vụ theo dõi xác nhận đơn, không phải API chi tiết tài chính hoặc queue refund. `GET /api/v1/orders/buyer` và `/seller` trả đơn của chính tài khoản đăng nhập, không phải công cụ STAFF đọc đơn người khác.

Vì vậy, bản FE hiện làm được luồng report + shipment + audit hoàn tiền. Nếu yêu cầu sản phẩm bắt buộc STAFF thấy chính xác tiền/escrow/hạn giao trên cùng một lần tải và tính toàn bộ `allowedActions` trước khi bấm, cần bổ sung API read-only dành riêng cho STAFF, ví dụ chi tiết tranh chấp tổng hợp. Đây là đề xuất bổ sung, **chưa phải endpoint đã triển khai**; tối thiểu nên trả `finalPrice`, `escrowStatus`, `shippingDeliveredAt`, `disputeDeadline`, `shippingHold` và khả năng thao tác do BE tính. Không mock các field này thành giá trị thật trong FE.

## 11. Checklist nghiệm thu FE

- Tài khoản đúng quyền mở được danh sách ORDER, page0/items đúng contract; người thiếu quyền bị chặn.
- Người khác đã nhận report: chỉ đọc, không có nút kết luận; selfCase không nhận/xét duyệt được.
- Report hợp lệ mở trước48h vẫn xử lý được sau48h, không dùng countdown STAFF để chặn duyệt.
- Duyệt thành công hiển thị chờ tạo chuyến; không báo đã refund.
- Tạo chuyến gửi đúng reportId, leg và requestId; giữ key/body khi timeout/double-click.
- HTTP200 với CANCELLED/CREATION_UNCERTAIN hiển thị đúng lỗi, không toast “tạo tài xế thành công”.
- Chọn shipment theo returnShipmentId; shipment giao đi DELIVERED không bật refund.
- Không refund khi chuyến mới READY_TO_PICK/ASSIGNED/PICKED/FAILED hoặc link chưa khớp.
- Hủy chỉ khi carrier còn cho phép; thay chuyến dùng UUID mới sau CANCELLED.
- Hoàn tiền200 xong tải report, hiển thị đúng refundedAt/refundAmount/refundedBy; không chờ report REFUNDED.
- Retry refund không cộng tiền UI lần nữa; refresh trang vẫn giữ trạng thái hoàn tiền từ server.
- Bác report không hiển thị đã giải ngân vì có thể còn report/POD/shipping hold khác.
- Evidence fetch có Bearer, blob URL được thu hồi; token không nằm trong URL/log.
- Lỗi403/409/429/5xx có trạng thái giữ form, tải lại và retry đúng từng thao tác.
- Không gửi/đọc các endpoint, filter hoặc field tài chính chưa tồn tại.

## 12. Nguồn đối chiếu trong repository

- `controller/StaffReportController.java`, `controller/UserReportController.java`, `controller/ReportEvidenceController.java`.
- `controller/ShipmentController.java`, `controller/OrderConfirmationController.java`, `controller/OrderSettlementController.java`.
- `dto/response/ReportResponse.java`, `ShipmentResponse.java`, `ShipmentEventResponse.java`, `OrderConfirmationResponse.java`.
- `common/ApiResponse.java`, `common/PageResponse.java`, `common/ErrorResponse.java`.
- `service/impl/UserReportServiceImpl.java`, `OrderDisputeServiceImpl.java`, `ShipmentServiceImpl.java`.
- `repository/UserReportRepository.java`: filter queue và thứ tự phân trang thực tế.
