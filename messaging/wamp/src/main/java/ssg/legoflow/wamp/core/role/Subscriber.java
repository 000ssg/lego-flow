package ssg.legoflow.wamp.core.role;

import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.transport.WampTransport;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * WAMP Subscriber role — subscribes to topics and receives events.
 *
 * @since 0.1.0
 */
public class Subscriber {

    private final WampTransport transport;
    private final AtomicLong requestIdCounter = new AtomicLong(1);
    private final Map<Long, String> pendingSubscriptions = new ConcurrentHashMap<>();
    private final Map<Long, String> activeSubscriptions = new ConcurrentHashMap<>();
    private volatile Consumer<WampMessage.Publish> eventHandler;

    /**
     * Creates a new Subscriber that communicates via the given transport.
     *
     * @param transport the transport to use
     */
    public Subscriber(WampTransport transport) {
        this.transport = transport;
    }

    /**
     * Subscribes to a topic. Returns the request ID for the pending subscription.
     *
     * @param topic the topic URI to subscribe to
     * @return the request ID
     */
    public long subscribe(String topic) {
        long requestId = requestIdCounter.getAndIncrement();
        pendingSubscriptions.put(requestId, topic);
        transport.send(new WampMessage.Subscribe(requestId, Map.of(), topic));
        return requestId;
    }

    /**
     * Unsubscribes from a previously established subscription.
     *
     * @param subscriptionId the subscription identifier to cancel
     */
    public void unsubscribe(long subscriptionId) {
        long requestId = requestIdCounter.getAndIncrement();
        activeSubscriptions.remove(subscriptionId);
        transport.send(new WampMessage.Unsubscribe(requestId, subscriptionId));
    }

    /**
     * Registers a handler for incoming events.
     *
     * @param handler the event handler
     */
    public void onEvent(Consumer<WampMessage.Publish> handler) {
        this.eventHandler = handler;
    }

    /**
     * Handles a Subscribed confirmation from the router.
     *
     * @param subscribed the subscribed confirmation message
     */
    public void handleSubscribed(WampMessage.Subscribed subscribed) {
        var topic = pendingSubscriptions.remove(subscribed.requestId());
        if (topic != null) {
            activeSubscriptions.put(subscribed.subscriptionId(), topic);
        }
    }

    /**
     * Handles an incoming event by delegating to the registered handler.
     *
     * @param event the publish/event message
     */
    public void handleEvent(WampMessage.Publish event) {
        var handler = this.eventHandler;
        if (handler != null) {
            handler.accept(event);
        }
    }

    /**
     * Handles an incoming EVENT message (Advanced Profile) by converting to a Publish
     * and delegating to the registered handler.
     *
     * @param event the Event message from the router
     * @since 0.1.0
     */
    public void handleEvent(WampMessage.Event event) {
        var handler = this.eventHandler;
        if (handler != null) {
            handler.accept(new WampMessage.Publish(0, event.details(), "", event.args()));
        }
    }

    /**
     * Handles an incoming WAMP message that may be either a Publish or Event.
     *
     * @param msg the received message
     * @since 0.1.0
     */
    public void handleEventMessage(WampMessage msg) {
        switch (msg) {
            case WampMessage.Publish p -> handleEvent(p);
            case WampMessage.Event e -> handleEvent(e);
            default -> { /* ignore */ }
        }
    }

    /**
     * Returns the active subscriptions.
     *
     * @return map of subscription ID to topic URI
     */
    public Map<Long, String> getActiveSubscriptions() {
        return Map.copyOf(activeSubscriptions);
    }
}
