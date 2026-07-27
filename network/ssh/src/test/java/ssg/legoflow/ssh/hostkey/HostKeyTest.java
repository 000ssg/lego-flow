package ssg.legoflow.ssh.hostkey;

import org.junit.jupiter.api.Test;
import java.security.KeyPair;
import static org.assertj.core.api.Assertions.*;

class HostKeyTest {

    @Test
    void testEd25519Name() {
        assertThat(new Ed25519().name()).isEqualTo("ssh-ed25519");
    }

    @Test
    void testEd25519GenerateKeyPair() {
        Ed25519 alg = new Ed25519();
        KeyPair kp = alg.generateKeyPair();
        assertThat(kp).isNotNull();
        assertThat(kp.getPublic()).isNotNull();
        assertThat(kp.getPrivate()).isNotNull();
    }

    @Test
    void testEd25519SignVerify() {
        Ed25519 alg = new Ed25519();
        KeyPair kp = alg.generateKeyPair();
        byte[] data = "test data to sign".getBytes();
        byte[] sig = alg.sign(kp, data);
        byte[] pubBlob = alg.encodePublicKey(kp);
        assertThat(alg.verify(pubBlob, data, sig)).isTrue();
    }

    @Test
    void testEd25519VerifyFailWrongData() {
        Ed25519 alg = new Ed25519();
        KeyPair kp = alg.generateKeyPair();
        byte[] sig = alg.sign(kp, "original".getBytes());
        byte[] pubBlob = alg.encodePublicKey(kp);
        assertThat(alg.verify(pubBlob, "tampered".getBytes(), sig)).isFalse();
    }

    @Test
    void testEd25519PublicKeyEncoding() {
        Ed25519 alg = new Ed25519();
        KeyPair kp = alg.generateKeyPair();
        byte[] blob = alg.encodePublicKey(kp);
        String type = SshPublicKey.keyTypeFromBlob(blob);
        assertThat(type).isEqualTo("ssh-ed25519");
    }

    @Test
    void testRsaSha256Name() {
        assertThat(new RsaSha256().name()).isEqualTo("rsa-sha2-256");
    }

    @Test
    void testRsaSha256SignVerify() {
        RsaSha256 alg = new RsaSha256();
        KeyPair kp = alg.generateKeyPair();
        byte[] data = "test rsa data".getBytes();
        byte[] sig = alg.sign(kp, data);
        byte[] pubBlob = alg.encodePublicKey(kp);
        assertThat(alg.verify(pubBlob, data, sig)).isTrue();
    }

    @Test
    void testRsaSha512Name() {
        assertThat(new RsaSha512().name()).isEqualTo("rsa-sha2-512");
    }

    @Test
    void testRsaSha512SignVerify() {
        RsaSha512 alg = new RsaSha512();
        KeyPair kp = alg.generateKeyPair();
        byte[] data = "test rsa-512 data".getBytes();
        byte[] sig = alg.sign(kp, data);
        byte[] pubBlob = alg.encodePublicKey(kp);
        assertThat(alg.verify(pubBlob, data, sig)).isTrue();
    }

    @Test
    void testEcdsaNistp256Name() {
        assertThat(new EcdsaSha2Nistp256().name()).isEqualTo("ecdsa-sha2-nistp256");
    }

    @Test
    void testEcdsaNistp256SignVerify() {
        EcdsaSha2Nistp256 alg = new EcdsaSha2Nistp256();
        KeyPair kp = alg.generateKeyPair();
        byte[] data = "test ecdsa data".getBytes();
        byte[] sig = alg.sign(kp, data);
        byte[] pubBlob = alg.encodePublicKey(kp);
        assertThat(alg.verify(pubBlob, data, sig)).isTrue();
    }

    @Test
    void testEcdsaNistp384SignVerify() {
        EcdsaSha2Nistp384 alg = new EcdsaSha2Nistp384();
        KeyPair kp = alg.generateKeyPair();
        byte[] data = "test ecdsa-384 data".getBytes();
        byte[] sig = alg.sign(kp, data);
        byte[] pubBlob = alg.encodePublicKey(kp);
        assertThat(alg.verify(pubBlob, data, sig)).isTrue();
    }

    @Test
    void testHostKeyFactoryCreate() {
        assertThat(HostKeyFactory.create("ssh-ed25519")).isInstanceOf(Ed25519.class);
        assertThat(HostKeyFactory.create("rsa-sha2-256")).isInstanceOf(RsaSha256.class);
        assertThat(HostKeyFactory.create("ecdsa-sha2-nistp256")).isInstanceOf(EcdsaSha2Nistp256.class);
    }

    @Test
    void testHostKeyFactoryUnsupported() {
        assertThatThrownBy(() -> HostKeyFactory.create("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testHostKeyFactoryIsSupported() {
        assertThat(HostKeyFactory.isSupported("ssh-ed25519")).isTrue();
        assertThat(HostKeyFactory.isSupported("dsa")).isFalse();
    }

    @Test
    void testSshKeyPairGenerate() {
        SshKeyPair kp = SshKeyPair.generate(new Ed25519());
        assertThat(kp.algorithm()).isEqualTo("ssh-ed25519");
        assertThat(kp.publicKeyBlob()).isNotEmpty();
    }

    @Test
    void testSshKeyPairSign() {
        SshKeyPair kp = SshKeyPair.generate(new Ed25519());
        byte[] sig = kp.sign("test".getBytes());
        assertThat(sig).isNotEmpty();
    }

    @Test
    void testSshKeyPairPublicKey() {
        SshKeyPair kp = SshKeyPair.generate(new Ed25519());
        SshPublicKey pub = kp.publicKey();
        assertThat(pub.keyType()).isEqualTo("ssh-ed25519");
    }
}
