package ssg.legoflow.ws.content;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.header.MediaType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
public class ContentNegotiator {

    private final List<MediaType> supportedTypes;

    public ContentNegotiator(List<MediaType> supportedTypes) {
        this.supportedTypes = List.copyOf(supportedTypes);
    }

    public MediaType negotiate(HttpRequest request) {
        var acceptHeader = request.getHeaders().get(HttpHeaders.ACCEPT);
        if (acceptHeader == null || acceptHeader.isBlank()) {
            return supportedTypes.isEmpty() ? MediaType.APPLICATION_JSON : supportedTypes.getFirst();
        }
        var acceptEntries = parseAcceptHeader(acceptHeader);
        acceptEntries.sort(Comparator.comparingDouble(QualityEntry::quality).reversed());
        for (var entry : acceptEntries) {
            for (var supported : supportedTypes) {
                if (entry.mediaType().matches(supported)) {
                    return supported;
                }
            }
        }
        return null;
    }

    public MediaType negotiateOrDefault(HttpRequest request) {
        var result = negotiate(request);
        return result != null ? result : (supportedTypes.isEmpty() ? MediaType.APPLICATION_JSON : supportedTypes.getFirst());
    }

    public List<MediaType> getSupportedTypes() {
        return supportedTypes;
    }

    private List<QualityEntry> parseAcceptHeader(String header) {
        var entries = new ArrayList<QualityEntry>();
        for (var part : header.split(",")) {
            var trimmed = part.strip();
            if (trimmed.isEmpty()) continue;
            double quality = 1.0;
            String mediaStr = trimmed;
            int qIndex = trimmed.indexOf(";q=");
            if (qIndex < 0) qIndex = trimmed.indexOf(";Q=");
            if (qIndex >= 0) {
                mediaStr = trimmed.substring(0, qIndex).strip();
                try {
                    quality = Double.parseDouble(trimmed.substring(qIndex + 3).strip());
                } catch (NumberFormatException e) {
                    quality = 0.0;
                }
            }
            try {
                entries.add(new QualityEntry(MediaType.parse(mediaStr), quality));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return entries;
    }

    private record QualityEntry(MediaType mediaType, double quality) {}
}
