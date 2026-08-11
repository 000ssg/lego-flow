// Lego Flow LDAP — Lightweight Directory Access Protocol v3 (RFC 4511)
val slf4jVersion: String by project

dependencies {
    api(project(":lego-flow-blocks"))
    api(project(":lego-flow-service"))
    api(project(":lego-flow-network-common"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
