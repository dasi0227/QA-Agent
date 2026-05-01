package com.dasi.qa.agent.infrastructure.persistent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("qa_item")
public class QaItemEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    
    private String userId;
    
    private String qaSetId;
    
    private String question;
    
    private String knowledgeNote;
    
    private String answer;
    
    private String moduleTag;
    
    private String difficulty;
    
    private String conflictTip;
    
    private String sourceChunkIdsJson;
    
    private Integer sortOrder;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
