package ssg.legoflow.wamp.demo.base;

import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.role.Callee;
import ssg.legoflow.wamp.core.role.Caller;
import ssg.legoflow.wamp.core.router.Dealer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Calculator service demo: registers math operations (add, multiply, divide) as separate
 * callees and routes calls through a shared Dealer. Division by zero produces a WAMP error.
 *
 * @since 0.1.0
 */
public class CalculatorServiceDemo {

    private final Dealer dealer = new Dealer();
    private final Map<String, CalleeEndpoint> callees = new ConcurrentHashMap<>();
    private InMemoryTransport[] callerPair;
    private Caller caller;

    /**
     * Sets up the calculator service by registering add, multiply, and divide procedures.
     */
    public void setup() {
        callerPair = InMemoryTransport.createPair();
        caller = new Caller(callerPair[0]);

        registerProcedure("com.calc.add", args -> {
            double a = ((Number) args.get(0)).doubleValue();
            double b = ((Number) args.get(1)).doubleValue();
            return List.of(a + b);
        });

        registerProcedure("com.calc.multiply", args -> {
            double a = ((Number) args.get(0)).doubleValue();
            double b = ((Number) args.get(1)).doubleValue();
            return List.of(a * b);
        });

        registerProcedure("com.calc.divide", args -> {
            double a = ((Number) args.get(0)).doubleValue();
            double b = ((Number) args.get(1)).doubleValue();
            if (b == 0) {
                throw new ArithmeticException("Division by zero");
            }
            return List.of(a / b);
        });
    }

    /**
     * Calls the add procedure.
     *
     * @param a first operand
     * @param b second operand
     * @return the sum
     */
    public double add(double a, double b) {
        return callProcedure("com.calc.add", List.of(a, b));
    }

    /**
     * Calls the multiply procedure.
     *
     * @param a first operand
     * @param b second operand
     * @return the product
     */
    public double multiply(double a, double b) {
        return callProcedure("com.calc.multiply", List.of(a, b));
    }

    /**
     * Calls the divide procedure.
     *
     * @param a dividend
     * @param b divisor
     * @return the quotient
     */
    public double divide(double a, double b) {
        return callProcedure("com.calc.divide", List.of(a, b));
    }

    /**
     * Calls the divide procedure and returns the future directly,
     * allowing callers to check for errors.
     *
     * @param a dividend
     * @param b divisor
     * @return a future that may complete exceptionally on division by zero
     */
    public CompletableFuture<WampMessage.Result> divideAsync(double a, double b) {
        var future = caller.call("com.calc.divide", List.of(a, b));
        var callMsg = (WampMessage.Call) callerPair[1].receive();
        dealer.handleCall(callMsg, callerPair[1]);

        var endpoint = callees.get("com.calc.divide");
        var invocation = (WampMessage.Invocation) endpoint.calleePair[0].receive();
        try {
            endpoint.callee.handleInvocation(invocation);
            var yieldMsg = (WampMessage.Yield) endpoint.calleePair[1].receive();
            dealer.handleYield(yieldMsg);
            caller.handleResult((WampMessage.Result) callerPair[0].receive());
        } catch (RuntimeException e) {
            var errorMsg = new WampMessage.Error(
                    48, callMsg.requestId(), Map.of(), "wamp.error.runtime_error");
            callerPair[1].send(errorMsg);
            caller.handleError((WampMessage.Error) callerPair[0].receive());
        }
        return future;
    }

    private void registerProcedure(String procedure, Function<List<Object>, List<Object>> handler) {
        var pair = InMemoryTransport.createPair();
        var callee = new Callee(pair[0]);
        callee.register(procedure, handler);

        var registerMsg = (WampMessage.Register) pair[1].receive();
        var registered = dealer.handleRegister(registerMsg, pair[1]);
        pair[1].send(registered);
        callee.handleRegistered((WampMessage.Registered) pair[0].receive());

        callees.put(procedure, new CalleeEndpoint(callee, pair));
    }

    private double callProcedure(String procedure, List<Object> args) {
        var future = caller.call(procedure, args);

        var callMsg = (WampMessage.Call) callerPair[1].receive();
        dealer.handleCall(callMsg, callerPair[1]);

        var endpoint = callees.get(procedure);
        endpoint.callee.handleInvocation((WampMessage.Invocation) endpoint.calleePair[0].receive());
        dealer.handleYield((WampMessage.Yield) endpoint.calleePair[1].receive());
        caller.handleResult((WampMessage.Result) callerPair[0].receive());

        return ((Number) future.join().args().getFirst()).doubleValue();
    }

    private record CalleeEndpoint(Callee callee, InMemoryTransport[] calleePair) {}
}
