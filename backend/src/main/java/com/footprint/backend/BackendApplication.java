package com.footprint.backend;

import com.footprint.backend.config.ProductionSettingsChecker;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Spring Boot 애플리케이션을 시작합니다.
@SpringBootApplication
public class BackendApplication {

	// 서버를 켭니다. IntelliJ의 실행 버튼, gradlew bootRun, java -jar가 이 메서드를 부릅니다.
	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(BackendApplication.class);
		// 운영 설정(prod)이면 데이터베이스에 접속하기 전에 비밀값을 검사합니다. 개발 설정에서는 검사하지 않습니다.
		application.addListeners(new ProductionSettingsChecker());
		application.run(args);
	}

}
