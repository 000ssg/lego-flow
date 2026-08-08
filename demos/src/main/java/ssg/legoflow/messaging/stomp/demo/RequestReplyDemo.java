package ssg.legoflow.messaging.stomp.demo;

import ssg.legoflow.messaging.stomp.core.StompBroker;
import ssg.legoflow.messaging.stomp.core.StompClient;
import ssg.legoflow.messaging.stomp.core.StompFrame;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Demonstrates request-reply messaging pattern over STOMP.
 *
 * <p>A client sends a request to a "request" destination and subscribes to a
 * "reply" destination. A service subscribes to the "request" destination,
 * processes requests, and sends replies to the "reply" destination.
 *
 * @since 0.1.0
 */
public class RequestReplyDemo {

    private final StompBroker broker;
    private final StompClient requester;
    private final StompClient responder;

    /**
     * Creates and wires the demo components.
     */
    public RequestReplyDemo() {
        broker = new StompBroker();

        var reqPair = InMemoryStompTransport.createPair();
        var resPair = InMemoryStompTransport.createPair();

        broker.accept(reqPair[1]);
        broker.accept(resPair[1]);

        requester = new StompClient(reqPair[0]);
        responder = new StompClient(resPair[0]);
    }

    /**
     * Runs the request-reply demo.
     *
     * @param requestDest the request destination
     * @param replyDest   the reply destination
     * @param request     the request message
     * @return the reply message body
     * @throws Exception if something fails
     */
    public String run(String requestDest, String replyDest, String request) throws Exception {
        requester.connect("localhost");
        responder.connect("localhost");

        // Set up the responder: listen for requests, send replies
        responder.subscribe(requestDest, msg -> {
            String body = msg.bodyAsText();
            String reply = "Reply to: " + body;
            responder.send(replyDest, reply, "text/plain");
        });

        // Set up the requester: listen for replies
        var replyFuture = new CompletableFuture<String>();
        requester.subscribe(replyDest, msg -> {
            replyFuture.complete(msg.bodyAsText());
        });

        Thread.sleep(50);

        // Send request
        requester.send(requestDest, request, "text/plain");

        // Wait for reply
        return replyFuture.get(5, TimeUnit.SECONDS);
    }

    /**
     * Closes all resources.
     */
    public void close() {
        requester.close();
        responder.close();
        broker.close();
    }
}
