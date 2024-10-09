package com.yys.util;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class MinioClientProvider {

    @Value("${minio.url}")
    private static String Url;

    @Value("${minio.accesskey}")
    private static String accesskey;

    @Value("${minio.secretkey}")
    private static String secretkey;

    @Bean
    public static MinioClient createMinioClient() {
        // 创建 MinioClient
        MinioClient minioClient = MinioClient.builder()
                .endpoint(Url)
                .credentials(accesskey, secretkey)
                .build();
        return minioClient;
    }

}

