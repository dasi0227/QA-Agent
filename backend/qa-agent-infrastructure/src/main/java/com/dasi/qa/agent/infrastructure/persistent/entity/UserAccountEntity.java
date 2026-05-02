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
@TableName("user_account")
public class UserAccountEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String username;

    private String email;

    private String password;

    private String status;

    private String avatar;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
