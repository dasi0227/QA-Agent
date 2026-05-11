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
@TableName("qa_set_document_ref")
public class QaSetDocumentRef {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String qaSetId;

    private String documentId;

    private LocalDateTime createdAt;
}
