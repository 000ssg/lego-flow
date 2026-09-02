package ssg.legoflow.messaging.nats.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link ConnectOptions}.
 */
class ConnectOptionsTest {

    @Test
    void testWithDefaults() {
        var opts = ConnectOptions.withDefaults("test-client");
        assertThat(opts.name()).isEqualTo("test-client");
        assertThat(opts.verbose()).isFalse();
        assertThat(opts.echo()).isTrue();
        assertThat(opts.headers()).isTrue();
        assertThat(opts.noResponders()).isTrue();
        assertThat(opts.lang()).isEqualTo("java");
    }

    @Test
    void testWithToken() {
        var opts = ConnectOptions.withDefaults("c").withToken("mytoken");
        assertThat(opts.authToken()).isEqualTo("mytoken");
        assertThat(opts.name()).isEqualTo("c");
    }

    @Test
    void testWithUserPass() {
        var opts = ConnectOptions.withDefaults("c").withUserPass("admin", "secret");
        assertThat(opts.user()).isEqualTo("admin");
        assertThat(opts.pass()).isEqualTo("secret");
    }

    @Test
    void testWithVerbose() {
        var opts = ConnectOptions.withDefaults("c").withVerbose(true);
        assertThat(opts.verbose()).isTrue();
    }

    @Test
    void testToJsonContainsFields() {
        var opts = ConnectOptions.withDefaults("my-app").withToken("tok");
        String json = opts.toJson();

        assertThat(json).contains("\"name\":\"my-app\"");
        assertThat(json).contains("\"auth_token\":\"tok\"");
        assertThat(json).contains("\"echo\":true");
        assertThat(json).contains("\"headers\":true");
    }

    @Test
    void testToJsonOmitsNullToken() {
        var opts = ConnectOptions.withDefaults("c");
        String json = opts.toJson();
        assertThat(json).doesNotContain("auth_token");
    }

    @Test
    void testRoundTrip() {
        var original = ConnectOptions.withDefaults("test")
                .withToken("secret")
                .withVerbose(true);
        var parsed = ConnectOptions.fromJson(original.toJson());

        assertThat(parsed.name()).isEqualTo("test");
        assertThat(parsed.authToken()).isEqualTo("secret");
        assertThat(parsed.verbose()).isTrue();
        assertThat(parsed.echo()).isTrue();
    }

    @Test
    void testRoundTripUserPass() {
        var original = ConnectOptions.withDefaults("app")
                .withUserPass("user1", "pass1");
        var parsed = ConnectOptions.fromJson(original.toJson());

        assertThat(parsed.user()).isEqualTo("user1");
        assertThat(parsed.pass()).isEqualTo("pass1");
    }

    @Test
    void testFromJsonMinimal() {
        var parsed = ConnectOptions.fromJson("{}");
        assertThat(parsed.verbose()).isFalse();
        assertThat(parsed.authToken()).isNull();
        assertThat(parsed.name()).isNull();
    }
}
