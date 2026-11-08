# FE Admin — Giá kiểm định theo Item và API tìm kiếm checklist

Cập nhật 06/11/2026. Phần này bổ sung BE; FE cần tích hợp để hiển thị các cột và bộ lọc mới.

## 1. API trước và sau thay đổi

Trước đây, API checklist chỉ trả Item, cấp độ, tiêu chí và trạng thái; không kèm giá.
Giá nằm riêng trong đề xuất biểu phí theo trung tâm và cấp L1–L4.

Sau thay đổi, hai API quản trị sau trả thêm object `pricing` cho mỗi checklist:
- GET `/api/v1/admin/inspection/checklist-proposals`
- GET `/api/v1/admin/inspection/checklist-proposals/{id}`

API tìm kiếm mới:
- GET `/api/v1/admin/inspection/checklist-proposals/search`

API danh sách cũ và `/search` dùng cùng filter/response. FE có thể giữ route cũ và thêm query params.
Yêu cầu ADMIN + INSPECTION_CHECKLIST_MANAGE; JWT qua Authorization: Bearer token.

Đợt này không thêm migration, không thay công thức tính phí, không cập nhật đơn cũ và không sửa FE.

## 2. Ý nghĩa giá trả về

Không có một giá kiểm định chung duy nhất cho mỗi Item trên toàn sàn.
Giá hiển thị trong dòng này là:
**giá của trung tâm tác giả checklist, tại cấp độ được ghi trên chính dòng checklist đó**.

`inspectionCenterId` trong bộ lọc cũng là trung tâm **tác giả đề xuất**, không phải chọn một trung tâm khác để tính lại giá của dòng.

- `pricing.baseInspectionFee`: giá cấp đã được Admin duyệt gần nhất tại trung tâm đó; null nếu chưa có.
- `pendingBaseInspectionFee`: giá đề xuất đang chờ duyệt gần nhất; luôn tách riêng.
- Pending không thay thế giá approved; đề xuất bị từ chối không làm mất giá approved cũ.
- Approved chọn theo reviewedAt rồi ID; pending chọn theo createdAt rồi ID.
- Giá approved bằng 0 vẫn hợp lệ. Null có nghĩa chưa có giá, không được đổi thành 0.
- Checklist pending có thể hiển thị giá tham khảo của cấp được đề xuất, nhưng chưa được coi là cấu hình có hiệu lực cho Item.
- Checklist LEGACY hoặc chưa có Item/cấp không được gán L1 hoặc lấy giá danh mục làm mặc định.

## 3. Bộ lọc và tìm kiếm

| Param | Kiểu | Ý nghĩa |
| --- | --- | --- |
| keyword | String, tối đa 120 ký tự | Tìm một phần nội dung, không phân biệt hoa/thường hoặc dấu tiếng Việt |
| status | Enum | LEGACY, PENDING_APPROVAL, APPROVED, REJECTED, WITHDRAWN |
| categoryId | UUID | Danh mục của checklist |
| itemId | UUID | Loại thiết bị cụ thể |
| complexityLevel | Enum | L1, L2, L3 hoặc L4 |
| inspectionCenterId | UUID | Trung tâm tác giả checklist |
| active | Boolean | Phiên bản checklist đang/chưa active; không phải trạng thái tài khoản trung tâm |
| page | Integer | Bắt đầu từ 0 |
| size | Integer | Số bản ghi/trang |
| sort | String | Ví dụ createdAt,desc |

Các bộ lọc được kết hợp bằng AND, lọc tại DB trước phân trang và COUNT.
Không truyền param nghĩa là không lọc theo param đó.
Đổi keyword/filter cần đặt page về 0.

Trường hỗ trợ sort: id, createdAt, reviewedAt, revision, approvalStatus, complexityLevel.
Không gửi sort=["string"], sort theo price hoặc tên Item khi BE chưa hỗ trợ; trường không hợp lệ trả 400.

Keyword tìm trên:
- Tên Item.
- Tên danh mục.
- Tiêu đề và mô tả checklist.
- Tên hồ sơ và email trung tâm tác giả.

Ví dụ:
- `ban lam`, `BÀN LÀM` → khớp “Bàn làm việc”.
- `noi com` → khớp “Nồi cơm…”.
- Nhiều từ khớp theo thứ tự xuất hiện, có thể có nội dung xen giữa.
- Keyword rỗng/toàn khoảng trắng → không lọc từ khóa.
- %, _ và ! được tìm như ký tự thường, không dùng làm wildcard.
- Đây là tìm kiếm chuỗi con đã chuẩn hóa dấu; **chưa tự sửa lỗi chính tả**, ví dụ không bảo đảm “banf” khớp “bàn”.

Ví dụ URL trên Swagger/trình duyệt:

```text
GET /api/v1/admin/inspection/checklist-proposals/search?keyword=ban%20lam&complexityLevel=L2&status=PENDING_APPROVAL&page=0&size=20&sort=createdAt,desc
```

Dùng URLSearchParams trong FE để encode keyword và query params; không nối chuỗi input thẳng vào URL.

## 4. JSON response mẫu

