package com.footprint.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footprint.backend.dto.ApiErrorResponse;
import com.footprint.backend.security.JwtAuthenticationFilter;
import com.footprint.backend.security.JwtProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

// 요청이 Controller에 닿기 전에 거치는 보안 검사(filter chain)를 설정합니다.
@Configuration
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final JwtProvider jwtProvider;

    public SecurityConfig(ObjectMapper objectMapper, JwtProvider jwtProvider) {
        this.objectMapper = objectMapper;
        this.jwtProvider = jwtProvider;
    }

    // 어떤 주소를 로그인 없이 열어 두고, 어떤 주소를 막을지 정합니다.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // WebConfig의 CORS 설정을 Security에도 적용합니다. 없으면 브라우저가 먼저 보내는 OPTIONS 요청이 401로 막힙니다.
                .cors(Customizer.withDefaults())
                // csrf는 cookie로 로그인할 때의 공격을 막는 장치입니다. 우리는 cookie 대신 token을 header로 보내므로 끕니다.
                .csrf(csrf -> csrf.disable())
                // 서버가 session을 만들지 않습니다. 요청마다 token으로 누구인지 확인합니다.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Spring Security가 기본으로 주는 로그인 화면과 브라우저 로그인 창을 끕니다. 로그인은 /auth/login API로만 합니다.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        // 로그인 API는 token이 없어도 불러야 합니다.
                        .requestMatchers("/auth/**").permitAll()
                        // 서버 오류가 나면 Spring이 /error로 다시 보냅니다. 이것까지 막으면 500 오류가 401로 바뀌어 보입니다.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                // token 확인 필터를 앞쪽에 둡니다. 주소별 허용·차단을 판단하기 전에 "누가 보냈는지"가 적혀 있어야 합니다.
                // UsernamePasswordAuthenticationFilter는 자리를 정하는 기준일 뿐입니다. formLogin을 껐으므로 실제로 실행되지는 않습니다.
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class)
                // 로그인하지 않은 요청이 막힌 주소에 오면 401 JSON을 돌려줍니다.
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다")));
        return http.build();
    }

    // 비밀번호를 BCrypt로 바꾸고, 입력한 비밀번호와 저장된 값을 비교합니다.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Security 단계에서 막힌 요청은 Controller에 닿지 않아 GlobalExceptionHandler가 처리하지 못합니다. 그래서 같은 모양의 JSON을 여기서 직접 씁니다.
    private void writeError(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), new ApiErrorResponse(code, message));
    }
}
