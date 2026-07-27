package ssg.legoflow.http.auth.oidc;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class UserInfoTest {

    @Test
    void testFromJson() {
        String json = "{\"sub\":\"user123\",\"name\":\"Alice Smith\",\"email\":\"alice@example.com\"}";
        var userInfo = UserInfo.fromJson(json);
        assertThat(userInfo.getSubject()).isEqualTo("user123");
        assertThat(userInfo.getName()).isEqualTo("Alice Smith");
        assertThat(userInfo.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void testAllStandardClaims() {
        String json = """
                {"sub":"uid","name":"Full Name","given_name":"First","family_name":"Last",\
                "preferred_username":"preferred","email":"e@x.com","email_verified":"true",\
                "picture":"https://pic.com/a.jpg","locale":"en-US","zoneinfo":"America/NY",\
                "phone_number":"+1234567890"}""";
        var userInfo = UserInfo.fromJson(json);
        assertThat(userInfo.getSubject()).isEqualTo("uid");
        assertThat(userInfo.getName()).isEqualTo("Full Name");
        assertThat(userInfo.getGivenName()).isEqualTo("First");
        assertThat(userInfo.getFamilyName()).isEqualTo("Last");
        assertThat(userInfo.getPreferredUsername()).isEqualTo("preferred");
        assertThat(userInfo.getEmail()).isEqualTo("e@x.com");
        assertThat(userInfo.getEmailVerified()).isEqualTo("true");
        assertThat(userInfo.getPicture()).isEqualTo("https://pic.com/a.jpg");
        assertThat(userInfo.getLocale()).isEqualTo("en-US");
        assertThat(userInfo.getZoneinfo()).isEqualTo("America/NY");
        assertThat(userInfo.getPhoneNumber()).isEqualTo("+1234567890");
    }

    @Test
    void testGetStringClaimNull() {
        var userInfo = new UserInfo(Map.of("sub", "uid"));
        assertThat(userInfo.getStringClaim("missing")).isNull();
    }

    @Test
    void testGetClaims() {
        var claims = Map.<String, Object>of("sub", "uid", "name", "Test");
        var userInfo = new UserInfo(claims);
        assertThat(userInfo.getClaims()).containsEntry("sub", "uid");
        assertThat(userInfo.getClaims()).containsEntry("name", "Test");
    }

    @Test
    void testGetClaimsImmutable() {
        var userInfo = new UserInfo(Map.of("sub", "uid"));
        assertThatThrownBy(() -> userInfo.getClaims().put("key", "val"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testToJson() {
        var userInfo = new UserInfo(Map.of("sub", "uid", "name", "Test User"));
        String json = userInfo.toJson();
        assertThat(json).contains("\"sub\":\"uid\"");
        assertThat(json).contains("\"name\":\"Test User\"");
    }

    @Test
    void testConstructorCopiesMap() {
        var claims = new java.util.HashMap<String, Object>();
        claims.put("sub", "uid");
        var userInfo = new UserInfo(claims);
        claims.put("extra", "added");
        assertThat(userInfo.getClaims()).doesNotContainKey("extra");
    }
}
