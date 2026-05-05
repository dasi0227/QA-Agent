package com.dasi.qa.agent.application.configuration;

import lombok.extern.slf4j.Slf4j;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class XxlJobConfiguration {


    @Bean
    public XxlJobSpringExecutor xxlJobExecutor(
            @Value("${qa-agent.xxl-job.admin-addresses}") String adminAddresses,
            @Value("${qa-agent.xxl-job.app-name}") String appName,
            @Value("${qa-agent.xxl-job.port}") int port,
            @Value("${qa-agent.xxl-job.access-token}") String accessToken,
            @Value("${qa-agent.xxl-job.log-path}") String logPath,
            @Value("${qa-agent.xxl-job.log-retention-days}") int logRetentionDays) {
        log.info("xxl-job config: adminAddresses={}, appName={}, port={}", adminAddresses, appName, port);
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAppname(appName);
        executor.setPort(port);
        executor.setAccessToken(accessToken);
        executor.setLogPath(logPath);
        executor.setLogRetentionDays(logRetentionDays);
        return executor;
    }
}
