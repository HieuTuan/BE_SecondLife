# Test Swagger: Staff xử lý tranh chấp, hoàn hàng và hoàn tiền

## 1. Sắp xếp danh sách

GET /api/v1/reports/me?targetType=ORDER&page=0&size=20 dùng JWT buyer để lấy report của chính mình.
GET /api/v1/staff/reports?targetType=ORDER&status=OPEN&page=0&size=20 dùng JWT staff.

Hai danh sách cố định mới nhất trước: created_at DESC, id DESC (ID ổn định thứ tự khi trùng thời gian). Không gửi sort. sort=["string"] là placeholder Swagger sinh từ Pageable, không phải field nghiệp vụ. Swagger chỉ hiển thị page/size và bộ lọc; request cũ có sort vẫn được tiếp nhận và không đổi thứ tự. page bắt đầu0, size theo giới hạn pagination cấu hình.

## 2. Tài khoản và dữ liệu

- BUYER: chủ đơn, có PROFILE_READ_SELF và MEDIA_UPLOAD_SELF nếu upload bằng chứng.
- SELLER: seller của đơn, MEDIA_UPLOAD_SELF nếu upload ảnh/video đóng gói.
- STAFF A: ROLE_STAFF + STAFF_FRAUD_REVIEW + STAFF_DISPUTE_REVIEW; không được là buyer/seller của đơn.
- Chuẩn bị orderId có status DELIVERED, shippingDeliveredAt do hãng xác nhận, escrow HELD hoặc FROZEN; chưa đủ48giờ và buyer chưa xác nhận nhận hàng. Đúng mốc48h cũng bị từ chối.
- Không gọi PUT /api/v1/orders/{id}/delivered để giả lập carrier delivery: API này là buyer xác nhận và có thể giải ngân tiền.
- Chuẩn bị Cloudinary cho bằng chứng video, Lalamove cho vận đơn hoàn; đồng hồ BE phải chính xác. Tất cả ORDER_ID_THAT/REPORT_ID_THAT/EVIDENCE_ID_THAT phải thay bằng UUID thực. Các UUID requestId mẫu phải thay khi tạo test case độc lập mới.

Mở http://localhost:8080/swagger-ui/index.html. API nào cũng Try it out -> nhập tham số/JSON -> Execute. Login mỗi tài khoản, copy data.accessToken vào Authorize, KHÔNG tự thêm Bearer prefix. Chỉ dùng một token ở mỗi lượt; ghi rõ đang test vai trò nào.

POST /api/auth/login:
```json
{"email":"buyer@example.com","password":"MAT_KHAU_THAT"}
```
Dùng email/password thực của buyer/staff/seller khi đổi tài khoản.

## 3. Buyer lưu số dư và upload bằng chứng

GET /api/v1/wallets/me trả DTO trực tiếp; lưu balance (không phải data.balance) để đối chiếu tiền sau hoàn.

Ảnh: POST /api/v1/reports/evidence -> chọn file multipart, lấy data.evidenceUrl.
Video: POST /api/video/upload-signature -> FE/browser upload chunks trực tiếp Cloudinary -> POST /api/video/complete -> lấy data.evidenceUrl. Hướng dẫn đầy đủ: [FE_CHUNKED_VIDEO_EVIDENCE_GUIDE.md](FE_CHUNKED_VIDEO_EVIDENCE_GUIDE.md). Swagger BE không tự chia/gửi các chunk tới provider. Không dùng CDN URL thẳng vào evidenceUrls.

Để chỉ test trạng thái/tiền có thể dùng evidenceUrls: []; khi test staff xem bằng chứng phải upload thật rồi thay URL vào request. Video upload không gia hạn cửa sổ mở khiếu nại.

## 4. Buyer mở khiếu nại

POST /api/v1/reports:
```json
{
  "requestId":"11111111-1111-4111-8111-111111111111",
  "orderId":"ORDER_ID_THAT",
  "reasonCode":"MISLEADING_DESCRIPTION",
  "description":"Hàng nhận bị nứt màn hình, khác mô tả. Đề nghị kiểm tra bằng chứng và hoàn hàng, hoàn tiền.",
  "evidenceUrls":["/api/v1/reports/evidence/EVIDENCE_ID_THAT"]
}
```
201: data.id là reportId; status OPEN, resolutionAction NONE. Tiền chuyển sang FROZEN cùng transaction.

GET /api/v1/reports/me?targetType=ORDER&page=0&size=20: dữ liệu tại data.items; mới nhất trước. GET /api/v1/reports/{id}: điền reportId.

