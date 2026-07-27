package ssg.legoflow.email.common.mime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MimeMultipart}.
 */
class MimeMultipartTest {

    @Test
    void testGenerateBoundary() {
        String b1 = MimeMultipart.generateBoundary();
        String b2 = MimeMultipart.generateBoundary();
        assertThat(b1).isNotEqualTo(b2);
        assertThat(b1).startsWith("----=_Part_");
    }

    @Test
    void testPartAccess() {
        MimePart part1 = new MimePart(new MimeHeaders(), new byte[0]);
        MimePart part2 = new MimePart(new MimeHeaders(), new byte[0]);
        MimeMultipart mp = new MimeMultipart("boundary", MultipartType.MIXED,
                List.of(part1, part2));

        assertThat(mp.partCount()).isEqualTo(2);
        assertThat(mp.part(0)).isSameAs(part1);
        assertThat(mp.part(1)).isSameAs(part2);
    }

    @Test
    void testAllPartsRecursive() {
        MimePart leaf1 = new MimePart(new MimeHeaders(), new byte[0]);
        MimePart leaf2 = new MimePart(new MimeHeaders(), new byte[0]);
        MimePart leaf3 = new MimePart(new MimeHeaders(), new byte[0]);

        MimeMultipart inner = new MimeMultipart("inner-b", MultipartType.ALTERNATIVE,
                List.of(leaf1, leaf2));
        MimeMultipart outer = new MimeMultipart("outer-b", MultipartType.MIXED,
                List.of(inner, leaf3));

        List<MimePart> allParts = outer.allParts();
        assertThat(allParts).hasSize(3);
        assertThat(allParts).containsExactly(leaf1, leaf2, leaf3);
    }

    @Test
    void testPreambleAndEpilogue() {
        MimeMultipart mp = new MimeMultipart("b", MultipartType.MIXED,
                List.of(), "This is a preamble", "This is an epilogue");
        assertThat(mp.preamble()).isEqualTo("This is a preamble");
        assertThat(mp.epilogue()).isEqualTo("This is an epilogue");
    }

    @Test
    void testBoundary() {
        MimeMultipart mp = new MimeMultipart("my-boundary", MultipartType.MIXED, List.of());
        assertThat(mp.boundary()).isEqualTo("my-boundary");
    }

    @Test
    void testMultipartType() {
        MimeMultipart mp = new MimeMultipart("b", MultipartType.ALTERNATIVE, List.of());
        assertThat(mp.multipartType()).isEqualTo(MultipartType.ALTERNATIVE);
    }

    @Test
    void testToString() {
        MimeMultipart mp = new MimeMultipart("b", MultipartType.MIXED,
                List.of(new MimePart(new MimeHeaders(), new byte[0])));
        assertThat(mp.toString()).contains("mixed");
        assertThat(mp.toString()).contains("parts=1");
    }
}
