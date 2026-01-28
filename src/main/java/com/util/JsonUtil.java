package com.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.ObjectUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class JsonUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public JsonUtil() {
    }

    public static <T> T parse(String json, Class<T> classOfT) {
        T t = null;

        try {
            t = objectMapper.readValue(json, new TypeReference<T>() {
            });
        } catch (IOException var4) {
            throw new RuntimeException(var4.getMessage(), var4);
        }

        return t;
    }

    public static <T> T parseObject(String json, Class<T> classOfT) {
        if (json == null) {
            return null;
        } else {
            T t = null;

            try {
                t = objectMapper.readValue(json, classOfT);
            } catch (IOException var4) {
                throw new RuntimeException(var4.getMessage(), var4);
            }

            return t;
        }
    }

    public static <T> List<T> parseList(String json, TypeReference<List<T>> classOfT) {
        if (ObjectUtils.isEmpty(json)) {
            return Collections.emptyList();
        } else {
            List<T> t = null;

            try {
                t = (List)objectMapper.readValue(json, classOfT);
            } catch (IOException var4) {
                throw new RuntimeException(var4.getMessage(), var4);
            }

            return t;
        }
    }

    public static Object toJSONString(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException var2) {
            throw new RuntimeException(var2.getMessage(), var2);
        }
    }

    static {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
