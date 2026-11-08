# FE — Checklist theo Item, cấp độ và biểu phí kiểm định

Cập nhật: 05/11/2026. Tài liệu mô tả phần BE bổ sung; FE chưa được sửa trong đợt này.
Base URL khi chạy local: `http://localhost:8080`. API nghiệp vụ bên dưới yêu cầu JWT hợp lệ qua `Authorization: Bearer <accessToken>`.

Bổ sung 06/11/2026: API Admin có thêm giá theo Item/cấp/trung tâm và bộ lọc/tìm kiếm. Xem [hướng dẫn giá và tìm kiếm Admin](FE_ADMIN_INSPECTION_PRICING_SEARCH_GUIDE.md).

## 1. Mô hình áp dụng

```text
Danh mục phòng → Item cụ thể → checklist + cấp L1–L4 được Admin duyệt
                                      ↓
Trung tâm đã chọn + bảng giá L1–L4 được Admin duyệt
                                      ↓
Báo giá → tạo đơn → lưu cố định phiên bản checklist, giá và tỷ lệ nền tảng
```

- Danh mục như “Bếp & Phòng ăn” dùng phân loại sản phẩm. Không dùng một checklist duy nhất cho tất cả sản phẩm trong phòng.
- Trung tâm soạn checklist cho từng Item, đề xuất cấp độ phù hợp và gửi Admin duyệt.
- Mỗi Item có tối đa một checklist APPROVED đang active trên toàn hệ thống. Các trung tâm áp dụng cùng checklist/cấp độ đã duyệt; mỗi trung tâm có bảng giá L1–L4 riêng.
- L1–L4 là mã cấp độ, không phải bốn checklist dùng chung. Ví dụ nồi cơm cơ và nồi cơm điện tử có hai checklist riêng.
- BE không tự gán “nồi cơm = L2”, không tạo giá mẫu để bán thật. Trung tâm và Admin phải cấu hình dữ liệu thực tế.
- Mức giá và cấp trong JSON minh họa dưới đây chỉ dùng test. UUID phải thay bằng ID thật lấy từ API.

## 2. Catalog đã tách và dữ liệu cũ

GET `/api/v1/items` và các API danh sách Item chỉ trả Item còn được chọn (`selectable=true`).

Các tên gộp được tách nếu tồn tại trong DB:

| Item gốc | Item cụ thể |
| --- | --- |
| Nồi cơm điện | Nồi cơm cơ; Nồi cơm điện tử / Đa chức năng |
| Bếp / Nồi cơm điện | Bếp từ; Bếp hồng ngoại; hai loại nồi cơm ở trên |
| Máy giặt | Máy giặt thông thường; Máy giặt tích hợp sấy |
| Máy sấy | Máy sấy điện trở; Máy sấy bơm nhiệt |
| Giàn phơi thông minh | Giàn phơi quay tay; Giàn phơi điều khiển điện |
| Máy lọc nước gia đình | Bộ lọc nước không điện; Máy lọc nước RO; Máy lọc nước nóng lạnh |
| Máy hút bụi | Máy hút bụi thông thường; Robot hút bụi / Lau nhà |
| Lò vi sóng / Lò nướng | Lò vi sóng; Lò nướng |
| Bếp từ / Bếp hồng ngoại | Bếp từ; Bếp hồng ngoại |
| Bình đun siêu tốc / Bình thủy điện | Bình đun siêu tốc; Bình thủy điện |
| Tủ / Kệ phòng tắm | Tủ phòng tắm; Kệ phòng tắm |

Migration cũng nhận diện một số tên seed tiếng Anh tương ứng. Giữ Item đích nếu đã có cùng tên trong cùng danh mục; không tạo bản sao. Không tự tách mọi Item khác, không xóa danh mục tùy chỉnh.

Response Item giữ các field cũ và thêm:
- `selectable`: còn được chọn cho bài/yêu cầu mới.
- `replacementForItemId`: Item gốc của biến thể mới; có thể null nếu tận dụng Item đích đã tồn tại.
- `variantCode`: mã biến thể; có thể null.

