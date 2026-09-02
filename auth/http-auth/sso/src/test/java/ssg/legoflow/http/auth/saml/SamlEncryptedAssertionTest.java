package ssg.legoflow.http.auth.saml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;
class SamlEncryptedAssertionTest {

    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        var kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        keyPair = kpg.generateKeyPair();
    }

    @Test
    void testConstructorNullThrows() {
        assertThatThrownBy(() -> new SamlEncryptedAssertion(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testDecryptNullReturnsEmpty() {
        var handler = new SamlEncryptedAssertion(keyPair.getPrivate());
        assertThat(handler.decrypt(null)).isEmpty();
        assertThat(handler.decrypt("")).isEmpty();
    }

    @Test
    void testDecryptWithValidEncryptedAssertion() throws Exception {
        String assertionXml = "<saml:Assertion><saml:Subject><saml:NameID>alice</saml:NameID></saml:Subject></saml:Assertion>";

        // Generate random AES key
        byte[] aesKey = new byte[16]; // AES-128
        new SecureRandom().nextBytes(aesKey);

        // Encrypt the assertion with AES-CBC
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        aesCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
        byte[] encrypted = aesCipher.doFinal(assertionXml.getBytes());

        // Combine IV + ciphertext
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        String encDataBase64 = Base64.getEncoder().encodeToString(combined);

        // Encrypt the AES key with RSA-OAEP
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
        rsaCipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublic());
        byte[] encryptedKey = rsaCipher.doFinal(aesKey);
        String encKeyBase64 = Base64.getEncoder().encodeToString(encryptedKey);

        // Build EncryptedAssertion XML
        String xml = """
                <samlp:Response>
                    <xenc:EncryptedData xmlns:xenc="http://www.w3.org/2001/04/xmlenc#">
                        <xenc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes128-cbc"/>
                        <ds:KeyInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
                            <xenc:EncryptedKey>
                                <xenc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#rsa-oaep-mgf1p"/>
                                <xenc:CipherData>
                                    <xenc:CipherValue>%s</xenc:CipherValue>
                                </xenc:CipherData>
                            </xenc:EncryptedKey>
                        </ds:KeyInfo>
                        <xenc:CipherData>
                            <xenc:CipherValue>%s</xenc:CipherValue>
                        </xenc:CipherData>
                    </xenc:EncryptedData>
                </samlp:Response>
                """.formatted(encKeyBase64, encDataBase64);

        var handler = new SamlEncryptedAssertion(keyPair.getPrivate());
        var result = handler.decrypt(xml);
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(assertionXml);
    }

    @Test
    void testDecryptNoEncryptedKey() {
        String xml = "<samlp:Response><xenc:EncryptedData/></samlp:Response>";
        var handler = new SamlEncryptedAssertion(keyPair.getPrivate());
        assertThat(handler.decrypt(xml)).isEmpty();
    }

    @Test
    void testExtractKeyAlgorithm() {
        String xml = """
                <xenc:EncryptedKey>
                    <xenc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#rsa-oaep-mgf1p"/>
                </xenc:EncryptedKey>
                """;
        assertThat(SamlEncryptedAssertion.extractKeyAlgorithm(xml))
                .isEqualTo("http://www.w3.org/2001/04/xmlenc#rsa-oaep-mgf1p");
    }

    @Test
    void testExtractDataAlgorithm() {
        String xml = """
                <xenc:EncryptedData>
                    <xenc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes128-cbc"/>
                </xenc:EncryptedData>
                """;
        assertThat(SamlEncryptedAssertion.extractDataAlgorithm(xml))
                .isEqualTo("http://www.w3.org/2001/04/xmlenc#aes128-cbc");
    }

    @Test
    void testDecryptDataAesCbc() throws Exception {
        byte[] aesKey = new byte[16];
        new SecureRandom().nextBytes(aesKey);
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);

        String plaintext = "Hello, World!";
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(plaintext.getBytes());

        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

        String result = SamlEncryptedAssertion.decryptData(aesKey, combined, "aes128-cbc");
        assertThat(result).isEqualTo(plaintext);
    }

    @Test
    void testExtractEncryptedKey() {
        String xml = """
                <xenc:EncryptedKey>
                    <xenc:CipherData>
                        <xenc:CipherValue>abc123==</xenc:CipherValue>
                    </xenc:CipherData>
                </xenc:EncryptedKey>
                """;
        assertThat(SamlEncryptedAssertion.extractEncryptedKey(xml)).isEqualTo("abc123==");
    }

    @Test
    void testExtractEncryptedKeyNotFound() {
        assertThat(SamlEncryptedAssertion.extractEncryptedKey("<root/>")).isNull();
    }
}
