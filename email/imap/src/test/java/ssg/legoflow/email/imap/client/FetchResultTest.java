package ssg.legoflow.email.imap.client;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FetchResultTest {
    @Test void testCreation() {
        var result = new FetchResult(1);
        assertThat(result.sequenceNumber()).isEqualTo(1);
        assertThat(result.flags()).isNull();
        assertThat(result.uid()).isEqualTo(-1);
        assertThat(result.internalDate()).isNull();
        assertThat(result.size()).isEqualTo(-1L);
    }

    @Test void testPutAndGet() {
        var result = new FetchResult(5);
        result.put("FLAGS", "(\\Seen \\Flagged)");
        assertThat(result.get("flags")).isEqualTo("(\\Seen \\Flagged)"); // case-insensitive
        result.put("UID", "42");
        assertThat(result.uid()).isEqualTo(42L);
    }

    @Test void testFlags() {
        var result = new FetchResult(1);
        result.put("FLAGS", "(\\Seen)");
        assertThat(result.flags()).isEqualTo("(\\Seen)");
    }

    @Test void testSize() {
        var result = new FetchResult(1);
        result.put("RFC822.SIZE", "1024");
        assertThat(result.size()).isEqualTo(1024L);
    }

    @Test void testItemNames() {
        var result = new FetchResult(1);
        result.put("FLAGS", "(\\Seen)");
        result.put("UID", "5");
        assertThat(result.itemNames()).containsExactlyInAnyOrder("FLAGS", "UID");
    }

    @Test void testParseFetchResponse() {
        var parsed = FetchResult.parse("* 1 FETCH (FLAGS (\\Seen \\Flagged) UID 42)");
        assertThat(parsed).isNotNull();
        assertThat(parsed.sequenceNumber()).isEqualTo(1);
        assertThat(parsed.uid()).isEqualTo(42L);
        assertThat(parsed.flags()).isEqualTo("(\\Seen \\Flagged)");
    }

    @Test void testParseNonFetchReturnsNull() {
        assertThat(FetchResult.parse("OK Ready")).isNull();
        assertThat(FetchResult.parse("* BYE")).isNull();
    }

    @Test void testBodyContent() {
        var result = new FetchResult(1);
        result.put("BODY[]", "Hello world");
        assertThat(result.bodyContent()).isEqualTo("Hello world");
    }

    @Test void testToString() {
        var result = new FetchResult(7);
        result.put("FLAGS", "(\\Seen)");
        String s = result.toString();
        assertThat(s).contains("seq=7").contains("FLAGS");
    }

    @Test void testInternalDate() {
        var result = new FetchResult(1);
        result.put("INTERNALDATE", "Wed, 09 Oct 2024 10:00:00 +0000");
        assertThat(result.internalDate()).isEqualTo("Wed, 09 Oct 2024 10:00:00 +0000");
    }

    @Test void testEnvelope() {
        var result = new FetchResult(1);
        result.put("ENVELOPE", "(\"From\" ...)");
        assertThat(result.envelope()).isEqualTo("(\"From\" ...)");
    }

    @Test void testBodyStructure() {
        var result = new FetchResult(1);
        result.put("BODYSTRUCTURE", "(\"TEXT\" \"PLAIN\" ...)");
        assertThat(result.bodyStructure()).isEqualTo("(\"TEXT\" \"PLAIN\" ...)");
    }

    @Test void testParseWithQuotedStrings() {
        var parsed = FetchResult.parse("* 2 FETCH (BODY[] \"Hello World\")");
        assertThat(parsed).isNotNull();
        assertThat(parsed.sequenceNumber()).isEqualTo(2);
    }

    @Test void testParseWithNILValue() {
        var parsed = FetchResult.parse("* 3 FETCH (ENVELOPE NIL)");
        assertThat(parsed).isNotNull();
        assertThat(parsed.get("ENVELOPE")).isEqualTo("NIL");
    }
}
