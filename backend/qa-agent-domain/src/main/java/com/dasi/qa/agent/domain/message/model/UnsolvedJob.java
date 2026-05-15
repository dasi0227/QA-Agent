package com.dasi.qa.agent.domain.message.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnsolvedJob {

    private String jobId;

    private int retry;

    private String topic;

    private String content;

}
