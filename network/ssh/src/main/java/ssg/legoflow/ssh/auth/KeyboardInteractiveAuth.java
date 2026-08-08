package ssg.legoflow.ssh.auth;

import ssg.legoflow.ssh.transport.SshTransportCodec;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Keyboard-interactive authentication per RFC 4256.
 *
 * @since 0.1.0
 */
public final class KeyboardInteractiveAuth implements AuthMethod {

    private final Function<List<String>, List<String>> responseProvider;

    /**
     * Creates a keyboard-interactive auth method.
     *
     * @param responseProvider function that takes prompt list and returns responses
     */
    public KeyboardInteractiveAuth(Function<List<String>, List<String>> responseProvider) {
        this.responseProvider = Objects.requireNonNull(responseProvider, "responseProvider");
    }

    @Override public String methodName() { return "keyboard-interactive"; }
    @Override public boolean isInteractive() { return true; }

    @Override
    public byte[] encodeRequest(String username, String serviceName) {
        ByteBuffer buf = ByteBuffer.allocate(512);
        buf.put((byte) 50); // SSH_MSG_USERAUTH_REQUEST
        SshTransportCodec.writeString(buf, username);
        SshTransportCodec.writeString(buf, serviceName);
        SshTransportCodec.writeString(buf, "keyboard-interactive");
        SshTransportCodec.writeString(buf, ""); // language tag
        SshTransportCodec.writeString(buf, ""); // submethods
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Encodes responses to an info request.
     *
     * @param prompts the prompts received from the server
     * @return the encoded response payload
     */
    public byte[] encodeResponses(List<String> prompts) {
        List<String> responses = responseProvider.apply(prompts);
        ByteBuffer buf = ByteBuffer.allocate(4096);
        buf.put((byte) 61); // SSH_MSG_USERAUTH_INFO_RESPONSE
        buf.putInt(responses.size());
        for (String response : responses) {
            SshTransportCodec.writeString(buf, response);
        }
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Returns the response provider.
     *
     * @return the response provider function
     */
    public Function<List<String>, List<String>> responseProvider() {
        return responseProvider;
    }
}
