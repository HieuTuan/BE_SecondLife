package com.secondlife.secondlife.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.cloudinary.utils.ObjectUtils;

@Configuration
public class CloudinaryConfig {

    @Value("${app.cloudinary.cloud-name:${CLOUDINARY_CLOUD_NAME:demo}}")
    private String cloudName;

    @Value("${app.cloudinary.api-key:${CLOUDINARY_API_KEY:demo}}")
    private String apiKey;

    @Value("${app.cloudinary.api-secret:${CLOUDINARY_API_SECRET:demo}}")
    private String apiSecret;

    @Bean
    public Cloudinary cloudinary() {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
    }
}
