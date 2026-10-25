package com.footprint.backend;

import com.footprint.backend.exception.NotFoundException;
import com.footprint.backend.service.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// StorageService가 MinIO에 파일을 올리고 같은 내용으로 꺼내는지 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 시험 파일이 시연용 버킷에 쌓이지 않도록 시험 전용 버킷(footprint-test)을 씁니다.
@SpringBootTest(properties = "storage.bucket=footprint-test")
class StorageServiceTests {

    private final StorageService storageService;

    // 테스트 클래스는 JUnit이 만들기 때문에, 생성자 주입을 하려면 생성자에 @Autowired를 붙여야 합니다.
    @Autowired
    StorageServiceTests(StorageService storageService) {
        this.storageService = storageService;
    }

    // 한글 이름 파일을 올린 뒤 꺼낸 내용이 올린 내용과 같은지 확인합니다.
    @Test
    void saveAndLoad() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "요구사항 명세서.txt", "text/plain", "발자국 저장 시험".getBytes(StandardCharsets.UTF_8));

        String key = storageService.save(file);

        try (InputStream loaded = storageService.load(key).getInputStream()) {
            assertArrayEquals(file.getBytes(), loaded.readAllBytes());
        }
    }

    // 없는 key를 꺼내면 NotFoundException이 나는지 확인합니다.
    @Test
    void loadMissingKey() {
        assertThrows(NotFoundException.class, () -> storageService.load("no-such-key"));
    }
}
