Bạn là Senior React TypeScript Developer. Chỉ sửa FE tại:
C:\Users\hieu.tuanz\Desktop\KY-9\SEP490\fe\SEP490_FE_SecondLife

Đọc contract BE đã triển khai trước khi sửa:
C:\Users\hieu.tuanz\Desktop\KY-9\SEP490\be\secondLife\docs\ADMIN_TOPUP_PACKAGES_API_GUIDE.md
Được đọc BE để đối chiếu, không sửa BE, không tự tạo API/response hoặc mô phỏng thành công.
Kiểm tra hướng dẫn repository và giữ nguyên thay đổi hiện có. Không commit/push/deploy.

Mục tiêu: hoàn thiện Admin → BẢNG GIÁ & CHIẾT KHẤU và modal ví/mua gói theo API V68.
Đây là mua lượt dịch vụ bằng tiền ví, không phải subscription. Nạp tiền SePay và mua gói là hai giao dịch riêng.

1. Rà soát các file hiện có, ưu tiên mở rộng:
- src/pages/AdminDashboardView.tsx
- src/components/modals/TopUpModal.tsx
- src/services/adminCreditPricingService.ts
- src/services/topupService.ts
- src/types/index.ts, các service ví/history có liên quan.
Tìm toàn bộ caller purchasePackage, gồm cả nhánh tự mua sau khi nhận tiền SePay.

2. Admin:
- Dùng GET /api/v1/admin/topup-packages, response.data, có cả gói ngừng bán.
- Form POST/PUT gồm name, description, postCredits, chatCredits, valuationCredits, price, active.
- Bỏ input discountPercentage. Hiển thị giá mua riêng/referencePrice và % tiết kiệm là dữ liệu BE chỉ đọc.
- Validate tên không trắng/max100, mô tả max255, số lượt nguyên không âm và tổng>0, giá>=1000, tối đa2 chữ số thập phân.
- Mở/ngừng bán qua PATCH /api/v1/admin/topup-packages/{id}/status body {active:boolean}.
- Thay thao tác Xóa bằng Ngừng bán có xác nhận. Không xóa dòng khỏi admin như đã mất dữ liệu;
  tải lại danh sách và hiển thị trạng thái. Có thể mở bán lại theo cùng ID.
- pricingAvailable=false: thông báo cần cấu hình đơn giá/giá gói; không hiển thị ưu đãi giả.
  Vẫn cho ngừng bán; mở bán có thể nhận409 và phải hiện thông báo BE.
- Sau CRUD hoặc đổi đơn giá, refresh cả đơn giá và các gói; đổi đơn giá có thể thay đổi ưu đãi của nhiều gói.
- Backend yêu cầu role ADMIN và ADMIN_PRICING_MANAGE. FE ẩn/khóa nút phù hợp, BE vẫn quyết định quyền.

3. Giao diện mua gói:
- GET /api/v1/topup/packages vẫn trả list trực tiếp. Chỉ render active=true, pricingAvailable=true.
- Dùng price là tiền cuối cùng, referencePrice là giá mua riêng, discountPercentage do BE tính.
- Giá gạch ngang chỉ khi referencePrice>price và pricingAvailable=true; tuyệt đối không tính ngược price/(1-discount/100).
- Bỏ chữ cố định Tiết kiệm tới35%. Nếu hiển thị mức lớn nhất, tính max từ % BE của các gói hợp lệ đang hiển thị.
- Gọi là Gói dịch vụ/Mua lượt sử dụng. Giữ nhóm Combo/Đăng tin/Chat AI/Định giá theo dữ liệu.
- API lỗi phải hiện lỗi và nút thử lại. Không fallback gói giả, ID pkg-1 hoặc giá mock để tiếp tục bán.

4. Mua và retry:
POST /api/v1/topup/purchase/{packageId} bắt buộc body:
{requestId:crypto.randomUUID(), packageVersion:pkg.packageVersion, expectedPrice:pkg.price}.
Response mới là ApiResponse<TopupPackagePurchaseResponse>, không còn là CreditBalanceResponse trực tiếp.
Đọc schema đầy đủ trong hướng dẫn BE.
- Khi user xác nhận, tạo và lưu một ý định mua trước POST, gắn với đúng tài khoản/package/version/price.
- Giữ nguyên requestId/body cho retry timeout, mất mạng, HTTP500 hoặc khi chưa biết request trước commit chưa.
  Không sinh UUID mới tự động cho mỗi lần click/retry. Disable nút khi đang gửi nhưng không coi đó là idempotency duy nhất.
