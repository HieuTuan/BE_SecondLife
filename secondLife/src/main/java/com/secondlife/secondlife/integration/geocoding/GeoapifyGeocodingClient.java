package com.secondlife.secondlife.integration.geocoding;

import com.secondlife.secondlife.service.impl.ShippingCatalogPolicy;
import com.secondlife.secondlife.common.VietnameseAdministrativeAreas;
import com.secondlife.secondlife.mapper.CurrentVietnameseAddress;

import com.secondlife.secondlife.config.GeocodingProperties;
import com.secondlife.secondlife.dto.response.GeocodedAddressResponse;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.request.ShippingAddressRequest;
import com.secondlife.secondlife.entity.GeocodedLocationCache;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ShippingProviderException;
import com.secondlife.secondlife.repository.GeocodedLocationCacheRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Set;
import java.util.Locale;

@Service
public class GeoapifyGeocodingClient {
    private final GeocodingProperties properties;
    private final ShippingCatalogPolicy policy;
    private final ObjectMapper mapper;
    private final RestClient.Builder builder;
    private final GeocodedLocationCacheRepository cacheRepository;
    private volatile RestClient client;

    public GeoapifyGeocodingClient(GeocodingProperties properties, ShippingCatalogPolicy policy,
                                   ObjectMapper mapper, RestClient.Builder builder) {
        this(properties, policy, mapper, builder, null);
    }

    @Autowired
    public GeoapifyGeocodingClient(GeocodingProperties properties, ShippingCatalogPolicy policy,
                                   ObjectMapper mapper, RestClient.Builder builder,
                                   @Autowired(required = false) GeocodedLocationCacheRepository cacheRepository) {
        this.properties = properties;
        this.policy = policy;
        this.mapper = mapper;
        this.builder = builder;
        this.cacheRepository = cacheRepository;
        var factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        builder.requestFactory(factory);
    }

    public ShippingAddress shipping(ShippingAddressRequest request) {
        var resolved = reverse(request.latitude(), request.longitude());
        return new ShippingAddress(request.name().strip(), request.phone().strip(), resolved.address(), resolved.latitude(), resolved.longitude());
    }

    public GeocodedAddressResponse reverse(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null || latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0)
            throw new BadRequestException("Vĩ độ hoặc kinh độ không hợp lệ");

        // 1. Kiểm tra cache nội bộ theo toạ độ (làm tròn 4 chữ số thập phân ~ 11 mét)
        BigDecimal latKey = latitude.setScale(4, RoundingMode.HALF_UP);
        BigDecimal lonKey = longitude.setScale(4, RoundingMode.HALF_UP);

