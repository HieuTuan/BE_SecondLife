# Hướng dẫn FE — Điểm uy tín SELLER và xác nhận đơn

Đối chiếu BE ngày 14/10/2026. Phần gửi/duyệt report tạm hoãn. Tài liệu này mô tả UI và API hiện có; JSON/số liệu bên dưới là minh họa.

## 1. Công thức và ý nghĩa

```text
G = goodTransactions
B = badTransactions
N = evaluatedOrders = G + B
P = confirmationPenaltyPoints
Nếu N = 0: transactionScore = null, score = null.
Nếu N > 0:
  transactionScore = round(100 * G / N, 2)
  score = max(0, transactionScore - P), làm tròn 2 chữ số HALF_UP.
```

Điểm có thang 0–100. Mỗi đơn GOOD làm G tăng một, không cộng cố định 7/10 điểm. GOOD được ghi nhận khi đơn COMPLETED và ký quỹ RELEASED. DELIVERED chưa tự tăng GOOD. Một đơn kết thúc có lỗi SELLER chỉ tính BAD một lần. Đơn đang xử lý không nằm trong N; hủy do BUYER không có lỗi SELLER được loại khỏi mẫu.

Cấu hình local hiện tại: chưa xác nhận sau 24h phạt 2 điểm; sau 48h phạt thêm 3 điểm và tự hủy khi đủ điều kiện. Hai hạn đều tính từ paidAt. FE đọc deadline/mức phạt đã lưu theo từng đơn, không hardcode chính sách. GET chỉ đọc; BE xử lý điểm, hoàn tiền và lịch sử.

| Mốc minh họa | G | B | transactionScore | P | score |
|---|---:|---:|---:|---:|---:|
| Ban đầu | 5 | 0 | 100 | 0 | 100 |
| Quá hạn đầu | 5 | 0 | 100 | 2 | 98 |
| Phạt thêm do timeout | 5 | 0 | 100 | 5 | 95 |
| Đánh giá đơn hủy | 5 | 1 | 83.33 | 5 | 78.33 |

Các bước hủy có thể cùng transaction: API trả kết quả cuối; lịch sử giải thích từng sự kiện. Vì BAD đổi mẫu giao dịch nên giảm điểm cuối có thể lớn hơn 5.

## 2. Bố cục UI

```text
UY TÍN CỦA TÔI                              [Tải lại]
78,33 / 100                 [Đủ số đơn tham chiếu]
Điểm giao dịch: 83,33       Điểm phạt tích lũy: 5
[5 đơn thành công] [1 đơn lỗi SELLER] [6 đơn được đánh giá]
Không hoàn thành: 16,67% (1/6)
Hủy do SELLER: 1            Trả hàng lỗi SELLER: 0
Bàn giao trễ: 0% (0/5)
Cảnh báo: Có điểm phạt chưa xác nhận đơn đúng hạn.
LỊCH SỬ: Thời gian | Sự kiện | Đơn | Điểm trước/sau | Phạt tăng
```

BUYER hiển thị thẻ uy tín công khai bên cạnh thông tin người bán của bài đăng. SELLER có màn hình đầy đủ và lịch sử riêng. STAFF có bảng theo dõi đơn chờ xác nhận, không có nút xác nhận hộ.

