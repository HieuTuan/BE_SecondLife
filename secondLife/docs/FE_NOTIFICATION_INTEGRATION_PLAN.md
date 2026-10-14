# KẾ HOẠCH TRIỂN KHAI TÍCH HỢP HỆ THỐNG THÔNG BÁO CHO FRONTEND (FE INTEGRATION PLAN)

> **Tài liệu hướng dẫn kỹ thuật dành cho lập trình viên Frontend (Web & Mobile)**
> **Hệ thống**: SecondLife Platform
> **Mục tiêu**: Tích hợp chuông thông báo (Notification Bell), Trung tâm thông báo (Notification Center), kết nối thời gian thực qua WebSocket STOMP và điều hướng Deep Link theo từng Role.

---

## 1. Tổng quan Kiến trúc Giao tiếp (Communication Architecture)

Hệ thống thông báo SecondLife kết hợp 2 kênh giao tiếp:
1. **REST API (HTTP)**: Khởi tạo dữ liệu khi tải trang (Badge count, danh sách phân trang, đánh dấu đã đọc, xóa thông báo).
2. **WebSocket STOMP (Real-time)**: Nhận thông báo tức thì khi có sự kiện nghiệp vụ xảy ra trên sàn mà không cần reload/polling, tự động tăng badge quả chuông và hiển thị Toast alert.

```
+-----------------------------------------------------------------------------------+
|                                 FRONTEND (FE)                                     |
|  [Header Notification Bell]  <--->  [Dropdown Popover]  <--->  [/notifications]   |
+-----------------------------------------------------------------------------------+
         |                                                       ^
         | (1) REST API: GET /unread-count                       | (2) WebSocket STOMP:
         |               GET /notifications                      |     Sub: /user/queue/notifications
         v               PATCH /read, DELETE...                  |
+-----------------------------------------------------------------------------------+
|                             BACKEND SPRING BOOT 4                                 |
|  [NotificationController]                           [NotificationServiceImpl]     |
|             |                                                  |                  |
|             v                                                  v                  |
|   [(Database: V42 notifications)]                    [SimpMessagingTemplate]      |
+-----------------------------------------------------------------------------------+
```

---

## 2. TypeScript Data Contracts (Type Definitions)

Frontend tạo file `types/notification.ts` định nghĩa các kiểu dữ liệu chuẩn:

```typescript
// types/notification.ts

export type NotificationType = 
  | 'ORDER'         // Đơn hàng mua/bán thông thường
  | 'INSPECTION'    // Đơn kiểm định sản phẩm
  | 'SHIPPING'      // Tiến độ vận chuyển Lalamove
  | 'LISTING'       // Trạng thái bài đăng tin
  | 'NEGOTIATION'   // Thương lượng / Trả giá
  | 'WALLET'        // Nạp tiền ví, hoàn tiền, nhận doanh thu
  | 'EKYC'          // Xác minh hồ sơ người bán
  | 'SECURITY'      // Cảnh báo bảo mật tài khoản
  | 'SYSTEM';       // Thông báo chung từ hệ thống

export type ReferenceType = 
  | 'ORDER'
  | 'INSPECTION_ORDER'
  | 'POST'
  | 'NEGOTIATION'
  | 'WALLET_TRANSACTION'
  | 'USER'
  | 'SYSTEM';

export interface NotificationItem {
  id: string;                      // UUID thông báo
  title: string;                   // Tiêu đề ngắn gọn
  message: string;                 // Nội dung thông báo
  type: NotificationType;          // Phân loại để hiển thị icon/màu sắc
  referenceId: string | null;      // ID đối tượng nghiệp vụ (orderId, postId...)
  referenceType: ReferenceType | null;
  actionUrl: string | null;        // Deep link FE cần redirect tới
  isRead: boolean;                 // Trạng thái đã đọc
  readAt: string | null;           // ISO Timestamp thời điểm đọc
  createdAt: string;               // ISO Timestamp thời điểm tạo
}

export interface UnreadCountResponse {
  unreadCount: number;             // Số lượng thông báo chưa đọc
}

export interface NotificationActionResponse {
  success: boolean;
  affectedRows: number;            // Số lượng thông báo đã xử lý
  message: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;                    // 0-indexed
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
  first: boolean;
  empty: boolean;
}
```

