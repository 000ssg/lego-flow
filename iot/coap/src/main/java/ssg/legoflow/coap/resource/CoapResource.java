package ssg.legoflow.coap.resource;

import ssg.legoflow.coap.protocol.CoapCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Represents a CoAP resource that can handle requests.
 *
 * <p>Resources are organized in a tree structure and support GET, POST, PUT,
 * and DELETE operations. Observable resources can trigger notifications to
 * registered observers.
 *
 * @since 1.0.0
 */
public class CoapResource {

    private static final Logger LOG = LoggerFactory.getLogger(CoapResource.class);

    private final String name;
    private final String path;
    private final boolean observable;
    private final ResourceAttributes attributes;
    private final Map<String, CoapResource> children = new ConcurrentHashMap<>();
    private Consumer<String> observeNotifier;

    /**
     * Creates a new CoAP resource.
     *
     * @param name       the resource name (last path segment)
     * @param path       the full resource path (e.g. "/sensors/temperature")
     * @param observable whether this resource supports observe
     * @since 1.0.0
     */
    public CoapResource(String name, String path, boolean observable) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.path = Objects.requireNonNull(path, "path must not be null");
        this.observable = observable;
        this.attributes = new ResourceAttributes().observable(observable);
    }

    /**
     * Creates a non-observable resource.
     *
     * @param name the resource name
     * @param path the full resource path
     * @since 1.0.0
     */
    public CoapResource(String name, String path) {
        this(name, path, false);
    }

    /**
     * Returns the resource name.
     *
     * @return the name
     * @since 1.0.0
     */
    public String name() {
        return name;
    }

    /**
     * Returns the full resource path.
     *
     * @return the path
     * @since 1.0.0
     */
    public String path() {
        return path;
    }

    /**
     * Returns whether this resource is observable.
     *
     * @return {@code true} if observable
     * @since 1.0.0
     */
    public boolean isObservable() {
        return observable;
    }

    /**
     * Returns the resource attributes for discovery.
     *
     * @return the resource attributes
     * @since 1.0.0
     */
    public ResourceAttributes getAttributes() {
        return attributes;
    }

    /**
     * Handles a GET request. Default returns 4.05 Method Not Allowed.
     *
     * @param exchange the exchange context
     * @since 1.0.0
     */
    public void handleGet(CoapExchange exchange) {
        exchange.respond(CoapCode.METHOD_NOT_ALLOWED);
    }

    /**
     * Handles a POST request. Default returns 4.05 Method Not Allowed.
     *
     * @param exchange the exchange context
     * @since 1.0.0
     */
    public void handlePost(CoapExchange exchange) {
        exchange.respond(CoapCode.METHOD_NOT_ALLOWED);
    }

    /**
     * Handles a PUT request. Default returns 4.05 Method Not Allowed.
     *
     * @param exchange the exchange context
     * @since 1.0.0
     */
    public void handlePut(CoapExchange exchange) {
        exchange.respond(CoapCode.METHOD_NOT_ALLOWED);
    }

    /**
     * Handles a DELETE request. Default returns 4.05 Method Not Allowed.
     *
     * @param exchange the exchange context
     * @since 1.0.0
     */
    public void handleDelete(CoapExchange exchange) {
        exchange.respond(CoapCode.METHOD_NOT_ALLOWED);
    }

    /**
     * Adds a child resource.
     *
     * @param child the child resource
     * @throws NullPointerException if {@code child} is {@code null}
     * @since 1.0.0
     */
    public void addChild(CoapResource child) {
        Objects.requireNonNull(child, "child must not be null");
        children.put(child.name(), child);
    }

    /**
     * Returns the child resource with the given name, or {@code null} if absent.
     *
     * @param name the child resource name
     * @return the child resource, or {@code null}
     * @since 1.0.0
     */
    public CoapResource getChild(String name) {
        return children.get(name);
    }

    /**
     * Returns an unmodifiable map of child resources.
     *
     * @return the children map
     * @since 1.0.0
     */
    public Map<String, CoapResource> children() {
        return Collections.unmodifiableMap(children);
    }

    /**
     * Sets the callback for triggering observe notifications.
     *
     * @param notifier the notifier callback accepting the resource path
     * @since 1.0.0
     */
    public void setObserveNotifier(Consumer<String> notifier) {
        this.observeNotifier = notifier;
    }

    /**
     * Triggers observe notifications for this resource.
     *
     * <p>Only works if this resource is observable and a notifier has been set.
     *
     * @since 1.0.0
     */
    public void notifyObservers() {
        if (observable && observeNotifier != null) {
            observeNotifier.accept(path);
        }
    }

    /**
     * Returns the CoRE Link Format representation of this resource.
     *
     * @return the link-format string
     * @since 1.0.0
     */
    public String toCoreLinkFormat() {
        return "<" + path + ">" + attributes.toCoreLinkFormat();
    }

    @Override
    public String toString() {
        return "CoapResource{name='" + name + "', path='" + path + "', observable=" + observable + "}";
    }
}
