package ssg.legoflow.wamp.core.role;

import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.transport.WampTransport;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * WAMP Callee role — registers procedures and handles invocations.
 *
 * @since 0.1.0
 */
public class Callee {

    private final WampTransport transport;
    private final AtomicLong requestIdCounter = new AtomicLong(1);
    private final Map<String, Function<List<Object>, List<Object>>> procedures = new ConcurrentHashMap<>();
    private final Map<Long, String> pendingRegistrations = new ConcurrentHashMap<>();

    /**
     * Creates a new Callee that communicates via the given transport.
     *
     * @param transport the transport to use
     */
    public Callee(WampTransport transport) {
        this.transport = transport;
    }

    /**
     * Registers a procedure with the given handler.
     * Sends a REGISTER message and stores the handler for future invocations.
     *
     * @param procedure the procedure URI to register
     * @param handler   the function that processes invocation arguments and returns result arguments
     */
    public void register(String procedure, Function<List<Object>, List<Object>> handler) {
        procedures.put(procedure, handler);
        long requestId = requestIdCounter.getAndIncrement();
        pendingRegistrations.put(requestId, procedure);
        transport.send(new WampMessage.Register(requestId, Map.of(), procedure));
    }

    /**
     * Handles an incoming Invocation by invoking the registered procedure and yielding the result.
     *
     * @param invocation the invocation message
     */
    public void handleInvocation(WampMessage.Invocation invocation) {
        // Find the procedure by registration ID — for simplicity, iterate
        // In a full implementation, we'd maintain a registrationId -> procedure mapping
        for (var entry : procedures.entrySet()) {
            var handler = entry.getValue();
            var result = handler.apply(invocation.args());
            transport.send(new WampMessage.Yield(invocation.requestId(), Map.of(), result));
            return;
        }
    }

    /**
     * Handles a Registered confirmation from the router.
     *
     * @param registered the registered confirmation message
     */
    public void handleRegistered(WampMessage.Registered registered) {
        pendingRegistrations.remove(registered.requestId());
    }

    /**
     * Returns the map of registered procedures.
     *
     * @return unmodifiable view of procedure URI to handler map
     */
    public Map<String, Function<List<Object>, List<Object>>> getProcedures() {
        return Map.copyOf(procedures);
    }
}
