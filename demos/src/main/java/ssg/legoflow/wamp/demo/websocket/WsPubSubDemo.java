package ssg.legoflow.wamp.demo.websocket;

import ssg.legoflow.http.websocket.WebSocketSession;
import ssg.legoflow.wamp.adapter.websocket.WebSocketWampTransport;
import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.WampSerializer;
import ssg.legoflow.wamp.core.role.Publisher;
import ssg.legoflow.wamp.core.role.Subscriber;
import ssg.legoflow.wamp.core.router.Broker;
import java.util.ArrayList;
import java.util.List;
/**
 * Pub/Sub over WebSocket transport demo.
 * Demonstrates multiple subscribers receiving events from a publisher,
 * all communicating through {@link WebSocketWampTransport} with a Broker.
 *
 * @since 0.1.0
 */
public class WsPubSubDemo {

    /**
     * Runs the WebSocket pub/sub demo with two subscribers.
     *
     * @return a list containing two sublists: events received by subscriber 1 and subscriber 2
     */
    public List<List<Object>> run() {
        var serializer = new WampSerializer();
        var broker = new Broker();

        // Create transports
        var sub1Client = new WebSocketSession("sub1-client");
        var sub1Router = new WebSocketSession("sub1-router");
        var sub1Transport = new WebSocketWampTransport(sub1Client, serializer);
        var sub1RouterTransport = new WebSocketWampTransport(sub1Router, serializer);
        wireTransports(sub1Transport, sub1RouterTransport);

        var sub2Client = new WebSocketSession("sub2-client");
        var sub2Router = new WebSocketSession("sub2-router");
        var sub2Transport = new WebSocketWampTransport(sub2Client, serializer);
        var sub2RouterTransport = new WebSocketWampTransport(sub2Router, serializer);
        wireTransports(sub2Transport, sub2RouterTransport);

        var pubClient = new WebSocketSession("pub-client");
        var pubRouter = new WebSocketSession("pub-router");
        var pubTransport = new WebSocketWampTransport(pubClient, serializer);
        var pubRouterTransport = new WebSocketWampTransport(pubRouter, serializer);
        wireTransports(pubTransport, pubRouterTransport);

        var sub1 = new Subscriber(sub1Transport);
        var sub2 = new Subscriber(sub2Transport);
        var pub = new Publisher(pubTransport);

        var received1 = new ArrayList<Object>();
        var received2 = new ArrayList<Object>();
        sub1.onEvent(e -> received1.addAll(e.args()));
        sub2.onEvent(e -> received2.addAll(e.args()));

        // Subscribe both
        sub1.subscribe("events.ws");
        var subMsg1 = (WampMessage.Subscribe) sub1RouterTransport.receive();
        sub1RouterTransport.send(broker.handleSubscribe(subMsg1, sub1RouterTransport));
        sub1.handleSubscribed((WampMessage.Subscribed) sub1Transport.receive());

        sub2.subscribe("events.ws");
        var subMsg2 = (WampMessage.Subscribe) sub2RouterTransport.receive();
        sub2RouterTransport.send(broker.handleSubscribe(subMsg2, sub2RouterTransport));
        sub2.handleSubscribed((WampMessage.Subscribed) sub2Transport.receive());

        // Publish
        pub.publish("events.ws", List.of("ws-hello", 99));
        var pubMsg = (WampMessage.Publish) pubRouterTransport.receive();
        broker.handlePublish(pubMsg, pubRouterTransport);

        // Both subscribers receive
        sub1.handleEventMessage(sub1Transport.receive());
        sub2.handleEventMessage(sub2Transport.receive());

        return List.of(received1, received2);
    }

    private void wireTransports(WebSocketWampTransport a, WebSocketWampTransport b) {
        a.onFrame(frame -> b.injectFrame(frame));
        b.onFrame(frame -> a.injectFrame(frame));
    }
}
