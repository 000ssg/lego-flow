// Lego Flow Demos — Protocol demonstration and example code
// Excluded from Maven install/deploy lifecycle

val javaRelease: String by project
val slf4jVersion: String by project
val mockitoVersion: String by project

plugins { `java-library` }

group = "ssg"
version = rootProject.version

configure<JavaPluginExtension> {
    toolchain { languageVersion.set(JavaLanguageVersion.of(javaRelease.toInt())) }
}

tasks.withType<JavaCompile> { options.encoding = "UTF-8"; options.release.set(javaRelease.toInt()) }
tasks.withType<Test> { useJUnitPlatform(); maxParallelForks = 4; jvmArgs("-XX:+UseG1GC") }
tasks.withType<AbstractArchiveTask>().configureEach { isPreserveFileTimestamps = false; isReproducibleFileOrder = true }

dependencies {
    // Core modules
    api(project(":lego-flow-blocks")); api(project(":lego-flow-service"))

    // Cluster modules
    api(project(":lego-flow-cluster-core")); api(project(":lego-flow-cluster-discovery"))
    api(project(":lego-flow-cluster-coordination"))

    // Web modules
    api(project(":lego-flow-http")); api(project(":lego-flow-http2")); api(project(":lego-flow-http3"))
    api(project(":lego-flow-web-services")); api(project(":lego-flow-http-proxy"))

    // Messaging modules
    api(project(":lego-flow-kafka")); api(project(":lego-flow-amqp"))
    api(project(":lego-flow-stomp")); api(project(":lego-flow-nats"))
    api(project(":lego-flow-mqtt")); api(project(":lego-flow-xmpp")); api(project(":lego-flow-wamp"))

    // RPC modules
    api(project(":lego-flow-grpc")); api(project(":lego-flow-graphql"))

    // Database modules
    api(project(":lego-flow-redis")); api(project(":lego-flow-postgresql")); api(project(":lego-flow-mysql"))

    // Email modules
    api(project(":lego-flow-email-common")); api(project(":lego-flow-smtp")); api(project(":lego-flow-imap"))

    // Network modules
    api(project(":lego-flow-network-common")); api(project(":lego-flow-dns")); api(project(":lego-flow-ldap"))
    api(project(":lego-flow-snmp")); api(project(":lego-flow-syslog"))
    api(project(":lego-flow-modbus")); api(project(":lego-flow-ssh")); api(project(":lego-flow-ftp"))

    // Terminal modules
    api(project(":lego-flow-terminals-base"))
    api(project(":lego-flow-vt52"))
    api(project(":lego-flow-vt100"))
    api(project(":lego-flow-vt200"))
    api(project(":lego-flow-vt400"))
    api(project(":lego-flow-vt500"))
    api(project(":lego-flow-ansi"))
    api(project(":lego-flow-xterm"))

    // Telnet modules
    api(project(":lego-flow-telnet-base"))
    api(project(":lego-flow-telnet-negotiation"))
    api(project(":lego-flow-telnet-gateway"))

    // IoT modules
    api(project(":lego-flow-upnp")); api(project(":lego-flow-coap"))

    // Media modules
    api(project(":lego-flow-media-common")); api(project(":lego-flow-rtsp"))
    api(project(":lego-flow-rtp")); api(project(":lego-flow-sip"))

    // Auth modules
    api(project(":lego-flow-gssapi"))
    api(project(":lego-flow-http-auth-core")); api(project(":lego-flow-http-auth-basic-digest"))
    api(project(":lego-flow-http-auth-oauth")); api(project(":lego-flow-http-auth-sso"))
    api(project(":lego-flow-http-auth-spnego"))

    // Logging (for demos)
    implementation("org.slf4j:slf4j-api:$slf4jVersion")

    // Testing
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
}
