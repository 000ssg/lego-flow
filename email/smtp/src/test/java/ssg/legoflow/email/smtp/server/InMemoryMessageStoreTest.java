package ssg.legoflow.email.smtp.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
/**
 * Unit tests for {@link InMemoryMessageStore}.
 */
@DisplayName("InMemoryMessageStore")
class InMemoryMessageStoreTest {

    @Test
    void testInitialStoreIsEmpty() {
        var store = new InMemoryMessageStore();
        assertThat(store.getMessages()).isEmpty();
        assertThat(store.getMessageCount()).isZero();
        assertThat(store.getLastMessage()).isNull();
    }

    @Test
    void testStoreAndRetrieve() {
        var store = new InMemoryMessageStore();
        var env = new MailEnvelope("sender@test.com", List.of("rcpt@test.com"),
                "body".getBytes(StandardCharsets.UTF_8), "msg-1");
        
        store.store(env);
        assertThat(store.getMessages()).hasSize(1);
        assertThat(store.getMessageCount()).isEqualTo(1);
        assertThat(store.getLastMessage()).isSameAs(env);
    }

    @Test
    void testGetMessagesForRecipient() {
        var store = new InMemoryMessageStore();
        var env1 = new MailEnvelope("a@test.com", List.of("rcpt1@test.com"),
                "body".getBytes(), "msg-1");
        var env2 = new MailEnvelope("b@test.com", List.of("rcpt2@test.com"),
                "body".getBytes(), "msg-2");
        var env3 = new MailEnvelope("c@test.com", List.of("rcpt1@test.com", "rcpt2@test.com"),
                "body".getBytes(), "msg-3");

        store.store(env1);
        store.store(env2);
        store.store(env3);

        assertThat(store.getMessagesFor("rcpt1@test.com")).containsExactly(env1, env3);
        assertThat(store.getMessagesFor("rcpt2@test.com")).containsExactly(env2, env3);
        assertThat(store.getMessagesFor("nobody@test.com")).isEmpty();
    }

    @Test
    void testGetMessagesForSender() {
        var store = new InMemoryMessageStore();
        var env1 = new MailEnvelope("alice@test.com", List.of("rcpt@test.com"),
                "body".getBytes(), "msg-1");
        var env2 = new MailEnvelope("bob@test.com", List.of("rcpt@test.com"),
                "body".getBytes(), "msg-2");

        store.store(env1);
        store.store(env2);

        assertThat(store.getMessagesFor("alice@test.com")).isEmpty(); // getMessagesFor filters by recipient
    }

    @Test
    void testClear() {
        var store = new InMemoryMessageStore();
        store.store(new MailEnvelope("a@b.com", List.of("c@d.com"),
                "body".getBytes(), "msg-1"));
        assertThat(store.getMessageCount()).isEqualTo(1);

        store.clear();
        assertThat(store.getMessages()).isEmpty();
        assertThat(store.getMessageCount()).isZero();
        assertThat(store.getLastMessage()).isNull();
    }

    @Test
    void testStoreMultipleAndOrder() {
        var store = new InMemoryMessageStore();
        for (int i = 0; i < 5; i++) {
            store.store(new MailEnvelope("sender@test.com", List.of("rcpt@test.com"),
                    ("body-" + i).getBytes(), "msg-" + i));
        }

        assertThat(store.getMessageCount()).isEqualTo(5);
        assertThat(store.getMessages()).hasSize(5);
        assertThat(store.getLastMessage().messageId()).isEqualTo("msg-4");
    }

    @Test
    void testStoreResultSuccess() {
        var result = MessageStore.StoreResult.success("msg-100");
        assertThat(result.accepted()).isTrue();
        assertThat(result.messageId()).isEqualTo("msg-100");
    }

    @Test
    void testStoreResultRejected() {
        var result = MessageStore.StoreResult.rejected("storage full");
        assertThat(result.accepted()).isFalse();
        assertThat(result.messageId()).isNull();
        assertThat(result.message()).isEqualTo("storage full");
    }

    @Test
    void testMessageStoreException() {
        var ex = new MessageStoreException("disk failure");
        assertThat(ex.getMessage()).isEqualTo("disk failure");

        var cause = new RuntimeException("IO error");
        var ex2 = new MessageStoreException("write failed", cause);
        assertThat(ex2.getCause()).isSameAs(cause);
    }
}
