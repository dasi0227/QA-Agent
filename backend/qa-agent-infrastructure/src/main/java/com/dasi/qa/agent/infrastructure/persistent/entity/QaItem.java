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
@TableName("qa_item")
public class QaItem {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    
    private String userId;
    
    private String qaSetId;
    
    private String question;
    
    private String knowledgeNote;
    
    private String answer;
    
    private String moduleTag;
    
    private String difficulty;
    
    private String keywords;

    private String hint;

    private Boolean sourceReliable;
    
    private String sourceChunkIdsJson;

    private String completeStatus;
    
    private Integer sortOrder;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
