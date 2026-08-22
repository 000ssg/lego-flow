package ssg.legoflow.http.header;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class LanguageTagTest {

    @Test
    void testParseWithSubTag() {
        LanguageTag tag = LanguageTag.parse("en-US");
        assertThat(tag.primaryTag()).isEqualTo("en");
        assertThat(tag.subTag()).isEqualTo("US");
    }

    @Test
    void testParseWithoutSubTag() {
        LanguageTag tag = LanguageTag.parse("en");
        assertThat(tag.primaryTag()).isEqualTo("en");
        assertThat(tag.subTag()).isNull();
    }

    @Test
    void testParseWithWhitespace() {
        LanguageTag tag = LanguageTag.parse("  en-US  ");
        assertThat(tag.primaryTag()).isEqualTo("en");
        assertThat(tag.subTag()).isEqualTo("US");
    }

    @Test
    void testParseNullThrows() {
        assertThatThrownBy(() -> LanguageTag.parse(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testMatchesSameLanguage() {
        LanguageTag en = LanguageTag.parse("en");
        LanguageTag enUs = LanguageTag.parse("en-US");
        assertThat(en.matches(enUs)).isTrue();
        assertThat(enUs.matches(en)).isTrue();
    }

    @Test
    void testMatchesExactMatch() {
        LanguageTag tag1 = LanguageTag.parse("en-US");
        LanguageTag tag2 = LanguageTag.parse("en-us");
        assertThat(tag1.matches(tag2)).isTrue();
    }

    @Test
    void testMatchesDifferentLanguage() {
        LanguageTag en = LanguageTag.parse("en");
        LanguageTag fr = LanguageTag.parse("fr");
        assertThat(en.matches(fr)).isFalse();
    }

    @Test
    void testMatchesWildcard() {
        LanguageTag wildcard = new LanguageTag("*", null);
        LanguageTag en = LanguageTag.parse("en");
        assertThat(wildcard.matches(en)).isTrue();
        assertThat(en.matches(wildcard)).isTrue();
    }

    @Test
    void testToStringWithSubTag() {
        LanguageTag tag = new LanguageTag("en", "US");
        assertThat(tag.toString()).isEqualTo("en-US");
    }

    @Test
    void testToStringWithoutSubTag() {
        LanguageTag tag = new LanguageTag("en", null);
        assertThat(tag.toString()).isEqualTo("en");
    }

    @Test
    void testToStringWithEmptySubTag() {
        LanguageTag tag = new LanguageTag("en", "");
        assertThat(tag.toString()).isEqualTo("en");
    }

    @Test
    void testMatchesNullThrows() {
        LanguageTag tag = LanguageTag.parse("en");
        assertThatThrownBy(() -> tag.matches(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testConstructorNullPrimaryThrows() {
        assertThatThrownBy(() -> new LanguageTag(null, "US"))
                .isInstanceOf(NullPointerException.class);
    }
}
