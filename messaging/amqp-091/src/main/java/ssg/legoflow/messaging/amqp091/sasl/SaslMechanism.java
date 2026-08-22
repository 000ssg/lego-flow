package ssg.legoflow.messaging.amqp091.sasl;

/**
 * SASL authentication mechanism for AMQP 0-9-1.
 *
 * @since 0.2.0
 */
public interface SaslMechanism {
    String name();
    byte[] initialResponse();
    byte[] respond(byte[] challenge);
}
