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
@TableName("user_memory")
public class UserMemory {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String userId;
    private String memoryType;
    private String targetType;
    private String targetKey;
    private String title;
    private String summary;
    private String detail;
    private Integer confidence;
    private Integer supportCount;
    private String status;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime hiddenAt;
    private String latestSessionId;
    private String latestQaSetId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
