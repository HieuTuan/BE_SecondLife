# Khôi phục Flyway — đã xử lý ngày 01/11/2026

## Nguyên nhân
V65 gói dịch vụ trên nhánh local trùng số với V65 AI template đã áp trên DB dùng chung.
DB còn có V66/V67 kiểm định nhưng nhánh local thiếu hai file nguồn.

## Source đã sửa
- Khôi phục V65__ai_template_proposals_and_unique_constraints.sql từ origin/feature/ai-template-proposal-and-markdown-desc, checksum -855031162 khớp DB.
- Gói dịch vụ dùng V68__topup_package_sales_and_purchase_receipts.sql.
- Dọn Maven target bằng clean, build/test lại: 70 test liên quan đạt.
- Không còn migration trùng phiên bản trong source hoặc resources build.

## Thao tác DB đã được người dùng chấp thuận
Người dùng xác nhận: Đồng ý repair V66/V67 và áp V68.
Đã sao lưu toàn bộ flyway_schema_history trước khi thao tác:
C:\Users\hieu.tuanz\.codex\visualizations\2026\10\20\01a1589d-4166-7192-b820-b2c4f7cec0ea\flyway-history-before-repair-d49ee004-19a2-48cb-89a6-e7581a9a4fca.json

Script kiểm tra chỉ có V66/V67 thiếu nguồn và duy nhất V68 pending trước khi thực hiện.
Flyway repair kết quả:
- migrationsDeleted: V66, V67 (đánh dấu đã bỏ nguồn trong schema history).
- migrationsAligned: rỗng; không đổi checksum migration khác.
- migrationsRemoved: rỗng; không xóa bản ghi migration lỗi khác.

Sau đó Flyway migrate áp đúng 1 migration: V68.
Kết quả validate sau cùng: true.
Không xóa bảng/dữ liệu kiểm định, ví, giao dịch hoặc credit cũ.
V68 chỉ thêm cấu trúc quản lý gói dịch vụ và biên nhận mua.

## Vận hành tiếp theo
Chạy lại SecondLifeApplication trong IntelliJ. Không dùng ddl-auto=create hoặc tắt validation.
Phối hợp các nhánh khác khi lấy lại nguồn V66/V67: DB đã đánh dấu chúng là đã bỏ nguồn;
không tự chạy lại SQL kiểm định vì cấu trúc/dữ liệu của chúng vẫn đang tồn tại.
Bản sao history giữ nguyên trạng thái trước repair để phục vụ đối chiếu/khôi phục có kiểm soát.
Không thêm repair tự động vào startup của ứng dụng.
