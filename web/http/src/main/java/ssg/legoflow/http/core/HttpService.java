package ssg.legoflow.http.core;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceDescriptor;

public class HttpService extends AbstractService<HttpRequest, HttpResponse> {

    private HttpRequestHandler requestHandler;

    public HttpService() {
        super(HttpRequest.class, HttpResponse.class, new ServiceDescriptor("http", "HTTP service"));
    }

    public void setRequestHandler(HttpRequestHandler handler) {
        this.requestHandler = handler;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected HttpResponse[] convertToOutput(Context ctx, HttpRequest... input) {
        if (requestHandler == null) {
            throw new IllegalStateException("No request handler configured");
        }
        HttpContext httpCtx = (ctx instanceof HttpContext hc) ? hc : null;
        HttpResponse[] responses = new HttpResponse[input.length];
        for (int i = 0; i < input.length; i++) {
            responses[i] = requestHandler.handle(httpCtx, input[i]);
        }
        return responses;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected HttpRequest[] convertToInput(Context ctx, HttpResponse... output) {
        HttpRequest[] requests = new HttpRequest[output.length];
        for (int i = 0; i < output.length; i++) {
            requests[i] = HttpRequest.of(HttpMethod.GET, "/");
        }
        return requests;
    }
}
