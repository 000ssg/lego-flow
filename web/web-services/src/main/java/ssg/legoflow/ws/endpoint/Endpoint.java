package ssg.legoflow.ws.endpoint;

import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequestHandler;

public record Endpoint(String path, HttpMethod method, HttpRequestHandler handler) {}