Item gốc giữ nguyên ID, được chuyển `selectable=false`. Bài cũ, ảnh và đơn lịch sử không bị tự đổi sang một biến thể suy đoán.

Seller chọn lại Item cho bài cũ bằng:
`PATCH /api/v1/posts/my-posts/{postId}/item`

```json
{
  "itemId": "22222222-2222-4222-8222-222222222222"
}
```

Trả PostDto trực tiếp. Chỉ chủ bài được sửa, chỉ khi Item cũ đã bị ngừng chọn hoặc chưa xác định, Item mới cùng danh mục và bài chưa vướng giao dịch/kiểm định đang xử lý. Trạng thái được hỗ trợ: DRAFT, ACTIVE, REJECTED, PENDING. Gửi lại đúng Item đã chọn được xử lý an toàn. FE hiển thị thông báo 409 nếu bài chưa đủ điều kiện.

“Mục Khác” vẫn là danh mục chứa các Item cụ thể hiện có, không phải lý do để BE tự chọn L1. Item chưa có checklist/cấp được duyệt sẽ chưa lấy được báo giá kiểm định mới.

## 3. Quyền và hình dạng response

| API | Quyền chính | Hình dạng thành công |
| --- | --- | --- |
| GET /api/v1/inspector-center/checklists | INSPECTION_CHECKLIST_MANAGE; tài khoản trung tâm | List trực tiếp |
| GET /api/v1/inspector-center/checklists/{id} | như trên; kiểm tra quyền xem đề xuất | Checklist trực tiếp |
| POST /api/v1/inspector-center/checklists | như trên | Checklist trực tiếp, HTTP 200 |
| PUT /api/v1/inspector-center/checklists/{id} | như trên; tác giả đề xuất | Checklist phiên bản mới trực tiếp, HTTP 200 |
| DELETE /api/v1/inspector-center/checklists/{id} | như trên; tác giả | HTTP 204 |
| GET /api/v1/inspector-center/checklists/proposals | INSPECTION_CENTER + INSPECTION_CHECKLIST_MANAGE | ApiResponse, danh sách ở data.items |
| GET /api/v1/admin/inspection/checklist-proposals | ADMIN + INSPECTION_CHECKLIST_MANAGE | ApiResponse, data.items |
| GET /api/v1/admin/inspection/checklist-proposals/{id} | như trên | ApiResponse, data |
| POST /api/v1/admin/inspection/checklist-proposals/{id}/review | như trên | ApiResponse, data |
| POST /api/v1/inspector-center/level-fees/propose | INSPECTION_CENTER + INSPECTION_FEE_MANAGE | FeeProposal trực tiếp, HTTP 200 |
| GET /api/v1/inspector-center/level-fees | như trên | List trực tiếp |
| GET /api/v1/inspector-center/level-fees/proposals | INSPECTION_FEE_MANAGE; tài khoản trung tâm | Spring Page trực tiếp, danh sách ở content |
| GET /api/v1/admin/inspection/fee-proposals | ADMIN + INSPECTION_FEE_MANAGE | ApiResponse, data.items |
| POST /api/v1/admin/inspection/fee-proposals/{id}/review | như trên | ApiResponse, data |
| GET /api/v1/inspection/centers | Đã đăng nhập | ApiResponse, data là List |
| POST /api/v1/inspection/quote | Đã đăng nhập | Quote trực tiếp |
| POST /api/v1/inspection/requests | Đã đăng nhập; kiểm tra buyer/bài tại service | Order trực tiếp |
| GET /api/v1/inspector/orders/{id}/checklist | Đã đăng nhập; kiểm tra người tham gia đơn | Checklist trực tiếp |

Danh sách checklist của trung tâm gồm checklist được duyệt đang áp dụng toàn hệ thống và các phiên bản chính trung tâm đã gửi. Đề xuất chưa duyệt của trung tâm khác không được xem/sửa/rút.

Danh sách có phân trang hỗ trợ `page=0&size=20&sort=createdAt,desc`; không gửi sort=["string"]. Sort không được hỗ trợ trả 400.

