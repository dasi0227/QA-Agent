package com.dasi.qa.agent.infrastructure.persistent.mapper.mysql;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dasi.qa.agent.infrastructure.persistent.entity.SourceDocument;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SourceDocumentMapper extends BaseMapper<SourceDocument> {
}