---

## 3. Chi tiết Danh sách REST API (Backend Endpoints)

Base URL: `/api/v1/notifications`  
Yêu cầu Header: `Authorization: Bearer <jwt_access_token>`

### 3.1. Lấy số lượng thông báo chưa đọc (Unread Count)
Dùng để render badge số đỏ trên quả chuông ở Header/Navbar khi user vừa đăng nhập hoặc tải trang.

- **Method**: `GET`
- **URL**: `/api/v1/notifications/unread-count`
- **Response**: `200 OK`
```json
{
  "unreadCount": 3
}
```

### 3.2. Lấy danh sách thông báo có phân trang & lọc (Get Notifications)
Dùng cho Dropdown xem nhanh và Trang trung tâm thông báo `/notifications`.

- **Method**: `GET`
- **URL**: `/api/v1/notifications`
- **Query Parameters**:
  - `page`: Số trang (0-indexed, mặc định `0`).
  - `size`: Số lượng mỗi trang (mặc định `10` hoặc `20`).
  - `unreadOnly`: `true` (chỉ lấy thông báo chưa đọc), `false` (lấy tất cả). Mặc định `false`.
  - `type`: Lọc theo loại (`ORDER`, `INSPECTION`, `SHIPPING`, `LISTING`, `NEGOTIATION`, `WALLET`, `EKYC`...).
- **Response**: `200 OK`
```json
{
  "content": [
    {
      "id": "7fa84b80-1a2b-4e89-b001-c529d3327d81",
      "title": "Đơn hàng đang được giao #ord_001",
      "message": "Đơn hàng #ord_001 đã được tài xế lấy và đang trên đường giao tới bạn.",
      "type": "SHIPPING",
      "referenceId": "23df0112-9c12-4c22-9214-3efac7191312",
      "referenceType": "ORDER",
      "actionUrl": "/buyer/orders/23df0112-9c12-4c22-9214-3efac7191312",
      "isRead": false,
      "readAt": null,
      "createdAt": "2026-10-14T06:45:00Z"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 25,
  "totalPages": 3,
  "last": false,
  "first": true,
  "empty": false
}
```

### 3.3. Đánh dấu 1 thông báo là đã đọc (Mark as Read)
Kích hoạt khi người dùng click vào thông báo trong danh sách hoặc bấm icon "Đã đọc".

- **Method**: `PATCH`
- **URL**: `/api/v1/notifications/{id}/read`
- **Response**: `200 OK` (Trả về `NotificationItem` với `isRead: true`, `readAt: ISO_TIME`).

### 3.4. Đánh dấu tất cả thông báo là đã đọc (Mark All as Read)
Kích hoạt khi người dùng click nút "Đánh dấu tất cả đã đọc" (Mark all as read) trên Header hoặc Dropdown.

- **Method**: `PATCH`
- **URL**: `/api/v1/notifications/mark-all-read`
- **Response**: `200 OK`
```json
{
  "success": true,
  "affectedRows": 3,
  "message": "Đã đánh dấu 3 thông báo là đã đọc"
}
```

### 3.5. Xóa mềm 1 thông báo cụ thể (Delete One)
Kích hoạt khi người dùng bấm nút xóa (icon thùng rác) cạnh từng thông báo.

- **Method**: `DELETE`
- **URL**: `/api/v1/notifications/{id}`
- **Response**: `204 No Content`

### 3.6. Xóa tất cả thông báo đã đọc (Clear Read)
Kích hoạt khi người dùng chọn dọn dẹp các thông báo đã xem.

- **Method**: `DELETE`
- **URL**: `/api/v1/notifications/clear-read`
- **Response**: `200 OK`
```json
{
  "success": true,
  "affectedRows": 15,
  "message": "Đã xóa 15 thông báo đã đọc"
}
```