Retry đúng requestId/body trả lại report cũ; sửa nội dung với cùng requestId409. Tạo yêu cầu khác dùng UUID mới. Request cũ hợp lệ có thể replay sau hạn48h, nhưng không tạo được khiếu nại mới sau hạn.

## 5. Seller phản hồi bằng chứng (trước khi staff chốt)

Đổi sang token seller. Upload ảnh hoặc upload video/complete bằng chính tài khoản seller để có evidenceUrl của seller. Seller phải biết đúng reportId; không dùng evidence của buyer.

POST /api/v1/reports/{reportId}/responses:
```json
{
  "requestId":"22222222-2222-4222-8222-222222222222",
  "description":"Video tình trạng sản phẩm và đóng gói trước khi bàn giao cho hãng vận chuyển.",
  "evidenceUrls":["/api/v1/reports/evidence/EVIDENCE_ID_CUA_SELLER"]
}
```
201, data.authorRole SELLER. Chỉ thêm khi report OPEN/IN_REVIEW. Buyer cũng dùng API này để bổ sung bằng chứng của chính mình.

GET /api/v1/reports/{reportId}/responses?page=0&size=20: seller/buyer chỉ thấy responses của mình. Staff có quyền review sẽ thấy cả hai bên. API này không mở quyền seller đọc report chi tiết hoặc ảnh/video riêng của buyer.

## 6. Staff tiếp nhận và đối chiếu bằng chứng

Đổi Authorize sang JWT STAFF A.
GET /api/v1/staff/reports?targetType=ORDER&status=OPEN&page=0&size=20 -> data.items.
GET /api/v1/reports/{id} -> nội dung và evidenceUrls ban đầu của buyer.
GET /api/v1/reports/{reportId}/responses?page=0&size=20 -> bằng chứng bổ sung và phản hồi seller.

POST /api/v1/staff/reports/{id}/start-review không body. 200: status IN_REVIEW, reviewerId là STAFF A. Chỉ reviewer đã nhận mới được resolve, kể cả khi đổi sang admin.

Mỗi bằng chứng: GET /api/v1/reports/evidence/{evidenceId}/metadata bằng JWT staff:
- IMAGE: fetch contentUrl kèm token để xem ảnh.
- VIDEO: GET contentUrl (API /api/video/{uploadId}/playback), lấy data.url để mở video trên Cloudinary. URL xem hết hạn thì xin lại.
Staff không xem được upload chưa gắn report. Có bằng chứng thì đối chiếu trước khi chốt; API hiện không ép staff phải bấm xem từng file.

## 7. Staff duyệt hoàn hàng

POST /api/v1/staff/reports/{id}/resolve:
```json
{
  "decision":"CONFIRMED",
  "responsibleParty":"SELLER",
  "reason":"Đã đối chiếu video mở hàng và bằng chứng đóng gói, xác nhận sản phẩm không đúng mô tả. Chấp thuận hoàn hàng và hoàn tiền.",
  "resolutionAction":"RETURN_AND_REFUND"
}
```
200: status CONFIRMED, resolutionAction RETURN_AND_REFUND, returnApprovedAt có giá trị. Đây mới là duyệt quyết định: chưa tạo vận đơn, chưa hoàn tiền. Report mở trong hạn vẫn được staff xử lý sau48h.

## 8. Staff tạo vận đơn hoàn

POST /api/v1/orders/{orderId}/shipments:
```json
{
  "requestId":"33333333-3333-4333-8333-333333333333",
  "leg":"BUYER_TO_SELLER",
  "reason":"Tạo chuyến hoàn theo quyết định tranh chấp đã được duyệt.",
  "reportId":"REPORT_ID_THAT"
}
```
200 trả ShipmentResponse; lưu data.shipmentId là returnShipmentId. Kiểm tra leg BUYER_TO_SELLER, status, orderCode, lastError. HTTP200 không bảo đảm hãng đã nhận booking nếu status CREATION_UNCERTAIN/CANCELLED/FAILED.

GET /api/v1/reports/{id}: data.returnShipmentId phải đúng vận đơn vừa tạo. BE dùng dữ liệu địa chỉ/kiện hàng từ đơn/quote đã thanh toán; không nhập lại địa chỉ tùy ý.

