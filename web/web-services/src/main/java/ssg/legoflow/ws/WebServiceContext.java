package ssg.legoflow.ws;

import ssg.legoflow.http.core.HttpContext;
public interface WebServiceContext extends HttpContext {

    WebServiceDescriptor getServiceDescriptor();

    String getPathParameter(String name);

    String getQueryParameter(String name);
}
