package com.footprint.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// 서버 설정(bean, DB·MinIO 연결)이 오류 없이 올라오는지 확인합니다.
// test 프로필: 시험 전용 스키마와 버킷을 씁니다(src/test/resources/application-test.yml). 개발 데이터를 지우지 않습니다.
// @AutoConfigureMockMvc: 규칙 테스트와 설정을 똑같이 맞춥니다. 같으면 서버 설정 하나를 같이 써서 시작 데이터가 한 번만 들어갑니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
