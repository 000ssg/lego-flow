package ssg.legoflow.http.header;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class QualityValueTest {

    @Test
    void testParseWithQualityFactor() {
        // When
        var qv = QualityValue.parse("text/html;q=0.9");

        // Then
        assertThat(qv.value()).isEqualTo("text/html");
        assertThat(qv.quality()).isEqualTo(0.9);
    }

    @Test
    void testParseWithoutQualityFactor() {
        // When
        var qv = QualityValue.parse("application/json");

        // Then
        assertThat(qv.value()).isEqualTo("application/json");
        assertThat(qv.quality()).isEqualTo(1.0);
    }

    @Test
    void testDefaultQualityIsOne() {
        // When
        var qv = new QualityValue("text/plain");

        // Then
        assertThat(qv.quality()).isEqualTo(1.0);
    }

    @Test
    void testComparisonSortDescending() {
        // Given
        var list = new ArrayList<>(List.of(
                new QualityValue("text/html", 0.5),
                new QualityValue("application/json", 1.0),
                new QualityValue("text/xml", 0.8)
        ));

        // When
        Collections.sort(list);

        // Then
        assertThat(list).extracting(QualityValue::value)
                .containsExactly("application/json", "text/xml", "text/html");
    }

    @Test
    void testToStringWithQuality() {
        // When
        var qv = new QualityValue("text/html", 0.9);

        // Then
        assertThat(qv.toString()).isEqualTo("text/html;q=0.9");
    }

    @Test
    void testToStringDefaultQuality() {
        // When
        var qv = new QualityValue("text/html", 1.0);

        // Then
        assertThat(qv.toString()).isEqualTo("text/html");
    }

    @Test
    void testInvalidQualityThrows() {
        assertThatThrownBy(() -> new QualityValue("text/html", 1.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QualityValue("text/html", -0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEquality() {
        // Given
        var qv1 = new QualityValue("text/html", 0.9);
        var qv2 = new QualityValue("text/html", 0.9);
        var qv3 = new QualityValue("text/html", 0.8);

        // Then
        assertThat(qv1).isEqualTo(qv2);
        assertThat(qv1).isNotEqualTo(qv3);
        assertThat(qv1.hashCode()).isEqualTo(qv2.hashCode());
    }
}
