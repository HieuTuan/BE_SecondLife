package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.BuyerDeliveryAddressRequest;
import com.secondlife.secondlife.dto.response.BuyerDeliveryAddressResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.BuyerAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shipping/delivery-addresses")
@Tag(name = "Buyer Delivery Addresses", description = "Quản lý danh bạ nhiều địa chỉ nhận hàng của người mua (Shopee-style Address Book)")
public class BuyerAddressController {

    private final BuyerAddressService addressService;
    private final CurrentUserProvider current;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy danh sách địa chỉ nhận hàng đã lưu của người dùng",
            description = "Trả về danh bạ các địa chỉ nhận hàng đã lưu của tài khoản đang đăng nhập. Nếu chưa có, tự động lấy địa chỉ từ profile.")
    public ApiResponse<List<BuyerDeliveryAddressResponse>> getAddresses(
            @AuthenticationPrincipal CustomUserDetails user) {
        UUID userId = current.resolveUserId(user);
        return ApiResponse.success(addressService.getAddresses(userId));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Thêm địa chỉ nhận hàng mới vào danh bạ",
            description = "Thêm địa chỉ nhận hàng mới. Nếu là địa chỉ đầu tiên hoặc được đánh dấu mặc định, các địa chỉ khác sẽ tự động bỏ mặc định.")
    public ApiResponse<BuyerDeliveryAddressResponse> addAddress(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody BuyerDeliveryAddressRequest body) {
        UUID userId = current.resolveUserId(user);
        return ApiResponse.success(addressService.addAddress(userId, body));
    }

    @PutMapping("/{addressId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cập nhật thông tin địa chỉ nhận hàng",
            description = "Sửa tên người nhận, số điện thoại, địa chỉ hoặc vị trí ghim bản đồ của địa chỉ có id tương ứng.")
    public ApiResponse<BuyerDeliveryAddressResponse> updateAddress(
            @AuthenticationPrincipal CustomUserDetails user,
            @Parameter(description = "Mã địa chỉ cần cập nhật", example = "addr-1732608400000")
            @PathVariable("addressId") String addressId,
            @Valid @RequestBody BuyerDeliveryAddressRequest body) {
        UUID userId = current.resolveUserId(user);
        return ApiResponse.success(addressService.updateAddress(userId, addressId, body));
    }

    @DeleteMapping("/{addressId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xóa một địa chỉ khỏi danh bạ",
            description = "Xóa địa chỉ nhận hàng khỏi danh bạ. Nếu xóa địa chỉ mặc định, địa chỉ đầu tiên còn lại sẽ tự động trở thành mặc định.")
    public ApiResponse<Void> deleteAddress(
            @AuthenticationPrincipal CustomUserDetails user,
            @Parameter(description = "Mã địa chỉ cần xóa", example = "addr-1732608400000")
            @PathVariable("addressId") String addressId) {
        UUID userId = current.resolveUserId(user);
        addressService.deleteAddress(userId, addressId);
        return ApiResponse.success("Đã xóa địa chỉ thành công");
    }

    @PutMapping("/{addressId}/default")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đặt làm địa chỉ nhận hàng mặc định",
            description = "Đặt địa chỉ có id tương ứng làm mặc định và đồng bộ lên hồ sơ người dùng.")
    public ApiResponse<BuyerDeliveryAddressResponse> setDefaultAddress(
            @AuthenticationPrincipal CustomUserDetails user,
            @Parameter(description = "Mã địa chỉ cần đặt làm mặc định", example = "addr-1732608400000")
            @PathVariable("addressId") String addressId) {
        UUID userId = current.resolveUserId(user);
        return ApiResponse.success(addressService.setDefaultAddress(userId, addressId));
    }

    @PutMapping("/sync")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đồng bộ toàn bộ danh bạ địa chỉ",
            description = "Cập nhật hoặc đồng bộ toàn bộ danh sách địa chỉ nhận hàng từ frontend lên backend.")
    public ApiResponse<List<BuyerDeliveryAddressResponse>> syncAddresses(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody List<BuyerDeliveryAddressRequest> body) {
        UUID userId = current.resolveUserId(user);
        return ApiResponse.success(addressService.syncAddresses(userId, body));
    }
}
