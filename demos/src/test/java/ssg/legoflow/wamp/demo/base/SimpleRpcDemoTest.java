package ssg.legoflow.wamp.demo.base;

import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.role.Callee;
import ssg.legoflow.wamp.core.role.Caller;
import ssg.legoflow.wamp.core.router.Dealer;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
class SimpleRpcDemoTest {

    @Test
    void testSimpleRpcDemo() {
        var demo = new SimpleRpcDemo();
        var result = demo.run();

        assertThat(result).hasSize(1);
        assertThat(((Number) result.getFirst()).intValue()).isEqualTo(8);
    }

    @Test
    void testRpcWithMultiplyProcedure() {
        var callerPair = InMemoryTransport.createPair();
        var calleePair = InMemoryTransport.createPair();
        var dealer = new Dealer();
        var caller = new Caller(callerPair[0]);
        var callee = new Callee(calleePair[0]);

        // Register multiply
        callee.register("com.example.multiply", args -> {
            int a = ((Number) args.get(0)).intValue();
            int b = ((Number) args.get(1)).intValue();
            return List.of(a * b);
        });

        var registerMsg = (WampMessage.Register) calleePair[1].receive();
        var registered = dealer.handleRegister(registerMsg, calleePair[1]);
        calleePair[1].send(registered);
        callee.handleRegistered((WampMessage.Registered) calleePair[0].receive());

        // Call multiply(6, 7)
        var future = caller.call("com.example.multiply", List.of(6, 7));
        var callMsg = (WampMessage.Call) callerPair[1].receive();
        dealer.handleCall(callMsg, callerPair[1]);
        callee.handleInvocation((WampMessage.Invocation) calleePair[0].receive());
        dealer.handleYield((WampMessage.Yield) calleePair[1].receive());
        caller.handleResult((WampMessage.Result) callerPair[0].receive());

        assertThat(future).isCompleted();
        assertThat(((Number) future.join().args().getFirst()).intValue()).isEqualTo(42);
    }

    @Test
    void testRpcWithStringResult() {
        var callerPair = InMemoryTransport.createPair();
        var calleePair = InMemoryTransport.createPair();
        var dealer = new Dealer();
        var caller = new Caller(callerPair[0]);
        var callee = new Callee(calleePair[0]);

        callee.register("com.example.greet", args ->
                List.of("Hello, " + args.getFirst() + "!"));

        var registerMsg = (WampMessage.Register) calleePair[1].receive();
        dealer.handleRegister(registerMsg, calleePair[1]);
        calleePair[1].send(new WampMessage.Registered(registerMsg.requestId(), 1L));
        callee.handleRegistered((WampMessage.Registered) calleePair[0].receive());

        var future = caller.call("com.example.greet", List.of("World"));
        var callMsg = (WampMessage.Call) callerPair[1].receive();
        dealer.handleCall(callMsg, callerPair[1]);
        callee.handleInvocation((WampMessage.Invocation) calleePair[0].receive());
        dealer.handleYield((WampMessage.Yield) calleePair[1].receive());
        caller.handleResult((WampMessage.Result) callerPair[0].receive());

        assertThat(future.join().args().getFirst()).isEqualTo("Hello, World!");
    }
}
