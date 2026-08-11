// Lego Flow Media Common — SDP Parser (RFC 4566)
val slf4jVersion: String by project

dependencies {
    api(project(":lego-flow-blocks"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
