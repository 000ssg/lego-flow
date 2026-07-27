package ssg.legoflow.http.client;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.http.config.ClientConfig;
import ssg.legoflow.http.core.*;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceDescriptor;

public class HttpClient extends AbstractService<HttpResponse, HttpRequest> {

    private final ClientConfig config;

    public HttpClient(ClientConfig config) {
        super(HttpResponse.class, HttpRequest.class,
                new ServiceDescriptor("http-client", "HTTP Client"));
        this.config = config;
    }

    public ClientConfig getConfig() { return config; }

    @SuppressWarnings("unchecked")
    @Override
    protected HttpRequest[] convertToOutput(Context ctx, HttpResponse... input) {
        return new HttpRequest[0];
    }

    @SuppressWarnings("unchecked")
    @Override
    protected HttpResponse[] convertToInput(Context ctx, HttpRequest... output) {
        return new HttpResponse[0];
    }

    public HttpResponse send(HttpRequest request) {
        return HttpResponse.of(HttpStatus.OK);
    }
}
