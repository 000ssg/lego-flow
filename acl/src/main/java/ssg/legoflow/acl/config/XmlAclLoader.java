package ssg.legoflow.acl.config;

import ssg.legoflow.acl.AclDomainBuilder;
import ssg.legoflow.acl.model.AclDomain;
import ssg.legoflow.acl.model.AclRule;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

/**
 * Load ACL domain from XML.
 */
public final class XmlAclLoader {
    private XmlAclLoader() {}

    public static AclDomain load(InputStream is) throws IOException {
        return parse(new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
    }

    public static AclDomain load(File file) throws IOException {
        try (var is = new FileInputStream(file)) { return load(is); }
    }

    public static AclDomain load(Path path) throws IOException {
        return load(path.toFile());
    }

    static AclDomain parse(String xml) {
        var builder = new AclDomainBuilder();
        var doc = new SimpleXmlParser(xml);

        // Domain config
        var domainSection = doc.path("domain");
        builder.name(doc.attr(domainSection, "name", "Unnamed"));
        var ks = doc.attr(domainSection, "keySize", "2048");
        builder.keySize(Integer.parseInt(ks));
        var vy = doc.attr(domainSection, "validityYears", "10");
        builder.validityYears(Long.parseLong(vy));
        if ("true".equals(doc.attr(domainSection, "withCerts", "false"))) builder.withCerts();

        // Roles
        for (var role : doc.children(domainSection, "role")) {
            var name = doc.attr(role, "name");
            var perms = doc.attr(role, "permissions");
            var set = splitComma(perms);
            if (set.isEmpty()) builder.role(name);
            else builder.role(name, set);
        }

        // Groups
        for (var group : doc.children(domainSection, "group")) {
            var name = doc.attr(group, "name");
            var roles = splitComma(doc.attr(group, "roles"));
            var members = splitComma(doc.attr(group, "members"));
            builder.group(name, roles, members, null);
        }

        // ACLs
        for (var acl : doc.children(domainSection, "acl")) {
            var name = doc.attr(acl, "name");
            var uri = doc.attr(acl, "uri");
            var desc = doc.attr(acl, "description", null);
            var control = AclRule.Control.valueOf(doc.attr(acl, "control", "ALLOW"));
            var level = AclRule.AccessLevel.valueOf(doc.attr(acl, "accessLevel", "READ"));
            var aRoles = splitComma(doc.attr(acl, "roles"));
            if (aRoles.isEmpty()) builder.acl(name, uri, desc, control, level);
            else builder.acl(name, uri, desc, control, level, aRoles);
        }

        // Users
        for (var user : doc.children(domainSection, "user")) {
            var username = doc.attr(user, "name");
            var password = doc.attr(user, "password");
            var roles = splitComma(doc.attr(user, "roles"));
            var groups = splitComma(doc.attr(user, "groups"));
            builder.user(username, password, roles, groups, null);
        }

        return builder.build();
    }

    private static Set<String> splitComma(String s) {
        if (s == null || s.isBlank()) return Set.of();
        return java.util.Arrays.stream(s.split(","))
                .map(String::trim).filter(t -> !t.isEmpty())
                .collect(java.util.stream.Collectors.toSet());
    }

    // Minimal XML tag parser — no external deps
    static class SimpleXmlParser {
        private final String xml;

        SimpleXmlParser(String xml) { this.xml = xml.strip(); }

        int root() { return 0; }

        int path(int parent, String tag) { return childTag(xml, parent, tag); }
        int path(String tag) { return childTag(xml, 0, tag); }

        String attr(int pos, String key) { return attr(xml, pos, key, null); }
        String attr(int pos, String key, String def) { return attr(xml, pos, key, def); }

        List<Integer> children(int pos, String tag) { return childTags(xml, pos, tag); }

        static int childTag(String xml, int parentPos, String tag) {
            var tags = childTags(xml, parentPos, tag);
            return tags.isEmpty() ? -1 : tags.get(0);
        }

        static List<Integer> childTags(String xml, int parentPos, String tag) {
            var result = new ArrayList<Integer>();
            var start = tagStart(xml, parentPos, tag);
            while (start >= 0) {
                result.add(start);
                start = tagStart(xml, tagEnd(xml, start), tag);
            }
            return result;
        }

        static String attr(String xml, int pos, String key, String def) {
            var tag = xml.substring(pos, Math.min(tagEnd(xml, pos), xml.length()));
            var idx = tag.indexOf('"' + key + "=\"");
            if (idx < 0) {
                idx = tag.indexOf(" " + key + "=\"");
                if (idx >= 0) idx++;
            }
            if (idx >= 0) {
                idx += key.length() + 3; // skip 'key="'
                var end = tag.indexOf('"', idx);
                return end >= 0 ? tag.substring(idx, end) : def;
            }
            return def;
        }

        static int tagStart(String xml, int from, String tag) {
            var s = "<" + tag + " ";
            var i = xml.indexOf(s, from);
            if (i >= 0) return i;
            s = "<" + tag + ">";
            i = xml.indexOf(s, from);
            if (i >= 0) return i;
            s = "<" + tag + "/";
            i = xml.indexOf(s, from);
            return i;
        }

        static int tagEnd(String xml, int start) {
            var selfClose = xml.indexOf("/>", start);
            if (selfClose >= 0) return selfClose + 2;
            var closeTag = xml.indexOf(">", start);
            if (closeTag < 0) return xml.length();
            var tag = xml.substring(start, closeTag + 1);
            var name = tag.substring(1).split("[\\s/>]").length > 0 ? tag.substring(1).split("[\\s/>]")[0] : tag.substring(1);
            var end = "</" + name + ">";
            var e = xml.indexOf(end, closeTag + 1);
            return e >= 0 ? e + end.length() : xml.length();
        }
    }
}