Các UUID và số tiền sau chỉ minh họa; các field cũ khác của checklist vẫn được giữ.
Dòng dưới là checklist đang chờ duyệt, có giá L2 đã duyệt và một giá mới đang chờ duyệt:

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "items": [
      {
        "id": "11111111-1111-4111-8111-111111111111",
        "itemId": "22222222-2222-4222-8222-222222222222",
        "itemName": "Bàn làm việc",
        "categoryName": "Phòng ngủ",
        "title": "Bộ tiêu chuẩn kiểm định Bàn làm việc",
        "complexityLevel": "L2",
        "approvalStatus": "PENDING_APPROVAL",
        "active": false,
        "createdById": "33333333-3333-4333-8333-333333333333",
        "pricing": {
          "inspectionCenterId": "33333333-3333-4333-8333-333333333333",
          "inspectionCenterName": "Trung tâm kiểm định A",
          "complexityLevel": "L2",
          "baseInspectionFee": 100000,
          "feeProposalId": "44444444-4444-4444-8444-444444444444",
          "feeReviewedAt": "2026-11-06T01:00:00Z",
          "pendingBaseInspectionFee": 120000,
          "pendingFeeProposalId": "55555555-5555-4555-8555-555555555555",
          "priceStatus": "APPROVED",
          "applicable": false,
          "unavailableReason": "CHECKLIST_NOT_EFFECTIVE"
        }
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "isLast": true
  }
}
```

`priceStatus=APPROVED` mô tả **trạng thái giá**, không phải trạng thái checklist.
`applicable=false` trong ví dụ vì Item chưa được áp dụng checklist đang chờ duyệt này.

## 5. Giá trị trạng thái và cách hiển thị

| priceStatus | Hiển thị |
| --- | --- |
| APPROVED | Có giá cấp được duyệt; xem thêm applicable để biết checklist có hiệu lực hay chưa |
| PENDING_APPROVAL | Chưa có giá approved, đang có đề xuất chờ duyệt |
| NOT_CONFIGURED | Chưa có giá cấp được duyệt hoặc pending |
| LEGACY | Chưa liên kết Item/cấp hợp lệ; không hiển thị giá suy đoán |
| CENTER_UNAVAILABLE | Trung tâm không còn hoạt động/không còn role phù hợp; giá nếu còn được trả chỉ phục vụ tra cứu |

`unavailableReason`:
- ITEM_NOT_CLASSIFIED: thiếu Item/cấp hoặc là checklist legacy.
- CENTER_UNAVAILABLE: trung tâm không đủ điều kiện hoạt động.
- NO_APPROVED_LEVEL_FEE: chưa có giá được duyệt cho cấp.
- ITEM_NOT_SELECTABLE: Item đã ngừng cho chọn.
- CHECKLIST_NOT_EFFECTIVE: checklist chưa được duyệt hoặc đã bị thay thế/ngừng active.
- null: cặp checklist/giá hợp lệ.

`applicable` chỉ xác nhận cặp Item/checklist/giá và trung tâm hợp lệ; không thay cho bước kiểm tra GPS, địa chỉ hoặc vận chuyển khi gọi API quote.
Đây là **phí cơ sở kiểm định**, chưa bao gồm phí di chuyển hoặc ship.

Gợi ý các cột:
1. Item và danh mục.
2. Cấp độ.
3. Bộ tiêu chuẩn.
4. Trung tâm tác giả.
5. Giá cấp đã duyệt.
6. Giá đang đề xuất, nếu có.
7. Hiệu lực checklist/giá.
8. Trạng thái và thao tác duyệt hiện có.

Nếu baseInspectionFee không null nhưng applicable=false, ghi “Giá tham khảo tại cấp đề xuất”.
Nếu giá null, ghi “Chưa có giá được duyệt”; không hiển thị 0 đ.
Nếu complexityLevel=null, ghi “Chưa phân cấp”; không dùng `|| 'L1'`.
Không gán itemName bằng categoryName rồi trình bày như Item đã xác định.

## 6. Phạm vi bảo mật và đơn cũ

- Giá pending bổ sung chỉ ở lượt đọc quản trị Admin; API trung tâm không bị mở quyền xem pending riêng của nơi khác.
- GET detail trả pricing trong ApiResponse.data; danh sách trả trong data.items.
- API POST/PUT/review checklist giữ contract cũ; pricing có thể null ở response thao tác. Sau khi lưu/duyệt, FE tải lại GET.
- Response dùng trong checklist snapshot của đơn không được cập nhật giá trực tiếp. Xem đơn cũ vẫn dùng bản chốt lúc tạo đơn.
- Không thay đổi quyền duyệt hoặc công thức giá của luồng mua/đăng bài.

## 7. Test Swagger

1. Đăng nhập Admin, gọi danh sách cũ không có keyword: vẫn có data.items và các field cũ.
2. Gọi /search với `keyword=ban lam`, chọn Item thật “Bàn làm việc”: kiểm tra có kết quả không dấu.
3. Thử chữ hoa, nhiều khoảng trắng và một phần tên: cùng nguyên tắc khớp.
4. Thêm status + categoryId + itemId + complexityLevel + inspectionCenterId + active: chỉ trả các dòng đáp ứng toàn bộ.
5. Gọi page=0,size=1 và page=1,size=1: totalElements phản ánh số dòng sau lọc, không phải tổng toàn hệ thống.
6. Tạo hai trung tâm có giá L2 khác nhau: dòng checklist của trung tâm nào lấy đúng giá trung tâm đó.
7. Có approved 100.000 và pending 120.000: hai giá ở hai field; base vẫn 100.000.
8. Chỉ có pending hoặc chưa có giá: baseInspectionFee=null; không giả định miễn phí.
9. Giá approved=0: trả 0 thật, khác trường hợp null.
10. Checklist pending/inactive có approved fee: applicable=false.
11. Checklist LEGACY chưa có Item/cấp: pricing.priceStatus=LEGACY, không gán L1.
12. Keyword dài quá 120, sai enum, sai UUID hoặc sort không được hỗ trợ: HTTP 400.
13. Không token: 401; Buyer hoặc Center gọi API Admin: 403.
14. Đổi giá sau khi tạo đơn: API Admin đọc giá mới, checklist và tiền của đơn cũ không thay.

