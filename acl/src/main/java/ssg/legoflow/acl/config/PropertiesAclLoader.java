package ssg.legoflow.acl.config;

import ssg.legoflow.acl.AclDomainBuilder;
import ssg.legoflow.acl.model.AclDomain;
import ssg.legoflow.acl.model.AclRule;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Load ACL domain configuration from Properties files.
 *
 * <p>Format:
 * <pre>
 * domain.name=Test
 * domain.keySize=2048
 * domain.validityYears=10
 * domain.withCerts=true
 *
 * roles=admin,manager,user,guest
 * admin.permissions=*,read,write
 * manager.permissions=read,write,list
 * user.permissions=read,list
 * guest.permissions=read
 *
 * groups=admins,managers,users,guests
 * admins.roles=admin
 * managers.roles=manager
 * users.roles=user
 * guests.roles=guest
 *
 * acls=admin-all,manager-res
 * admin-all.uri=**
 * admin-all.control=ALLOW
 * admin-all.accessLevel=ALL
 * admin-all.roles=admin
 * manager-res.uri=/resources/**
 * manager-res.control=ALLOW
 * manager-res.accessLevel=WRITE
 * manager-res.roles=manager
 *
 * users=admin,manager1,user1,guest
 * admin.password=admin
 * admin.roles=admin
 * admin.groups=admins
 * manager1.password=manager1
 * manager1.roles=manager
 * manager1.groups=managers
 * user1.password=user1
 * user1.groups=users
 * guest.password=guest
 * guest.roles=guest
 * guest.groups=guests
 * </pre>
 */
public final class PropertiesAclLoader {
    private PropertiesAclLoader() {}

    public static AclDomain load(InputStream is) throws IOException {
        var props = new Properties();
        props.load(is);
        return load(props);
    }

    public static AclDomain loadFile(File file) throws IOException {
        try (var is = new java.io.FileInputStream(file)) {
            return load(is);
        }
    }

    public static AclDomain loadPath(Path path) throws IOException {
        return loadFile(path.toFile());
    }

    public static AclDomain load(Properties props) {
        var builder = new AclDomainBuilder();
        builder.name(require(props, "domain.name"));
        if (props.containsKey("domain.keySize")) builder.keySize(Integer.parseInt(props.getProperty("domain.keySize")));
        if (props.containsKey("domain.validityYears")) builder.validityYears(Long.parseLong(props.getProperty("domain.validityYears")));
        if ("true".equals(props.getProperty("domain.withCerts"))) builder.withCerts();

        // Roles
        var roleNames = split(props.getProperty("roles"));
        for (String rn : roleNames) {
            var perms = split(props.getProperty(rn + ".permissions"));
            if (perms.isEmpty()) builder.role(rn);
            else builder.role(rn, perms);
        }

        // Groups
        var groupNames = split(props.getProperty("groups"));
        for (String gn : groupNames) {
            var gRoles = split(props.getProperty(gn + ".roles"));
            var members = split(props.getProperty(gn + ".members"));
            if (gRoles.isEmpty()) builder.group(gn);
            else builder.group(gn, gRoles, members, null);
        }

        // ACL rules
        var aclNames = split(props.getProperty("acls"));
        for (String an : aclNames) {
            var uri = require(props, an + ".uri");
            var control = AclRule.Control.valueOf(props.getProperty(an + ".control", "ALLOW"));
            var level = AclRule.AccessLevel.valueOf(props.getProperty(an + ".accessLevel", "READ"));
            var desc = props.getProperty(an + ".description");
            var aRoles = split(props.getProperty(an + ".roles"));
            if (aRoles.isEmpty())
                builder.acl(an, uri, desc, control, level);
            else
                builder.acl(an, uri, desc, control, level, aRoles);
        }

        // Users
        var userNames = split(props.getProperty("users"));
        for (String un : userNames) {
            var password = require(props, un + ".password");
            var uRoles = split(props.getProperty(un + ".roles"));
            var uGroups = split(props.getProperty(un + ".groups"));
            builder.user(un, password, uRoles, uGroups, null);
        }

        return builder.build();
    }

    private static String require(Properties props, String key) {
        var v = props.getProperty(key);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Missing required property: " + key);
        return v.trim();
    }

    private static Set<String> split(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
