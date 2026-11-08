# FE — Báo giá vận chuyển kiểm định bằng Lalamove

## 1. Công thức và phạm vi

Hàng trước kiểm định nằm ở **người bán**. Buyer chọn trung tâm và xem chi phí trước khi gửi yêu cầu.

```text
Tổng gói AT_CENTER
= Phí kiểm định cơ sở đã được Admin duyệt cho trung tâm/cấp độ của Item
+ Quotation Lalamove SELLER_TO_CENTER
+ Dự phòng hoàn hàng từ quotation Lalamove CENTER_TO_SELLER
```

Hai chiều được báo giá riêng, không nhân đôi giá một chiều. Cùng một kiện hàng có thể nhận loại xe và giá khác nhau cho hai hướng.

Địa chỉ xuất phát lấy từ địa chỉ pickup seller, điểm đến lấy từ hồ sơ trung tâm được chọn. Cân nặng tính bằng gram, kích thước bằng cm, dùng dữ liệu đóng gói của bài đăng. FE không gửi phí hoặc tự chọn xe để thay cho kết quả của hãng.

`INSPECTION_SHIPPING_FEE` / `app.inspection.shipping-fee` không còn được sử dụng trong BE. Thiếu địa chỉ, kích thước, hãng lỗi hoặc không hỗ trợ tuyến sẽ trả lỗi; không dùng 50.000đ hoặc 0đ làm giá dự phòng.

Phí di chuyển KTV cho `AT_HOME` tiếp tục theo biểu phí di chuyển của hệ thống. Đây là dịch vụ khác với vận chuyển hàng bằng Lalamove.

`atCenterTotalFee` chỉ là phí dịch vụ kiểm định và hai khoản vận chuyển nêu trên, không gồm tiền mua sản phẩm hoặc phí trung tâm→buyer sau khi kiểm định đạt. Tiền cam kết mua hàng nếu có vẫn theo luồng hiện tại và hiển thị riêng.

## 2. API lấy báo giá

```http
POST /api/v1/inspection/quote
Authorization: Bearer <access_token>
Content-Type: application/json
```

```json
{
  "postId": "<UUID bài đăng thực tế>",
  "inspectionCenterId": "<UUID trung tâm đã chọn>",
  "orderType": "AT_CENTER"
}
```

Trung tâm phải có hotline, địa chỉ, GPS hợp lệ qua `PUT /api/v1/inspector-center/profile`. Seller phải lưu địa chỉ pickup và kiện hàng. Xem `FE_INSPECTION_CENTER_PROFILE_GUIDE.md`.

Endpoint này tiếp tục trả `InspectionQuoteResponse` trực tiếp, **không bọc `data`**. Ví dụ rút gọn, các giá dưới đây chỉ minh họa:

```json
{
  "postId": "<postId>",
  "baseInspectionFee": 50000,
  "baseFee": 50000,
  "shippingToCenterFee": 27000,
  "returnShippingReserveFee": 39000,
  "atCenterTotalFee": 116000,
  "atCenterSplitOptions": {
    "BUYER_PAYS": 116000,
    "SELLER_PAYS": 116000,
    "SPLIT_50_50": 58000
  },
  "atCenterPackage": {
    "shippingToCenterFee": 27000,
    "returnReserveFee": 39000,
    "totalFee": 116000,
    "suggestedSplit50": 58000
  },
  "inspectionConfiguration": {
    "inspectionCenterId": "<centerId>",
    "checklistTemplateId": "<templateId>",
    "feeProposalId": "<feeProposalId>",
    "complexityLevel": "L2",
    "inspectionFee": 50000
  },
  "shippingQuote": {
    "id": "<inspectionShippingQuoteId>",
    "inspectionCenterId": "<centerId>",
    "expiresAt": "2026-11-06T08:20:00Z",
    "outbound": {
      "leg": "SELLER_TO_CENTER",
      "provider": "LALAMOVE",
      "shippingFee": 27000,
      "serviceType": "MOTORCYCLE",
      "providerQuotationId": "<quotationId hãng chiều đi>",
      "expiresAt": "2026-11-06T08:20:00Z",
      "vehicleSelection": null
    },
    "returnReserve": {
      "leg": "CENTER_TO_SELLER",
      "provider": "LALAMOVE",
      "shippingFee": 39000,
      "serviceType": "MOTORCYCLE",
      "providerQuotationId": "<quotationId hãng chiều hoàn>",
      "expiresAt": "2026-11-06T08:21:00Z",
      "vehicleSelection": null
    }
  }
}
```

