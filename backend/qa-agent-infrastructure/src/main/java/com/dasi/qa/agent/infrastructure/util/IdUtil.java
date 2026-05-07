package com.dasi.qa.agent.infrastructure.util;

import com.dasi.qa.agent.domain.util.IIdUtil;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class IdUtil implements IIdUtil {

    public String taskId(){
        return uniqueId("task");
    }

    public String userId(){
        return uniqueId("user");
    }

    public String uniqueId(String prefix){
        return prefix + "-" + UUID.randomUUID();
    }

}
