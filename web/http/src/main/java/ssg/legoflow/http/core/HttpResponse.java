package ssg.legoflow.http.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class HttpResponse extends HttpMessage {

    private final HttpStatus status;

    public HttpResponse(HttpStatus status, HttpVersion version, HttpHeaders headers) {
        super(version, headers);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static HttpResponse of(HttpStatus status) {
        return new HttpResponse(status, HttpVersion.HTTP_1_1, new HttpHeaders());
    }

    public static HttpResponse of(HttpStatus status, String body) {
        HttpResponse response = new HttpResponse(status, HttpVersion.HTTP_1_1, new HttpHeaders());
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        response.setBody(ByteBuffer.wrap(bodyBytes));
        response.getHeaders().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(bodyBytes.length));
        return response;
    }
}
