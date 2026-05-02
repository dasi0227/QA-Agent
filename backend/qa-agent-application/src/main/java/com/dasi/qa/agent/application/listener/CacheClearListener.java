package com.dasi.qa.agent.application.listener;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class CacheClearListener implements ApplicationListener<ApplicationReadyEvent> {

    private final CacheManager cacheManager;

    public CacheClearListener(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }
}