Status checklist: LEGACY, PENDING_APPROVAL, APPROVED, REJECTED, WITHDRAWN.
Status đề xuất giá: PENDING_APPROVAL, APPROVED, REJECTED.

## 4. Trung tâm gửi checklist và độ phức tạp

POST `/api/v1/inspector-center/checklists`

```json
{
  "itemId": "22222222-2222-4222-8222-222222222222",
  "complexityLevel": "L2",
  "title": "Kiểm định nồi cơm cơ",
  "description": "Đề xuất L2: kiểm tra gia nhiệt, chuyển chế độ nấu/giữ ấm và an toàn điện.",
  "items": [
    {
      "itemTitle": "Kiểm tra an toàn điện",
      "criteriaDescription": "Dây dẫn nguyên vẹn, không hở điện và không có dấu hiệu chập cháy.",
      "mandatory": true,
      "orderIndex": 1
    },
    {
      "itemTitle": "Kiểm tra nấu và giữ ấm",
      "criteriaDescription": "Chạy thử theo quy trình an toàn; kiểm tra chuyển chế độ.",
      "mandatory": true,
      "orderIndex": 2
    }
  ]
}
```

`categoryId` có thể bỏ; BE suy ra từ Item. Nếu gửi thì phải khớp danh mục của Item.
Không gửi createdById, reviewedById, approvalStatus, active hoặc giá trong request này.

Validation:
- Title bắt buộc, tối đa 200 ký tự; description tối đa 10.000.
- Item cụ thể còn được chọn; complexityLevel thuộc L1–L4.
- Từ 1–100 tiêu chí; ít nhất một tiêu chí mandatory.
- Tên tiêu chí bắt buộc, tối đa 255 ký tự, không trùng sau khi so sánh không phân biệt hoa/thường và khoảng trắng đầu/cuối.
- criteriaDescription tối đa 4.000 ký tự.
- orderIndex dương và không trùng; 0/null để BE đánh số theo vị trí.

Response trả đủ field cũ và thêm `itemId, itemName, complexityLevel, approvalStatus, revision, supersedesId, createdById, reviewedById, reviewedAt, rejectReason`.
Ngay sau khi gửi: `approvalStatus=PENDING_APPROVAL`, `active=false`. Ghi lại `id` là checklistProposalId.

## 5. Admin duyệt và phiên bản checklist

GET `/api/v1/admin/inspection/checklist-proposals?status=PENDING_APPROVAL&page=0&size=20&sort=createdAt,desc`.

Admin đối chiếu Item, tiêu chí, mô tả lý do chọn cấp, rồi:
POST `/api/v1/admin/inspection/checklist-proposals/{checklistProposalId}/review`

Duyệt:
```json
{
  "approved": true
}
```

Từ chối:
```json
{
  "approved": false,
  "reason": "Cần bổ sung tiêu chí kiểm tra chế độ giữ ấm."
}
```

Lý do từ chối bắt buộc, tối đa 2.000 ký tự. Tác giả không được tự duyệt kể cả có thêm role ADMIN.

Khi duyệt thành công:
- Phiên bản mới APPROVED, active=true.
- Phiên bản được thay thế vẫn giữ lịch sử APPROVED nhưng active=false.
- Đơn đã tạo giữ snapshot cũ.
- Hai đề xuất cạnh tranh thay cùng phiên bản: chỉ một được kích hoạt; đề xuất còn lại trả 409, cần tạo lại dựa trên phiên bản hiện hành.
- Gửi review lần hai trả 409.

PUT dùng cùng JSON như POST nhưng tạo **phiên bản mới, ID mới**, không sửa tại chỗ checklist đã chốt. Nếu sửa đề xuất đang pending, bản pending cũ được WITHDRAWN. FE phải dùng ID trả về, tải lại danh sách.
DELETE là rút đề xuất, không xóa vật lý. Không cho xóa checklist APPROVED/LEGACY; trường hợp này trả 409.

