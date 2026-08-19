package ssg.legoflow.http.auth.saml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;
class SamlSignatureValidatorTest {

    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        var kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        keyPair = kpg.generateKeyPair();
    }

    @Test
    void testConstructorWithPublicKey() {
        var validator = new SamlSignatureValidator(keyPair.getPublic());
        assertThat(validator.getPublicKey()).isEqualTo(keyPair.getPublic());
    }

    @Test
    void testNullKeyThrows() {
        assertThatThrownBy(() -> new SamlSignatureValidator(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testExtractSignatureValue() {
        String xml = "<ds:Signature><ds:SignatureValue>abc123==</ds:SignatureValue></ds:Signature>";
        assertThat(SamlSignatureValidator.extractSignatureValue(xml)).isEqualTo("abc123==");
    }

    @Test
    void testExtractSignatureValueNoPrefix() {
        String xml = "<Signature><SignatureValue>xyz789</SignatureValue></Signature>";
        assertThat(SamlSignatureValidator.extractSignatureValue(xml)).isEqualTo("xyz789");
    }

    @Test
    void testExtractSignatureValueNotFound() {
        String xml = "<root>no signature here</root>";
        assertThat(SamlSignatureValidator.extractSignatureValue(xml)).isNull();
    }

    @Test
    void testExtractDigestValue() {
        String xml = "<ds:DigestValue>digest123</ds:DigestValue>";
        assertThat(SamlSignatureValidator.extractDigestValue(xml)).isEqualTo("digest123");
    }

    @Test
    void testExtractSignatureAlgorithm() {
        String xml = "<ds:SignatureMethod Algorithm=\"http://www.w3.org/2001/04/xmldsig-more#rsa-sha256\"/>";
        assertThat(SamlSignatureValidator.extractSignatureAlgorithm(xml))
                .isEqualTo("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256");
    }

    @Test
    void testExtractSignedInfo() {
        String xml = "<ds:Signature><ds:SignedInfo><ds:Reference/></ds:SignedInfo></ds:Signature>";
        assertThat(SamlSignatureValidator.extractSignedInfo(xml))
                .isEqualTo("<ds:SignedInfo><ds:Reference/></ds:SignedInfo>");
    }

    @Test
    void testHasValidSignature() {
        var validator = new SamlSignatureValidator(keyPair.getPublic());
        String xml = """
                <samlp:Response>
                    <ds:Signature>
                        <ds:SignedInfo><ds:Reference/></ds:SignedInfo>
                        <ds:SignatureValue>abc==</ds:SignatureValue>
                        <ds:Reference><ds:DigestValue>xyz==</ds:DigestValue></ds:Reference>
                    </ds:Signature>
                </samlp:Response>
                """;
        assertThat(validator.hasValidSignature(xml)).isTrue();
    }

    @Test
    void testHasNoSignature() {
        var validator = new SamlSignatureValidator(keyPair.getPublic());
        assertThat(validator.hasValidSignature("<samlp:Response/>")).isFalse();
        assertThat(validator.hasValidSignature(null)).isFalse();
    }

    @Test
    void testValidateWithRealSignature() throws Exception {
        // Create a signed SAML-like XML
        String signedInfo = "<ds:SignedInfo><ds:Reference/></ds:SignedInfo>";

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(keyPair.getPrivate());
        sig.update(signedInfo.getBytes());
        byte[] sigBytes = sig.sign();
        String sigBase64 = Base64.getEncoder().encodeToString(sigBytes);

        String xml = """
                <samlp:Response>
                    <ds:Signature>
                        <ds:SignedInfo><ds:Reference/></ds:SignedInfo>
                        <ds:SignatureValue>%s</ds:SignatureValue>
                        <ds:DigestValue>test</ds:DigestValue>
                    </ds:Signature>
                </samlp:Response>
                """.formatted(sigBase64);

        var validator = new SamlSignatureValidator(keyPair.getPublic());
        boolean valid = validator.validate(xml);
        assertThat(valid).isTrue();
    }

    @Test
    void testValidateInvalidSignature() throws Exception {
        String xml = """
                <samlp:Response>
                    <ds:Signature>
                        <ds:SignedInfo><ds:Reference/></ds:SignedInfo>
                        <ds:SignatureValue>aW52YWxpZA==</ds:SignatureValue>
                    </ds:Signature>
                </samlp:Response>
                """;
        var validator = new SamlSignatureValidator(keyPair.getPublic());
        boolean valid = validator.validate(xml);
        assertThat(valid).isFalse();
    }

    @Test
    void testValidateNullXml() {
        var validator = new SamlSignatureValidator(keyPair.getPublic());
        assertThat(validator.validate(null)).isFalse();
        assertThat(validator.validate("")).isFalse();
    }

    @Test
    void testValidateNoSignatureElement() {
        var validator = new SamlSignatureValidator(keyPair.getPublic());
        assertThat(validator.validate("<samlp:Response/>")).isFalse();
    }

    @Test
    void testExtractSignatureAlgorithmNotFound() {
        assertThat(SamlSignatureValidator.extractSignatureAlgorithm("<root/>")).isNull();
    }
}
