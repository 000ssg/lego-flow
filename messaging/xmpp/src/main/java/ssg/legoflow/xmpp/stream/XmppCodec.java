package ssg.legoflow.xmpp.stream;

import ssg.legoflow.blocks.AbstractDataFilter;
import ssg.legoflow.blocks.Context;
import ssg.legoflow.xmpp.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import ssg.legoflow.service.util.BufferPool;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XMPP codec that encodes stanzas to XML and decodes XML to stanzas.
 *
 * <p>Extends {@link AbstractDataFilter} to participate in the data processing pipeline.
 * Supports incremental XML parsing for handling partial data.
 *
 * @since 0.1.0
 */
public class XmppCodec extends AbstractDataFilter<ByteBuffer> {

    private static final Logger LOG = LoggerFactory.getLogger(XmppCodec.class);

    private static final Pattern MESSAGE_PATTERN = Pattern.compile(
            "<message\\s([^>]*)>(.*?)</message>", Pattern.DOTALL);
    private static final Pattern PRESENCE_PATTERN = Pattern.compile(
            "<presence\\s([^>]*)>(.*?)</presence>", Pattern.DOTALL);
    private static final Pattern IQ_PATTERN = Pattern.compile(
            "<iq\\s([^>]*)>(.*?)</iq>", Pattern.DOTALL);
    private static final Pattern ATTR_PATTERN = Pattern.compile(
            "(\\w+)=\"([^\"]*)\"");
    private static final Pattern BODY_PATTERN = Pattern.compile(
            "<body>(.*?)</body>", Pattern.DOTALL);
    private static final Pattern SUBJECT_PATTERN = Pattern.compile(
            "<subject>(.*?)</subject>", Pattern.DOTALL);
    private static final Pattern THREAD_PATTERN = Pattern.compile(
            "<thread>(.*?)</thread>", Pattern.DOTALL);
    private static final Pattern SHOW_PATTERN = Pattern.compile(
            "<show>(.*?)</show>");
    private static final Pattern STATUS_PATTERN = Pattern.compile(
            "<status>(.*?)</status>");
    private static final Pattern PRIORITY_PATTERN = Pattern.compile(
            "<priority>(.*?)</priority>");

    private final StringBuilder buffer = new StringBuilder();

