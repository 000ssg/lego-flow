package ssg.legoflow.network.ldap.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LdapMessagesTest {

    @Test
    void testBindRequestSimple() {
        var auth = new BindRequest.AuthenticationChoice.Simple("password");
        var req = new BindRequest(3, "cn=admin", auth);
        assertThat(req.version()).isEqualTo(3);
    }

    @Test
    void testBindResponse() {
        var resp = new BindResponse(LdapResult.success(), null);
        assertThat(resp.result().resultCode()).isEqualTo(LdapResultCode.SUCCESS);
    }

    @Test
    void testSearchResultEntry() {
        var attr = new LdapAttribute("cn", java.util.List.of(new byte[]{'J'}));
        var entry = new SearchResultEntry("cn=John", java.util.List.of(attr));
    }

    @Test
    void testSearchResultDone() {
        var done = new SearchResultDone(LdapResult.success());
    }

    @Test
    void testAddRequest() {
        var attr = new LdapAttribute("oc", java.util.List.of(new byte[]{'p'}));
        var req = new AddRequest("cn=John", java.util.List.of(attr));
    }

    @Test
    void testDeleteRequest() {
        var req = new DeleteRequest("cn=John");
    }

    @Test
    void testAbandonRequest() {
        var req = new AbandonRequest(42);
        assertThat(req.abandonedMessageId()).isEqualTo(42);
    }

    @Test
    void testSearchScopeValues() {
        assertThat(SearchScope.BASE_OBJECT.value()).isZero();
        assertThat(SearchScope.WHOLE_SUBTREE.value()).isEqualTo(2);
    }

    @Test
    void testDerefAliasesValues() {
        assertThat(DerefAliases.NEVER_DEREF_ALIASES.value()).isZero();
        assertThat(DerefAliases.DEREF_ALWAYS.value()).isEqualTo(3);
    }

    @Test
    void testLdapResultCodeValues() {
        assertThat(LdapResultCode.SUCCESS.code()).isZero();
        assertThat(LdapResultCode.OPERATIONS_ERROR.code()).isEqualTo(1);
    }

    @Test
    void testLdapMessageOf() {
        var msg = LdapMessage.of(5, BindRequest.anonymous());
        assertThat(msg.messageId()).isEqualTo(5);
    }

    @Test
    void testModifyOperation() {
        assertThat(ModifyRequest.ModifyOperation.ADD).isNotNull();
    }

    @Test
    void testExtendedRequest() {
        var req = new ExtendedRequest("1.3.6.1.4.1.1466.20037", null);
    }

    @Test
    void testUnbindRequest() {
        var req = UnbindRequest.INSTANCE;
        assertThat(req).isNotNull();
    }

    @Test
    void testLdapResultSuccess() {
        var result = LdapResult.success();
        assertThat(result.resultCode()).isEqualTo(LdapResultCode.SUCCESS);
    }

    @Test
    void testIntermediateResponse() {
        var resp = new IntermediateResponse("1.2.3.4", null);
    }
}
