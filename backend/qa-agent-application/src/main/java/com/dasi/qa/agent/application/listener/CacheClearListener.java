package com.dasi.qa.agent.application.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@Slf4j
public class CacheClearListener implements ApplicationListener<ApplicationReadyEvent> {

    private final CacheManager cacheManager;

    public CacheClearListener(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("【事件】应用启动，已清理旧缓存");
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
    }
}
