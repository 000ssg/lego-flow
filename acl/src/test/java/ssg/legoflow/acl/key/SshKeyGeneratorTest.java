package ssg.legoflow.acl.key;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.security.*;

import static org.assertj.core.api.Assertions.*;

class SshKeyGeneratorTest {

    @Test void generateRsaKeyPair() {
        var pair = SshKeyGenerator.generate("RSA");
        assertThat(pair.algorithm()).isEqualTo("RSA");
        assertThat(pair.wireFormat()).isEqualTo("ssh-rsa");
        assertThat(pair.keyPair()).isNotNull();
    }

    @Test void generateEd25519KeyPair() {
        var pair = SshKeyGenerator.generate("Ed25519");
        assertThat(pair.algorithm()).isEqualTo("Ed25519");
        assertThat(pair.wireFormat()).isEqualTo("ssh-ed25519");
        assertThat(pair.keyPair()).isNotNull();
    }

    @Test void generateEcDsaKeyPair() {
        var pair = SshKeyGenerator.generate("ECDSA");
        assertThat(pair.algorithm()).isEqualTo("ECDSA");
        assertThat(pair.wireFormat()).isEqualTo("ecdsa-sha2-nistp256");
    }

    @Test void publicKeyOpenSshFormat() {
        var rsa = SshKeyGenerator.generate("RSA");
        var pub = rsa.publicKeyOpenSsh();
        assertThat(pub).startsWith("ssh-rsa ");
        var parts = pub.split(" ");
        assertThat(parts).hasSize(2);
        var blob = java.util.Base64.getDecoder().decode(parts[1]);
        assertThat(blob).isNotEmpty();
    }

    @Test void ed25519PublicKeyFormat() {
        var key = SshKeyGenerator.generate("Ed25519");
        var pub = key.publicKeyOpenSsh();
        assertThat(pub).startsWith("ssh-ed25519 ");
        var parts = pub.split(" ");
        assertThat(parts).hasSize(2);
    }

    @Test void writeKeysToFile() throws Exception {
        var tmpDir = Files.createTempDirectory("ssh-test");
        var pair = SshKeyGenerator.generate("RSA");
        pair.writePrivateKey(tmpDir.resolve("id_rsa"));
        pair.writePublicKey(tmpDir.resolve("id_rsa.pub"));
        assertThat(tmpDir.resolve("id_rsa")).exists();
        assertThat(tmpDir.resolve("id_rsa.pub")).exists();
        assertThat(Files.readString(tmpDir.resolve("id_rsa"))).contains("BEGIN OPENSSH PRIVATE KEY");
        assertThat(Files.readString(tmpDir.resolve("id_rsa.pub"))).startsWith("ssh-rsa ");
    }

    @Test void privateKeyPemFormat() {
        var pair = SshKeyGenerator.generate("RSA");
        var pem = pair.privateKeyPem();
        assertThat(pem).contains("BEGIN OPENSSH PRIVATE KEY");
        assertThat(pem).contains("END OPENSSH PRIVATE KEY");
    }

    @Test void keyPairCanSignAndVerify() throws Exception {
        var pair = SshKeyGenerator.generate("RSA");
        var sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(pair.keyPair().getPrivate());
        sig.update("test".getBytes());
        var signature = sig.sign();
        sig.initVerify(pair.keyPair().getPublic());
        sig.update("test".getBytes());
        assertThat(sig.verify(signature)).isTrue();
    }

    @Test void unauthorizedAlgorithm() {
        assertThatThrownBy(() -> SshKeyGenerator.generate("invalid"))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test void generateMultipleKeysDifferentSizes() {
        for (int size : new int[]{1024, 2048, 4096}) {
            var pair = SshKeyGenerator.generate("RSA", size);
            var pub = pair.publicKeyOpenSsh();
            assertThat(pub).isNotEmpty();
        }
    }
}
