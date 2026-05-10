package com.dasi.qa.agent.domain.util;

import java.util.List;

public interface IJsonUtil {

    String toJsonString(Object obj);

    String extractJsonArray(String json);

    String extractJsonObject(String json);

    <T> List<T> parseJsonArray(String rawJson, Class<T> clazz);

    <T> T parseJsonObject(String rawJson, Class<T> clazz);

}