### 3.7. Xóa toàn bộ thông báo (Clear All)
Kích hoạt khi người dùng chọn dọn sạch toàn bộ hộp thư thông báo.

- **Method**: `DELETE`
- **URL**: `/api/v1/notifications/clear-all`
- **Response**: `200 OK`
```json
{
  "success": true,
  "affectedRows": 25,
  "message": "Đã xóa toàn bộ 25 thông báo"
}
```

---

## 4. Hướng dẫn Tích hợp WebSocket STOMP (Real-time Notification)

Backend sử dụng Spring WebSocket STOMP có xác thực qua JWT token.

### 4.1. Cài đặt thư viện
```bash
npm install @stomp/stompjs sockjs-client
# hoặc yarn add @stomp/stompjs sockjs-client
```

### 4.2. Khởi tạo WebSocket Client (ví dụ `services/websocketService.ts`)

```typescript
// services/websocketService.ts
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { NotificationItem } from '../types/notification';

const WS_URL = process.env.NEXT_PUBLIC_WS_URL || 'http://localhost:8080/ws';

class NotificationWebSocketClient {
  private client: Client | null = null;
  private onNotificationCallback: ((item: NotificationItem) => void) | null = null;

  public connect(token: string, onNotification: (item: NotificationItem) => void) {
    this.onNotificationCallback = onNotification;

    this.client = new Client({
      // Sử dụng SockJS fallback
      webSocketFactory: () => new SockJS(WS_URL),
      connectHeaders: {
        Authorization: `Bearer ${token}`
      },
      debug: (str) => {
        if (process.env.NODE_ENV === 'development') console.log('[STOMP]', str);
      },
      reconnectDelay: 5000, // Tự động reconnect sau 5 giây nếu đứt mạng
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    this.client.onConnect = () => {
      console.log('✅ Connected to WebSocket STOMP server');
      
      // Subscribe kênh cá nhân của user
      this.client?.subscribe('/user/queue/notifications', (message: IMessage) => {
        try {
          const notification: NotificationItem = JSON.parse(message.body);
          if (this.onNotificationCallback) {
            this.onNotificationCallback(notification);
          }
        } catch (err) {
          console.error('Failed to parse incoming notification:', err);
        }
      });
    };

    this.client.onStompError = (frame) => {
      console.error('❌ Broker reported error: ' + frame.headers['message']);
    };

    this.client.activate();
  }

  public disconnect() {
    if (this.client && this.client.active) {
      this.client.deactivate();
      console.log('🔌 Disconnected WebSocket');
    }
  }
}

export const notificationWsClient = new NotificationWebSocketClient();
```

### 4.3. Xử lý sự kiện khi nhận thông báo real-time
Khi nhận được message từ `/user/queue/notifications`:
1. **Âm thanh & Toast Alert**:
   - Phát âm thanh ngắn thông báo (`new Audio('/sounds/notification.mp3').play()`).
   - Hiển thị Toast Popup (Ant Design `notification.open`, React Toastify, Sonner, v.v.) hiển thị: `title`, `message`, icon theo `type`, và nút bấm "Xem chi tiết" dẫn tới `actionUrl`.
2. **Cập nhật Global State**:
   - Tăng `unreadCount` lên `+1`.
   - Nếu Dropdown hoặc Trang `/notifications` đang mở: chèn `notification` mới lên vị trí đầu tiên của danh sách.

---

## 5. Bản đồ Điều hướng Deep Link (Action URL Routing Map)

Mỗi thông báo trả về trường `actionUrl`. Frontend chỉ cần gọi hàm điều hướng (Next.js `router.push(item.actionUrl)` hoặc React Router `navigate(item.actionUrl)`) sau khi gọi API mark-as-read:

| Loại thông báo (`type`) | Nghiệp vụ cụ thể | `actionUrl` mẫu | Màn hình FE tương ứng |
|---|---|---|---|
| `ORDER` | Đặt hàng thành công / Hủy đơn | `/buyer/orders/{orderId}` | Chi tiết đơn mua của Buyer |
| `ORDER` | Có đơn hàng mới cần gói | `/seller/orders/{orderId}` | Chi tiết đơn bán của Seller |
| `SHIPPING` | Đơn hàng đang giao / Đã giao | `/buyer/orders/{orderId}` | Tiến độ giao hàng & tracking Lalamove |
| `INSPECTION` | Yêu cầu kiểm định mới | `/seller/inspections/{id}` | Màn hình Seller duyệt/phí kiểm định |
| `INSPECTION` | Phản hồi kiểm định / Kết quả | `/buyer/inspections/{id}` | Chi tiết biên bản kiểm định Buyer |
| `INSPECTION` | KTV được phân công đơn | `/inspector/inspections/{id}` | Màn hình thực thi của Inspector |
| `NEGOTIATION` | Đề xuất giá mới / Hủy deal | `/seller/negotiations` | Quản lý danh sách trả giá của Seller |
| `NEGOTIATION` | Người bán đồng ý / từ chối | `/posts/{postId}` | Chi tiết bài đăng để Buyer chốt đơn |
| `LISTING` | Bài đăng được duyệt | `/posts/{postId}` | Xem bài đăng công khai trên sàn |
| `LISTING` | Bài đăng bị từ chối duyệt | `/seller/posts/{postId}/edit` | Màn hình chỉnh sửa bài viết của Seller |
| `WALLET` | Nạp tiền ví, hoàn tiền, nhận doanh thu | `/wallet` | Quản lý số dư & lịch sử giao dịch ví |
| `EKYC` | Kết quả duyệt hồ sơ Người bán | `/seller/dashboard` hoặc `/seller/onboarding` | Dashboard người bán hoặc form nộp lại |

---

## 6. Lộ trình Triển khai UI theo từng Phase (Implementation Phases)

### Phase 1: Service Layer & State Management (Ngày 1)
- [ ] Tạo file types `types/notification.ts`.
- [ ] Xây dựng `api/notificationApi.ts` với đầy đủ 7 endpoint Axios/Fetch.
- [ ] Viết WebSocket Client kết nối STOMP `@stomp/stompjs`.
- [ ] Tạo Context/Zustand Store `useNotificationStore`:
  - `unreadCount`: số lượng chưa đọc.
  - `notifications`: danh sách item hiện tại.
  - Các actions: `fetchUnreadCount()`, `fetchNotifications()`, `markAsRead(id)`, `markAllAsRead()`, `deleteOne(id)`, `clearRead()`, `clearAll()`, `handleIncomingRealtime(item)`.

### Phase 2: Header Notification Bell & Dropdown Quick View (Ngày 2)
- [ ] Thêm Icon Quả chuông trên Header/Navbar.
- [ ] Render Badge màu đỏ hiển thị `unreadCount` (nếu > 99 thì hiển thị `99+`, nếu = 0 thì ẩn badge).
- [ ] Popover / Dropdown hiển thị khi click quả chuông:
  - Header dropdown: "Thông báo" + Nút "Đánh dấu tất cả đã đọc" (`markAllAsRead`).
  - Body: Danh sách 5 thông báo mới nhất (Hiển thị icon theo `type`, title đậm, message, thời gian `timeago` như "5 phút trước", chấm tròn xanh cho thông báo chưa đọc).
  - Footer dropdown: Link "Xem tất cả thông báo" dẫn tới `/notifications`.
- [ ] Click vào 1 thông báo trong Dropdown: Gọi `markAsRead(item.id)` -> Đóng Dropdown -> Chuyển hướng tới `item.actionUrl`.

