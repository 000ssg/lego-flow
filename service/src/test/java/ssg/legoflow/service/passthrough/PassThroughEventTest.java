package ssg.legoflow.service.passthrough;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class PassThroughEventTest {

    @Test
    void testStartedEvent() {
        Map<Integer, InetSocketAddress> bindings = Map.of(8080, new InetSocketAddress("127.0.0.1", 80));
        
        PassThroughEvent.Started event = new PassThroughEvent.Started(null, bindings, Instant.now());
        
        assertThat(event.source()).isNull();
        assertThat(event.bindings()).containsKey(8080);
    }

    @Test
    void testStoppedEvent() {
        PassThroughEvent.Stopped event = new PassThroughEvent.Stopped(null, Instant.now());
        assertThat(event.source()).isNull();
    }

    @Test
    void testPausedEvent() {
        PassThroughEvent.Paused event = new PassThroughEvent.Paused(null, Duration.ofMinutes(5), Instant.now());
        assertThat(event.duration()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void testDataTransferredEvent() {
        Instant now = Instant.now();
        
        PassThroughEvent.DataTransferred e1 = new PassThroughEvent.DataTransferred(null, 
                Direction.LOCAL_TO_REMOTE, 500, now);
        assertThat(e1.direction()).isEqualTo(Direction.LOCAL_TO_REMOTE);
        assertThat(e1.bytes()).isEqualTo(500);
        
        PassThroughEvent.DataTransferred e2 = new PassThroughEvent.DataTransferred(null, 
                Direction.REMOTE_TO_LOCAL, 300, now);
        assertThat(e2.direction()).isEqualTo(Direction.REMOTE_TO_LOCAL);
    }

    @Test
    void testConnectionClosedEvent() {
        ConnectionStatistics stats = new ConnectionStatistics(100, 200, 300, 400);
        PassThroughEvent.ConnectionClosed event = new PassThroughEvent.ConnectionClosed(null, stats, Instant.now());
        
        assertThat(event.stats()).isEqualTo(stats);
    }

    @Test
    void testDirectionValues() {
        Direction[] values = Direction.values();
        assertThat(values).hasSize(2);
    }

    @Nested
    class ErrorTests {
        
        @Test
        void testErrorWithAllFields() {
            Exception cause = new RuntimeException("root cause");
            
            PassThroughEvent.Error error = new PassThroughEvent.Error(
                    "source", "Something went wrong", cause, Instant.now());
            
            assertThat(error.source()).isEqualTo("source");
            assertThat(error.message()).isEqualTo("Something went wrong");
            assertThat(error.cause()).isSameAs(cause);
        }

        @Test
        void testErrorWithNullCause() {
            PassThroughEvent.Error error = new PassThroughEvent.Error(
                    null, "Unknown error", null, Instant.now());
            
            assertThat(error.source()).isNull();
            assertThat(error.cause()).isNull();
        }

        @Test
        void testErrorEquality() {
            Exception cause = new RuntimeException("test");
            Instant now = Instant.now();
            
            PassThroughEvent.Error e1 = new PassThroughEvent.Error("src", "msg", cause, now);
            PassThroughEvent.Error e2 = new PassThroughEvent.Error("src", "msg", cause, now);
            PassThroughEvent.Error e3 = new PassThroughEvent.Error("other", "msg", cause, now);
            
            assertThat(e1).isEqualTo(e2);
            assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
            assertThat(e1).isNotEqualTo(e3);
        }

        @Test
        void testErrorImplementsPassThroughEvent() {
            PassThroughEvent.Error error = new PassThroughEvent.Error(null, null, null, Instant.now());
            assertThat(error).isInstanceOf(PassThroughEvent.class);
        }

        @Test
        void testErrorMessageWithSpecialCharacters() {
            String msg = "Error with 'quotes' and \"double\" & special<chars>";
            PassThroughEvent.Error error = new PassThroughEvent.Error("src", msg, null, Instant.now());
            assertThat(error.message()).isEqualTo(msg);
        }

        @Test
        void testErrorSourceIsAnyObject() {
            PassThroughEvent.Error e1 = new PassThroughEvent.Error("stringSource", "msg", null, Instant.now());
            PassThroughEvent.Error e2 = new PassThroughEvent.Error(42, "msg", null, Instant.now());
            
            assertThat(e1.source()).isEqualTo("stringSource");
            assertThat(e2.source()).isEqualTo(42);
        }

        @Test
        void testErrorWithEmptyMessage() {
            PassThroughEvent.Error error = new PassThroughEvent.Error("src", "", null, Instant.now());
            assertThat(error.message()).isEmpty();
        }

        @Test
        void testErrorTimestampPrecision() {
            Instant precise = Instant.parse("2024-01-15T10:30:00.123456789Z");
            PassThroughEvent.Error error = new PassThroughEvent.Error(null, null, null, precise);
            assertThat(error.timestamp()).isEqualTo(precise);
        }

        @Test
        void testErrorToString() {
            PassThroughEvent.Error error = new PassThroughEvent.Error(
                    "src", "msg", new RuntimeException("cause"), Instant.now());
            
            String str = error.toString();
            assertThat(str).contains("src");
            assertThat(str).contains("msg");
        }

        @Test
        void testPatternMatchingOnError() {
            PassThroughEvent event = new PassThroughEvent.Error(null, "test error", null, Instant.now());
            
            if (event instanceof PassThroughEvent.Error err) {
                assertThat(err.message()).isEqualTo("test error");
            } else {
                fail("Expected Error event");
            }
        }
    }

    @Nested
    class SealedHierarchyTests {
        
        @Test
        void testAllEventsArePassThroughEvents() {
            Instant now = Instant.now();
            
            assertThat(new PassThroughEvent.Started(null, Map.of(), now)).isInstanceOf(PassThroughEvent.class);
            assertThat(new PassThroughEvent.Stopped(null, now)).isInstanceOf(PassThroughEvent.class);
            assertThat(new PassThroughEvent.Error(null, "err", null, now)).isInstanceOf(PassThroughEvent.class);
        }

        @Test
        void testStartedEventBindings() {
            Map<Integer, InetSocketAddress> bindings = Map.of(
                    8080, new InetSocketAddress("127.0.0.1", 80),
                    8443, new InetSocketAddress("127.0.0.1", 443));
            
            PassThroughEvent.Started event = new PassThroughEvent.Started(null, bindings, Instant.now());
            
            assertThat(event.bindings()).hasSize(2);
            assertThat(event.bindings().get(8080).getPort()).isEqualTo(80);
        }

        @Test
        void testConnectionAcceptedEvent() {
            PassThroughEvent.ConnectionAccepted event = new PassThroughEvent.ConnectionAccepted(null, Instant.now());
            assertThat(event.timestamp()).isNotNull();
        }
    }
}
