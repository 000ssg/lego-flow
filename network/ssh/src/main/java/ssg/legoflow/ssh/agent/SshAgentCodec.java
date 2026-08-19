package ssg.legoflow.ssh.agent;

import ssg.legoflow.ssh.transport.SshTransportCodec;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
/**
 * Encoder and decoder for SSH agent protocol messages.
 *
 * <p>Agent messages use a 4-byte length prefix followed by a message type byte and payload,
 * as defined in draft-miller-ssh-agent.
 *
 * @since 0.1.0
 */
public final class SshAgentCodec {

    private SshAgentCodec() {}

    /**
     * Encodes an agent message to wire format (4-byte length + type + payload).
     *
     * @param message the agent message to encode
     * @return the encoded bytes including length prefix
     */
    public static byte[] encode(SshAgentMessage message) {
        byte[] payload = encodePayload(message);
        ByteBuffer result = ByteBuffer.allocate(4 + payload.length);
        result.putInt(payload.length);
        result.put(payload);
        return result.array();
    }

    /**
     * Decodes an agent message from wire format.
     *
     * @param data the raw bytes including the 4-byte length prefix
     * @return the decoded agent message
     * @throws IllegalArgumentException if the message is malformed
     */
    public static SshAgentMessage decode(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        int length = buf.getInt();
        if (length < 1 || length > data.length - 4) {
            throw new IllegalArgumentException("Invalid agent message length: " + length);
        }
        int type = buf.get() & 0xFF;
        return decodeByType(type, buf);
    }

    /**
     * Decodes an agent message from payload bytes (without length prefix).
     *
     * @param payload the payload bytes starting with message type
     * @return the decoded agent message
     */
    public static SshAgentMessage decodePayload(byte[] payload) {
        ByteBuffer buf = ByteBuffer.wrap(payload);
        int type = buf.get() & 0xFF;
        return decodeByType(type, buf);
    }

    private static byte[] encodePayload(SshAgentMessage message) {
        return switch (message) {
            case SshAgentMessage.Failure _ -> new byte[]{(byte) SshAgentMessage.SSH_AGENT_FAILURE};
            case SshAgentMessage.Success _ -> new byte[]{(byte) SshAgentMessage.SSH_AGENT_SUCCESS};
            case SshAgentMessage.RequestIdentities _ ->
                    new byte[]{(byte) SshAgentMessage.SSH_AGENTC_REQUEST_IDENTITIES};
            case SshAgentMessage.RemoveAllIdentities _ ->
                    new byte[]{(byte) SshAgentMessage.SSH_AGENTC_REMOVE_ALL_IDENTITIES};
            case SshAgentMessage.IdentitiesAnswer answer -> encodeIdentitiesAnswer(answer);
            case SshAgentMessage.SignRequest req -> encodeSignRequest(req);
            case SshAgentMessage.SignResponse resp -> encodeSignResponse(resp);
            case SshAgentMessage.AddIdentity add -> encodeAddIdentity(add);
            case SshAgentMessage.RemoveIdentity rem -> encodeRemoveIdentity(rem);
        };
    }

    private static byte[] encodeIdentitiesAnswer(SshAgentMessage.IdentitiesAnswer answer) {
        int size = 1 + 4; // type + count
        for (var id : answer.identities()) {
            size += 4 + id.publicKeyBlob().length + 4 + id.comment().length();
        }
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.put((byte) SshAgentMessage.SSH_AGENT_IDENTITIES_ANSWER);
        buf.putInt(answer.identities().size());
        for (var id : answer.identities()) {
            SshTransportCodec.writeBinary(buf, id.publicKeyBlob());
            SshTransportCodec.writeString(buf, id.comment());
        }
        return buf.array();
    }

    private static byte[] encodeSignRequest(SshAgentMessage.SignRequest req) {
        ByteBuffer buf = ByteBuffer.allocate(1 + 4 + req.publicKeyBlob().length
                + 4 + req.data().length + 4);
        buf.put((byte) SshAgentMessage.SSH_AGENTC_SIGN_REQUEST);
        SshTransportCodec.writeBinary(buf, req.publicKeyBlob());
        SshTransportCodec.writeBinary(buf, req.data());
        buf.putInt(req.flags());
        return buf.array();
    }

