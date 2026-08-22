package ssg.legoflow.network.ldap.codec;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.ldap.control.LdapControl;
import ssg.legoflow.network.ldap.filter.SearchFilter;
import ssg.legoflow.network.ldap.protocol.*;
import java.nio.ByteBuffer;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link LdapCodec} BER encoding/decoding round-trips.
 *
 * @since 0.1.0
 */
class LdapCodecTest {

    @Test
    void testBindRequestRoundTrip() {
        LdapMessage original = LdapMessage.of(1, BindRequest.simple("cn=admin,dc=example,dc=com", "secret"));
        byte[] encoded = LdapCodec.encodeToBytes(original);
        LdapMessage decoded = LdapCodec.decode(encoded);

        assertThat(decoded.messageId()).isEqualTo(1);
        assertThat(decoded.protocolOp()).isInstanceOf(BindRequest.class);
        BindRequest bind = (BindRequest) decoded.protocolOp();
        assertThat(bind.version()).isEqualTo(3);
        assertThat(bind.name()).isEqualTo("cn=admin,dc=example,dc=com");
        assertThat(bind.authentication()).isInstanceOf(BindRequest.AuthenticationChoice.Simple.class);
        assertThat(((BindRequest.AuthenticationChoice.Simple) bind.authentication()).password())
                .isEqualTo("secret");
    }

    @Test
    void testBindResponseRoundTrip() {
        LdapMessage original = LdapMessage.of(1, BindResponse.success());
        byte[] encoded = LdapCodec.encodeToBytes(original);
        LdapMessage decoded = LdapCodec.decode(encoded);

        assertThat(decoded.protocolOp()).isInstanceOf(BindResponse.class);
        BindResponse resp = (BindResponse) decoded.protocolOp();
        assertThat(resp.result().resultCode()).isEqualTo(LdapResultCode.SUCCESS);
    }

    @Test
    void testSearchRequestRoundTrip() {
        SearchFilter filter = SearchFilter.and(
                SearchFilter.equalityMatch("objectClass", "person"),
                SearchFilter.present("cn")
        );
        SearchRequest search = new SearchRequest("dc=example,dc=com",
                SearchScope.WHOLE_SUBTREE, DerefAliases.NEVER_DEREF_ALIASES,
                100, 30, false, filter, List.of("cn", "mail"));

        LdapMessage original = LdapMessage.of(2, search);
        byte[] encoded = LdapCodec.encodeToBytes(original);
        LdapMessage decoded = LdapCodec.decode(encoded);

        assertThat(decoded.messageId()).isEqualTo(2);
        assertThat(decoded.protocolOp()).isInstanceOf(SearchRequest.class);
        SearchRequest decodedSearch = (SearchRequest) decoded.protocolOp();
        assertThat(decodedSearch.baseObject()).isEqualTo("dc=example,dc=com");
        assertThat(decodedSearch.scope()).isEqualTo(SearchScope.WHOLE_SUBTREE);
        assertThat(decodedSearch.sizeLimit()).isEqualTo(100);
        assertThat(decodedSearch.timeLimit()).isEqualTo(30);
        assertThat(decodedSearch.attributes()).containsExactly("cn", "mail");
    }

    @Test
    void testSearchResultEntryRoundTrip() {
        SearchResultEntry entry = new SearchResultEntry("cn=John,dc=example,dc=com",
                List.of(
                        LdapAttribute.of("cn", "John"),
                        LdapAttribute.of("mail", "john@example.com")
                ));

        LdapMessage original = LdapMessage.of(2, entry);
        byte[] encoded = LdapCodec.encodeToBytes(original);
        LdapMessage decoded = LdapCodec.decode(encoded);

        assertThat(decoded.protocolOp()).isInstanceOf(SearchResultEntry.class);
        SearchResultEntry decodedEntry = (SearchResultEntry) decoded.protocolOp();
        assertThat(decodedEntry.objectName()).isEqualTo("cn=John,dc=example,dc=com");
        assertThat(decodedEntry.attributes()).hasSize(2);
        assertThat(decodedEntry.attributes().get(0).type()).isEqualTo("cn");
        assertThat(decodedEntry.attributes().get(0).firstValueAsString()).isEqualTo("John");
    }

    @Test
    void testSearchResultDoneRoundTrip() {
        LdapMessage original = LdapMessage.of(2, SearchResultDone.success());
        byte[] encoded = LdapCodec.encodeToBytes(original);
        LdapMessage decoded = LdapCodec.decode(encoded);

        assertThat(decoded.protocolOp()).isInstanceOf(SearchResultDone.class);
        SearchResultDone done = (SearchResultDone) decoded.protocolOp();
        assertThat(done.result().resultCode()).isEqualTo(LdapResultCode.SUCCESS);
    }

