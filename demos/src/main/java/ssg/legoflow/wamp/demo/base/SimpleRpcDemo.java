package ssg.legoflow.wamp.demo.base;

import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.role.Callee;
import ssg.legoflow.wamp.core.role.Caller;
import ssg.legoflow.wamp.core.router.Dealer;
import java.util.List;
/**
 * Simple RPC demo: creates a Caller and Callee, wires them through a Dealer via InMemoryTransport,
 * registers an "add" procedure, and calls it.
 *
 * @since 0.1.0
 */
public class SimpleRpcDemo {

    /**
     * Runs the RPC demo.
     *
     * @return the result arguments from the "add" procedure call
     */
    public List<Object> run() {
        // Create transport pairs: caller <-> dealer, dealer <-> callee
        var callerPair = InMemoryTransport.createPair();
        var calleePair = InMemoryTransport.createPair();

        var callerTransport = callerPair[0]; // caller side
        var callerRouterTransport = callerPair[1]; // router side (for caller)
        var calleeTransport = calleePair[0]; // callee side
        var calleeRouterTransport = calleePair[1]; // router side (for callee)

        var dealer = new Dealer();
        var caller = new Caller(callerTransport);
        var callee = new Callee(calleeTransport);

        // Register "add" procedure
        callee.register("com.example.add", args -> {
            int a = ((Number) args.get(0)).intValue();
            int b = ((Number) args.get(1)).intValue();
            return List.of(a + b);
        });

        // Router receives the Register message from callee
        var registerMsg = (WampMessage.Register) calleeRouterTransport.receive();
        var registered = dealer.handleRegister(registerMsg, calleeRouterTransport);
        calleeRouterTransport.send(registered);

        // Callee receives registered confirmation
        var registeredMsg = (WampMessage.Registered) calleeTransport.receive();
        callee.handleRegistered(registeredMsg);

        // Caller calls "add" with arguments [3, 5]
        var future = caller.call("com.example.add", List.of(3, 5));

        // Router receives the Call message from caller
        var callMsg = (WampMessage.Call) callerRouterTransport.receive();
        dealer.handleCall(callMsg, callerRouterTransport);

        // Callee receives the Invocation
        var invocation = (WampMessage.Invocation) calleeTransport.receive();
        callee.handleInvocation(invocation);

        // Router receives the Yield from callee
        var yieldMsg = (WampMessage.Yield) calleeRouterTransport.receive();
        dealer.handleYield(yieldMsg);

        // Caller receives the Result
        var resultMsg = (WampMessage.Result) callerTransport.receive();
        caller.handleResult(resultMsg);

        return future.join().args();
    }
}
