package ssg.legoflow.ssh.hostkey;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class SshPublicKeyTest {

    @Test
    void testKeyTypeFromBlob() {
        SshKeyPair kp = SshKeyPair.generate(new Ed25519());
        String type = SshPublicKey.keyTypeFromBlob(kp.publicKeyBlob());
        assertThat(type).isEqualTo("ssh-ed25519");
    }

    @Test
    void testAuthorizedKeysFormat() {
        SshKeyPair kp = SshKeyPair.generate(new Ed25519());
        SshPublicKey pub = kp.publicKey();
        String line = pub.toAuthorizedKeysLine();
        assertThat(line).startsWith("ssh-ed25519 ");
    }

    @Test
    void testParseAuthorizedKeysLine() {
        SshKeyPair kp = SshKeyPair.generate(new Ed25519());
        String line = kp.publicKey().toAuthorizedKeysLine() + " test@example.com";
        SshPublicKey parsed = SshPublicKey.parse(line);
        assertThat(parsed.keyType()).isEqualTo("ssh-ed25519");
        assertThat(parsed.comment()).isEqualTo("test@example.com");
    }

    @Test
    void testFingerprint() {
        SshKeyPair kp = SshKeyPair.generate(new Ed25519());
        String fp = kp.publicKey().fingerprint();
        assertThat(fp).startsWith("SHA256:");
    }

    @Test
    void testKeyBlob() {
        SshKeyPair kp = SshKeyPair.generate(new Ed25519());
        SshPublicKey pub = new SshPublicKey("ssh-ed25519", kp.publicKeyBlob(), null);
        assertThat(pub.keyBlob()).isEqualTo(kp.publicKeyBlob());
    }

    @Test
    void testParseInvalidFormat() {
        assertThatThrownBy(() -> SshPublicKey.parse("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testToString() {
        SshKeyPair kp = SshKeyPair.generate(new Ed25519());
        String str = kp.publicKey().toString();
        assertThat(str).contains("ssh-ed25519");
        assertThat(str).contains("SHA256:");
    }

    @Test
    void testRoundTripParse() {
        SshKeyPair kp = SshKeyPair.generate(new Ed25519());
        SshPublicKey original = kp.publicKey();
        String line = original.toAuthorizedKeysLine();
        SshPublicKey parsed = SshPublicKey.parse(line);
        assertThat(parsed.keyType()).isEqualTo(original.keyType());
        assertThat(parsed.keyBlob()).isEqualTo(original.keyBlob());
    }
}
