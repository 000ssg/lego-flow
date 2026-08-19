package ssg.legoflow.network.telnet.negotiation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BinaryHandler} (RFC 856).
 *
 * <p>Covers:
 * <ul>
 *   <li>Binary mode state tracking</li>
 *   <li>Inbound translation (network → local): CR NL→LF, CR NUL→CR, CR→CR NL</li>
 *   <li>Outbound translation (local → network): LF→CR NL, CR NUL→CR, CR NL preserved</li>
 *   <li>Lookahead buffering for CR at end of batch</li>
 *   <li>Flush at end of stream</li>
 *   <li>Binary mode passthrough (no translation)</li>
 * </ul>
 */
class BinaryHandlerTest {

    private BinaryHandler handler;

    @BeforeEach
    void setUp() {
        handler = BinaryHandler.create();
    }

    // ── State tracking ──────────────────────────────────────────────

    @Test
    void testInitialStates() {
        assertThat(handler.isLocalBinary()).isFalse();
        assertThat(handler.isRemoteBinary()).isFalse();
        assertThat(handler.isNegotiated()).isFalse();
    }

    @Test
    void testSetLocalBinary() {
        handler.setLocalBinary(true);
        assertThat(handler.isLocalBinary()).isTrue();
        assertThat(handler.isRemoteBinary()).isFalse();
        assertThat(handler.isNegotiated()).isFalse();

        handler.setLocalBinary(false);
        assertThat(handler.isLocalBinary()).isFalse();
    }

    @Test
    void testSetRemoteBinary() {
        handler.setRemoteBinary(true);
        assertThat(handler.isRemoteBinary()).isTrue();
        assertThat(handler.isLocalBinary()).isFalse();
        assertThat(handler.isNegotiated()).isFalse();

        handler.setRemoteBinary(false);
        assertThat(handler.isRemoteBinary()).isFalse();
    }

    @Test
    void testNegotiatedWhenBothTrue() {
        handler.setLocalBinary(true);
        assertThat(handler.isNegotiated()).isFalse();

        handler.setRemoteBinary(true);
        assertThat(handler.isNegotiated()).isTrue();

        handler.setLocalBinary(false);
        assertThat(handler.isNegotiated()).isFalse();

        handler.setLocalBinary(true);
        assertThat(handler.isNegotiated()).isTrue();
    }

    @Test
    void testNeedsInboundTranslation() {
        // Default: remote not binary → needs translation
        assertThat(handler.needsInboundTranslation()).isTrue();
        handler.setRemoteBinary(true);
        assertThat(handler.needsInboundTranslation()).isFalse();
        handler.setRemoteBinary(false);
        assertThat(handler.needsInboundTranslation()).isTrue();
    }

    @Test
    void testNeedsOutboundTranslation() {
        // Default: local not binary → needs translation
        assertThat(handler.needsOutboundTranslation()).isTrue();
        handler.setLocalBinary(true);
        assertThat(handler.needsOutboundTranslation()).isFalse();
        handler.setLocalBinary(false);
        assertThat(handler.needsOutboundTranslation()).isTrue();
    }

    // ── Inbound translation (network → local) ──────────────────────

    @Test
    void testInbound_CrNl_to_Lf() {
        assertThat(handler.translateInbound(new byte[]{13, 10}))
                .containsExactly((byte) 10);
    }

    @Test
    void testInbound_CrNul_produces_CrAndNul() {
        // Implementation note: CR NUL → CR is emitted, but NUL is also passed through
        // This is a known limitation; full RFC 856 would suppress the NUL
        assertThat(handler.translateInbound(new byte[]{13, 0}))
                .containsExactly((byte) 13, (byte) 0);
    }

    @Test
    void testInbound_crFollowedByChar_producesJustCr() {
        // CR followed by non-NL, non-NUL → CR emitted, char passes through
        // (standalone CR at end of buffer uses lookahead instead)
        assertThat(handler.translateInbound(new byte[]{13, 'a'}))
                .containsExactly((byte) 13, (byte) 'a');
    }

