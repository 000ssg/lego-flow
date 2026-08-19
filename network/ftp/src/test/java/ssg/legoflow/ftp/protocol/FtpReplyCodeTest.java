package ssg.legoflow.ftp.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link FtpReplyCode}.
 */
class FtpReplyCodeTest {

    @Test
    void testAllCodesHaveValidRange() {
        for (FtpReplyCode code : FtpReplyCode.values()) {
            assertThat(code.code()).isBetween(100, 599);
        }
    }

    @Test
    void testPreliminaryCodes() {
        assertThat(FtpReplyCode.RESTART_MARKER.isPreliminary()).isTrue();
        assertThat(FtpReplyCode.SERVICE_READY_IN_MINUTES.isPreliminary()).isTrue();
        assertThat(FtpReplyCode.DATA_CONNECTION_OPEN.isPreliminary()).isTrue();
        assertThat(FtpReplyCode.FILE_STATUS_OK.isPreliminary()).isTrue();
    }

    @Test
    void testPositiveCompletionCodes() {
        assertThat(FtpReplyCode.COMMAND_OK.isPositiveCompletion()).isTrue();
        assertThat(FtpReplyCode.SERVICE_READY.isPositiveCompletion()).isTrue();
        assertThat(FtpReplyCode.USER_LOGGED_IN.isPositiveCompletion()).isTrue();
        assertThat(FtpReplyCode.FILE_ACTION_OK.isPositiveCompletion()).isTrue();
        assertThat(FtpReplyCode.CLOSING_DATA_CONNECTION.isPositiveCompletion()).isTrue();
    }

    @Test
    void testPositiveIntermediateCodes() {
        assertThat(FtpReplyCode.USER_OK_NEED_PASSWORD.isPositiveIntermediate()).isTrue();
        assertThat(FtpReplyCode.NEED_ACCOUNT.isPositiveIntermediate()).isTrue();
        assertThat(FtpReplyCode.FILE_ACTION_PENDING.isPositiveIntermediate()).isTrue();
    }

    @Test
    void testTransientNegativeCodes() {
        assertThat(FtpReplyCode.SERVICE_NOT_AVAILABLE.isTransientNegative()).isTrue();
        assertThat(FtpReplyCode.CANT_OPEN_DATA_CONNECTION.isTransientNegative()).isTrue();
        assertThat(FtpReplyCode.INSUFFICIENT_STORAGE.isTransientNegative()).isTrue();
    }

    @Test
    void testPermanentNegativeCodes() {
        assertThat(FtpReplyCode.SYNTAX_ERROR.isPermanentNegative()).isTrue();
        assertThat(FtpReplyCode.NOT_LOGGED_IN.isPermanentNegative()).isTrue();
        assertThat(FtpReplyCode.FILE_UNAVAILABLE.isPermanentNegative()).isTrue();
        assertThat(FtpReplyCode.FILE_NAME_NOT_ALLOWED.isPermanentNegative()).isTrue();
    }

    @Test
    void testFromCode() {
        assertThat(FtpReplyCode.fromCode(220)).isEqualTo(FtpReplyCode.SERVICE_READY);
        assertThat(FtpReplyCode.fromCode(331)).isEqualTo(FtpReplyCode.USER_OK_NEED_PASSWORD);
        assertThat(FtpReplyCode.fromCode(550)).isEqualTo(FtpReplyCode.FILE_UNAVAILABLE);
    }

    @Test
    void testFromCodeUnknown() {
        assertThatIllegalArgumentException().isThrownBy(() -> FtpReplyCode.fromCode(999));
    }

    @ParameterizedTest
    @CsvSource({
            "110, RESTART_MARKER",
            "120, SERVICE_READY_IN_MINUTES",
            "125, DATA_CONNECTION_OPEN",
            "150, FILE_STATUS_OK",
            "200, COMMAND_OK",
            "220, SERVICE_READY",
            "226, CLOSING_DATA_CONNECTION",
            "227, ENTERING_PASSIVE_MODE",
            "229, ENTERING_EXTENDED_PASSIVE_MODE",
            "230, USER_LOGGED_IN",
            "234, SECURITY_DATA_EXCHANGE_COMPLETE",
            "250, FILE_ACTION_OK",
            "257, PATHNAME_CREATED",
            "331, USER_OK_NEED_PASSWORD",
            "350, FILE_ACTION_PENDING",
            "421, SERVICE_NOT_AVAILABLE",
            "425, CANT_OPEN_DATA_CONNECTION",
            "500, SYNTAX_ERROR",
            "530, NOT_LOGGED_IN",
            "550, FILE_UNAVAILABLE"
    })
    void testFromCodeMapping(int code, String expectedName) {
        FtpReplyCode rc = FtpReplyCode.fromCode(code);
        assertThat(rc.name()).isEqualTo(expectedName);
        assertThat(rc.code()).isEqualTo(code);
    }

    @Test
    void testDescriptionNotEmpty() {
        for (FtpReplyCode code : FtpReplyCode.values()) {
            assertThat(code.description()).isNotEmpty();
        }
    }

    @Test
    void testCategoryExclusivity() {
        for (FtpReplyCode code : FtpReplyCode.values()) {
            int trueCount = 0;
            if (code.isPreliminary()) trueCount++;
            if (code.isPositiveCompletion()) trueCount++;
            if (code.isPositiveIntermediate()) trueCount++;
            if (code.isTransientNegative()) trueCount++;
            if (code.isPermanentNegative()) trueCount++;
            assertThat(trueCount).as("Code %d belongs to exactly one category", code.code()).isEqualTo(1);
        }
    }
}
