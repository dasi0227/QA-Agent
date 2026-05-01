package com.dasi.qa.agent.infrastructure.persistent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("practice_session_item")
public class PracticeSessionItemEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    
    private String userId;
    
    private String sessionId;
    
    private String qaItemId;
    
    private Integer sortOrder;
    
    private String userAnswer;
    
    private String result;
    
    private Integer score;
    
    private String feedbackSummary;
    
    private LocalDateTime answeredAt;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
