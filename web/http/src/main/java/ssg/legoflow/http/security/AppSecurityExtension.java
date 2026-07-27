package ssg.legoflow.http.security;

import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;

public interface AppSecurityExtension {

    String getName();

    HttpRequest processRequest(HttpRequest request);

    HttpResponse processResponse(HttpResponse response);
}
