package ssg.legoflow.wamp.core.router;

import ssg.legoflow.wamp.core.WampMessage;
import ssg.legoflow.wamp.core.WampMessageType;
import ssg.legoflow.wamp.core.transport.WampTransport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WAMP Dealer — manages RPC procedure registrations and routes Call requests to Callees.
 * Supports Advanced Profile features: progressive call results, call cancellation,
 * caller identification, and shared registrations with load-balancing policies.
 *
 * @since 0.1.0
 */
public class Dealer {

    private final AtomicLong registrationIdCounter = new AtomicLong(1);
    private final AtomicLong invocationIdCounter = new AtomicLong(1);

    /** procedure URI -> list of registration entries (shared registrations) */
    private final Map<String, List<RegistrationEntry>> registrations = new ConcurrentHashMap<>();
    /** invocation request ID -> pending invocation info */
    private final Map<Long, PendingInvocation> pendingInvocations = new ConcurrentHashMap<>();
    /** round-robin counters per procedure */
    private final Map<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();

    /**
     * Handles a Register request from a callee. Supports shared registrations
     * with the {@code invoke} option: "single" (default), "first", "last", "roundrobin", "random".
     *
     * @param register  the register message
     * @param transport the callee's transport
     * @return the Registered confirmation message, or {@code null} if registration is rejected
     */
    public WampMessage handleRegister(WampMessage.Register register, WampTransport transport) {
        long registrationId = registrationIdCounter.getAndIncrement();
        String procedure = register.procedure();
        String invokePolicy = getInvokePolicy(register.options());
        var entry = new RegistrationEntry(registrationId, transport, invokePolicy);

        var existing = registrations.get(procedure);
        if (existing != null && !existing.isEmpty()) {
            String existingPolicy = existing.getFirst().invokePolicy();
            if ("single".equals(existingPolicy)) {
                // Single policy does not allow shared registration
                return new WampMessage.Error(
                        WampMessageType.REGISTER.code(), register.requestId(),
                        Map.of(), "wamp.error.procedure_already_exists");
            }
        }

        registrations.computeIfAbsent(procedure, k -> new ArrayList<>()).add(entry);
        return new WampMessage.Registered(register.requestId(), registrationId);
    }

    /**
     * Handles an Unregister request from a callee.
     *
     * @param unregister the unregister message
     * @return the Unregistered confirmation message
     */
    public WampMessage.Unregistered handleUnregister(WampMessage.Unregister unregister) {
        for (var entries : registrations.values()) {
            entries.removeIf(e -> e.registrationId() == unregister.registrationId());
        }
        registrations.values().removeIf(List::isEmpty);
        return new WampMessage.Unregistered(unregister.requestId());
    }

    /**
     * Handles a Call request from a caller by routing it to a registered callee.
     * Supports caller identification via the {@code disclose_me} option,
     * and progressive results via the {@code receive_progress} option.
     *
     * @param call            the call message
     * @param callerTransport the caller's transport
     * @return the Invocation message sent to the callee, or {@code null} if no registration exists
     */
    public WampMessage.Invocation handleCall(WampMessage.Call call, WampTransport callerTransport) {
        return handleCall(call, callerTransport, 0);
    }

    /**
     * Handles a Call request with caller session ID tracking.
     *
     * @param call            the call message
     * @param callerTransport the caller's transport
     * @param callerSessionId the caller's session ID
     * @return the Invocation message sent to the callee, or {@code null}
     */
    public WampMessage.Invocation handleCall(WampMessage.Call call, WampTransport callerTransport, long callerSessionId) {
        var entries = registrations.get(call.procedure());
        if (entries == null || entries.isEmpty()) {
            callerTransport.send(new WampMessage.Error(
                    WampMessageType.CALL.code(), call.requestId(),
                    Map.of(), "wamp.error.no_such_procedure"));
            return null;
        }

        // Select callee based on invoke policy
        var entry = selectCallee(call.procedure(), entries);

        long invocationId = invocationIdCounter.getAndIncrement();
        boolean receiveProgress = Boolean.TRUE.equals(call.options().get("receive_progress"));
        pendingInvocations.put(invocationId, new PendingInvocation(
                callerTransport, call.requestId(), receiveProgress, entry.transport()));

        // Build invocation details
        var details = new java.util.HashMap<String, Object>();

        // Caller identification
        if (Boolean.TRUE.equals(call.options().get("disclose_me")) && callerSessionId != 0) {
            details.put("caller", callerSessionId);
        }

        // Progressive results indicator
        if (receiveProgress) {
            details.put("receive_progress", true);
        }

        var invocation = new WampMessage.Invocation(invocationId, entry.registrationId(), Map.copyOf(details), call.args());
        entry.transport().send(invocation);
        return invocation;
    }

