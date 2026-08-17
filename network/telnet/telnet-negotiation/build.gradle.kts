// Lego Flow Telnet — Option negotiation (RFC 855)
val slf4jVersion: String by project

dependencies {
    api(project(":lego-flow-telnet-base"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
