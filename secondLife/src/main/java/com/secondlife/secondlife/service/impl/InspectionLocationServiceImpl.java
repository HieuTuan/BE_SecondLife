package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.common.GeoDistance;
import com.secondlife.secondlife.common.VietnameseAdministrativeAreas;
import com.secondlife.secondlife.config.ShippingCatalogProperties.City;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.response.InspectionCenterOptionResponse;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.UserProfile;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.service.InspectionCenterProfileService;
import com.secondlife.secondlife.service.InspectionLocationService;
import com.secondlife.secondlife.service.ReverseGeocodingService;
import com.secondlife.secondlife.service.ShippingQuoteService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class InspectionLocationServiceImpl implements InspectionLocationService {
    private static final String PICKUP_REQUIRED =
            "Người bán cần cập nhật địa chỉ lấy hàng chi tiết và GPS trước khi báo phí kiểm định";
    private static final Pattern CITY_IN_ADDRESS = Pattern.compile("\\b(ha noi|hanoi|ho chi minh|hcmc?|sai gon|da nang)\\b");

    private final ShippingQuoteService shipping;
    private final ReverseGeocodingService geocoding;
    private final InspectionCenterProfileService centers;
    private final ShippingCatalogPolicy shippingPolicy;
    private final Validator validator;

    @Override
    @Transactional(readOnly = true)
    public ShippingAddress sellerPickup(Post post) {
        var seller = post.getUser();
        ShippingAddress pickup = shipping.pickup(seller.getId());
        if (pickup == null || pickup.address() == null || pickup.address().isBlank()) {
            throw new ConflictException(PICKUP_REQUIRED);
        }

        if (pickup.latitude() == null || pickup.longitude() == null) {
            pickup = geocodePickup(pickup);
        }
        if (!validator.validate(pickup).isEmpty()) {
            throw new ConflictException(PICKUP_REQUIRED);
        }
        return pickup;
    }

    @Override
    @Transactional(readOnly = true)
    public HubSelection selectHub(ShippingAddress sellerPickup, UUID requestedCenterId) {
        City sellerCity = cityAt(sellerPickup.latitude(), sellerPickup.longitude());
        if (sellerCity == null || shippingPolicy.allowedCity(sellerCity.name()) == null) {
            throw new ConflictException("Kho người bán nằm ngoài khu vực kiểm định đang hỗ trợ");
        }

        Candidate best = centers.getAvailableCenters().stream()
                .filter(InspectionCenterOptionResponse::shippingAddressConfigured)
                .filter(center -> requestedCenterId == null || requestedCenterId.equals(center.id()))
                .filter(center -> sellerCity == cityAt(center.latitude(), center.longitude()))
                .map(center -> new Candidate(center, distance(sellerPickup, center)))
                .min(Comparator.comparingDouble(Candidate::distanceKm).thenComparing(candidate -> candidate.center().id()))
                .orElseThrow(() -> new ConflictException(requestedCenterId == null
                        ? "Chưa có trung tâm kiểm định đang hoạt động và có địa chỉ hợp lệ trong khu vực kho người bán"
                        : "Trung tâm đã chọn không khả dụng trong khu vực kho người bán; vui lòng chọn tự động để lấy báo giá mới"));
        return new HubSelection(best.center().id(), best.center().name(),
                BigDecimal.valueOf(best.distanceKm()));
    }

    private ShippingAddress geocodePickup(ShippingAddress pickup) {
        City expectedCity = cityFromAddress(pickup.address());
        if (expectedCity != null && shippingPolicy.allowedCity(expectedCity.name()) == null) {
            throw new ConflictException("Kho người bán nằm ngoài khu vực kiểm định đang hỗ trợ");
        }
        try {
            var found = geocoding.geocodeStreetAddress(pickup.address());
            if (expectedCity != null && expectedCity != found.city()) {
                throw new ConflictException(PICKUP_REQUIRED);
            }
            return new ShippingAddress(pickup.name(), pickup.phone(), pickup.address(), found.latitude(), found.longitude());
        } catch (BadRequestException unresolvedAddress) {
            throw new ConflictException(PICKUP_REQUIRED);
        }
    }

    private BigDecimal decimalCoordinate(Double value) {
        return value != null && Double.isFinite(value) ? BigDecimal.valueOf(value) : null;
    }

    private City cityFromAddress(String address) {
        String normalized = Normalizer.normalize(address.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        var names = CITY_IN_ADDRESS.matcher(normalized);
        City city = null;
        while (names.find()) {
            city = switch (names.group()) {
                case "ha noi", "hanoi" -> City.HANOI;
                case "da nang" -> City.DANANG;
                default -> City.HO_CHI_MINH;
            };
        }
        return city;
    }

    private City cityAt(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        var division = VietnameseAdministrativeAreas.locate(latitude.doubleValue(), longitude.doubleValue());
        return division == null ? null : division.city();
    }

    private double distance(ShippingAddress pickup, InspectionCenterOptionResponse center) {
        return GeoDistance.haversineKm(pickup.latitude().doubleValue(), pickup.longitude().doubleValue(),
                center.latitude().doubleValue(), center.longitude().doubleValue());
    }

    private record Candidate(InspectionCenterOptionResponse center, double distanceKm) {
    }
}
