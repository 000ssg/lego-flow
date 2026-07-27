rootProject.name = "lego-flow"

// Core modules
include("blocks")
include("service")

// Web modules
include("web:http")
include("web:http2")
include("web:http3")
include("web:web-services")
include("web:http-proxy")

// IoT modules
include("iot:upnp")
include("iot:coap")

// Auth modules
include("auth:gssapi")
include("auth:http-auth:core")
include("auth:http-auth:basic-digest")
include("auth:http-auth:oauth")
include("auth:http-auth:sso")
include("auth:http-auth:spnego")

// Messaging modules
include("messaging:kafka")
include("messaging:amqp")
include("messaging:stomp")
include("messaging:nats")
include("messaging:mqtt")
include("messaging:xmpp")
include("messaging:wamp")

// RPC modules
include("rpc:grpc")
include("rpc:graphql")

// Database modules
include("database:redis")
include("database:postgresql")
include("database:mysql")

// Email modules
include("email:common")
include("email:smtp")
include("email:imap")

// Network modules
include("network:common")
include("network:dns")
include("network:ldap")
include("network:snmp")
include("network:syslog")
include("network:modbus")
include("network:ssh")
include("network:ftp")

// Media modules
include("media:common")
include("media:rtsp")
include("media:rtp")
include("media:sip")
