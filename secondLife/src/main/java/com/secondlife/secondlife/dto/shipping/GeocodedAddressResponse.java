package com.secondlife.secondlife.dto.shipping;
import com.secondlife.secondlife.config.ShippingCatalogProperties.City;
import java.math.BigDecimal;
public record GeocodedAddressResponse(String address, BigDecimal latitude, BigDecimal longitude,
        City city, String countryCode, String placeId) {}
