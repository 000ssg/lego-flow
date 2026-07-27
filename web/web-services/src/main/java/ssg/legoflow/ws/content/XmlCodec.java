package ssg.legoflow.ws.content;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class XmlCodec {

    private static final Pattern ELEMENT = Pattern.compile("<(\\w+)>([^<]*)</\\1>");

    public String encode(String rootElement, Map<String, String> data) {
        var sb = new StringBuilder("<").append(rootElement).append(">");
        for (var entry : data.entrySet()) {
            sb.append("<").append(entry.getKey()).append(">");
            sb.append(escapeXml(entry.getValue()));
            sb.append("</").append(entry.getKey()).append(">");
        }
        sb.append("</").append(rootElement).append(">");
        return sb.toString();
    }

    public Map<String, String> decode(String xml) {
        var result = new LinkedHashMap<String, String>();
        var matcher = ELEMENT.matcher(xml);
        while (matcher.find()) {
            result.put(matcher.group(1), matcher.group(2));
        }
        return result;
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
