package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.response.WithdrawalMobileUploadSessionResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.WithdrawalMobileUploadSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;

@RestController
@RequestMapping("/api/v1/wallets")
@Tag(name = "Withdrawal Mobile Evidence Upload", description = "Tạo mã QR và tải ảnh minh chứng chuyển khoản/sao kê từ điện thoại lên máy tính")
public class WithdrawalMobileUploadController {

  private final WithdrawalMobileUploadSessionService sessionService;
  private final CurrentUserProvider currentUserProvider;

  @Value("${server.port:8080}")
  private int serverPort;

  public WithdrawalMobileUploadController(
      WithdrawalMobileUploadSessionService sessionService,
      CurrentUserProvider currentUserProvider) {
    this.sessionService = sessionService;
    this.currentUserProvider = currentUserProvider;
  }

  @PostMapping("/admin/withdrawals/{id}/mobile-upload-session")
  @PreAuthorize("(hasRole('STAFF') and hasAuthority('STAFF_DISPUTE_REVIEW')) or (hasRole('ADMIN') and hasAuthority('ADMIN_RBAC_MANAGE'))")
  @Operation(summary = "Staff/Admin tạo phiên QR tải ảnh biên lai từ điện thoại (hết hạn sau 10 phút)")
  public ResponseEntity<WithdrawalMobileUploadSessionResponse> createSession(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable UUID id) {
    UUID staffId = currentUserProvider.resolveUserId(userDetails);
    return ResponseEntity.ok(sessionService.createSession(staffId, id));
  }

  @GetMapping("/admin/withdrawals/{id}/mobile-upload-session/{sessionId}")
  @PreAuthorize("(hasRole('STAFF') and hasAuthority('STAFF_DISPUTE_REVIEW')) or (hasRole('ADMIN') and hasAuthority('ADMIN_RBAC_MANAGE'))")
  @Operation(summary = "Staff/Admin kiểm tra trạng thái phiên QR tải ảnh từ điện thoại")
  public ResponseEntity<WithdrawalMobileUploadSessionResponse> getSessionStatus(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable UUID id,
      @PathVariable String sessionId) {
    UUID staffId = currentUserProvider.resolveUserId(userDetails);
    return ResponseEntity.ok(sessionService.getSessionStatusForStaff(staffId, id, sessionId));
  }

  @GetMapping("/admin/withdrawals/mobile-upload-network-info")
  @PreAuthorize("(hasRole('STAFF') and hasAuthority('STAFF_DISPUTE_REVIEW')) or (hasRole('ADMIN') and hasAuthority('ADMIN_RBAC_MANAGE'))")
  @Operation(summary = "Staff/Admin lấy danh sách IP mạng LAN của máy chủ để tạo mã QR nội bộ")
  public ResponseEntity<Map<String, Object>> getNetworkInfo() {
    return ResponseEntity.ok(buildNetworkInfoPayload());
  }

  @PostMapping("/withdrawals/{id}/mobile-upload-session")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Người dùng tạo phiên QR tải ảnh minh chứng sao kê từ điện thoại (hết hạn sau 10 phút)")
  public ResponseEntity<WithdrawalMobileUploadSessionResponse> createUserSession(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable UUID id,
      @RequestParam(name = "purpose", defaultValue = "USER_DISPUTE") String purpose,
      @RequestParam(name = "maxFiles", defaultValue = "3") Integer maxFiles) {
    UUID userId = currentUserProvider.resolveUserId(userDetails);
    return ResponseEntity.ok(sessionService.createUserSession(userId, id, purpose, maxFiles));
  }

  @GetMapping("/withdrawals/{id}/mobile-upload-session/{sessionId}")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Người dùng kiểm tra trạng thái phiên QR tải ảnh minh chứng từ điện thoại")
  public ResponseEntity<WithdrawalMobileUploadSessionResponse> getUserSessionStatus(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable UUID id,
      @PathVariable String sessionId) {
    UUID userId = currentUserProvider.resolveUserId(userDetails);
    return ResponseEntity.ok(sessionService.getSessionStatusForUser(userId, id, sessionId));
  }

  @GetMapping("/withdrawals/mobile-upload-network-info")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Người dùng lấy danh sách IP mạng LAN của máy chủ để tạo mã QR tải ảnh nội bộ")
  public ResponseEntity<Map<String, Object>> getUserNetworkInfo() {
    return ResponseEntity.ok(buildNetworkInfoPayload());
  }

