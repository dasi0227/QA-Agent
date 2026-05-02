package com.dasi.qa.agent.application.configuration;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.dasi.qa.agent.infrastructure.properties.AliOssProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AliOssProperties.class)
public class AliOssConfiguration {

    @Bean(destroyMethod = "shutdown")
    public OSS ossClient(AliOssProperties properties) {
        return new OSSClientBuilder().build(
            properties.getEndpoint(),
            properties.getAccessKeyId(),
            properties.getAccessKeySecret()
        );
    }
}