    private static byte[] encodeSignResponse(SshAgentMessage.SignResponse resp) {
        ByteBuffer buf = ByteBuffer.allocate(1 + 4 + resp.signature().length);
        buf.put((byte) SshAgentMessage.SSH_AGENT_SIGN_RESPONSE);
        SshTransportCodec.writeBinary(buf, resp.signature());
        return buf.array();
    }

    private static byte[] encodeAddIdentity(SshAgentMessage.AddIdentity add) {
        ByteBuffer buf = ByteBuffer.allocate(1 + 4 + add.keyType().length()
                + 4 + add.keyBlob().length + 4 + add.privateKey().length
                + 4 + add.comment().length());
        buf.put((byte) SshAgentMessage.SSH_AGENTC_ADD_IDENTITY);
        SshTransportCodec.writeString(buf, add.keyType());
        SshTransportCodec.writeBinary(buf, add.keyBlob());
        SshTransportCodec.writeBinary(buf, add.privateKey());
        SshTransportCodec.writeString(buf, add.comment());
        return buf.array();
    }

    private static byte[] encodeRemoveIdentity(SshAgentMessage.RemoveIdentity rem) {
        ByteBuffer buf = ByteBuffer.allocate(1 + 4 + rem.publicKeyBlob().length);
        buf.put((byte) SshAgentMessage.SSH_AGENTC_REMOVE_IDENTITY);
        SshTransportCodec.writeBinary(buf, rem.publicKeyBlob());
        return buf.array();
    }

    private static SshAgentMessage decodeByType(int type, ByteBuffer buf) {
        return switch (type) {
            case SshAgentMessage.SSH_AGENT_FAILURE -> new SshAgentMessage.Failure();
            case SshAgentMessage.SSH_AGENT_SUCCESS -> new SshAgentMessage.Success();
            case SshAgentMessage.SSH_AGENTC_REQUEST_IDENTITIES -> new SshAgentMessage.RequestIdentities();
            case SshAgentMessage.SSH_AGENTC_REMOVE_ALL_IDENTITIES -> new SshAgentMessage.RemoveAllIdentities();
            case SshAgentMessage.SSH_AGENT_IDENTITIES_ANSWER -> decodeIdentitiesAnswer(buf);
            case SshAgentMessage.SSH_AGENTC_SIGN_REQUEST -> decodeSignRequest(buf);
            case SshAgentMessage.SSH_AGENT_SIGN_RESPONSE -> decodeSignResponse(buf);
            case SshAgentMessage.SSH_AGENTC_ADD_IDENTITY -> decodeAddIdentity(buf);
            case SshAgentMessage.SSH_AGENTC_REMOVE_IDENTITY -> decodeRemoveIdentity(buf);
            default -> throw new IllegalArgumentException("Unknown agent message type: " + type);
        };
    }

    private static SshAgentMessage.IdentitiesAnswer decodeIdentitiesAnswer(ByteBuffer buf) {
        int count = buf.getInt();
        List<SshAgentMessage.IdentitiesAnswer.Identity> identities = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte[] blob = SshTransportCodec.readBinary(buf);
            String comment = SshTransportCodec.readString(buf);
            identities.add(new SshAgentMessage.IdentitiesAnswer.Identity(blob, comment));
        }
        return new SshAgentMessage.IdentitiesAnswer(List.copyOf(identities));
    }

    private static SshAgentMessage.SignRequest decodeSignRequest(ByteBuffer buf) {
        byte[] blob = SshTransportCodec.readBinary(buf);
        byte[] data = SshTransportCodec.readBinary(buf);
        int flags = buf.getInt();
        return new SshAgentMessage.SignRequest(blob, data, flags);
    }

    private static SshAgentMessage.SignResponse decodeSignResponse(ByteBuffer buf) {
        byte[] sig = SshTransportCodec.readBinary(buf);
        return new SshAgentMessage.SignResponse(sig);
    }

    private static SshAgentMessage.AddIdentity decodeAddIdentity(ByteBuffer buf) {
        String keyType = SshTransportCodec.readString(buf);
        byte[] keyBlob = SshTransportCodec.readBinary(buf);
        byte[] privateKey = SshTransportCodec.readBinary(buf);
        String comment = SshTransportCodec.readString(buf);
        return new SshAgentMessage.AddIdentity(keyType, keyBlob, privateKey, comment);
    }

    private static SshAgentMessage.RemoveIdentity decodeRemoveIdentity(ByteBuffer buf) {
        byte[] blob = SshTransportCodec.readBinary(buf);
        return new SshAgentMessage.RemoveIdentity(blob);
    }
}
