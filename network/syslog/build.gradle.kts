// Lego Flow Syslog — Structured Logging Protocol (RFC 5424)
val slf4jVersion: String by project

dependencies {
    api(project(":blocks"))
    api(project(":service"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
