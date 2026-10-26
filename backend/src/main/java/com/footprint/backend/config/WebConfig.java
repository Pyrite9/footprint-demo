package com.footprint.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 화면(http://localhost:5173)이 백엔드를 부를 수 있도록 CORS를 허용합니다.
@Configuration
public class WebConfig implements WebMvcConfigurer {

    // 모든 주소에 대해 5173 화면의 요청을 허용합니다.
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE");
    }
}