  @GetMapping("/withdrawals/mobile-upload/{token}")
  @Operation(summary = "Lấy thông tin phiên tải ảnh minh chứng bằng token từ mã QR")
  public ResponseEntity<WithdrawalMobileUploadSessionResponse> getPublicSessionInfo(
      @PathVariable String token) {
    return ResponseEntity.ok(sessionService.getPublicSessionInfo(token));
  }

  @PostMapping(value = "/withdrawals/mobile-upload/{token}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Tải ảnh minh chứng từ điện thoại bằng token từ mã QR")
  public ResponseEntity<WithdrawalMobileUploadSessionResponse> uploadEvidenceFromMobile(
      @PathVariable String token,
      @RequestPart("file") MultipartFile file) {
    return ResponseEntity.ok(sessionService.uploadEvidenceWithToken(token, file));
  }

  @GetMapping(value = "/withdrawals/mobile-upload/{token}/page", produces = MediaType.TEXT_HTML_VALUE)
  @Operation(summary = "Trang web di động độc lập để tải ảnh minh chứng khi kết nối trực tiếp tới Backend (Ngrok/LAN)")
  public ResponseEntity<String> renderStandaloneMobileUploadPage(@PathVariable String token) {
    String safeToken = escapeHtmlAttribute(token);
    long configuredMaxBytes = sessionService.getMaxEvidenceFileBytes();
    String html = """
        <!doctype html>
        <html lang="vi">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1" />
          <title>SecondLife - Tải ảnh minh chứng từ điện thoại</title>
          <style>
            * { box-sizing: border-box; }
            body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #faf8f5; color: #24263e; min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 16px; }
            .card { width: 100%; max-width: 440px; background: #fff; border: 1px solid #e2e8f0; border-radius: 20px; box-shadow: 0 12px 30px rgba(36, 38, 62, 0.08); overflow: hidden; }
            .header { background: #24263e; color: #fff; padding: 18px 20px; }
            .header h1 { margin: 0; font-size: 16px; font-weight: 800; }
            .header p { margin: 4px 0 0; font-size: 12px; color: rgba(255,255,255,0.78); }
            .body { padding: 18px 20px 22px; }
            .info { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 14px; padding: 12px 14px; margin-bottom: 14px; font-size: 13px; }
            .row { display: flex; justify-content: space-between; gap: 10px; padding: 4px 0; }
            .label { color: #64748b; font-weight: 600; }
            .val { font-weight: 700; text-align: right; word-break: break-word; }
            .amount { color: #c34c36; font-size: 16px; font-weight: 900; }
            .picker { display: block; width: 100%; border: 2px dashed #cbd5e1; border-radius: 14px; padding: 18px 14px; text-align: center; cursor: pointer; background: #fff; margin-bottom: 12px; }
            .picker strong { display: block; font-size: 14px; color: #24263e; }
            .picker span { display: block; font-size: 11.5px; color: #64748b; margin-top: 4px; }
            .preview-grid { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
            .preview-item { width: 88px; height: 88px; object-fit: cover; border-radius: 10px; border: 1px solid #e2e8f0; background: #f8fafc; }
            .preview-single { width: 100%; max-height: 260px; object-fit: contain; border-radius: 12px; border: 1px solid #e2e8f0; margin-bottom: 12px; display: none; background: #f8fafc; }
            .btn { width: 100%; border: 0; border-radius: 12px; padding: 13px 16px; font-size: 14px; font-weight: 800; cursor: pointer; background: #c34c36; color: #fff; }
            .btn:disabled { opacity: 0.5; cursor: not-allowed; }
            .msg { border-radius: 12px; padding: 12px 14px; font-size: 13px; font-weight: 600; margin-bottom: 12px; display: none; }
            .msg.err { background: #fef2f2; color: #b91c1c; border: 1px solid #fecaca; }
            .msg.ok { background: #ecfdf5; color: #065f46; border: 1px solid #a7f3d0; }
            .quota { font-size: 12px; font-weight: 700; color: #475569; margin-bottom: 10px; display: none; }
            .loading { text-align: center; padding: 28px 12px; color: #64748b; font-size: 13px; font-weight: 600; }
          </style>
        </head>
        <body>
          <div class="card" data-token="%s" data-max-bytes="%d">
            <div class="header">
              <h1 id="pageTitle">Tải ảnh minh chứng từ điện thoại</h1>
              <p id="pageSubtitle">Ảnh tải lên sẽ tự động hiển thị ngay trên máy tính</p>
            </div>
            <div class="body">
              <div id="loadingBox" class="loading">Đang kiểm tra phiên tải ảnh...</div>
              <div id="msgBox" class="msg"></div>
              <div id="contentBox" style="display:none;">
                <div class="info">
                  <div class="row"><span class="label">Mã lệnh rút</span><span class="val" id="reqCode">—</span></div>
                  <div class="row"><span class="label">Số tiền</span><span class="val amount" id="reqAmount">—</span></div>
                  <div class="row"><span class="label">Ngân hàng</span><span class="val" id="reqBank">—</span></div>
                  <div class="row"><span class="label">Chủ tài khoản</span><span class="val" id="reqHolder">—</span></div>
                </div>
                <div id="quotaBox" class="quota"></div>
                <label class="picker" id="pickerLabel">
                  <strong id="pickerTitle">Chọn ảnh minh chứng từ điện thoại</strong>
                  <span id="pickerHint">Hỗ trợ JPEG, PNG, WEBP</span>
                  <input id="fileInput" type="file" accept="image/jpeg,image/png,image/webp" style="display:none;" />
                </label>
                <img id="previewSingle" class="preview-single" alt="Xem trước ảnh" />
                <div id="previewGrid" class="preview-grid" style="display:none;"></div>
                <button id="uploadBtn" class="btn" type="button" disabled>Gửi ảnh lên máy tính</button>
              </div>
            </div>
          </div>
          <script>
            (function () {
              var card = document.querySelector('.card');
              var token = card.getAttribute('data-token');
              var configuredMaxBytes = Number(card.getAttribute('data-max-bytes') || 0) || (10 * 1024 * 1024);
              var loadingBox = document.getElementById('loadingBox');
              var contentBox = document.getElementById('contentBox');
              var msgBox = document.getElementById('msgBox');
              var quotaBox = document.getElementById('quotaBox');
              var pickerLabel = document.getElementById('pickerLabel');
              var fileInput = document.getElementById('fileInput');
              var previewSingle = document.getElementById('previewSingle');
              var previewGrid = document.getElementById('previewGrid');
              var uploadBtn = document.getElementById('uploadBtn');
              var selectedFiles = [];
              var sessionData = null;

              function effectiveMaxBytes() {
                var fromSession = sessionData && Number(sessionData.maxFileSizeBytes);
                return fromSession > 0 ? fromSession : configuredMaxBytes;
              }

              function formatSizeLabel(bytes) {
                var mb = bytes / (1024 * 1024);
                if (mb >= 1) {
                  return (Number.isInteger(mb) ? mb.toFixed(0) : mb.toFixed(1).replace(/\\.0$/, '')) + ' MB';
                }
                return Math.max(1, Math.round(bytes / 1024)) + ' KB';
              }

              function showMsg(type, text) {
                msgBox.className = 'msg ' + type;
                msgBox.textContent = text;
                msgBox.style.display = 'block';
              }

              function formatVnd(val) {
                var n = Number(val || 0);
                return n.toLocaleString('vi-VN') + ' ₫';
              }

              function remainingSlots() {
                if (!sessionData) return 1;
                var max = Number(sessionData.maxFiles || 1);
                var used = Array.isArray(sessionData.evidenceUrls) ? sessionData.evidenceUrls.length : (sessionData.status === 'UPLOADED' ? max : 0);
                return Math.max(0, max - used);
              }

              function applySessionUi(s) {
                sessionData = s;
                var purpose = s.purpose || 'STAFF_RECEIPT';
                var max = Number(s.maxFiles || 1);
                var used = Array.isArray(s.evidenceUrls) ? s.evidenceUrls.length : 0;
                var left = Math.max(0, max - used);
                var sizeText = formatSizeLabel(effectiveMaxBytes());

                if (purpose === 'USER_DISPUTE') {
                  document.getElementById('pageTitle').textContent = 'Tải ảnh minh chứng báo cáo sự cố';
                  document.getElementById('pageSubtitle').textContent = 'Chụp màn hình sao kê / lịch sử giao dịch ngân hàng để gửi lên máy tính';
                  document.getElementById('pickerTitle').textContent = 'Chọn ảnh chụp lịch sử giao dịch';
                  uploadBtn.textContent = 'Gửi ảnh minh chứng lên máy tính';
                } else if (purpose === 'USER_EXCEPTION') {
                  document.getElementById('pageTitle').textContent = 'Tải ảnh minh chứng hỗ trợ ngoại lệ';
                  document.getElementById('pageSubtitle').textContent = 'Ảnh tải lên sẽ tự động thêm vào biểu mẫu trên máy tính';
                  document.getElementById('pickerTitle').textContent = 'Chọn ảnh sao kê / bằng chứng bổ sung';
                  uploadBtn.textContent = 'Gửi ảnh minh chứng lên máy tính';
                } else {
                  document.getElementById('pageTitle').textContent = 'Tải ảnh biên lai chuyển khoản';
                  document.getElementById('pageSubtitle').textContent = 'Ảnh tải lên sẽ tự động hiển thị ngay trên máy tính xử lý';
                  document.getElementById('pickerTitle').textContent = 'Chọn ảnh biên lai từ điện thoại';
                  uploadBtn.textContent = 'Gửi ảnh biên lai lên máy tính';
                }

                if (max > 1) {
                  fileInput.multiple = true;
                  quotaBox.style.display = 'block';
                  quotaBox.textContent = 'Đã gửi ' + used + '/' + max + ' ảnh (còn có thể thêm ' + left + ' ảnh)';
                  document.getElementById('pickerHint').textContent = 'Chọn tối đa ' + left + ' ảnh JPEG, PNG, WEBP (tối đa ' + sizeText + ' / ảnh)';
                } else {
                  fileInput.multiple = false;
                  quotaBox.style.display = 'none';
                  document.getElementById('pickerHint').textContent = 'Hỗ trợ JPEG, PNG, WEBP (tối đa ' + sizeText + ' / ảnh)';
                }

                document.getElementById('reqCode').textContent = '#' + (s.requestCode || '');
                document.getElementById('reqAmount').textContent = formatVnd(s.amount);
                document.getElementById('reqBank').textContent = (s.bankName || '') + ' (' + (s.maskedAccountNumber || '') + ')';
                document.getElementById('reqHolder').textContent = s.accountHolderName || '—';
                contentBox.style.display = 'block';

                if (s.status === 'UPLOADED' || left <= 0) {
                  showMsg('ok', purpose === 'STAFF_RECEIPT'
                    ? 'Ảnh biên lai đã được tải lên thành công. Bạn có thể quay lại máy tính để xác nhận.'
                    : 'Đã gửi đủ ' + used + '/' + max + ' ảnh minh chứng lên máy tính. Bạn có thể đóng trang này và tiếp tục gửi báo cáo.');
                  uploadBtn.disabled = true;
                  uploadBtn.textContent = 'Đã tải lên thành công';
                  pickerLabel.style.display = 'none';
                }
              }

              fetch('/api/v1/wallets/withdrawals/mobile-upload/' + encodeURIComponent(token), {
                headers: { 'ngrok-skip-browser-warning': 'true' }
              })
                .then(function (r) { return r.json().then(function (d) { return { ok: r.ok, data: d }; }); })
                .then(function (res) {
                  loadingBox.style.display = 'none';
                  if (!res.ok) {
                    showMsg('err', (res.data && res.data.message) || 'Mã QR không hợp lệ hoặc đã hết hạn.');
                    return;
                  }
                  var s = (res.data && res.data.data) || res.data;
                  applySessionUi(s);
                })
                .catch(function () {
                  loadingBox.style.display = 'none';
                  showMsg('err', 'Không thể kết nối máy chủ. Vui lòng kiểm tra kết nối mạng.');
                });

              fileInput.addEventListener('change', function () {
                var files = fileInput.files ? Array.prototype.slice.call(fileInput.files) : [];
                if (!files.length) return;
                var left = remainingSlots();
                if (files.length > left) {
                  files = files.slice(0, left);
                }
                var maxBytes = effectiveMaxBytes();
                for (var i = 0; i < files.length; i++) {
                  if (files[i].size > maxBytes) {
                    showMsg('err', 'Mỗi ảnh minh chứng không được vượt quá ' + formatSizeLabel(maxBytes) + '.');
                    return;
                  }
                }
                selectedFiles = files;
                msgBox.style.display = 'none';
                previewGrid.innerHTML = '';
                if (files.length === 1) {
                  previewGrid.style.display = 'none';
                  previewSingle.src = URL.createObjectURL(files[0]);
                  previewSingle.style.display = 'block';
                } else {
                  previewSingle.style.display = 'none';
                  previewGrid.style.display = 'flex';
                  files.forEach(function (f) {
                    var img = document.createElement('img');
                    img.className = 'preview-item';
                    img.src = URL.createObjectURL(f);
                    previewGrid.appendChild(img);
                  });
                }
                uploadBtn.disabled = false;
                uploadBtn.textContent = files.length > 1
                  ? 'Gửi ' + files.length + ' ảnh minh chứng lên máy tính'
                  : (sessionData && sessionData.purpose === 'STAFF_RECEIPT' ? 'Gửi ảnh biên lai lên máy tính' : 'Gửi ảnh minh chứng lên máy tính');
              });

              uploadBtn.addEventListener('click', function () {
                if (!selectedFiles.length) return;
                uploadBtn.disabled = true;
                var queue = selectedFiles.slice();
                var uploadedNow = 0;

                function uploadNext(index) {
                  if (index >= queue.length) {
                    selectedFiles = [];
                    fileInput.value = '';
                    previewSingle.style.display = 'none';
                    previewGrid.style.display = 'none';
                    previewGrid.innerHTML = '';
                    var left = remainingSlots();
                    if (left > 0 && sessionData && sessionData.status !== 'UPLOADED') {
                      showMsg('ok', 'Đã gửi ' + uploadedNow + ' ảnh lên máy tính. Ảnh đã xuất hiện trong biểu mẫu — bạn có thể chọn thêm tối đa ' + left + ' ảnh nữa hoặc quay lại máy tính.');
                      uploadBtn.textContent = 'Chọn thêm ảnh nếu cần';
                      uploadBtn.disabled = true;
                    }
                    return;
                  }
                  uploadBtn.textContent = 'Đang tải ảnh ' + (index + 1) + '/' + queue.length + '...';
                  var fd = new FormData();
                  fd.append('file', queue[index]);
                  fetch('/api/v1/wallets/withdrawals/mobile-upload/' + encodeURIComponent(token) + '/evidence', {
                    method: 'POST',
                    headers: { 'ngrok-skip-browser-warning': 'true' },
                    body: fd
                  })
                    .then(function (r) { return r.json().then(function (d) { return { ok: r.ok, data: d }; }); })
                    .then(function (res) {
                      if (!res.ok) {
                        showMsg('err', (res.data && res.data.message) || 'Tải ảnh thất bại. Vui lòng thử lại.');
                        uploadBtn.disabled = false;
                        uploadBtn.textContent = 'Thử gửi lại ảnh';
                        return;
                      }
                      uploadedNow += 1;
                      var updated = (res.data && res.data.data) || res.data;
                      applySessionUi(updated);
                      uploadNext(index + 1);
                    })
                    .catch(function () {
                      showMsg('err', 'Lỗi kết nối khi tải ảnh lên. Vui lòng thử lại.');
                      uploadBtn.disabled = false;
                      uploadBtn.textContent = 'Thử gửi lại ảnh';
                    });
                }

                uploadNext(0);
              });
            })();
          </script>
        </body>
        </html>
        """
        .formatted(safeToken, configuredMaxBytes);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
        .body(html);
  }