Giữ nguyên requestId/body để retry sau timeout. Không tạo UUID mới liên tục. Nếu CREATION_UNCERTAIN và đã tìm được mã đơn carrier, STAFF có thể POST /api/v1/shipments/{shipmentId}/reconcile-creation:
```json
{"orderCode":"MA_DON_THAT_BEN_LALAMOVE"}
```
Chỉ đối soát booking đã có, không tạo đơn carrier mới.

## 9. Test chặn hoàn tiền sớm

Khi vận đơn hoàn chưa DELIVERED, POST /api/v1/shipments/{shipmentId}/refund, shipmentId là returnShipmentId:
```json
{"reason":"Kiểm tra hệ thống không hoàn tiền khi hàng chưa hoàn về seller."}
```
409: Product must be physically returned before refund. Buyer không được cộng tiền; report chưa có refundedAt.

## 10. Đồng bộ giao hàng hoàn

Sau khi hãng/Sandbox thực sự ghi nhận giao hàng hoàn cho seller, POST /api/v1/shipments/{shipmentId}/sync không body. Vận đơn cần orderCode để sync.

GET /api/v1/orders/{orderId}/shipments -> data là array. Chọn đúng returnShipmentId với leg BUYER_TO_SELLER và status DELIVERED; không nhầm outbound SELLER_TO_BUYER đã DELIVERED.
GET /api/v1/shipments/{shipmentId}/events xem timeline.

Không có API Swagger ép DELIVERED. /sync chỉ đọc trạng thái thật của hãng. Khi chưa có giao hoàn thật thì dừng ở ca chặn bước9, chưa thể test hoàn tiền thành công.

## 11. Staff hoàn tiền

POST /api/v1/shipments/{shipmentId}/refund:
```json
{"reason":"Hãng đã xác nhận hàng hoàn về seller. Duyệt hoàn toàn bộ tiền hàng vào ví buyer."}
```
200 vẫn trả ShipmentResponse. Có thể bỏ responsibleParty để lấy quyết định đã duyệt; nếu gửi phải khớp quyết định đó. BE hoàn finalPrice (tiền hàng), không nhận amount tùy ý, không gồm phí vận chuyển trong khoản hoàn sản phẩm.

GET /api/v1/reports/{id}: status vẫn CONFIRMED, action RETURN_AND_REFUND, returnShipmentId đúng; refundedAt/refundedBy/refundAmount có giá trị. Nếu kiểm tra DB: order CANCELLED, escrow REFUNDED. Không có trạng thái report REFUNDED.

Đổi token buyer, GET /api/v1/wallets/me: balance tăng đúng refundAmount khi không có giao dịch khác. GET /api/v1/wallets/me/transactions?page=0&size=20 trả danh sách ở items (không phải data.items) để đối chiếu giao dịch.

Đổi lại staff, retry đúng refund request:200, audit giữ nguyên, ví và ledger không được cộng lầnhai. GET /orders/{id}/settlement không phải API bằng chứng hoàn tiền; luồng refund không tạo commission settlement cho seller.

## 12. Nhánh bác khiếu nại

Dùng report riêng -> start-review -> resolve:
```json
{
  "decision":"DISMISSED",
  "responsibleParty":"NONE",
  "reason":"Đã xem bằng chứng, chưa đủ cơ sở xác nhận nội dung khiếu nại.",
  "resolutionAction":"NONE"
}
```
200: DISMISSED/NONE, không hoàn tiền. Chỉ gỡ freeze về HELD khi không còn hold khác; không tự giải ngân ngay. Report đã chốt không thể đổi quyết định qua retry.

## 13. Checklist ca lỗi

| Ca | Mong đợi |
|---|---|
| Không JWT |401|
| Buyer khác tạo khiếu nại đơn |403|
| Chưa giao, đủ/quá48h, đã xác nhận nhận hàng |409|
| Staff thiếu quyền |403|
| Staff khác resolve report được giao |403|
| Chưa duyệt hoàn hàng đã tạo return |409|
| BUYER_TO_SELLER thiếu reportId |400|
| Report sai đơn |409|
| Return chưa DELIVERED đã refund |409|
| Refund trách nhiệm khác quyết định |409|
| Bằng chứng không thuộc người gửi |403|
| Thêm response mới sau chốt |409|
| Retry cùng requestId khác nội dung |409|
| Retry refund thành công |200, không cộng tiền lầnhai|

Các bước gây thay đổi trạng thái dùng đơn/report riêng cho từng ca. Biên đúng48h nên kiểm tra tự động bằng clock kiểm soát thay vì chỉnh giờ máy thật (sẽ làm hỏng chữ ký provider).
