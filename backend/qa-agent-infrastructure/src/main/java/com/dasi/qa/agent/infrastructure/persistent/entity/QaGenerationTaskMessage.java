package com.dasi.qa.agent.infrastructure.persistent.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("qa_generation_task_message")
public class QaGenerationTaskMessage {

    @TableId
    private String id;

    private String taskId;

    private String userId;

    private String stage;

    private String message;

    private String content;

    private LocalDateTime createdAt;
}
