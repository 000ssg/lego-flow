package ssg.legoflow.messaging.amqp.types;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Descriptors} — AMQP descriptor code constants.
 */
class DescriptorsTest {

    @Test void testPerformativeDescriptors() {
        assertThat(Descriptors.OPEN).isEqualTo(0x10L);
        assertThat(Descriptors.BEGIN).isEqualTo(0x11L);
        assertThat(Descriptors.ATTACH).isEqualTo(0x12L);
        assertThat(Descriptors.FLOW).isEqualTo(0x13L);
        assertThat(Descriptors.TRANSFER).isEqualTo(0x14L);
        assertThat(Descriptors.DISPOSITION).isEqualTo(0x15L);
        assertThat(Descriptors.DETACH).isEqualTo(0x16L);
        assertThat(Descriptors.END).isEqualTo(0x17L);
        assertThat(Descriptors.CLOSE).isEqualTo(0x18L);
    }

    @Test void testSaslDescriptors() {
        assertThat(Descriptors.SASL_MECHANISMS).isEqualTo(0x40L);
        assertThat(Descriptors.SASL_INIT).isEqualTo(0x41L);
        assertThat(Descriptors.SASL_CHALLENGE).isEqualTo(0x42L);
        assertThat(Descriptors.SASL_RESPONSE).isEqualTo(0x43L);
        assertThat(Descriptors.SASL_OUTCOME).isEqualTo(0x44L);
    }

    @Test void testMessageSectionDescriptors() {
        assertThat(Descriptors.HEADER).isEqualTo(0x70L);
        assertThat(Descriptors.DELIVERY_ANNOTATIONS).isEqualTo(0x71L);
        assertThat(Descriptors.MESSAGE_ANNOTATIONS).isEqualTo(0x72L);
        assertThat(Descriptors.PROPERTIES).isEqualTo(0x73L);
        assertThat(Descriptors.APPLICATION_PROPERTIES).isEqualTo(0x74L);
        assertThat(Descriptors.DATA).isEqualTo(0x75L);
        assertThat(Descriptors.AMQP_SEQUENCE).isEqualTo(0x76L);
        assertThat(Descriptors.AMQP_VALUE).isEqualTo(0x77L);
        assertThat(Descriptors.FOOTER).isEqualTo(0x78L);
    }

    @Test void testDeliveryStateDescriptors() {
        assertThat(Descriptors.RECEIVED).isEqualTo(0x23L);
        assertThat(Descriptors.ACCEPTED).isEqualTo(0x24L);
        assertThat(Descriptors.REJECTED).isEqualTo(0x25L);
        assertThat(Descriptors.RELEASED).isEqualTo(0x26L);
        assertThat(Descriptors.MODIFIED).isEqualTo(0x27L);
    }

    @Test void testAddressingDescriptors() {
        assertThat(Descriptors.SOURCE).isEqualTo(0x28L);
        assertThat(Descriptors.TARGET).isEqualTo(0x29L);
    }

    @Test void testTransactionDescriptors() {
        assertThat(Descriptors.COORDINATOR).isEqualTo(0x30L);
        assertThat(Descriptors.DECLARE).isEqualTo(0x31L);
        assertThat(Descriptors.DISCHARGE).isEqualTo(0x32L);
        assertThat(Descriptors.DECLARED).isEqualTo(0x33L);
        assertThat(Descriptors.TRANSACTIONAL_STATE).isEqualTo(0x34L);
    }

    @Test void testErrorDescriptor() {
        assertThat(Descriptors.ERROR).isEqualTo(0x1DL);
    }
}
