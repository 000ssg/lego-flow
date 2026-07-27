package ssg.legoflow.email.common.builder;

import org.junit.jupiter.api.Test;
import ssg.legoflow.email.common.mime.MimeHeaders;
import ssg.legoflow.email.common.mime.MimeMultipart;
import ssg.legoflow.email.common.mime.MimePart;
import ssg.legoflow.email.common.mime.MultipartType;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MultipartBuilder}.
 */
class MultipartBuilderTest {

    @Test
    void testBuildMixed() {
        MimeMultipart mp = MultipartBuilder.mixed()
                .addPart(new MimePart(new MimeHeaders(), "Part 1".getBytes()))
                .addPart(new MimePart(new MimeHeaders(), "Part 2".getBytes()))
                .build();

        assertThat(mp.multipartType()).isEqualTo(MultipartType.MIXED);
        assertThat(mp.partCount()).isEqualTo(2);
    }

    @Test
    void testBuildAlternative() {
        MimeMultipart mp = MultipartBuilder.alternative()
                .addPart(MimePartBuilder.create()
                        .textPlain(StandardCharsets.UTF_8)
                        .content("Plain")
                        .build())
                .addPart(MimePartBuilder.create()
                        .textHtml(StandardCharsets.UTF_8)
                        .content("<html>HTML</html>")
                        .build())
                .build();

        assertThat(mp.multipartType()).isEqualTo(MultipartType.ALTERNATIVE);
        assertThat(mp.partCount()).isEqualTo(2);
    }

    @Test
    void testBuildRelated() {
        MimeMultipart mp = MultipartBuilder.related().build();
        assertThat(mp.multipartType()).isEqualTo(MultipartType.RELATED);
    }

    @Test
    void testBuildWithPreambleAndEpilogue() {
        MimeMultipart mp = MultipartBuilder.mixed()
                .preamble("This is a preamble")
                .epilogue("This is an epilogue")
                .build();

        assertThat(mp.preamble()).isEqualTo("This is a preamble");
        assertThat(mp.epilogue()).isEqualTo("This is an epilogue");
    }

    @Test
    void testBuildNested() {
        MimeMultipart inner = MultipartBuilder.alternative()
                .addPart(new MimePart(new MimeHeaders(), "text".getBytes()))
                .addPart(new MimePart(new MimeHeaders(), "html".getBytes()))
                .build();

        MimeMultipart outer = MultipartBuilder.mixed()
                .addPart(inner)
                .addPart(new MimePart(new MimeHeaders(), "attachment".getBytes()))
                .build();

        assertThat(outer.partCount()).isEqualTo(2);
        assertThat(outer.part(0)).isInstanceOf(MimeMultipart.class);
    }

    @Test
    void testBuildWithPartBuilder() {
        MimeMultipart mp = MultipartBuilder.mixed()
                .addPart(MimePartBuilder.create()
                        .textPlain(StandardCharsets.UTF_8)
                        .content("Hello"))
                .build();

        assertThat(mp.partCount()).isEqualTo(1);
    }

    @Test
    void testBuildWithNestedBuilder() {
        MimeMultipart mp = MultipartBuilder.mixed()
                .addPart(MultipartBuilder.alternative()
                        .addPart(new MimePart(new MimeHeaders(), "text".getBytes())))
                .build();

        assertThat(mp.partCount()).isEqualTo(1);
        assertThat(mp.part(0)).isInstanceOf(MimeMultipart.class);
    }

    @Test
    void testContentTypeValue() {
        MultipartBuilder builder = MultipartBuilder.mixed();
        String ctValue = builder.contentTypeValue();
        assertThat(ctValue).startsWith("multipart/mixed; boundary=\"");
    }

    @Test
    void testBoundaryIsUnique() {
        MultipartBuilder b1 = MultipartBuilder.mixed();
        MultipartBuilder b2 = MultipartBuilder.mixed();
        assertThat(b1.boundary()).isNotEqualTo(b2.boundary());
    }

    @Test
    void testBuildWithCustomBoundary() {
        MimeMultipart mp = MultipartBuilder.of(MultipartType.MIXED, "custom-boundary")
                .addPart(new MimePart(new MimeHeaders(), new byte[0]))
                .build();

        assertThat(mp.boundary()).isEqualTo("custom-boundary");
    }
}
