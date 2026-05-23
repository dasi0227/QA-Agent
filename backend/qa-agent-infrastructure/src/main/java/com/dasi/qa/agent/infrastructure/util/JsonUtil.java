package com.dasi.qa.agent.infrastructure.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import io.github.haibiiin.json.repair.JSONRepair;
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
        String json = extractJsonArray(rawJson);
        try {
            return JSON.parseArray(json, clazz);
        } catch (JSONException e) {
            try {
                String repaired = new JSONRepair().handle(json);
                return JSON.parseArray(repaired, clazz);
            } catch (Exception ignored) {
                throw e;
            }
        }
    }

    @Override
    public <T> T parseJsonObject(String rawJson, Class<T> clazz) {
        String json = extractJsonObject(rawJson);
        try {
            return JSON.parseObject(json, clazz);
        } catch (JSONException e) {
            try {
                String repaired = new JSONRepair().handle(json);
                return JSON.parseObject(repaired, clazz);
            } catch (Exception ignored) {
                throw e;
            }
        }
    }
}
