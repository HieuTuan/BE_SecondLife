# Hướng dẫn migration V61: bổ sung khóa ngoại còn thiếu

## Phạm vi

`V61__restore_missing_foreign_keys.sql` khôi phục 46 quan hệ đã được các migration V11/V13/V16/V18/V21/V23/V24/V25/V32 khai báo. Một số database từng có bảng do Hibernate tạo nên `CREATE TABLE IF NOT EXISTS` trong migration cũ không bổ sung được các ràng buộc còn thiếu.

Migration không đổi API, entity, tên bảng/cột, số dư, nội dung hoặc thời gian của các dòng dữ liệu. Không sửa migration đã áp dụng và không repair lịch sử Flyway.

## Cách hoạt động

1. Bổ sung hai unique index bắt buộc cho các khóa ghép nếu chưa có index tương đương: `items(id, category_id)` và `order_commission_snapshots(id, order_id)`. Cột `id` đã là primary key nên không cần xóa/gộp dòng trùng.
2. Kiểm tra khóa ngoại bằng bảng và cặp cột tham chiếu, không dựa vào tên do Hibernate/Flyway đặt. Nếu đã có khóa tương đương, giữ nguyên khóa, trạng thái validate và quy tắc update/delete.
3. Thêm khóa thiếu bằng `NOT VALID`, sau đó thử `VALIDATE CONSTRAINT`.
4. Nếu dữ liệu cũ hợp lệ, khóa được validate hoàn toàn. Nếu có tham chiếu mồ côi, giữ nguyên các dòng cũ và giữ khóa ở trạng thái `NOT VALID`; PostgreSQL vẫn kiểm tra các tham chiếu mới/thay đổi. Flyway ghi NOTICE chỉ gồm tên khóa/bảng, không log dữ liệu người dùng.
5. Chỉ lỗi `foreign_key_violation` khi validate dữ liệu cũ được xử lý theo bước trên. Các lỗi schema/quyền/DDL khác tiếp tục làm migration thất bại và rollback theo transaction PostgreSQL/Flyway.

Không thêm khóa cho `wallet_transactions.reference_id`, các reference đa hình, ID nhà cung cấp, request ID, `ai_price_estimates.listing_id`, `ai_chat_sessions.post_id`, `role_permission_audit.permission_code` hoặc các thuộc tính snapshot cố ý giữ lịch sử. Các bảng cache/cấu hình độc lập vẫn độc lập.

Quy tắc xóa khôi phục đúng migration ban đầu: 42 quan hệ `NO ACTION`, 3 `SET NULL`, 1 `CASCADE` dành cho hồ sơ onboarding khi xóa user. Không dùng `DROP ... CASCADE` và không thay quy tắc của khóa đã tồn tại.

## Danh sách quan hệ được kiểm tra/bổ sung

