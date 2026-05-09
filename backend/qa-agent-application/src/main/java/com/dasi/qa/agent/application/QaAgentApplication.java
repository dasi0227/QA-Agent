package com.dasi.qa.agent.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationListener;

import java.util.Objects;

@EnableCaching
@SpringBootApplication(scanBasePackages = "com.dasi.qa.agent")
@Slf4j
public class QaAgentApplication implements ApplicationListener<ApplicationReadyEvent> {

    public static void main(String[] args)  {
        SpringApplication.run(QaAgentApplication.class, args);
    }

    private final CacheManager cacheManager;

    public QaAgentApplication(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("【启动】已清理旧缓存");
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
    }


}
