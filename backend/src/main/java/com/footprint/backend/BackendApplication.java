package com.footprint.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Spring Boot 애플리케이션을 시작합니다.
@SpringBootApplication
public class BackendApplication {

	// 서버를 켭니다. IntelliJ의 실행 버튼이나 gradlew bootRun이 이 메서드를 부릅니다.
	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