  private Map<String, Object> buildNetworkInfoPayload() {
    List<String> lanIps = detectLanIpv4Addresses();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("lanIps", lanIps);
    payload.put("backendPort", serverPort);
    payload.put("recommendedFrontendPort", 3000);
    payload.put("maxEvidenceFileSizeBytes", sessionService.getMaxEvidenceFileBytes());
    return payload;
  }

  private List<String> detectLanIpv4Addresses() {
    Set<String> result = new LinkedHashSet<>();
    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces != null && interfaces.hasMoreElements()) {
        NetworkInterface nif = interfaces.nextElement();
        if (!nif.isUp() || nif.isLoopback() || nif.isVirtual())
          continue;
        Enumeration<InetAddress> addresses = nif.getInetAddresses();
        while (addresses.hasMoreElements()) {
          InetAddress addr = addresses.nextElement();
          if (addr instanceof Inet4Address ipv4 && !ipv4.isLoopbackAddress() && !ipv4.isLinkLocalAddress()) {
            String host = ipv4.getHostAddress();
            if (host != null && !host.isBlank()) {
              result.add(host);
            }
          }
        }
      }
    } catch (Exception ignored) {
    }
    return new ArrayList<>(result);
  }

  private static String escapeHtmlAttribute(String raw) {
    if (raw == null)
      return "";
    return raw.replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("'", "&#39;");
  }
}
