package ssg.legoflow.messaging.amqp.sasl;

/**
 * SASL ANONYMOUS mechanism — no credentials required.
 *
 * @since 1.0.0
 */
public final class AnonymousMechanism implements SaslMechanism {

    @Override
    public String name() {
        return "ANONYMOUS";
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
