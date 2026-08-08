package ssg.legoflow.ssh.auth;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link AuthBanner}.
 */
class AuthBannerTest {

    @Test void testBasicBanner() {
        var banner = new AuthBanner("Warning: unauthorized access prohibited", "en-US");
        assertThat(banner.message()).isEqualTo("Warning: unauthorized access prohibited");
        assertThat(banner.language()).isEqualTo("en-US");
    }

    @Test void testOfStaticFactory() {
        var banner = AuthBanner.of("Welcome to SSH server");
        assertThat(banner.message()).isEqualTo("Welcome to SSH server");
        // Default language should be empty or ""
        assertThat(banner.language()).isEmpty();
    }

    @Test void testNullMessageThrows() {
        assertThatThrownBy(() -> new AuthBanner(null, "en"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void testNullLanguageThrows() {
        assertThatThrownBy(() -> new AuthBanner("message", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void testDifferentLanguages() {
        var en = new AuthBanner("Warning", "en");
        var fr = new AuthBanner("Avertissement", "fr");
        assertThat(en.language()).isEqualTo("en");
        assertThat(fr.language()).isEqualTo("fr");
    }

    @Test void testMultilineMessage() {
        var msg = "Line 1\nLine 2\nLine 3";
        var banner = new AuthBanner(msg, "en");
        assertThat(banner.message()).contains("\n");
    }

    @Test void testEqualsAndHashCode() {
        var b1 = new AuthBanner("msg", "en");
        var b2 = new AuthBanner("msg", "en");
        assertThat(b1).isEqualTo(b2);
        assertThat(b1.hashCode()).isEqualTo(b2.hashCode());
        
        var b3 = new AuthBanner("other", "fr");
        assertThat(b1).isNotEqualTo(b3);
    }
}
