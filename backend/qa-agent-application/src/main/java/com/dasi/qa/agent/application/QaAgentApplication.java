package com.dasi.qa.agent.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.dasi.qa.agent")
@Slf4j
public class QaAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(QaAgentApplication.class, args);
        log.info("================================== 程序启动 ================================== ");
    }

}