Các UUID/quotation ID và thời gian ở ví dụ phải thay bằng giá trị response thực tế, không hardcode. BE lưu báo giá hai chiều trong database và lấy thời hạn sớm hơn của hai chiều làm `shippingQuote.expiresAt`.

`vehicleSelection` dùng cùng contract giải thích lựa chọn xe như API shipping quotes hiện có. FE hiển thị thông báo của BE nếu xe nhỏ bị hãng từ chối và phải dùng xe lớn. Không tự suy đoán “đường xa nên phải dùng xe tải”.

### Chỉ xem gói tại nhà

Gửi `orderType: "AT_HOME"` để chỉ tính gói KTV đến seller, không gọi Lalamove. Các trường tiền `AT_CENTER` và `shippingQuote` trả null; FE không đổi null thành giá 0đ.

Bỏ `orderType` giữ hành vi trả hai gói; BE sẽ cần lấy giá Lalamove để điền gói tại trung tâm. Nếu tuyến hãng không phục vụ và người dùng chuyển sang tại nhà, gọi lại với `AT_HOME`.

## 3. Buyer xác nhận tạo yêu cầu

```http
POST /api/v1/inspection/requests
Authorization: Bearer <buyer_access_token>
Content-Type: application/json
```

```json
{
  "postId": "<postId>",
  "inspectionCenterId": "<centerId từ configuration>",
  "orderType": "AT_CENTER",
  "feePayer": "BUYER_PAYS",
  "buyerConsentZin": true,
  "expectedFeeProposalId": "<feeProposalId từ configuration>",
  "expectedChecklistTemplateId": "<checklistTemplateId từ configuration>",
  "inspectionShippingQuoteId": "<shippingQuote.id từ bước trước>"
}
```

`inspectionShippingQuoteId` bắt buộc cho yêu cầu buyer `AT_CENTER`. Thiếu trả 400. Với `AT_HOME`, không yêu cầu mã này.

Không nhầm `inspectionShippingQuoteId` với `shippingQuoteId` của luồng checkout mua hàng, hoặc với `providerQuotationId` của hãng. Ba loại ID phục vụ ba mục đích khác nhau.

BE dùng lại báo giá đã lưu, kiểm tra đúng bài đăng/trung tâm/địa chỉ/kiện hàng/giá bài và còn hạn trước khi tạo yêu cầu. Bước này không tự gọi hãng để âm thầm thay số tiền buyer vừa xác nhận.

Đơn kiểm định trả thêm `inspectionShippingQuoteId` để truy vết. Các khoản `shippingFee`, `returnShippingReserveFee`, `buyerFeeShare`, `sellerFeeShare` được lưu từ báo giá và phương án chia phí của yêu cầu.

`feePayer=BUYER_PAYS`: buyer chịu toàn bộ tổng phí. Nếu chọn chia đôi hoặc seller chịu thì BE áp dụng theo phương án đó. Gói có chia phí vẫn phải được các bên xác nhận theo quy trình hiện tại.

## 4. Hết hạn, sửa thông tin và lỗi hãng

