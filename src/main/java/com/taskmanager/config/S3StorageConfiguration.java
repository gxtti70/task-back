package com.taskmanager.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(prefix = "taskmanager.billing", name = "simulate", havingValue = "false")
public class S3StorageConfiguration {
    @Bean(destroyMethod = "close")
    DefaultCredentialsProvider s3CredentialsProvider() {
        return DefaultCredentialsProvider.create();
    }

    @Bean(destroyMethod = "close")
    S3Client s3Client(TaskManagerProperties properties, DefaultCredentialsProvider credentialsProvider) {
        return S3Client.builder()
                .region(Region.of(properties.getStorage().getS3().getRegion()))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(TaskManagerProperties properties, DefaultCredentialsProvider credentialsProvider) {
        return S3Presigner.builder()
                .region(Region.of(properties.getStorage().getS3().getRegion()))
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