| Field summary | Hiển thị / ý nghĩa |
|---|---|
| sellerId | ID người bán, khác postId |
| score | Điểm tổng /100; null hiển thị chưa có điểm |
| transactionScore | Điểm từ tỷ lệ giao dịch |
| confirmationPenaltyPoints | Điểm phạt tích lũy |
| goodTransactions, badTransactions, evaluatedOrders | GOOD, BAD, tổng mẫu |
| minimumEvaluatedOrders | Mốc tham chiếu lấy từ BE, local hiện 5 |
| assessmentStatus | NO_TRANSACTIONS: chưa có mẫu; INSUFFICIENT_EVIDENCE: ít dữ liệu; EVALUATED: đủ số đơn tham chiếu |
| sellerCancellationOrders | Đơn đã đánh giá có lỗi hủy SELLER |
| sellerReturnFaultOrders | Đơn đã đánh giá có lỗi trả hàng SELLER |
| nonFulfillmentOrders, nonFulfillmentRate | Đơn có lỗi hủy hoặc trả hàng; tỷ lệ trên evaluatedOrders |
| handoverEvaluatedOrders | Mẫu đã đánh giá có hạn bàn giao và pickup thực tế |
| lateHandoverOrders, lateHandoverRate | Lỗi bàn giao trễ đã được xác nhận; tỷ lệ trên handoverEvaluatedOrders |
| confirmedIncidents, seriousIncidents | Số vi phạm hiện có; chưa dựng UI xử lý report |
| sellingRestricted | Banner hạn chế bán; không tự khóa mọi thao tác đơn đang xử lý |
| warningCode | Cảnh báo ưu tiên theo bảng dưới |
| updatedAt | Thời điểm cập nhật; nullable |
| formulaVersion | Version kỹ thuật, hiện TRANSACTION_SUCCESS_RATE_V3 |

Tỷ lệ response đã là phần trăm, không nhân 100 thêm. Mẫu số 0 trả null, hiện chưa có dữ liệu. Không cộng số hủy với số trả hàng để suy ra nonFulfillmentOrders vì một đơn có thể có cả hai. Tỷ lệ bàn giao trễ không tự tạo thêm khoản phạt; candidate trễ không chứng minh lỗi SELLER.

| warningCode | Nhãn |
|---|---|
| SELLING_RESTRICTED | Đang bị hạn chế bán |
| SERIOUS_VIOLATION_HISTORY | Có lịch sử vi phạm nghiêm trọng |
| ORDER_CONFIRMATION_VIOLATION | Có phạt chậm xác nhận |
| CONFIRMED_VIOLATION_HISTORY | Có vi phạm được xác nhận |

Cảnh báo có thứ tự ưu tiên như bảng, không phải danh sách mọi vấn đề. EVALUATED không đồng nghĩa uy tín cao hay đã eKYC. Không tự tạo ngưỡng tốt/xấu hoặc quy tắc phạt 10% của sàn khác. Loading dùng skeleton; lỗi giữ dữ liệu cũ và cho thử lại. Phân biệt 0 với null.

## 3. API, quyền và cách đọc JSON

Base URL local http://localhost:8080; lấy từ cấu hình FE khi triển khai. Các API riêng tư gửi Authorization: Bearer <accessToken>. GET/PUT confirm/PATCH read không có body.

body bên dưới là JSON đã parse. Axios: lấy axiosResponse.data làm body trước.

| API | Quyền | Dữ liệu |
|---|---|---|
| GET /api/v1/sellers/{sellerId}/reputation | Công khai | body.data |
| GET /api/v1/seller/reputation/me | SELLER + PROFILE_READ_SELF | body.data |
| GET /api/v1/seller/reputation/me/history?page=0&size=10 | SELLER + PROFILE_READ_SELF | body.data.items |
| GET /api/v1/orders/seller?page=0&size=10 | Đăng nhập, đơn của mình | body.content |
| GET /api/v1/orders/buyer?page=0&size=10 | Đăng nhập, đơn của mình | body.content |
| GET /api/v1/orders/{orderId}/confirmation | Các bên sở hữu; STAFF + STAFF_DISPUTE_REVIEW; ADMIN + ADMIN_RBAC_MANAGE | body.data |
| PUT /api/v1/orders/{orderId}/confirm | Role SELLER, sở hữu đơn | body.data |
| GET /api/v1/staff/orders?status=PROCESSING&page=0&size=10 | STAFF + STAFF_DISPUTE_REVIEW hoặc ADMIN + ADMIN_RBAC_MANAGE | body.data.items |
| GET /api/v1/notifications?type=REPUTATION&page=0&size=10 | Thông báo của mình | body.items |
| GET /api/v1/notifications/unread-count | Đăng nhập | body.unreadCount |
| PATCH /api/v1/notifications/{id}/read | Người nhận | DTO trực tiếp |
| GET /api/v1/wallets/me | Ví của mình | body.balance |
| GET /api/v1/wallets/me/transactions?page=0&size=10 | Giao dịch của mình | body.items |

