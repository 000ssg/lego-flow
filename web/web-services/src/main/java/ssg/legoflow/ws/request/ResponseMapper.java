package ssg.legoflow.ws.request;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.http.header.MediaType;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ResponseMapper {

    public HttpResponse json(HttpStatus status, String jsonBody) {
        var response = HttpResponse.of(status);
        response.setBody(ByteBuffer.wrap(jsonBody.getBytes(StandardCharsets.UTF_8)));
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
        response.getHeaders().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(jsonBody.length()));
        return response;
    }

    public HttpResponse text(HttpStatus status, String textBody) {
        var response = HttpResponse.of(status);
        response.setBody(ByteBuffer.wrap(textBody.getBytes(StandardCharsets.UTF_8)));
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN.toString());
        response.getHeaders().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(textBody.length()));
        return response;
    }

    public HttpResponse xml(HttpStatus status, String xmlBody) {
        var response = HttpResponse.of(status);
        response.setBody(ByteBuffer.wrap(xmlBody.getBytes(StandardCharsets.UTF_8)));
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_XML.toString());
        response.getHeaders().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(xmlBody.length()));
        return response;
    }

    public HttpResponse noContent() {
        return HttpResponse.of(HttpStatus.NO_CONTENT);
    }

    public HttpResponse notFound(String message) {
        return json(HttpStatus.NOT_FOUND, "{\"error\":\"" + message + "\"}");
    }

    public HttpResponse badRequest(String message) {
        return json(HttpStatus.BAD_REQUEST, "{\"error\":\"" + message + "\"}");
    }
}
