# 백엔드 이미지를 만듭니다. 저장소 맨 위의 docker-compose.yml이 이 파일을 씁니다.
# 두 단계로 나눕니다. 1단계(build)는 JDK로 jar를 만들고, 2단계는 실행에 필요한 JRE와 jar만 담아 이미지를 작게 합니다.

# 1단계: 소스로 실행 파일(jar)을 만듭니다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Gradle 설정만 먼저 복사해 의존성을 내려받습니다. 소스만 바뀌면 Docker가 이 단계를 저장해 둔 결과(cache)로 건너뜁니다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
# Windows에서 받은 저장소는 gradlew에 실행 권한이 없을 수 있어서 붙여 줍니다.
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

COPY src src
# 테스트는 실제 PostgreSQL·MinIO에 접속해야 해서 이미지를 만들 때는 건너뜁니다(-x test). 테스트는 내 컴퓨터의 gradlew build로 돌립니다.
RUN ./gradlew bootJar -x test --no-daemon

# 2단계: 만든 jar를 실행합니다.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
