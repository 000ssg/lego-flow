package ssg.legoflow.http.core;

import ssg.legoflow.service.ServiceContext;

public interface HttpContext extends ServiceContext {

    HttpRequest getRequest();

    HttpResponse getResponse();

    void setResponse(HttpResponse response);
}