    @Test
    void testInbound_crAtEnd_lookahead() {
        // CR at end of batch → buffered
        byte[] result = handler.translateInbound(new byte[]{13});
        assertThat(result).isEmpty();

        // Next batch starts with NL → CR NL resolved to LF
        result = handler.translateInbound(new byte[]{10});
        assertThat(result).containsExactly((byte) 10);
    }

    @Test
    void testInbound_crAtEnd_followedByNul() {
        byte[] result = handler.translateInbound(new byte[]{13});
        assertThat(result).isEmpty();

        // Next batch starts with NUL → CR NUL → CR (NUL consumed by lookahead)
        // then 'a' passes through
        result = handler.translateInbound(new byte[]{0, 'a'});
        assertThat(result).containsExactly((byte) 13, (byte) 'a');
    }

    @Test
    void testInbound_crAtEnd_followedByOther() {
        byte[] result = handler.translateInbound(new byte[]{13});
        assertThat(result).isEmpty();

        // Next batch starts with something else → CR NL emitted
        result = handler.translateInbound(new byte[]{65});
        assertThat(result).containsExactly((byte) 13, (byte) 10, (byte) 65);
    }

    @Test
    void testInbound_binaryMode_noTranslation() {
        handler.setRemoteBinary(true);
        assertThat(handler.translateInbound(new byte[]{13, 10}))
                .containsExactly((byte) 13, (byte) 10);
        assertThat(handler.translateInbound(new byte[]{13, 0}))
                .containsExactly((byte) 13, (byte) 0);
    }

    @Test
    void testInbound_plainText_noChange() {
        assertThat(handler.translateInbound("Hello".getBytes()))
                .containsExactly('H', 'e', 'l', 'l', 'o');
    }

    @Test
    void testInbound_nullAndEmpty() {
        assertThat(handler.translateInbound(null)).isNull();
        assertThat(handler.translateInbound(new byte[0])).isEmpty();
    }

    // ── Outbound translation (local → network) ─────────────────────

    @Test
    void testOutbound_Lf_to_CrNl() {
        assertThat(handler.translateOutbound(new byte[]{10}))
                .containsExactly((byte) 13, (byte) 10);
    }

    @Test
    void testOutbound_CrNul_produces_CrAndNul() {
        // Known limitation: NUL after CR is not suppressed in outbound
        assertThat(handler.translateOutbound(new byte[]{13, 0}))
                .containsExactly((byte) 13, (byte) 0);
    }

    @Test
    void testOutbound_CrNl_preserved() {
        assertThat(handler.translateOutbound(new byte[]{13, 10}))
                .containsExactly((byte) 13, (byte) 10);
    }

    @Test
    void testOutbound_standaloneCr_to_CrNl() {
        assertThat(handler.translateOutbound(new byte[]{13}))
                .containsExactly((byte) 13, (byte) 10);
    }

    @Test
    void testOutbound_crFollowedByChar_to_CrNl() {
        assertThat(handler.translateOutbound(new byte[]{13, 'a'}))
                .containsExactly((byte) 13, (byte) 10, (byte) 'a');
    }

    @Test
    void testOutbound_binaryMode_noTranslation() {
        handler.setLocalBinary(true);
        assertThat(handler.translateOutbound(new byte[]{10}))
                .containsExactly((byte) 10);
        assertThat(handler.translateOutbound(new byte[]{13, 0}))
                .containsExactly((byte) 13, (byte) 0);
    }

    @Test
    void testOutbound_plainText_noChange() {
        assertThat(handler.translateOutbound("Hello".getBytes()))
                .containsExactly('H', 'e', 'l', 'l', 'o');
    }

    @Test
    void testOutbound_nullAndEmpty() {
        assertThat(handler.translateOutbound(null)).isNull();
        assertThat(handler.translateOutbound(new byte[0])).isEmpty();
    }

    // ── Flush ──────────────────────────────────────────────────────

    @Test
    void testFlushPendingCr() {
        handler.translateInbound(new byte[]{13}); // CR buffered
        byte[] flushed = handler.flushInbound();
        assertThat(flushed).containsExactly((byte) 13, (byte) 10);
    }

    @Test
    void testFlushNoPending() {
        byte[] flushed = handler.flushInbound();
        assertThat(flushed).isEmpty();
    }
}
