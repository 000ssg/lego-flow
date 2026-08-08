plugins {
    java
}

dependencies {
    api(project(":blocks"))
    api(project(":service"))
    api(project(":lego-flow-http"))
    api(project(":lego-flow-mqtt"))
    api(project(":lego-flow-redis"))
    api(project(":lego-flow-dns"))
    api(project(":lego-flow-smtp"))
    api(project(":lego-flow-coap"))
    api(project(":lego-flow-gssapi"))
    api(project(":lego-flow-http-auth-core"))
    api(project(":lego-flow-http-auth-basic-digest"))
    api(project(":lego-flow-sip"))
    api(project(":lego-flow-rtp"))
    api(project(":lego-flow-stomp"))
    api(project(":lego-flow-amqp"))
    api(project(":lego-flow-ftp"))
    api(project(":lego-flow-ldap"))
    api(project(":lego-flow-kafka"))
    api(project(":lego-flow-modbus"))
    api(project(":lego-flow-mysql"))
    api(project(":lego-flow-nats"))
    api(project(":lego-flow-snmp"))
    api(project(":lego-flow-ssh"))
    api(project(":lego-flow-upnp"))
    api(project(":lego-flow-web-services"))
    api(project(":lego-flow-graphql"))
    implementation("org.openjdk.jmh:jmh-core:1.37")
    implementation("org.slf4j:slf4j-simple:2.0.9")
}
