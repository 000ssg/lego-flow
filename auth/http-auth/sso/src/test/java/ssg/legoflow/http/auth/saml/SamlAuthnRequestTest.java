package ssg.legoflow.http.auth.saml;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.zip.InflaterInputStream;

import static org.assertj.core.api.Assertions.*;

class SamlAuthnRequestTest {

    @Test
    void testGenerateXml() {
        var request = new SamlAuthnRequest(
                "https://sp.example.com/acs",
                "https://sp.example.com",
                "https://idp.example.com/sso",
                "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress");

        String xml = request.toXml();
        assertThat(xml).contains("<samlp:AuthnRequest");
        assertThat(xml).contains("ID=\"" + request.getId() + "\"");
        assertThat(xml).contains("Version=\"2.0\"");
        assertThat(xml).contains("IssueInstant=");
        assertThat(xml).contains("Destination=\"https://idp.example.com/sso\"");
        assertThat(xml).contains("AssertionConsumerServiceURL=\"https://sp.example.com/acs\"");
        assertThat(xml).contains("<saml:Issuer>https://sp.example.com</saml:Issuer>");
        assertThat(xml).contains("NameIDPolicy");
        assertThat(xml).contains("</samlp:AuthnRequest>");
    }

    @Test
    void testGenerateXmlWithoutNameIdFormat() {
        var request = new SamlAuthnRequest(
                "https://sp.example.com/acs",
                "https://sp.example.com",
                "https://idp.example.com/sso",
                null);

        String xml = request.toXml();
        assertThat(xml).doesNotContain("NameIDPolicy");
    }

    @Test
    void testRedirectBinding() {
        var request = new SamlAuthnRequest(
                "https://sp.example.com/acs",
                "https://sp.example.com",
                "https://idp.example.com/sso",
                null);

        String encoded = request.toRedirectBinding();
        assertThat(encoded).isNotEmpty();

        // Should be valid base64
        byte[] decoded = Base64.getDecoder().decode(encoded);
        assertThat(decoded).isNotEmpty();

        // Should be deflated — inflate and verify XML
        try {
            var inflater = new InflaterInputStream(new ByteArrayInputStream(decoded),
                    new java.util.zip.Inflater(true));
            String xml = new String(inflater.readAllBytes());
            assertThat(xml).contains("<samlp:AuthnRequest");
        } catch (Exception e) {
            fail("Failed to inflate redirect binding", e);
        }
    }

    @Test
    void testPostBindingForm() {
        var request = new SamlAuthnRequest(
                "https://sp.example.com/acs",
                "https://sp.example.com",
                "https://idp.example.com/sso",
                null);

        String html = request.toPostBindingForm();
        assertThat(html).contains("<!DOCTYPE html>");
        assertThat(html).contains("method=\"post\"");
        assertThat(html).contains("action=\"https://idp.example.com/sso\"");
        assertThat(html).contains("name=\"SAMLRequest\"");
        assertThat(html).contains("document.forms[0].submit()");
    }

    @Test
    void testFromConfig() {
        var config = SamlConfig.of("https://idp.example.com", "https://idp.example.com/sso");
        var request = SamlAuthnRequest.fromConfig(config, "https://sp.example.com/acs", "https://sp.example.com");

        assertThat(request.getDestination()).isEqualTo("https://idp.example.com/sso");
        assertThat(request.getIssuer()).isEqualTo("https://sp.example.com");
        assertThat(request.getAssertionConsumerServiceUrl()).isEqualTo("https://sp.example.com/acs");
    }

    @Test
    void testGetters() {
        var request = new SamlAuthnRequest(
                "https://sp.example.com/acs",
                "https://sp.example.com",
                "https://idp.example.com/sso",
                null);

        assertThat(request.getId()).startsWith("_");
        assertThat(request.getIssueInstant()).isNotNull();
        assertThat(request.getAssertionConsumerServiceUrl()).isEqualTo("https://sp.example.com/acs");
        assertThat(request.getIssuer()).isEqualTo("https://sp.example.com");
        assertThat(request.getDestination()).isEqualTo("https://idp.example.com/sso");
    }

    @Test
    void testIdIsUnique() {
        var r1 = new SamlAuthnRequest("https://acs", "https://sp", "https://idp", null);
        var r2 = new SamlAuthnRequest("https://acs", "https://sp", "https://idp", null);
        assertThat(r1.getId()).isNotEqualTo(r2.getId());
    }

    @Test
    void testXmlEscaping() {
        var request = new SamlAuthnRequest(
                "https://sp.example.com/acs?param=val&other=2",
                "https://sp.example.com",
                "https://idp.example.com/sso",
                null);
        String xml = request.toXml();
        assertThat(xml).contains("&amp;");
        assertThat(xml).doesNotContain("param=val&other");
    }
}
