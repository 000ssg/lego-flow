// Lego Flow SSH — Secure Shell Protocol (RFC 4251-4256)
val slf4jVersion: String by project

dependencies {
    api(project(":lego-flow-blocks"))
    api(project(":lego-flow-service"))
    api(project(":lego-flow-gssapi"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
