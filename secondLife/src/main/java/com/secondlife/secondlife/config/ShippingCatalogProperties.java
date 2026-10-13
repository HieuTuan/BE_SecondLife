package com.secondlife.secondlife.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import java.util.List;

@Component @Validated @Getter @Setter
@ConfigurationProperties(prefix="app.shipping.catalog")
public class ShippingCatalogProperties {
    public enum City { HANOI, DANANG, HO_CHI_MINH }
    @NotEmpty private List<@NotNull City> allowedCities;
}