    /**
     * Handles a Yield from a callee by routing the result back to the original caller.
     * Supports progressive results: if the yield has {@code progress: true}, the result
     * is forwarded as a progressive result and the invocation remains pending.
     *
     * @param yield the yield message from the callee
     */
    public void handleYield(WampMessage.Yield yield) {
        boolean isProgress = Boolean.TRUE.equals(yield.options().get("progress"));

        if (isProgress) {
            var pending = pendingInvocations.get(yield.requestId());
            if (pending != null && pending.receiveProgress()) {
                pending.callerTransport().send(
                        new WampMessage.Result(pending.callRequestId(),
                                Map.of("progress", true), yield.args()));
            }
        } else {
            var pending = pendingInvocations.remove(yield.requestId());
            if (pending != null) {
                pending.callerTransport().send(
                        new WampMessage.Result(pending.callRequestId(), Map.of(), yield.args()));
            }
        }
    }

    /**
     * Handles a Cancel request from a caller. Sends an INTERRUPT to the callee
     * based on the cancel mode.
     *
     * @param cancel the cancel message
     * @return {@code true} if the cancellation was processed
     */
    public boolean handleCancel(WampMessage.Cancel cancel) {
        // Find the pending invocation by the call request ID
        Long invocationId = null;
        PendingInvocation pending = null;
        for (var e : pendingInvocations.entrySet()) {
            if (e.getValue().callRequestId() == cancel.requestId()) {
                invocationId = e.getKey();
                pending = e.getValue();
                break;
            }
        }
        if (pending == null) return false;

        String mode = "killnowait";
        if (cancel.options().containsKey("mode")) {
            mode = (String) cancel.options().get("mode");
        }

        switch (mode) {
            case "skip" -> {
                // Just remove the invocation — don't send INTERRUPT
                pendingInvocations.remove(invocationId);
                pending.callerTransport().send(new WampMessage.Error(
                        WampMessageType.CALL.code(), cancel.requestId(),
                        Map.of(), "wamp.error.canceled"));
            }
            case "kill" -> {
                // Send INTERRUPT and wait for ERROR from callee
                pending.calleeTransport().send(new WampMessage.Interrupt(
                        invocationId, Map.of("mode", "kill")));
            }
            case "killnowait" -> {
                // Send INTERRUPT and immediately cancel
                pending.calleeTransport().send(new WampMessage.Interrupt(
                        invocationId, Map.of("mode", "killnowait")));
                pendingInvocations.remove(invocationId);
                pending.callerTransport().send(new WampMessage.Error(
                        WampMessageType.CALL.code(), cancel.requestId(),
                        Map.of(), "wamp.error.canceled"));
            }
        }
        return true;
    }

    /**
     * Handles an Error response from a callee (e.g., after INTERRUPT).
     *
     * @param error the error message from the callee
     */
    public void handleError(WampMessage.Error error) {
        var pending = pendingInvocations.remove((long) error.requestId());
        if (pending != null) {
            pending.callerTransport().send(new WampMessage.Error(
                    WampMessageType.CALL.code(), pending.callRequestId(),
                    error.details(), error.error()));
        }
    }

    /**
     * Returns whether a procedure is registered.
     *
     * @param procedure the procedure URI
     * @return {@code true} if registered
     */
    public boolean isRegistered(String procedure) {
        var entries = registrations.get(procedure);
        return entries != null && !entries.isEmpty();
    }

    /**
     * Returns the number of registrations for a procedure.
     *
     * @param procedure the procedure URI
     * @return the registration count
     */
    public int getRegistrationCount(String procedure) {
        var entries = registrations.get(procedure);
        return entries != null ? entries.size() : 0;
    }

    private RegistrationEntry selectCallee(String procedure, List<RegistrationEntry> entries) {
        if (entries.size() == 1) return entries.getFirst();

        String policy = entries.getFirst().invokePolicy();
        return switch (policy) {
            case "first" -> entries.getFirst();
            case "last" -> entries.getLast();
            case "roundrobin" -> {
                var counter = roundRobinCounters.computeIfAbsent(procedure, k -> new AtomicInteger(0));
                int idx = Math.abs(counter.getAndIncrement() % entries.size());
                yield entries.get(idx);
            }
            case "random" -> entries.get((int) (Math.random() * entries.size()));
            default -> entries.getFirst(); // single policy — should only have one
        };
    }

    private String getInvokePolicy(Map<String, Object> options) {
        if (options == null) return "single";
        var invoke = options.get("invoke");
        if (invoke instanceof String s) return s;
        return "single";
    }

    /**
     * Registration entry with invoke policy for shared registrations.
     *
     * @since 0.1.0
     */
    record RegistrationEntry(long registrationId, WampTransport transport, String invokePolicy) {}

    /**
     * Pending invocation tracking caller and callee info.
     *
     * @since 0.1.0
     */
    record PendingInvocation(WampTransport callerTransport, long callRequestId,
                             boolean receiveProgress, WampTransport calleeTransport) {
        PendingInvocation(WampTransport callerTransport, long callRequestId) {
            this(callerTransport, callRequestId, false, null);
        }
    }
}
