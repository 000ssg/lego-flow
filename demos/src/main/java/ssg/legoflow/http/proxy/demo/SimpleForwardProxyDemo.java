package ssg.legoflow.http.proxy.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.proxy.forward.ForwardProxy;
import ssg.legoflow.http.proxy.forward.ForwardProxyConfig;
import ssg.legoflow.http.proxy.forward.ProxyAccessControl;
import java.util.Set;
/**
 * Basic forward proxy demo.
 *
 * <p>Demonstrates setting up a simple forward proxy with access control
 * and handling both plain HTTP and CONNECT requests.</p>
 *
 * @since 0.1.0
 */
public class SimpleForwardProxyDemo {

    private final ForwardProxy proxy;

    /**
     * Creates the demo with a pre-configured forward proxy.
     *
     * @since 0.1.0
     */
    public SimpleForwardProxyDemo() {
        var config = new ForwardProxyConfig();
        config.setProxyName("demo-forward-proxy");

        var accessControl = ProxyAccessControl.allowHosts(
                Set.of("example.com", "api.example.com", "localhost"));

        this.proxy = new ForwardProxy(config, accessControl) {
            @Override
            protected HttpResponse simulateUpstreamRequest(HttpRequest request, TargetAddress target) {
                String body = "Proxied response from " + target.host() + ":" + target.port()
                        + " path=" + target.path();
                return HttpResponse.of(HttpStatus.OK, body);
            }
        };
    }

    /**
     * Runs the demo by sending various requests through the proxy.
     *
     * @return the results summary
     * @since 0.1.0
     */
    public String run() {
        var sb = new StringBuilder();

        // Plain HTTP request to allowed host
        var req1 = HttpRequest.of(HttpMethod.GET, "http://example.com/index.html");
        var resp1 = proxy.handleRequest(req1);
        sb.append("GET http://example.com/index.html -> ").append(resp1.getStatus().code()).append("\n");

        // CONNECT request for HTTPS tunneling
        var req2 = HttpRequest.of(HttpMethod.CONNECT, "example.com:443");
        var resp2 = proxy.handleRequest(req2);
        sb.append("CONNECT example.com:443 -> ").append(resp2.getStatus().code()).append("\n");

        // Request to denied host
        var req3 = HttpRequest.of(HttpMethod.GET, "http://blocked.com/secret");
        var resp3 = proxy.handleRequest(req3);
        sb.append("GET http://blocked.com/secret -> ").append(resp3.getStatus().code()).append("\n");

        sb.append("Total requests: ").append(proxy.getRequestCount()).append("\n");
        sb.append("Total tunnels: ").append(proxy.getTunnelCount()).append("\n");

        return sb.toString();
    }

    /**
     * Returns the forward proxy instance.
     *
     * @return the proxy
     * @since 0.1.0
     */
    public ForwardProxy getProxy() {
        return proxy;
    }
}
