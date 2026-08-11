// Lego Flow Email Common — MIME Parsing (RFC 2045-2049)
val slf4jVersion: String by project

dependencies {
    api(project(":lego-flow-blocks"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
