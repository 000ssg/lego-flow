-- Prosody configuration for Lego Flow XMPP interoperability tests
-- Self-signed SSL for local/CI testing

-- Modules
modules_enabled = {
    "roster";
    "saslauth";
    "tls";
    "dialback";
    "disco";
    "http_folders";
    "http_upload";
    "pep";
    "register";
    "vcard";
    "version";
    "uptime";
    "time";
    "ping";
    "stats";
    "tcp_forwarder";
    "inline_attachments";
    "websocket";
}

modules_disabled = {
    "muc";
}

-- General config
admins = {}

-- Internal admin port (for prosodyctl registration)
admin = {
    "localhost"
}

-- Networking
interface = "0.0.0.0"

-- Virtual hosts
VirtualHost "localhost"
    ssl = {
        key = "/etc/prosody/certs/localhost.key";
        certificate = "/etc/prosody/certs/localhost.crt";
    }

-- Component config
Component "conference.localhost" "muc"
    name = "Lego Flow Chat"
    modules_enabled = {}

-- Trust the proxy for proxied connections
trust_proxy = true
