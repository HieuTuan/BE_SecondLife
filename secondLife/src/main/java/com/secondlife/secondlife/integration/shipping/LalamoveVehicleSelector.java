package com.secondlife.secondlife.integration.shipping;

import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ShippingProviderException;
import com.secondlife.secondlife.common.VietnameseAdministrativeAreas;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.*;

/** Selects only vehicles with verified capacity; packed appliances remain upright. */
final class LalamoveVehicleSelector {
    private LalamoveVehicleSelector() { }

    static List<String> eligible(JsonNode catalogue, Map<String,Object> payload) {
        BigDecimal weight=packed(payload,"weight"),length=packed(payload,"length"),width=packed(payload,"width"),height=packed(payload,"height");
        var division=VietnameseAdministrativeAreas.locate(coordinate(payload,"from_latitude",90),coordinate(payload,"from_longitude",180));
        if(division==null) throw new BadRequestException("Điểm lấy hàng nằm ngoài khu vực vận chuyển được hỗ trợ");
        String locode=switch(division.city()) { case HANOI->"VN HAN";case DANANG->"VN DAD";case HO_CHI_MINH->"VN SGN"; };
        if(catalogue==null || !catalogue.isArray()) throw new ShippingProviderException("Lalamove returned an invalid vehicle catalogue");
        Set<String> keys=new LinkedHashSet<>();
        for(var city:catalogue) {
            if(!locode.equals(city.path("locode").asText().strip().toUpperCase(Locale.ROOT).replaceAll("\\s+"," "))) continue;
            for(var service:city.path("services")) {
                String key=service.path("key").asText();
                if(!key.matches("[A-Z][A-Z0-9_]{0,59}")) continue;
                BigDecimal load=capacity(service.path("load"),true),l=capacity(service.path("dimensions").path("length"),false),
                    w=capacity(service.path("dimensions").path("width"),false),h=capacity(service.path("dimensions").path("height"),false);
                if(load==null || l==null || w==null || h==null || weight.compareTo(load)>0 || height.compareTo(h)>0) continue;
                if((length.compareTo(l)<=0 && width.compareTo(w)<=0) || (length.compareTo(w)<=0 && width.compareTo(l)<=0)) keys.add(key);
            }
        }
        if(keys.isEmpty()) throw new BadRequestException("Không có loại xe Lalamove đủ tải trọng và kích thước cho kiện hàng tại điểm lấy hàng");
        return List.copyOf(keys);
    }
    static void validateParcel(Map<String,Object> payload) {
        for(String field:List.of("weight","length","width","height")) packed(payload,field);
    }
    private static BigDecimal packed(Map<String,Object> payload,String field) {
        try {
            var value=new BigDecimal(String.valueOf(payload.get(field)));
            if(value.signum()<=0 || value.stripTrailingZeros().scale()>0) throw new NumberFormatException();
            return value;
        } catch(RuntimeException ex) { throw new BadRequestException("Người bán cần cung cấp cân nặng (g) và kích thước kiện hàng (cm) hợp lệ"); }
    }
    private static double coordinate(Map<String,Object> payload,String field,int limit) {
        try {
            var value=new BigDecimal(String.valueOf(payload.get(field)));
            if(value.abs().compareTo(BigDecimal.valueOf(limit))>0) throw new NumberFormatException();
            return value.doubleValue();
        } catch(RuntimeException ex) { throw new BadRequestException("Điểm lấy hàng cần tọa độ hợp lệ để chọn xe vận chuyển"); }
    }
    private static BigDecimal capacity(JsonNode field,boolean weight) {
        try {
            var value=new BigDecimal(field.path("value").asText());
            if(value.signum()<=0) return null;
            String unit=field.path("unit").asText().strip().toLowerCase(Locale.ROOT);
            BigDecimal multiplier=weight?switch(unit) {case "g"->BigDecimal.ONE;case "kg"->BigDecimal.valueOf(1000);case "t"->BigDecimal.valueOf(1000000);default->null;}
                :switch(unit) {case "cm"->BigDecimal.ONE;case "m"->BigDecimal.valueOf(100);case "mm"->new BigDecimal("0.1");default->null;};
            return multiplier==null?null:value.multiply(multiplier);
        } catch(RuntimeException ex) { return null; }
    }
}