- Đếm ngược bằng `shippingQuote.expiresAt`; hết hạn khóa nút Xác nhận và cung cấp nút lấy giá mới.
- Đổi bài đăng hoặc trung tâm: xóa báo giá trong state, gọi lại API. Không dùng ID cũ cho trung tâm mới.
- BE phát hiện pickup seller, hồ sơ trung tâm, cân nặng/kích thước hoặc giá bài đổi thì trả 409. FE tải giá mới, hiển thị số tiền mới và yêu cầu người dùng xác nhận lại.
- Có thể gọi quote với `inspectionShippingQuoteId` cũ để kiểm tra/dùng lại báo giá còn hạn. Báo giá hết hạn không tự làm mới trong cùng request; bỏ ID để chủ động xin giá mới.
- Debounce/chặn bấm lặp khi đổi lựa chọn; không polling liên tục. Adapter Lalamove hiện có cache preview ngắn hạn và ưu tiên xe nhỏ nhất phù hợp.
- 422: tuyến không có xe phù hợp theo kết quả hãng; hiển thị lỗi, cho đổi trung tâm hoặc chọn tại nhà. Không thay bằng cước tự tính.
- 429: hãng giới hạn tần suất; cho thử lại sau. Không lặp request dồn dập.
- 502/503/504: tích hợp lỗi; hiển thị trạng thái chưa lấy được giá. Không cho thanh toán dựa trên giá giả hoặc giá trống.

## 5. Khi tạo vận đơn sau khi các bên đã đóng phí

Seller của đơn hoặc STAFF/ADMIN có thể tạo chặng `SELLER_TO_CENTER`. Với đơn mới có `inspectionShippingQuoteId`:

1. BE kiểm tra tuyến/kiện hàng hiện tại khớp snapshot đã xác nhận.
2. BE lấy quotation mới lúc đặt chuyến vì lịch lấy hàng và thời hạn quotation ban đầu có thể đã qua trong thời gian chờ duyệt/thanh toán.
3. Nếu giá lúc đặt chuyến lớn hơn ngân sách chiều tương ứng đã ghi trong đơn, trả 409 trước khi gọi API đặt chuyến; không tự trừ thêm ví buyer/seller. Cần xử lý đơn chưa gửi theo quy trình hủy/hoàn phí và tạo lại yêu cầu với giá mới.
4. Nếu đặt được trong ngân sách, tạo vận đơn theo cơ chế idempotency hiện có; retry requestId không tạo chuyến thứ hai.

Thời hạn quotation lúc buyer xác nhận không phải cam kết hãng giữ giá trong suốt thời gian các bên chờ duyệt. FE cần giải thích khả năng yêu cầu lấy lại giá ở bước điều phối.

Chiều hoàn vẫn là khoản dự phòng. Việc hàng có cần hoàn và việc phân bổ/hoàn phần dự phòng theo kết quả kiểm định tiếp tục theo luồng settlement hiện tại. Thay đổi này không bổ sung cơ chế tự động thu chênh lệch hoặc quyết toán lại theo hóa đơn hãng.

Đơn lịch sử giữ nguyên số tiền đã lưu và không bị migration tính lại. Các bản ghi cũ có `inspectionShippingQuoteId=null` tiếp tục theo nhánh vận chuyển tương thích. Các yêu cầu mới không lấy phí cấu hình cố định nữa.

## 6. Triển khai BE

Migration mới `V73__inspection_lalamove_shipping_quotes.sql` tạo bảng snapshot quotation và liên kết tùy chọn vào đơn kiểm định. Flyway áp dụng khi khởi động; không sửa migration đã chạy hoặc chạy `ddl-auto=create` để áp dụng tính năng.

Mã nguồn BE không còn đọc `INSPECTION_SHIPPING_FEE`. Có thể bỏ biến cũ khỏi cấu hình triển khai sau khi đồng bộ phiên bản. Cấu hình xác thực, môi trường Sandbox/Production và giới hạn timeout của Lalamove vẫn dùng cấu hình hiện có.

## 7. Test thực tế trên Swagger