### Phase 3: Trang Trung Tâm Thông Báo Toàn Diện (`/notifications`) (Ngày 3)
- [ ] Layout trang `/notifications`:
  - Header: Tiêu đề "Trung tâm thông báo" + Các nút thao tác hàng loạt:
    - Nút "Đánh dấu tất cả đã đọc"
    - Menu xổ xuống "Dọn dẹp": "Xóa thông báo đã đọc" (`clear-read`), "Xóa tất cả thông báo" (`clear-all`).
  - Thanh Tabs / Bộ lọc:
    - Tab "Tất cả" (`unreadOnly=false`)
    - Tab "Chưa đọc" (`unreadOnly=true`)
    - Dropdown filter theo loại: "Tất cả loại", "Đơn hàng", "Kiểm định", "Vận chuyển", "Ví tiền", v.v.
- [ ] Danh sách thông báo:
  - Render Card từng thông báo với layout đẹp mắt, hover effect, icon phân loại màu sắc:
    - `ORDER`: Màu xanh dương (Shopping Bag icon)
    - `INSPECTION`: Màu tím (Shield Check icon)
    - `SHIPPING`: Màu cam (Truck icon)
    - `WALLET`: Màu xanh lá cây (Wallet icon)
    - `LISTING`: Màu chàm (Document icon)
    - `NEGOTIATION`: Màu vàng (Tag icon)
    - `SECURITY`: Màu đỏ (Alert Triangle icon)
  - Nút Xóa (thùng rác) ở mỗi dòng: Hiện modal confirm xác nhận xóa -> gọi `DELETE /api/v1/notifications/{id}`.
- [ ] Phân trang (Pagination) hoặc Infinite Scroll với component phân trang chuẩn.
- [ ] Empty State: Hình minh họa "Không có thông báo nào" khi danh sách trống.

### Phase 4: Tích hợp âm thanh & Toast Notification (Ngày 4)
- [ ] Tích hợp Toast banner nổi góc màn hình khi đang ở bất kỳ trang nào và nhận được thông báo mới từ WebSocket.
- [ ] Âm thanh thông báo nhẹ nhàng (kèm tùy chọn toggle "Bật/Tắt âm thanh thông báo" trong Cài đặt nếu cần).
- [ ] Kiểm thử luồng đa thiết bị (mở 2 tab hoặc 2 trình duyệt khác nhau kiểm tra real-time).

---

## 7. Checklist Kiểm thử Dành cho Frontend (QA / Testing Checklist)

| STT | Kịch bản kiểm thử (Test Scenario) | Kết quả mong đợi |
|---|---|---|
| 1 | Người dùng đăng nhập vào hệ thống | Badge quả chuông hiển thị đúng số thông báo chưa đọc từ API `GET /unread-count`. WebSocket kết nối thành công. |
| 2 | Backend phát sinh thông báo mới (ví dụ có đơn hàng mới) | Pop-up Toast hiện góc màn hình, badge tăng +1, thông báo mới xuất hiện đầu danh sách không cần F5. |
| 3 | Click vào 1 thông báo chưa đọc | Trạng thái chuyển thành đã đọc (chấm xanh biến mất), badge giảm đi 1, trình duyệt chuyển hướng đúng `actionUrl`. |
| 4 | Click "Đánh dấu tất cả đã đọc" | Toàn bộ thông báo chuyển sang trạng thái đã đọc, badge trên quả chuông biến mất (về 0). |
| 5 | Bấm icon xóa 1 thông báo | Thông báo biến mất khỏi danh sách, nếu là thông báo chưa đọc thì badge giảm đi 1. |
| 6 | Bấm "Xóa thông báo đã đọc" | Các thông báo có `isRead: true` bị xóa hết, các thông báo chưa đọc vẫn giữ nguyên. |
| 7 | Bấm "Xóa toàn bộ" | Danh sách thông báo trống trơn, hiển thị Empty State. |
| 8 | Chuyển đổi Tab "Tất cả" và "Chưa đọc" | Danh sách lọc chính xác theo param `unreadOnly`. |
| 9 | Mất mạng đột ngột rồi có mạng lại | WebSocket client tự động reconnect trong 5s và đồng bộ lại số đếm. |

---
*Tài liệu này được tạo tự động để hỗ trợ đội ngũ Frontend tích hợp nhanh chóng, chính xác và đồng bộ hoàn toàn với Backend.*