- Có thể lưu ý định chưa rõ kết quả trong sessionStorage theo userId; khôi phục khi mở lại modal/reload.
  Không dùng lại ý định mua giữa các tài khoản; xử lý logout/đổi tài khoản đúng cách. Không lưu JWT vào key này.
- Sau thành công có data.id: hiện biên nhận, đánh dấu hoàn tất ý định cũ; mua thêm là thao tác xác nhận mới với UUID mới.
- 409 gói đổi/ngừng bán: tải lại, yêu cầu user xác nhận giá/quyền lợi mới. Không tự mua theo giá mới.
- 409 requestId dùng cho nội dung khác: hiện lỗi, không tự đổi key và gửi tiếp vì có thể đã có giao dịch.
- 400 thiếu tiền: cho nạp thêm qua SePay theo luồng hiện có. Nhận tiền nạp không có nghĩa đã mua thành công.
  Nhánh tự mua sau SePay cũng dùng đúng ý định/requestId, kiểm tra lỗi và chỉ báo mua thành công khi API mua thành công.
- Sau mua gọi lại wallet/my-credit; không cộng lượt hoặc trừ ví ở client bằng dữ liệu suy đoán.

5. Lịch sử:
GET /api/v1/topup/purchases?page=0&size=20 trả wrapper data với items/page/size/totalElements/totalPages/isLast.
GET /api/v1/topup/purchases/{purchaseId} trả wrapper data một biên nhận, yêu cầu CREDIT_READ_SELF.
- Thêm lịch sử mua gói và màn/modal chi tiết trong khu vực lịch sử hiện có; giữ lịch sử giao dịch ví cũ.
- Hiện tên gói snapshot, giá đã trả, từng loại lượt đã cấp, thời gian, mã giao dịch, số dư ví trước/sau.
- Dùng snapshot trong biên nhận, không lấy tên/giá/quyền lợi hiện tại của gói để dựng lại lịch sử.
- Các trường balanceAfter là số dư tại thời điểm mua, không phải số dư hiện tại. createdAt dùng ISO UTC, format giờ VN.
- Không có bản ghi V68 không có nghĩa giao dịch ví cũ đã mất: không dựng dữ liệu giả cho lịch sử cũ.
- Không cho chọn userId từ FE để đọc lịch sử người khác; owner do BE lấy từ JWT.

6. Loại bỏ thành công giả:
Rà soát getCreditPrices/updateCreditPrice, topupService và nhánh tự kích hoạt trong TopUpModal.
Không catch lỗi rồi return giá/gói giả hoặc object giống update thành công. Không toast Lưu thành công khi API thất bại.
Được giữ state cũ trên màn hình khi refresh lỗi, nhưng phải đánh dấu lỗi và chặn dùng giá chưa xác minh để mua.
Không tự gán quyền quản trị hoặc nới API khi403; dùng xử lý auth hiện có.

7. Kiểm thử và bàn giao:
- Chạy npm run lint, npm run build.
- Test các ca: CRUD admin; ngừng/mở bán; tính ưu đãi từBE; thiếu đơn giá; API500 không hiện thành công;
  ví đủ/thiếu; double click; retry cùngrequestId; response mất sau khi BE đã xử lý;
  giá/quyền lợi đổi trong lúc chờ nạp; gói ngừng bán; đổi tài khoản; xem biên nhận sau khi gói đổi tên/giá.
- Dùng mock API cho tình huống lỗi trong test, không đưa mock vào đường chạy production.
- Báo file thay đổi, kết quả kiểm thử và hạn chế còn lại. Chỉ sửa FE, không commit/push/deploy.
Bổ sung cho bảng đơn giá chuẩn:
GET /api/v1/admin/credit-pricing luôn trả đủ LISTING, AI_CHAT, VALUATION.
Loại chưa cấu hình có unitPrice=null, active=false; khai báo unitPrice: number | null ở FE,
hiển thị Chưa cấu hình, không dùng giá fallback hoặc hiểu null là miễn phí.
PUT /api/v1/admin/credit-pricing/{creditType} body {unitPrice:number} hỗ trợ cả cấu hình lần đầu và cập nhật.
Chỉ báo thành công sau response thật của BE và tải lại bảng giá/gói dịch vụ.
