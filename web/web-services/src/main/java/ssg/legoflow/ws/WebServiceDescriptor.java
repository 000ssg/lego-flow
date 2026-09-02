package ssg.legoflow.ws;

import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.header.MediaType;
import java.util.List;
import java.util.Set;
public record WebServiceDescriptor(
        String path,
        Set<HttpMethod> methods,
        List<MediaType> produces,
        List<MediaType> consumes
) {
    public WebServiceDescriptor(String path, Set<HttpMethod> methods) {
        this(path, methods, List.of(MediaType.APPLICATION_JSON), List.of(MediaType.APPLICATION_JSON));
    }

    public WebServiceDescriptor(String path) {
        this(path, Set.of(HttpMethod.GET));
    }
}
