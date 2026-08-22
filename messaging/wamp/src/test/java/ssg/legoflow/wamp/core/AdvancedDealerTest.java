package ssg.legoflow.wamp.core;

import ssg.legoflow.wamp.core.router.Dealer;
import ssg.legoflow.wamp.core.transport.InMemoryTransport;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for Dealer Advanced Profile features: progressive call results,
 * call cancellation, caller identification, and shared registrations.
 */
class AdvancedDealerTest {

    // --- Progressive call results ---

    @Test
    void testProgressiveCallResults() {
        var dealer = new Dealer();
        var callerPair = InMemoryTransport.createPair();
        var calleePair = InMemoryTransport.createPair();

        // Register
        var register = new WampMessage.Register(1L, Map.of(), "com.test.stream");
        dealer.handleRegister(register, calleePair[0]);

        // Call with receive_progress
        var call = new WampMessage.Call(2L, Map.of("receive_progress", true), "com.test.stream", List.of());
        var invocation = dealer.handleCall(call, callerPair[0], 10);
        assertThat(invocation).isNotNull();

        // Callee yields progressive result
        dealer.handleYield(new WampMessage.Yield(invocation.requestId(), Map.of("progress", true), List.of("part1")));

        var progress = (WampMessage.Result) callerPair[1].receive();
        assertThat(progress.details()).containsEntry("progress", true);
        assertThat(progress.args()).containsExactly("part1");

        // Callee yields final result
        dealer.handleYield(new WampMessage.Yield(invocation.requestId(), Map.of(), List.of("final")));

        var result = (WampMessage.Result) callerPair[1].receive();
        assertThat(result.details()).doesNotContainKey("progress");
        assertThat(result.args()).containsExactly("final");
    }

    // --- Call cancellation ---

    @Test
    void testCancelWithSkipMode() {
        var dealer = new Dealer();
        var callerPair = InMemoryTransport.createPair();
        var calleePair = InMemoryTransport.createPair();

        dealer.handleRegister(new WampMessage.Register(1L, Map.of(), "com.test.slow"), calleePair[0]);
        dealer.handleCall(new WampMessage.Call(2L, Map.of(), "com.test.slow", List.of()), callerPair[0], 10);

        // Consume the invocation on callee side
        calleePair[1].receive();

        // Cancel with "skip" mode
        var cancelled = dealer.handleCancel(new WampMessage.Cancel(2L, Map.of("mode", "skip")));
        assertThat(cancelled).isTrue();

        // Caller should receive an error
        var error = (WampMessage.Error) callerPair[1].receive();
        assertThat(error.error()).isEqualTo("wamp.error.canceled");
    }

    @Test
    void testCancelWithKillnowaitMode() {
        var dealer = new Dealer();
        var callerPair = InMemoryTransport.createPair();
        var calleePair = InMemoryTransport.createPair();

        dealer.handleRegister(new WampMessage.Register(1L, Map.of(), "com.test.slow"), calleePair[0]);
        dealer.handleCall(new WampMessage.Call(2L, Map.of(), "com.test.slow", List.of()), callerPair[0], 10);

        calleePair[1].receive(); // consume invocation

        dealer.handleCancel(new WampMessage.Cancel(2L, Map.of("mode", "killnowait")));

        // Callee should receive INTERRUPT
        var interrupt = (WampMessage.Interrupt) calleePair[1].receive();
        assertThat(interrupt).isNotNull();

        // Caller should receive error
        var error = (WampMessage.Error) callerPair[1].receive();
        assertThat(error.error()).isEqualTo("wamp.error.canceled");
    }

    @Test
    void testCancelWithKillMode() {
        var dealer = new Dealer();
        var callerPair = InMemoryTransport.createPair();
        var calleePair = InMemoryTransport.createPair();

        dealer.handleRegister(new WampMessage.Register(1L, Map.of(), "com.test.slow"), calleePair[0]);
        dealer.handleCall(new WampMessage.Call(2L, Map.of(), "com.test.slow", List.of()), callerPair[0], 10);

        calleePair[1].receive(); // consume invocation

        dealer.handleCancel(new WampMessage.Cancel(2L, Map.of("mode", "kill")));

        // Callee should receive INTERRUPT
        var interrupt = (WampMessage.Interrupt) calleePair[1].receive();
        assertThat(interrupt).isNotNull();
    }

    @Test
    void testCancelNonexistentCallReturnsFalse() {
        var dealer = new Dealer();
        assertThat(dealer.handleCancel(new WampMessage.Cancel(999L, Map.of("mode", "skip")))).isFalse();
    }

    // --- Caller identification ---

    @Test
    void testCallerIdentificationDisclosed() {
        var dealer = new Dealer();
        var callerPair = InMemoryTransport.createPair();
        var calleePair = InMemoryTransport.createPair();

        dealer.handleRegister(new WampMessage.Register(1L, Map.of(), "com.test.proc"), calleePair[0]);

        var call = new WampMessage.Call(2L, Map.of("disclose_me", true), "com.test.proc", List.of());
        dealer.handleCall(call, callerPair[0], 42);

        var invocation = (WampMessage.Invocation) calleePair[1].receive();
        assertThat(invocation.details()).containsEntry("caller", 42L);
    }

