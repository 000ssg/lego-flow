package ssg.legoflow.email.imap.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class FolderViewTest {
    @Test void testCreation() {
        var view = new FolderView("INBOX");
        assertThat(view.name()).isEqualTo("INBOX");
        assertThat(view.isReadOnly()).isFalse();
        assertThat(view.messageCount()).isZero();
    }

    @Test void testSetters() {
        var view = new FolderView("Drafts");
        view.setMessageCount(42);
        assertThat(view.messageCount()).isEqualTo(42);
        view.setUidNext(100L);
        assertThat(view.uidNext()).isEqualTo(100L);
        view.setUidValidity(999L);
        assertThat(view.uidValidity()).isEqualTo(999L);
        view.setReadOnly(true);
        assertThat(view.isReadOnly()).isTrue();
    }

    @Test void testRecentCount() {
        var view = new FolderView("INBOX");
        view.setRecentCount(3);
        assertThat(view.recentCount()).isEqualTo(3);
    }

    @Test void testHighestModSeq() {
        var view = new FolderView("INBOX");
        view.setHighestModSeq(500L);
        assertThat(view.highestModSeq()).isEqualTo(500L);
    }
}
