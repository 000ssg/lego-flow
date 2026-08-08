package ssg.legoflow.email.imap.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FetchDataItemTest {

    @Test void testConstants() {
        assertThat(FetchDataItem.FLAGS.name()).isEqualTo("FLAGS");
        assertThat(FetchDataItem.INTERNALDATE.name()).isEqualTo("INTERNALDATE");
        assertThat(FetchDataItem.RFC822_SIZE.name()).isEqualTo("RFC822.SIZE");
        assertThat(FetchDataItem.ENVELOPE.name()).isEqualTo("ENVELOPE");
        assertThat(FetchDataItem.BODYSTRUCTURE.name()).isEqualTo("BODYSTRUCTURE");
        assertThat(FetchDataItem.BODY.name()).isEqualTo("BODY");
        assertThat(FetchDataItem.RFC822.name()).isEqualTo("RFC822");
        assertThat(FetchDataItem.RFC822_HEADER.name()).isEqualTo("RFC822.HEADER");
        assertThat(FetchDataItem.RFC822_TEXT.name()).isEqualTo("RFC822.TEXT");
        assertThat(FetchDataItem.UID.name()).isEqualTo("UID");
    }

    @Test void testBodySection() {
        var item = FetchDataItem.bodySection("1");
        assertThat(item.section()).isEqualTo("1");
        assertThat(item.isPeek()).isFalse();
    }

    @Test void testBodyPeek() {
        var item = FetchDataItem.bodyPeek("HEADER.FIELDS (Subject From)");
        assertThat(item.section()).isEqualTo("HEADER.FIELDS (Subject From)");
        assertThat(item.isPeek()).isTrue();
    }

    @Test void testPartial() {
        var item = FetchDataItem.partial("1.2", false, 100, 500);
        assertThat(item.section()).isEqualTo("1.2");
        assertThat(item.partialOffset()).isEqualTo(100L);
        assertThat(item.partialLength()).isEqualTo(500L);
    }

    @Test void testPartialPeek() {
        var item = FetchDataItem.partial("TEXT", true, 0, 4096);
        assertThat(item.isPeek()).isTrue();
        assertThat(item.isPartial()).isTrue();
    }

    @Test void testFromWireFlags() {
        var item = FetchDataItem.parse("FLAGS");
        assertThat(item).isSameAs(FetchDataItem.FLAGS);
    }

    @Test void testFromWireUid() {
        var item = FetchDataItem.parse("UID");
        assertThat(item).isSameAs(FetchDataItem.UID);
    }

    @Test void testFromWireBodySection() {
        var item = FetchDataItem.parse("BODY[1.2]");
        assertThat(item.name()).isEqualTo("BODY");
        assertThat(item.section()).isEqualTo("1.2");
        assertThat(item.isPeek()).isFalse();
    }

    @Test void testFromWireBodyPeek() {
        var item = FetchDataItem.parse("BODY.PEEK[HEADER]");
        assertThat(item.name()).isEqualTo("BODY");
        assertThat(item.section()).isEqualTo("HEADER");
        assertThat(item.isPeek()).isTrue();
    }

    @Test void testFromWireBodyPartial() {
        var item = FetchDataItem.parse("BODY[TEXT]<100.500>");
        assertThat(item.name()).isEqualTo("BODY");
        assertThat(item.section()).isEqualTo("TEXT");
        assertThat(item.partialOffset()).isEqualTo(100L);
        assertThat(item.partialLength()).isEqualTo(500L);
    }

    @Test void testFromWireBodyPeekPartial() {
        var item = FetchDataItem.parse("BODY.PEEK[1]<0.4096>");
        assertThat(item.name()).isEqualTo("BODY");
        assertThat(item.section()).isEqualTo("1");
        assertThat(item.isPeek()).isTrue();
        assertThat(item.partialOffset()).isEqualTo(0L);
        assertThat(item.partialLength()).isEqualTo(4096L);
    }

    @Test void testToWireFlags() {
        var item = FetchDataItem.FLAGS;
        assertThat(item.toWire()).isEqualTo("FLAGS");
    }

    @Test void testToWireBodySection() {
        var item = FetchDataItem.bodySection("HEADER.FIELDS (Date)");
        assertThat(item.toWire()).isEqualTo("BODY[HEADER.FIELDS (Date)]");
    }

    @Test void testToWireBodyPeek() {
        var item = FetchDataItem.bodyPeek("TEXT");
        assertThat(item.toWire()).isEqualTo("BODY.PEEK[TEXT]");
    }

    @Test void testToWirePartial() {
        var item = FetchDataItem.partial("1", false, 0, 1024);
        assertThat(item.toWire()).isEqualTo("BODY[1]<0.1024>");
    }

    @Test void testHasSection() {
        assertThat(FetchDataItem.bodySection("1").hasSection()).isTrue();
        assertThat(FetchDataItem.FLAGS.hasSection()).isFalse();
    }

    @Test void testIsPartial() {
        var partial = FetchDataItem.partial("1", false, 0, 100);
        assertThat(partial.isPartial()).isTrue();
        assertThat(FetchDataItem.bodySection("1").isPartial()).isFalse();
    }

    @Test void testEqualsAndHashCode() {
        var a = FetchDataItem.bodySection("1");
        var b = FetchDataItem.bodySection("1");
        var c = FetchDataItem.bodyPeek("1");
        
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
    }

    @Test void testToString() {
        var item = FetchDataItem.FLAGS;
        assertThat(item.toString()).isEqualTo("FLAGS");
    }

    @Test void testFromWireRfc822Size() {
        var item = FetchDataItem.parse("RFC822.SIZE");
        assertThat(item).isSameAs(FetchDataItem.RFC822_SIZE);
    }

    @Test void testFromWireBody() {
        var item = FetchDataItem.parse("BODY");
        assertThat(item).isSameAs(FetchDataItem.BODY);
    }

    @Test void testFromWireEnvelope() {
        var item = FetchDataItem.parse("ENVELOPE");
        assertThat(item).isSameAs(FetchDataItem.ENVELOPE);
    }
}
