package ssg.legoflow.ssh.auth;

import ssg.legoflow.ssh.transport.SshTransportCodec;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * SSH_MSG_USERAUTH_BANNER (type 53) for displaying messages to the user
 * before authentication completes.
 *
 * @param message  the banner message text
 * @param language the language tag (RFC 3066)
 * @since 1.0.0
 */
public record AuthBanner(String message, String language) {

    /**
     * Creates a new auth banner.
     */
    public AuthBanner {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(language, "language");
    }

    /**
     * Creates a banner with default (empty) language tag.
     *
     * @param message the banner message
     * @return the banner
     */
    public static AuthBanner of(String message) {
        return new AuthBanner(message, "");
    }

    /**
     * Encodes this banner to SSH wire format.
     *
     * @return the encoded payload
     */
    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(4 + message.length() + 4 + language.length() + 1);
        buf.put((byte) 53); // SSH_MSG_USERAUTH_BANNER
        SshTransportCodec.writeString(buf, message);
        SshTransportCodec.writeString(buf, language);
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Decodes a banner from SSH wire format.
     *
     * @param payload the payload (starting after message type byte)
     * @return the decoded banner
     */
    public static AuthBanner decode(byte[] payload) {
        ByteBuffer buf = ByteBuffer.wrap(payload);
        if (buf.get() != 53) {
            throw new IllegalArgumentException("Not a USERAUTH_BANNER message");
        }
        String message = SshTransportCodec.readString(buf);
        String language = SshTransportCodec.readString(buf);
        return new AuthBanner(message, language);
    }
}
