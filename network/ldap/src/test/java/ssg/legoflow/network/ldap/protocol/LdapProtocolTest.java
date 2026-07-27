package ssg.legoflow.network.ldap.protocol;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.ldap.control.LdapControl;
import ssg.legoflow.network.ldap.filter.SearchFilter;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for LDAP protocol message records.
 *
 * @since 1.0.0
 */
class LdapProtocolTest {

    @Test
    void testLdapMessageCreation() {
        LdapMessage msg = LdapMessage.of(1, BindRequest.anonymous());
        assertThat(msg.messageId()).isEqualTo(1);
        assertThat(msg.protocolOp()).isInstanceOf(BindRequest.class);
        assertThat(msg.controls()).isEmpty();
    }

    @Test
    void testLdapMessageWithControls() {
        LdapControl ctrl = LdapControl.of("1.2.3", false);
        LdapMessage msg = LdapMessage.of(1, BindRequest.anonymous(), List.of(ctrl));
        assertThat(msg.controls()).hasSize(1);
    }

    @Test
    void testLdapMessageNegativeIdThrows() {
        assertThatThrownBy(() -> LdapMessage.of(-1, BindRequest.anonymous()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testBindRequestSimple() {
        BindRequest req = BindRequest.simple("cn=admin", "pass");
        assertThat(req.version()).isEqualTo(3);
        assertThat(req.name()).isEqualTo("cn=admin");
        assertThat(req.tagNumber()).isEqualTo(0);
    }

    @Test
    void testBindRequestAnonymous() {
        BindRequest req = BindRequest.anonymous();
        assertThat(req.name()).isEmpty();
        assertThat(req.authentication()).isInstanceOf(BindRequest.AuthenticationChoice.Simple.class);
    }

    @Test
    void testBindRequestSasl() {
        BindRequest req = BindRequest.sasl("cn=user", "GSSAPI", new byte[]{1, 2, 3});
        assertThat(req.authentication()).isInstanceOf(BindRequest.AuthenticationChoice.Sasl.class);
    }

    @Test
    void testBindResponseSuccess() {
        BindResponse resp = BindResponse.success();
        assertThat(resp.result().resultCode()).isEqualTo(LdapResultCode.SUCCESS);
        assertThat(resp.tagNumber()).isEqualTo(1);
    }

    @Test
    void testSearchRequestSubtree() {
        SearchFilter filter = SearchFilter.present("objectClass");
        SearchRequest req = SearchRequest.subtree("dc=example", filter);
        assertThat(req.scope()).isEqualTo(SearchScope.WHOLE_SUBTREE);
        assertThat(req.sizeLimit()).isZero();
        assertThat(req.tagNumber()).isEqualTo(3);
    }

    @Test
    void testSearchResultEntry() {
        SearchResultEntry entry = new SearchResultEntry("cn=John,dc=example",
                List.of(LdapAttribute.of("cn", "John")));
        assertThat(entry.objectName()).isEqualTo("cn=John,dc=example");
        assertThat(entry.tagNumber()).isEqualTo(4);
    }

    @Test
    void testLdapAttribute() {
        LdapAttribute attr = LdapAttribute.of("cn", "John", "Jane");
        assertThat(attr.type()).isEqualTo("cn");
        assertThat(attr.values()).hasSize(2);
        assertThat(attr.firstValueAsString()).isEqualTo("John");
        assertThat(attr.valuesAsStrings()).containsExactly("John", "Jane");
    }

    @Test
    void testModifyRequest() {
        ModifyRequest req = new ModifyRequest("cn=John", List.of(
                new ModifyRequest.Change(ModifyRequest.ModifyOperation.REPLACE,
                        LdapAttribute.of("mail", "j@example.com"))
        ));
        assertThat(req.tagNumber()).isEqualTo(6);
        assertThat(req.changes()).hasSize(1);
    }

    @Test
    void testModifyDnRequest() {
        ModifyDnRequest req = ModifyDnRequest.rename("cn=Old,dc=example", "cn=New", true);
        assertThat(req.tagNumber()).isEqualTo(12);
        assertThat(req.newSuperior()).isNull();
    }

    @Test
    void testCompareRequest() {
        CompareRequest req = CompareRequest.of("cn=John", "cn", "John");
        assertThat(req.tagNumber()).isEqualTo(14);
    }

    @Test
    void testAbandonRequest() {
        AbandonRequest req = new AbandonRequest(42);
        assertThat(req.abandonedMessageId()).isEqualTo(42);
        assertThat(req.tagNumber()).isEqualTo(16);
    }

    @Test
    void testExtendedRequestStartTls() {
        ExtendedRequest req = ExtendedRequest.startTls();
        assertThat(req.requestName()).isEqualTo(ExtendedRequest.START_TLS_OID);
        assertThat(req.requestValue()).isNull();
        assertThat(req.tagNumber()).isEqualTo(23);
    }

    @Test
    void testLdapResultCode() {
        assertThat(LdapResultCode.of(0)).isEqualTo(LdapResultCode.SUCCESS);
        assertThat(LdapResultCode.of(49)).isEqualTo(LdapResultCode.INVALID_CREDENTIALS);
        assertThatThrownBy(() -> LdapResultCode.of(999))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSearchScope() {
        assertThat(SearchScope.of(0)).isEqualTo(SearchScope.BASE_OBJECT);
        assertThat(SearchScope.of(1)).isEqualTo(SearchScope.SINGLE_LEVEL);
        assertThat(SearchScope.of(2)).isEqualTo(SearchScope.WHOLE_SUBTREE);
    }

    @Test
    void testDerefAliases() {
        assertThat(DerefAliases.of(0)).isEqualTo(DerefAliases.NEVER_DEREF_ALIASES);
        assertThat(DerefAliases.of(3)).isEqualTo(DerefAliases.DEREF_ALWAYS);
    }

    @Test
    void testDeleteRequest() {
        DeleteRequest req = new DeleteRequest("cn=entry,dc=example");
        assertThat(req.entry()).isEqualTo("cn=entry,dc=example");
        assertThat(req.tagNumber()).isEqualTo(10);
    }

    @Test
    void testSearchResultReference() {
        SearchResultReference ref = new SearchResultReference(
                List.of("ldap://other.example.com/dc=example"));
        assertThat(ref.uris()).hasSize(1);
        assertThat(ref.tagNumber()).isEqualTo(19);
    }

    @Test
    void testIntermediateResponse() {
        IntermediateResponse resp = new IntermediateResponse("1.2.3.4", new byte[]{1});
        assertThat(resp.responseName()).isEqualTo("1.2.3.4");
        assertThat(resp.tagNumber()).isEqualTo(25);
    }
}
