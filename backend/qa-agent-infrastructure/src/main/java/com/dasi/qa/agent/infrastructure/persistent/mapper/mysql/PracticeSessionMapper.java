package com.dasi.qa.agent.infrastructure.persistent.mapper.mysql;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dasi.qa.agent.infrastructure.persistent.entity.PracticeSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PracticeSessionMapper extends BaseMapper<PracticeSession> {
}
