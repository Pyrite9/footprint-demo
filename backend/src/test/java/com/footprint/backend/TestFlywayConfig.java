package com.footprint.backend;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

// 테스트의 Spring context가 뜰 때마다 footprint_test 스키마를 비우고 migration을 처음부터 다시 돌립니다.
// 그래서 빌드마다 "빈 데이터베이스 + migration"으로 만든 스키마에서 테스트가 돌고, migration이 Entity와 어긋나면 빌드가 깨집니다.
// 지난 빌드의 데이터도 남지 않아서 시작 데이터가 매번 같은 상태로 들어갑니다(ddl-auto: create를 쓰던 때와 같음).
// 비우는 스키마는 application-test.yml의 spring.flyway.schemas(footprint_test)뿐입니다. 개발 데이터(public)는 건드리지 않습니다.
// src/test에 있어서 테스트에서만 읽힙니다. @Profile("test")는 test 프로필이 아닌 테스트가 생겨도 clean이 돌지 않게 하는 안전장치입니다.
@Configuration
@Profile("test")
public class TestFlywayConfig {

    // Spring Boot가 서버를 켤 때 부르는 Flyway 실행 방법을 "clean 다음 migrate"로 바꿉니다. 기본은 migrate만 합니다.
    @Bean
    public FlywayMigrationStrategy cleanThenMigrate() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
