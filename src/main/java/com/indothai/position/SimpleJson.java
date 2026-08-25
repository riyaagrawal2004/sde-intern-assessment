package com.indothai.position;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal JSON helper for this project's simple, flat payloads.
 * Not a general-purpose JSON library on purpose -- the event payload
 * and the /position response are both flat objects (no nesting/arrays),
 * so a tiny hand-rolled parser/serializer keeps dependencies at zero.
 */
public class SimpleJson {

    /** Parses a flat JSON object like {"a":"1","b":2} into a String map (values kept as raw text). */
    public static Map<String, String> parseFlatObject(String json) {
        Map<String, String> result = new HashMap<>();
        if (json == null) return result;

        String trimmed = json.trim();
        if (trimmed.isEmpty() || !trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IllegalArgumentException("Not a JSON object");
        }
        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) return result;

        // Split on commas that are not inside a quoted string.
        int depth = 0;
        boolean inQuotes = false;
        StringBuilder token = new StringBuilder();
        java.util.List<String> pairs = new java.util.ArrayList<>();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"' && (i == 0 || body.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
            }
            if (c == ',' && !inQuotes) {
                pairs.add(token.toString());
                token.setLength(0);
            } else {
                token.append(c);
            }
        }
        if (token.length() > 0) pairs.add(token.toString());

        for (String pair : pairs) {
            int colonIdx = findColon(pair);
            if (colonIdx < 0) continue;
            String key = stripQuotes(pair.substring(0, colonIdx).trim());
            String value = pair.substring(colonIdx + 1).trim();
            value = stripQuotes(value);
            result.put(key, value);
        }
        return result;
    }

    private static int findColon(String pair) {
        boolean inQuotes = false;
        for (int i = 0; i < pair.length(); i++) {
            char c = pair.charAt(i);
            if (c == '"' && (i == 0 || pair.charAt(i - 1) != '\\')) inQuotes = !inQuotes;
            if (c == ':' && !inQuotes) return i;
        }
        return -1;
    }

    private static String stripQuotes(String s) {
        s = s.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** Serializes a String->Integer map into a flat JSON object string. */
    public static String toJsonObject(Map<String, Integer> data) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(entry.getKey())).append("\":").append(entry.getValue());
        }
        sb.append("}");
        return sb.toString();
    }

    public static String errorJson(String message) {
        return "{\"error\":\"" + escape(message) + "\"}";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}