Nút FE:
| Trạng thái/quyền | Thao tác |
| --- | --- |
| Trung tâm là tác giả, pending/rejected | Sửa và gửi phiên bản mới; rút đề xuất |
| Trung tâm là tác giả, APPROVED | Tạo phiên bản đề xuất mới; không xóa bản đang áp dụng |
| Trung tâm xem checklist được duyệt của nơi khác | Xem; có thể tạo đề xuất riêng qua POST cho cùng Item, không PUT bản của nơi khác |
| Admin, PENDING_APPROVAL, không phải tác giả | Duyệt / Từ chối |
| Đã review hoặc WITHDRAWN | Xem lịch sử; không review lại |

## 6. Trung tâm đề xuất bảng giá bốn cấp

POST `/api/v1/inspector-center/level-fees/propose`

```json
{
  "complexityLevel": "L2",
  "baseInspectionFee": 50000
}
```

Gửi riêng từng cấp muốn cung cấp. Mức phí không âm, tối đa 16 chữ số nguyên và 2 chữ số thập phân. Giá 0 chỉ hợp lệ khi được đề xuất và Admin duyệt rõ ràng.
Không gửi categoryId, tiers hay tự tính discount/platform fee trong API mới này.

Trả FeeProposal trực tiếp; lưu `id` thành feeProposalId. Trước khi duyệt, giá cũ vẫn có hiệu lực.
Admin xem `GET /api/v1/admin/inspection/fee-proposals?status=PENDING_APPROVAL&page=0&size=20&sort=createdAt,desc`.
Admin duyệt bằng `POST /api/v1/admin/inspection/fee-proposals/{feeProposalId}/review`, body giống phần 5.

GET `/api/v1/inspector-center/level-fees` trả tối đa bốn mức giá **đã được duyệt gần nhất của chính trung tâm**, mỗi cấp một bản. Các bản APPROVED cũ trong lịch sử không đồng nghĩa vẫn là giá hiện hành.
Cấp chưa có giá hiển thị “Chưa có giá được duyệt”; không tự thay bằng 0.

API cũ `/travel-tiers/propose` và lịch sử đề xuất theo category vẫn giữ để tương thích. Giá cơ sở của đơn mới lấy theo Item/cấp/trung tâm đã duyệt; không lấy giá category cũ làm fallback.

## 7. Chuẩn bị địa chỉ và lấy báo giá

Trung tâm cập nhật địa chỉ + GPS trong hồ sơ hiện có: PATCH `/api/v1/users/me`.
Ví dụ JSON (thay địa chỉ/GPS bằng địa điểm thật):

```json
{
  "province": "Thành phố Hồ Chí Minh",
  "streetAddress": "Địa chỉ trung tâm kiểm định thực tế",
  "latitude": 10.7300,
  "longitude": 106.7050
}
```

Seller cấu hình địa chỉ lấy hàng qua `PUT /api/v1/shipping/pickup-address` theo schema ShippingAddressRequest hiện có. Tọa độ trung tâm phải hữu hạn, latitude trong [-90,90], longitude trong [-180,180].
Không có GPS hợp lệ hoặc seller chưa có địa chỉ lấy hàng: không tạo báo giá bằng tọa độ suy đoán.

FE lấy `GET /api/v1/inspection/centers`, hiển thị các `id,name`; chủ động gửi trung tâm người dùng chọn.
Danh sách này chỉ xác nhận trung tâm đang hoạt động; không bảo đảm trung tâm đã có đầy đủ giá/checklist/GPS.

POST `/api/v1/inspection/quote`

```json
{
  "postId": "33333333-3333-4333-8333-333333333333",
  "inspectionCenterId": "11111111-1111-4111-8111-111111111111"
}
```

Nếu không truyền centerId, BE ưu tiên trung tâm đã gửi checklist được duyệt nếu còn hoạt động; nếu không thì chỉ tự chọn khi có duy nhất một trung tâm đang hoạt động. Trường hợp còn lại yêu cầu chọn trung tâm rõ ràng.

Response giữ các field báo giá cũ và thêm `inspectionConfiguration`. Ví dụ **các field chính**, không phải toàn bộ response:

```json
{
  "postId": "33333333-3333-4333-8333-333333333333",
  "baseInspectionFee": 50000,
  "shippingToCenterFee": 50000,
  "returnShippingReserveFee": 50000,
  "atCenterTotalFee": 150000,
  "inspectionConfiguration": {
    "inspectionCenterId": "11111111-1111-4111-8111-111111111111",
    "checklistTemplateId": "44444444-4444-4444-8444-444444444444",
    "complexityLevel": "L2",
    "feeProposalId": "55555555-5555-4555-8555-555555555555",
    "inspectionFee": 50000,
    "platformFeeRate": 0.1,
    "checklist": {
      "id": "44444444-4444-4444-8444-444444444444",
      "itemId": "22222222-2222-4222-8222-222222222222",
      "title": "Kiểm định nồi cơm cơ",
      "complexityLevel": "L2",
      "approvalStatus": "APPROVED",
      "active": true,
      "revision": 1,
      "items": [
        {
          "id": "66666666-6666-4666-8666-666666666666",
          "itemTitle": "Kiểm tra an toàn điện",
          "mandatory": true,
          "orderIndex": 1
        }
      ]
    }
  }
}
```

Ví dụ phí 50.000/chiều chỉ đúng khi môi trường cấu hình như vậy. Rate 0.1 chỉ minh họa; môi trường mặc định có thể là 0.

### Công thức và giới hạn hiện tại

- Phí cơ sở = giá được Admin duyệt của trung tâm cho cấp độ trong checklist được duyệt của Item.
- AT_HOME = phí cơ sở + phí di chuyển khứ hồi.
- AT_CENTER = phí cơ sở + phí chuyển tới trung tâm + dự phòng phí chuyển về.
- BE tính khoản chia phí theo BUYER_PAYS/SELLER_PAYS/SPLIT_50_50; FE dùng số trả về, không tự làm tròn khác BE.
- Tọa độ AT_HOME đã dùng trung tâm được chọn và địa chỉ seller. Khoảng cách vẫn là **ước tính Haversine × 1,25, tối thiểu 1 km**, áp dụng bảng bậc di chuyển hiện có; chưa phải tuyến đường thực tế từ routing provider.
- **Cập nhật: phí chuyển AT_CENTER và dự phòng hoàn về lấy quotation Lalamove hai chiều theo hồ sơ trung tâm.** FE đọc `FE_INSPECTION_LALAMOVE_QUOTE_GUIDE.md`, dùng `shippingQuote.id` khi tạo yêu cầu và xử lý hạn báo giá; không dùng phí cấu hình cố định.
- `INSPECTION_FEE` cũ không thay thế được bảng giá L1–L4 của đơn mới khi thiếu cấu hình.
- `INSPECTION_PLATFORM_FEE_RATE` là tỷ lệ 0–1, tối đa 6 chữ số thập phân; mặc định 0. BE lưu tỷ lệ vào đơn mới. Phí nền tảng được trích trong phí cơ sở, không cộng thêm lần nữa khi FE hiển thị tổng.
- Ví dụ phí cơ sở 50.000, rate 10%: phần nền tảng là 5.000; trung tâm nhận phần phí cơ sở còn lại 45.000. Phí di chuyển được xử lý riêng theo quyết toán hiện có.

## 8. Tạo đơn với cấu hình vừa được xác nhận

POST `/api/v1/inspection/requests`

```json
{
  "postId": "33333333-3333-4333-8333-333333333333",
  "orderType": "AT_CENTER",
  "feePayer": "BUYER_PAYS",
  "buyerConsentZin": true,
  "inspectionCenterId": "11111111-1111-4111-8111-111111111111",
  "expectedFeeProposalId": "55555555-5555-4555-8555-555555555555",
  "expectedChecklistTemplateId": "44444444-4444-4444-8444-444444444444",
  "note": "Kiểm tra tình trạng hoạt động trước khi mua."
}
```

