package ssg.legoflow.rpc.grpc.transport;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class GrpcTimeoutTest {

    @Test
    void testParseNanoseconds() {
        var timeout = GrpcTimeout.parse("100n");
        assertThat(timeout.value()).isEqualTo(100);
        assertThat(timeout.unit()).isEqualTo(GrpcTimeout.TimeoutUnit.NANOSECONDS);
    }

    @Test
    void testParseMicroseconds() {
        var timeout = GrpcTimeout.parse("50u");
        assertThat(timeout.value()).isEqualTo(50);
        assertThat(timeout.unit()).isEqualTo(GrpcTimeout.TimeoutUnit.MICROSECONDS);
    }

    @Test
    void testParseMilliseconds() {
        var timeout = GrpcTimeout.parse("500m");
        assertThat(timeout.value()).isEqualTo(500);
        assertThat(timeout.unit()).isEqualTo(GrpcTimeout.TimeoutUnit.MILLISECONDS);
        assertThat(timeout.toMillis()).isEqualTo(500);
    }

    @Test
    void testParseSeconds() {
        var timeout = GrpcTimeout.parse("30S");
        assertThat(timeout.value()).isEqualTo(30);
        assertThat(timeout.unit()).isEqualTo(GrpcTimeout.TimeoutUnit.SECONDS);
        assertThat(timeout.toMillis()).isEqualTo(30000);
    }

    @Test
    void testParseMinutes() {
        var timeout = GrpcTimeout.parse("5M");
        assertThat(timeout.value()).isEqualTo(5);
        assertThat(timeout.unit()).isEqualTo(GrpcTimeout.TimeoutUnit.MINUTES);
    }

    @Test
    void testParseHours() {
        var timeout = GrpcTimeout.parse("2H");
        assertThat(timeout.value()).isEqualTo(2);
        assertThat(timeout.unit()).isEqualTo(GrpcTimeout.TimeoutUnit.HOURS);
    }

    @Test
    void testParseNull() {
        assertThat(GrpcTimeout.parse(null)).isNull();
    }

    @Test
    void testParseEmpty() {
        assertThat(GrpcTimeout.parse("")).isNull();
    }

    @Test
    void testParseInvalid() {
        assertThatThrownBy(() -> GrpcTimeout.parse("abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEncodeSeconds() {
        var timeout = GrpcTimeout.ofSeconds(10);
        assertThat(timeout.encode()).isEqualTo("10S");
    }

    @Test
    void testEncodeMillis() {
        var timeout = GrpcTimeout.ofMillis(500);
        assertThat(timeout.encode()).isEqualTo("500m");
    }

    @Test
    void testParseEncodeRoundTrip() {
        for (String input : new String[]{"100n", "50u", "500m", "30S", "5M", "2H"}) {
            var timeout = GrpcTimeout.parse(input);
            assertThat(timeout.encode()).isEqualTo(input);
        }
    }

    @Test
    void testToDuration() {
        var timeout = GrpcTimeout.ofSeconds(5);
        assertThat(timeout.toDuration()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void testToNanos() {
        var timeout = GrpcTimeout.parse("100n");
        assertThat(timeout.toNanos()).isEqualTo(100);
    }

    @Test
    void testFromDurationSeconds() {
        var timeout = GrpcTimeout.fromDuration(Duration.ofSeconds(10));
        assertThat(timeout.value()).isEqualTo(10);
        assertThat(timeout.unit()).isEqualTo(GrpcTimeout.TimeoutUnit.SECONDS);
    }

    @Test
    void testFromDurationMillis() {
        var timeout = GrpcTimeout.fromDuration(Duration.ofMillis(500));
        assertThat(timeout.value()).isEqualTo(500);
        assertThat(timeout.unit()).isEqualTo(GrpcTimeout.TimeoutUnit.MILLISECONDS);
    }

    @Test
    void testFromDurationHours() {
        var timeout = GrpcTimeout.fromDuration(Duration.ofHours(1));
        assertThat(timeout.value()).isEqualTo(1);
        assertThat(timeout.unit()).isEqualTo(GrpcTimeout.TimeoutUnit.HOURS);
    }

    @Test
    void testFromDurationMinutes() {
        var timeout = GrpcTimeout.fromDuration(Duration.ofMinutes(5));
        assertThat(timeout.value()).isEqualTo(5);
        assertThat(timeout.unit()).isEqualTo(GrpcTimeout.TimeoutUnit.MINUTES);
    }

    @Test
    void testToString() {
        assertThat(GrpcTimeout.ofSeconds(30).toString()).isEqualTo("30S");
    }
}
