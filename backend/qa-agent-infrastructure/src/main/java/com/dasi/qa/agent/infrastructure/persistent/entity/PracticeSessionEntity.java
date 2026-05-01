package com.dasi.qa.agent.infrastructure.persistent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("practice_session")
public class PracticeSessionEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    
    private String userId;
    
    private String qaSetId;
    
    private String mode;
    
    private String feedbackMode;
    
    private String status;
    
    private String selectedModule;
    
    private Integer totalQuestions;
    
    private Integer answeredCount;
    
    private Integer score;
    
    private BigDecimal accuracy;
    
    private String summary;
    
    private LocalDateTime startedAt;
    
    private LocalDateTime finishedAt;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
