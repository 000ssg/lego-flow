package ssg.legoflow.email.smtp.server;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class InMemoryMessageStoreExtendedTest {

    @Test void storeAndGetMessages() {
        var store = new InMemoryMessageStore();
        var envelope = new MailEnvelope("sender@test.com", List.of("rcpt@test.com"), 
                "message data".getBytes(), "msg-1");
        MessageStore.StoreResult result = store.store(envelope);
        assertThat(result.accepted()).isTrue();
        assertThat(store.getMessages()).hasSize(1);
    }

    @Test void getMessagesForRecipient() {
        var store = new InMemoryMessageStore();
        store.store(new MailEnvelope("a@test.com", List.of("b@test.com"), 
                "data1".getBytes(), "m1"));
        store.store(new MailEnvelope("c@test.com", List.of("d@test.com"), 
                "data2".getBytes(), "m2"));
        
        assertThat(store.getMessagesFor("b@test.com")).hasSize(1);
        assertThat(store.getMessagesFor("x@test.com")).isEmpty();
    }

    @Test void getMessageCount() {
        var store = new InMemoryMessageStore();
        assertThat(store.getMessageCount()).isEqualTo(0);
        store.store(new MailEnvelope("a@b.com", List.of("c@d.com"), 
                "data".getBytes(), "m1"));
        assertThat(store.getMessageCount()).isEqualTo(1);
    }

    @Test void clear() {
        var store = new InMemoryMessageStore();
        store.store(new MailEnvelope("a@b.com", List.of("c@d.com"), 
                "data".getBytes(), "m1"));
        store.clear();
        assertThat(store.getMessageCount()).isEqualTo(0);
    }

    @Test void getLastMessage() {
        var store = new InMemoryMessageStore();
        store.store(new MailEnvelope("a@b.com", List.of("c@d.com"), 
                "first".getBytes(), "m1"));
        store.store(new MailEnvelope("x@y.com", List.of("z@w.com"), 
                "second".getBytes(), "m2"));
        var last = store.getLastMessage();
        assertThat(last.messageId()).isEqualTo("m2");
    }

    @Test void getLastMessageWhenEmpty() {
        var store = new InMemoryMessageStore();
        assertThat(store.getLastMessage()).isNull();
    }
}
