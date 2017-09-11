package com.sm.finance.charge.serializer.json;

import com.google.common.collect.Maps;

import com.fasterxml.jackson.databind.JavaType;
import com.sm.finance.charge.common.JsonUtil;
import com.sm.finance.charge.serializer.api.DataStructure;
import com.sm.finance.charge.serializer.api.Serializer;

import java.util.HashMap;
import java.util.Map;

/**
 * @author shifeng.luo
 * @version created on 2017/9/11 下午3:35
 */
public class JsonSerializer implements Serializer {
    private final String typeKey = "type";
    private final String dataKey = "data";

    private final DataStructure dataStructure;

    public JsonSerializer(DataStructure dataStructure) {
        this.dataStructure = dataStructure;
    }

    @Override
    public byte[] serialize(Object obj) {
        byte type = dataStructure.getStructType(obj.getClass());
        Map<String, Object> map = Maps.newHashMapWithExpectedSize(2);
        map.put(typeKey, type);
        map.put(dataKey, obj);

        String json = JsonUtil.toJson(map);
        if (json == null) {
            throw new RuntimeException("序列化数据失败");
        }
        return json.getBytes();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes) {
        String jsonStr = new String(bytes);
        JavaType mapType = JsonUtil.createMapType(HashMap.class, String.class, Object.class);

        Map<String, Object> map = JsonUtil.fromJson(jsonStr, mapType);
        if (map == null) {
            throw new RuntimeException("反序列化数据失败");
        }

        Integer type = (Integer) map.get(typeKey);
        Class<?> dataType = this.dataStructure.getStruct(type.byteValue());
        String dataStr = JsonUtil.toJson(map.get(dataKey));

        return (T) JsonUtil.fromJson(dataStr, dataType);
    }
}