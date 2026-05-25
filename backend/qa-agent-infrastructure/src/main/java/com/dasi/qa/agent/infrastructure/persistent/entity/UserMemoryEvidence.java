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
@TableName("user_memory_evidence")
public class UserMemoryEvidence {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String memoryId;
    private String userId;
    private String sessionId;
    private String sessionItemId;
    private String qaSetId;
    private String qaItemId;
    private String moduleTag;
    private String questionSnapshot;
    private String result;
    private Integer score;
    private String sourceChunkIdsJson;
    private String evidenceSummary;
    private LocalDateTime createdAt;
}
