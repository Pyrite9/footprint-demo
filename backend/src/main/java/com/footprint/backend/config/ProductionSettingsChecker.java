package com.footprint.backend.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.ArrayList;
import java.util.List;

// 운영 설정(prod 프로필)으로 켤 때, 데이터베이스에 접속하기 전에 설정값을 검사합니다.
// 비밀값이 저장소에 공개된 개발용 기본값이거나 비어 있으면, 또는 교수 이메일 목록이 비어 있으면 서버가 켜지지 않습니다.
// BackendApplication.main이 이 검사를 SpringApplication에 직접 넣습니다(addListeners). 개발 설정에서는 아무것도 하지 않습니다.
// 테스트(@SpringBootTest)는 main을 거치지 않아서 이 검사가 돌지 않습니다. 검사는 ProductionSettingsRuleTests가 따로 확인합니다.
public class ProductionSettingsChecker implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    // 저장소(application.yml, infra/.env.example, infra/docker-compose.yml)에 공개된 개발용 기본값입니다. 누구나 볼 수 있어서 운영에서 쓰면 안 됩니다.
    // application.yml의 기본값을 바꾸면 여기도 같이 바꿉니다.
    private static final String PUBLIC_JWT_SECRET = "footprint-demo-dev-secret-key-change-me-0000";
    private static final String PUBLIC_POSTGRES_PASSWORD = "change-me";
    private static final String PUBLIC_MINIO_ROOT_PASSWORD = "change-me-please";

    // Spring이 설정 파일과 환경 변수를 모두 읽은 직후에 부릅니다. 아직 bean을 하나도 만들지 않아서 데이터베이스와 MinIO에 접속하기 전입니다.
    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        check(event.getEnvironment());
    }

    // 운영 설정이면 설정값을 검사합니다. 문제가 있으면 환경 변수 이름만 담은 예외를 던져 서버 시작을 멈춥니다. 값은 로그에 남기지 않습니다.
    public void check(Environment environment) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }

        // getProperty는 application.yml의 ${JWT_SECRET:기본값}을 풀어서, 환경 변수가 있으면 그 값을, 없으면 기본값을 돌려줍니다.
        List<String> names = new ArrayList<>();
        addIfPublicOrBlank(names, "JWT_SECRET", environment.getProperty("jwt.secret"), PUBLIC_JWT_SECRET);
        addIfPublicOrBlank(names, "POSTGRES_PASSWORD", environment.getProperty("spring.datasource.password"), PUBLIC_POSTGRES_PASSWORD);
        addIfPublicOrBlank(names, "MINIO_ROOT_PASSWORD", environment.getProperty("storage.secret-key"), PUBLIC_MINIO_ROOT_PASSWORD);

        // 운영 설정에는 시작 계정도 이메일·비밀번호 로그인도 없어서, 교수 목록이 비면 교수가 될 길이 없고 아무도 과제를 만들 수 없습니다.
        String professorEmails = environment.getProperty("google.professor-emails");
        if (professorEmails == null || professorEmails.replace(",", "").isBlank()) {
            names.add("PROFESSOR_EMAILS");
        }

        if (!names.isEmpty()) {
            throw new IllegalStateException("운영 설정(prod)으로 켤 수 없습니다. 바꿔야 하는 환경 변수: " + String.join(", ", names)
                    + ". 비밀값(JWT_SECRET, POSTGRES_PASSWORD, MINIO_ROOT_PASSWORD)은 저장소에 공개된 개발용 기본값이나 빈 값을 쓸 수 없고,"
                    + " PROFESSOR_EMAILS는 비울 수 없습니다");
        }
    }

    // 값이 비었거나 공개된 개발용 기본값과 같으면 환경 변수 이름을 목록에 더합니다.
    private void addIfPublicOrBlank(List<String> names, String name, String value, String publicValue) {
        if (value == null || value.isBlank() || value.equals(publicValue)) {
            names.add(name);
        }
    }
}
