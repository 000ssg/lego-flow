package ssg.legoflow.http.auth.saml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class SamlAssertionParserTest {

    private SamlAssertionParser parser;
    private SamlConfig config;

    @BeforeEach
    void setUp() {
        config = SamlConfig.of("https://idp.example.com", "https://idp.example.com/sso");
        parser = new SamlAssertionParser(config);
    }

    @Test
    void testParseSimpleAssertion() {
        String xml = """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                                xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
                    <saml:Issuer>https://idp.example.com</saml:Issuer>
                    <saml:Assertion>
                        <saml:Subject>
                            <saml:NameID>alice@example.com</saml:NameID>
                        </saml:Subject>
                    </saml:Assertion>
                </samlp:Response>
                """;
        var result = parser.parseResponse(xml);
        assertThat(result).isPresent();
        assertThat(result.get().nameId()).isEqualTo("alice@example.com");
        assertThat(result.get().issuer()).isEqualTo("https://idp.example.com");
    }

    @Test
    void testParseWithAttributes() {
        String xml = """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                                xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
                    <saml:Issuer>https://idp.example.com</saml:Issuer>
                    <saml:Assertion>
                        <saml:Subject>
                            <saml:NameID>bob@example.com</saml:NameID>
                        </saml:Subject>
                        <saml:AttributeStatement>
                            <saml:Attribute Name="email">
                                <saml:AttributeValue>bob@example.com</saml:AttributeValue>
                            </saml:Attribute>
                            <saml:Attribute Name="displayName">
                                <saml:AttributeValue>Bob Smith</saml:AttributeValue>
                            </saml:Attribute>
                        </saml:AttributeStatement>
                    </saml:Assertion>
                </samlp:Response>
                """;
        var result = parser.parseResponse(xml);
        assertThat(result).isPresent();
        assertThat(result.get().attributes()).containsEntry("email", "bob@example.com");
        assertThat(result.get().attributes()).containsEntry("displayName", "Bob Smith");
    }

    @Test
    void testParseWithConditions() {
        String xml = """
                <samlp:Response xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
                    <saml:Issuer>https://idp.example.com</saml:Issuer>
                    <saml:Assertion>
                        <saml:Subject>
                            <saml:NameID>alice@example.com</saml:NameID>
                        </saml:Subject>
                        <saml:Conditions NotBefore="2024-01-01T00:00:00Z" NotOnOrAfter="2024-01-01T01:00:00Z"/>
                    </saml:Assertion>
                </samlp:Response>
                """;
        var result = parser.parseResponse(xml);
        assertThat(result).isPresent();
        assertThat(result.get().notBefore()).isEqualTo("2024-01-01T00:00:00Z");
        assertThat(result.get().notOnOrAfter()).isEqualTo("2024-01-01T01:00:00Z");
    }

    @Test
    void testParseNoNameId() {
        String xml = """
                <samlp:Response xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
                    <saml:Issuer>https://idp.example.com</saml:Issuer>
                    <saml:Assertion>
                        <saml:Subject/>
                    </saml:Assertion>
                </samlp:Response>
                """;
        var result = parser.parseResponse(xml);
        assertThat(result).isEmpty();
    }

    @Test
    void testParseIssuerMismatch() {
        String xml = """
                <samlp:Response xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
                    <saml:Issuer>https://other-idp.example.com</saml:Issuer>
                    <saml:Assertion>
                        <saml:Subject>
                            <saml:NameID>alice@example.com</saml:NameID>
                        </saml:Subject>
                    </saml:Assertion>
                </samlp:Response>
                """;
        var result = parser.parseResponse(xml);
        assertThat(result).isEmpty();
    }

    @Test
    void testParseNull() {
        assertThat(parser.parseResponse(null)).isEmpty();
    }

    @Test
    void testParseBlank() {
        assertThat(parser.parseResponse("  ")).isEmpty();
    }

    @Test
    void testParseBase64Response() {
        String xml = """
                <samlp:Response xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
                    <saml:Issuer>https://idp.example.com</saml:Issuer>
                    <saml:Assertion>
                        <saml:Subject>
                            <saml:NameID>alice@example.com</saml:NameID>
                        </saml:Subject>
                    </saml:Assertion>
                </samlp:Response>
                """;
        String base64 = Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
        var result = parser.parseBase64Response(base64);
        assertThat(result).isPresent();
        assertThat(result.get().nameId()).isEqualTo("alice@example.com");
    }

    @Test
    void testParseBase64Invalid() {
        assertThat(parser.parseBase64Response("not-valid-base64!!!")).isEmpty();
    }

    @Test
    void testToPrincipal() {
        String xml = """
                <samlp:Response xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
                    <saml:Issuer>https://idp.example.com</saml:Issuer>
                    <saml:Assertion>
                        <saml:Subject>
                            <saml:NameID>alice@example.com</saml:NameID>
                        </saml:Subject>
                        <saml:AttributeStatement>
                            <saml:Attribute Name="Role">
                                <saml:AttributeValue>admin,user</saml:AttributeValue>
                            </saml:Attribute>
                        </saml:AttributeStatement>
                    </saml:Assertion>
                </samlp:Response>
                """;
        var result = parser.parseResponse(xml);
        assertThat(result).isPresent();
        var principal = result.get().toPrincipal();
        assertThat(principal.getName()).isEqualTo("alice@example.com");
        assertThat(principal.getRoles()).containsExactlyInAnyOrder("admin", "user");
    }

    @Test
    void testToPrincipalNoRoles() {
        String xml = """
                <samlp:Response xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
                    <saml:Issuer>https://idp.example.com</saml:Issuer>
                    <saml:Assertion>
                        <saml:Subject>
                            <saml:NameID>bob@example.com</saml:NameID>
                        </saml:Subject>
                    </saml:Assertion>
                </samlp:Response>
                """;
        var result = parser.parseResponse(xml);
        assertThat(result).isPresent();
        var principal = result.get().toPrincipal();
        assertThat(principal.getName()).isEqualTo("bob@example.com");
        assertThat(principal.getRoles()).isEmpty();
    }

    @Test
    void testGetConfig() {
        assertThat(parser.getConfig()).isEqualTo(config);
    }

    @Test
    void testNullConfigThrows() {
        assertThatThrownBy(() -> new SamlAssertionParser(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testExtractElementContent() {
        String xml = "<saml:NameID>test-value</saml:NameID>";
        assertThat(SamlAssertionParser.extractElementContent(xml, "NameID")).isEqualTo("test-value");
    }

    @Test
    void testExtractElementContentNoPrefix() {
        String xml = "<NameID>test-value</NameID>";
        assertThat(SamlAssertionParser.extractElementContent(xml, "NameID")).isEqualTo("test-value");
    }

    @Test
    void testExtractElementContentNotFound() {
        String xml = "<other>value</other>";
        assertThat(SamlAssertionParser.extractElementContent(xml, "NameID")).isNull();
    }

    @Test
    void testExtractAttribute() {
        String xml = "<saml:Conditions NotBefore=\"2024-01-01\" NotOnOrAfter=\"2024-01-02\"/>";
        assertThat(SamlAssertionParser.extractAttribute(xml, "Conditions", "NotBefore"))
                .isEqualTo("2024-01-01");
    }

    @Test
    void testExtractAttributeNotFound() {
        String xml = "<saml:Conditions/>";
        assertThat(SamlAssertionParser.extractAttribute(xml, "Conditions", "NotBefore")).isNull();
    }

    @Test
    void testExtractAttributes() {
        String xml = """
                <saml:AttributeStatement>
                    <saml:Attribute Name="email">
                        <saml:AttributeValue>test@example.com</saml:AttributeValue>
                    </saml:Attribute>
                    <saml:Attribute Name="name">
                        <saml:AttributeValue>Test User</saml:AttributeValue>
                    </saml:Attribute>
                </saml:AttributeStatement>
                """;
        var attrs = SamlAssertionParser.extractAttributes(xml);
        assertThat(attrs).hasSize(2);
        assertThat(attrs).containsEntry("email", "test@example.com");
        assertThat(attrs).containsEntry("name", "Test User");
    }

    @Test
    void testExtractAttributesEmpty() {
        String xml = "<root>no attributes here</root>";
        var attrs = SamlAssertionParser.extractAttributes(xml);
        assertThat(attrs).isEmpty();
    }

    @Test
    void testParseWithSaml2Prefix() {
        String xml = """
                <samlp:Response>
                    <saml2:Issuer>https://idp.example.com</saml2:Issuer>
                    <saml2:Assertion>
                        <saml2:Subject>
                            <saml2:NameID>carol@example.com</saml2:NameID>
                        </saml2:Subject>
                    </saml2:Assertion>
                </samlp:Response>
                """;
        var result = parser.parseResponse(xml);
        assertThat(result).isPresent();
        assertThat(result.get().nameId()).isEqualTo("carol@example.com");
    }

    @Test
    void testToPrincipalWithMemberOfRole() {
        String xml = """
                <samlp:Response xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
                    <saml:Issuer>https://idp.example.com</saml:Issuer>
                    <saml:Assertion>
                        <saml:Subject>
                            <saml:NameID>dave@example.com</saml:NameID>
                        </saml:Subject>
                        <saml:AttributeStatement>
                            <saml:Attribute Name="memberOf">
                                <saml:AttributeValue>editors</saml:AttributeValue>
                            </saml:Attribute>
                        </saml:AttributeStatement>
                    </saml:Assertion>
                </samlp:Response>
                """;
        var result = parser.parseResponse(xml);
        assertThat(result).isPresent();
        var principal = result.get().toPrincipal();
        assertThat(principal.getRoles()).contains("editors");
    }
}
