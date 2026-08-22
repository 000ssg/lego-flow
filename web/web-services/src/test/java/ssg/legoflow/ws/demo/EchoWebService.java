package ssg.legoflow.ws.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.ws.WebService;
import ssg.legoflow.ws.WebServiceContext;
import ssg.legoflow.ws.WebServiceDescriptor;
import java.nio.ByteBuffer;
import java.util.Set;
public class EchoWebService implements WebService {

    private final WebServiceDescriptor descriptor = new WebServiceDescriptor(
            "/echo", Set.of(HttpMethod.POST));

    @Override
    public WebServiceDescriptor getDescriptor() { return descriptor; }

    @Override
    public HttpResponse handle(WebServiceContext ctx, HttpRequest request) {
        var response = HttpResponse.of(HttpStatus.OK);
        response.setBody(request.getBody() != null ? request.getBody() : ByteBuffer.allocate(0));
        var contentType = request.getHeaders().get(HttpHeaders.CONTENT_TYPE);
        if (contentType != null) {
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, contentType);
        }
        return response;
    }
}
