package ssg.legoflow.email.imap.server;

import org.junit.jupiter.api.Test;
import ssg.legoflow.email.imap.protocol.FetchDataItem;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class FetchHandlerExtendedTest {

    private StoredMessage makeMessage(int uid) {
        return new StoredMessage(uid, Instant.now(), 
                ("Subject: Test\r\nFrom: test@example.com\r\n\r\nBody " + uid).getBytes(), 
                Set.of());
    }

    @Test void fetchItemUid() {
        var msg = makeMessage(1);
        String result = FetchHandler.fetchItem(msg, FetchDataItem.UID, false);
        assertThat(result).contains("UID");
    }

    @Test void fetchItemFlags() {
        var msg = makeMessage(2);
        String result = FetchHandler.fetchItem(msg, FetchDataItem.FLAGS, false);
        assertThat(result).contains("FLAGS");
    }

    @Test void fetchItemInternalDate() {
        var msg = makeMessage(3);
        String result = FetchHandler.fetchItem(msg, FetchDataItem.INTERNALDATE, false);
        assertThat(result).contains("INTERNALDATE");
    }

    @Test void fetchMultipleItems() {
        var msg = makeMessage(5);
        String result = FetchHandler.fetch(msg, 5, List.of(FetchDataItem.UID, FetchDataItem.FLAGS), false);
        assertThat(result).contains("UID");
        assertThat(result).contains("FLAGS");
    }

    @Test void fetchItemBody() {
        var msg = makeMessage(9);
        String result = FetchHandler.fetchItem(msg, FetchDataItem.BODY, false);
        assertThat(result).contains("BODY");
    }

    @Test void fetchMarkSeen() {
        var msg = makeMessage(10);
        FetchHandler.fetchItem(msg, FetchDataItem.UID, true);
    }
}