Trang bắt đầu 0. Không gửi sort trong các ví dụ. size thực tế chịu giới hạn cấu hình BE. page vượt totalPages có thể trả items rỗng dù totalElements > 0. Không có endpoint công khai xem private history SELLER khác.

Ví dụ phần data summary, rút gọn:

```json
{
  "score": 78.33,
  "transactionScore": 83.33,
  "confirmationPenaltyPoints": 5,
  "goodTransactions": 5,
  "badTransactions": 1,
  "evaluatedOrders": 6,
  "minimumEvaluatedOrders": 5,
  "assessmentStatus": "EVALUATED",
  "nonFulfillmentOrders": 1,
  "nonFulfillmentRate": 16.67,
  "handoverEvaluatedOrders": 5,
  "lateHandoverOrders": 0,
  "lateHandoverRate": 0,
  "sellingRestricted": false,
  "warningCode": "ORDER_CONFIRMATION_VIOLATION"
}
```

## 4. Nối luồng xác nhận đơn

1. BUYER có quote vận chuyển hợp lệ, bài đang bán, đủ ví; gọi POST /api/v1/orders.
2. Lấy body.id làm orderId (POST order trả DTO trực tiếp).
3. SELLER GET danh sách đơn, GET confirmation của đơn.
4. SELLER PUT confirm không body; thành công cập nhật đơn, refetch summary/history.
5. Sau confirm tiếp tục tạo vận đơn. Với đơn mới, booking trước confirm bị 409.

JSON POST order mẫu; thay postId/shippingQuoteId bằng UUID thật, giữ requestId khi retry cùng yêu cầu:

```json
{
  "postId": "f059552c-3921-4e02-9c39-20dfbdde7331",
  "shippingQuoteId": "e81d7838-66c6-4f51-8184-af4ac396fbd4",
  "requestId": "095695a0-f23d-44d4-9a9e-0b2d33900e6d"
}
```

Ví dụ phần data của GET confirmation:

```json
{
  "orderId": "579af91c-1335-4fd1-abaa-e85c8263f266",
  "postId": "f059552c-3921-4e02-9c39-20dfbdde7331",
  "postTitle": "Bàn ban công",
  "buyerId": "437e9ed1-4d55-442b-9bd9-d685b769f4c9",
  "sellerId": "d6cd7fd6-0783-4f63-906c-f895e866470f",
  "status": "PROCESSING",
  "confirmationStatus": "PENDING",
  "paidAt": "2026-10-14T14:50:00Z",
  "confirmationDeadline": "2026-10-15T14:50:00Z",
  "confirmationCancelAt": "2026-10-16T14:50:00Z",
  "sellerConfirmedAt": null,
  "latePenaltyPoints": 2,
  "timeoutPenaltyPoints": 3
}
```

UI hiển thị trạng thái, paidAt, hai deadline, đếm ngược và mức phạt snapshot. confirmationStatus không có LATE; derive quá hạn từ deadline, giữ status BE.

- PENDING trước hạn hủy: SELLER có thể xác nhận nếu đơn PROCESSING + HELD; disable trong khi request chạy.
- Sau hạn đầu nhưng trước hạn hủy: vẫn cho xác nhận, ghi rõ phạt đầu vẫn áp dụng. BE phạt ngay nếu job chưa xử lý.
- Sau hạn hủy: khóa nút và tải lại; confirm trả 409 ngay cả khi job chưa đổi status.
- CONFIRMED: hiển thị sellerConfirmedAt. Retry không đổi thời điểm cũ. Không coi là đã giao hàng.
- CANCELLED: hiển thị kết quả đơn/ví từ BE.
- NOT_REQUIRED: đơn lịch sử không có deadline, không dựng hạn/phạt hồi tố.

