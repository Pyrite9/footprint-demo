package com.footprint.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

// MinIO에 접속하는 S3 클라이언트를 만듭니다. 코드는 S3 API로만 접근하므로 다른 S3 호환 서버로 바꿀 수 있습니다.
@Configuration
public class S3Config {

    // StorageService가 쓰는 S3Client를 Spring bean으로 등록합니다. 접속값은 application.yml의 storage에서 받습니다.
    @Bean
    public S3Client s3Client(@Value("${storage.endpoint}") String endpoint,
                             @Value("${storage.access-key}") String accessKey,
                             @Value("${storage.secret-key}") String secretKey) {
        return S3Client.builder()
                // AWS 서버 대신 우리 MinIO 주소로 보냅니다.
                .endpointOverride(URI.create(endpoint))
                // MinIO는 region을 쓰지 않지만 SDK가 반드시 요구해서 아무 값이나 넣습니다.
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                // 버킷 이름을 주소 앞(footprint.localhost:9000)이 아니라 경로(localhost:9000/footprint)에 넣습니다. localhost에서는 이 방식만 됩니다.
                .forcePathStyle(true)
                .build();
    }
}
