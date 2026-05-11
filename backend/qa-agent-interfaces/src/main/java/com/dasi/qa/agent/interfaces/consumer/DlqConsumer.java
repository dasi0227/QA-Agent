package com.dasi.qa.agent.interfaces.consumer;

import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DlqConsumer {


    @KafkaListener(topics = "${qa-agent.kafka.topic-document-index-dlq}", groupId = "${spring.kafka.consumer.group-id}-dlq")
    public void onDlqMessage(String message) {
        log.error("【死信队列】收到错误，暂时不处理: message={}", message);
    }
}