Confirmation DTO không có escrowStatus; lấy từ DTO đơn nếu cần gating HELD. Không tự đổi đơn CANCELLED chỉ vì timer hết. Một đơn đã SHIPPED/DELIVERED/COMPLETED vẫn có thể có confirmationStatus CONFIRMED.

Tạo vận đơn bằng POST /api/v1/orders/{orderId}/shipments:

```json
{
  "requestId": "7cfdf74f-206b-4f2a-895e-8d647146d2d9",
  "leg": "SELLER_TO_BUYER"
}
```

Giữ requestId khi retry cùng booking. HTTP 200 với status CANCELLED/lastError không phải booking thành công. Carrier báo giao thành công rồi BUYER PUT /api/v1/orders/{orderId}/delivered không body, hoặc BE giải ngân đủ điều kiện theo lịch; sau COMPLETED + RELEASED refetch điểm.

24h/48h xác nhận tính từ thanh toán khác với 48h giải ngân tính từ giao thành công. Không dùng chung timer.

## 5. Test quá hạn và UI cập nhật

Tạo đơn mới riêng, không confirm và không tạo vận đơn. Ghi số dư BUYER và điểm SELLER ban đầu.

| Mốc | Kiểm tra |
|---|---|
| Trước hạn đầu | PROCESSING + HELD, chưa phạt |
| Sau hạn đầu | P tăng latePenaltyPoints một lần; lịch sử ORDER_CONFIRMATION_LATE; chưa tăng BAD chỉ vì mốc này |
| Sau hạn hủy đủ điều kiện | CANCELLED + REFUNDED; P tăng thêm timeoutPenaltyPoints; BAD tăng một lần; reason SELLER_CONFIRMATION_TIMEOUT |
| Chờ thêm chu kỳ | Không phạt, tính BAD hoặc hoàn tiền lại |

GET summary, history, notifications bằng SELLER; GET orders/buyer, wallet và transactions bằng BUYER. Không chỉ nhìn CANCELLED rồi tự cộng ví. Thông thường đơn mới chưa có carrier booking được hoàn giá sản phẩm + phí vận chuyển; nếu từng có orderCode, BE có thể giữ phí theo nghiệp vụ. Dùng số tiền hoàn thực tế của giao dịch ví.

Job local hiện kiểm tra mỗi 60 giây; timer về 0 có thể chờ chu kỳ. FE hiện đang chờ cập nhật, refetch. Job bỏ qua đơn đã confirm, escrow không HELD, pickup/delivered hoặc còn booking hoạt động/uncertain. GET không kích hoạt job. Đơn không đủ điều kiện không được FE tự ép hủy.

Swagger không có API đổi thời gian. Test nhanh trên database test riêng, thay ORDER_ID:

```sql
UPDATE orders
SET confirmation_deadline = NOW() - INTERVAL '1 minute',
    confirmation_cancel_at = NOW() + INTERVAL '1 hour'
WHERE id = 'ORDER_ID'::uuid AND status = 'PROCESSING'
  AND escrow_status = 'HELD' AND seller_confirmed_at IS NULL;
```

Chờ job, kiểm tra phạt đầu. Sau đó:

```sql
UPDATE orders
SET confirmation_cancel_at = NOW() - INTERVAL '1 minute'
WHERE id = 'ORDER_ID'::uuid AND status = 'PROCESSING'
  AND escrow_status = 'HELD' AND seller_confirmed_at IS NULL;
```

SQL chỉ giả lập deadline của đơn test, không phải thao tác FE trên đơn khách hàng. Không đổi snapshot điểm. Nếu job lần đầu chạy khi cả hai mốc qua, BE áp dụng cả hai một lần trong luồng hủy.

## 6. Lịch sử uy tín

