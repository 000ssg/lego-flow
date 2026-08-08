package ssg.legoflow.messaging.amqp.sasl;

/**
 * SASL EXTERNAL mechanism — authentication via external means (e.g. TLS client certificate).
 *
 * @since 0.1.0
 */
public final class ExternalMechanism implements SaslMechanism {

    @Override
    public String name() {
        return "EXTERNAL";
    }

    @Override
    public byte[] initialResponse() {
        return new byte[0];
    }

    @Override
    public byte[] respond(byte[] challenge) {
        return new byte[0];
    }
}
