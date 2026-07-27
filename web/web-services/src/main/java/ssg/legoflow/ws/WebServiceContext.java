package ssg.legoflow.ws;

import ssg.legoflow.http.core.HttpContext;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;

public interface WebServiceContext extends HttpContext {

    WebServiceDescriptor getServiceDescriptor();

    String getPathParameter(String name);

    String getQueryParameter(String name);
}
