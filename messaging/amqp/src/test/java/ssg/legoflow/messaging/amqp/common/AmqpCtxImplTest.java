package ssg.legoflow.messaging.amqp.common;

import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.amqp.client.BrokerMode;
import ssg.legoflow.messaging.amqp.container.ContainerMode;
import static org.junit.jupiter.api.Assertions.*;

class AmqpCtxImplTest {

    @Test
    void contextCreation() {
        AmqpContext ctx = new AmqpCtxImpl();
        assertNotNull(ctx);
        assertEquals(ConnectionState.START, ctx.getConnectionState());
        assertNotNull(ctx.getSessionId());
        assertNull(ctx.getContainerId());
        assertEquals(BrokerMode.STANDARD, ctx.getBrokerMode());
        assertEquals(ContainerMode.STANDARD, ctx.getContainerMode());
        assertTrue(ctx.getMaxFrameSize() > 0);
        assertTrue(ctx.getChannelMax() > 0);
        assertTrue(ctx.getSessions().isEmpty());
    }

    @Test
    void validTransition() {
        AmqpContext ctx = new AmqpCtxImpl();
        assertTrue(ctx.transitionTo(ConnectionState.HDR_SENT));
        assertEquals(ConnectionState.HDR_SENT, ctx.getConnectionState());
    }

    @Test
    void invalidTransition() {
        AmqpContext ctx = new AmqpCtxImpl();
        assertFalse(ctx.transitionTo(ConnectionState.OPENED));
        assertEquals(ConnectionState.START, ctx.getConnectionState());
    }

    @Test
    void fullHappyPath() {
        AmqpContext ctx = new AmqpCtxImpl();
        assertTrue(ctx.transitionTo(ConnectionState.HDR_SENT));
        assertTrue(ctx.transitionTo(ConnectionState.HDR_RCVD));
        assertTrue(ctx.transitionTo(ConnectionState.HDR_EXCH));
        assertTrue(ctx.transitionTo(ConnectionState.OPEN_SENT));
        assertTrue(ctx.transitionTo(ConnectionState.OPEN_RCVD));
        assertTrue(ctx.transitionTo(ConnectionState.OPENED));
        assertEquals(ConnectionState.OPENED, ctx.getConnectionState());
    }

    @Test
    void typedFieldsSetAndGet() {
        AmqpContext ctx = new AmqpCtxImpl();
        ctx.setBrokerMode(BrokerMode.RABBITMQ);
        assertEquals(BrokerMode.RABBITMQ, ctx.getBrokerMode());
        ctx.setContainerMode(ContainerMode.ARTEMIS);
        assertEquals(ContainerMode.ARTEMIS, ctx.getContainerMode());
        ctx.setMaxFrameSize(65536);
        assertEquals(65536, ctx.getMaxFrameSize());
        ctx.setChannelMax(128);
        assertEquals(128, ctx.getChannelMax());
        ctx.setContainerId("test-container");
        assertEquals("test-container", ctx.getContainerId());
    }
}
