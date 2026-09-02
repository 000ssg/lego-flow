package ssg.legoflow.http.auth.saml;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class SamlLogoutTest {

    @Test
    void testGenerateLogoutRequest() {
        String xml = SamlLogout.generateLogoutRequest(
                "https://sp.example.com",
                "https://idp.example.com/slo",
                "alice@example.com",
                "session-123");

        assertThat(xml).contains("<samlp:LogoutRequest");
        assertThat(xml).contains("ID=\"_");
        assertThat(xml).contains("Version=\"2.0\"");
        assertThat(xml).contains("IssueInstant=");
        assertThat(xml).contains("Destination=\"https://idp.example.com/slo\"");
        assertThat(xml).contains("<saml:Issuer>https://sp.example.com</saml:Issuer>");
        assertThat(xml).contains("<saml:NameID>alice@example.com</saml:NameID>");
        assertThat(xml).contains("<samlp:SessionIndex>session-123</samlp:SessionIndex>");
        assertThat(xml).contains("</samlp:LogoutRequest>");
    }

    @Test
    void testGenerateLogoutRequestWithoutSessionIndex() {
        String xml = SamlLogout.generateLogoutRequest(
                "https://sp.example.com",
                "https://idp.example.com/slo",
                "bob@example.com",
                null);

        assertThat(xml).doesNotContain("SessionIndex");
        assertThat(xml).contains("<saml:NameID>bob@example.com</saml:NameID>");
    }

    @Test
    void testGenerateLogoutRequestFromConfig() {
        var config = SamlConfig.of("https://idp.example.com", "https://idp.example.com/sso");
        String xml = SamlLogout.generateLogoutRequest(config, "https://sp.example.com",
                "alice@example.com", null);
        assertThat(xml).contains("<samlp:LogoutRequest");
        assertThat(xml).contains("Destination=\"https://idp.example.com/sso\"");
    }

    @Test
    void testGenerateLogoutRequestNullIssuerThrows() {
        String nullStr = null;
        assertThatThrownBy(() -> SamlLogout.generateLogoutRequest(nullStr, "dest", "name", "session"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testGenerateLogoutRequestNullDestThrows() {
        String nullStr = null;
        assertThatThrownBy(() -> SamlLogout.generateLogoutRequest("issuer", nullStr, "name", "session"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testGenerateLogoutRequestNullNameIdThrows() {
        String nullStr = null;
        assertThatThrownBy(() -> SamlLogout.generateLogoutRequest("issuer", "dest", nullStr, "session"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testParseLogoutResponseSuccess() {
        String xml = """
                <samlp:LogoutResponse xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                                      xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                                      ID="_resp-123" InResponseTo="_req-456" Version="2.0">
                    <saml:Issuer>https://idp.example.com</saml:Issuer>
                    <samlp:Status>
                        <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/>
                    </samlp:Status>
                </samlp:LogoutResponse>
                """;

        var result = SamlLogout.parseLogoutResponse(xml);
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("_resp-123");
        assertThat(result.get().inResponseTo()).isEqualTo("_req-456");
        assertThat(result.get().issuer()).isEqualTo("https://idp.example.com");
        assertThat(result.get().statusCode()).isEqualTo("urn:oasis:names:tc:SAML:2.0:status:Success");
        assertThat(result.get().success()).isTrue();
    }

    @Test
    void testParseLogoutResponseFailure() {
        String xml = """
                <samlp:LogoutResponse ID="_resp-789" Version="2.0">
                    <saml:Issuer>https://idp.example.com</saml:Issuer>
                    <samlp:Status>
                        <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Requester"/>
                    </samlp:Status>
                </samlp:LogoutResponse>
                """;

        var result = SamlLogout.parseLogoutResponse(xml);
        assertThat(result).isPresent();
        assertThat(result.get().success()).isFalse();
    }

    @Test
    void testParseLogoutResponseNull() {
        assertThat(SamlLogout.parseLogoutResponse(null)).isEmpty();
    }

    @Test
    void testParseLogoutResponseBlank() {
        assertThat(SamlLogout.parseLogoutResponse("  ")).isEmpty();
    }

    @Test
    void testXmlEscaping() {
        String xml = SamlLogout.generateLogoutRequest(
                "https://sp.example.com",
                "https://idp.example.com/slo?a=1&b=2",
                "user@example.com",
                null);
        assertThat(xml).contains("&amp;");
    }
}