    @Test
    void testCallerNotDisclosedByDefault() {
        var dealer = new Dealer();
        var callerPair = InMemoryTransport.createPair();
        var calleePair = InMemoryTransport.createPair();

        dealer.handleRegister(new WampMessage.Register(1L, Map.of(), "com.test.proc"), calleePair[0]);

        dealer.handleCall(new WampMessage.Call(2L, Map.of(), "com.test.proc", List.of()), callerPair[0], 42);

        var invocation = (WampMessage.Invocation) calleePair[1].receive();
        assertThat(invocation.details()).doesNotContainKey("caller");
    }

    // --- Shared registrations ---

    @Test
    void testSharedRegistrationRoundRobin() {
        var dealer = new Dealer();
        var calleePair1 = InMemoryTransport.createPair();
        var calleePair2 = InMemoryTransport.createPair();
        var callerPair = InMemoryTransport.createPair();

        dealer.handleRegister(new WampMessage.Register(1L, Map.of("invoke", "roundrobin"), "com.test.shared"), calleePair1[0]);
        dealer.handleRegister(new WampMessage.Register(2L, Map.of("invoke", "roundrobin"), "com.test.shared"), calleePair2[0]);

        assertThat(dealer.getRegistrationCount("com.test.shared")).isEqualTo(2);

        // First call goes to callee1
        dealer.handleCall(new WampMessage.Call(10L, Map.of(), "com.test.shared", List.of("a")), callerPair[0]);
        var inv1 = calleePair1[1].receive();
        assertThat(inv1).isInstanceOf(WampMessage.Invocation.class);

        // Second call goes to callee2
        dealer.handleCall(new WampMessage.Call(11L, Map.of(), "com.test.shared", List.of("b")), callerPair[0]);
        var inv2 = calleePair2[1].receive();
        assertThat(inv2).isInstanceOf(WampMessage.Invocation.class);
    }

    @Test
    void testSharedRegistrationFirst() {
        var dealer = new Dealer();
        var calleePair1 = InMemoryTransport.createPair();
        var calleePair2 = InMemoryTransport.createPair();
        var callerPair = InMemoryTransport.createPair();

        dealer.handleRegister(new WampMessage.Register(1L, Map.of("invoke", "first"), "com.test.first"), calleePair1[0]);
        dealer.handleRegister(new WampMessage.Register(2L, Map.of("invoke", "first"), "com.test.first"), calleePair2[0]);

        // Both calls should go to first callee
        dealer.handleCall(new WampMessage.Call(10L, Map.of(), "com.test.first", List.of()), callerPair[0]);
        dealer.handleCall(new WampMessage.Call(11L, Map.of(), "com.test.first", List.of()), callerPair[0]);

        assertThat(calleePair1[1].receive()).isInstanceOf(WampMessage.Invocation.class);
        assertThat(calleePair1[1].receive()).isInstanceOf(WampMessage.Invocation.class);
    }

    @Test
    void testSharedRegistrationLast() {
        var dealer = new Dealer();
        var calleePair1 = InMemoryTransport.createPair();
        var calleePair2 = InMemoryTransport.createPair();
        var callerPair = InMemoryTransport.createPair();

        dealer.handleRegister(new WampMessage.Register(1L, Map.of("invoke", "last"), "com.test.last"), calleePair1[0]);
        dealer.handleRegister(new WampMessage.Register(2L, Map.of("invoke", "last"), "com.test.last"), calleePair2[0]);

        // Both calls should go to last callee
        dealer.handleCall(new WampMessage.Call(10L, Map.of(), "com.test.last", List.of()), callerPair[0]);

        assertThat(calleePair2[1].receive()).isInstanceOf(WampMessage.Invocation.class);
    }

    @Test
    void testSingleRegistrationRejectsDuplicate() {
        var dealer = new Dealer();
        var calleePair1 = InMemoryTransport.createPair();
        var calleePair2 = InMemoryTransport.createPair();

        var registered = dealer.handleRegister(new WampMessage.Register(1L, Map.of(), "com.test.single"), calleePair1[0]);
        assertThat(registered).isInstanceOf(WampMessage.Registered.class);

        var rejected = dealer.handleRegister(new WampMessage.Register(2L, Map.of(), "com.test.single"), calleePair2[0]);
        assertThat(rejected).isInstanceOf(WampMessage.Error.class);
        assertThat(((WampMessage.Error) rejected).error()).isEqualTo("wamp.error.procedure_already_exists");
    }

    @Test
    void testUnregisterWithSharedRegistrations() {
        var dealer = new Dealer();
        var calleePair1 = InMemoryTransport.createPair();
        var calleePair2 = InMemoryTransport.createPair();

        var reg1 = (WampMessage.Registered) dealer.handleRegister(
                new WampMessage.Register(1L, Map.of("invoke", "roundrobin"), "com.test.shared"), calleePair1[0]);
        dealer.handleRegister(new WampMessage.Register(2L, Map.of("invoke", "roundrobin"), "com.test.shared"), calleePair2[0]);

        assertThat(dealer.getRegistrationCount("com.test.shared")).isEqualTo(2);

        dealer.handleUnregister(new WampMessage.Unregister(3L, reg1.registrationId()));
        assertThat(dealer.getRegistrationCount("com.test.shared")).isEqualTo(1);
    }
}
