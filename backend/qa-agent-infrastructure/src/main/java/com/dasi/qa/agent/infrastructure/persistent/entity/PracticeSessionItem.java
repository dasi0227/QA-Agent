package com.dasi.qa.agent.infrastructure.persistent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
@TableName("practice_session_item")
public class PracticeSessionItem {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    
    private String userId;
    
    private String sessionId;
    
    private String qaItemId;
    
    private Integer sortOrder;
    
    private String userAnswer;

    private String status;

    private Boolean unknown;
    
    private String result;
    
    private Integer score;
    
    private String feedbackSummary;

    private String feedbackJudgeDetail;

    private String feedbackHintDetail;
    
    private LocalDateTime answeredAt;

    private LocalDateTime submittedAt;

    private String questionSnapshot;

    private String standardAnswerSnapshot;

    private String knowledgeNoteSnapshot;

    private String keywordsSnapshot;

    private String moduleTagSnapshot;

    private String difficultySnapshot;

    private String sourceChunkIdsSnapshotJson;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
