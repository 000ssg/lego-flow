package ssg.legoflow.messaging.mqtt.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** QoS enum coverage. */
class QoSTest {

    @Test
    void values() {
        assertEquals(0, QoS.AT_MOST_ONCE.value());
        assertEquals(1, QoS.AT_LEAST_ONCE.value());
        assertEquals(2, QoS.EXACTLY_ONCE.value());
    }

    @Test
    void fromValue() {
        assertEquals(QoS.AT_MOST_ONCE, QoS.fromValue(0));
        assertEquals(QoS.AT_LEAST_ONCE, QoS.fromValue(1));
        assertEquals(QoS.EXACTLY_ONCE, QoS.fromValue(2));
    }

    @Test
    void fromValueInvalid() {
        assertThrows(IllegalArgumentException.class, () -> QoS.fromValue(3));
        assertThrows(IllegalArgumentException.class, () -> QoS.fromValue(-1));
    }

    @Test
    void ordinal() {
        assertEquals(0, QoS.AT_MOST_ONCE.ordinal());
        assertEquals(1, QoS.AT_LEAST_ONCE.ordinal());
        assertEquals(2, QoS.EXACTLY_ONCE.ordinal());
    }

    @Test
    void allValues() {
        assertEquals(3, QoS.values().length);
    }
}
