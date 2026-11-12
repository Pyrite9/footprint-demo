package com.footprint.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 상태 확인(O-8) 규칙을 개발 설정에서 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 운영 설정에서도 같은지는 ProductionSettingsRuleTests에서 확인합니다.
// 데이터베이스가 꺼졌을 때 DOWN이 되는지는 테스트로 만들 수 없어서 실제 서버로 확인했습니다(docs/worklog.md O-8).
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HealthRuleTests {

    // 열지 않은 actuator 주소들입니다. /actuator는 열린 주소 목록 화면입니다.
    static final List<String> CLOSED_ACTUATOR_PATHS = List.of(
            "/actuator", "/actuator/env", "/actuator/beans", "/actuator/metrics", "/actuator/info",
            "/actuator/configprops", "/actuator/mappings", "/actuator/loggers", "/actuator/heapdump",
            "/actuator/threaddump");

    // 상태 확인의 항목별 주소입니다(db: 데이터베이스, diskSpace: 디스크). 항목별 상태를 내보내지 않아서 닫혀 있습니다.
    static final List<String> HEALTH_COMPONENT_PATHS = List.of("/actuator/health/db", "/actuator/health/diskSpace");

    private final MockMvc mockMvc;
    private final TestApi api;

    @Autowired
    HealthRuleTests(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
        this.api = new TestApi(mockMvc);
    }

    // token 없이 200 UP이고, 응답은 status 칸 하나뿐입니다(DB 종류, 디스크 경로, 버전 같은 상세 없음).
    @Test
    void healthIsUpWithoutToken() throws Exception {
        api.getWithoutToken("/actuator/health")
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        { "status": "UP" }
                        """, true));
    }

    // 다른 actuator 주소와 항목별 주소는 token이 없으면 다른 막힌 주소처럼 401입니다.
    @Test
    void otherActuatorPathsNeedToken() throws Exception {
        for (String path : allClosedPaths()) {
            api.getWithoutToken(path)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    // 로그인한 회원이 불러도 없는 주소(404)입니다. 교수도 같습니다.
    @Test
    void otherActuatorPathsAreNotFound() throws Exception {
        String professorToken = api.login("professor@example.com");
        for (String path : CLOSED_ACTUATOR_PATHS) {
            api.get(path, professorToken)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("없는 주소입니다"));
        }
    }

    // 항목별 주소는 로그인한 회원이 불러도 404입니다. 다만 actuator가 직접 내는 응답이라 body가 비어 있습니다({ code, message } 아님).
    @Test
    void healthComponentPathsAreEmptyNotFound() throws Exception {
        String professorToken = api.login("professor@example.com");
        for (String path : HEALTH_COMPONENT_PATHS) {
            api.get(path, professorToken)
                    .andExpect(status().isNotFound())
                    .andExpect(content().string(""));
        }
    }

    // 상태 확인 주소에 GET이 아닌 method를 보내면 다른 주소와 같이 404 "없는 주소입니다"입니다.
    @Test
    void healthAcceptsOnlyGet() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/actuator/health"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // token 없이 부를 때 401이어야 하는 주소 전체입니다.
    static List<String> allClosedPaths() {
        List<String> paths = new ArrayList<>(CLOSED_ACTUATOR_PATHS);
        paths.addAll(HEALTH_COMPONENT_PATHS);
        return paths;
    }
}
