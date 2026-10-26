package com.footprint.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 서버 설정(bean, DB·MinIO 연결)이 오류 없이 올라오는지 확인합니다.
// 시작 데이터의 파일이 시연용 버킷에 쌓이지 않도록 StorageServiceTests와 같은 시험 전용 버킷을 씁니다.
// properties가 같으면 두 테스트가 서버 설정 하나를 같이 써서 시작 데이터도 한 번만 들어갑니다.
@SpringBootTest(properties = "storage.bucket=footprint-test")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
