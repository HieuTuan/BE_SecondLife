package com.secondlife.secondlife.service.shipping;

import com.secondlife.secondlife.dto.shipping.*;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.exception.BadRequestException;
import java.util.*;
import java.math.BigDecimal;

public final class ShippingPayloads {
    private ShippingPayloads() {}
    public static ShippingParcel parcel(Post post) {
        if (post.getShippingWeight()==null || post.getShippingLength()==null || post.getShippingWidth()==null || post.getShippingHeight()==null)
            throw new BadRequestException("Seller must save the packed weight and dimensions before a GHN quote");
        return new ShippingParcel(post.getShippingWeight(),post.getShippingLength(),post.getShippingWidth(),post.getShippingHeight());
    }
    public static Map<String,Object> create(ShippingAddress from, ShippingAddress to, ShippingParcel parcel, String title, BigDecimal price) {
        if (price==null || price.signum()<=0 || price.stripTrailingZeros().scale()>0 || price.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE))>0)
            throw new BadRequestException("GHN shipping requires a positive product price in whole VND within its integer limit");
        if (parcel.weight()<1 || parcel.weight()>50000 || parcel.length()<1 || parcel.length()>200
                || parcel.width()<1 || parcel.width()>200 || parcel.height()<1 || parcel.height()>200)
            throw new BadRequestException("GHN supports packages up to 50 kg and 200 cm per dimension");
        var data = new LinkedHashMap<String,Object>();
        address(data,"from",from); address(data,"to",to); address(data,"return",from);
        if (from.districtId()!=null) data.put("from_district_id",from.districtId());
        if (from.wardCode()!=null && !from.wardCode().isBlank()) data.put("from_ward_code",from.wardCode());
        if (to.districtId()!=null) data.put("to_district_id",to.districtId());
        if (to.wardCode()!=null && !to.wardCode().isBlank()) data.put("to_ward_code",to.wardCode());
        data.put("weight",parcel.weight()); data.put("length",parcel.length()); data.put("width",parcel.width()); data.put("height",parcel.height());
        data.put("service_type_id",parcel.weight()>=20000 ? 5 : 2);
        data.put("payment_type_id",1); // Platform shop settles GHN fees; buyer's fee is recorded on the paid order.
        data.put("cod_amount",0); data.put("required_note","CHOXEMHANGKHONGTHU");
        data.put("insurance_value", price.min(BigDecimal.valueOf(5000000)).setScale(0,java.math.RoundingMode.DOWN).intValueExact());
        data.put("order_value",price.intValueExact());
        data.put("content",title);
        data.put("items",List.of(Map.of("name",title,"quantity",1,"weight",parcel.weight(),"length",parcel.length(),"width",parcel.width(),"height",parcel.height())));
        return data;
    }
    public static ShippingAddress storedAddress(tools.jackson.databind.JsonNode data,String prefix) {
        return new ShippingAddress(data.path(prefix+"_name").asText(),data.path(prefix+"_phone").asText(),data.path(prefix+"_address").asText(),
            data.path(prefix+"_province_name").asText(),data.path(prefix+"_district_name").asText(),data.path(prefix+"_ward_name").asText(),
            data.has(prefix+"_district_id")?data.path(prefix+"_district_id").asInt():null,data.has(prefix+"_ward_code")?data.path(prefix+"_ward_code").asText():null,data.path("is_new_"+prefix+"_address").asBoolean());
    }
    public static ShippingParcel storedParcel(tools.jackson.databind.JsonNode data) {
        return new ShippingParcel(data.path("weight").asInt(),data.path("length").asInt(),data.path("width").asInt(),data.path("height").asInt());
    }
    public static Map<String,Object> fee(Map<String,Object> create) {
        var fee = new LinkedHashMap<String,Object>();
        for (String key : List.of("from_district_id","from_ward_code","to_district_id","to_ward_code","service_type_id","weight","length","width","height","insurance_value","items"))
            fee.put(key,create.get(key));
        return fee;
    }
    private static void address(Map<String,Object> data,String prefix,ShippingAddress address) {
        if (address==null) throw new BadRequestException("Pickup and delivery addresses are required");
        data.put(prefix+"_name",address.name()); data.put(prefix+"_phone",address.phone()); data.put(prefix+"_address",address.address());
        data.put(prefix+"_province_name",address.provinceName()); data.put(prefix+"_ward_name",address.wardName());
        if (!address.newAddress()) data.put(prefix+"_district_name",address.districtName());
        data.put("is_new_"+prefix+"_address",address.newAddress());
    }
}
