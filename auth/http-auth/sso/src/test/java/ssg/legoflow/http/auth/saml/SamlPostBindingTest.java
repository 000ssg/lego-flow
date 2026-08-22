package ssg.legoflow.http.auth.saml;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class SamlPostBindingTest {

    @Test
    void testGenerateRequestForm() {
        String html = SamlPostBinding.generateRequestForm(
                "https://idp.example.com/sso", "PHNhbWxwOkF1dG...", null);
        assertThat(html).contains("<!DOCTYPE html>");
        assertThat(html).contains("action=\"https://idp.example.com/sso\"");
        assertThat(html).contains("name=\"SAMLRequest\"");
        assertThat(html).contains("value=\"PHNhbWxwOkF1dG...\"");
        assertThat(html).contains("document.forms[0].submit()");
        assertThat(html).doesNotContain("RelayState");
    }

    @Test
    void testGenerateRequestFormWithRelayState() {
        String html = SamlPostBinding.generateRequestForm(
                "https://idp.example.com/sso", "PHNhbWxwOkF1dG...", "https://sp.example.com/home");
        assertThat(html).contains("name=\"RelayState\"");
        assertThat(html).contains("value=\"https://sp.example.com/home\"");
    }

    @Test
    void testGenerateResponseForm() {
        String html = SamlPostBinding.generateResponseForm(
                "https://sp.example.com/acs", "PHNhbWxwOlJlc3...", null);
        assertThat(html).contains("action=\"https://sp.example.com/acs\"");
        assertThat(html).contains("name=\"SAMLResponse\"");
    }

    @Test
    void testHtmlEscaping() {
        String html = SamlPostBinding.generateRequestForm(
                "https://idp.example.com/sso?a=1&b=2", "value\"with'quotes", null);
        assertThat(html).contains("&amp;");
        assertThat(html).contains("&quot;");
        assertThat(html).contains("&#39;");
    }

    @Test
    void testNoscriptFallback() {
        String html = SamlPostBinding.generateRequestForm(
                "https://idp.example.com/sso", "test", null);
        assertThat(html).contains("<noscript>");
        assertThat(html).contains("type=\"submit\"");
    }
}
