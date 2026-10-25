package com.footprint.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 프론트 개발 서버(http://localhost:5173) 에서 백엔드로의 접근(allowedMethods)을 허용합니다
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
