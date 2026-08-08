package ssg.legoflow.email.imap.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for IMAP protocol classes.
 */
class ImapProtocolTest {

    @Test void testImapCodecQuoteString() {
        String result = ImapCodec.quoteString("hello world");
        assertThat(result).isEqualTo("\"hello world\"");
    }

    @Test void testImapCommandValues() {
        var commands = ImapCommand.values();
        assertThat(commands).contains(ImapCommand.LOGIN, ImapCommand.SELECT);
    }

    @Test void testImapStatusOk() {
        var resp = ImapResponse.tagged("TAG", ImapStatus.OK, "done");
        assertThat(resp.status()).isEqualTo(ImapStatus.OK);
    }

    @Test void testImapStatusNo() {
        var resp = ImapResponse.tagged("TAG", ImapStatus.NO, "error");
        assertThat(resp.status()).isEqualTo(ImapStatus.NO);
    }

    @Test void testSearchCriteria() {
        var criteria = SearchCriteria.from("ALL UNSEEN SINCE 01-Jan-2024");
        assertThat(criteria).isNotNull();
    }

    @Test void testSortCriteriaKeyValues() {
        var keys = SortCriteria.SortKey.values();
        assertThat(keys).isNotEmpty();
    }
}
