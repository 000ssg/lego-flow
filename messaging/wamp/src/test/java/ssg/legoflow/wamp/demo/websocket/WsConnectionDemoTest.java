package ssg.legoflow.wamp.demo.websocket;

import ssg.legoflow.http.websocket.WebSocketSession;
import ssg.legoflow.wamp.adapter.websocket.WebSocketWampTransport;
import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.WampSerializer;
import ssg.legoflow.wamp.core.WampSession;
import ssg.legoflow.wamp.core.realm.Realm;
import ssg.legoflow.wamp.core.realm.RealmManager;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WsConnectionDemoTest {

    @Test
    void testWebSocketHandshakeAndWelcome() {
        var serializer = new WampSerializer();
        var clientSession = new WebSocketSession("ws-client");
        var routerSession = new WebSocketSession("ws-router");
        var clientTransport = new WebSocketWampTransport(clientSession, serializer);
        var routerTransport = new WebSocketWampTransport(routerSession, serializer);
        clientTransport.onFrame(routerTransport::injectFrame);
        routerTransport.onFrame(clientTransport::injectFrame);

        var realm = new Realm("ws.test.realm");

        clientTransport.send(new WampMessage.Hello("ws.test.realm", Map.of("roles", Map.of())));

        var hello = (WampMessage.Hello) routerTransport.receive();
        assertThat(hello.realm()).isEqualTo("ws.test.realm");

        var welcome = realm.addSession(routerTransport);
        routerTransport.send(welcome);

        var welcomeMsg = (WampMessage.Welcome) clientTransport.receive();
        assertThat(welcomeMsg.sessionId()).isPositive();
        assertThat(realm.getSessionCount()).isEqualTo(1);
    }

    @Test
    void testWebSocketGracefulClose() {
        var serializer = new WampSerializer();
        var clientSession = new WebSocketSession("ws-client");
        var routerSession = new WebSocketSession("ws-router");
        var clientTransport = new WebSocketWampTransport(clientSession, serializer);
        var routerTransport = new WebSocketWampTransport(routerSession, serializer);
        clientTransport.onFrame(routerTransport::injectFrame);
        routerTransport.onFrame(clientTransport::injectFrame);

        var realm = new Realm("ws.close.realm");
        var welcome = realm.addSession(routerTransport);
        long sessionId = welcome.sessionId();

        var session = new WampSession();
        session.establish(sessionId, "ws.close.realm");
        assertThat(session.isEstablished()).isTrue();

        clientTransport.send(new WampMessage.Goodbye(Map.of(), "wamp.close.normal"));
        var goodbye = (WampMessage.Goodbye) routerTransport.receive();
        assertThat(goodbye.reason()).isEqualTo("wamp.close.normal");

        realm.removeSession(sessionId);
        routerTransport.send(new WampMessage.Goodbye(Map.of(), "wamp.close.normal"));

        var goodbyeResponse = (WampMessage.Goodbye) clientTransport.receive();
        session.close();

        assertThat(session.isEstablished()).isFalse();
        assertThat(realm.getSessionCount()).isZero();
    }

    @Test
    void testWebSocketRpcAfterHandshake() {
        var serializer = new WampSerializer();
        var pair = createWiredPair("rpc", serializer);
        var clientTransport = pair[0];
        var routerTransport = pair[1];

        var realm = new Realm("ws.rpc.realm");
        realm.addSession(routerTransport);

        var calleePair = createWiredPair("callee", serializer);
        var callee = new ssg.legoflow.wamp.core.role.Callee(calleePair[0]);
        callee.register("com.ws.echo", args -> args);

        var registerMsg = (WampMessage.Register) calleePair[1].receive();
        calleePair[1].send(realm.getDealer().handleRegister(registerMsg, calleePair[1]));
        callee.handleRegistered((WampMessage.Registered) calleePair[0].receive());

        var caller = new ssg.legoflow.wamp.core.role.Caller(clientTransport);
        var future = caller.call("com.ws.echo", java.util.List.of("ping"));

        var callMsg = (WampMessage.Call) routerTransport.receive();
        realm.getDealer().handleCall(callMsg, routerTransport);
        callee.handleInvocation((WampMessage.Invocation) calleePair[0].receive());
        realm.getDealer().handleYield((WampMessage.Yield) calleePair[1].receive());
        caller.handleResult((WampMessage.Result) clientTransport.receive());

        assertThat(future.join().args()).containsExactly("ping");
    }

    @Test
    void testWebSocketAbortOnUnknownRealm() {
        var serializer = new WampSerializer();
        var clientSession = new WebSocketSession("ws-client");
        var routerSession = new WebSocketSession("ws-router");
        var clientTransport = new WebSocketWampTransport(clientSession, serializer);
        var routerTransport = new WebSocketWampTransport(routerSession, serializer);
        clientTransport.onFrame(routerTransport::injectFrame);
        routerTransport.onFrame(clientTransport::injectFrame);

        var realmManager = new RealmManager();

        clientTransport.send(new WampMessage.Hello("nonexistent.realm", Map.of()));
        var hello = (WampMessage.Hello) routerTransport.receive();

        var realmOpt = realmManager.getRealm(hello.realm());
        assertThat(realmOpt).isEmpty();

        routerTransport.send(new WampMessage.Abort(Map.of(), "wamp.error.no_such_realm"));
        var abort = (WampMessage.Abort) clientTransport.receive();
        assertThat(abort.reason()).isEqualTo("wamp.error.no_such_realm");
    }

    private WebSocketWampTransport[] createWiredPair(String prefix, WampSerializer serializer) {
        var clientSession = new WebSocketSession(prefix + "-client");
        var routerSession = new WebSocketSession(prefix + "-router");
        var clientTransport = new WebSocketWampTransport(clientSession, serializer);
        var routerTransport = new WebSocketWampTransport(routerSession, serializer);
        clientTransport.onFrame(routerTransport::injectFrame);
        routerTransport.onFrame(clientTransport::injectFrame);
        return new WebSocketWampTransport[]{clientTransport, routerTransport};
    }
}
