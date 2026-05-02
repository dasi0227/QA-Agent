package com.dasi.qa.agent.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication(scanBasePackages = "com.dasi.qa.agent")
public class QaAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(QaAgentApplication.class, args);
    }

}
