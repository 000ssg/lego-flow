package ssg.legoflow.ws;

import ssg.legoflow.http.core.*;

public interface WebService {

    WebServiceDescriptor getDescriptor();

    HttpResponse handle(WebServiceContext ctx, HttpRequest request);
}
