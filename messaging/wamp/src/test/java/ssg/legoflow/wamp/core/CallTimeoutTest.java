package ssg.legoflow.wamp.core;

import ssg.legoflow.wamp.core.router.Dealer;
import ssg.legoflow.wamp.core.transport.InMemoryTransport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class CallTimeoutTest {

    @Test
    void testCallTimeoutFiresWhenCalleeDoesNotRespond() {
        var dealer = new Dealer();
        var executor = Executors.newSingleThreadScheduledExecutor();
        dealer.setTimeoutExecutor(executor);

        var callerPair = InMemoryTransport.createPair();
        var calleePair = InMemoryTransport.createPair();

        dealer.handleRegister(new WampMessage.Register(1L, Map.of(), "com.test.slow"), calleePair[0]);

        var call = new WampMessage.Call(2L, Map.of("timeout", 0.05), "com.test.slow", List.of("arg"));
        dealer.handleCall(call, callerPair[0], 10);

        // Callee receives invocation but does NOT respond
        var invocation = calleePair[1].receive();
        assertThat(invocation).isInstanceOf(WampMessage.Invocation.class);

        // After timeout, caller receives wamp.error.timeout
        var error = callerPair[1].receive();
        assertThat(error).isInstanceOf(WampMessage.Error.class);
        assertThat(((WampMessage.Error) error).error()).isEqualTo("wamp.error.timeout");

        // Callee receives INTERRUPT (killnowait)
        var interrupt = calleePair[1].receive();
        assertThat(interrupt).isInstanceOf(WampMessage.Interrupt.class);

        executor.shutdown();
    }

    @Test
    void testNoTimeoutWithoutExecutor() {
        var dealer = new Dealer();
        // No executor set — timeout should be ignored

        var callerPair = InMemoryTransport.createPair();
        var calleePair = InMemoryTransport.createPair();

        dealer.handleRegister(new WampMessage.Register(1L, Map.of(), "com.test.proc"), calleePair[0]);

        var call = new WampMessage.Call(2L, Map.of("timeout", 0.01), "com.test.proc", List.of());
        dealer.handleCall(call, callerPair[0], 10);

        // Callee receives invocation
        var invocation = (WampMessage.Invocation) calleePair[1].receive();
        assertThat(invocation).isNotNull();

        // No timeout error should arrive (no executor configured)
        var msg = callerPair[1].tryReceive();
        assertThat(msg).isNull();

        // Normal yield should still work
        dealer.handleYield(new WampMessage.Yield(invocation.requestId(), Map.of(), List.of("ok")));

        var result = callerPair[1].receive();
        assertThat(result).isInstanceOf(WampMessage.Result.class);
        assertThat(((WampMessage.Result) result).args()).containsExactly("ok");
    }

    @Test
    void testYieldCancelsTimeout() {
        var dealer = new Dealer();
        var executor = Executors.newSingleThreadScheduledExecutor();
        dealer.setTimeoutExecutor(executor);

        var callerPair = InMemoryTransport.createPair();
        var calleePair = InMemoryTransport.createPair();

        dealer.handleRegister(new WampMessage.Register(1L, Map.of(), "com.test.fast"), calleePair[0]);

        var call = new WampMessage.Call(2L, Map.of("timeout", 0.5), "com.test.fast", List.of());
        var invocation = dealer.handleCall(call, callerPair[0], 10);
        assertThat(invocation).isNotNull();

        // Consume invocation from callee side
        var receivedInvocation = (WampMessage.Invocation) calleePair[1].receive();

        // Callee responds immediately (before timeout)
        dealer.handleYield(new WampMessage.Yield(receivedInvocation.requestId(), Map.of(), List.of("result")));

        var result = callerPair[1].receive();
        assertThat(result).isInstanceOf(WampMessage.Result.class);
        assertThat(((WampMessage.Result) result).args()).containsExactly("result");

        // Verify no timeout error arrives (yield cancelled it)
        var timeoutMsg = callerPair[1].tryReceive();
        assertThat(timeoutMsg).isNull();

        executor.shutdown();
    }

    @Test
    void testCancelCancelsTimeout() {
        var dealer = new Dealer();
        var executor = Executors.newSingleThreadScheduledExecutor();
        dealer.setTimeoutExecutor(executor);

        var callerPair = InMemoryTransport.createPair();
        var calleePair = InMemoryTransport.createPair();

        dealer.handleRegister(new WampMessage.Register(1L, Map.of(), "com.test.proc"), calleePair[0]);

        var call = new WampMessage.Call(2L, Map.of("timeout", 0.1), "com.test.proc", List.of());
        dealer.handleCall(call, callerPair[0], 10);

        // Consume invocation from callee side
        calleePair[1].receive();

        // Cancel the call (with skip mode) — should also cancel timeout
        dealer.handleCancel(new WampMessage.Cancel(2L, Map.of("mode", "skip")));

        var error = callerPair[1].receive();
        assertThat(error).isInstanceOf(WampMessage.Error.class);
        assertThat(((WampMessage.Error) error).error()).isEqualTo("wamp.error.canceled");

        // No timeout should fire because cancel consumed it
        var timeoutMsg = callerPair[1].tryReceive();
        assertThat(timeoutMsg).isNull();

        executor.shutdown();
    }

    @Test
    void testInvalidTimeoutValueIgnored() {
        var dealer = new Dealer();
        var executor = Executors.newSingleThreadScheduledExecutor();
        dealer.setTimeoutExecutor(executor);

        var callerPair = InMemoryTransport.createPair();
        var calleePair = InMemoryTransport.createPair();

        dealer.handleRegister(new WampMessage.Register(1L, Map.of(), "com.test.proc"), calleePair[0]);

        // timeout with a string value (invalid) — should not throw
        var call = new WampMessage.Call(2L, Map.of("timeout", "invalid"), "com.test.proc", List.of());
        var invocation = dealer.handleCall(call, callerPair[0], 10);
        assertThat(invocation).isNotNull();

        // Normal operation should still work
        calleePair[1].receive(); // consume invocation
        dealer.handleYield(new WampMessage.Yield(invocation.requestId(), Map.of(), List.of("ok")));

        var result = callerPair[1].receive();
        assertThat(result).isInstanceOf(WampMessage.Result.class);

        executor.shutdown();
    }
}
