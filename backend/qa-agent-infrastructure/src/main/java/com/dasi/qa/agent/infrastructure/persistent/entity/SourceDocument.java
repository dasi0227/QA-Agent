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
@TableName("source_document")
public class SourceDocument {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    
    private String userId;
    
    private String fileName;
    
    private String fileType;
    
    private String filePath;
    
    private String rawContent;

    private Integer referenceCount;
    
    private Boolean deleted;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
