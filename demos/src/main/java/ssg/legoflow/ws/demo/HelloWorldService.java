package ssg.legoflow.ws.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.ws.WebService;
import ssg.legoflow.ws.WebServiceContext;
import ssg.legoflow.ws.WebServiceDescriptor;
import java.util.Set;
public class HelloWorldService implements WebService {

    private final WebServiceDescriptor descriptor = new WebServiceDescriptor("/hello", Set.of(HttpMethod.GET));

    @Override
    public WebServiceDescriptor getDescriptor() { return descriptor; }

    @Override
    public HttpResponse handle(WebServiceContext ctx, HttpRequest request) {
        return HttpResponse.of(HttpStatus.OK, "Hello, World!");
    }
}
