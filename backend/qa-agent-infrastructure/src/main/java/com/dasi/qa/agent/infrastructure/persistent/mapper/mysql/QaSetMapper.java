package com.dasi.qa.agent.infrastructure.persistent.mapper.mysql;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dasi.qa.agent.infrastructure.persistent.po.QaSet;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QaSetMapper extends BaseMapper<QaSet> {
}