    /**
     * Creates a new XMPP codec.
     */
    public XmppCodec() {
        super(ByteBuffer.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ByteBuffer[] doFilter(Context ctx, ByteBuffer... data) {
        return data;
    }

    /**
     * Encodes a stanza to an XML ByteBuffer.
     *
     * @param stanza the stanza to encode
     * @return the encoded ByteBuffer
     */
    public ByteBuffer encodeStanza(Stanza stanza) {
        String xml = switch (stanza) {
            case MessageStanza msg -> msg.toXml();
            case PresenceStanza pres -> pres.toXml();
            case IqStanza iq -> iq.toXml();
        };
        LOG.debug("Encoded stanza: {}", xml);
        return ByteBuffer.wrap(xml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes XML data into stanzas. Supports incremental parsing.
     *
     * @param data the XML data
     * @return the list of decoded stanzas
     */
    public List<Stanza> decodeStanzas(ByteBuffer data) {
        String xml = StandardCharsets.UTF_8.decode(data).toString();
        buffer.append(xml);

        List<Stanza> stanzas = new ArrayList<>();
        String content = buffer.toString();

        // Parse stanzas and track the furthest matched end position
        int furthestEnd = 0;
        furthestEnd = Math.max(furthestEnd, parseMessages(content, stanzas));
        furthestEnd = Math.max(furthestEnd, parsePresences(content, stanzas));
        furthestEnd = Math.max(furthestEnd, parseIqs(content, stanzas));

        // Delete only up to the furthest end position of successfully parsed stanzas
        if (furthestEnd > 0) {
            buffer.delete(0, furthestEnd);
        }

        return stanzas;
    }

    /**
     * Returns true if there is buffered data awaiting more input.
     *
     * @return true if the internal buffer is non-empty
     */
    public boolean hasBufferedData() {
        return !buffer.isEmpty();
    }

    /**
     * Resets the internal buffer.
     */
    public void reset() {
        buffer.setLength(0);
    }

    /**
     * Returns the current buffer content (for incremental parsing).
     *
     * @return the buffered content
     */
    public String getBufferedContent() {
        return buffer.toString();
    }

    private int parseMessages(String content, List<Stanza> stanzas) {
        int furthestEnd = 0;
        Matcher matcher = MESSAGE_PATTERN.matcher(content);
        while (matcher.find()) {
            furthestEnd = Math.max(furthestEnd, matcher.end());
            String attrs = matcher.group(1);
            String body = matcher.group(2);
            var attrMap = parseAttributes(attrs);

            String id = attrMap.getOrDefault("id", UUID.randomUUID().toString());
            JID from = attrMap.containsKey("from") ? JID.parse(attrMap.get("from")) : null;
            JID to = JID.parse(attrMap.getOrDefault("to", "unknown@localhost"));
            MessageStanza.MessageType msgType = parseMessageType(attrMap.get("type"));

            String bodyText = extractElement(body, BODY_PATTERN);
            String subject = extractElement(body, SUBJECT_PATTERN);
            String thread = extractElement(body, THREAD_PATTERN);

            stanzas.add(new MessageStanza(id, from, to, msgType, bodyText, subject, thread, List.of()));
        }
        return furthestEnd;
    }

    private int parsePresences(String content, List<Stanza> stanzas) {
        int furthestEnd = 0;
        Matcher matcher = PRESENCE_PATTERN.matcher(content);
        while (matcher.find()) {
            furthestEnd = Math.max(furthestEnd, matcher.end());
            String attrs = matcher.group(1);
            String body = matcher.group(2);
            var attrMap = parseAttributes(attrs);

            String id = attrMap.getOrDefault("id", UUID.randomUUID().toString());
            JID from = attrMap.containsKey("from") ? JID.parse(attrMap.get("from")) : null;
            JID to = attrMap.containsKey("to") ? JID.parse(attrMap.get("to")) : null;
            PresenceStanza.PresenceType presType = parsePresenceType(attrMap.get("type"));
            PresenceStanza.PresenceShow show = parseShow(extractElement(body, SHOW_PATTERN));
            String status = extractElement(body, STATUS_PATTERN);
            int priority = parsePriority(extractElement(body, PRIORITY_PATTERN));

            stanzas.add(new PresenceStanza(id, from, to, presType, show, status, priority, List.of()));
        }
        return furthestEnd;
    }

    private int parseIqs(String content, List<Stanza> stanzas) {
        int furthestEnd = 0;
        Matcher matcher = IQ_PATTERN.matcher(content);
        while (matcher.find()) {
            furthestEnd = Math.max(furthestEnd, matcher.end());
            String attrs = matcher.group(1);
            var attrMap = parseAttributes(attrs);

            String id = attrMap.getOrDefault("id", UUID.randomUUID().toString());
            JID from = attrMap.containsKey("from") ? JID.parse(attrMap.get("from")) : null;
            JID to = attrMap.containsKey("to") ? JID.parse(attrMap.get("to")) : null;
            IqStanza.IqType iqType = parseIqType(attrMap.get("type"));

            stanzas.add(new IqStanza(id, from, to, iqType, null, null));
        }
        return furthestEnd;
    }

    private java.util.Map<String, String> parseAttributes(String attrs) {
        var map = new java.util.HashMap<String, String>();
        Matcher matcher = ATTR_PATTERN.matcher(attrs);
        while (matcher.find()) {
            map.put(matcher.group(1), matcher.group(2));
        }
        return map;
    }

    private String extractElement(String body, Pattern pattern) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? unescapeXml(matcher.group(1)) : null;
    }

    private MessageStanza.MessageType parseMessageType(String type) {
        if (type == null) return MessageStanza.MessageType.NORMAL;
        return switch (type.toLowerCase()) {
            case "chat" -> MessageStanza.MessageType.CHAT;
            case "groupchat" -> MessageStanza.MessageType.GROUPCHAT;
            case "headline" -> MessageStanza.MessageType.HEADLINE;
            case "error" -> MessageStanza.MessageType.ERROR;
            default -> MessageStanza.MessageType.NORMAL;
        };
    }

    private PresenceStanza.PresenceType parsePresenceType(String type) {
        if (type == null) return PresenceStanza.PresenceType.AVAILABLE;
        return switch (type.toLowerCase()) {
            case "unavailable" -> PresenceStanza.PresenceType.UNAVAILABLE;
            case "subscribe" -> PresenceStanza.PresenceType.SUBSCRIBE;
            case "subscribed" -> PresenceStanza.PresenceType.SUBSCRIBED;
            case "unsubscribe" -> PresenceStanza.PresenceType.UNSUBSCRIBE;
            case "unsubscribed" -> PresenceStanza.PresenceType.UNSUBSCRIBED;
            case "probe" -> PresenceStanza.PresenceType.PROBE;
            case "error" -> PresenceStanza.PresenceType.ERROR;
            default -> PresenceStanza.PresenceType.AVAILABLE;
        };
    }

    private PresenceStanza.PresenceShow parseShow(String show) {
        if (show == null) return null;
        return switch (show.toLowerCase()) {
            case "chat" -> PresenceStanza.PresenceShow.CHAT;
            case "away" -> PresenceStanza.PresenceShow.AWAY;
            case "xa" -> PresenceStanza.PresenceShow.XA;
            case "dnd" -> PresenceStanza.PresenceShow.DND;
            default -> null;
        };
    }

    private IqStanza.IqType parseIqType(String type) {
        if (type == null) return IqStanza.IqType.GET;
        return switch (type.toLowerCase()) {
            case "set" -> IqStanza.IqType.SET;
            case "result" -> IqStanza.IqType.RESULT;
            case "error" -> IqStanza.IqType.ERROR;
            default -> IqStanza.IqType.GET;
        };
    }

    private int parsePriority(String priority) {
        if (priority == null) return 0;
        try {
            return Integer.parseInt(priority);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String unescapeXml(String text) {
        return text.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
    }
}