| Bảng và cột nguồn | Bảng và cột đích | ON DELETE | Nguồn khai báo |
|---|---|---|---|
| `category_question_templates(category_id)` | `categories(id)` | `NO ACTION` | `V21` |
| `category_question_templates(item_id, category_id)` | `items(id, category_id)` | `NO ACTION` | `V21` |
| `commission_rule_audits(actor_id)` | `users(id)` | `NO ACTION` | `V32` |
| `commission_rule_audits(rule_id)` | `commission_rules(id)` | `NO ACTION` | `V32` |
| `commission_rules(category_id)` | `categories(id)` | `NO ACTION` | `V32` |
| `commission_rules(updated_by)` | `users(id)` | `NO ACTION` | `V32` |
| `credit_balances(credit_type)` | `credit_types(code)` | `NO ACTION` | `V11` |
| `credit_balances(user_id)` | `users(id)` | `NO ACTION` | `V11` |
| `credit_discount_tiers(updated_by)` | `users(id)` | `SET NULL` | `V11` |
| `credit_ledger(credit_type)` | `credit_types(code)` | `NO ACTION` | `V11` |
| `credit_ledger(purchase_id)` | `credit_purchases(id)` | `NO ACTION` | `V11` |
| `credit_ledger(user_id)` | `users(id)` | `NO ACTION` | `V11` |
| `credit_pricing_rules(credit_type)` | `credit_types(code)` | `NO ACTION` | `V11` |
| `credit_pricing_rules(updated_by)` | `users(id)` | `SET NULL` | `V11` |
| `credit_purchases(discount_tier_id)` | `credit_discount_tiers(id)` | `SET NULL` | `V11` |
| `credit_purchases(user_id)` | `users(id)` | `NO ACTION` | `V11` |
| `order_commission_snapshots(captured_by)` | `users(id)` | `NO ACTION` | `V32` |
| `order_commission_snapshots(order_id)` | `orders(id)` | `NO ACTION` | `V32` |
| `order_commission_snapshots(rule_id)` | `commission_rules(id)` | `NO ACTION` | `V32` |
| `order_settlements(buyer_id)` | `users(id)` | `NO ACTION` | `V32` |
| `order_settlements(commission_snapshot_id, order_id)` | `order_commission_snapshots(id, order_id)` | `NO ACTION` | `V32` |
| `order_settlements(order_id)` | `orders(id)` | `NO ACTION` | `V32` |
| `order_settlements(seller_id)` | `users(id)` | `NO ACTION` | `V32` |
| `orders(shipping_quote_id)` | `shipping_quotes(id)` | `NO ACTION` | `V24` |
| `payment_callback_events(payment_intent_id)` | `payment_intents(id)` | `NO ACTION` | `V11` |
| `payment_intents(purchase_id)` | `credit_purchases(id)` | `NO ACTION` | `V11` |
| `permission_assignable_roles(role_code)` | `roles(code)` | `NO ACTION` | `V16` |
| `posts(category_id)` | `categories(id)` | `NO ACTION` | `V21` |
| `posts(item_id, category_id)` | `items(id, category_id)` | `NO ACTION` | `V21` |
| `posts(reviewed_by)` | `users(id)` | `NO ACTION` | `V23` |
| `role_permission_audit(changed_by)` | `users(id)` | `NO ACTION` | `V13` |
| `role_permission_audit(role_id)` | `roles(id)` | `NO ACTION` | `V13` |
| `seller_onboarding(user_id)` | `users(id)` | `CASCADE` | `V25` |
| `shipment_events(shipment_id)` | `shipments(id)` | `NO ACTION` | `V24` |
| `shipments(buyer_id)` | `users(id)` | `NO ACTION` | `V24` |
| `shipments(inspection_order_id)` | `inspection_orders(id)` | `NO ACTION` | `V24` |
| `shipments(order_id)` | `orders(id)` | `NO ACTION` | `V24` |
| `shipments(requested_by)` | `users(id)` | `NO ACTION` | `V24` |
| `shipments(seller_id)` | `users(id)` | `NO ACTION` | `V24` |
| `shipping_pickup_addresses(user_id)` | `users(id)` | `NO ACTION` | `V24` |
| `shipping_quotes(buyer_id)` | `users(id)` | `NO ACTION` | `V24` |
| `shipping_quotes(consumed_order_id)` | `orders(id)` | `NO ACTION` | `V24` |
| `shipping_quotes(post_id)` | `posts(id)` | `NO ACTION` | `V24` |
| `user_role_audit(changed_by)` | `users(id)` | `NO ACTION` | `V18` |
| `user_role_audit(role_code)` | `roles(code)` | `NO ACTION` | `V18` |
| `user_role_audit(user_id)` | `users(id)` | `NO ACTION` | `V18` |

## Chạy trên môi trường ứng dụng

1. Tạo backup mới của database đích trước lần triển khai và giữ bản backup ngoài repository.
2. Triển khai code có V61, giữ cấu hình:

```ini
JPA_DDL_AUTO=none
FLYWAY_ENABLED=true
```

3. Khởi động BE. Flyway kiểm tra lịch sử và chạy V61 nếu chưa áp dụng. Không chạy Hibernate `create`/`create-drop` và không tự sửa checksum/repair nếu Flyway báo lịch sử không khớp.
4. Kiểm tra V61 và các khóa chờ xác thực:

```sql
SELECT version, description, success
FROM public.flyway_schema_history
WHERE version = '61';

SELECT c.conrelid::regclass AS child_table,
       c.conname,
       c.confrelid::regclass AS parent_table,
       pg_get_constraintdef(c.oid) AS definition
FROM pg_constraint c
WHERE c.contype = 'f'
  AND c.connamespace = 'public'::regnamespace
  AND NOT c.convalidated
ORDER BY c.conrelid::regclass::text, c.conname;
```

Nếu truy vấn thứ hai không trả dòng nào, không còn FK chờ validate trong schema public. Nếu có dòng, cần kiểm tra từng quan hệ: truy vấn cũng liệt kê các khóa NOT VALID đã tồn tại trước V61 mà migration cố ý giữ nguyên trạng thái. NOT VALID không tự chứng minh có dữ liệu mồ côi; hãy kiểm tra số dòng tham chiếu không hợp lệ trước khi xử lý dữ liệu.

Ví dụ đếm credit balance tham chiếu user không tồn tại:

```sql
SELECT count(*) AS orphan_rows
FROM public.credit_balances b
LEFT JOIN public.users u ON u.id = b.user_id
WHERE b.user_id IS NOT NULL AND u.id IS NULL;
```

Chỉ xử lý dữ liệu mồ côi khi đã xác minh nguồn dữ liệu và có quyết định nghiệp vụ phù hợp. Sau khi xác nhận dữ liệu hợp lệ hoặc xử lý dữ liệu đã được phê duyệt, validate bằng đúng tên khóa trả về ở cột conname. Ví dụ dưới đây chỉ áp dụng nếu khóa thực tế có tên fk_v61_credit_balances_user_id:

```sql
ALTER TABLE public.credit_balances
    VALIDATE CONSTRAINT fk_v61_credit_balances_user_id;
```

Flyway chạy V61 một lần; khởi động lại BE không tự sửa các dòng mồ côi hoặc tự validate lại các khóa này.

## Kiểm chứng

- `MissingForeignKeysMigrationTest`: nâng cấp từ V60; giữ nguyên dòng và snapshot tiền; chặn tham chiếu sai mới; giữ dữ liệu mồ côi cũ; không tạo trùng FK có tên khác; khôi phục unique/FK ghép danh mục và quyết toán; giữ `SET NULL`; giữ lịch sử AI khi xóa tin nháp.
- `WalletTransactionDetailsMigrationTest` được giới hạn rõ `target("60")` vì kiểm thử này chỉ kiểm tra riêng V60.
- Bản sao local của backup ngày 27/10/2026: Flyway áp dụng đúng một migration V61; chạy lại không có migration mới; 76 bảng ứng dụng có cùng số dòng và fingerprint nội dung trước/sau; FK tăng từ 94 lên 140; không có khóa chờ validate.
- Kết quả test rộng hơn được báo trong bàn giao. Kiểm chứng local không thay đổi database Supabase đang sử dụng.

Tham khảo: [PostgreSQL ALTER TABLE: NOT VALID và VALIDATE CONSTRAINT](https://www.postgresql.org/docs/current/sql-altertable.html).

## Kết quả kiểm thử lần triển khai này

- 8/8 test migration đạt; đóng gói BE được kiểm tra riêng bằng Maven.
- Nhóm liên quan chạy tách JVM: 114 test, 112 đạt, 2 failures, 0 errors, 0 skipped. Hai failures là ReadQueryIntegrationTest.buyerOrdersKeepWalletAndBankPaymentDetailsWithBoundedQueries và ReadQueryIntegrationTest.sellerOrdersKeepPageAndBankDetailsWithBoundedQueries (giới hạn 3 câu SQL, thực tế 5). Cả hai lỗi đã được tái hiện chính xác trên bản sao source chưa có V61.
- Toàn bộ mvn test: 1215 test, 39 failures, 74 errors, 0 skipped. Có giới hạn 20 kết nối của PostgreSQL local khi nhiều Spring context được cache, cùng các lỗi assertion/fixture ngoài phạm vi. Một số lỗi quyền, seed và số migration đã được đối chứng trên V60; không tuyên bố toàn bộ suite đạt.
