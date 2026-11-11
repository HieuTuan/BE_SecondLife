# Hướng dẫn FE Web — Biên bản kiểm định tại trung tâm

## 1. Phạm vi

Áp dụng khi đơn kiểm định có `orderType === "AT_CENTER"`. KTV thực hiện checklist, upload ảnh hiện trạng và nộp kết quả. Bỏ các bước nhập barcode, sinh hoặc xác nhận tem niêm phong khỏi màn nộp biên bản của hình thức này.

`AT_HOME` dùng bước check-in của Seller và KTV. Giữ hai nhánh độc lập bằng `orderType` từ BE.

## 2. Vị trí tích hợp

| File FE | Thay đổi |
| --- | --- |
| `src/pages/ProductInspectionStationView.tsx` | Đổi bước “Bằng chứng & Niêm phong” thành “Ảnh bằng chứng” cho `AT_CENTER`. Gỡ điều kiện `sealConfirmed` khỏi bước ảnh, nộp kết quả và xử lý lỗi. |
| `src/services/mediaService.ts` | Tái sử dụng `uploadImage` và `uploadMultipleImages` cho ảnh KTV chụp. |
| `src/services/inspectorService.ts` | Thêm `evidencePhotoUrls?: string[]` vào request và `evidencePhotoUrls?: string[] \| null` vào DTO đọc đơn. |
| `src/pages/BuyerInspectionDecisionView.tsx` và `src/components/modals/DigitalInspectionCertificateModal.tsx` | Đọc `evidencePhotoUrls` để dựng gallery. Với biên bản cũ, giữ fallback đọc ảnh từ `note` dạng `[Photos: [...]]`. Bỏ phần tem giả và nhãn “Đã niêm phong” trong biên bản tại trung tâm. |

## 3. Bắt đầu kiểm định và tải ảnh

1. KTV mở đơn và checklist. Luồng tại trung tâm gọi `POST /api/v1/inspector/orders/{orderId}/start` với body rỗng. Dùng `status` từ BE. Nếu gọi start lỗi, tải lại đơn. Chỉ tiếp tục từ giao diện khi BE xác nhận trạng thái `INSPECTING`.
2. Chọn hoặc chụp ảnh hiện trạng. Dùng `POST /api/v1/media/upload` với `multipart/form-data`. Gửi `file` và `folder=secondlife/inspections/proofs`. Để HTTP client tạo multipart boundary.
3. Có thể tải nhiều ảnh bằng `POST /api/v1/media/upload-multiple`, dùng trường `files` lặp lại và cùng thư mục. KTV cần quyền `MEDIA_UPLOAD_SELF`.
4. Chỉ đưa URL trả trong `data.url` của upload thành công vào danh sách ảnh sẽ gửi. Với upload nhiều ảnh, lấy URL từ từng phần tử trong `data`. BE kiểm tra URL ảnh Cloudinary của cloud hiện tại trong đúng thư mục trên.
5. Hiện thumbnail, lỗi và nút thử lại cho từng ảnh. Ảnh chưa upload xong, ảnh upload lỗi và preview `blob:` hoặc `data:` chỉ dùng ở local. Nút nộp yêu cầu 1–10 ảnh đã upload thành công và checklist hợp lệ.

## 4. Nộp biên bản

`POST /api/v1/inspector/orders/{orderId}/result`. KTV cần quyền `INSPECTION_REPORT_SUBMIT`. `orderId` là ID **đơn kiểm định**. Ví dụ một tiêu chí bắt buộc trượt:

```json
{
  "status": "FAILED",
  "note": "Máy không duy trì được nhiệt trong lần thử.",
  "conditionScore": 65,
  "conditionGrade": "POOR",
  "checklistResults": [
    {
      "itemId": "<UUID tiêu chí trong checklist của đơn>",
      "itemTitle": "Kiểm tra khả năng giữ nhiệt",
      "passed": false,
      "inspectorNote": "Nhiệt giảm sau khi chuyển chế độ."
    }
  ],
  "evidencePhotoUrls": [
    "<data.url của ảnh toàn cảnh đã upload>",
    "<data.url của ảnh chi tiết đã upload>"
  ]
}
```

Gửi kết quả cho tất cả tiêu chí bắt buộc. `PASSED` yêu cầu các tiêu chí bắt buộc đạt theo checklist đã lưu trên đơn. Bỏ `tamperSealId`, `barcode` và đoạn `[Photos: ...]` khỏi payload mới. Giữ `note` cho ghi chú của KTV. Chỉ gửi trường video khi có video thật.

API trả DTO đơn trực tiếp, ví dụ rút gọn:

```json
{
  "id": "<orderId>",
  "orderType": "AT_CENTER",
  "status": "FAILED",
  "note": "Máy không duy trì được nhiệt trong lần thử.",
  "evidencePhotoUrls": ["<URL ảnh đã lưu>", "<URL ảnh đã lưu>"]
}
```

Dùng DTO này cập nhật giao diện. Khi mở lại biên bản, gọi `GET /api/v1/inspection/orders/{orderId}` và đọc `evidencePhotoUrls`. Gỡ nhánh gán ảnh đầu tiên vào `videoProofUrl`.

## 5. Lỗi và tiêu chí bàn giao

| Tình huống | Xử lý FE |
| --- | --- |
| Thiếu ảnh hoặc URL ảnh không hợp lệ | BE trả `400`. Giữ checklist và ghi chú để KTV sửa ảnh rồi nộp lại. |
| Chưa được phép xử lý đơn | Hiện lỗi từ BE và tải lại quyền hoặc thông tin phân công. |
| Start lỗi hoặc trạng thái đơn đã đổi | Tải lại đơn. Nếu kết quả đã nộp, chuyển sang màn xem biên bản. |
| Mất mạng khi nộp | Đọc lại chi tiết đơn để xác định kết quả trước khi cho thử lại. |
| Xem biên bản cũ | Giữ cách đọc ảnh lịch sử từ `note` khi `evidencePhotoUrls` vắng mặt. |

Kiểm thử cả `PASSED` và `FAILED`, ảnh upload lỗi, biên bản thiếu ảnh, checklist thiếu mục bắt buộc và mở lại biên bản bằng Buyer/Seller. Kiểm tra màn tại trung tâm không gọi `generate-seal` khi nộp và không hiển thị barcode tự sinh.

## Phân biệt luồng tại nhà

Seller lấy mã qua `POST /api/v1/inspection/orders/{orderId}/check-in-code` với body rỗng. Dùng `data.code` làm nội dung barcode hoặc QR. KTV được phân công quét và gửi `{"checkInCode":"<data.code>"}` vào `POST /api/v1/inspector/orders/{orderId}/start`. BE trả `INSPECTING` và `homeCheckedInAt` khi check-in thành công. Mã cũ hết hiệu lực khi đổi lịch hoặc KTV phụ trách.

