package com.campuscare;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight JSON and Query String utilities with zero external dependencies.
 */
public class JsonUtil {

    private static final Pattern PAIR_PATTERN = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(?:\"([^\"]*)\"|([0-9.-]+|true|false|null))");

    public static Map<String, String> parseFlatJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.isBlank()) return map;
        
        Matcher matcher = PAIR_PATTERN.matcher(json);
        while (matcher.find()) {
            String key = matcher.group(1);
            String strVal = matcher.group(2);
            String rawVal = matcher.group(3);
            if (strVal != null) {
                map.put(key, unescape(strVal));
            } else if (rawVal != null) {
                map.put(key, rawVal);
            }
        }
        return map;
    }

    public static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isBlank()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                map.put(key, val);
            }
        }
        return map;
    }

    public static String quote(String value) {
        if (value == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32 || c >= 127) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int len = s.length();
        while (i < len) {
            char c = s.charAt(i++);
            if (c == '\\' && i < len) {
                char next = s.charAt(i++);
                switch (next) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 <= len) {
                            String hex = s.substring(i, i + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        }
                    }
                    default -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
