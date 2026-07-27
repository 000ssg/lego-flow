package ssg.legoflow.wamp.demo.base;

import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.role.Callee;
import ssg.legoflow.wamp.core.role.Caller;
import ssg.legoflow.wamp.core.router.Dealer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RpcErrorHandlingDemoTest {

    @Test
    void testCallToNonExistentProcedureReturnsError() {
        var callerPair = InMemoryTransport.createPair();
        var dealer = new Dealer();
        var caller = new Caller(callerPair[0]);

        var future = caller.call("com.example.nonexistent", List.of());

        var callMsg = (WampMessage.Call) callerPair[1].receive();
        dealer.handleCall(callMsg, callerPair[1]);

        var errorMsg = (WampMessage.Error) callerPair[0].receive();
        assertThat(errorMsg.error()).isEqualTo("wamp.error.no_such_procedure");

        caller.handleError(errorMsg);
        assertThat(future).isCompletedExceptionally();
    }

    @Test
    void testCallerHandlesErrorGracefully() {
        var pair = InMemoryTransport.createPair();
        var caller = new Caller(pair[0]);

        var future = caller.call("com.example.fail", List.of());
        var callMsg = (WampMessage.Call) pair[1].receive();

        var error = new WampMessage.Error(
                48, callMsg.requestId(), Map.of("message", "something went wrong"),
                "wamp.error.runtime_error");
        caller.handleError(error);

        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .cause()
                .hasMessageContaining("wamp.error.runtime_error");
    }

    @Test
    void testErrorForUnknownRequestIdIsIgnored() {
        var pair = InMemoryTransport.createPair();
        var caller = new Caller(pair[0]);

        var error = new WampMessage.Error(48, 99999L, Map.of(), "wamp.error.no_such_procedure");
        // Should not throw
        caller.handleError(error);
    }

    @Test
    void testMultipleCallsOneFailsOneSucceeds() {
        var callerPair = InMemoryTransport.createPair();
        var calleePair = InMemoryTransport.createPair();
        var dealer = new Dealer();
        var caller = new Caller(callerPair[0]);
        var callee = new Callee(calleePair[0]);

        callee.register("com.example.registered", args -> List.of("ok"));
        var registerMsg = (WampMessage.Register) calleePair[1].receive();
        dealer.handleRegister(registerMsg, calleePair[1]);
        calleePair[1].send(new WampMessage.Registered(registerMsg.requestId(), 1L));
        callee.handleRegistered((WampMessage.Registered) calleePair[0].receive());

        // Call to non-existent procedure
        var failFuture = caller.call("com.example.nonexistent", List.of());
        var failCall = (WampMessage.Call) callerPair[1].receive();
        dealer.handleCall(failCall, callerPair[1]);
        var errorMsg = (WampMessage.Error) callerPair[0].receive();
        caller.handleError(errorMsg);

        // Call to registered procedure
        var successFuture = caller.call("com.example.registered", List.of());
        var successCall = (WampMessage.Call) callerPair[1].receive();
        dealer.handleCall(successCall, callerPair[1]);
        callee.handleInvocation((WampMessage.Invocation) calleePair[0].receive());
        dealer.handleYield((WampMessage.Yield) calleePair[1].receive());
        caller.handleResult((WampMessage.Result) callerPair[0].receive());

        assertThat(failFuture).isCompletedExceptionally();
        assertThat(successFuture).isCompleted();
        assertThat(successFuture.join().args()).containsExactly("ok");
    }
}
