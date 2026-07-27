package ssg.legoflow.wamp.core;

import ssg.legoflow.wamp.core.role.Callee;
import ssg.legoflow.wamp.core.role.Caller;
import ssg.legoflow.wamp.core.router.Dealer;
import ssg.legoflow.wamp.demo.base.InMemoryTransport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallerCalleeTest {

    @Test
    void testCallerSendsCallMessage() {
        var pair = InMemoryTransport.createPair();
        var caller = new Caller(pair[0]);

        caller.call("com.test.proc", List.of(1, 2));

        var msg = pair[1].receive();
        assertThat(msg).isInstanceOf(WampMessage.Call.class);
        var call = (WampMessage.Call) msg;
        assertThat(call.procedure()).isEqualTo("com.test.proc");
        assertThat(call.args()).containsExactly(1, 2);
    }

    @Test
    void testCallerHandlesResult() {
        var pair = InMemoryTransport.createPair();
        var caller = new Caller(pair[0]);

        var future = caller.call("com.test.proc", List.of());

        var call = (WampMessage.Call) pair[1].receive();
        pair[1].send(new WampMessage.Result(call.requestId(), Map.of(), List.of("ok")));
        caller.handleResult((WampMessage.Result) pair[0].receive());

        assertThat(future).isCompleted();
        assertThat(future.join().args()).containsExactly("ok");
    }

    @Test
    void testCallerHandlesError() {
        var pair = InMemoryTransport.createPair();
        var caller = new Caller(pair[0]);

        var future = caller.call("com.test.proc", List.of());

        var call = (WampMessage.Call) pair[1].receive();
        var error = new WampMessage.Error(
                WampMessageType.CALL.code(), call.requestId(),
                Map.of(), "wamp.error.runtime_error");
        caller.handleError(error);

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void testCalleeRegistersAndHandlesInvocation() {
        var pair = InMemoryTransport.createPair();
        var callee = new Callee(pair[0]);

        callee.register("com.test.add", args -> {
            int a = ((Number) args.get(0)).intValue();
            int b = ((Number) args.get(1)).intValue();
            return List.of(a + b);
        });

        var registerMsg = (WampMessage.Register) pair[1].receive();
        assertThat(registerMsg.procedure()).isEqualTo("com.test.add");

        pair[1].send(new WampMessage.Registered(registerMsg.requestId(), 100L));
        callee.handleRegistered((WampMessage.Registered) pair[0].receive());

        assertThat(callee.getProcedures()).containsKey("com.test.add");
    }

    @Test
    void testCalleeYieldsResult() {
        var pair = InMemoryTransport.createPair();
        var callee = new Callee(pair[0]);

        callee.register("com.test.double", args -> {
            int val = ((Number) args.get(0)).intValue();
            return List.of(val * 2);
        });

        pair[1].receive(); // consume Register
        pair[1].send(new WampMessage.Registered(1L, 200L));
        callee.handleRegistered((WampMessage.Registered) pair[0].receive());

        var invocation = new WampMessage.Invocation(42L, 200L, Map.of(), List.of(5));
        callee.handleInvocation(invocation);

        var yieldMsg = (WampMessage.Yield) pair[1].receive();
        assertThat(yieldMsg.requestId()).isEqualTo(42L);
        assertThat(yieldMsg.args()).containsExactly(10);
    }

    @Test
    void testMultipleConcurrentCalls() {
        var callerPair = InMemoryTransport.createPair();
        var calleePair = InMemoryTransport.createPair();
        var dealer = new Dealer();
        var caller = new Caller(callerPair[0]);
        var callee = new Callee(calleePair[0]);

        callee.register("com.test.echo", args -> args);
        var registerMsg = (WampMessage.Register) calleePair[1].receive();
        dealer.handleRegister(registerMsg, calleePair[1]);
        calleePair[1].send(new WampMessage.Registered(registerMsg.requestId(), 1L));
        callee.handleRegistered((WampMessage.Registered) calleePair[0].receive());

        var future1 = caller.call("com.test.echo", List.of("first"));
        var future2 = caller.call("com.test.echo", List.of("second"));

        // Process first call
        var call1 = (WampMessage.Call) callerPair[1].receive();
        dealer.handleCall(call1, callerPair[1]);
        callee.handleInvocation((WampMessage.Invocation) calleePair[0].receive());
        dealer.handleYield((WampMessage.Yield) calleePair[1].receive());
        caller.handleResult((WampMessage.Result) callerPair[0].receive());

        // Process second call
        var call2 = (WampMessage.Call) callerPair[1].receive();
        dealer.handleCall(call2, callerPair[1]);
        callee.handleInvocation((WampMessage.Invocation) calleePair[0].receive());
        dealer.handleYield((WampMessage.Yield) calleePair[1].receive());
        caller.handleResult((WampMessage.Result) callerPair[0].receive());

        assertThat(future1.join().args()).containsExactly("first");
        assertThat(future2.join().args()).containsExactly("second");
    }
}
