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
@TableName("qa_set")
public class QaSet {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    
    private String userId;
    
    private String taskId;
    
    private String title;
    
    private String description;
    
    private String moduleTagsJson;
    
    private Integer questionCount;
    
    private Integer practiceCount;
    
    private Integer averageScore;
    
    private Integer bestScore;
    
    private BigDecimal averageAccuracy;
    
    private BigDecimal bestAccuracy;
    
    private LocalDateTime lastPracticedAt;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
