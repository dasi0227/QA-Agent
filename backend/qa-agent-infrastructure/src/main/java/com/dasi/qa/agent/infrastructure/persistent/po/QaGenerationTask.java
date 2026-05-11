package com.dasi.qa.agent.infrastructure.persistent.po;

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
@TableName("qa_generation_task")
public class QaGenerationTask {

    @TableId
    private String id;

    private String userId;

    private String title;

    private String note;

    private String documentIdsJson;

    private String qaSetId;

    private String status;

    private String stage;

    private String errorCode;

    private String errorMessage;

    private Boolean allowGeneralKnowledge;

    private Boolean allowWebSearch;

    private Integer requestedQuestionCount;

    private LocalDateTime createdAt;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private LocalDateTime updatedAt;
}
