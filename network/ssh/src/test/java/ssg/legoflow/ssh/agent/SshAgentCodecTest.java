package ssg.legoflow.ssh.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SshAgentCodecTest {

    @Test
    void testEncodeDecodeFailure() {
        SshAgentMessage msg = new SshAgentMessage.Failure();
        byte[] encoded = SshAgentCodec.encode(msg);
        SshAgentMessage decoded = SshAgentCodec.decode(encoded);
        assertThat(decoded).isInstanceOf(SshAgentMessage.Failure.class);
        assertThat(decoded.type()).isEqualTo(SshAgentMessage.SSH_AGENT_FAILURE);
    }

    @Test
    void testEncodeDecodeSuccess() {
        SshAgentMessage msg = new SshAgentMessage.Success();
        byte[] encoded = SshAgentCodec.encode(msg);
        SshAgentMessage decoded = SshAgentCodec.decode(encoded);
        assertThat(decoded).isInstanceOf(SshAgentMessage.Success.class);
        assertThat(decoded.type()).isEqualTo(SshAgentMessage.SSH_AGENT_SUCCESS);
    }

    @Test
    void testEncodeDecodeRequestIdentities() {
        SshAgentMessage msg = new SshAgentMessage.RequestIdentities();
        byte[] encoded = SshAgentCodec.encode(msg);
        SshAgentMessage decoded = SshAgentCodec.decode(encoded);
        assertThat(decoded).isInstanceOf(SshAgentMessage.RequestIdentities.class);
    }

    @Test
    void testEncodeDecodeRemoveAllIdentities() {
        SshAgentMessage msg = new SshAgentMessage.RemoveAllIdentities();
        byte[] encoded = SshAgentCodec.encode(msg);
        SshAgentMessage decoded = SshAgentCodec.decode(encoded);
        assertThat(decoded).isInstanceOf(SshAgentMessage.RemoveAllIdentities.class);
    }

    @Test
    void testEncodeDecodeIdentitiesAnswer() {
        byte[] blob1 = new byte[]{1, 2, 3, 4};
        byte[] blob2 = new byte[]{5, 6, 7};
        var identities = List.of(
                new SshAgentMessage.IdentitiesAnswer.Identity(blob1, "key1"),
                new SshAgentMessage.IdentitiesAnswer.Identity(blob2, "key2")
        );
        SshAgentMessage msg = new SshAgentMessage.IdentitiesAnswer(identities);
        byte[] encoded = SshAgentCodec.encode(msg);
        SshAgentMessage decoded = SshAgentCodec.decode(encoded);

        assertThat(decoded).isInstanceOf(SshAgentMessage.IdentitiesAnswer.class);
        var answer = (SshAgentMessage.IdentitiesAnswer) decoded;
        assertThat(answer.identities()).hasSize(2);
        assertThat(answer.identities().get(0).publicKeyBlob()).isEqualTo(blob1);
        assertThat(answer.identities().get(0).comment()).isEqualTo("key1");
        assertThat(answer.identities().get(1).publicKeyBlob()).isEqualTo(blob2);
        assertThat(answer.identities().get(1).comment()).isEqualTo("key2");
    }

    @Test
    void testEncodeDecodeIdentitiesAnswerEmpty() {
        SshAgentMessage msg = new SshAgentMessage.IdentitiesAnswer(List.of());
        byte[] encoded = SshAgentCodec.encode(msg);
        var decoded = (SshAgentMessage.IdentitiesAnswer) SshAgentCodec.decode(encoded);
        assertThat(decoded.identities()).isEmpty();
    }

    @Test
    void testEncodeDecodeSignRequest() {
        byte[] blob = new byte[]{10, 20, 30};
        byte[] data = "data to sign".getBytes();
        SshAgentMessage msg = new SshAgentMessage.SignRequest(blob, data, 42);
        byte[] encoded = SshAgentCodec.encode(msg);
        var decoded = (SshAgentMessage.SignRequest) SshAgentCodec.decode(encoded);
        assertThat(decoded.publicKeyBlob()).isEqualTo(blob);
        assertThat(decoded.data()).isEqualTo(data);
        assertThat(decoded.flags()).isEqualTo(42);
    }

    @Test
    void testEncodeDecodeSignResponse() {
        byte[] sig = new byte[]{99, 100, 101, 102};
        SshAgentMessage msg = new SshAgentMessage.SignResponse(sig);
        byte[] encoded = SshAgentCodec.encode(msg);
        var decoded = (SshAgentMessage.SignResponse) SshAgentCodec.decode(encoded);
        assertThat(decoded.signature()).isEqualTo(sig);
    }

    @Test
    void testEncodeDecodeAddIdentity() {
        SshAgentMessage msg = new SshAgentMessage.AddIdentity(
                "ssh-ed25519", new byte[]{1, 2}, new byte[]{3, 4}, "my key");
        byte[] encoded = SshAgentCodec.encode(msg);
        var decoded = (SshAgentMessage.AddIdentity) SshAgentCodec.decode(encoded);
        assertThat(decoded.keyType()).isEqualTo("ssh-ed25519");
        assertThat(decoded.keyBlob()).isEqualTo(new byte[]{1, 2});
        assertThat(decoded.privateKey()).isEqualTo(new byte[]{3, 4});
        assertThat(decoded.comment()).isEqualTo("my key");
    }

    @Test
    void testEncodeDecodeRemoveIdentity() {
        byte[] blob = new byte[]{50, 60, 70};
        SshAgentMessage msg = new SshAgentMessage.RemoveIdentity(blob);
        byte[] encoded = SshAgentCodec.encode(msg);
        var decoded = (SshAgentMessage.RemoveIdentity) SshAgentCodec.decode(encoded);
        assertThat(decoded.publicKeyBlob()).isEqualTo(blob);
    }

    @Test
    void testDecodeUnknownTypeFails() {
        byte[] data = new byte[]{0, 0, 0, 1, (byte) 255};
        assertThatThrownBy(() -> SshAgentCodec.decode(data))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDecodePayloadRoundtrip() {
        byte[] blob = new byte[]{1, 2, 3};
        byte[] signData = "hello".getBytes();
        var original = new SshAgentMessage.SignRequest(blob, signData, 7);
        byte[] encoded = SshAgentCodec.encode(original);
        // Extract payload (skip 4-byte length prefix)
        byte[] payload = new byte[encoded.length - 4];
        System.arraycopy(encoded, 4, payload, 0, payload.length);
        SshAgentMessage decoded = SshAgentCodec.decodePayload(payload);
        assertThat(decoded).isInstanceOf(SshAgentMessage.SignRequest.class);
        var req = (SshAgentMessage.SignRequest) decoded;
        assertThat(req.publicKeyBlob()).isEqualTo(blob);
        assertThat(req.flags()).isEqualTo(7);
    }

    @Test
    void testMessageTypeConstants() {
        assertThat(new SshAgentMessage.Failure().type()).isEqualTo(5);
        assertThat(new SshAgentMessage.Success().type()).isEqualTo(6);
        assertThat(new SshAgentMessage.RequestIdentities().type()).isEqualTo(11);
        assertThat(new SshAgentMessage.IdentitiesAnswer(List.of()).type()).isEqualTo(12);
        assertThat(new SshAgentMessage.SignRequest(new byte[0], new byte[0], 0).type()).isEqualTo(13);
        assertThat(new SshAgentMessage.SignResponse(new byte[0]).type()).isEqualTo(14);
        assertThat(new SshAgentMessage.AddIdentity("", new byte[0], new byte[0], "").type()).isEqualTo(17);
        assertThat(new SshAgentMessage.RemoveIdentity(new byte[0]).type()).isEqualTo(18);
        assertThat(new SshAgentMessage.RemoveAllIdentities().type()).isEqualTo(19);
    }
}
