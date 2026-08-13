package ssg.legoflow.wamp.core;

import ssg.legoflow.wamp.core.router.WampRouter;
import ssg.legoflow.wamp.core.transport.InMemoryTransport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetaProcedureTest {

    @Test
    void testRegisterCustomMetaProcedure() {
        var router = new WampRouter();
        var pair = InMemoryTransport.createPair();

        router.registerMetaProcedure("com.test.hello", (call, transport) ->
                List.of("Hello, " + (call.args() != null && !call.args().isEmpty()
                        ? call.args().getFirst() : "World")));

        router.route(new WampMessage.Call(1L, Map.of(), "com.test.hello", List.of("Alice")), pair[0]);

        var response = pair[1].receive();
        assertThat(response).isInstanceOf(WampMessage.Result.class);
        assertThat(((WampMessage.Result) response).args()).containsExactly("Hello, Alice");
    }

    @Test
    void testCustomMetaProcedureWithEmptyArgs() {
        var router = new WampRouter();
        var pair = InMemoryTransport.createPair();

        router.registerMetaProcedure("com.test.echo", (call, transport) ->
                call.args() != null ? call.args() : List.of());

        router.route(new WampMessage.Call(1L, Map.of(), "com.test.echo", List.of()), pair[0]);

        var response = pair[1].receive();
        assertThat(response).isInstanceOf(WampMessage.Result.class);
        assertThat(((WampMessage.Result) response).args()).isEmpty();
    }

    @Test
    void testUnregisterMetaProcedure() {
        var router = new WampRouter();
        assertThat(router.unregisterMetaProcedure("com.test.nonexistent")).isFalse();

        router.registerMetaProcedure("com.test.tmp", (call, transport) -> List.of(42));
        assertThat(router.unregisterMetaProcedure("com.test.tmp")).isTrue();
        assertThat(router.unregisterMetaProcedure("com.test.tmp")).isFalse();
    }

    @Test
    void testBuiltInMetaProceduresStillWork() {
        var router = new WampRouter();
        var pair = InMemoryTransport.createPair();

        router.route(new WampMessage.Call(1L, Map.of(), "wamp.session.count", List.of()), pair[0]);

        var response = pair[1].receive();
        assertThat(response).isInstanceOf(WampMessage.Result.class);
        assertThat(((WampMessage.Result) response).args()).containsExactly(0);
    }

    @Test
    void testCustomMetaProcedureDoesNotOverrideBuiltIn() {
        var router = new WampRouter();
        var pair = InMemoryTransport.createPair();

        // Register a custom handler for a built-in procedure
        router.registerMetaProcedure("wamp.session.count", (call, transport) -> List.of(999));

        // Built-in handlers are checked first (switch expression), so custom handler
        // for built-in names will not be called — the switch catches it
        router.route(new WampMessage.Call(1L, Map.of(), "wamp.session.count", List.of()), pair[0]);

        var response = pair[1].receive();
        assertThat(response).isInstanceOf(WampMessage.Result.class);
        // Built-in handler returns 0 (no sessions), not 999
        assertThat(((WampMessage.Result) response).args()).containsExactly(0);
    }
}