        var division = VietnameseAdministrativeAreas.locate(latitude.doubleValue(), longitude.doubleValue());
        GeocodedLocationCache cacheEntry = null;
        if (cacheRepository != null) {
            try {
                cacheEntry = cacheRepository.findByLatKeyAndLonKey(latKey, lonKey).orElse(null);
                if (isCurrentCacheEntry(cacheEntry, division)) {
                    return new GeocodedAddressResponse(cacheEntry.getFormattedAddress(), latitude.stripTrailingZeros(),
                            longitude.stripTrailingZeros(), division.city(), "VN", cacheEntry.getPlaceId());
                }
            } catch (RuntimeException ignored) {
                // Cache availability must not prevent address resolution.
            }
        }

        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank())
            throw new ShippingProviderException("Chưa cấu hình Geoapify; cần bật dịch vụ và cung cấp API key", 503);

        GeocodedAddressResponse resolved = null;
        ShippingProviderException geoapifyError = null;

        // 2. Gọi dịch vụ chính: Geoapify
        try {
            var uri = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                    .queryParam("lat", latitude.stripTrailingZeros().toPlainString())
                    .queryParam("lon", longitude.stripTrailingZeros().toPlainString())
                    .queryParam("lang", properties.getLanguage()).queryParam("format", "json")
                    .queryParam("apiKey", properties.getApiKey()).build().encode().toUri();

            JsonNode root = client().get().uri(uri).accept(MediaType.APPLICATION_JSON).exchange((request, response) -> {
                if (!response.getStatusCode().is2xxSuccessful())
                    throw new ShippingProviderException("Geoapify không xử lý được yêu cầu; kiểm tra API key, quyền truy cập và hạn mức", response.getStatusCode().value());
                return mapper.readTree(readBounded(response.getBody()));
            });
            if (root == null || !root.isObject() || !root.path("results").isArray()) throw malformed();
            if (root.path("results").isEmpty()) throw new BadRequestException("Không tìm thấy địa chỉ tại tọa độ này");

            for (var result : root.path("results")) {
                if (!result.isObject()) throw malformed();
                if (!Set.of("street", "building", "amenity").contains(result.path("result_type").asText())) continue;
                String country = result.path("country_code").asText().strip().toUpperCase(Locale.ROOT);
                if (!"VN".equals(country) || division == null || policy.allowedCity(division.city().name()) == null)
                    throw new BadRequestException("Địa chỉ nằm ngoài khu vực phục vụ Hà Nội, Đà Nẵng và Thành phố Hồ Chí Minh");
                String original = result.path("formatted").asText();
                if (original.isBlank() || original.length() > 500) throw malformed();
                String address = CurrentVietnameseAddress.format(result, division);
                resolved = new GeocodedAddressResponse(address, latitude.stripTrailingZeros(), longitude.stripTrailingZeros(),
                        division.city(), country, result.path("place_id").asText(null));
                break;
            }
            if (resolved == null) {
                throw new BadRequestException("Không tìm thấy địa chỉ đường hoặc tòa nhà; vui lòng chọn tọa độ chính xác hơn");
            }
        } catch (BadRequestException bre) {
            throw bre;
        } catch (ShippingProviderException error) {
            geoapifyError = error;
        } catch (ResourceAccessException error) {
            boolean timeout = false;
            for (Throwable cause = error; cause != null; cause = cause.getCause())
                if (cause instanceof java.net.SocketTimeoutException || cause instanceof java.net.http.HttpTimeoutException) timeout = true;
            geoapifyError = new ShippingProviderException(timeout ? "Geoapify request timed out" : "Geoapify is unavailable", timeout ? 504 : 502);
        } catch (RuntimeException error) {
            geoapifyError = malformed();
        }

        // 3. Fallback sang OpenStreetMap Nominatim khi Geoapify trả về 503 (Service Unavailable)
        if (resolved == null && geoapifyError != null && geoapifyError.getUpstreamStatus() == 503) {
            resolved = fallbackNominatim(latitude, longitude);
            if (resolved == null) {
                throw geoapifyError;
            }
        } else if (resolved == null && geoapifyError != null) {
            throw geoapifyError;
        }

        // 4. Lưu kết quả thành công vào cache
        if (resolved != null && cacheRepository != null) {
            try {
                if (cacheEntry == null) cacheEntry = new GeocodedLocationCache();
                cacheEntry.setLatKey(latKey);
                cacheEntry.setLonKey(lonKey);
                cacheEntry.setFormattedAddress(resolved.address());
                cacheEntry.setCity(resolved.city().name());
                cacheEntry.setCountry(resolved.countryCode());
                cacheEntry.setPlaceId(resolved.placeId());
                cacheRepository.save(cacheEntry);
            } catch (RuntimeException ignored) {}
        }

        return resolved;
    }

    private boolean isCurrentCacheEntry(GeocodedLocationCache entry, VietnameseAdministrativeAreas.Division division) {
        if (entry == null || division == null || policy.allowedCity(division.city().name()) == null
                || !"VN".equals(entry.getCountry()) || entry.getFormattedAddress() == null) return false;
        var city = policy.allowedCity(entry.getCity());
        if (city == null) city = policy.allowedAdministrativeCity(entry.getCity());
        if (city != division.city()) return false;
        // Legacy cache rows have only formatted text. Refresh instead of trusting stale administrative labels.
        var result = mapper.createObjectNode().put("formatted", entry.getFormattedAddress());
        try {
            return entry.getFormattedAddress().equals(CurrentVietnameseAddress.format(result, division));
        } catch (BadRequestException invalid) {
            return false;
        }
    }

    private GeocodedAddressResponse fallbackNominatim(BigDecimal latitude, BigDecimal longitude) {
        try {
            var uri = UriComponentsBuilder.fromUriString(properties.getFallbackBaseUrl())
                    .queryParam("lat", latitude.stripTrailingZeros().toPlainString())
                    .queryParam("lon", longitude.stripTrailingZeros().toPlainString())
                    .queryParam("format", "json")
                    .queryParam("addressdetails", "1")
                    .queryParam("accept-language", properties.getLanguage())
                    .build().encode().toUri();

            JsonNode root = client().get().uri(uri)
                    .header("User-Agent", properties.getFallbackUserAgent())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) return null;
                        return mapper.readTree(readBounded(response.getBody()));
                    });

            if (root == null || !root.isObject() || !root.path("address").isObject()) return null;
            String original = root.path("display_name").asText("");
            if (original.isBlank() || original.length() > 500) return null;
            JsonNode addr = root.path("address");
            String country = addr.path("country_code").asText("").strip().toUpperCase(Locale.ROOT);
            var division = VietnameseAdministrativeAreas.locate(latitude.doubleValue(), longitude.doubleValue());
            if (!"VN".equals(country) || division == null || policy.allowedCity(division.city().name()) == null) return null;
            var normalized = mapper.createObjectNode().put("formatted", original);
            addr.properties().forEach(e -> normalized.set(e.getKey(), e.getValue()));
            normalized.put("street", addr.path("road").asText(""));
            normalized.put("housenumber", addr.path("house_number").asText(""));
            String address = CurrentVietnameseAddress.format(normalized, division);
            String placeId = "osm-" + root.path("osm_id").asText("0");
            return new GeocodedAddressResponse(address, latitude.stripTrailingZeros(), longitude.stripTrailingZeros(),
                    division.city(), country, placeId);
        } catch (RuntimeException ignored) {
            // Preserve the sanitized primary error if the fallback also fails.
        }
        return null;
    }

    private synchronized RestClient client() {
        if (client == null) client = builder.build();
        return client;
    }

    private String readBounded(InputStream stream) throws IOException {
        if (stream == null) throw malformed();
        var reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
        var content = new StringBuilder();
        var buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            if (content.length() + count > properties.getMaxResponseCharacters()) throw malformed();
            content.append(buffer, 0, count);
        }
        return content.toString();
    }

    private ShippingProviderException malformed() {
        return new ShippingProviderException("Geoapify returned an invalid response");
    }
}
