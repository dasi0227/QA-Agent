package com.dasi.qa.agent.infrastructure.util;

import com.dasi.qa.agent.domain.util.IIdUtil;
import com.github.f4b6a3.tsid.TsidCreator;
import org.springframework.stereotype.Service;

@Service
public class IdUtil implements IIdUtil {

    @Override
    public String nextId() {
        return TsidCreator.getTsid().toString();
    }

}
