package org.json;

import java.util.HashMap;
import java.util.Map;

public class JSONObject {
    private final Map<String,Object> values = new HashMap<>();
    public JSONObject put(String key, Object value) { values.put(key, value); return this; }
    public String optString(String key, String fallback) {
        Object value = values.get(key); return value instanceof String ? (String) value : fallback;
    }
    public long optLong(String key, long fallback) {
        Object value = values.get(key); return value instanceof Number ? ((Number) value).longValue() : fallback;
    }
    public int optInt(String key, int fallback) {
        Object value = values.get(key); return value instanceof Number ? ((Number) value).intValue() : fallback;
    }
    public boolean optBoolean(String key, boolean fallback) {
        Object value = values.get(key); return value instanceof Boolean ? (Boolean) value : fallback;
    }
    public boolean has(String key) { return values.containsKey(key); }
}