Ba ID cấu hình lấy từ báo giá thật, không tự sinh. expected IDs là tùy chọn để tương thích caller cũ; **FE mới nên luôn gửi**.
Nếu Admin thay checklist/giá trước lúc tạo đơn, BE trả 409: tải báo giá mới, hiển thị lại và để người dùng xác nhận lại.
Không gửi giá tiền/cấp/checklist tự tính để ép BE sử dụng.

Order response giữ các field trước và thêm:
`inspectionCenterId, checklistTemplateId, feeProposalId, complexityLevel, platformFeeRateSnapshot`.
Nội dung checklist đã chốt lấy ở API checklist theo orderId; không lấy lại mẫu đang active để nhập kết quả cho đơn cũ.

Luồng BUYER_REQUEST sau đó vẫn theo API hiện có:
1. Seller POST `/api/v1/inspection/requests/{orderId}/respond`:
   `{"action":"ACCEPT","sellerConsentZin":true}`.
2. Bên chịu phí POST `/api/v1/inspection/orders/{orderId}/pay-fee` từ ví.
3. Khi đủ phần phí và tiền cam kết cần nộp: CONFIRMED_PENDING_DISPATCH.
4. Trung tâm POST `/api/v1/inspector-center/orders/{orderId}/assign?inspectorId={id}`.
5. Inspector đã được phân công POST `/api/v1/inspector/orders/{orderId}/start`.
6. Đọc checklist đã chốt, nhập kết quả, POST `/api/v1/inspector/orders/{orderId}/result`.

Chưa đủ tiền thì đơn mới không được phân công/bắt đầu để vượt bước thanh toán. Inspector phải thuộc trung tâm đã chốt. Thông báo đơn mới chỉ gửi trung tâm được chọn; đơn legacy chưa có center giữ hành vi tương thích.

## 9. Inspector nộp kết quả đúng checklist của đơn

GET `/api/v1/inspector/orders/{orderId}/checklist`.
Chỉ buyer, seller, inspector được giao, trung tâm phụ trách và vai trò quản trị được phép mới xem được. Trung tâm khác/khách khác trả 403.

POST `/api/v1/inspector/orders/{orderId}/result`

```json
{
  "status": "PASSED",
  "note": "Đã kiểm tra theo checklist của đơn.",
  "checklistResults": [
    {
      "itemId": "66666666-6666-4666-8666-666666666666",
      "passed": true,
      "inspectorNote": "Đạt yêu cầu."
    }
  ]
}
```

- `itemId` ở đây là **ID tiêu chí checklist**, không phải ID Item catalog.
- Gửi đủ mọi tiêu chí mandatory lấy từ API của đơn.
- Không gửi ID thuộc checklist khác, không lặp ID.
- PASSED không hợp lệ khi bất kỳ tiêu chí mandatory được đánh giá false.
- `itemTitle` từ client không được dùng làm nguồn dữ liệu; BE lưu tên tiêu chí chuẩn từ snapshot.
- inspectorNote tối đa 4.000 ký tự. Các field video/tem/điểm hiện có của InspectionResultRequest vẫn dùng theo nghiệp vụ cũ.

## 10. Test thực tế trên Swagger

Chuẩn bị token của Center A, Center B, Admin, Seller, Buyer, Inspector thuộc A; chọn Item thật và bài ACTIVE của seller khác buyer. Cấp đủ quyền hiện có cho từng tài khoản. Ví bên chịu phí phải đủ tổng BE trả, bao gồm tiền cam kết nếu môi trường bật.

