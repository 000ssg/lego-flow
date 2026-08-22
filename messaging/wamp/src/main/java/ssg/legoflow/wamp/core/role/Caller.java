package ssg.legoflow.wamp.core.role;

import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.transport.WampTransport;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
/**
 * WAMP Caller role — invokes remote procedures and awaits results.
 *
 * @since 0.1.0
 */
public class Caller {

    private final WampTransport transport;
    private final AtomicLong requestIdCounter = new AtomicLong(1);
    private final Map<Long, CompletableFuture<WampMessage.Result>> pendingCalls = new ConcurrentHashMap<>();

    /**
     * Creates a new Caller that communicates via the given transport.
     *
     * @param transport the transport to use
     */
    public Caller(WampTransport transport) {
        this.transport = transport;
    }

    /**
     * Calls a remote procedure with the given arguments.
     *
     * @param procedure the procedure URI to call
     * @param args      positional arguments for the call
     * @return a future that completes with the result
     */
    public CompletableFuture<WampMessage.Result> call(String procedure, List<Object> args) {
        long requestId = requestIdCounter.getAndIncrement();
        var future = new CompletableFuture<WampMessage.Result>();
        pendingCalls.put(requestId, future);
        transport.send(new WampMessage.Call(requestId, Map.of(), procedure, args));
        return future;
    }

    /**
     * Handles an incoming Result message by completing the corresponding pending call.
     *
     * @param result the result message
     */
    public void handleResult(WampMessage.Result result) {
        var future = pendingCalls.remove(result.requestId());
        if (future != null) {
            future.complete(result);
        }
    }

    /**
     * Handles an incoming Error message by completing the corresponding pending call exceptionally.
     *
     * @param error the error message
     */
    public void handleError(WampMessage.Error error) {
        var future = pendingCalls.remove(error.requestId());
        if (future != null) {
            future.completeExceptionally(new RuntimeException("WAMP call error: " + error.error()));
        }
    }
}
