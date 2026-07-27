package ssg.legoflow.messaging.amqp.transport;

import ssg.legoflow.messaging.amqp.types.AmqpType;

import java.util.List;
import java.util.Map;

/**
 * Sealed interface for all AMQP 1.0 transport performatives.
 *
 * <p>Each performative corresponds to a described list sent in an AMQP frame body.
 * The nine performatives control connection, session, and link lifecycle.
 *
 * @since 1.0.0
 */
public sealed interface Performative
        permits Performative.Open, Performative.Begin, Performative.Attach,
                Performative.Flow, Performative.Transfer, Performative.Disposition,
                Performative.Detach, Performative.End, Performative.Close {

    /**
     * Connection negotiation (section 2.7.1).
     *
     * @param containerId   the container identifier (required)
     * @param hostname      the DNS hostname of the target
     * @param maxFrameSize  maximum frame size (default 4294967295)
     * @param channelMax    maximum channel number (default 65535)
     * @param idleTimeout   idle timeout in milliseconds (0 = disabled)
     * @param offeredCapabilities capabilities offered by this peer
     * @param desiredCapabilities capabilities desired from the peer
     * @param properties    connection properties
     */
    record Open(
            String containerId,
            String hostname,
            long maxFrameSize,
            int channelMax,
            long idleTimeout,
            List<String> offeredCapabilities,
            List<String> desiredCapabilities,
            Map<String, Object> properties
    ) implements Performative {

        /** Creates an Open with minimal required fields. */
        public Open(String containerId) {
            this(containerId, null, 0xFFFFFFFFL, 65535, 0, List.of(), List.of(), Map.of());
        }
    }

    /**
     * Session begin (section 2.7.2).
     *
     * @param remoteChannel    the remote channel number for this session (null if initiating)
     * @param nextOutgoingId   the transfer-id of the next transfer (required)
     * @param incomingWindow   the incoming window size (required)
     * @param outgoingWindow   the outgoing window size (required)
     * @param handleMax        the maximum handle number (default 4294967295)
     * @param offeredCapabilities capabilities offered
     * @param desiredCapabilities capabilities desired
     * @param properties       session properties
     */
    record Begin(
            Integer remoteChannel,
            long nextOutgoingId,
            long incomingWindow,
            long outgoingWindow,
            long handleMax,
            List<String> offeredCapabilities,
            List<String> desiredCapabilities,
            Map<String, Object> properties
    ) implements Performative {

        /** Creates a Begin with default window sizes. */
        public Begin(Integer remoteChannel, long nextOutgoingId, long incomingWindow, long outgoingWindow) {
            this(remoteChannel, nextOutgoingId, incomingWindow, outgoingWindow,
                    0xFFFFFFFFL, List.of(), List.of(), Map.of());
        }
    }

    /**
     * Link attach (section 2.7.3).
     *
     * @param name             the link name (required, unique within session)
     * @param handle           the link handle (required)
     * @param role             true = receiver, false = sender
     * @param sndSettleMode    sender settle mode (0=unsettled, 1=settled, 2=mixed)
     * @param rcvSettleMode    receiver settle mode (0=first, 1=second)
     * @param source           the source address (described type)
     * @param target           the target address (described type)
     * @param initialDeliveryCount the initial delivery count (senders only)
     * @param maxMessageSize   max message size in bytes (0 = no limit)
     * @param offeredCapabilities offered capabilities
     * @param desiredCapabilities desired capabilities
     * @param properties       link properties
     */
    record Attach(
            String name,
            long handle,
            boolean role,
            int sndSettleMode,
            int rcvSettleMode,
            AmqpType source,
            AmqpType target,
            Long initialDeliveryCount,
            long maxMessageSize,
            List<String> offeredCapabilities,
            List<String> desiredCapabilities,
            Map<String, Object> properties
    ) implements Performative {

        /** Creates an Attach with common defaults. */
        public Attach(String name, long handle, boolean role, AmqpType source, AmqpType target) {
            this(name, handle, role, 2, 0, source, target,
                    role ? null : 0L, 0, List.of(), List.of(), Map.of());
        }
    }

    /**
     * Link flow control (section 2.7.4).
     *
     * @param nextIncomingId   the expected next incoming transfer-id
     * @param incomingWindow   the incoming window size
     * @param nextOutgoingId   the next outgoing transfer-id
     * @param outgoingWindow   the outgoing window size
     * @param handle           the link handle (null for session-level flow)
     * @param deliveryCount    the delivery count at the sender
     * @param linkCredit       the link credit being granted
     * @param available        messages available at the sender
     * @param drain            request drain of remaining credit
     * @param echo             request flow state echo
     * @param properties       flow properties
     */
    record Flow(
            Long nextIncomingId,
            long incomingWindow,
            long nextOutgoingId,
            long outgoingWindow,
            Long handle,
            Long deliveryCount,
            Long linkCredit,
            Long available,
            boolean drain,
            boolean echo,
            Map<String, Object> properties
    ) implements Performative {}

    /**
     * Message transfer (section 2.7.5).
     *
     * @param handle          the link handle (required)
     * @param deliveryId      the delivery-id
     * @param deliveryTag     the delivery-tag
     * @param messageFormat   the message format (0 for standard AMQP)
     * @param settled         whether the delivery is pre-settled
     * @param more            whether more frames follow for this delivery
     * @param rcvSettleMode   receiver settle mode override
     * @param state           the delivery state
     * @param resume          whether this is a resume
     * @param aborted         whether the transfer is aborted
     * @param batchable       whether this transfer is batchable
     */
    record Transfer(
            long handle,
            Long deliveryId,
            byte[] deliveryTag,
            long messageFormat,
            boolean settled,
            boolean more,
            Integer rcvSettleMode,
            AmqpType state,
            boolean resume,
            boolean aborted,
            boolean batchable
    ) implements Performative {

        /** Creates a Transfer with common defaults. */
        public Transfer(long handle, Long deliveryId, byte[] deliveryTag, boolean settled) {
            this(handle, deliveryId, deliveryTag, 0, settled, false, null, null, false, false, false);
        }
    }

    /**
     * Delivery disposition (section 2.7.6).
     *
     * @param role       true = receiver, false = sender
     * @param first      the first delivery-id in the range
     * @param last       the last delivery-id in the range (null = same as first)
     * @param settled    whether the deliveries are settled
     * @param state      the delivery state (accepted/rejected/released/modified)
     * @param batchable  whether this disposition is batchable
     */
    record Disposition(
            boolean role,
            long first,
            Long last,
            boolean settled,
            AmqpType state,
            boolean batchable
    ) implements Performative {}

    /**
     * Link detach (section 2.7.7).
     *
     * @param handle the link handle
     * @param closed whether the link endpoint is closed
     * @param error  optional error condition
     */
    record Detach(
            long handle,
            boolean closed,
            AmqpType error
    ) implements Performative {

        /** Creates a graceful Detach with no error. */
        public Detach(long handle, boolean closed) {
            this(handle, closed, null);
        }
    }

    /**
     * Session end (section 2.7.8).
     *
     * @param error optional error condition
     */
    record End(AmqpType error) implements Performative {

        /** Creates a graceful End with no error. */
        public End() {
            this(null);
        }
    }

    /**
     * Connection close (section 2.7.9).
     *
     * @param error optional error condition
     */
    record Close(AmqpType error) implements Performative {

        /** Creates a graceful Close with no error. */
        public Close() {
            this(null);
        }
    }
}