| STT | Thao tác | Kết quả cần kiểm tra |
| --- | --- | --- |
| 1 | Center A gửi checklist JSON phần 4 | 200; pending, inactive, có ID tiêu chí |
| 2 | Buyer lấy quote trước khi Admin duyệt | 409; không dùng giá/category fallback |
| 3 | Center B đọc/PUT/DELETE đề xuất pending của A | 403 |
| 4 | Admin duyệt checklist, chưa duyệt giá L2 của A | Quote vẫn 409 vì thiếu giá được duyệt |
| 5 | A gửi giá L2 phần 6, Admin duyệt | Current level fees của A có đúng giá đó |
| 6 | Quote bài dùng Item này, center A | 200; đúng Item, cấp, giá và center |
| 7 | Xóa GPS trung tâm rồi quote | 409; cập nhật GPS thật rồi thử lại |
| 8 | Giá âm hoặc không có cấp | 400 |
| 9 | Checklist thiếu Item/cấp, tên tiêu chí trùng hoặc không có mandatory | 400 |
| 10 | Tạo đơn với expected IDs vừa lấy | Có các ID cấu hình và rate snapshot trong response |
| 11 | A phân công trước khi đóng đủ phí | 409 |
| 12 | Seller đồng ý, nộp đủ phí, A phân công Inspector của A | 200; đúng trạng thái luồng |
| 13 | A gán Inspector thuộc B hoặc B xem checklist/đơn của A | 403 |
| 14 | Inspector đọc checklist, bắt đầu, gửi đủ mandatory=true | PASSED được chấp nhận |
| 15 | Bỏ một mandatory / gửi ID lạ / lặp tiêu chí / mandatory=false nhưng PASSED | 400 |
| 16 | A PUT checklist và Admin duyệt bản mới | ID mới; bản active cũ ngừng áp dụng |
| 17 | Xem checklist và phí đơn đã tạo ở bước 10 | Vẫn giữ phiên bản/giá/rate cũ |
| 18 | Dùng quote cũ sau khi giá/checklist thay đổi để tạo đơn khác | 409; lấy lại quote |
| 19 | Admin review lại đề xuất đã xử lý | 409 |
| 20 | Hai đề xuất cùng thay một checklist, duyệt lần lượt/đồng thời | Một được áp dụng; bản cạnh tranh trả 409 |
| 21 | Center A đổi giá L2, gửi pending nhưng chưa duyệt | Current price và quote mới vẫn dùng giá approved cũ |
| 22 | Danh sách proposals size=1, rồi size=20; sort=createdAt,desc | Đúng totalElements, thứ tự, đầy đủ tiêu chí |
| 23 | Seller chọn lại Item cũ bằng PATCH phần 2 | Đúng chủ bài và bài chưa bận: 200; sai chủ: 403 |
| 24 | Bài có Item gốc ngừng chọn/chưa xác định | Không được mặc định L1 để kiểm định; cần seller phân loại |
| 25 | Đơn đã hoàn tất, thực hiện lại quyết toán cùng đơn qua luồng hiện có | Không cộng/trừ ví lần hai |

Lỗi chung giữ ApiResponse lỗi của hệ thống: `success=false, message, path, errors, timestamp`.
FE hiển thị message phù hợp; không biến 409 thiếu cấu hình thành “Miễn phí”.

## 11. Migration và vận hành

- V70 bổ sung liên kết Item/checklist, approval/version, cấp giá theo trung tâm và snapshot đơn.
- V71 tách Item gộp, giữ ID gốc và tham chiếu lịch sử; chạy lặp an toàn từ hai đường seed catalog.
- V72 bổ sung tỷ lệ nền tảng lưu trên đơn mới.
- Checklist cũ được đánh dấu LEGACY, không giả định đã được Admin duyệt. Đơn cũ chưa có snapshot vẫn dùng nhánh tương thích; không tự sửa tiền hay gán cấp cho chúng.
- Không xóa dữ liệu để áp dụng thay đổi; không chạy ddl-auto=create. Dùng Flyway theo cấu hình hiện có.
- Sau khi cập nhật BE, khởi động lại để Flyway áp migration mới. Đợt triển khai code này không chủ động chạy migration trên DB dùng thật.
- Trước khi bật đặt kiểm định trên FE, cần hoàn tất checklist/cấp, giá được duyệt, GPS trung tâm và địa chỉ seller.
- Nhánh kiểm định PRE_LISTING giá trị cao dùng cấu hình phí/checklist đã duyệt khi tạo mới, nhưng đợt này không thiết kế lại toàn bộ vòng đời thanh toán/hoàn hàng PRE_LISTING.