GET /api/v1/seller/reputation/me/history?page=0&size=10 trả envelope có data.items, page, size, totalElements, totalPages, isLast.

| Field dòng lịch sử | FE dùng |
|---|---|
| id | Key dòng |
| eventType | Nhãn sự kiện |
| orderId | Link đơn khi có |
| reportId | Có thể tồn tại trong dữ liệu cũ; chưa nối UI report |
| goodBefore/badBefore/goodAfter/badAfter | Thay đổi mẫu giao dịch |
| scoreBefore/scoreAfter | Điểm trước/sau, nullable |
| penaltyBefore/penaltyAfter | Phạt tăng = after - before; không dùng chênh score suy ra phạt |
| priorGood/priorTotal | Dữ liệu chính sách cũ, không thay G/N hiện tại |
| createdAt | UTC |
| formulaVersion | Version lúc ghi; không tính lại snapshot cũ |
| eventSequence | Thứ tự mới; cũ có thể null |

| eventType | Nhãn |
|---|---|
| ORDER_COMPLETED | Đánh giá giao dịch hoàn tất |
| ORDER_CONFIRMATION_LATE | Phạt quá hạn xác nhận |
| ORDER_CONFIRMATION_TIMEOUT | Phạt thêm quá hạn hủy |
| SELLER_CANCELLED_ORDER | Hủy do lỗi SELLER, bao gồm hủy tự động |
| CLOSED_ORDER_EVALUATED | Đánh giá đơn đã kết thúc |
| HISTORICAL_ORDER_IMPORTED | Nhập giao dịch lịch sử |
| REPUTATION_POLICY_CHANGED | Đổi chính sách uy tín |
| SELLER_FAULT_CONFIRMED | Ghi nhận lỗi SELLER hiện có |
| SERIOUS_VIOLATION_CONFIRMED | Ghi nhận vi phạm nghiêm trọng |
| SELLING_RESTRICTION_LIFTED | Gỡ hạn chế bán |

Giữ thứ tự BE trả; eventSequence mới trước, dữ liệu cũ theo thời gian/ID. Không sort lại UUID khi timestamp giống nhau. Score null không tự thay 0. Xác nhận đúng hạn không đổi điểm nên không bắt buộc có dòng uy tín mới. Fallback nhãn cho eventType chưa biết.

## 7. Chuông thông báo

GET /api/v1/notifications?type=REPUTATION&page=0&size=10 hoặc type=ORDER. Mẫu response rút gọn:

```json
{
  "items": [
    {
      "id": "6b822174-e49c-45d2-9c6c-2d79e7ce5285",
      "title": "Bị trừ điểm uy tín",
      "message": "Đơn hàng quá hạn xác nhận. Bạn bị trừ 2 điểm.",
      "type": "REPUTATION",
      "referenceId": "579af91c-1335-4fd1-abaa-e85c8263f266",
      "referenceType": "ORDER",
      "actionUrl": "/seller/orders/579af91c-1335-4fd1-abaa-e85c8263f266",
      "read": false,
      "readAt": null,
      "createdAt": "2026-10-15T14:51:00Z"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

JSON thực tế dùng **read**, không phải isRead (test NotificationControllerTest kiểm tra $.read). Page dùng items/isLast; không content/last. PATCH /notifications/{id}/read và PATCH /notifications/read-all không body. Thông báo chỉ thuộc người nhận. Deduplicate theo notification.id, không theo orderId vì một đơn có thể có nhiều sự kiện hợp lệ.

WebSocket: SockJS /ws; STOMP CONNECT Authorization: Bearer token; subscribe /user/queue/notifications. Khi REPUTATION/ORDER tới: toast, refetch score/history/đơn liên quan và unread-count. Reconnect tải REST để phục hồi tin bỏ lỡ, token refresh dùng token mới, logout disconnect và xóa cache riêng tư. Không giả định mọi thay đổi điểm có REPUTATION riêng; refetch sau hoàn tất đơn và khi mở màn hình.

BE gửi realtime/email sau commit; email/socket chậm không có nghĩa điểm chưa được lưu. REST là nguồn kiểm tra. actionUrl nội bộ ánh xạ router FE theo vai trò hoặc dùng referenceId để mở đơn. STAFF dùng queue để theo dõi, không giả định có notification cho mọi lần confirm/timeout.

## 8. Gợi ý nối React

```typescript
// apiClient là client hiện có, baseURL chỉ chứa origin, interceptor gắn token.
const loadReputation = async () => {
  const [summary, history] = await Promise.all([
    apiClient.get('/api/v1/seller/reputation/me'),
    apiClient.get('/api/v1/seller/reputation/me/history', {
      params: { page: 0, size: 10 }
    })
  ]);
  return { summary: summary.data.data, history: history.data.data };
};

