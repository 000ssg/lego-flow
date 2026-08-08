package ssg.legoflow.email.imap.server;

import org.junit.jupiter.api.Test;
import ssg.legoflow.email.imap.protocol.FetchDataItem;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class FetchHandlerTest {
    private StoredMessage createMessage() {
        byte[] c = "From: alice@example.com\r\nTo: bob@example.com\r\nSubject: Test\r\nMessage-ID: <msg001>\r\n\r\nBody content here".getBytes(StandardCharsets.UTF_8);
        return new StoredMessage(1L, Instant.now(), c, Set.of("\\Seen", "\\Flagged"));
    }

    @Test void testFetchBody() {
        var msg = createMessage();
        String result = FetchHandler.fetch(msg, 1, List.of(FetchDataItem.BODY), false);
        assertThat(result).contains("FETCH").contains("BODY");
    }

    @Test void testFetchFlags() {
        var msg = createMessage();
        String result = FetchHandler.fetchItem(msg, FetchDataItem.FLAGS, false);
        assertThat(result).contains("\\Seen").contains("\\Flagged");
    }

    @Test void testFetchUid() {
        var msg = createMessage();
        String result = FetchHandler.fetchItem(msg, FetchDataItem.UID, false);
        assertThat(result).contains("UID 1");
    }

    @Test void testFetchInternalDate() {
        var msg = createMessage();
        String result = FetchHandler.fetchItem(msg, FetchDataItem.INTERNALDATE, false);
        assertThat(result).isNotBlank();
    }

    @Test void testFetchRfc822Size() {
        var msg = createMessage();
        String result = FetchHandler.fetchItem(msg, FetchDataItem.RFC822_SIZE, false);
        assertThat(result).contains(String.valueOf(msg.size()));
    }

    @Test void testFetchEnvelope() {
        var msg = createMessage();
        String result = FetchHandler.formatEnvelope(msg);
        assertThat(result).isNotBlank();
    }

    @Test void testFetchMultipleItems() {
        var msg = createMessage();
        String result = FetchHandler.fetch(msg, 1, List.of(FetchDataItem.FLAGS, FetchDataItem.UID), false);
        assertThat(result).isNotBlank();
    }

    @Test void testDataItemValues() {
        for (FetchDataItem item : List.of(
                FetchDataItem.FLAGS, FetchDataItem.INTERNALDATE, FetchDataItem.RFC822_SIZE,
                FetchDataItem.ENVELOPE, FetchDataItem.BODY, FetchDataItem.BODYSTRUCTURE,
                FetchDataItem.RFC822, FetchDataItem.RFC822_HEADER, FetchDataItem.RFC822_TEXT,
                FetchDataItem.UID)) {
            assertThat(item.name()).isNotBlank();
            assertThat(item.section()).isNull();
        }
    }

    @Test void testBodySection() {
        var item = FetchDataItem.bodySection("HEADER.FROM");
        assertThat(item.isPeek()).isFalse();
        assertThat(item.section()).isEqualTo("HEADER.FROM");
    }

    @Test void testBodyPeek() {
        var item = FetchDataItem.bodyPeek("");
        assertThat(item.isPeek()).isTrue();
    }

    @Test void testPartialFetch() {
        var item = FetchDataItem.partial("", true, 0, 100);
        assertThat(item.isPeek()).isTrue();
        assertThat(item.isPartial()).isTrue();
    }

    @Test void testBodyStructure() {
        var msg = createMessage();
        assertThat(FetchHandler.formatBodyStructure(msg, false)).isNotBlank();
        assertThat(FetchHandler.formatBodyStructure(msg, true)).isNotBlank();
    }
}
