package com.secondlife.secondlife.service.shipping;
import com.secondlife.secondlife.exception.ConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import java.util.Map;
@Component @Primary
public class ShippingProviderRouter implements ShippingProvider {
    private final LalamoveShippingProvider lalamove;
    public ShippingProviderRouter(@Value("${app.shipping.provider}") String active,LalamoveShippingProvider lalamove) {
        if(!"LALAMOVE".equals(active)) throw new IllegalArgumentException("SHIPPING_PROVIDER must be LALAMOVE");
        this.lalamove=lalamove;
    }
    @Override public String code() { return "LALAMOVE"; }
    @Override public ShippingProvider forProvider(String code) {
        if(!"LALAMOVE".equals(code)) throw new ConflictException("Historical carrier integration is no longer available");
        return lalamove;
    }
    @Override public JsonNode cities() { return lalamove.cities(); }
    @Override public JsonNode driver(String id,String driver) { return lalamove.driver(id,driver); }
    @Override public JsonNode quote(Map<String,Object> p) { return lalamove.quote(p); }
    @Override public JsonNode preview(Map<String,Object> p) { return lalamove.preview(p); }
    @Override public JsonNode create(Map<String,Object> p) { return lalamove.create(p); }
    @Override public JsonNode detail(String code) { return lalamove.detail(code); }
    @Override public JsonNode cancel(String code) { return lalamove.cancel(code); }
    @Override public JsonNode label(String code) { return lalamove.label(code); }
    @Override public JsonNode returnToSender(String code) { return lalamove.returnToSender(code); }
}
