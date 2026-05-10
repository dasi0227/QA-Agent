package com.dasi.qa.agent.infrastructure.util;

import com.alibaba.fastjson2.JSON;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JsonUtil implements IJsonUtil {

    @Override
    public String toJsonString(Object obj) {
        return JSON.toJSONString(obj);
    }

    @Override
    public String extractJsonArray(String json) {
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return json.substring(start, end + 1);
        }
        return json;
    }

    @Override
    public String extractJsonObject(String json) {
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return json.substring(start, end + 1);
        }
        return json;
    }

    @Override
    public <T> List<T> parseJsonArray(String rawJson, Class<T> clazz) {
        return JSON.parseArray(extractJsonArray(rawJson), clazz);
    }

    @Override
    public <T> T parseJsonObject(String rawJson, Class<T> clazz) {
        return JSON.parseObject(extractJsonObject(rawJson), clazz);
    }
}
