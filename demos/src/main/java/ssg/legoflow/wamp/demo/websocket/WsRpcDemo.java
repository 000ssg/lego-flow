package ssg.legoflow.wamp.demo.websocket;

import ssg.legoflow.http.websocket.WebSocketFrame;
import ssg.legoflow.http.websocket.WebSocketSession;
import ssg.legoflow.wamp.adapter.websocket.WebSocketWampTransport;
import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.WampSerializer;
import ssg.legoflow.wamp.core.role.Callee;
import ssg.legoflow.wamp.core.role.Caller;
import ssg.legoflow.wamp.core.router.Dealer;

import java.util.List;
import java.util.Map;

/**
 * RPC over WebSocket transport demo.
 * Demonstrates Caller and Callee communicating through {@link WebSocketWampTransport}
 * with a Dealer routing calls between them.
 *
 * <p>Uses two pairs of WebSocket sessions wired back-to-back to simulate
 * network communication without an actual HTTP server.</p>
 *
 * @since 0.1.0
 */
public class WsRpcDemo {

    /**
     * Runs the WebSocket RPC demo.
     *
     * @return the result arguments from the "add" procedure call
     */
    public List<Object> run() {
        var serializer = new WampSerializer();

        // Create WebSocket session pairs for caller and callee
        var callerWs = createConnectedSessionPair("caller");
        var calleeWs = createConnectedSessionPair("callee");

        var callerTransport = new WebSocketWampTransport(callerWs[0], serializer);
        var callerRouterTransport = new WebSocketWampTransport(callerWs[1], serializer);
        var calleeTransport = new WebSocketWampTransport(calleeWs[0], serializer);
        var calleeRouterTransport = new WebSocketWampTransport(calleeWs[1], serializer);

        // Wire frame forwarding: when one side sends, the other receives
        wireTransports(callerTransport, callerRouterTransport);
        wireTransports(calleeTransport, calleeRouterTransport);

        var dealer = new Dealer();
        var caller = new Caller(callerTransport);
        var callee = new Callee(calleeTransport);

        // Register "add" procedure
        callee.register("com.example.add", args -> {
            int a = ((Number) args.get(0)).intValue();
            int b = ((Number) args.get(1)).intValue();
            return List.of(a + b);
        });

        // Router side: process register
        var registerMsg = (WampMessage.Register) callerRouterReceive(calleeRouterTransport);
        var registered = dealer.handleRegister(registerMsg, calleeRouterTransport);
        calleeRouterTransport.send(registered);
        callee.handleRegistered((WampMessage.Registered) calleeTransport.receive());

        // Caller calls "add(3, 5)"
        var future = caller.call("com.example.add", List.of(3, 5));

        // Router processes the call
        var callMsg = (WampMessage.Call) callerRouterReceive(callerRouterTransport);
        dealer.handleCall(callMsg, callerRouterTransport);

        // Callee handles invocation
        callee.handleInvocation((WampMessage.Invocation) calleeTransport.receive());

        // Router forwards yield back
        var yieldMsg = (WampMessage.Yield) callerRouterReceive(calleeRouterTransport);
        dealer.handleYield(yieldMsg);

        // Caller gets result
        caller.handleResult((WampMessage.Result) callerTransport.receive());

        return future.join().args();
    }

    /**
     * Creates a pair of WebSocket sessions wired together.
     * Frames sent via session[0] are delivered to session[1] and vice versa.
     */
    private WebSocketSession[] createConnectedSessionPair(String prefix) {
        var session1 = new WebSocketSession(prefix + "-client");
        var session2 = new WebSocketSession(prefix + "-router");
        return new WebSocketSession[]{session1, session2};
    }

    /**
     * Wires two transports so that frames sent by one are injected into the other.
     */
    private void wireTransports(WebSocketWampTransport a, WebSocketWampTransport b) {
        a.onFrame(frame -> b.injectFrame(frame));
        b.onFrame(frame -> a.injectFrame(frame));
    }

    private WampMessage callerRouterReceive(WebSocketWampTransport transport) {
        return transport.receive();
    }
}