    @Test
    void testAddRequestRoundTrip() {
        AddRequest add = new AddRequest("cn=New,dc=example,dc=com",
                List.of(LdapAttribute.of("cn", "New"),
                        LdapAttribute.of("objectClass", "person")));

        LdapMessage original = LdapMessage.of(3, add);
        byte[] encoded = LdapCodec.encodeToBytes(original);
        LdapMessage decoded = LdapCodec.decode(encoded);

        assertThat(decoded.protocolOp()).isInstanceOf(AddRequest.class);
        AddRequest decodedAdd = (AddRequest) decoded.protocolOp();
        assertThat(decodedAdd.entry()).isEqualTo("cn=New,dc=example,dc=com");
        assertThat(decodedAdd.attributes()).hasSize(2);
    }

    @Test
    void testDeleteRequestRoundTrip() {
        LdapMessage original = LdapMessage.of(4, new DeleteRequest("cn=Old,dc=example,dc=com"));
        byte[] encoded = LdapCodec.encodeToBytes(original);
        LdapMessage decoded = LdapCodec.decode(encoded);

        assertThat(decoded.protocolOp()).isInstanceOf(DeleteRequest.class);
        DeleteRequest del = (DeleteRequest) decoded.protocolOp();
        assertThat(del.entry()).isEqualTo("cn=Old,dc=example,dc=com");
    }

    @Test
    void testModifyRequestRoundTrip() {
        ModifyRequest modify = new ModifyRequest("cn=John,dc=example,dc=com",
                List.of(
                        new ModifyRequest.Change(ModifyRequest.ModifyOperation.REPLACE,
                                LdapAttribute.of("mail", "new@example.com"))
                ));

        LdapMessage original = LdapMessage.of(5, modify);
        byte[] encoded = LdapCodec.encodeToBytes(original);
        LdapMessage decoded = LdapCodec.decode(encoded);

        assertThat(decoded.protocolOp()).isInstanceOf(ModifyRequest.class);
        ModifyRequest decodedMod = (ModifyRequest) decoded.protocolOp();
        assertThat(decodedMod.object()).isEqualTo("cn=John,dc=example,dc=com");
        assertThat(decodedMod.changes()).hasSize(1);
        assertThat(decodedMod.changes().get(0).operation()).isEqualTo(ModifyRequest.ModifyOperation.REPLACE);
    }

    @Test
    void testModifyDnRequestRoundTrip() {
        ModifyDnRequest modDn = ModifyDnRequest.rename("cn=Old,dc=example,dc=com", "cn=New", true);

        LdapMessage original = LdapMessage.of(6, modDn);
        byte[] encoded = LdapCodec.encodeToBytes(original);
        LdapMessage decoded = LdapCodec.decode(encoded);

        assertThat(decoded.protocolOp()).isInstanceOf(ModifyDnRequest.class);
        ModifyDnRequest decodedModDn = (ModifyDnRequest) decoded.protocolOp();
        assertThat(decodedModDn.entry()).isEqualTo("cn=Old,dc=example,dc=com");
        assertThat(decodedModDn.newRdn()).isEqualTo("cn=New");
        assertThat(decodedModDn.deleteOldRdn()).isTrue();
        assertThat(decodedModDn.newSuperior()).isNull();
    }

    @Test
    void testCompareRequestRoundTrip() {
        CompareRequest compare = CompareRequest.of("cn=John,dc=example,dc=com", "cn", "John");

        LdapMessage original = LdapMessage.of(7, compare);
        byte[] encoded = LdapCodec.encodeToBytes(original);
        LdapMessage decoded = LdapCodec.decode(encoded);

        assertThat(decoded.protocolOp()).isInstanceOf(CompareRequest.class);
        CompareRequest decodedComp = (CompareRequest) decoded.protocolOp();
        assertThat(decodedComp.entry()).isEqualTo("cn=John,dc=example,dc=com");
        assertThat(decodedComp.attribute()).isEqualTo("cn");
    }

    @Test
    void testAbandonRequestRoundTrip() {
        LdapMessage original = LdapMessage.of(8, new AbandonRequest(5));
        byte[] encoded = LdapCodec.encodeToBytes(original);
        LdapMessage decoded = LdapCodec.decode(encoded);

        assertThat(decoded.protocolOp()).isInstanceOf(AbandonRequest.class);
        assertThat(((AbandonRequest) decoded.protocolOp()).abandonedMessageId()).isEqualTo(5);
    }

