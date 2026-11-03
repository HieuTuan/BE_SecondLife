# BE: Lịch sử và chi tiết giao dịch ví

Phạm vi: ví tiền của user đang đăng nhập. API lấy userId từ JWT; không nhận userId/walletId do client truyền để đọc ví của người khác. Các thay đổi chỉ nằm ở backend.

## 1. Danh sách giao dịch

```http
GET /api/v1/wallets/me/transactions?page=0&size=20
Authorization: Bearer <ACCESS_TOKEN>
```

Không có request body. Nếu không truyền sort, mặc định `createdAt,desc` rồi `id,desc` để phân trang ổn định khi thời gian trùng nhau. Có thể truyền `sort=createdAt,desc`, `sort=amount,asc` hoặc trường id/type/referenceId. Không dùng giá trị mẫu `string` của Swagger. Sort có ignoreCase không được hỗ trợ.

Response giữ nguyên PageResponse, không bọc thêm success/data:

```json
{
  "items": [
    {
      "id": "10000000-0000-4000-8000-000000000001",
      "walletId": "20000000-0000-4000-8000-000000000001",
      "amount": -30000.00,
      "type": "PAYMENT",
      "referenceId": "30000000-0000-4000-8000-000000000001",
      "createdAt": "2026-10-25T03:30:00Z",
      "currency": "VND",
      "direction": "DEBIT",
      "description": "Thanh toán đơn hàng từ ví",
      "referenceType": "ORDER",
      "balanceBefore": 100000.00,
      "balanceAfter": 70000.00
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

Các UUID trong ví dụ chỉ minh họa. Dùng id thực tế do API trả về khi gọi chi tiết.

### Lọc theo loại và thời gian

```http
GET /api/v1/wallets/me/transactions?type=REFUND&from=2026-10-24T17:00:00Z&to=2026-10-25T17:00:00Z&page=0&size=20
```

- `type`: DEPOSIT, WITHDRAW, PAYMENT, EARNING, REFUND, RETURN_SHIPPING_FEE.
- `from`: thời điểm bắt đầu, có bao gồm chính thời điểm đó.
- `to`: thời điểm kết thúc, không bao gồm chính thời điểm đó.
- Dùng timestamp ISO-8601 có múi giờ; mẫu UTC kết thúc bằng Z tránh lỗi dấu cộng trong URL.
- Có thể truyền riêng from hoặc riêng to. Nếu có cả hai thì from phải nhỏ hơn to.
- Khoảng thời gian trong ví dụ tương ứng ngày 25/10/2026 theo giờ Việt Nam.

## 2. Chi tiết một giao dịch

```http
GET /api/v1/wallets/me/transactions/10000000-0000-4000-8000-000000000001
Authorization: Bearer <ACCESS_TOKEN>
```

HTTP 200 trả trực tiếp một object giao dịch có cùng các field trong items ở trên. Dùng `id` của giao dịch, không dùng referenceId hoặc walletId trong path này.

Giao dịch không tồn tại và giao dịch thuộc user khác đều trả HTTP 404, tránh lộ sự tồn tại của giao dịch người khác. Cả API danh sách và chi tiết đều cần đăng nhập.

## 3. Ý nghĩa dữ liệu

| Field | Ý nghĩa |
|---|---|
| amount | Số tiền có dấu: dương là cộng ví, âm là trừ ví; giữ nguyên contract cũ |
| type | Loại biến động tiền của ledger |
| direction | CREDIT: cộng tiền; DEBIT: trừ tiền; NEUTRAL: bản ghi cũ có số tiền 0 |
| currency | VND |
| description | Mô tả tiếng Việt do BE cung cấp dựa trên loại giao dịch và nguồn đã ghi nhận |
| referenceType | Loại đối tượng nghiệp vụ mà referenceId trỏ đến |
| referenceId | UUID của đối tượng nghiệp vụ, khác với UUID của chính giao dịch ví |
| balanceBefore | Số dư ngay trước giao dịch, không phải số dư hiện tại |
| balanceAfter | Số dư ngay sau giao dịch, không phải số dư hiện tại |

### Nguồn tham chiếu

| referenceType | referenceId trỏ đến |
|---|---|
| ORDER | Đơn mua bán thông thường |
| INSPECTION_ORDER | Đơn kiểm định; khoản thanh toán, hoàn phí hoặc tiền nhận từ luồng kiểm định |
| DEPOSIT_REQUEST | Yêu cầu nạp tiền hoặc yêu cầu chuyển khoản có phần tiền được cộng vào ví |
| WITHDRAWAL | Yêu cầu rút tiền; gồm cả khoản trừ ban đầu và khoản hoàn khi bị từ chối |
| SHIPMENT | Chuyến hoàn hàng có phí thực tế đã được trừ khỏi ví seller |
| CREDIT_PURCHASE | Lần mua quyền sử dụng dịch vụ theo số lượng |
| TOPUP_PACKAGE | Gói quyền sử dụng dịch vụ trong luồng mua gói cũ |
| UNKNOWN | Nguồn không được ghi nhận trong giao dịch lịch sử |

API cung cấp loại nguồn và UUID, không tự ghép thêm thông tin sản phẩm/ngân hàng từ các bảng khác. Việc này tránh đoán nhầm nguồn chỉ dựa trên UUID. Những API nghiệp vụ hiện có tiếp tục chịu trách nhiệm về chi tiết đơn hàng/kiểm định/rút tiền.

## 4. Dữ liệu cũ và các giới hạn nghiệp vụ

- Migration V60 thêm reference_type, balance_before, balance_after; không đổi số dư ví, số tiền hay lịch sử đã ghi trước đó.
- Giao dịch cũ có referenceType=UNKNOWN và hai field số dư=null. Không suy ra số dư cũ bằng cách trừ ngược từ số dư hiện tại.
- Giao dịch mới ghi snapshot cùng transaction với thao tác cộng/trừ tiền, dưới khóa ví đã có. Luôn có balanceAfter = balanceBefore + amount.
- Số tiền gửi vào các thao tác ví phải biểu diễn chính xác với tối đa 2 chữ số thập phân. Ví dụ 50000.005 trả 400 trước khi trừ tiền; 50000.000 vẫn hợp lệ vì các chữ số thêm đều bằng 0. BE không tự làm tròn số tiền để ghi snapshot.
- WITHDRAW nghĩa là số dư đã bị trừ cho yêu cầu rút tiền, không khẳng định ngân hàng đã chuyển tiền thành công. Xem trạng thái yêu cầu qua GET /api/v1/wallets/withdrawals.
- REFUND + WITHDRAWAL mô tả riêng khoản hoàn vì yêu cầu rút tiền bị từ chối.
- RETURN_SHIPPING_FEE chỉ phản ánh số tiền đã trừ thật. Công nợ ship hoàn chưa thu không tạo biến động tiền giả trong lịch sử ví.
- Thanh toán trực tiếp từ ngân hàng vào escrow vẫn không tạo PAYMENT trong ví cá nhân. Chỉ phần tiền thực sự cộng/trừ ví mới có bản ghi ở API này.
- Chức năng không mở quyền cho Staff/Admin tra cứu ví của user bất kỳ và không sửa frontend.

## 5. Quy trình kiểm tra trên Swagger

1. Đăng nhập Buyer/Seller, Authorize bằng access token.
2. Gọi danh sách để kiểm tra phân trang. User chưa có giao dịch nhận items rỗng; đọc lịch sử không tự tạo ví.
3. Thực hiện các luồng nạp/thanh toán/hoàn/rút tiền hiện có; gọi lại danh sách và kiểm tra mô tả, chiều tiền, nguồn và snapshot.
4. Copy id từ items rồi gọi API chi tiết; kiểm tra dữ liệu khớp bản ghi danh sách.
5. Thử type, from, to để kiểm tra bộ lọc. Không cần gửi JSON body cho GET.
6. Đăng nhập user khác và gọi cùng id: phải trả 404.
7. Thử type=NOT_A_TYPE, from=not-a-date, from>=to hoặc sort=string: phải trả 400. Bỏ token: phải trả 401.
8. Xem giao dịch cũ và xác nhận hai snapshot vẫn null; không coi đó là số dư 0.

Sau khi cập nhật code, khởi động lại BE để Flyway chạy V60. Không dùng ddl-auto=create/update thay cho migration và không chạy lại migration bằng cách sửa file đã áp dụng.
