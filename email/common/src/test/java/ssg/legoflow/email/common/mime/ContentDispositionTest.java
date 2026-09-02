package ssg.legoflow.email.common.mime;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link ContentDisposition}.
 */
class ContentDispositionTest {

    @Test
    void testParseAttachment() {
        ContentDisposition cd = ContentDisposition.parse("attachment; filename=\"doc.pdf\"");
        assertThat(cd.type()).isEqualTo("attachment");
        assertThat(cd.filename()).isEqualTo("doc.pdf");
        assertThat(cd.isAttachment()).isTrue();
        assertThat(cd.isInline()).isFalse();
    }

    @Test
    void testParseInline() {
        ContentDisposition cd = ContentDisposition.parse("inline");
        assertThat(cd.type()).isEqualTo("inline");
        assertThat(cd.isInline()).isTrue();
        assertThat(cd.isAttachment()).isFalse();
    }

    @Test
    void testParseWithSize() {
        ContentDisposition cd = ContentDisposition.parse(
                "attachment; filename=\"data.csv\"; size=12345");
        assertThat(cd.size()).isEqualTo(12345);
    }

    @Test
    void testParseSizeNotPresent() {
        ContentDisposition cd = ContentDisposition.parse("attachment");
        assertThat(cd.size()).isEqualTo(-1);
    }

    @Test
    void testParseNullReturnsInline() {
        ContentDisposition cd = ContentDisposition.parse(null);
        assertThat(cd.isInline()).isTrue();
    }

    @Test
    void testFactoryInline() {
        ContentDisposition cd = ContentDisposition.inline();
        assertThat(cd.type()).isEqualTo("inline");
    }

    @Test
    void testFactoryAttachment() {
        ContentDisposition cd = ContentDisposition.attachment("report.pdf");
        assertThat(cd.type()).isEqualTo("attachment");
        assertThat(cd.filename()).isEqualTo("report.pdf");
    }

    @Test
    void testToHeaderValue() {
        ContentDisposition cd = ContentDisposition.attachment("doc.pdf");
        String value = cd.toHeaderValue();
        assertThat(value).contains("attachment");
        assertThat(value).contains("filename");
        assertThat(value).contains("doc.pdf");
    }

    @Test
    void testEquality() {
        ContentDisposition a = ContentDisposition.parse("attachment; filename=\"a.txt\"");
        ContentDisposition b = ContentDisposition.parse("attachment; filename=\"a.txt\"");
        assertThat(a).isEqualTo(b);
    }
}