    @Test
    void testExtendedRequestRoundTrip() {
        LdapMessage original = LdapMessage.of(9, ExtendedRequest.startTls());
        byte[] encoded = LdapCodec.encodeToBytes(original);
        LdapMessage decoded = LdapCodec.decode(encoded);

        assertThat(decoded.protocolOp()).isInstanceOf(ExtendedRequest.class);
        assertThat(((ExtendedRequest) decoded.protocolOp()).requestName())
                .isEqualTo(ExtendedRequest.START_TLS_OID);
    }

    @Test
    void testExtendedResponseRoundTrip() {
        LdapMessage original = LdapMessage.of(9, ExtendedResponse.success());
        byte[] encoded = LdapCodec.encodeToBytes(original);
        LdapMessage decoded = LdapCodec.decode(encoded);

        assertThat(decoded.protocolOp()).isInstanceOf(ExtendedResponse.class);
        assertThat(((ExtendedResponse) decoded.protocolOp()).result().resultCode())
                .isEqualTo(LdapResultCode.SUCCESS);
    }

    @Test
    void testUnbindRequestRoundTrip() {
        LdapMessage original = LdapMessage.of(10, UnbindRequest.INSTANCE);
        byte[] encoded = LdapCodec.encodeToBytes(original);
        LdapMessage decoded = LdapCodec.decode(encoded);

        assertThat(decoded.protocolOp()).isInstanceOf(UnbindRequest.class);
    }

    @Test
    void testMessageWithControls() {
        LdapControl control = LdapControl.of("1.2.3.4.5", true, new byte[]{0x01, 0x02});
        LdapMessage original = LdapMessage.of(1, BindResponse.success(), List.of(control));
        byte[] encoded = LdapCodec.encodeToBytes(original);
        LdapMessage decoded = LdapCodec.decode(encoded);

        assertThat(decoded.controls()).hasSize(1);
        assertThat(decoded.controls().get(0).oid()).isEqualTo("1.2.3.4.5");
        assertThat(decoded.controls().get(0).criticality()).isTrue();
    }

    @Test
    void testTryDecodeWithInsufficientData() {
        ByteBuffer partial = ByteBuffer.wrap(new byte[]{0x30});
        LdapMessage result = LdapCodec.tryDecode(partial);
        assertThat(result).isNull();
    }

    @Test
    void testFilterEqualityMatchRoundTrip() {
        SearchFilter filter = SearchFilter.equalityMatch("cn", "John");
        SearchRequest search = SearchRequest.subtree("dc=example", filter);
        LdapMessage original = LdapMessage.of(1, search);
        byte[] encoded = LdapCodec.encodeToBytes(original);
        LdapMessage decoded = LdapCodec.decode(encoded);

        SearchRequest decodedSearch = (SearchRequest) decoded.protocolOp();
        assertThat(decodedSearch.filter()).isInstanceOf(SearchFilter.EqualityMatch.class);
    }

    @Test
    void testFilterPresenceRoundTrip() {
        SearchFilter filter = SearchFilter.present("objectClass");
        SearchRequest search = SearchRequest.subtree("dc=example", filter);
        LdapMessage original = LdapMessage.of(1, search);
        byte[] encoded = LdapCodec.encodeToBytes(original);
        LdapMessage decoded = LdapCodec.decode(encoded);

        SearchRequest decodedSearch = (SearchRequest) decoded.protocolOp();
        assertThat(decodedSearch.filter()).isInstanceOf(SearchFilter.Present.class);
    }

    @Test
    void testFilterAndRoundTrip() {
        SearchFilter filter = SearchFilter.and(
                SearchFilter.equalityMatch("objectClass", "person"),
                SearchFilter.present("cn")
        );
        SearchRequest search = SearchRequest.subtree("dc=example", filter);
        LdapMessage original = LdapMessage.of(1, search);
        byte[] encoded = LdapCodec.encodeToBytes(original);
        LdapMessage decoded = LdapCodec.decode(encoded);

        SearchRequest decodedSearch = (SearchRequest) decoded.protocolOp();
        assertThat(decodedSearch.filter()).isInstanceOf(SearchFilter.And.class);
        assertThat(((SearchFilter.And) decodedSearch.filter()).filters()).hasSize(2);
    }

    @Test
    void testResultWithDiagnosticMessage() {
        LdapResult result = LdapResult.of(LdapResultCode.INVALID_CREDENTIALS, "Wrong password");
        LdapMessage original = LdapMessage.of(1, BindResponse.of(result));
        byte[] encoded = LdapCodec.encodeToBytes(original);
        LdapMessage decoded = LdapCodec.decode(encoded);

        BindResponse resp = (BindResponse) decoded.protocolOp();
        assertThat(resp.result().resultCode()).isEqualTo(LdapResultCode.INVALID_CREDENTIALS);
        assertThat(resp.result().diagnosticMessage()).isEqualTo("Wrong password");
    }
}
