# Tối ưu Identity và ranh giới module — mục 2 đến 5

Thay đổi này giữ cấu trúc layer, các khóa ngoại, JSON/API và JWT claims hiện có. Không thay đổi cách ghi `last_login_at` của luồng đăng nhập (mục 1 không thuộc phạm vi được giao).

## 1. Migration và index

- **V63** thêm ba index phục vụ query đang có: `orders(buyer_id, created_at DESC)`, `orders(seller_id, created_at DESC)`, `negotiations(buyer_id, created_at DESC)`.
- Trước khi tạo, migration kiểm tra index tương đương theo key thực tế, không chỉ theo tên. Không tạo trùng index ghép có prefix tương đương. Không coi index partial, expression hoặc cột INCLUDE là index thay thế cho hai search key.
- **V64** thêm `roles.permissions_version BIGINT NOT NULL DEFAULT 0`, có check không âm. Mọi role hiện có được backfill 0; không thay đổi permission đã gán.
- V1–V62 không bị sửa. Migration không xóa dữ liệu hoặc FK.
- Script `scripts/database/audit_user_foreign_key_indexes.sql` chỉ đọc catalog, trả về FK và độ phủ index; không tự tạo toàn bộ index còn thiếu.

Xem [giải thích lựa chọn index và cách kiểm tra execution plan](USER_FOREIGN_KEY_INDEXES.md). V63 dùng CREATE INDEX thông thường trong transaction; cần chọn thời điểm triển khai phù hợp vì xây index giữ khóa chặn ghi trên bảng được đánh index.

## 2. Cache quyền và hiệu lực thay đổi bảo mật

Mỗi lượt xác thực HTTP hoặc WebSocket CONNECT thực hiện:

1. Parse và kiểm tra JWT một lần.
2. Đọc projection hiện tại từ DB: user ID, email, trạng thái tài khoản, emailVerified, tokenVersion và tổng permissionsVersion của các role đang gán. Projection không tải profile hoặc collection permission.
3. Từ chối tài khoản không ACTIVE hoặc tokenVersion không khớp ngay cả khi cache còn dữ liệu cũ.
4. Tái sử dụng principal cache nếu toàn bộ snapshot bảo mật khớp. Cache thiếu hoặc sai phiên bản mới tải đầy đủ role/permission.
5. Nếu dữ liệu thay đổi giữa hai lần đọc, thử lại một lần; không dùng một snapshot không nhất quán để xác thực.

Kết quả đã kiểm tra bằng PostgreSQL/JPA: cache lạnh dùng **2 query** (state + full details); cache ấm dùng **1 query nhỏ**. Đây là chi phí xác minh an toàn, không phải cam kết API nhanh hơn một tỷ lệ cụ thể hoặc một cơ chế bỏ qua DB khi DB lỗi.

`Role.addPermission/removePermission` tăng revision khi collection thực sự thay đổi, bao gồm luồng reviewer và seeder hiện có. Khi tập role của user thay đổi, các service hiện tại vẫn bump tokenVersion như trước. Vì vậy tổng revision là hợp lệ khi so sánh snapshot cùng user/tokenVersion. Chỉnh permission của role không bắt toàn bộ thành viên phải đăng nhập lại.

- Đổi/reset mật khẩu và thay đổi role: luồng revoke-all refresh token lên lịch xóa principal sau commit.
- Đổi trạng thái tài khoản: xóa principal sau commit.
- Đổi tập permission của role: xóa cache local sau commit; revision trong DB giúp các instance khác tự phát hiện thay đổi mà không cần Redis.
- Rollback không xóa principal đang phản ánh dữ liệu đã commit.
- Generation fence ngăn tác vụ đang tải dữ liệu cũ ghi trở lại cache sau một lần eviction/clear.
- Cache có giới hạn dung lượng, TTL và dọn entry hết hạn. Không giữ lock cache trong lúc truy vấn DB.

Các cấu hình có mặc định, không cần thêm biến môi trường để giữ hành vi đã chọn:

```properties
app.security.principal-cache.ttl-ms=60000
app.security.principal-cache.maximum-size=10000
app.security.principal-cache.cleanup-interval-ms=60000
```

Mọi sửa role/permission bằng SQL ngoài service phải cập nhật revision của role trong cùng transaction; sửa tập role trực tiếp phải tuân thủ chính sách tokenVersion hiện có. Xóa cache riêng một process không đủ cho thay đổi SQL thủ công trên hệ thống nhiều instance.

