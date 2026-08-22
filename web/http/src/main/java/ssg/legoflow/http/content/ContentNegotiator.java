package ssg.legoflow.http.content;

import ssg.legoflow.http.header.ContentEncoding;
import ssg.legoflow.http.header.LanguageTag;
import ssg.legoflow.http.header.MediaType;
import ssg.legoflow.http.header.QualityValue;
import java.util.*;
public class ContentNegotiator {

    public Optional<MediaType> negotiateMediaType(String acceptHeader, List<MediaType> available) {
        if (acceptHeader == null || acceptHeader.isBlank()) {
            return available.isEmpty() ? Optional.empty() : Optional.of(available.getFirst());
        }
        var preferences = parseQualityValues(acceptHeader);
        for (var pref : preferences) {
            var requested = MediaType.parse(pref.value());
            for (var offered : available) {
                if (offered.matches(requested)) {
                    return Optional.of(offered);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<ContentEncoding> negotiateEncoding(String acceptEncodingHeader, List<ContentEncoding> available) {
        if (acceptEncodingHeader == null || acceptEncodingHeader.isBlank()) {
            return Optional.of(ContentEncoding.IDENTITY);
        }
        var preferences = parseQualityValues(acceptEncodingHeader);
        for (var pref : preferences) {
            for (var offered : available) {
                if (offered.value().equalsIgnoreCase(pref.value())) {
                    return Optional.of(offered);
                }
            }
        }
        return Optional.of(ContentEncoding.IDENTITY);
    }

    public Optional<LanguageTag> negotiateLanguage(String acceptLanguageHeader, List<LanguageTag> available) {
        if (acceptLanguageHeader == null || acceptLanguageHeader.isBlank()) {
            return available.isEmpty() ? Optional.empty() : Optional.of(available.getFirst());
        }
        var preferences = parseQualityValues(acceptLanguageHeader);
        for (var pref : preferences) {
            var requested = LanguageTag.parse(pref.value());
            for (var offered : available) {
                if (offered.matches(requested)) {
                    return Optional.of(offered);
                }
            }
        }
        return Optional.empty();
    }

    private List<QualityValue> parseQualityValues(String header) {
        var values = new ArrayList<QualityValue>();
        for (var part : header.split(",")) {
            values.add(QualityValue.parse(part.trim()));
        }
        Collections.sort(values);
        return values;
    }
}
