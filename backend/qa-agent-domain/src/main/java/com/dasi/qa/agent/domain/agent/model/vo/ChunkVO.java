package com.dasi.qa.agent.domain.agent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资料切片 VO，供 Practice 链路内部使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkVO {

    private String chunkId;
    private String documentId;
    private String titlePath;
    private String summary;
    private String content;
}
