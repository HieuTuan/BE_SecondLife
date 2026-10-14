package com.secondlife.secondlife.service.shipping;

import com.secondlife.secondlife.config.GeocodingProperties;
import com.secondlife.secondlife.dto.shipping.*;
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
public class ReverseGeocodingService {
    private final GeocodingProperties properties;
    private final ShippingCatalogPolicy policy;
    private final ObjectMapper mapper;
    private final RestClient.Builder builder;
    private final GeocodedLocationCacheRepository cacheRepository;
    private volatile RestClient client;

    public ReverseGeocodingService(GeocodingProperties properties, ShippingCatalogPolicy policy,
                                   ObjectMapper mapper, RestClient.Builder builder) {
        this(properties, policy, mapper, builder, null);
    }

    @Autowired
    public ReverseGeocodingService(GeocodingProperties properties, ShippingCatalogPolicy policy,
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

        if (cacheRepository != null) {
            try {
                var cached = cacheRepository.findByLatKeyAndLonKey(latKey, lonKey);
                if (cached.isPresent()) {
                    var c = cached.get();
                    var city = policy.allowedCity(c.getCity()) != null
                            ? policy.allowedCity(c.getCity())
                            : policy.allowedAdministrativeCity(c.getCity());
                    if (city != null) {
                        return new GeocodedAddressResponse(
                                c.getFormattedAddress(),
                                latitude.stripTrailingZeros(),
                                longitude.stripTrailingZeros(),
                                city,
                                c.getCountry(),
                                c.getPlaceId()
                        );
                    }
                }
            } catch (Exception ignored) {}
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
                String state = result.path("state").asText("").strip();
                var city = state.isBlank() ? policy.allowedCity(result.path("city").asText("")) : policy.allowedAdministrativeCity(state);
                if (!"VN".equals(country) || city == null)
                    throw new BadRequestException("Địa chỉ nằm ngoài khu vực phục vụ Hà Nội, Đà Nẵng và Thành phố Hồ Chí Minh");
                String address = result.path("formatted").asText();
                if (address.isBlank() || address.length() > 500) throw malformed();
                resolved = new GeocodedAddressResponse(address, latitude.stripTrailingZeros(), longitude.stripTrailingZeros(), city, country, result.path("place_id").asText(null));
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
                cacheRepository.save(new GeocodedLocationCache(
                        latKey,
                        lonKey,
                        resolved.address(),
                        resolved.city().name(),
                        resolved.countryCode(),
                        resolved.placeId()
                ));
            } catch (Exception ignored) {}
        }

        return resolved;
    }

    private GeocodedAddressResponse fallbackNominatim(BigDecimal latitude, BigDecimal longitude) {
        try {
            var uri = UriComponentsBuilder.fromUriString("https://nominatim.openstreetmap.org/reverse")
                    .queryParam("lat", latitude.stripTrailingZeros().toPlainString())
                    .queryParam("lon", longitude.stripTrailingZeros().toPlainString())
                    .queryParam("format", "json")
                    .queryParam("addressdetails", "1")
                    .queryParam("accept-language", "vi")
                    .build().encode().toUri();

            JsonNode root = client().get().uri(uri)
                    .header("User-Agent", "SecondLife-Platform/1.0 (support@secondlife.tech)")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) return null;
                        return mapper.readTree(readBounded(response.getBody()));
                    });

            if (root != null && root.has("display_name")) {
                String address = root.path("display_name").asText();
                JsonNode addr = root.path("address");
                String country = addr.path("country_code").asText("").strip().toUpperCase(Locale.ROOT);
                String state = addr.path("state").asText("").strip();
                String cityStr = addr.path("city").asText("").strip();
                if (cityStr.isBlank()) cityStr = addr.path("province").asText("").strip();
                if (cityStr.isBlank()) cityStr = state;

                var city = policy.allowedAdministrativeCity(state) != null
                        ? policy.allowedAdministrativeCity(state)
                        : policy.allowedCity(cityStr);

                if ("VN".equals(country) && city != null && !address.isBlank()) {
                    String placeId = "osm-" + root.path("osm_id").asText("0");
                    return new GeocodedAddressResponse(address, latitude.stripTrailingZeros(), longitude.stripTrailingZeros(), city, country, placeId);
                }
            }
        } catch (Throwable ignored) {}
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