const confirmOrder = async (orderId: string) => {
  const response = await apiClient.put(`/api/v1/orders/${orderId}/confirm`);
  return response.data.data;
};

const formatScore = (n: number | null | undefined) =>
  n == null ? 'Chưa có dữ liệu' : new Intl.NumberFormat('vi-VN', {
    maximumFractionDigits: 2
  }).format(n);

const formatTime = (iso: string | null | undefined) =>
  !iso ? '—' : new Intl.DateTimeFormat('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'short'
  }).format(new Date(iso));
```

Nếu dùng React Query/SWR, key chứa userId/sellerId/page; invalidate sau confirm hoặc sự kiện liên quan. Reset state khi đổi tài khoản. Dùng nullish check thay vì value || default vì 0 hợp lệ. Các field nullable có thể null hoặc thiếu tùy serializer.

## 9. Thời gian, lỗi và checklist

Instant có Z là UTC. 15:18:47Z tương ứng 22:18:47 ở Việt Nam. Parse Date và format múi giờ; không cộng 7h lần nữa. timestamp envelope là local không offset, không dùng làm UTC hoặc deadline. Clock FE chỉ hướng dẫn; BE quyết định đủ điều kiện.

| Tình huống | FE xử lý |
|---|---|
| 401 | Refresh/login, reconnect socket |
| 403 | Không đủ quyền/không sở hữu đơn |
| 404 | Kiểm tra sellerId/orderId |
| 409 confirm | Refetch confirmation/đơn, có thể quá hạn hoặc đổi trạng thái/escrow |
| 400 | Hiển thị message/errors, kiểm tra UUID/body/query |
| 5xx/mất mạng | Giữ dữ liệu, cho retry và GET kiểm tra kết quả |
| N=0, P>0 | Score null nhưng vẫn hiển thị điểm phạt/history |
| Trang rỗng có totalElements | Quay về page hợp lệ |

Checklist FE:

- [ ] Thẻ public đúng SELLER; không mở private history người khác.
- [ ] Thang100, điểm giao dịch/phạt tách rõ, GOOD/BAD/N đúng BE.
- [ ] Null/0/loading/error và trạng thái ít mẫu hiển thị đúng.
- [ ] Tỷ lệ có mẫu số; không cộng phạt theo ngưỡng tự đặt.
- [ ] Deadline và phạt lấy snapshot từng đơn; chỉ SELLER xác nhận.
- [ ] Không tự hủy/hoàn tiền/chỉnh score trên FE.
- [ ] Bảng lịch sử giữ thứ tự, xử lý legacy nullable.
- [ ] Notification dùng read/items/isLast, cập nhật REST sau socket.
- [ ] Test đúng hạn, muộn trước hủy, timeout, retry, quyền sai, đơn lịch sử.
- [ ] Phạm vi hiện tại chưa có UI gửi/duyệt report.

Nguồn: SellerReputationController/Service, ReputationCalculator/Response/HistoryResponse, OrderConfirmationController/Service/Response, StaffOrderController, OrderController, NotificationController/ResponseDTO, PageResponse, SecurityConfig, WebSocketConfig và NotificationControllerTest.
