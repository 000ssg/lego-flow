package ssg.legoflow.xmpp.demo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SimpleChatDemo}.
 *
 * @since 0.1.0
 */
class SimpleChatDemoTest {

    private SimpleChatDemo demo;

    @BeforeEach
    void setUp() {
        demo = new SimpleChatDemo();
        demo.setup("example.com");
    }

    @AfterEach
    void tearDown() {
        demo.shutdown();
    }

    @Test
    void testClientsConnected() {
        assertThat(demo.getAlice().isConnected()).isTrue();
        assertThat(demo.getBob().isConnected()).isTrue();
        assertThat(demo.getAlice().isAuthenticated()).isTrue();
        assertThat(demo.getBob().isAuthenticated()).isTrue();
    }

    @Test
    void testAliceSendsToBob() {
        demo.aliceSays("Hello Bob!");
        assertThat(demo.getBobMessages()).hasSize(1);
        assertThat(demo.getBobMessages().getFirst().body()).isEqualTo("Hello Bob!");
    }

    @Test
    void testBobSendsToAlice() {
        demo.bobSays("Hi Alice!");
        assertThat(demo.getAliceMessages()).hasSize(1);
        assertThat(demo.getAliceMessages().getFirst().body()).isEqualTo("Hi Alice!");
    }

    @Test
    void testConversation() {
        demo.aliceSays("Hello!");
        demo.bobSays("Hi!");
        demo.aliceSays("How are you?");
        assertThat(demo.getBobMessages()).hasSize(2);
        assertThat(demo.getAliceMessages()).hasSize(1);
    }
}
