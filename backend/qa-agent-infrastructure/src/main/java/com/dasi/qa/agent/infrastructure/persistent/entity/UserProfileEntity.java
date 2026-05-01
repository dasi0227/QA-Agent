package com.dasi.qa.agent.infrastructure.persistent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_profile")
public class UserProfileEntity {

    @TableId(value = "user_id", type = IdType.INPUT)
    private String userId;

    private String targetRole;

    private String targetDomain;

    private String targetCompany;

    private Boolean allowGeneralKnowledge;

    private Boolean allowWebSearch;

    private String answerStyle;

    private String feedbackStyle;

    private String age;

    private String grade;

    private String major;

    private String stage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
