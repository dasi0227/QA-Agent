package com.dasi.qa.agent.interfaces.consumer;

import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DlqConsumer {


    @KafkaListener(topics = {
            "${qa-agent.kafka.topic-document-index-dlq}",
            "${qa-agent.kafka.topic-qa-item-assist-dlq}",
            "${qa-agent.kafka.topic-memory-ingest-dlq}"
    }, groupId = "${spring.kafka.consumer.group-id}-dlq")
    public void onDlqMessage(String message) {
        log.error("【消息队列消费者】收到死信消息，暂不处理: message={}", message);
    }
}
