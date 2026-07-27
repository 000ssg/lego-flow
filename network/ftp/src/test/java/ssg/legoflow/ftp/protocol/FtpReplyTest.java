package ssg.legoflow.ftp.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link FtpReply}.
 */
class FtpReplyTest {

    @Test
    void testSingleLineReply() {
        var reply = new FtpReply(220, "Welcome");
        assertThat(reply.code()).isEqualTo(220);
        assertThat(reply.text()).isEqualTo("Welcome");
        assertThat(reply.isMultiLine()).isFalse();
        assertThat(reply.lines()).hasSize(1);
    }

    @Test
    void testMultiLineReply() {
        var reply = new FtpReply(211, List.of("Features:", " SIZE", " MDTM", "End"));
        assertThat(reply.code()).isEqualTo(211);
        assertThat(reply.isMultiLine()).isTrue();
        assertThat(reply.lines()).hasSize(4);
        assertThat(reply.text()).isEqualTo("Features:");
    }

    @Test
    void testOfReplyCode() {
        var reply = FtpReply.of(FtpReplyCode.SERVICE_READY);
        assertThat(reply.code()).isEqualTo(220);
        assertThat(reply.text()).isEqualTo("Service ready for new user");
    }

    @Test
    void testOfReplyCodeWithCustomText() {
        var reply = FtpReply.of(FtpReplyCode.USER_LOGGED_IN, "Hello user!");
        assertThat(reply.code()).isEqualTo(230);
        assertThat(reply.text()).isEqualTo("Hello user!");
    }

    @Test
    void testIsSuccess() {
        assertThat(new FtpReply(200, "OK").isSuccess()).isTrue();
        assertThat(new FtpReply(250, "OK").isSuccess()).isTrue();
        assertThat(new FtpReply(150, "OK").isSuccess()).isFalse();
        assertThat(new FtpReply(550, "Error").isSuccess()).isFalse();
    }

    @Test
    void testIsIntermediate() {
        assertThat(new FtpReply(331, "Need password").isIntermediate()).isTrue();
        assertThat(new FtpReply(350, "Pending").isIntermediate()).isTrue();
        assertThat(new FtpReply(200, "OK").isIntermediate()).isFalse();
    }

    @Test
    void testIsNegative() {
        assertThat(new FtpReply(421, "Error").isNegative()).isTrue();
        assertThat(new FtpReply(550, "Error").isNegative()).isTrue();
        assertThat(new FtpReply(200, "OK").isNegative()).isFalse();
    }

    @Test
    void testReplyCodeResolution() {
        var reply = new FtpReply(220, "Ready");
        assertThat(reply.replyCode()).isEqualTo(FtpReplyCode.SERVICE_READY);
    }

    @Test
    void testReplyCodeUnknown() {
        var reply = new FtpReply(199, "Custom");
        assertThat(reply.replyCode()).isNull();
    }

    @Test
    void testInvalidCodeTooLow() {
        assertThatIllegalArgumentException().isThrownBy(() -> new FtpReply(99, "Bad"));
    }

    @Test
    void testInvalidCodeTooHigh() {
        assertThatIllegalArgumentException().isThrownBy(() -> new FtpReply(600, "Bad"));
    }

    @Test
    void testEmptyLinesThrows() {
        assertThatIllegalArgumentException().isThrownBy(() -> new FtpReply(200, List.of()));
    }

    @Test
    void testNullTextThrows() {
        assertThatNullPointerException().isThrownBy(() -> new FtpReply(200, (String) null));
    }

    @Test
    void testToStringSingleLine() {
        var reply = new FtpReply(200, "OK");
        assertThat(reply.toString()).isEqualTo("200 OK");
    }

    @Test
    void testToStringMultiLine() {
        var reply = new FtpReply(211, List.of("Line1", "Line2", "End"));
        String str = reply.toString();
        assertThat(str).contains("211-Line1");
        assertThat(str).contains("211 End");
    }

    @Test
    void testEquals() {
        var r1 = new FtpReply(200, "OK");
        var r2 = new FtpReply(200, "OK");
        var r3 = new FtpReply(200, "Different");
        assertThat(r1).isEqualTo(r2);
        assertThat(r1).isNotEqualTo(r3);
    }

    @Test
    void testHashCode() {
        var r1 = new FtpReply(200, "OK");
        var r2 = new FtpReply(200, "OK");
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }
}