Phạm vi WebSocket vẫn là xác thực tại CONNECT. Session WebSocket đã mở không được tự động ngắt bởi thay đổi này; việc quản lý thu hồi session đang mở là một yêu cầu riêng như đã ghi trong coding standards. Request đã bắt đầu trước khi thay đổi bảo mật commit vẫn có thể đã đọc snapshot cũ; đây không phải cơ chế hủy các thao tác đang chạy.

## 3. Quyền database runtime

Đã thêm:

- `scripts/database/provision_runtime_role.sql`: script psql do administrator chạy trên đúng schema đã rà soát.
- `application-runtime.properties`: profile tùy chọn, tách datasource runtime với datasource Flyway và cố định Hibernate ở validate.

Role runtime được đọc/thêm/cập nhật user, khóa/vô hiệu hóa tài khoản và chạy DML nghiệp vụ bình thường. Role này không được DELETE/TRUNCATE users, DDL hoặc ghi Flyway history. Không thêm trigger cấm tuyệt đối xóa user; tài khoản quản trị vẫn có thể thực hiện bảo trì theo FK/SET NULL hiện có.

**Profile mặc định và thông tin kết nối hiện tại không bị thay đổi.** Script đã được kiểm tra trong PostgreSQL local riêng, chưa được chạy lên database ứng dụng. Muốn áp dụng giới hạn quyền trên deployment thật cần provision role, tạo login riêng, cấp secret và bật profile runtime theo [hướng dẫn triển khai](DATABASE_RUNTIME_ROLE_GUIDE.md). Không tự đổi account DB đang chạy sang account thiếu quyền.

## 4. Ranh giới module trong monolith

Giữ các module nghiệp vụ logic Identity, Listing, Order, Inspection, Shipping và Wallet trong cấu trúc layer hiện có. Không chuyển package sang feature root hoặc bỏ FK.

Thay đổi code cụ thể: ListingAccess lấy thông tin role qua `IdentityAccessService`, không trực tiếp inject UserRepository. Quy tắc ưu tiên hồ sơ seller đã duyệt, fallback SELLER/ADMIN, kiểm tra hạn chế bán và thông báo lỗi giữ nguyên. Các test kiến trúc bảo vệ những seam đại diện và việc phụ thuộc service interface.

Đây là bước củng cố ranh giới có kiểm thử; các transaction/repository/entity dùng chung đang tồn tại được liệt kê rõ, không coi toàn bộ ứng dụng đã được cô lập thành các module độc lập. Xem [quyền sở hữu và các ngoại lệ hiện tại](BACKEND_MODULE_BOUNDARIES.md).

## 5. Kiểm thử và triển khai

- Migration index: nâng V62→V63, bảo toàn dữ liệu, tránh index trùng, audit composite/partial/INCLUDE/invalid; workload tổng hợp 100.000 order + 100.000 negotiation đối chiếu page/count và EXPLAIN ANALYZE BUFFERS trước/sau.
- Auth: cache ấm vẫn từ chối user bị khóa, xóa hoặc đổi tokenVersion; permission revision làm mới quyền qua hai cache độc lập; rollback giữ quyền đã commit; kiểm tra số query và SQL shape trên PostgreSQL thật.
- Cache: TTL, dung lượng/LRU, generation fence và afterCommit/rollback.
- Quyền DB: thực thi script thật bằng psql trên schema local riêng; DML cần thiết được phép, xóa/truncate user, DDL và ghi lịch sử migration bị từ chối.
- Module: interface Identity thực sự được Listing sử dụng, kiểm tra dependency và các nhánh nghiệp vụ cũ.

Các test DB chỉ nhận URL loopback và database test riêng. Không sử dụng `.env` hoặc database từ deployment làm test fixture. Kết quả kiểm tra và phạm vi lệnh chạy được ghi cùng báo cáo hoàn thành; chưa chạy toàn bộ test suite không đồng nghĩa toàn bộ test cũ đều đạt.

Kết quả đợt này: **198/198 test liên quan đạt, package build thành công**. Chi tiết phạm vi và giới hạn tại [báo cáo kiểm chứng](IDENTITY_OPTIMIZATION_VERIFICATION.md).

Khi triển khai: backup và rà metadata trước; giữ Flyway bật để áp V63/V64; kiểm tra startup/migration và API hiện có; sau đó triển khai role runtime theo giai đoạn. Không dùng Hibernate create/create-drop hoặc sửa migration đã áp để thực hiện nâng cấp này.
