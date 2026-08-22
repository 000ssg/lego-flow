package ssg.legoflow.ws;

import ssg.legoflow.http.server.HttpRouter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
public class WebServiceRegistry {

    private final ConcurrentHashMap<String, WebService> services = new ConcurrentHashMap<>();

    public void register(WebService service) {
        services.put(service.getDescriptor().path(), service);
    }

    public void unregister(String path) {
        services.remove(path);
    }

    public WebService getService(String path) {
        return services.get(path);
    }

    public List<WebService> getServices() {
        return List.copyOf(services.values());
    }

    public void installRoutes(HttpRouter router) {
        for (var entry : services.entrySet()) {
            var service = entry.getValue();
            var descriptor = service.getDescriptor();
            for (var method : descriptor.methods()) {
                router.route(descriptor.path(), method, (ctx, req) -> {
                    var wsCtx = new DefaultWebServiceContext(ctx, descriptor);
                    return service.handle(wsCtx, req);
                });
            }
        }
    }

    private static class DefaultWebServiceContext implements WebServiceContext {
        private final ssg.legoflow.http.core.HttpContext delegate;
        private final WebServiceDescriptor descriptor;

        DefaultWebServiceContext(ssg.legoflow.http.core.HttpContext delegate, WebServiceDescriptor descriptor) {
            this.delegate = delegate;
            this.descriptor = descriptor;
        }

        @Override public WebServiceDescriptor getServiceDescriptor() { return descriptor; }
        @Override public String getPathParameter(String name) { return null; }
        @Override public String getQueryParameter(String name) {
            var request = delegate.getRequest();
            if (request != null) {
                return request.getQueryParams().get(name);
            }
            return null;
        }
        @Override public ssg.legoflow.http.core.HttpRequest getRequest() { return delegate.getRequest(); }
        @Override public ssg.legoflow.http.core.HttpResponse getResponse() { return delegate.getResponse(); }
        @Override public void setResponse(ssg.legoflow.http.core.HttpResponse response) { delegate.setResponse(response); }
        @Override public org.slf4j.Logger getLogger() { return delegate.getLogger(); }
        @Override public ssg.legoflow.blocks.ProcessorStatistics getStatistics() { return delegate.getStatistics(); }
        @Override public void handleError(Throwable error) { delegate.handleError(error); }
        @Override public <T> T getAttribute(String key) { return delegate.getAttribute(key); }
        @Override public void setAttribute(String key, Object value) { delegate.setAttribute(key, value); }
        @Override public ssg.legoflow.service.scope.SiteScope getSiteScope() { return delegate.getSiteScope(); }
        @Override public ssg.legoflow.service.scope.ApplicationScope getApplicationScope() { return delegate.getApplicationScope(); }
        @Override public ssg.legoflow.service.scope.SessionScope getSessionScope() { return delegate.getSessionScope(); }
        @Override public ssg.legoflow.service.scope.RequestScope getRequestScope() { return delegate.getRequestScope(); }
        @Override public ssg.legoflow.service.user.ServiceUser getUser() { return delegate.getUser(); }
        @Override public boolean hasRole(ssg.legoflow.service.user.ServiceRole role) { return delegate.hasRole(role); }
        @Override public void checkPermission(String operation) { delegate.checkPermission(operation); }
    }
}
