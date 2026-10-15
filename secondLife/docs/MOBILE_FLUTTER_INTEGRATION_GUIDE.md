# HƯỚNG DẪN TÍCH HỢP TOÀN DIỆN CHO MOBILE FLUTTER (MOBILE FLUTTER INTEGRATION GUIDE)

**Dự án**: SecondLife Marketplace – Mobile Clients (Buyer App, Seller App, Inspector App)  
**Ngày lập**: 14/10/2026  
**Công nghệ**: Flutter (Dart) 3.x / Android & iOS  
**Mục tiêu**: Cung cấp tài liệu kỹ thuật chi tiết, schema dữ liệu và mã nguồn mẫu bằng Dart để đội ngũ Mobile Flutter triển khai 3 ứng dụng di động kết nối với Spring Boot Backend.

---

## MỤC LỤC
1. [Kiến trúc Chung & Cấu hình Môi trường](#1-kiến-trúc-chung--cấu-hình-môi-trường)
2. [Hệ thống Push Notification (Firebase Cloud Messaging - FCM)](#2-hệ-thống-push-notification-firebase-cloud-messaging---fcm)
3. [Tích hợp dành cho App BUYER (Người mua)](#3-tích-hợp-dành-cho-app-buyer-người-mua)
4. [Tích hợp dành cho App INSPECTOR (Kỹ thuật viên Kiểm định)](#4-tích-hợp-dành-cho-app-inspector-kỹ-thuật-viên-kiểm-định)
5. [Tích hợp dành cho App SELLER (Người bán)](#5-tích-hợp-dành-cho-app-seller-người-bán)
6. [Bảng Mã Lỗi (Error Codes) & Best Practices cho Flutter](#6-bảng-mã-lỗi-error-codes--best-practices-cho-flutter)

---

## 1. KIẾN TRÚC CHUNG & CẤU HÌNH MÔI TRƯỜNG

### 1.1. Base URL & Headers
- **Local Dev / Emulator Android**: `http://10.0.2.2:8080`
- **Local Dev / iOS Simulator**: `http://localhost:8080`
- **LAN Device Testing**: `http://<IP_LAN_MAY_CHỦ>:8080`
- **Staging / Production**: `https://api.secondlife.tech`

**Standard Request Headers**:
```http
Content-Type: application/json
Accept: application/json
Authorization: Bearer <access_token>
```

---

## 2. HỆ THỐNG PUSH NOTIFICATION (FIREBASE CLOUD MESSAGING - FCM)

> Áp dụng cho **cả 3 ứng dụng** (Buyer, Seller, Inspector).

### 2.1. Cấu hình Dependencies trong `pubspec.yaml`
```yaml
dependencies:
  firebase_core: ^3.6.0
  firebase_messaging: ^15.1.3
  flutter_local_notifications: ^17.2.3
```

### 2.2. Luồng Vòng đời Thiết bị (Device Token Lifecycle)
1. **Khởi động App & Đăng nhập**:
   - Khởi tạo Firebase $\rightarrow$ Gọi `FirebaseMessaging.instance.getToken()`.
   - Gửi token lên Backend qua endpoint `POST /api/v1/notifications/devices`.
2. **Lắng nghe Token Refresh**:
   - `FirebaseMessaging.instance.onTokenRefresh.listen(...)` $\rightarrow$ gọi lại API để Backend cập nhật token mới.
3. **Đăng xuất (Logout)**:
   - Trước khi xoá token cục bộ, gọi `DELETE /api/v1/notifications/devices/{token}` để Backend hủy gửi notification cho thiết bị này.

### 2.3. Chi tiết API Quản lý Device Token

#### a. Đăng ký / Cập nhật Device Token
- **Endpoint**: `POST /api/v1/notifications/devices`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:
```json
{
  "token": "eX_92...fcm_token_string...0A",
  "deviceType": "ANDROID", // "ANDROID" | "IOS" | "WEB"
  "deviceModel": "Google Pixel 7 Pro"
}
```
- **Response**: `200 OK` (hoặc `201 Created`).

#### b. Huỷ đăng ký Device Token khi Logout
- **Endpoint**: `DELETE /api/v1/notifications/devices/{token}`
- **Headers**: `Authorization: Bearer <token>`
- **Response**: `204 No Content` hoặc `200 OK`.

### 2.4. Cấu trúc Payload Thông báo Đẩy từ Backend
Khi Backend bắn push, payload sẽ chứa cả notification hiển thị và data phục vụ Deep Linking:
```json
{
  "notification": {
    "title": "Kiểm định hoàn tất: ĐẠT",
    "body": "Sản phẩm iPhone 13 Pro Max đã vượt qua quy trình kiểm định."
  },
  "data": {
    "notificationType": "INSPECTION",
    "referenceId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "referenceType": "ORDER",
    "targetUrl": "/orders/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "click_action": "FLUTTER_NOTIFICATION_CLICK"
  }
}
```

### 2.5. Dart Service Mẫu cho Flutter (`fcm_service.dart`)
```dart
import 'dart:io';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:dio/dio.dart';

class FcmService {
  final FirebaseMessaging _fcm = FirebaseMessaging.instance;
  final Dio _dio;

  FcmService(this._dio);

  Future<void> initAndRegisterToken() async {
    // 1. Yêu cầu quyền nhận thông báo
    NotificationSettings settings = await _fcm.requestPermission(
      alert: true,
      badge: true,
      sound: true,
    );

    if (settings.authorizationStatus == AuthorizationStatus.authorized) {
      // 2. Lấy FCM Token
      String? token = await _fcm.getToken();
      if (token != null) {
        await _sendTokenToServer(token);
      }

      // 3. Lắng nghe nếu FCM đổi token mới
      _fcm.onTokenRefresh.listen((newToken) {
        _sendTokenToServer(newToken);
      });
    }

    // 4. Lắng nghe khi app đang foreground
    FirebaseMessaging.onMessage.listen((RemoteMessage message) {
      _showLocalNotification(message);
    });

    // 5. Xử lý khi người dùng nhấn vào thông báo để mở app
    FirebaseMessaging.onMessageOpenedApp.listen((RemoteMessage message) {
      _handleNotificationNavigation(message.data);
    });
  }

  Future<void> _sendTokenToServer(String token) async {
    try {
      await _dio.post(
        '/api/v1/notifications/devices',
        data: {
          'token': token,
          'deviceType': Platform.isAndroid ? 'ANDROID' : 'IOS',
          'deviceModel': '${Platform.operatingSystem} ${Platform.version}',
        },
      );
    } catch (e) {
      // Log lỗi gửi token
    }
  }

  Future<void> unregisterOnLogout() async {
    try {
      String? token = await _fcm.getToken();
      if (token != null) {
        await _dio.delete('/api/v1/notifications/devices/$token');
      }
    } catch (_) {}
  }

  void _handleNotificationNavigation(Map<String, dynamic> data) {
    final type = data['notificationType'];
    final referenceId = data['referenceId'];

    if (type == 'INSPECTION' || type == 'ORDER') {
      // Navigator.pushNamed(context, '/order-detail', arguments: referenceId);
    } else if (type == 'WALLET') {
      // Navigator.pushNamed(context, '/wallet');
    }
  }

  void _showLocalNotification(RemoteMessage message) {
    // Dùng flutter_local_notifications để hiện banner khi app đang mở
  }
}
```

---

## 3. TÍCH HỢP DÀNH CHO APP BUYER (NGƯỜI MUA)

### 3.1. Chi tiết Sản phẩm Công khai (Public Product Detail)
- **Endpoint**: `GET /api/v1/posts/{postId}`
- **Quyền hạn**: Không cần đăng nhập (`permitAll()`).
- **Response Model**:
```dart
class MarketplaceListingModel {
  final String id;
  final String sellerId;
  final String sellerName;
  final String? sellerAvatarUrl;
  final double? sellerRating;
  final String title;
  final String description;
  final double price;
  final String model;
  final String brand;
  final String categoryName;
  final List<ListingImageModel> images;
  final bool requiresInspection;
  final String status;

  MarketplaceListingModel({
    required this.id,
    required this.sellerId,
    required this.sellerName,
    this.sellerAvatarUrl,
    this.sellerRating,
    required this.title,
    required this.description,
    required this.price,
    required this.model,
    required this.brand,
    required this.categoryName,
    required this.images,
    required this.requiresInspection,
    required this.status,
  });

  factory MarketplaceListingModel.fromJson(Map<String, dynamic> json) {
    return MarketplaceListingModel(
      id: json['id'],
      sellerId: json['sellerId'],
      sellerName: json['sellerName'] ?? '',
      sellerAvatarUrl: json['sellerAvatarUrl'],
      sellerRating: (json['sellerRating'] as num?)?.toDouble(),
      title: json['title'] ?? '',
      description: json['description'] ?? '',
      price: (json['price'] as num).toDouble(),
      model: json['model'] ?? '',
      brand: json['brand'] ?? '',
      categoryName: json['categoryName'] ?? '',
      images: (json['images'] as List? ?? [])
          .map((i) => ListingImageModel.fromJson(i))
          .toList(),
      requiresInspection: json['requiresInspection'] ?? false,
      status: json['status'] ?? '',
    );
  }
}
```

### 3.2. Theo dõi Tiến trình Kiểm định
- **Endpoint**: `GET /api/v1/inspections/{orderId}`
- **Luồng trạng thái (`inspectionStatus`)**:
  1. `WAITING_FOR_DELIVERY`: Đang chờ giao máy đến trung tâm kiểm định.
  2. `DELIVERED_TO_CENTER`: Máy đã tới trung tâm kiểm định.
  3. `ASSIGNED_TO_INSPECTOR`: Đã phân công kỹ thuật viên.
  4. `INSPECTING`: **Kỹ thuật viên đang kiểm định máy (mổ máy & kiểm tra)**.
  5. `PASSED` / `FAILED`: Đã hoàn tất kiểm định, có biên bản chi tiết.

---

## 4. TÍCH HỢP DÀNH CHO APP INSPECTOR (KỸ THUẬT VIÊN KIỂM ĐỊNH)

### 4.1. Nút "Bắt đầu Kiểm định" (Start Inspection)
- Khi KTV nhận máy tại bàn kiểm định, bấm nút **"Bắt đầu kiểm tra"**.
- **Endpoint**: `POST /api/v1/inspector/orders/{orderId}/start`
- **Headers**: `Authorization: Bearer <inspector_token>`
- **Response**: Trả về `InspectionOrderResponse` với `inspectionStatus = "INSPECTING"`. Cả Buyer và Seller đều nhận được thông báo đẩy.

### 4.2. Chiến lược Tải Video Bằng chứng Kiểm định (Hybrid Strategy)
Quy trình kiểm tra bo mạch bên trong có thể kéo dài **1 đến 2 tiếng**, KTV có **2 sự lựa chọn** trên App:

#### Cách 1: Video Tóm tắt / Time-lapse trực tiếp ($\le 100\text{MB}$)
KTV quay video tua nhanh quá trình mổ máy (2–3 phút) hoặc video ngắn kiểm tra góc cạnh:
- **API Upload**: `POST /api/v1/media/upload-video`
- **Content-Type**: `multipart/form-data`
- **Dart Code**:
```dart
Future<Map<String, String>> uploadInspectionVideo(File videoFile) async {
  String fileName = videoFile.path.split('/').pop()!;
  FormData formData = FormData.fromMap({
    'file': await MultipartFile.fromFile(videoFile.path, filename: fileName),
    'folder': 'secondlife/inspections',
  });

  Response response = await _dio.post(
    '/api/v1/media/upload-video',
    data: formData,
    options: Options(contentType: 'multipart/form-data'),
  );

  return {
    'url': response.data['url'],
    'publicId': response.data['publicId'],
  };
}
```

#### Cách 2: Video Raw 1–2 Tiếng qua YouTube Unlisted / Google Drive
Nếu kiểm tra chuyên sâu quay toàn bộ 1-2h:
1. KTV upload video lên **YouTube** chọn chế độ **Không công khai (Unlisted)** hoặc tải lên Google Drive.
2. Trên màn hình kết quả của App Inspector, KTV chỉ cần dán link URL vào ô `videoProofUrl`.

### 4.3. Nộp Kết quả Kiểm định
- **Endpoint**: `POST /api/v1/inspector/orders/{orderId}/result`
- **Request Body**:
```json
{
  "overallResult": "PASSED", // "PASSED" hoặc "FAILED"
  "technicianNotes": "Máy nguyên tem, bo mạch sáng đẹp, không có dấu hiệu ngấm nước",
  "videoProofUrl": "https://youtu.be/example_code",
  "videoProofPublicId": null, // Nếu upload file trực tiếp thì truyền publicId ở đây
  "componentResults": [
    {
      "componentName": "SCREEN",
      "passed": true,
      "note": "Màn hình cảm ứng tốt, không ám ố"
    },
    {
      "componentName": "BATTERY",
      "passed": true,
      "note": "Độ chai pin 88%, chu kỳ sạc 420 lần"
    },
    {
      "componentName": "MAINBOARD",
      "passed": true,
      "note": "Bo mạch nguyên bản chưa sửa chữa"
    }
  ]
}
```

---

## 5. TÍCH HỢP DÀNH CHO APP SELLER (NGƯỜI BÁN)

### 5.1. Xem Bản Nháp Bài Đăng (Draft Listing)
- **Endpoint**: `GET /api/v1/posts/{postId}/draft`
- **Headers**: `Authorization: Bearer <seller_token>`

### 5.2. Màn hình Ví & Yêu cầu Rút Tiền

#### a. Lấy Số Dư Ví Hiện Tại
- **Endpoint**: `GET /api/v1/wallets/me`
- **Response**:
```json
{
  "id": "e44d4fa7-0801-4be3-b9ad-80eb9a2ec22d",
  "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "balance": 2500000.0,
  "updatedAt": "2026-10-14T09:30:00Z"
}
```

#### b. Gửi Yêu Cầu Rút Tiền
- **Endpoint**: `POST /api/v1/wallets/withdrawals`
- **Validation**:
  - `amount`: Tối thiểu 50,000 VND và không vượt quá số dư khả dụng.
  - `bankCode`: Viết tắt ngân hàng (VCB, MB, TCB, ACB, VPB, TPB, BIDV, CTG...).
  - `accountNumber`: Số tài khoản ngân hàng.
  - `accountHolderName`: Tên chủ tài khoản.
- **Request Body**:
```json
{
  "amount": 1000000,
  "bankCode": "VCB",
  "accountNumber": "1019283746",
  "accountHolderName": "TRAN VAN B"
}
```

#### c. Lịch Sử Yêu Cầu Rút Tiền
- **Endpoint**: `GET /api/v1/wallets/withdrawals?page=0&size=10`
- **Dart Model**:
```dart
class WalletWithdrawalItem {
  final String id;
  final double amount;
  final String bankCode;
  final String accountNumber;
  final String accountHolderName;
  final String status; // PENDING_REVIEW, APPROVED, REJECTED
  final String? adminNote;
  final DateTime createdAt;

  WalletWithdrawalItem({
    required this.id,
    required this.amount,
    required this.bankCode,
    required this.accountNumber,
    required this.accountHolderName,
    required this.status,
    this.adminNote,
    required this.createdAt,
  });

  factory WalletWithdrawalItem.fromJson(Map<String, dynamic> json) {
    return WalletWithdrawalItem(
      id: json['id'],
      amount: (json['amount'] as num).toDouble(),
      bankCode: json['bankCode'],
      accountNumber: json['accountNumber'],
      accountHolderName: json['accountHolderName'],
      status: json['status'],
      adminNote: json['adminNote'],
      createdAt: DateTime.parse(json['createdAt']),
    );
  }
}
```

---

## 6. BẢNG MÃ LỖI (ERROR CODES) & BEST PRACTICES CHO FLUTTER

### 6.1. Xử lý Lỗi Phổ biến từ Backend
| HTTP Status | Error Case | Thông báo gợi ý cho người dùng |
| :---: | :--- | :--- |
| `400 Bad Request` | Số dư không đủ rút tiền | *"Số dư khả dụng trong ví không đủ để thực hiện yêu cầu này."* |
| `400 Bad Request` | Số tiền rút dưới 50.000 đ | *"Số tiền rút tối thiểu là 50.000 VND."* |
| `400 Bad Request` | Đơn kiểm định không ở trạng thái phân công | *"Đơn kiểm định đã được chuyển trạng thái hoặc chưa được phân công."* |
| `400 Bad Request` | Video không đúng định dạng / quá 100MB | *"Video vượt quá dung lượng cho phép (tối đa 100MB). Vui lòng nén video hoặc dán link YouTube."* |
| `401 Unauthorized` | Token hết hạn | Điều hướng tự động về màn hình Login. |
| `403 Forbidden` | KTV không đúng thẩm quyền | *"Bạn không có quyền thực hiện kiểm định trên đơn này."* |

### 6.2. Best Practices khi Triển khai Flutter
1. **Network Interceptor (Dio)**: Bắt `401` để tự động refresh token hoặc logout xoá device token.
2. **Video Compression**: Với video quay từ camera điện thoại, dùng package `video_compress` để giảm bitrate trước khi gọi `uploadVideo`, đảm bảo file $< 50\text{MB}$ để upload nhanh chóng qua 4G/Wifi.
3. **Number Formatting**: Dùng `intl` (`NumberFormat.currency(locale: 'vi_VN', symbol: 'đ')`) khi hiển thị số dư ví và giá sản phẩm.

---

## 7. TÍCH HỢP QUÉT MÃ QR TEM NIÊM PHONG (TAMPER-EVIDENT SEAL)

> **Lưu ý nghiệp vụ**: Trung tâm kiểm định SecondLife dán **Tem niêm phong vỡ (Tamper-evident Seal)** có in mã QR định danh duy nhất (ví dụ: `SL-SEAL-20261014-9988`). **Hệ thống sử dụng camera quét mã QR, hoàn toàn không sử dụng chip NFC vật lý**.

### 7.1. Cài đặt Package quét mã QR
Trong `pubspec.yaml`:
```yaml
dependencies:
  mobile_scanner: ^5.2.3
```

### 7.2. Tra Cứu Thông Tin Tem Niêm Phong Cho Buyer / Khách Vãng Lai
Khi Buyer hoặc khách dùng camera quét tem dán trên sản phẩm:
- **Endpoint**: `GET /api/v1/inspection/seal/{tamperSealId}`
- **Quyền hạn**: Công khai (`permitAll()`), không bắt buộc đăng nhập.
- **Dart Service Mẫu**:
```dart
Future<InspectionSealVerificationResponse> verifySeal(String qrContent) async {
  // Trích xuất mã tem từ chuỗi quét được (hỗ trợ cả URL đầy đủ hoặc mã trực tiếp)
  String sealId = qrContent.contains('/seal/') 
      ? qrContent.split('/seal/').last.split('?').first 
      : qrContent.trim();

  final response = await _dio.get('/api/v1/inspection/seal/$sealId');
  return InspectionSealVerificationResponse.fromJson(response.data['data']);
}
```

### 7.3. Ghi Nhận Lịch Sử Quét Mã (Dành cho KTV & Shipper)
- **Endpoint**: `POST /api/v1/inspection/seal/scan`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:
```json
{
  "tamperSealId": "SL-SEAL-20261014-9988",
  "scanStage": "CARRIER_HANDOVER", // "INITIAL_SEAL" | "CARRIER_HANDOVER" | "DELIVERED_BUYER"
  "sealCondition": "INTACT",       // "INTACT" (nguyên vẹn) | "TAMPERED" (rách/mở)
  "locationText": "Kho trung chuyển Cầu Giấy, Hà Nội",
  "note": "Tem niêm phong nguyên vẹn, bao bì sạch sẽ"
}
```

---

## 8. TÍCH HỢP AI ĐỊNH GIÁ TRỰC TIẾP TRÊN SELLER APP (BƯỚC 3 ĐĂNG BÀI)

### 8.1. Endpoint Backend Spring Boot
- **Endpoint**: `POST /api/v1/ai/estimate-price` (hoặc alias `POST /api/ai/estimate-price`)
- **Headers**: `Authorization: Bearer <seller_token>`
- **Request Body**:
```json
{
  "title": "iPhone 13 Pro Max 128GB Xanh Sierra",
  "category": "Điện thoại",
  "brand": "Apple",
  "model": "iPhone 13 Pro Max",
  "itemCondition": "LIKE_NEW",
  "description": "Máy nữ dùng kỹ pin 89%, phụ kiện sạc cáp zin",
  "imageUrls": [
    "https://res.cloudinary.com/secondlife/image/upload/v123/img1.jpg"
  ]
}
```

### 8.2. Dart Model & Service Mẫu
```dart
class AiPriceEstimateResult {
  final double fairPriceMin;
  final double fairPriceMax;
  final double suggestedPrice;
  final String expectedSellTime;
  final String modelVersion;

  AiPriceEstimateResult({
    required this.fairPriceMin,
    required this.fairPriceMax,
    required this.suggestedPrice,
    required this.expectedSellTime,
    required this.modelVersion,
  });

  factory AiPriceEstimateResult.fromJson(Map<String, dynamic> json) {
    return AiPriceEstimateResult(
      fairPriceMin: (json['fairPriceMin'] as num).toDouble(),
      fairPriceMax: (json['fairPriceMax'] as num).toDouble(),
      suggestedPrice: (json['suggestedPrice'] as num).toDouble(),
      expectedSellTime: json['expectedSellTime'] ?? '',
      modelVersion: json['modelVersion'] ?? '',
    );
  }
}

Future<AiPriceEstimateResult> requestAiPriceEstimate({
  required String title,
  String? category,
  String? brand,
  String? model,
  String? itemCondition,
  String? description,
  List<String>? imageUrls,
}) async {
  final response = await _dio.post('/api/v1/ai/estimate-price', data: {
    'title': title,
    'category': category,
    'brand': brand,
    'model': model,
    'itemCondition': itemCondition,
    'description': description,
    'imageUrls': imageUrls,
  });
  return AiPriceEstimateResult.fromJson(response.data['data']);
}
```

