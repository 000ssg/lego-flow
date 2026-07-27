package ssg.legoflow.http2.server;

import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http2.stream.Http2Stream;

@FunctionalInterface
public interface Http2ServerHandler {

    HttpResponse handle(Http2Stream stream, HttpRequest request);
}
