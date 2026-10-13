package com.secondlife.secondlife.service.shipping;

import com.secondlife.secondlife.config.GeocodingProperties;
import com.secondlife.secondlife.dto.shipping.*;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ShippingProviderException;
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
import java.time.Duration;
import java.util.Set;
import java.util.Locale;

@Service
public class ReverseGeocodingService {
    private final GeocodingProperties properties;
    private final ShippingCatalogPolicy policy;
    private final ObjectMapper mapper;
    private final RestClient.Builder builder;
    private volatile RestClient client;
    public ReverseGeocodingService(GeocodingProperties properties,ShippingCatalogPolicy policy,
                                  ObjectMapper mapper,RestClient.Builder builder) {
        this.properties=properties; this.policy=policy; this.mapper=mapper; this.builder=builder;
        var factory=new SimpleClientHttpRequestFactory() {
            @Override protected void prepareConnection(HttpURLConnection connection,String method) throws IOException {
                super.prepareConnection(connection,method); connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        builder.requestFactory(factory);
    }
    public ShippingAddress shipping(ShippingAddressRequest request) {
        var resolved=reverse(request.latitude(),request.longitude());
        return new ShippingAddress(request.name().strip(),request.phone().strip(),resolved.address(),resolved.latitude(),resolved.longitude());
    }
    public GeocodedAddressResponse reverse(BigDecimal latitude,BigDecimal longitude) {
        if(latitude==null || longitude==null || latitude.abs().compareTo(BigDecimal.valueOf(90))>0
                || longitude.abs().compareTo(BigDecimal.valueOf(180))>0) throw new BadRequestException("Vĩ độ hoặc kinh độ không hợp lệ");
        if(!properties.isEnabled() || properties.getApiKey()==null || properties.getApiKey().isBlank())
            throw new ShippingProviderException("Chưa cấu hình Geoapify; cần bật dịch vụ và cung cấp API key",503);
        JsonNode root;
        try {
            var uri=UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                    .queryParam("lat",latitude.stripTrailingZeros().toPlainString())
                    .queryParam("lon",longitude.stripTrailingZeros().toPlainString())
                    .queryParam("lang",properties.getLanguage()).queryParam("format","json")
                    .queryParam("apiKey",properties.getApiKey()).build().encode().toUri();
            root=client().get().uri(uri).accept(MediaType.APPLICATION_JSON).exchange((request,response) -> {
                if(!response.getStatusCode().is2xxSuccessful()) throw new ShippingProviderException("Geoapify không xử lý được yêu cầu; kiểm tra API key, quyền truy cập và hạn mức");
                return mapper.readTree(readBounded(response.getBody()));
            });
        } catch(ShippingProviderException error) { throw error; }
        catch(ResourceAccessException error) {
            boolean timeout=false;
            for(Throwable cause=error;cause!=null;cause=cause.getCause())
                if(cause instanceof java.net.SocketTimeoutException || cause instanceof java.net.http.HttpTimeoutException) timeout=true;
            throw new ShippingProviderException(timeout?"Geoapify request timed out":"Geoapify is unavailable",timeout?504:502);
        }
        catch(RuntimeException error) { throw malformed(); }
        // Do not retain upstream errors: they can contain the API key in the request URL.
        if(root==null || !root.isObject()) throw malformed();
        if(!root.path("results").isArray()) throw malformed();
        if(root.path("results").isEmpty()) throw new BadRequestException("Không tìm thấy địa chỉ tại tọa độ này");
        for(var result:root.path("results")) {
            if(!result.isObject()) throw malformed();
            if(!Set.of("street","building","amenity").contains(result.path("result_type").asText())) continue;
            String country=result.path("country_code").asText().strip().toUpperCase(Locale.ROOT);
            // Normalize legacy province names; an unrelated state still cannot be overridden by city text.
            String state=result.path("state").asText("").strip();
            var city=state.isBlank()?policy.allowedCity(result.path("city").asText("")):policy.allowedAdministrativeCity(state);
            if(!"VN".equals(country) || city==null) throw new BadRequestException("Địa chỉ nằm ngoài khu vực phục vụ Hà Nội, Đà Nẵng và Thành phố Hồ Chí Minh");
            String address=result.path("formatted").asText();
            if(address.isBlank() || address.length()>500) throw malformed();
            return new GeocodedAddressResponse(address,latitude.stripTrailingZeros(),longitude.stripTrailingZeros(),city,country,result.path("place_id").asText(null));
        }
        throw new BadRequestException("Không tìm thấy địa chỉ đường hoặc tòa nhà; vui lòng chọn tọa độ chính xác hơn");
    }
    private synchronized RestClient client() { if(client==null) client=builder.build(); return client; }
    private String readBounded(InputStream stream) throws IOException {
        if(stream==null) throw malformed();
        var reader=new InputStreamReader(stream,StandardCharsets.UTF_8); var content=new StringBuilder(); var buffer=new char[4096]; int count;
        while((count=reader.read(buffer))!=-1) {
            if(content.length()+count>properties.getMaxResponseCharacters()) throw malformed();
            content.append(buffer,0,count);
        }
        return content.toString();
    }
    private ShippingProviderException malformed() { return new ShippingProviderException("Geoapify returned an invalid response"); }
}
