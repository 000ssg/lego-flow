package ssg.legoflow.email.common.header;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link HeaderField}.
 */
class HeaderFieldTest {

    @Test
    void testNameAndValue() {
        HeaderField field = new HeaderField("Subject", "Hello");
        assertThat(field.name()).isEqualTo("Subject");
        assertThat(field.rawValue()).isEqualTo("Hello");
    }

    @Test
    void testNameEqualsCaseInsensitive() {
        HeaderField field = new HeaderField("Content-Type", "text/plain");
        assertThat(field.nameEquals("content-type")).isTrue();
        assertThat(field.nameEquals("CONTENT-TYPE")).isTrue();
        assertThat(field.nameEquals("Content-Type")).isTrue();
        assertThat(field.nameEquals("Subject")).isFalse();
    }

    @Test
    void testDecodedValueWithEncodedWord() {
        HeaderField field = new HeaderField("Subject", "=?UTF-8?B?SGVsbG8=?=");
        assertThat(field.decodedValue()).isEqualTo("Hello");
    }

    @Test
    void testDecodedValuePlain() {
        HeaderField field = new HeaderField("Subject", "Hello");
        assertThat(field.decodedValue()).isEqualTo("Hello");
    }

    @Test
    void testToWireFormat() {
        HeaderField field = new HeaderField("Subject", "Hello");
        assertThat(field.toWireFormat()).isEqualTo("Subject: Hello");
    }

    @Test
    void testEquality() {
        HeaderField a = new HeaderField("Subject", "Hello");
        HeaderField b = new HeaderField("subject", "Hello");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void testInequalityDifferentValue() {
        HeaderField a = new HeaderField("Subject", "Hello");
        HeaderField b = new HeaderField("Subject", "World");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void testToString() {
        HeaderField field = new HeaderField("From", "user@example.com");
        assertThat(field.toString()).isEqualTo("From: user@example.com");
    }
}
