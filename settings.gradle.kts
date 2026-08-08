rootProject.name = "lego-flow"

// Core modules
include("blocks")
include("service")
include("demos")

// Infrastructure modules (excluded from install/deploy)
include("benchmarks")
include("interop-tests")

// Parent-only aggregator projects (Maven pom-style, no Java sources)
include(":auth")
include(":web")
include(":iot")
include(":messaging")
include(":rpc")
include(":database")
include(":email")
include(":network")
include(":media")

// Nested sub-modules — give each a unique project name matching Maven artifactId
val nestedModules = mapOf(
    // Web modules
    "lego-flow-http"          to "web/http",
    "lego-flow-http2"         to "web/http2",
    "lego-flow-http3"         to "web/http3",
    "lego-flow-web-services"  to "web/web-services",
    "lego-flow-http-proxy"    to "web/http-proxy",

    // Auth modules (including nested http-auth sub-modules)
    "lego-flow-gssapi"        to "auth/gssapi",
    "lego-flow-http-auth"     to "auth/http-auth",
    "lego-flow-http-auth-core"       to "auth/http-auth/core",
    "lego-flow-http-auth-basic-digest" to "auth/http-auth/basic-digest",
    "lego-flow-http-auth-oauth"      to "auth/http-auth/oauth",
    "lego-flow-http-auth-sso"        to "auth/http-auth/sso",
    "lego-flow-http-auth-spnego"     to "auth/http-auth/spnego",

    // IoT modules
    "lego-flow-upnp"         to "iot/upnp",
    "lego-flow-coap"         to "iot/coap",

    // Messaging modules
    "lego-flow-kafka"        to "messaging/kafka",
    "lego-flow-amqp"         to "messaging/amqp",
    "lego-flow-stomp"        to "messaging/stomp",
    "lego-flow-nats"         to "messaging/nats",
    "lego-flow-mqtt"         to "messaging/mqtt",
    "lego-flow-xmpp"         to "messaging/xmpp",
    "lego-flow-wamp"         to "messaging/wamp",

    // RPC modules
    "lego-flow-grpc"         to "rpc/grpc",
    "lego-flow-graphql"      to "rpc/graphql",

    // Database modules
    "lego-flow-redis"        to "database/redis",
    "lego-flow-postgresql"   to "database/postgresql",
    "lego-flow-mysql"        to "database/mysql",

    // Email modules
    "lego-flow-email-common" to "email/common",
    "lego-flow-smtp"         to "email/smtp",
    "lego-flow-imap"         to "email/imap",

    // Network modules
    "lego-flow-network-common"  to "network/common",
    "lego-flow-dns"            to "network/dns",
    "lego-flow-ldap"           to "network/ldap",
    "lego-flow-snmp"           to "network/snmp",
    "lego-flow-syslog"         to "network/syslog",
    "lego-flow-modbus"         to "network/modbus",
    "lego-flow-ssh"            to "network/ssh",
    "lego-flow-ftp"            to "network/ftp",

    // Media modules
    "lego-flow-media-common"   to "media/common",
    "lego-flow-rtsp"           to "media/rtsp",
    "lego-flow-rtp"            to "media/rtp",
    "lego-flow-sip"            to "media/sip",
)

for ((projectName, projectDir) in nestedModules) {
    include(projectName)
    project(":$projectName").projectDir = file(projectDir)
}
