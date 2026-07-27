package ssg.legoflow.ws.content;

import java.util.*;
import java.util.regex.Pattern;

public class JsonCodec {

    private static final Pattern KEY_VALUE = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");

    public String encode(Map<String, String> data) {
        var sb = new StringBuilder("{");
        var entries = new ArrayList<>(data.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(entries.get(i).getKey())).append("\":");
            sb.append("\"").append(escapeJson(entries.get(i).getValue())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    public Map<String, String> decode(String json) {
        var result = new LinkedHashMap<String, String>();
        var matcher = KEY_VALUE.matcher(json);
        while (matcher.find()) {
            result.put(matcher.group(1), matcher.group(2));
        }
        return result;
    }

    public String encodeList(List<Map<String, String>> items) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(encode(items.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