1. Cập nhật BE, chạy Flyway V73 trên môi trường phù hợp, đăng nhập trung tâm và lưu profile/GPS thực tế.
2. Seller lưu pickup/GPS, kiện hàng có đủ trọng lượng/dài/rộng/cao. Chọn Item đã có checklist và biểu phí approved cho trung tâm.
3. Buyer gọi `/inspection/quote` với `postId`, `inspectionCenterId`, `orderType=AT_CENTER`. Kiểm tra tổng bằng base + outbound + returnReserve, có hai quotationId và thời hạn thật.
4. Gọi `/inspection/requests` với ID vừa nhận; kiểm tra phí được lưu đúng theo báo giá. Không phải nhập giá trong request.
5. Thử thiếu mã báo giá: 400. Đổi trung tâm khi vẫn dùng mã cũ: 409. Chờ quá `expiresAt`: 409 và lấy báo giá mới.
6. Sửa kiện hàng/pickup sau khi quote rồi dùng ID cũ: 409. Không âm thầm chuyển sang cước mới.
7. Gọi quote `AT_HOME`: thành công mà không cần quotation hãng; các field giá trung tâm là null.
8. Test tuyến bị hãng từ chối: thấy lỗi 422, không thấy 50.000đ hoặc 0đ thay thế.
9. Sau khi các bên xác nhận và đóng phí, STAFF/ADMIN tạo shipment test: kiểm tra đúng tuyến/GPS và ngân sách. Nếu giá tăng, nhận 409 và không tạo chuyến.
10. Đơn cũ vẫn hiển thị số tiền cũ; không được tự tính lại khi chỉ mở trang lịch sử.

Các giá trong kiểm thử tự động được mock để kiểm chứng công thức, hết hạn, quyền và ghi DB. Kiểm thử này không tạo booking thật. Khi thử API tạo vận đơn bằng Swagger, dùng môi trường Sandbox và đơn test đã được chuẩn bị.

## 8. Prompt cho AI sửa FE

```text
Đọc docs/FE_INSPECTION_LALAMOVE_QUOTE_GUIDE.md và code FE hiện tại. Cập nhật buyer mua kèm kiểm định tại trung tâm:
1. POST /api/v1/inspection/quote gửi postId, inspectionCenterId, orderType=AT_CENTER; API trả response trực tiếp.
2. Hiển thị baseInspectionFee, shippingToCenterFee, returnShippingReserveFee và atCenterTotalFee từ BE. Hai phí vận chuyển đến từ hai quotation Lalamove; không hardcode hoặc nhân đôi một chiều.
3. Lưu shippingQuote.id + expiresAt cùng expectedFeeProposalId và expectedChecklistTemplateId. POST /inspection/requests phải gửi inspectionShippingQuoteId để tạo yêu cầu AT_CENTER.
4. Báo giá hết hạn/địa chỉ hoặc kiện hàng thay đổi: khóa xác nhận, lấy giá mới và cho người dùng xác nhận lại. Không âm thầm thay số tiền hoặc dùng fallback 0/50000.
5. Hiển thị phương tiện và vehicleSelection.message theo từng chiều nếu có, giải thích vì sao hãng chọn xe lớn. Dự phòng hoàn hàng phải ghi rõ là dự phòng.
6. Khi chọn AT_HOME, gửi orderType=AT_HOME, không yêu cầu inspectionShippingQuoteId; không hiển thị field AT_CENTER null thành miễn phí.
7. Xử lý 400/409/422/429/502/503/504. Debounce và chặn gọi lặp. Không dùng API tạo vận đơn để preview giá.
8. Bảo toàn các bước chia phí, xác nhận hai bên và tiền cam kết mua hàng hiện có; không đổi tổng phí thành tiền hàng.
9. Vận đơn lúc điều phối có thể bị chặn 409 nếu giá hãng vượt ngân sách đã xác nhận; không tự động thanh toán thêm.
Chỉ sửa FE, chạy TypeScript/build và báo file thay đổi cùng lỗi nền nếu có.
```
