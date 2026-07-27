package ssg.legoflow.ssh.kex;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class KexInitTest {

    @Test
    void testDefaultKexInitHasCookie() {
        KexInit ki = KexInit.defaultKexInit();
        assertThat(ki.cookie()).hasSize(16);
    }

    @Test
    void testDefaultKexInitAlgorithms() {
        KexInit ki = KexInit.defaultKexInit();
        assertThat(ki.kexAlgorithms()).contains("curve25519-sha256", "diffie-hellman-group14-sha256");
        assertThat(ki.serverHostKeyAlgorithms()).contains("ssh-ed25519");
        assertThat(ki.encryptionAlgorithmsClientToServer()).contains("aes256-ctr");
        assertThat(ki.macAlgorithmsClientToServer()).contains("hmac-sha2-256");
        assertThat(ki.compressionAlgorithmsClientToServer()).contains("none");
    }

    @Test
    void testEncodeDecodeRoundTrip() {
        KexInit original = KexInit.defaultKexInit();
        byte[] encoded = original.encode();
        KexInit decoded = KexInit.decode(encoded);
        assertThat(decoded.kexAlgorithms()).isEqualTo(original.kexAlgorithms());
        assertThat(decoded.serverHostKeyAlgorithms()).isEqualTo(original.serverHostKeyAlgorithms());
        assertThat(decoded.encryptionAlgorithmsClientToServer())
                .isEqualTo(original.encryptionAlgorithmsClientToServer());
        assertThat(decoded.firstKexPacketFollows()).isEqualTo(original.firstKexPacketFollows());
    }

    @Test
    void testMessageType() {
        KexInit ki = KexInit.defaultKexInit();
        assertThat(ki.messageType()).isEqualTo((byte) 20);
    }

    @Test
    void testDecodeInvalidType() {
        byte[] invalid = new byte[100];
        invalid[0] = 99; // wrong type
        assertThatThrownBy(() -> KexInit.decode(invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testCustomAlgorithms() {
        KexInit ki = new KexInit(
                new byte[16],
                List.of("diffie-hellman-group14-sha256"),
                List.of("rsa-sha2-256"),
                List.of("aes128-ctr"),
                List.of("aes128-ctr"),
                List.of("hmac-sha2-256"),
                List.of("hmac-sha2-256"),
                List.of("none"),
                List.of("none"),
                List.of(), List.of(), false
        );
        byte[] encoded = ki.encode();
        KexInit decoded = KexInit.decode(encoded);
        assertThat(decoded.kexAlgorithms()).containsExactly("diffie-hellman-group14-sha256");
    }

    @Test
    void testFirstKexPacketFollows() {
        KexInit ki = new KexInit(new byte[16],
                List.of("test"), List.of("test"), List.of("test"), List.of("test"),
                List.of("test"), List.of("test"), List.of("test"), List.of("test"),
                List.of(), List.of(), true);
        byte[] encoded = ki.encode();
        KexInit decoded = KexInit.decode(encoded);
        assertThat(decoded.firstKexPacketFollows()).isTrue();
    }

    @Test
    void testEmptyLanguages() {
        KexInit ki = KexInit.defaultKexInit();
        assertThat(ki.languagesClientToServer()).isEmpty();
        assertThat(ki.languagesServerToClient()).isEmpty();
    }
}
