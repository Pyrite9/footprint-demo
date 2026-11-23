package com.footprint.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 정해진 간격마다 메서드를 부르는 기능(@Scheduled)을 켭니다.
// @EnableScheduling이 없으면 @Scheduled를 붙여도 아무도 부르지 않습니다. 지금은 RefreshTokenService.deleteExpired 하나가 씁니다.
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
