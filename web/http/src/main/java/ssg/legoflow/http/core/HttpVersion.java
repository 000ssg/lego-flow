package ssg.legoflow.http.core;

public enum HttpVersion {
    HTTP_1_0("HTTP/1.0"),
    HTTP_1_1("HTTP/1.1"),
    HTTP_2("HTTP/2"),
    HTTP_3("HTTP/3");

    private final String value;

    HttpVersion(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static HttpVersion parse(String s) {
        return switch (s) {
            case "HTTP/1.0" -> HTTP_1_0;
            case "HTTP/1.1" -> HTTP_1_1;
            case "HTTP/2", "HTTP/2.0" -> HTTP_2;
            case "HTTP/3", "HTTP/3.0" -> HTTP_3;
            default -> throw new IllegalArgumentException("Unknown HTTP version: " + s);
        };
    }
}
