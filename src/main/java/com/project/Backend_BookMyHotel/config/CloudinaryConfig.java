package com.project.Backend_BookMyHotel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

@Configuration
public class CloudinaryConfig {

    @Value("${cloudinary_cloud_name}")
    private String cloud_name;

    @Value("${cloudinary_api_key}")
    private String cloud_api_key;

    @Value("${cloudinary_api_secret}")
    private String cloud_api_secret;

    @Bean
    public Cloudinary cloudinary() {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloud_name,
                "api_key", cloud_api_key,
                "api_secret", cloud_api_secret,
                "secure", true
        ));
    }
}
