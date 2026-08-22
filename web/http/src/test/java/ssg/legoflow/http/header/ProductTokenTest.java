package ssg.legoflow.http.header;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ProductTokenTest {

    @Test
    void testParseProductWithVersion() {
        var token = ProductToken.parse("Mozilla/5.0");

        assertThat(token.product()).isEqualTo("Mozilla");
        assertThat(token.version()).isEqualTo("5.0");
    }

    @Test
    void testParseProductWithoutVersion() {
        var token = ProductToken.parse("MyClient");

        assertThat(token.product()).isEqualTo("MyClient");
        assertThat(token.version()).isNull();
    }

    @Test
    void testParseProductWithWhitespace() {
        var token = ProductToken.parse("  MyApp / 1.2.3  ");

        assertThat(token.product()).isEqualTo("MyApp");
        assertThat(token.version()).isEqualTo("1.2.3");
    }

    @Test
    void testToStringWithVersion() {
        var token = new ProductToken("LegoFlow", "1.0");

        assertThat(token.toString()).isEqualTo("LegoFlow/1.0");
    }

    @Test
    void testToStringWithoutVersion() {
        var token = new ProductToken("LegoFlow", null);

        assertThat(token.toString()).isEqualTo("LegoFlow");
    }

    @Test
    void testToStringWithEmptyVersion() {
        var token = new ProductToken("LegoFlow", "");

        assertThat(token.toString()).isEqualTo("LegoFlow");
    }

    @Test
    void testParseNullThrows() {
        assertThatNullPointerException().isThrownBy(() -> ProductToken.parse(null));
    }

    @Test
    void testConstructorNullProductThrows() {
        assertThatNullPointerException().isThrownBy(() -> new ProductToken(null, "1.0"));
    }

    @Test
    void testRoundTripParsing() {
        var original = new ProductToken("Server", "2.5.1");
        var parsed = ProductToken.parse(original.toString());

        assertThat(parsed.product()).isEqualTo("Server");
        assertThat(parsed.version()).isEqualTo("2.5.1");
    }
}
