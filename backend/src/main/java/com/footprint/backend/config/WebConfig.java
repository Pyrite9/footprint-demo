package com.footprint.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

// 다른 주소의 화면이 백엔드를 직접 부를 수 있도록 CORS를 허용합니다. 허용할 화면 주소는 application.yml의 cors.allowed-origins입니다.
// 우리 화면은 같은 주소의 /api로 불러서(Vite proxy, nginx) CORS를 거치지 않습니다. 이 설정은 가짜 API를 쓰던 화면 코드나
// 다른 주소에서 백엔드(127.0.0.1:8080)를 직접 부르는 경우를 위한 것입니다.
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    // 쉼표로 구분한 주소를 Spring이 목록으로 바꿔 넣어 줍니다. 기본값은 개발 화면 주소 http://localhost:5173 하나입니다.
    public WebConfig(@Value("${cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    // 모든 주소에 대해 허용한 화면 주소의 요청을 받습니다. 목록에 없는 화면이 보낸 요청은 브라우저가 막습니다(사전 요청은 403).
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                // 다른 주소에서 부를 때도 refresh token cookie를 주고받게 합니다(브라우저의 fetch credentials: 'include').
                // 이것을 켜면 allowedOrigins에 "*"를 쓸 수 없습니다. 주소를 하나씩 적어야 합니다.
                .allowCredentials(true);
    }
}
